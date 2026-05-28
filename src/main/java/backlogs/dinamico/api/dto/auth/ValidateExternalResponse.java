package backlogs.dinamico.api.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response de validación externa de credenciales.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateExternalResponse {

    /**
     * Indica si las credenciales son válidas.
     */
    private boolean isValid;
}

