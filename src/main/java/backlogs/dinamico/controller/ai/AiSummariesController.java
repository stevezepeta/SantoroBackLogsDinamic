package backlogs.dinamico.controller.ai;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.service.ai.*;
import backlogs.dinamico.service.ai.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Validated
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(value = "/api/ai/summaries", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(
        name = "AI - Summaries",
        description = """
Resúmenes agregados **localmente** (sin IA externa).

Incluye:
- **Hourly Summary**: buckets por hora (usa `eventTime`)
- **Daily Summary**: buckets por día
- **Insights operativos**: highlights, warnings y recomendaciones
"""
)
public class AiSummariesController {

    private static final String DEFAULT_TZ = "America/Mexico_City";
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final HourlySummaryService hourlySummaryService;
    private final DailySummaryService dailySummaryService;
    private final SummaryInsightsService summaryInsightsService;

    private final DailyManagerBriefService dailyManagerBriefService;
    private final AiDailyManagerTicketDraftService dailyManagerTicketDraftService;

    // -------------------- HOURLY --------------------

    @Operation(
            summary = "Resumen por hora",
            description = """
            Regresa un resumen agregado por **hora** usando `eventTime`.
            
            Parámetros:
            - `hours`: ventana en horas (máx 31 días)
            - `tz`: zona horaria para truncar por hora (ej. America/Mexico_City)
            - `from/to`: opcional. Si se mandan, se usa ese rango.
            """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "bad_request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "forbidden")
    })
    @GetMapping("/hourly")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<HourlySummaryDto> hourlySummary(
            Authentication auth,
            HttpServletRequest req,

            @RequestParam(defaultValue = "24")
            @Min(1) @Max(24 * 31)
            @Schema(description = "Ventana en horas (máx 744)", example = "24")
            int hours,

            @RequestParam(defaultValue = DEFAULT_TZ)
            @Schema(description = "Zona horaria para truncar por hora", example = "America/Mexico_City")
            String tz,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Schema(description = "Inicio (UTC ISO-8601). Si se manda, sobrescribe hours.", example = "2026-02-10T00:00:00Z")
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Schema(description = "Fin (UTC ISO-8601).", example = "2026-02-11T00:00:00Z")
            Instant to
    ) {
        ObjectId tenantId = resolveTenantId(auth, req);
        HourlySummaryDto data = hourlySummaryService.buildHourlySummary(tenantId, hours, tz, from, to);

        ZoneId zone = safeZone(tz);
        applyLocalWindow(data, zone);

        return ApiResponse.ok("Resumen por hora", "ai_hourly_summary", data);
    }

    // -------------------- DAILY --------------------

    @Operation(
            summary = "Resumen diario",
            description = """
            Regresa un resumen agregado por **día** usando `eventTime`.
            
            Parámetros:
            - `days`: ventana en días (máx 365)
            - `tz`: zona horaria para truncar por día
            - `from/to`: opcional. Si se mandan, se usa ese rango.
            """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "bad_request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "forbidden")
    })
    @GetMapping("/daily")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<DailySummaryDto> dailySummary(
            Authentication auth,
            HttpServletRequest req,

            @RequestParam(defaultValue = "7")
            @Min(1) @Max(365)
            @Schema(description = "Ventana en días (máx 365)", example = "7")
            int days,

            @RequestParam(defaultValue = DEFAULT_TZ)
            @Schema(description = "Zona horaria para truncar por día", example = "America/Mexico_City")
            String tz,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Schema(description = "Inicio (UTC ISO-8601). Si se manda, sobrescribe days.", example = "2026-02-01T00:00:00Z")
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @Schema(description = "Fin (UTC ISO-8601).", example = "2026-02-11T00:00:00Z")
            Instant to
    ) {
        ObjectId tenantId = resolveTenantId(auth, req);
        DailySummaryDto dto = dailySummaryService.buildDailySummary(tenantId, days, tz, from, to);

        ZoneId zone = safeZone(tz);
        applyLocalWindow(dto, zone);

        return ApiResponse.ok("Resumen diario", "ai_daily_summary", dto);
    }

    // -------------------- INSIGHTS (HOURLY) --------------------

    @Operation(
            summary = "Insights por hora",
            description = "Genera insights operativos a partir del resumen por hora (highlights/warnings/recommendations)."
    )
    @GetMapping({"/hourly/insights", "/insights/hourly"})
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<SummaryInsightsDto> hourlyInsights(
            Authentication auth,
            HttpServletRequest req,

            @RequestParam(defaultValue = "24")
            @Min(1) @Max(24 * 31)
            int hours,

            @RequestParam(defaultValue = DEFAULT_TZ)
            String tz,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        ObjectId tenantId = resolveTenantId(auth, req);

        HourlySummaryDto summary = hourlySummaryService.buildHourlySummary(tenantId, hours, tz, from, to);
        SummaryInsightsDto insights = summaryInsightsService.fromHourly(tenantId, summary);

        ZoneId zone = safeZone(tz);
        applyLocalWindow(insights, zone);

        return ApiResponse.ok("Insights por hora", "ai_hourly_insights", insights);
    }

    // -------------------- INSIGHTS (DAILY) --------------------

    @Operation(
            summary = "Insights diario",
            description = "Genera insights operativos a partir del resumen diario (highlights/warnings/recommendations)."
    )
    @GetMapping({"/daily/insights", "/insights/daily"})
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<SummaryInsightsDto> dailyInsights(
            Authentication auth,
            HttpServletRequest req,

            @RequestParam(defaultValue = "7")
            @Min(1) @Max(365)
            int days,

            @RequestParam(defaultValue = DEFAULT_TZ)
            String tz,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        ObjectId tenantId = resolveTenantId(auth, req);

        DailySummaryDto summary = dailySummaryService.buildDailySummary(tenantId, days, tz, from, to);
        SummaryInsightsDto insights = summaryInsightsService.fromDaily(tenantId, summary);

        ZoneId zone = safeZone(tz);
        applyLocalWindow(insights, zone);

        return ApiResponse.ok("Insights diario", "ai_daily_insights", insights);
    }

    // -------------------- DAILY MANAGER ---------------
    @Operation(
            summary = "Resumen diario (gerencia)",
            description = """
            Brief ejecutivo en lenguaje natural (plantillas + reglas) para gerencia.
            Por defecto resume el último día completo (en tz).
            """
    )
    @GetMapping({"/daily/manager", "/reports/daily-manager"})
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<DailyManagerBriefDto> dailyManagerBrief(
            Authentication auth,
            HttpServletRequest req,
            @RequestParam(defaultValue = DEFAULT_TZ) String tz,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        ObjectId tenantId = resolveTenantId(auth, req);
        DailyManagerBriefDto dto = dailyManagerBriefService.build(tenantId, tz, from, to);
        return ApiResponse.ok("Resumen diario (gerencia)", "ai_daily_manager_brief", dto);
    }

    @GetMapping("/daily/ticket/draft")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<TicketDraftDto> dailyTicketDraft(
            Authentication auth,
            HttpServletRequest req,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = DEFAULT_TZ) String tz,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        ObjectId tenantId = resolveTenantId(auth, req);

        TicketDraftDto dto = dailyManagerTicketDraftService.draft(tenantId, days, tz, from, to);

        return ApiResponse.ok("Ticket draft diario (manager)", "ai_daily_ticket_draft", dto);
    }

    // -------------------- helpers --------------------

    private ObjectId resolveTenantId(Authentication auth, HttpServletRequest req) {
        return hourlySummaryService.resolveTenantId(auth, req);
    }

    private static ZoneId safeZone(String tz) {
        try {
            if (!StringUtils.hasText(tz)) return ZoneId.of(DEFAULT_TZ);
            return ZoneId.of(tz.trim());
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TZ);
        }
    }

    private static String fmtLocal(String isoInstant, ZoneId zone) {
        if (!StringUtils.hasText(isoInstant)) return null;
        try {
            return Instant.parse(isoInstant).atZone(zone).format(ISO_OFFSET);
        } catch (Exception e) {
            return null;
        }
    }

    private static void applyLocalWindow(HourlySummaryDto dto, ZoneId zone) {
        if (dto == null) return;
        dto.fromLocal = fmtLocal(dto.from, zone);
        dto.toLocal = fmtLocal(dto.to, zone);
    }

    private static void applyLocalWindow(DailySummaryDto dto, ZoneId zone) {
        if (dto == null) return;
        dto.fromLocal = fmtLocal(dto.from, zone);
        dto.toLocal = fmtLocal(dto.to, zone);
    }

    private static void applyLocalWindow(SummaryInsightsDto dto, ZoneId zone) {
        if (dto == null) return;
        dto.fromLocal = fmtLocal(dto.from, zone);
        dto.toLocal = fmtLocal(dto.to, zone);
    }
}
