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
import java.util.List;
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

    @PostMapping("/events/batch")
    public ApiResponse<?> ingestBatch(
            @Valid @RequestBody List<LogEventIngestReq> reqs,
            Authentication auth
    ) {
        if (reqs == null || reqs.isEmpty()) {
            return ApiResponse.ok("Sin logs", "log_batch_empty", Map.of("saved", 0));
        }

        // Limitar a 500 por petición
        List<LogEventIngestReq> batch = reqs.size() > 500 ? reqs.subList(0, 500) : reqs;

        int saved = 0;
        int skipped = 0;
        for (LogEventIngestReq req : batch) {
            try {
                service.ingest(req);
                saved++;
            } catch (Exception e) {
                skipped++;
            }
        }

        return ApiResponse.ok(
                "Batch procesado",
                "log_batch_ingest",
                Map.of(
                        "received", reqs.size(),
                        "saved",    saved,
                        "skipped",  skipped
                )
        );
    }

    @GetMapping("/events")
    public ApiResponse<Map<String, Object>> search(
            @RequestParam String system,

            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String eventCode,
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
            @RequestParam(defaultValue = "DESC") String sortDir,

            Authentication auth
    ) {
        Instant from = (fromDate != null) ? fromDate.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant to   = (toDate != null) ? toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;

        var result = service.search(
                auth,
                system,
                from,
                to,
                caseId,
                eventType,
                eventCode,
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
    public ApiResponse<LogEvent> getById(@PathVariable String id, Authentication auth) {
        LogEvent ev = service.getById(auth, new ObjectId(id));
        return ApiResponse.ok("Detalle del log", "log_event_detail", ev);
    }

    @GetMapping("/timeline")
    public ApiResponse<LogTimelineResponse> timeline(
            @RequestParam String system,
            @RequestParam String caseId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,

            Authentication auth
    ) {
        Instant from = (fromDate != null) ? fromDate.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant to   = (toDate != null) ? toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;

        var data = service.timeline(auth, system, caseId, from, to, page, size);
        return ApiResponse.ok("Timeline", "log_timeline", data);
    }

    @GetMapping("/events/all")
    public ApiResponse<Map<String, Object>> all(
            @RequestParam(required = false) String system,       // ← NUEVO filtro principal
            @RequestParam(required = false) String eventType,    // ← NUEVO
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) String status,       // ← NUEVO
            @RequestParam(required = false) String outcome,      // ← NUEVO
            @RequestParam(required = false) String severity,     // ← NUEVO

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,

            Authentication auth
    ) {
        Instant from = (fromDate != null) ? fromDate.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant to   = (toDate != null) ? toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;

        var result = service.all(auth, system, eventType, eventCode, status, outcome, severity,
                from, to, page, size, sortBy, sortDir);

        Map<String, Object> data = Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        );

        return ApiResponse.ok("Logs (ALL)", "log_events_all", data);
    }

}