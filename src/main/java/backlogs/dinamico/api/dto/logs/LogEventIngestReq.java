package backlogs.dinamico.api.dto.logs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record LogEventIngestReq(
        Integer schemaVersion,

        @NotBlank
        String system,
        String environment,

        @NotBlank
        String caseId,
        @NotNull
        Instant eventTime,

        @NotBlank
        String eventType,
        @NotBlank
        String status,

        String outcome,              // SUCCESS|FAILURE|IN_PROGRESS|CANCELED
        String severity,             // DEBUG|INFO|ERROR|FATAL

        @NotBlank
        String message,

        GeoPoint geo,                // Coordenadas del evento, real

        Actor actor,
        Location location,
        Correlation correlation,
        HttpInfo http,
        SlaInfo sla,
        ReasonInfo reason,

        List<String> tags,

        Map<String, Object> payload,
        Map<String, Object> meta

) {

    public record GeoPoint(
            String type,
            List<Double> coordinates,
            Integer accuracyMeters
    ) {}

    public record Actor(
            String id,
            String type,
            String username,
            String fullName
    ) {}
    public record Location(
            String id,
            String name,
            String city,
            String country
    ) {}
    public record Correlation(
            String requestId,
            String traceId,
            String spanId
    ) {}
    public record HttpInfo(
            String method,
            String path,
            Integer statusCode,
            Long latencyMs
    ) {}
    public record SlaInfo(
            Instant startTime,
            Instant endTime,
            Long elapsedSeconds
    ) {}
    public record ReasonInfo(
            String code,
            String description
    ) {}

}
