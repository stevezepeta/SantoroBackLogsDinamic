package backlogs.dinamico.service.auth;

import backlogs.dinamico.api.dto.ForgotPasswordRequest;
import backlogs.dinamico.api.dto.ResetPasswordRequest;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.service.email.EmailSenderPort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

/**
 * Servicio de recuperación de contraseñas SIN dependencia de TenantContext.
 * Flujo público:
 *   1. POST /forgot-password → genera código, lo envía por email
 *   2. POST /reset-password  → valida código y actualiza password
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSenderPort emailSender;

    @Value("${app.password-reset.ttl-minutes:15}")
    private long resetTtlMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ══════════════════════════════════════════════════════════════════════════
    // PASO 1: Solicitud de reset (POST /forgot-password)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Busca usuario por email GLOBAL (sin tenant), genera código de 6 dígitos
     * y lo envía por correo.
     * 
     * SEGURIDAD: Siempre retorna OK aunque el email no exista (previene enumeración).
     */
    public void requestReset(@Valid ForgotPasswordRequest req) {
        if (req == null || !StringUtils.hasText(req.getEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "email_required");
        }

        String email = req.getEmail().trim().toLowerCase();

        // Buscar usuario globalmente (sin filtrar por tenant)
        Optional<User> optUser = userRepository.findByEmailIgnoreCase(email);

        if (optUser.isEmpty()) {
            log.info("[PWD-RESET] Solicitud para email no encontrado: {} (silenciado para seguridad)", email);
            // No revelamos al frontend que el usuario no existe (previene enumeración)
            return;
        }

        User user = optUser.get();

        // Validar que el usuario esté activo
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            log.warn("[PWD-RESET] Usuario {} no está activo (status={})", email, user.getStatus());
            // Por seguridad, no revelamos el motivo al frontend
            return;
        }

        // Generar código de 6 dígitos
        String code = generateSixDigitCode();
        String codeHash = passwordEncoder.encode(code);

        Instant now = Instant.now();
        Instant expires = now.plus(resetTtlMinutes, ChronoUnit.MINUTES);

        // Actualizar usuario con el código hasheado
        user.setResetCodeHash(codeHash);
        user.setResetCodeExpires(expires);
        userRepository.save(user);

        // Enviar email con el código
        try {
            emailSender.sendPasswordResetCode(
                    user.getEmail(),
                    user.getName() != null ? user.getName() : "Usuario",
                    code,
                    (int) resetTtlMinutes
            );
            log.info("[PWD-RESET] Código enviado a {} (válido {} min)", email, resetTtlMinutes);
        } catch (Exception e) {
            log.error("[PWD-RESET] Error enviando email a {}: {}", email, e.getMessage());
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "email_send_failed");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PASO 2: Confirmar código y cambiar contraseña (POST /reset-password)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Valida el código recibido contra el hash en BD y actualiza la contraseña.
     * 
     * @throws ResponseStatusException 400 si el código es inválido o expirado
     * @throws ResponseStatusException 404 si el usuario no existe
     */
    public void resetPassword(@Valid ResetPasswordRequest req) {
        if (req == null || !StringUtils.hasText(req.getEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "email_required");
        }
        if (!StringUtils.hasText(req.getCode())) {
            throw new ResponseStatusException(BAD_REQUEST, "code_required");
        }
        if (!StringUtils.hasText(req.getNewPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "password_required");
        }

        String email = req.getEmail().trim().toLowerCase();
        String code = req.getCode().trim();
        String newPassword = req.getNewPassword().trim();

        // Buscar usuario por email
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "user_not_found"));

        // Validar que tenga un código de reset activo
        if (user.getResetCodeHash() == null || user.getResetCodeExpires() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "no_reset_code_requested");
        }

        // Validar que no haya expirado
        if (user.getResetCodeExpires().isBefore(Instant.now())) {
            log.warn("[PWD-RESET] Código expirado para usuario {}", email);
            throw new ResponseStatusException(BAD_REQUEST, "reset_code_expired");
        }

        // Validar que el código coincida (BCrypt hash)
        if (!passwordEncoder.matches(code, user.getResetCodeHash())) {
            log.warn("[PWD-RESET] Código inválido para usuario {}", email);
            throw new ResponseStatusException(BAD_REQUEST, "reset_code_invalid");
        }

        // ✅ Código válido → actualizar contraseña
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetCodeHash(null);        // Limpiar código usado
        user.setResetCodeExpires(null);
        user.setMustChangePassword(false);  // Si estaba forzado, ya no
        userRepository.save(user);

        log.info("[PWD-RESET] Contraseña actualizada exitosamente para usuario {} (tenant={})",
                email,
                user.getTenantId() != null ? user.getTenantId().toHexString() : "null");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Genera un código numérico de 6 dígitos (100000-999999).
     * Usa SecureRandom para seguridad criptográfica.
     */
    private String generateSixDigitCode() {
        int code = 100000 + RANDOM.nextInt(900000); // Rango: 100000-999999
        return String.valueOf(code);
    }

}
