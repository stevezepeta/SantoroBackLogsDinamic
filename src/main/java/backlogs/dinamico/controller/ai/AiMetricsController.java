package backlogs.dinamico.controller.ai;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.model.ai.AiMetricRecord;
import backlogs.dinamico.repository.ai.AiMetricRepository;
import backlogs.dinamico.service.ai.HourlySummaryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Validated
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/ai/metrics")
@RequiredArgsConstructor
@Tag(name = "AI - Metrics", description = "Series de métricas (time-series) para dashboards y detección de anomalías.")
public class AiMetricsController {

    private static final String DEFAULT_TZ = "America/Mexico_City";
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final HourlySummaryService hourlySummaryService;
    private final AiMetricRepository metricRepo;

    /**
     * Serie para graficar y para baseline/anomalías.
     * Ejemplo:
     *  /api/ai/metrics/series?granularity=daily&system=TICKETS&days=30&tz=America/Mexico_City
     */
    @GetMapping("/series")
    @PreAuthorize("hasAnyAuthority('PERM_LOG_READ', 'ORG_OWNER', 'ORG_ADMIN')")
    public ApiResponse<?> series(
            Authentication auth, HttpServletRequest req,
            @RequestParam String granularity,                // daily|hourly
            @RequestParam String system,                     // "TICKETS", "LECTOR GRUPO SANTORO", etc.
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days,
            @RequestParam(defaultValue = "168") @Min(1) @Max(720) int hours,
            @RequestParam(defaultValue = DEFAULT_TZ) String tz
    ) {
        ObjectId tenantId = hourlySummaryService.resolveTenantId(auth, req);
        ZoneId zone = safeZone(tz);

        String g = normGranularity(granularity);
        String sys = normSystem(system);

        Instant to = floorNowToBucket(zone, g);
        Instant from = "hourly".equals(g)
                ? to.minusSeconds(hours * 3600L)
                : to.minusSeconds(days * 86400L);

        List<AiMetricRecord> recs = metricRepo
                .findByTenantIdAndGranularityAndSystemAndBucketStartBetweenOrderByBucketStartAsc(
                        tenantId, g, sys, from, to
                );

        SeriesResponse out = new SeriesResponse();
        out.tz = zone.getId();
        out.granularity = g;
        out.system = sys;
        out.from = from.toString();
        out.to = to.toString();
        out.fromLocal = fmtLocal(from, zone);
        out.toLocal = fmtLocal(to, zone);

        out.points = new ArrayList<>();
        if (recs != null) {
            for (AiMetricRecord r : recs) {
                if (r == null) continue;
                SeriesPoint p = new SeriesPoint();
                p.bucketStart = (r.getBucketStart() == null) ? null : r.getBucketStart().toString();
                p.bucketStartLocal = fmtLocal(r.getBucketStart(), zone);
                p.total = r.getTotal();
                p.errorCount = r.getErrorCount();
                p.errorRate = r.getErrorRate();
                out.points.add(p);
            }
        }

        // meta opcional (útil para debug)
        out.count = out.points.size();

        return ApiResponse.ok("Serie metrics IA", "ai_metrics_series", out);
    }

    // ================= DTOs =================
    @Data
    public static class SeriesResponse {
        public String tz;
        public String granularity;
        public String system;

        public String from;      // UTC ISO instant
        public String to;        // UTC ISO instant
        public String fromLocal; // ISO_OFFSET in tz
        public String toLocal;

        public int count;
        public List<SeriesPoint> points;
    }

    @Data
    public static class SeriesPoint {
        public String bucketStart;       // UTC
        public String bucketStartLocal;  // local tz
        public long total;
        public long errorCount;
        public double errorRate;
    }

    // ================= Helpers =================
    private static String normGranularity(String g) {
        String x = (g == null) ? "" : g.trim().toLowerCase(Locale.ROOT);
        if (!"daily".equals(x) && !"hourly".equals(x)) {
            throw new IllegalArgumentException("granularity must be daily|hourly");
        }
        return x;
    }

    private static String normSystem(String s) {
        if (!StringUtils.hasText(s)) throw new IllegalArgumentException("system is required");
        return s.trim();
    }

    private static ZoneId safeZone(String tz) {
        try {
            if (!StringUtils.hasText(tz)) return ZoneId.of(DEFAULT_TZ);
            return ZoneId.of(tz.trim());
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TZ);
        }
    }

    private static String fmtLocal(Instant t, ZoneId zone) {
        return (t == null) ? null : t.atZone(zone).format(ISO_OFFSET);
    }

    /**
     * Para que la ventana sea consistente con tus buckets:
     * - hourly: floorToHour(now) en tz y convertir a Instant
     * - daily: floorToDay(now) en tz y convertir a Instant
     */
    private static Instant floorNowToBucket(ZoneId zone, String granularity) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        if ("hourly".equals(granularity)) {
            ZonedDateTime floored = now.withMinute(0).withSecond(0).withNano(0);
            return floored.toInstant();
        } else {
            ZonedDateTime floored = now.toLocalDate().atStartOfDay(zone);
            return floored.toInstant();
        }
    }
}