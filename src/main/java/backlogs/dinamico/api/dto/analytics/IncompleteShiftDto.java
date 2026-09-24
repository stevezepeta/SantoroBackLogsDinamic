package backlogs.dinamico.api.dto.analytics;

import java.time.Instant;

/**
 * Anomalía: jornada iniciada sin marcado de salida después de 12h.
 */
public record IncompleteShiftDto(
        String username,
        String fullName,
        Instant startTime,
        double hoursElapsed,
        String detail,
        String status
) {
}
