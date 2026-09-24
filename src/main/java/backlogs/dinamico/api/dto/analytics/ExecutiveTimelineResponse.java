package backlogs.dinamico.api.dto.analytics;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta del historial ejecutivo / timeline simplificado para supervisores.
 */
public record ExecutiveTimelineResponse(
        Header header,
        List<OperationalEventCard> events,
        PageMeta page
) {

    public record Header(
            String system,
            String caseId,
            String actorName,
            String locationName,
            Instant periodStart,
            Instant periodEnd
    ) {
    }
}
