package backlogs.dinamico.service.email;

import backlogs.dinamico.model.core.EmailVerification;
import backlogs.dinamico.repository.core.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Hashtable;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailValidationService {

    private static final Pattern EMAIL_REGEX =
            Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private static final int OTP_TTL_MINUTES  = 10;
    private static final int TOKEN_TTL_MINUTES = 15;
    private static final int MAX_ATTEMPTS      = 3;
    private static final int OTP_LENGTH        = 6;

    private final EmailVerificationRepository verificationRepo;
    private final EmailSenderPort             emailSender;
    private final PasswordEncoder             passwordEncoder;

    // ── Validación sin enviar OTP (para flujo de invite) ─────────────────────

    /**
     * Solo valida formato + MX records.
     * Lanza excepción si el email no es válido o el dominio no existe.
     * No genera ni envía ningún OTP.
     */
    public void validateOnly(String email) {
        if (!StringUtils.hasText(email))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email_required");

        String emailNorm = email.trim().toLowerCase();

        if (!EMAIL_REGEX.matcher(emailNorm).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email_format_invalid");

        String domain = emailNorm.substring(emailNorm.indexOf('@') + 1);
        if (!hasMxRecords(domain))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "email_domain_invalid: el dominio '" + domain + "' no tiene registros MX");
    }

    // ── Paso 1: validar + enviar OTP ─────────────────────────────────────────

    /**
     * Valida el email (formato + MX records) y envía el OTP.
     * Si ya hay un PENDING activo para este email, lo invalida y crea uno nuevo.
     *
     * @throws ResponseStatusException 400 formato inválido
     * @throws ResponseStatusException 422 dominio sin MX records (no existe)
     */
    public void sendOtp(String email) {
        if (!StringUtils.hasText(email))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email_required");

        String emailNorm = email.trim().toLowerCase();

        // ── 1. Formato ────────────────────────────────────────────────────────
        if (!EMAIL_REGEX.matcher(emailNorm).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email_format_invalid");

        // ── 2. MX Records (el dominio acepta emails) ──────────────────────────
        String domain = emailNorm.substring(emailNorm.indexOf('@') + 1);
        boolean mxValid = hasMxRecords(domain);
        if (!mxValid)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "email_domain_invalid: el dominio '" + domain + "' no tiene registros MX");

        // ── 3. Invalidar PENDING previo ───────────────────────────────────────
        verificationRepo.findFirstByEmailCiAndStatusOrderByCreatedAtDesc(emailNorm, "PENDING")
                .ifPresent(prev -> {
                    prev.setStatus("EXPIRED");
                    prev.setUpdatedAt(Instant.now());
                    verificationRepo.save(prev);
                });

        // ── 4. Generar OTP ────────────────────────────────────────────────────
        String otp     = generateOtp();
        String otpHash = passwordEncoder.encode(otp);   // nunca guardar en claro

        Instant now        = Instant.now();
        Instant otpExpires = now.plusSeconds(OTP_TTL_MINUTES * 60L);

        EmailVerification ev = EmailVerification.builder()
                .email(email.trim())
                .emailCi(emailNorm)
                .otpHash(otpHash)
                .otpExpiresAt(otpExpires)
                .attempts(0)
                .status("PENDING")
                .mxValid(true)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plusSeconds(86400))  // TTL MongoDB
                .build();

        verificationRepo.save(ev);

        // ── 5. Enviar ─────────────────────────────────────────────────────────
        emailSender.sendOtp(email.trim(), otp, OTP_TTL_MINUTES);

        log.info("[EmailValidation] OTP enviado a {} (dominio MX válido: {})", emailNorm, domain);
    }

    // ── Paso 2: confirmar OTP → emitir verificationToken ─────────────────────

    /**
     * Valida el OTP y emite un verificationToken de uso único (15 min).
     *
     * @return verificationToken — el frontend lo incluye en el body del invite/create-org
     * @throws ResponseStatusException 400 OTP inválido o expirado
     * @throws ResponseStatusException 429 demasiados intentos fallidos
     */
    public String confirmOtp(String email, String otp) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(otp))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email_and_otp_required");

        String emailCi = email.trim().toLowerCase();

        EmailVerification ev = verificationRepo
                .findFirstByEmailCiAndStatusOrderByCreatedAtDesc(emailCi, "PENDING")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "no_pending_verification: solicita un nuevo código"));

        // ── Expirado ──────────────────────────────────────────────────────────
        if (Instant.now().isAfter(ev.getOtpExpiresAt())) {
            ev.setStatus("EXPIRED");
            ev.setUpdatedAt(Instant.now());
            verificationRepo.save(ev);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "otp_expired: solicita un nuevo código");
        }

        // ── Demasiados intentos ───────────────────────────────────────────────
        if (ev.getAttempts() >= MAX_ATTEMPTS) {
            ev.setStatus("EXPIRED");
            ev.setUpdatedAt(Instant.now());
            verificationRepo.save(ev);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "otp_max_attempts: solicita un nuevo código");
        }

        // ── OTP incorrecto ────────────────────────────────────────────────────
        if (!passwordEncoder.matches(otp.trim(), ev.getOtpHash())) {
            ev.setAttempts(ev.getAttempts() + 1);
            ev.setUpdatedAt(Instant.now());
            verificationRepo.save(ev);

            int remaining = MAX_ATTEMPTS - ev.getAttempts();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "otp_invalid: " + remaining + " intento(s) restante(s)");
        }

        // ── OTP correcto → emitir verificationToken ───────────────────────────
        String token       = UUID.randomUUID().toString();
        Instant tokenExp   = Instant.now().plusSeconds(TOKEN_TTL_MINUTES * 60L);

        ev.setStatus("VERIFIED");
        ev.setVerificationToken(token);
        ev.setTokenExpiresAt(tokenExp);
        ev.setUpdatedAt(Instant.now());
        verificationRepo.save(ev);

        log.info("[EmailValidation] OTP confirmado para {} — token emitido (exp: {})", emailCi, tokenExp);
        return token;
    }

    // ── Paso 3: consumir verificationToken (llamado desde invite/create-org) ──

    /**
     * Verifica que el token sea válido y que corresponda al email indicado.
     * Marca el token como USED para que no pueda reutilizarse.
     *
     * @throws ResponseStatusException 400 token inválido, expirado o email no coincide
     */
    public void consumeToken(String verificationToken, String email) {
        if (!StringUtils.hasText(verificationToken))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "verification_token_required: el email debe ser verificado primero");

        EmailVerification ev = verificationRepo.findByVerificationToken(verificationToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "verification_token_invalid"));

        if (!"VERIFIED".equals(ev.getStatus()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "verification_token_already_used");

        if (Instant.now().isAfter(ev.getTokenExpiresAt()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "verification_token_expired: solicita un nuevo código OTP");

        // El email del token debe coincidir con el email del invite
        if (!ev.getEmailCi().equals(email.trim().toLowerCase()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "verification_token_email_mismatch");

        // Marcar como USED — no puede reutilizarse
        ev.setStatus("USED");
        ev.setUpdatedAt(Instant.now());
        verificationRepo.save(ev);

        log.info("[EmailValidation] Token consumido para {}", ev.getEmailCi());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Verifica que el dominio tenga MX records usando JNDI (DNS).
     * No requiere dependencias externas — está en el JDK estándar.
     */
    @SuppressWarnings("unchecked")
    private boolean hasMxRecords(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url",    "dns://8.8.8.8");  // Google DNS
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");

            InitialDirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
            ctx.close();

            var mx = attrs.get("MX");
            boolean valid = mx != null && mx.size() > 0;
            log.debug("[MX] dominio={} mxValid={}", domain, valid);
            return valid;

        } catch (NamingException e) {
            log.debug("[MX] dominio={} sin MX records: {}", domain, e.getMessage());
            return false;
        } catch (Exception e) {
            // Si el DNS falla por red, ser permisivo (no bloquear por error de infra)
            log.warn("[MX] Error consultando DNS para {}: {} — se permite continuar", domain, e.getMessage());
            return true;
        }
    }

    private String generateOtp() {
        SecureRandom rng = new SecureRandom();
        int code = 100_000 + rng.nextInt(900_000);   // 100000–999999
        return String.valueOf(code);
    }
}