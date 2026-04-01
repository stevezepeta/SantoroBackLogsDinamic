package backlogs.dinamico.api.dto.logs;

import java.time.Instant;
import java.util.List;

public record LogTimelineResponse(
        Header header,
        List<Item> items,
        PageMeta page
) {

    public record Header(String system, String caseId) {}

    public record Item(
            String id,
            Instant eventTime,
            String eventType,
            String status,
            String outcome,
            String severity,
            String message,

            // actor
            String actorId,
            String actorUsername,
            String actorFullName,

            // location
            String locationId,
            String locationName,

            // correlation
            String requestId,

            // geo
            List<Double> geoCoordinates,
            Integer geoAccuracyMeters
    ) {}

    public record PageMeta(int page, int size, boolean hasNext) {}

    public static LogTimelineResponse withMeta(Header header, List<Item> items, int page, int size, boolean hasNext) {
        return new LogTimelineResponse(header, items, new PageMeta(page, size, hasNext));
    }
}
