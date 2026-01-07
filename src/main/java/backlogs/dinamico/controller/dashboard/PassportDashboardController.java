package backlogs.dinamico.controller.dashboard;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.common.PageResult;
import backlogs.dinamico.api.dto.passport.*;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.model.passport.PassportEvent;
import backlogs.dinamico.service.dashboard.PassportEventService;
import backlogs.dinamico.service.dashboard.PassportOverviewService;
import backlogs.dinamico.service.dashboard.PassportTimelineService;
import backlogs.dinamico.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/dashboard/passports", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PassportDashboardController {

    private final PassportOverviewService passportOverviewService;
    private final PassportEventService passportEventService;
    private final PassportTimelineService timelineService;

    @PostMapping("/events")
    public ApiResponse<?> createEvent(@Valid @RequestBody PassportEventCreateReq req,
                                      Authentication auth) {

        ObjectId tenantId = resolveTenantId(auth);
        PassportEvent saved = passportEventService.createEvent(tenantId, req);

        return ApiResponse.ok(
                "Evento de pasaporte registrado correctamente",
                null,
                Map.of(
                        "id", saved.getId().toHexString(),
                        "tenantId", saved.getTenantId().toHexString(),
                        "status", saved.getStatus(),
                        "operationType", saved.getOperationType(),
                        "eventTime", saved.getEventTime()
                )
        );
    }

    /*
     * Resumen general de pasaportes (modulo vision General)
     * si no se manda la fecha, se toman los ultimos 30 dias
     */
    @GetMapping("/summary")
    public ApiResponse<PassportSummaryResponse> getSummary(

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @RequestParam(required = false)
            String officeId,
            @RequestParam(required = false)
            String userId,
            @RequestParam(required = false)
            String channel,
            @RequestParam(required = false)
            String operationType,

            @RequestParam(name = "status", required = false)
            String status,
            @RequestParam(name = "dbStatus", required = false)
            String dbStatus
    ) {
        // Normalizacion de los String
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
                from,
                to,
                officeId,
                userId,
                channel,
                operationType,
                finalStatus
        );

        return ApiResponse.ok(
                "Resumen general de pasaportes",
                "passports_summary",
                data
        );
    }

    private String normalize(String v) {
        if (v == null) return null;
        v = v.trim();
        if (v.isEmpty()) return null;
        if ("null".equalsIgnoreCase(v)) return null;
        return v;
    }

    /*
     * Pasaportes por oficina en rango de fechas
     */
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

        List<PassportsByOfficeItem> data = passportOverviewService.getByOffice(from, to);

        return ApiResponse.ok(
                "Pasaportes agrupados por oficina",
                "passports_by_office",
                data
        );
    }

    /*
     * Pasaportes por tipo de tramite (NUEVO, RENOVACION, EMERGENCIA)
     */
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

        List<PassportsByTypeItem> data = passportOverviewService.getByType(from, to);

        return ApiResponse.ok(
                "Pasaportes agrupados por tipo de trámite",
                "passports_by_type",
                data
        );
    }

    // GET - ALL
    @GetMapping("/events")
    public ApiResponse<Map<String, Object>> searchEvents(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         @RequestParam(defaultValue = "DESC") String sortDir,
                                                         @RequestParam(required = false) String sortBy,

                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String operationType,

                                                         @RequestParam(required = false) String officeId,
                                                         @RequestParam(required = false) String userId,
                                                         @RequestParam(required = false) String channel,
                                                         @RequestParam(required = false) String message,

                                                         @RequestParam(required = false) String reasonCode,

                                                         @RequestParam(required = false)
                                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

                                                         @RequestParam(required = false)
                                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

                                                         Authentication auth) {

        ObjectId tenantId = resolveTenantId(auth);

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Instant from = (fromDate != null)
                ? fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)
                : PassportOverviewService.defaultFrom();

        Instant to = (toDate != null)
                ? toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                : PassportOverviewService.defaultTo();

        var pageResult = passportEventService.searchEvents(
                from,
                to,
                status,
                operationType,
                officeId,
                userId,
                channel,
                message,
                reasonCode,
                page,
                size,
                sortBy,
                sortDir
        );

        Map<String, Object> data = Map.of(
                "items", pageResult.getContent(),
                "page", pageResult.getNumber(),
                "size", pageResult.getSize(),
                "totalElements", pageResult.getTotalElements(),
                "totalPages", pageResult.getTotalPages()
        );

        return ApiResponse.ok(
                "Logs de pasaportes",
                "passport_events_search",
                data
        );
    }

    // GET - ById
    @GetMapping("/events/{id}")
    public ApiResponse<PassportEvent> getEventById(@PathVariable String id,
                                                   Authentication auth) {

        ObjectId tenantId = resolveTenantId(auth);
        ObjectId eventId = new ObjectId(id);

        PassportEvent event = passportEventService
                .findById(tenantId, eventId)
                .orElseThrow(() -> new IllegalArgumentException("Log no encontrado"));

        return ApiResponse.ok(
                "Detalle del log de pasaporte",
                "passport_log_detail",
                event
        );

    }

    @GetMapping("/timeline")
    public ApiResponse<PassportTimelineResponse> getTimeline(
            @RequestParam(required = false) String passportNumber,
            @RequestParam(required = false) String personId,
            @RequestParam(required = false) String requestId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {

        Instant from = null;
        Instant to = null;

        if (fromDate != null) {
            from = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        if (toDate != null) {
            to = toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        }

        PassportTimelineResponse data = timelineService.getTimeline(
                passportNumber,
                personId,
                requestId,
                from,
                to
        );

        return ApiResponse.ok(
                "passports_timeline",
                "Timeline de eventos de pasaporte",
                data
        );

    }



    private ObjectId resolveTenantId(Authentication auth) {

        // 1) Intentar desde el principal (AuthUser)
        if (auth != null && auth.getPrincipal() instanceof AuthUser au) {
            ObjectId tenantId = au.tenantId();
            if (tenantId != null) {
                return tenantId;
            }
        }

        // 2) Fallback: TenantContext
        if (TenantContext.getTenantId() != null) {
            return TenantContext.getTenantId();
        }

        // 3) Si no se pudo resolver, error 401
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "tenant_not_resolved"
        );
    }

}
