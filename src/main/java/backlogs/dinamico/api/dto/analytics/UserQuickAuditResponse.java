package backlogs.dinamico.api.dto.analytics;

import java.time.Instant;

/**
 * Respuesta consolidada de la auditoría rápida de usuario.
 * Resume la actividad más reciente de un actor en la colección log_events.
 */
public record UserQuickAuditResponse(
        String username,
        String fullName,
        boolean isActiveToday,
        long totalEventsToday,
        Instant lastSeen,
        String lastDevice,
        String lastIp,
        String lastEventType,
        String lastEventOutcome
) {
}
