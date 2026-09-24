package backlogs.dinamico.api.dto.analytics;

import java.time.Instant;

/**
 * Anomalía: cierre de jornada sin entrada previa.
 */
public record OrphanClosureDto(
        String username,
        String fullName,
        Instant eventTime,
        String detail,
        String device,
        String status
) {
}
