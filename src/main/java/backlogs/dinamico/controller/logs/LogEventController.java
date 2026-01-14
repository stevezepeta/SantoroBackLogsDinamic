package backlogs.dinamico.controller.logs;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.logs.LogEventIngestReq;
import backlogs.dinamico.api.dto.logs.LogTimelineResponse;
import backlogs.dinamico.model.log.LogEvent;
import backlogs.dinamico.service.logs.LogEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/logs", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class LogEventController {

    private final LogEventService service;

    @PostMapping("/events")
    public ApiResponse<?> ingest(
                @Valid @RequestBody LogEventIngestReq req,
                Authentication auth
            ) {

        LogEvent saved = service.ingest(req);

        return ApiResponse.ok(
                "Log registro correctamente",
                "log_event_ingest",
                Map.of(
                        "id", saved.getId().toHexString(),
                        "tenantId", saved.getTenantId().toHexString(),
                        "system", saved.getSystem(),
                        "caseId", saved.getCaseId(),
                        "eventTime", saved.getEventTime()
                )
        );
    }

    @GetMapping("/events")
    public ApiResponse<Map<String, Object>> search(
            @RequestParam String system,

            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String severity,

            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String locationId,
            @RequestParam(required = false) String requestId,

            @RequestParam(required = false) String text,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir
    ) {
        Instant from = (fromDate != null) ? fromDate.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant to   = (toDate != null) ? toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;

        var result = service.search(
                system,
                from,
                to,
                caseId,
                eventType,
                status,
                outcome,
                severity,
                actorId,
                locationId,
                requestId,
                text,
                page,
                size,
                sortBy,
                sortDir
        );

        Map<String, Object> data = Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        );

        return ApiResponse.ok("Logs", "log_events_search", data);
    }

    @GetMapping("/events/{id}")
    public ApiResponse<LogEvent> getById(@PathVariable String id) {
        LogEvent ev = service.getById(new ObjectId(id));
        return ApiResponse.ok("Detalle del log", "log_event_detail", ev);
    }

    @GetMapping("/timeline")
    public ApiResponse<LogTimelineResponse> timeline(
            @RequestParam String system,
            @RequestParam String caseId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        Instant from = (fromDate != null) ? fromDate.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant to   = (toDate != null) ? toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;

        var data = service.timeline(system, caseId, from, to);
        return ApiResponse.ok("Timeline", "log_timeline", data);
    }

}
