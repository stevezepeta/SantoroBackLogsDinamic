package backlogs.dinamico.controller.dashboard;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.logs.LogTimelineResponse;
import backlogs.dinamico.api.dto.passport.PassportSummaryResponse;
import backlogs.dinamico.api.dto.passport.PassportsByOfficeItem;
import backlogs.dinamico.api.dto.passport.PassportsByTypeItem;
import backlogs.dinamico.model.log.LogEvent;
import backlogs.dinamico.service.dashboard.PassportEventService;
import backlogs.dinamico.service.dashboard.PassportOverviewService;
import backlogs.dinamico.service.dashboard.PassportTimelineService;
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
@RequestMapping(value = "/api/dashboard/passports", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PassportDashboardController {

    private static final String PASSPORT_SYSTEM = "PASSPORT_PA";

    private final PassportOverviewService passportOverviewService;
    private final PassportEventService passportEventService;     // ahora lee de log_events
    private final PassportTimelineService timelineService;       // resuelve caseId y llama a timeline universal

    /*
     * Resumen general (sigue siendo el mismo DTO, pero ahora se calcula desde log_events)
     */
    @GetMapping("/summary")
    public ApiResponse<PassportSummaryResponse> getSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @RequestParam(required = false) String officeId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String operationType,

            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "dbStatus", required = false) String dbStatus
    ) {
        officeId = normalize(officeId);
        userId = normalize(userId);
        channel = normalize(channel);
        operationType = normalize(operationType);

        String finalStatus = normalize(dbStatus != null ? dbStatus : status);

        Instant from = (fromDate != null)
                ? fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
                : null;

        Instant to = (toDate != null)
                ? toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                : null;

        PassportSummaryResponse data = passportOverviewService.getSummary(
                PASSPORT_SYSTEM,
                from,
                to,
                officeId,
                userId,
                channel,
                operationType,
                finalStatus
        );

        return ApiResponse.ok("Resumen general de pasaportes", "passports_summary", data);
    }

    @GetMapping("/by-office")
    public ApiResponse<List<PassportsByOfficeItem>> getByOffice(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {
        Instant from = (fromDate != null)
                ? fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
                : PassportOverviewService.defaultFrom();

        Instant to = (toDate != null)
                ? toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                : PassportOverviewService.defaultTo();

        var data = passportOverviewService.getByOffice(PASSPORT_SYSTEM, from, to);
        return ApiResponse.ok("Pasaportes agrupados por oficina", "passports_by_office", data);
    }

    @GetMapping("/by-type")
    public ApiResponse<List<PassportsByTypeItem>> getByType(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {
        Instant from = (fromDate != null)
                ? fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
                : PassportOverviewService.defaultFrom();

        Instant to = (toDate != null)
                ? toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                : PassportOverviewService.defaultTo();

        var data = passportOverviewService.getByType(PASSPORT_SYSTEM, from, to);
        return ApiResponse.ok("Pasaportes agrupados por tipo de trámite", "passports_by_type", data);
    }

    /*
     * GET ALL (ahora devuelve LogEvent desde log_events)
     */
    @GetMapping("/events")
    public ApiResponse<Map<String, Object>> searchEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "DESC") String sortDir,
            @RequestParam(required = false) String sortBy,

            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String operationType,

            @RequestParam(required = false) String officeId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String message,

            @RequestParam(required = false) String reasonCode,

            // fallback para resolver caseId si te mandan estos:
            @RequestParam(required = false) String passportNumber,
            @RequestParam(required = false) String personId,
            @RequestParam(required = false) String requestId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            Authentication auth
    ) {
        Instant from = (fromDate != null)
                ? fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
                : PassportOverviewService.defaultFrom();

        Instant to = (toDate != null)
                ? toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                : PassportOverviewService.defaultTo();

        var result = passportEventService.searchPassportEvents(
                PASSPORT_SYSTEM,
                from,
                to,
                normalize(caseId),
                normalize(passportNumber),
                normalize(personId),
                normalize(requestId),
                normalize(status),
                normalize(operationType),
                normalize(officeId),
                normalize(userId),
                normalize(channel),
                normalize(message),
                normalize(reasonCode),
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

        return ApiResponse.ok("Logs de pasaportes (desde log_events)", "passport_events_search", data);
    }

    /*
     * GET BY ID (ahora devuelve LogEvent)
     */
    @GetMapping("/events/{id}")
    public ApiResponse<LogEvent> getEventById(@PathVariable String id) {
        LogEvent ev = passportEventService.getLogByIdForCurrentTenant(new ObjectId(id));
        return ApiResponse.ok("Detalle del log", "passport_log_detail", ev);
    }

    /*
     * Timeline (devuelve el DTO universal LogTimelineResponse)
     */
    @GetMapping("/timeline")
    public ApiResponse<LogTimelineResponse> getTimeline(
            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) String passportNumber,
            @RequestParam(required = false) String personId,
            @RequestParam(required = false) String requestId,

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

        var data = timelineService.timelinePassport(
                auth,
                PASSPORT_SYSTEM,
                normalize(caseId),
                normalize(passportNumber),
                normalize(personId),
                normalize(requestId),
                from,
                to,
                page,
                size
        );

        return ApiResponse.ok("Timeline", "passports_timeline", data);
    }

    private String normalize(String v) {
        if (v == null) return null;
        v = v.trim();
        if (v.isEmpty()) return null;
        if ("null".equalsIgnoreCase(v)) return null;
        return v;
    }
}
