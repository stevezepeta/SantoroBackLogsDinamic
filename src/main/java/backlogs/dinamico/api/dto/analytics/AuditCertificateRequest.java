package backlogs.dinamico.api.dto.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Petición para generar un certificado de auditoría en PDF.
 */
public record AuditCertificateRequest(
        @NotBlank(message = "username_required")
        String username,

        String system,

        @NotNull(message = "from_date_required")
        String fromDate,

        @NotNull(message = "to_date_required")
        String toDate,

        String reason
) {
}
