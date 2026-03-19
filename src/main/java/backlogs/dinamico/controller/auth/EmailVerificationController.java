package backlogs.dinamico.controller.auth;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.service.email.EmailValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints del flujo de verificación de email por OTP.
 *
 * Paso 1 → POST /api/email/verify/send      — enviar OTP
 * Paso 2 → POST /api/email/verify/confirm   — validar OTP → recibir verificationToken
 *
 * El verificationToken se usa en:
 *   - POST /api/admin/invites               (campo: verificationToken)
 *   - POST /api/santoro/panel/organizations (campo: verificationToken en adminEmail)
 */
@Tag(name = "Email Verification", description = "Verificación de email por OTP antes de invitar o crear usuarios.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/email/verify")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailValidationService validationService;

    // ── Paso 1: enviar OTP ────────────────────────────────────────────────────

    @Operation(
            summary = "Enviar OTP de verificación",
            description = """
                    Valida formato + MX records del dominio y envía un código OTP al email.
                    El código expira en **10 minutos** y permite **3 intentos**.

                    Llamar antes de crear un invite o una organización nueva.
                    """
    )
    @PostMapping("/send")
    @PreAuthorize("hasAnyAuthority('PERM_USERS_MANAGE', 'PERM_ROLES_ASSIGN', 'ORG_ADMIN', 'PERM_SETTINGS_MANAGE')")
    public ApiResponse<Map<String, Object>> send(@Valid @RequestBody SendOtpRequest req) {
        validationService.sendOtp(req.getEmail());

        return ApiResponse.ok("OTP enviado", "email_otp_sent", Map.of(
                "email",      req.getEmail().trim().toLowerCase(),
                "message",    "Código de verificación enviado. Válido 10 minutos.",
                "maxAttempts", 3
        ));
    }

    // ── Paso 2: confirmar OTP ─────────────────────────────────────────────────

    @Operation(
            summary = "Confirmar OTP y obtener verificationToken",
            description = """
                    Valida el OTP recibido por email.
                    Si es correcto devuelve un **verificationToken** (válido **15 minutos**).

                    Incluye este token en el body de:
                    - `POST /api/admin/invites` → campo `verificationToken`
                    - `POST /api/santoro/panel/organizations` → campo `verificationToken`
                    """
    )
    @PostMapping("/confirm")
    @PreAuthorize("hasAnyAuthority('PERM_USERS_MANAGE', 'PERM_ROLES_ASSIGN', 'ORG_ADMIN', 'PERM_SETTINGS_MANAGE')")
    public ApiResponse<Map<String, Object>> confirm(@Valid @RequestBody ConfirmOtpRequest req) {
        String token = validationService.confirmOtp(req.getEmail(), req.getOtp());

        return ApiResponse.ok("Email verificado", "email_verified", Map.of(
                "email",             req.getEmail().trim().toLowerCase(),
                "verificationToken", token,
                "expiresInMinutes",  15,
                "message",           "Email verificado. Usa el verificationToken en tu próxima petición."
        ));
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @Data
    public static class SendOtpRequest {
        @NotBlank(message = "email es obligatorio")
        @Email(message = "email inválido")
        private String email;
    }

    @Data
    public static class ConfirmOtpRequest {
        @NotBlank(message = "email es obligatorio")
        @Email(message = "email inválido")
        private String email;

        @NotBlank(message = "otp es obligatorio")
        @Pattern(regexp = "^\\d{6}$", message = "otp debe ser 6 dígitos numéricos")
        private String otp;
    }
}