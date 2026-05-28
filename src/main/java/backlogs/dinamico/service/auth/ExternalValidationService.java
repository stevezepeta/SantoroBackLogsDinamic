package backlogs.dinamico.service.auth;

import backlogs.dinamico.model.core.User;
import backlogs.dinamico.repository.core.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Servicio de validación de credenciales para sistemas externos (ej: Tickets).
 * Permite validar email/password sin generar tokens JWT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalValidationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Valida las credenciales de un usuario sin restricción de tenant.
     * Busca el usuario por email (case-insensitive) en toda la base de datos.
     *
     * @param email    Email del usuario (case-insensitive)
     * @param password Contraseña en texto plano
     * @return true si el usuario existe y la contraseña coincide; false en caso contrario
     */
    public boolean validateCredentials(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.debug("[ExternalValidation] Credenciales vacías recibidas");
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase();

        // Buscar usuario por email (sin restricción de tenant)
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (user == null) {
            log.debug("[ExternalValidation] Usuario no encontrado: {}", normalizedEmail);
            return false;
        }

        // Validar que el usuario esté activo
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            log.debug("[ExternalValidation] Usuario inactivo: {}", normalizedEmail);
            return false;
        }

        // Comparar password con el hash almacenado
        boolean matches = passwordEncoder.matches(password, user.getPasswordHash());

        if (matches) {
            log.info("[ExternalValidation] ✅ Credenciales válidas para: {}", normalizedEmail);
        } else {
            log.debug("[ExternalValidation] ❌ Contraseña incorrecta para: {}", normalizedEmail);
        }

        return matches;
    }
}

