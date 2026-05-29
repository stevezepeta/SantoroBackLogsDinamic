package backlogs.dinamico.controller.auth;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.ForgotPasswordRequest;
import backlogs.dinamico.api.dto.ResetPasswordRequest;
import backlogs.dinamico.service.auth.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin
@Tag(name = "Password Recovery", description = "Recuperación de contraseña sin autenticación (público)")
public class PasswordResetController {

    private final PasswordResetService service;

    /**
     * PASO 1: Solicitar código de recuperación
     * El backend busca el usuario por email (sin necesidad de X-Tenant),
     * genera un código de 6 dígitos y lo envía por correo electrónico.
     */
    @Operation(
            summary = "Solicitar código de recuperación",
            description = """
                Envía un código de 6 dígitos al correo del usuario para recuperar su contraseña.
                
                - No requiere autenticación (endpoint público)
                - No requiere header X-Tenant (el backend busca el usuario por email globalmente)
                - El código expira en 15 minutos por defecto
                - Busca al usuario activo en la base de datos
                - Por seguridad, siempre retorna 200 OK aunque el email no exista
                """
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgot(@Valid @RequestBody ForgotPasswordRequest body) {

        long start = System.currentTimeMillis();
        log.info("[POST] /api/auth/forgot-password email={}", body.getEmail());

        service.requestReset(body);

        ApiResponse<Void> resp = ApiResponse.ok(
                "Si el correo está registrado, recibirás un código de recuperación en breve.",
                "reset_code_sent",
                null
        );
        log.info("[POST] /api/auth/forgot-password done in {} ms", System.currentTimeMillis() - start);
        return ResponseEntity.ok(resp);

    }

    /**
     * PASO 2: Confirmar código y restablecer contraseña
     * Valida el código recibido por email y actualiza la contraseña del usuario.
     */
    @Operation(
            summary = "Restablecer contraseña con código",
            description = """
                Valida el código de recuperación y actualiza la contraseña del usuario.
                
                - No requiere autenticación (endpoint público)
                - No requiere header X-Tenant
                - El código debe coincidir con el enviado por email
                - El código no debe haber expirado (15 min por defecto)
                - La nueva contraseña debe tener mínimo 8 caracteres
                - Limpia el código usado para prevenir reutilización
                - Desactiva la bandera mustChangePassword si estaba activa
                """
    )
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> reset(@Valid @RequestBody ResetPasswordRequest body) {
        long start = System.currentTimeMillis();
        log.info("[POST] /api/auth/reset-password email={}", body.getEmail());

        service.resetPassword(body);

        ApiResponse<Void> resp = ApiResponse.ok(
                "Tu contraseña ha sido actualizada exitosamente. Ya puedes iniciar sesión.",
                "password_reset_success",
                null
        );
        log.info("[POST] /api/auth/reset-password done in {} ms", System.currentTimeMillis() - start);
        return ResponseEntity.ok(resp);
    }

}
