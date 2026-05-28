package backlogs.dinamico.controller.auth;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.auth.ValidateExternalRequest;
import backlogs.dinamico.api.dto.auth.ValidateExternalResponse;
import backlogs.dinamico.service.auth.ExternalValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para validación de credenciales usada por sistemas externos.
 * Expone endpoints públicos (sin autenticación JWT) para verificar
 * email/password contra la base de datos de Logs.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth External", description = "Validación de credenciales para sistemas externos")
public class ExternalValidationController {

    private final ExternalValidationService externalValidationService;

    /**
     * Valida credenciales de usuario (email + password).
     * Endpoint público usado por el sistema de Tickets (Estrategia A).
     * 
     * @param request DTO con email y password en texto plano
     * @return ApiResponse con isValid=true si las credenciales son correctas, false en caso contrario
     */
    @PostMapping(value = "/validate-external", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Validar credenciales externas",
            description = "Valida email y password contra la base de datos. " +
                    "Endpoint público usado por sistemas externos (ej: Tickets). " +
                    "No genera tokens JWT, solo retorna si las credenciales son válidas."
    )
    public ApiResponse<ValidateExternalResponse> validateExternal(
            @Valid @RequestBody ValidateExternalRequest request) {

        log.info("[ValidateExternal] Solicitud de validación para email: {}", request.getEmail());

        boolean isValid = externalValidationService.validateCredentials(
                request.getEmail(),
                request.getPassword()
        );

        ValidateExternalResponse response = ValidateExternalResponse.builder()
                .isValid(isValid)
                .build();

        if (isValid) {
            return ApiResponse.ok("Credenciales válidas", null, response);
        } else {
            return ApiResponse.ok("Credenciales inválidas", null, response);
        }
    }
}

