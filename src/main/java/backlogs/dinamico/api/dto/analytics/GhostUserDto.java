package backlogs.dinamico.api.dto.analytics;

import java.time.Instant;

/**
 * Anomalía: usuario activo sin registros de actividad reciente.
 */
public record GhostUserDto(
        String username,
        String fullName,
        Instant lastSeen,
        String detail,
        String status
) {
}
