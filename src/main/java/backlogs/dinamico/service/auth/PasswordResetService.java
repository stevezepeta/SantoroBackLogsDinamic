package backlogs.dinamico.service.auth;

import backlogs.dinamico.api.dto.ForgotPasswordRequest;
import backlogs.dinamico.api.dto.ResetPasswordRequest;
import backlogs.dinamico.model.core.PasswordResetToken;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.repository.core.PasswordResetTokenRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend.base-url:https://dashboard.grupo-santoro.com.mx}")
    private String frontendBaseUrl;

    @Value("${app.password-reset.ttl-minutes:30}")
    private long resetTtlMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();


    // ---- Helpers multi-Tenant ---
    private ObjectId requireTenant() {
        var t = TenantContext.getTenantId();
        if (t == null) {
            throw new ResponseStatusException(BAD_REQUEST, "missing_tenant");
        }
        return t;
    }

    // Solicita el reset
    public void requestReset(ForgotPasswordRequest req) {
        if (req == null || !StringUtils.hasText(req.getEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "email_required");
        }

        ObjectId tenantId = requireTenant();

        String email = req.getEmail().trim().toLowerCase();

        Optional<User> optUser = userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, email);

        if (optUser.isEmpty()) {
            log.info("[PWD-RESET] Solicitud para email no encontrado: {}", email);
            return;
        }

        User user = optUser.get();
        ObjectId userId = user.getId();

        // Generacion de Token aleatorio
        String rawToken = generateRandomToken();
        String tokenHash = sha256(rawToken);

        Instant now = Instant.now();
        Instant exp = now.plus(resetTtlMinutes, ChronoUnit.MINUTES);

        PasswordResetToken entity = PasswordResetToken.builder()
                .tenantId(tenantId)
                .userId(userId)
                .tokenHash(tokenHash)
                .expirestAt(exp)
                .build();

        tokenRepository.save(entity);

        String resetLink = frontendBaseUrl + "/#/reset-password?token" + rawToken;

        log.info("[PWD-RESET] Link para {}: {}", email, resetLink);
    }

    // Se aplica el reset, valida el token y cambia la password
    public void resetPassword(ResetPasswordRequest req) {

        if (req == null || !StringUtils.hasText(req.getToken())) {
            throw new ResponseStatusException(BAD_REQUEST, "Token_ required");
        }
        if (!StringUtils.hasText(req.getNewPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "password_required");
        }

        String rawToken = req.getToken().trim();
        String tokenHash = sha256(rawToken);

        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "invalid_token"));

        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(BAD_REQUEST, "token_already_used");
        }
        if (token.getExpirestAt() != null && token.getExpirestAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(BAD_REQUEST, "token_expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "user_not_found"));

        // Actualizar el password_hash
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword().trim()));
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        log.info("[PWD-RESET] Password reseteado para user={} tenant={}",
                user.getEmail(),
                token.getTenantId() != null ? token.getTenantId().toHexString() : "null");

    }

    // Helpers
    private String generateRandomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(input.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException("sha256_error", e);
        }
    }

}
