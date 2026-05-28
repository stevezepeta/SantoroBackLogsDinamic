package backlogs.dinamico.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request para validación externa de credenciales (usado por sistema de Tickets).
 */
@Data
public class ValidateExternalRequest {

    @NotBlank(message = "El email es requerido")
    @Email(message = "El email debe ser válido")
    private String email;

    @NotBlank(message = "El password es requerido")
    private String password;
}

