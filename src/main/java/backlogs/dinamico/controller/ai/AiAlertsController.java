package backlogs.dinamico.controller.ai;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.model.ai.AiAlertRecord;
import backlogs.dinamico.repository.ai.AiAlertRepository;
import backlogs.dinamico.service.ai.AiAlertOperatorExplainService;
import backlogs.dinamico.service.ai.AiTicketDraftService;
import backlogs.dinamico.service.ai.HourlySummaryService;
import backlogs.dinamico.service.ai.dto.AlertOperatorExplainDto;
import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import backlogs.dinamico.service.ai.dto.TicketDraftDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


import backlogs.dinamico.service.ai.AiAlertContextService;
import backlogs.dinamico.service.ai.dto.AlertContextDto;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;


import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Validated
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/ai/alerts")
@RequiredArgsConstructor
@Tag(name = "AI - Alerts", description = "Historial de alertas operativas generadas por insights.")
public class AiAlertsController {

    private static final String DEFAULT_TZ = "America/Mexico_City";
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final HourlySummaryService hourlySummaryService;
    private final AiAlertRepository alertRepo;
    private final MongoTemplate mongoTemplate;

    private final AiAlertContextService alertContextService;
    private final AiAlertOperatorExplainService operatorExplainService;
    private final AiTicketDraftService ticketDraftService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<?> list(
            Authentication auth, HttpServletRequest req,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String state,        // OPEN|ACKED|RESOLVED
            @RequestParam(required = false) String status,       // OK|WARN|CRIT
            @RequestParam(required = false) String granularity,  // hourly|daily
            @RequestParam(required = false) String from,         // ISO-8601 — filtro de fecha desde
            @RequestParam(required = false) String to,           // ISO-8601 — filtro de fecha hasta
            @RequestParam(defaultValue = DEFAULT_TZ) String tz
    ) {
        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);
        ZoneId zone = safeZone(tz);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Criteria c = Criteria.where("tenantId").is(tenantId);

        // Filtro de fecha — limita el rango de alertas devueltas
        if (StringUtils.hasText(from)) {
            try {
                Instant fromInstant = Instant.parse(from.trim());
                c = c.and("createdAt").gte(fromInstant);
            } catch (Exception ignored) {}
        }
        if (StringUtils.hasText(to)) {
            try {
                Instant toInstant = Instant.parse(to.trim());
                c = c.and("createdAt").lte(toInstant);
            } catch (Exception ignored) {}
        }

        if (StringUtils.hasText(granularity)) {
            c = c.and("granularity").is(granularity.trim().toLowerCase(Locale.ROOT));
        }

        if (StringUtils.hasText(status)) {
            c = c.and("status").is(status.trim().toUpperCase(Locale.ROOT));
        }

        if (StringUtils.hasText(state)) {
            String st = state.trim().toUpperCase(Locale.ROOT);

            // OPEN = state null/absente o "OPEN" (por si luego lo manejas así)
            if ("OPEN".equals(st)) {
                c = new Criteria().andOperator(
                        c,
                        new Criteria().orOperator(
                                Criteria.where("state").exists(false),
                                Criteria.where("state").is(null),
                                Criteria.where("state").is("OPEN")
                        )
                );
            } else {
                c = c.and("state").is(st);
            }
        }

        Query q = new Query(c)
                .with(pageable.getSort())
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize());

        List<AiAlertRecord> records = mongoTemplate.find(q, AiAlertRecord.class, "ai_alerts");
        long total = mongoTemplate.count(new Query(c), AiAlertRecord.class, "ai_alerts");

        List<AiAlertView> views = records.stream()
                .map(r -> toView(r, zone))
                .collect(Collectors.toList());

        Page<AiAlertView> result = new PageImpl<>(views, pageable, total);

        return ApiResponse.ok("Alertas IA", "ai_alerts", result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<?> get(
            Authentication auth, HttpServletRequest req,
            @PathVariable ObjectId id,
            @RequestParam(defaultValue = DEFAULT_TZ) String tz
    ) {
        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);
        ZoneId zone = safeZone(tz);

        AiAlertRecord rec = alertRepo.findById(id)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ai_alert_not_found"));

        return ApiResponse.ok("Alerta IA", "ai_alert", toView(rec, zone));
    }

    @PostMapping("/{id}/ack")
    @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<?> ack(
            Authentication auth, HttpServletRequest req,
            @PathVariable ObjectId id,
            @RequestParam(defaultValue = DEFAULT_TZ) String tz
    ) {
        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);
        ZoneId zone = safeZone(tz);

        AiAlertRecord rec = alertRepo.findById(id)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ai_alert_not_found"));

        // Idempotente: si ya está RESOLVED no lo bajamos a ACKED
        if (!"RESOLVED".equalsIgnoreCase(nvl(rec.getState()))) {
            rec.setState("ACKED");
        }
        if (rec.getAckedAt() == null) rec.setAckedAt(Instant.now());
        if (!StringUtils.hasText(rec.getAckedBy())) rec.setAckedBy(actorEmail(auth));

        alertRepo.save(rec);
        return ApiResponse.ok("Alerta ACK", "ai_alert_ack", toView(rec, zone));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<?> resolve(
            Authentication auth, HttpServletRequest req,
            @PathVariable ObjectId id,
            @RequestParam(defaultValue = DEFAULT_TZ) String tz
    ) {
        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);
        ZoneId zone = safeZone(tz);

        AiAlertRecord rec = alertRepo.findById(id)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ai_alert_not_found"));

        // Resolve normalmente implica que alguien lo vio -> si no estaba ACKED, lo ACKEAMOS
        if (!StringUtils.hasText(rec.getAckedBy())) rec.setAckedBy(actorEmail(auth));
        if (rec.getAckedAt() == null) rec.setAckedAt(Instant.now());

        rec.setState("RESOLVED");
        rec.setResolvedAt(Instant.now());
        rec.setResolvedBy(actorEmail(auth));

        alertRepo.save(rec);
        return ApiResponse.ok("Alerta RESOLVED", "ai_alert_resolve", toView(rec, zone));
    }

    @GetMapping("/{id}/context")
    @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<AlertContextDto> context(
            Authentication auth,
            HttpServletRequest req,
            @PathVariable ObjectId id,
            @RequestParam(defaultValue = "America/Mexico_City") String tz,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit
    ) {
        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);
        AlertContextDto dto = alertContextService.getContext(tenantId, id, tz, limit);
        return ApiResponse.ok("Contexto de alerta", "ai_alert_context", dto);
    }

    @GetMapping("/{id}/explain/operator")
    @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<AlertOperatorExplainDto> explainForOperator(
            Authentication auth, HttpServletRequest req,
            @PathVariable ObjectId id,
            @RequestParam(defaultValue = "America/Mexico_City") String tz,
            @RequestParam(defaultValue = "10") int samples
    ) {
        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);

        AlertOperatorExplainDto dto = operatorExplainService.explain(
                tenantId,
                id,
                tz,
                samples
        );

        return ApiResponse.ok("Explicación para operador", "ai_alert_operator_explain", dto);
    }

    @GetMapping(value = "/{id}/ticket/draft", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<TicketDraftDto> ticketDraft(
            Authentication auht, HttpServletRequest req,
            @PathVariable ObjectId id,
            @RequestParam(defaultValue =  "America/Mexico_City") String tz,
            @RequestParam(defaultValue = "10") int samples
    ) {

        ObjectId tenantId = hourlySummaryService.resolveTenantId(auht, req);
        TicketDraftDto dto = ticketDraftService.draftFromAlert(tenantId, id, tz, samples);

        return ApiResponse.ok("Ticket draft", "ai_alert_ticket_draft", dto);
    }


    // ===================== DTO VIEW (con horas locales) =====================
    @Data
    public static class AiAlertView {
        public String id;
        public String tenantId;

        public String granularity;

        public String windowFromLocal;
        public String windowToLocal;
        public String bucketStartLocal;
        public String createdAtLocal;

        public String status;

        public long total;
        public double errorRate;

        public Map<String, Long> severities;
        public List<SummaryInsightsDto.Alert> alerts;

        public String fingerprint;

        public String state;

        public String ackedAtLocal;
        public String ackedBy;

        public String resolvedAtLocal;
        public String resolvedBy;

        public String tz;
    }

    private AiAlertView toView(AiAlertRecord r, ZoneId zone) {
        AiAlertView v = new AiAlertView();
        v.tz = zone.getId();

        v.id = r.getId() != null ? r.getId().toHexString() : null;
        v.tenantId = r.getTenantId() != null ? r.getTenantId().toHexString() : null;

        v.granularity = r.getGranularity();

        v.windowFromLocal = fmtLocal(r.getWindowFrom(), zone);
        v.windowToLocal = fmtLocal(r.getWindowTo(), zone);
        v.bucketStartLocal = fmtLocal(r.getBucketStart(), zone);
        v.createdAtLocal = fmtLocal(r.getCreatedAt(), zone);

        v.status = r.getStatus();
        v.total = r.getTotal();
        v.errorRate = r.getErrorRate();

        v.severities = r.getSeverities();
        v.alerts = r.getAlerts();

        v.fingerprint = r.getFingerprint();

        // Si viene null, lo interpretamos como OPEN
        v.state = StringUtils.hasText(r.getState()) ? r.getState() : "OPEN";

        v.ackedAtLocal = fmtLocal(r.getAckedAt(), zone);
        v.ackedBy = r.getAckedBy();

        v.resolvedAtLocal = fmtLocal(r.getResolvedAt(), zone);
        v.resolvedBy = r.getResolvedBy();

        return v;
    }

    private static String fmtLocal(Instant t, ZoneId zone) {
        return t == null ? null : t.atZone(zone).format(ISO_OFFSET);
    }

    private static ZoneId safeZone(String tz) {
        try {
            if (!StringUtils.hasText(tz)) return ZoneId.of(DEFAULT_TZ);
            return ZoneId.of(tz.trim());
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TZ);
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    // ===================== HELPER'S =======================
    public String actorEmail(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof backlogs.dinamico.infra.security.AuthUser au) return au.getEmail();
        if (p instanceof org.springframework.security.core.userdetails.UserDetails ud) return ud.getUsername();
        return String.valueOf(p);
    }
}