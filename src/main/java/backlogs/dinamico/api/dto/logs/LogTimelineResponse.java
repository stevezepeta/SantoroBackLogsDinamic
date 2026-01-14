package backlogs.dinamico.api.dto.logs;

import java.time.Instant;
import java.util.List;

public record LogTimelineResponse(
        Header header,
        List<Item> items
) {

    public record Header(
            String system,
            String caseId
    ) {}

    public record Item(
            String id,
            Instant eventTime,
            String eventType,
            String status,
            String outcome,
            String severity,
            String message,

            // location
            String actorId,
            String actorUsername,
            String actorFullName,
            String locationId,
            String locationName,

            // correlation
            String requestId,

            // Geo
            List<Double> geoCoordinates,
            Integer geoAccuracyMeters
    ) {}

}
