package backlogs.dinamico.controller.analytics;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.analytics.*;
import backlogs.dinamico.api.dto.catalog.ExecutiveSummaryDto;
import backlogs.dinamico.api.dto.catalog.SystemHealthDto;
import backlogs.dinamico.repository.log.LogEventRepository;
import backlogs.dinamico.service.analytics.*;
import backlogs.dinamico.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Controlador REST para endpoints de analítica avanzada.
 * Transforma logs de auditoría en métricas estratégicas de rendimiento.
 */
@Slf4j
@Tag(name = "Analytics", description = "Analítica estratégica de procesos - KPIs y métricas de conversión")
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "https://dashboard.grupo-santoro.com.mx", allowCredentials = "true")
public class AnalyticsController {

    private final FunnelAnalyticsService funnelAnalyticsService;
    private final ExecutiveAnalyticsService executiveAnalyticsService;
    private final OperationalErrorExplainerService errorExplainerService;
    private final LogEventRepository logEventRepository;
    private final AttendanceAnomalyService attendanceAnomalyService;
    private final AuditReportService auditReportService;
    private final DiagnosticService diagnosticService;
    private final SystemHealthService systemHealthService;

    @Operation(
            summary = "Análisis de Embudo de Conversión (Funnel)",
            description = "Calcula métricas de conversión y abandono para un flujo de proceso específico."
    )
    @GetMapping("/funnel/{systemName}")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<FunnelResponseDto> getFunnelAnalysis(
            Authentication auth,
            @PathVariable String systemName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        try {
            FunnelResponseDto funnel = funnelAnalyticsService.calculateFunnel(auth, systemName, from, to);
            return ApiResponse.ok("Funnel analysis completed", "funnel_analysis", funnel);
        } catch (Exception e) {
            return ApiResponse.ok(
                    "Funnel analysis unavailable",
                    "funnel_analysis_fallback",
                    FunnelResponseDto.builder()
                            .system(systemName)
                            .funnelName("Unavailable")
                            .summary(FunnelSummaryDto.builder().totalStarted(0).totalCompleted(0).globalConversionRate(0.0).build())
                            .steps(List.of())
                            .build()
            );
        }
    }

    @Operation(
            summary = "Listar Sistemas con Funnel Configurado",
            description = "Retorna la lista de sistemas que tienen análisis de funnel configurado y activo."
    )
    @GetMapping("/funnel-systems/available")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<List<String>> getAvailableFunnelSystems() {
        try {
            List<String> systems = funnelAnalyticsService.getAvailableSystems();
            return ApiResponse.ok("Available funnel systems retrieved", "funnel_systems_list", systems);
        } catch (Exception e) {
            return ApiResponse.ok("Available funnel systems unavailable", "funnel_systems_list_fallback", List.of());
        }
    }

    @Operation(
            summary = "Historial Ejecutivo de un caso",
            description = "Devuelve una línea de tiempo pre-digerida en tarjetas operativas para supervisores."
    )
    @GetMapping("/executive-timeline")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<ExecutiveTimelineResponse> getExecutiveTimeline(
            Authentication auth,
            @RequestParam String system,
            @RequestParam String caseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            ExecutiveTimelineResponse timeline = executiveAnalyticsService.executiveTimeline(auth, system, caseId, fromDate, toDate, page, size);
            return ApiResponse.ok("Executive timeline retrieved", "executive_timeline", timeline);
        } catch (Exception e) {
            Instant from = (fromDate != null) ? fromDate.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
            Instant to = (toDate != null) ? toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;
            return ApiResponse.ok(
                    "Executive timeline unavailable",
                    "executive_timeline_fallback",
                    new ExecutiveTimelineResponse(
                            new ExecutiveTimelineResponse.Header(system, caseId, null, null, from, to),
                            List.of(),
                            new PageMeta(page, size, false)
                    )
            );
        }
    }

    @Operation(
            summary = "User Journey de un caso",
            description = "Devuelve el recorrido paso a paso de un caso incluyendo coordenadas geográficas para el mapa."
    )
    @GetMapping("/user-journey/{caseId}")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<UserJourneyResponse> getUserJourney(
            Authentication auth,
            @PathVariable String caseId,
            @RequestParam String system
    ) {
        try {
            UserJourneyResponse journey = executiveAnalyticsService.userJourney(auth, caseId, system);
            return ApiResponse.ok("User journey retrieved", "user_journey", journey);
        } catch (Exception e) {
            return ApiResponse.ok(
                    "User journey unavailable",
                    "user_journey_fallback",
                    new UserJourneyResponse(
                            caseId,
                            system,
                            new UserJourneyResponse.JourneySummary(null, null, "UNKNOWN", 0, 0, 0, null, null, null),
                            List.of()
                    )
            );
        }
    }

    @Operation(
            summary = "Explicar error con IA",
            description = "Genera una explicación amigable de un error técnico bajo demanda."
    )
    @PostMapping("/explain-error/{logId}")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<ErrorExplanationResponse> explainError(
            Authentication auth,
            @PathVariable String logId
    ) {
        try {
            ErrorExplanationResponse response = errorExplainerService.explain(auth, new ObjectId(logId));
            return ApiResponse.ok("Error explanation generated", "explain_error", response);
        } catch (Exception e) {
            return ApiResponse.ok(
                    "Error explanation unavailable",
                    "explain_error_fallback",
                    ErrorExplanationResponse.builder()
                            .logId(logId)
                            .explanation(ErrorExplanation.builder()
                                    .summary("No se pudo generar la explicación en este momento.")
                                    .likelyCause("El servicio de explicación no está disponible.")
                                    .businessImpact("No identificado.")
                                    .recommendedAction("Intente más tarde o consulte los logs técnicos.")
                                    .confidence("LOW")
                                    .build())
                            .generatedAt(Instant.now())
                            .source("NOT_APPLICABLE")
                            .build()
            );
        }
    }

    @Operation(
            summary = "Resumen Ejecutivo",
            description = "Devuelve semáforos de salud por sistema, métricas globales y alertas recientes."
    )
    @GetMapping("/executive-summary")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<ExecutiveSummaryDto> getExecutiveSummary(
            Authentication auth,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate
    ) {
        String effectiveFromStr = StringUtils.hasText(from) ? from : fromDate;
        String effectiveToStr = StringUtils.hasText(to) ? to : toDate;

        Instant fromInstant = parseToInstant(effectiveFromStr, false);
        Instant toInstant = parseToInstant(effectiveToStr, true);

        // OBTENCIÓN DEL TENANT ID DEL CONTEXTO (CORREGIDO)
        ObjectId tenantId = TenantContext.requireTenantId();

        try {
            ExecutiveSummaryDto globalDto = systemHealthService.getExecutiveSummary(tenantId, fromInstant, toInstant);
            return ApiResponse.ok("Executive summary retrieved", "executive_summary", globalDto);
        } catch (Exception e) {
            log.error("Error obteniendo resumen ejecutivo", e);
            return ApiResponse.ok(
                    "Executive summary unavailable",
                    "executive_summary_fallback",
                    new ExecutiveSummaryDto("INACTIVE", 0L, 0.0, 0L)
            );
        }
    }

    @Operation(
            summary = "Salud de Sistemas Monitoreados",
            description = "Devuelve el semáforo de salud, conteo de eventos y porcentaje de error por cada sistema en el rango especificado."
    )
    @GetMapping({"/catalogs/systems-health", "/systems-health"})
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<List<SystemHealthDto>> getSystemsHealth(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate
    ) {
        String effectiveFromStr = StringUtils.hasText(from) ? from : fromDate;
        String effectiveToStr = StringUtils.hasText(to) ? to : toDate;

        Instant fromInstant = parseToInstant(effectiveFromStr, false);
        Instant toInstant = parseToInstant(effectiveToStr, true);

        ObjectId tenantId = TenantContext.requireTenantId();

        List<SystemHealthDto> healthList = systemHealthService.getSystemsHealth(tenantId, fromInstant, toInstant);
        return ApiResponse.ok("Systems health retrieved", "systems_health", healthList);
    }

    @Operation(
            summary = "Diagnóstico técnico",
            description = "Vista consolidada de salud de sistemas y top de fricción."
    )
    @GetMapping("/diagnostics")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<DiagnosticResponse> getDiagnostics(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        try {
            DiagnosticResponse diagnostics = diagnosticService.getDiagnostics(auth, from, to);
            return ApiResponse.ok("Diagnostics retrieved", "diagnostics", diagnostics);
        } catch (Exception e) {
            Instant now = Instant.now();
            return ApiResponse.ok(
                    "Diagnostics unavailable",
                    "diagnostics_fallback",
                    DiagnosticResponse.builder()
                            .from(from != null ? from : now)
                            .to(to != null ? to : now)
                            .systemsHealth(List.of())
                            .topFrictionalEvents(TopFrictionalEventsResponse.builder().date(LocalDate.now()).events(List.of()).build())
                            .build()
            );
        }
    }

    @Operation(
            summary = "Top Eventos de Fricción",
            description = "Devuelve los eventos con isError=true que afectaron a más casos únicos en el periodo."
    )
    @GetMapping("/top-frictional-events")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<TopFrictionalEventsResponse> getTopFrictionalEvents(
            Authentication auth,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(required = false) String system,
            @RequestParam(defaultValue = "5") int limit
    ) {
        Instant fromInstant = parseToInstant(from, false);
        Instant toInstant = parseToInstant(to, true);

        LocalDate effectiveDate = fromInstant != null
                ? fromInstant.atZone(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();
        int safeLimit = Math.min(limit, 10);

        try {
            TopFrictionalEventsResponse ranking = executiveAnalyticsService.topFrictionalEvents(auth, fromInstant, toInstant, system, safeLimit);
            return ApiResponse.ok("Top frictional events retrieved", "top_frictional_events", ranking);
        } catch (Exception e) {
            return ApiResponse.ok(
                    "Top frictional events unavailable",
                    "top_frictional_events_fallback",
                    TopFrictionalEventsResponse.builder().date(effectiveDate).events(List.of()).build()
            );
        }
    }

    @Operation(
            summary = "Auditoría Rápida de Usuario",
            description = "Buscador de usuarios para auditoría rápida sobre los logs de eventos."
    )
    @GetMapping("/users/quick-audit")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<List<UserQuickAuditResponse>> quickAuditUsers(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (query == null || query.trim().length() < 2) {
            return ApiResponse.ok("Quick audit requires at least 2 characters", "quick_audit_empty", List.of());
        }

        try {
            List<UserQuickAuditResponse> users = logEventRepository.quickAuditUsers(query, limit);
            return ApiResponse.ok("Quick audit completed", "quick_audit", users);
        } catch (Exception e) {
            return ApiResponse.ok("Quick audit unavailable", "quick_audit_fallback", List.of());
        }
    }

    @Operation(
            summary = "Anomalías de asistencia",
            description = "Detecta inconsistencias operativas de asistencia."
    )
    @GetMapping("/attendance/anomalies")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<AttendanceAnomaliesResponse> getAttendanceAnomalies(
            @RequestParam(required = false) String system,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        try {
            AttendanceAnomaliesResponse anomalies = attendanceAnomalyService.detect(system, date);
            return ApiResponse.ok("Attendance anomalies computed", "attendance_anomalies", anomalies);
        } catch (Exception e) {
            return ApiResponse.ok(
                    "Attendance anomalies unavailable",
                    "attendance_anomalies_fallback",
                    new AttendanceAnomaliesResponse(
                            system != null ? system : "TRUSTVALUE",
                            date != null ? date.toString() : LocalDate.now().toString(),
                            false,
                            0,
                            List.of(),
                            List.of(),
                            List.of()
                    )
            );
        }
    }

    @Operation(
            summary = "Certificado de auditoría (PDF)",
            description = "Genera un certificado en PDF con dictamen de actividad y sello digital SHA-256."
    )
    @PostMapping("/reports/audit-certificate")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ResponseEntity<byte[]> generateAuditCertificate(
            @Valid @RequestBody AuditCertificateRequest request
    ) {
        try {
            byte[] pdf = auditReportService.generateCertificate(request);
            String filename = "Certificado_Auditoria_" + request.username() + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Método Helper Único de Conversión de Fechas ───────────────────────────

    private Instant parseToInstant(String dateStr, boolean isEnd) {
        if (!StringUtils.hasText(dateStr) || dateStr.equalsIgnoreCase("null") || dateStr.startsWith("1970")) {
            return isEnd ? Instant.now() : Instant.EPOCH;
        }
        try {
            if (dateStr.contains("T")) {
                return Instant.parse(dateStr);
            }
            LocalDate localDate = LocalDate.parse(dateStr);
            return isEnd
                    ? localDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant()
                    : localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            return isEnd ? Instant.now() : Instant.EPOCH;
        }
    }
}