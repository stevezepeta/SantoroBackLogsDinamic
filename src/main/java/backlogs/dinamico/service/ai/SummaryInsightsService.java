package backlogs.dinamico.service.ai;

import backlogs.dinamico.model.ai.AiAlertRecord;
import backlogs.dinamico.repository.ai.AiAlertRepository;
import backlogs.dinamico.service.ai.dto.DailySummaryDto;
import backlogs.dinamico.service.ai.dto.HourlySummaryDto;
import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SummaryInsightsService {

    private final AiAlertRepository alertRepo;

    // thresholds (puedes moverlos a application.yml después)
    private static final double ERR_WARN = 0.20; // 20%
    private static final double ERR_CRIT = 0.50; // 50%

    private static final double DOMINANCE_WARN = 0.80; // 80% del total en 1 system
    private static final long   SPIKE_MIN_ABS = 200;   // mínimo absoluto para considerar spike
    private static final double SPIKE_WARN_X  = 2.0;   // 2x promedio
    private static final double SPIKE_CRIT_X  = 3.0;   // 3x promedio

    // ==================== PUBLIC API ====================

    public SummaryInsightsDto fromHourly(HourlySummaryDto s) {
        SummaryInsightsDto out = baseFromHourly(s);
        computeOperationalAlerts(out, s.buckets, true);
        // No persistimos sin tenant
        return out;
    }

    public SummaryInsightsDto fromDaily(DailySummaryDto s) {
        SummaryInsightsDto out = baseFromDaily(s);
        computeOperationalAlerts(out, s.buckets, false);
        // No persistimos sin tenant
        return out;
    }

    public SummaryInsightsDto fromHourly(ObjectId tenantId, HourlySummaryDto s) {
        SummaryInsightsDto out = baseFromHourly(s);
        computeOperationalAlerts(out, s.buckets, true);
        persistIfNeeded(tenantId, out, s.buckets, true);
        return out;
    }

    public SummaryInsightsDto fromDaily(ObjectId tenantId, DailySummaryDto s) {
        SummaryInsightsDto out = baseFromDaily(s);
        computeOperationalAlerts(out, s.buckets, false);
        persistIfNeeded(tenantId, out, s.buckets, false);
        return out;
    }

    // ==================== BASE INSIGHTS ====================
    private SummaryInsightsDto baseFromHourly(HourlySummaryDto s) {

        SummaryInsightsDto out = new SummaryInsightsDto();
        out.granularity = "hourly";
        out.tz = (s != null && s.tz != null) ? s.tz : "America/Mexico_City";
        out.from = (s != null) ? s.from : null;
        out.to   = (s != null) ? s.to   : null;

        // Local time en response (para UI)
        ZoneId zone = safeZone(out.tz);
        out.fromLocal = toLocal(parseInstantSafe(out.from), zone);
        out.toLocal   = toLocal(parseInstantSafe(out.to), zone);

        out.hours = (s != null) ? s.hours : null;
        out.days = null;
        out.buckets = (s == null || s.buckets == null) ? 0 : s.buckets.size();

        long total = 0;
        int active = 0;

        Map<String, Long> sevSum = new HashMap<>();

        // acumuladores para top global del rango
        Map<String, Long> sysRange = new HashMap<>();
        Map<String, Long> typeRange = new HashMap<>();
        Map<String, Long> statusRange = new HashMap<>();
        Map<String, Long> outcomeRange = new HashMap<>();
        Map<String, Long> errRange = new HashMap<>();

        long errTotal = 0; // para "Errores detectados"

        if (s != null && s.buckets != null) {
            for (var b : s.buckets) {
                if (b == null) continue;

                total += b.total;
                if (b.total > 0) active++;

                // severities sum
                if (b.severities != null) {
                    b.severities.forEach((k, v) -> {
                        String kk = normKey(k);
                        if (kk != null) sevSum.merge(kk, safeLong(v), Long::sum);
                    });
                }

                // errTotal por bucket (tu fuente confiable)
                errTotal += Math.max(0, b.errorTotal);

                // Acumular tops por bucket (range)
                mergeTopItemMap(sysRange, b.topSystems);
                mergeTopItemMap(typeRange, b.topEventTypes);
                mergeTopItemMap(statusRange, b.topStatus);
                mergeTopItemMap(outcomeRange, b.topOutcome);
                mergeTopErrorMap(errRange, b.topErrors);
            }
        }

        out.total = total;
        out.activeBuckets = active;
        out.severities = sevSum;

        // fallback por si errorTotal no viene
        if (errTotal == 0 && out.severities != null) {
            errTotal = out.severities.getOrDefault("ERROR", 0L) + out.severities.getOrDefault("FATAL", 0L);
        }

        out.errorRate = (out.total <= 0) ? 0.0 : ((double) errTotal / (double) out.total);

        // tops del último bucket activo (operativo)
        var last = lastActiveBucketHourly(s == null ? null : s.buckets);
        out.topSystems    = (last != null) ? mapTopItems(last.topSystems)    : new ArrayList<>();
        out.topEventTypes = (last != null) ? mapTopItems(last.topEventTypes) : new ArrayList<>();
        out.topStatus     = (last != null) ? mapTopItems(last.topStatus)     : new ArrayList<>();
        out.topOutcome    = (last != null) ? mapTopItems(last.topOutcome)    : new ArrayList<>();
        out.topErrors     = (last != null) ? mapTopErrors(last.topErrors)    : new ArrayList<>();

        // top globales del rango (gerencial)
        out.topSystemsRange    = toTopItems(sysRange, 5);
        out.topEventTypesRange = toTopItems(typeRange, 5);
        out.topStatusRange     = toTopItems(statusRange, 5);
        out.topOutcomeRange    = toTopItems(outcomeRange, 5);
        out.topErrorsRange     = toTopErrors(errRange, 5);

        out.highlights = new ArrayList<>();
        out.warnings = new ArrayList<>();
        out.recommendations = new ArrayList<>();

        // -------------------- HIGHLIGHTS (PRO) --------------------

        out.highlights.add("Total eventos: " + total + " en " + active + " buckets activos.");

        // 1) Top system rango (gerente)
        if (out.topSystemsRange != null && !out.topSystemsRange.isEmpty()) {
            var t = out.topSystemsRange.get(0);
            out.highlights.add("Top system (rango): " + t.name + " (" + t.count + ").");
        }

        // 2) Top system último bucket (operador)
        if (out.topSystems != null && !out.topSystems.isEmpty()) {
            var t = out.topSystems.get(0);
            out.highlights.add("Top system (último bucket): " + t.name + " (" + t.count + ").");
        }

        // 3) Errores absolutos + porcentaje
        out.highlights.add("Errores detectados: " + errTotal + " (" + pct(out.errorRate) + ").");

        // 4) Top error del rango (si existe)
        if (out.topErrorsRange != null && !out.topErrorsRange.isEmpty()) {
            var e = out.topErrorsRange.get(0);
            out.highlights.add("Top error (rango): \"" + safeSnippet(e.key) + "\" (" + e.count + ").");
        }

        return out;
    }

    private SummaryInsightsDto baseFromDaily(DailySummaryDto s) {
        SummaryInsightsDto out = new SummaryInsightsDto();
        out.granularity = "daily";
        out.tz = s.tz;
        out.from = s.from;
        out.to = s.to;
        out.days = s.days;
        out.hours = null;
        out.buckets = (s.buckets == null) ? 0 : s.buckets.size();

        long total = 0;
        int active = 0;
        Map<String, Long> sevSum = new HashMap<>();

        // acumuladores para tops globales del rango
        Map<String, Long> sysRange = new HashMap<>();
        Map<String, Long> typeRange = new HashMap<>();
        Map<String, Long> statusRange = new HashMap<>();
        Map<String, Long> outcomeRange = new HashMap<>();
        Map<String, Long> errRange = new HashMap<>();

        if (s.buckets != null) {
            for (var b : s.buckets) {
                if (b == null) continue;

                total += b.total;
                if (b.total > 0) active++;

                if (b.severities != null) {
                    b.severities.forEach((k, v) -> {
                        if (k != null && !k.trim().isEmpty()) {
                            sevSum.merge(k, v, Long::sum);
                        }
                    });
                }

                // acumular tops por bucket -> top globales del rango
                if (b.topSystems != null) {
                    for (var it : b.topSystems) {
                        if (it != null && it.name != null && !it.name.trim().isEmpty()) {
                            sysRange.merge(it.name, it.count, Long::sum);
                        }
                    }
                }
                if (b.topEventTypes != null) {
                    for (var it : b.topEventTypes) {
                        if (it != null && it.name != null && !it.name.trim().isEmpty()) {
                            typeRange.merge(it.name, it.count, Long::sum);
                        }
                    }
                }
                if (b.topStatus != null) {
                    for (var it : b.topStatus) {
                        if (it != null && it.name != null && !it.name.trim().isEmpty()) {
                            statusRange.merge(it.name, it.count, Long::sum);
                        }
                    }
                }
                if (b.topOutcome != null) {
                    for (var it : b.topOutcome) {
                        if (it != null && it.name != null && !it.name.trim().isEmpty()) {
                            outcomeRange.merge(it.name, it.count, Long::sum);
                        }
                    }
                }
                if (b.topErrors != null) {
                    for (var e : b.topErrors) {
                        if (e != null && e.key != null && !e.key.trim().isEmpty()) {
                            errRange.merge(e.key, e.count, Long::sum);
                        }
                    }
                }
            }
        }

        out.total = total;
        out.activeBuckets = active;
        out.severities = sevSum;

        long errTotal = 0;
        if (s.buckets != null) {
            for (var b : s.buckets) {
                if (b == null) continue;
                errTotal += Math.max(0, b.errorTotal);
            }
        }

        // falback por si alguna razon no viene el errorTotal
        if (errTotal == 0 && out.severities != null) {
            errTotal = out.severities.getOrDefault("ERROR", 0L) + out.severities.getOrDefault("FATAL", 0L);
        }

        out.errorRate = (out.total <= 0) ? 0.0 : ((double) errTotal / (double) out.total);

        var last = lastActiveBucketDaily(s.buckets);
        out.topSystems    = (last != null) ? mapTopItems(last.topSystems)    : new ArrayList<>();
        out.topEventTypes = (last != null) ? mapTopItems(last.topEventTypes) : new ArrayList<>();
        out.topStatus     = (last != null) ? mapTopItems(last.topStatus)     : new ArrayList<>();
        out.topOutcome    = (last != null) ? mapTopItems(last.topOutcome)    : new ArrayList<>();
        out.topErrors     = (last != null) ? mapTopErrors(last.topErrors)    : new ArrayList<>();

        // top globales del rango
        out.topSystemsRange = sysRange.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> new SummaryInsightsDto.TopItem(e.getKey(), e.getValue()))
                .toList();

        out.topEventTypesRange = typeRange.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> new SummaryInsightsDto.TopItem(e.getKey(), e.getValue()))
                .toList();

        out.topStatusRange = statusRange.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> new SummaryInsightsDto.TopItem(e.getKey(), e.getValue()))
                .toList();

        out.topOutcomeRange = outcomeRange.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> new SummaryInsightsDto.TopItem(e.getKey(), e.getValue()))
                .toList();

        out.topErrorsRange = errRange.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> new SummaryInsightsDto.TopError(e.getKey(), e.getValue()))
                .toList();

        out.highlights = new ArrayList<>();
        out.warnings = new ArrayList<>();
        out.recommendations = new ArrayList<>();

        out.highlights.add("Total eventos: " + total + " en " + active + " buckets activos.");

        // highlight gerencial: Top system global del rango
        if (out.topSystemsRange != null && !out.topSystemsRange.isEmpty()) {
            out.highlights.add("Top system (rango): " + out.topSystemsRange.get(0).name + " (" + out.topSystemsRange.get(0).count + ").");
        } else if (!out.topSystems.isEmpty()) {
            // fallback a operativo
            out.highlights.add("Top system (ultimo bucket): " + out.topSystems.get(0).name + " (" + out.topSystems.get(0).count + ").");
        }

        out.highlights.add("Error rate: " + String.format(Locale.ROOT, "%.2f%%", out.errorRate * 100.0));

        return out;
    }

    // ==================== ALERTS LOGIC ====================
    private void computeOperationalAlerts(SummaryInsightsDto out, List<?> buckets, boolean hourly) {

        // Asegurando listas mutables
        out.highlights = mutable(out.highlights);
        out.warnings = mutable(out.warnings);
        out.recommendations = mutable(out.recommendations);

        List<SummaryInsightsDto.Alert> alerts = new ArrayList<>();

        // ------- Contexto para meta ---------
        String topSystemRange = firstName(out.topSystemsRange);
        Long topSystemRangeCount = firstCount(out.topSystemsRange);

        String topErrorRange = firstKey(out.topErrorsRange);
        Long topErrorRangeCount = firstCountErr(out.topErrorsRange);

        // errorCount real
        long errorCount = hourly
                ? sumErrorTotalHourly(castHourly(buckets))
                : sumErrorTotalDaily(castDaily(buckets));

        // fallback
        if (errorCount == 0 && out.severities != null) {
            errorCount = out.severities.getOrDefault("ERROR", 0L) + out.severities.getOrDefault("FATAL", 0L);
        }

        // No data
        if (out.total == 0) {
            alerts.add(new SummaryInsightsDto.Alert(
                    "NO_DATA", "WARN",
                    "No hay eventos en el rango seleccionado.",
                    null, null,
                    meta(
                            "granularity", out.granularity,
                            "from", out.from,
                            "to", out.to
                    )
            ));
            out.warnings.add("No hay data: revisa si el sistema esta enviando logs o si el rango es correcto.");
        }

        // High error rate
        if (out.errorRate >= ERR_CRIT) {
            alerts.add(new SummaryInsightsDto.Alert(
                    "HIGH_ERROR_RATE", "CRIT",
                    "Error rate critico (" + pct(out.errorRate) + ").",
                    null, null,
                    meta(
                            "errorRate", out.errorRate,
                            "errorCount", errorCount,
                            "total", out.total,
                            "threshold", ERR_CRIT,
                            "topSystemRange", topSystemRange,
                            "topErrorRange", topErrorRange
                    )
            ));
            out.warnings.add("Error rate CRITICO: " + pct(out.errorRate) + " (" + errorCount + " errores).");
            out.recommendations.add("Revisar topErrors y correlacion (requestId/traceId). Escalar si afecta operacion");
        } else if (out.errorRate >= ERR_WARN) {
            alerts.add( new SummaryInsightsDto.Alert(
                    "HIGH_ERROR_RATE", "WARN",
                    "Error rate elevado (" + pct(out.errorRate) + ").",
                    null, null,
                    meta(
                        "errorRate", out.errorRate,
                        "errorCount", errorCount,
                        "total", out.total,
                        "threshold", ERR_WARN,
                        "topSystemRange", topSystemRange,
                        "topErrorRange", topErrorRange
                    )
            ));
            out.warnings.add("Error rate alto: " + pct(out.errorRate) + " (" + errorCount + " errores).");
            out.recommendations.add("Revisar si hubo deploy/cambio reciente. Ver topErrorsRange y topSystemsRange.");
        }

        // System dominance
        SummaryInsightsDto.TopItem dom = firstNonEmpty(out.topSystemsRange);
        if (dom == null) dom = firstNonEmpty(out.topSystems);

        if (out.total > 0 && dom != null && dom.count > 0) {
            double share = (double) dom.count / (double) out.total;
            if (share >= DOMINANCE_WARN) {
                alerts.add(new SummaryInsightsDto.Alert(
                        "SYSTEM_DOMINANCE", "INFO",
                        "Un solo system domina el trafico: " + dom.name + " (" + String.format(Locale.ROOT, "%.0f%%", share * 100.0) + ").",
                        null, null,
                        meta(
                                "system", dom.name,
                                "systemCount", dom.count,
                                "share", share,
                                "total", out.total
                        )
                ));
                out.highlights.add("Dominancia: " + dom.name + " concentra" + String.format(Locale.ROOT, "%.0f%%", share * 100.0) + " del trafico.");
            }
        }

        // Volumen spike
        if (buckets != null && !buckets.isEmpty()) {

            long lastTotal = hourly
                    ? lastActiveTotalHourly(castHourly(buckets))
                    : lastActiveTotalDaily(castDaily(buckets));

            double avg = hourly
                    ? avgActiveTotalHourly(castHourly(buckets))
                    : avgActiveTotalDaily(castDaily(buckets));

            if (lastTotal > 0 && avg > 0) {
                double factor = (double) lastTotal / avg;

                String[] bucketTimes = hourly
                        ? lastActiveTimeHourly(castHourly(buckets))
                        : lastActiveTimeDaily(castDaily(buckets));

                // meta enriquecida
                Map<String, Object> spikeMeta = meta(
                        "lastTotal", lastTotal,
                        "avg", avg,
                        "factor", factor,
                        "totalWindow", out.total,
                        "errorRate", out.errorRate,
                        "errorCount", errorCount,
                        "topSystemRange", topSystemRange,
                        "topErrorRange", topErrorRangeCount,
                        "topErrorRangeCount", topErrorRangeCount
                    );

                if (lastTotal >= SPIKE_MIN_ABS && factor >= SPIKE_CRIT_X) {
                    alerts.add(new SummaryInsightsDto.Alert(
                            "VOLUMEN_SPIKE", "CRIT",
                            "Spike critico de volumen: " + lastTotal + " (" + String.format(Locale.ROOT, "%.1fx", factor) + " del promedio).",
                            bucketTimes[0], bucketTimes[1],
                            spikeMeta
                    ));
                    out.warnings.add("Spike CRITICO: " + lastTotal + " eventos en el ultimo bucket activo.");
                    out.recommendations.add("Revisar loop/reintentos masivos/integracion duplicada. Ver TopSystemRange y topErrorRange.");
                } else if (lastTotal >= SPIKE_MIN_ABS && factor >= SPIKE_WARN_X) {
                    alerts.add(new SummaryInsightsDto.Alert(
                            "VOLUMEN_SPIKE", "WARN",
                            "Spike de volumen: " + lastTotal + " (" + String.format(Locale.ROOT, "%.1fx", factor) + " del promedio).",
                            bucketTimes[0], bucketTimes[1],
                            spikeMeta
                    ));
                    out.warnings.add("Spike: " + lastTotal + " eventos en el ultimo bucket activo.");
                    out.recommendations.add("Validar cambios recientes (deploy/config) y revisar topSystem/topErrors del bucket.");
                }
            }
        }
        out.alerts = alerts;
        out.status = deriveStatus(alerts);
    }

    private String safeSnippet(String s) {
        if (s == null) return null;
        String x = s.trim();
        return x.length() <= 140 ? x : x.substring(0, 140) + "...";
    }

    // ==================== PERSIST ====================
    private void persistIfNeeded(ObjectId tenantId, SummaryInsightsDto out, List<?> buckets, boolean hourly) {
        if (tenantId == null) return;

        // Persistimos SOLO si hay WARN/CRIT
        boolean hasImportant = out.alerts != null && out.alerts.stream().anyMatch(a -> !"INFO".equals(a.level));
        if (!hasImportant) return;

        Instant bucketStart = null;
        if (hourly) {
            var last = lastActiveBucketHourly(castHourly(buckets));
            if (last != null && last.hourStart != null) bucketStart = Instant.parse(last.hourStart);
        } else {
            var last = lastActiveBucketDaily(castDaily(buckets));
            if (last != null && last.dayStart != null) bucketStart = Instant.parse(last.dayStart);
        }

        // fingerprint para dedupe
        String fp = sha1(out.granularity + "|" + out.from + "|" + out.to + "|" + out.status + "|" +
                (bucketStart == null ? "-" : bucketStart.toString()) + "|" +
                (out.alerts == null ? "-" : out.alerts.stream().map(a -> a.type + ":" + a.level).collect(Collectors.joining(",")))
        );

        if (alertRepo.findFirstByTenantIdAndFingerprint(tenantId, fp).isPresent()) return;

        AiAlertRecord rec = new AiAlertRecord();
        rec.setTenantId(tenantId);
        rec.setGranularity(out.granularity);
        rec.setWindowFrom(Instant.parse(out.from));
        rec.setWindowTo(Instant.parse(out.to));
        rec.setBucketStart(bucketStart);
        rec.setCreatedAt(Instant.now());
        rec.setStatus(out.status);
        rec.setTotal(out.total);
        rec.setErrorRate(out.errorRate);
        rec.setSeverities(out.severities);
        rec.setAlerts(out.alerts);
        rec.setFingerprint(fp);

        alertRepo.save(rec);
    }

    // ==================== HELPERS ====================
    private String deriveStatus(List<SummaryInsightsDto.Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) return "OK";
        if (alerts.stream().anyMatch(a -> "CRIT".equals(a.level))) return "CRIT";
        if (alerts.stream().anyMatch(a -> "WARN".equals(a.level))) return "WARN";
        return "OK";
    }

    private String pct(double v) {
        return String.format(Locale.ROOT, "%.2f%%", v * 100.0);
    }

    private String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] out = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    // ----- map helpers (DTO -> Insights DTO) -----
    private ArrayList<SummaryInsightsDto.TopItem> mapTopItems(List<?> items) {
        if (items == null) return new ArrayList<>();
        return items.stream().map(x -> {
                    if (x instanceof HourlySummaryDto.TopItem t) return new SummaryInsightsDto.TopItem(t.name, t.count);
                    if (x instanceof DailySummaryDto.TopItem t)  return new SummaryInsightsDto.TopItem(t.name, t.count);
                    return null;
                }).filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ArrayList<SummaryInsightsDto.TopError> mapTopErrors(List<?> items) {
        if (items == null) return new ArrayList<>();
        return items.stream().map(x -> {
                    if (x instanceof HourlySummaryDto.TopError e) return new SummaryInsightsDto.TopError(e.key, e.count);
                    if (x instanceof DailySummaryDto.TopError e)  return new SummaryInsightsDto.TopError(e.key, e.count);
                    return null;
                }).filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // ----- casting helpers -----
    @SuppressWarnings("unchecked")
    private List<HourlySummaryDto.Bucket> castHourly(List<?> buckets) {
        return (List<HourlySummaryDto.Bucket>) buckets;
    }

    @SuppressWarnings("unchecked")
    private List<DailySummaryDto.Bucket> castDaily(List<?> buckets) {
        return (List<DailySummaryDto.Bucket>) buckets;
    }

    // ----- last active bucket helpers -----
    private HourlySummaryDto.Bucket lastActiveBucketHourly(List<HourlySummaryDto.Bucket> buckets) {
        if (buckets == null) return null;
        for (int i = buckets.size() - 1; i >= 0; i--) {
            if (buckets.get(i).total > 0) return buckets.get(i);
        }
        return null;
    }

    private DailySummaryDto.Bucket lastActiveBucketDaily(List<DailySummaryDto.Bucket> buckets) {
        if (buckets == null) return null;
        for (int i = buckets.size() - 1; i >= 0; i--) {
            if (buckets.get(i).total > 0) return buckets.get(i);
        }
        return null;
    }

    private long lastActiveTotalHourly(List<HourlySummaryDto.Bucket> buckets) {
        HourlySummaryDto.Bucket b = lastActiveBucketHourly(buckets);
        return (b == null) ? 0L : b.total;
    }

    private long lastActiveTotalDaily(List<DailySummaryDto.Bucket> buckets) {
        DailySummaryDto.Bucket b = lastActiveBucketDaily(buckets);
        return (b == null) ? 0L : b.total;
    }

    private double avgActiveTotalHourly(List<HourlySummaryDto.Bucket> buckets) {
        if (buckets == null) return 0;
        long sum = 0; int n = 0;
        for (var b : buckets) { if (b.total > 0) { sum += b.total; n++; } }
        return n == 0 ? 0 : ((double) sum / n);
    }

    private double avgActiveTotalDaily(List<DailySummaryDto.Bucket> buckets) {
        if (buckets == null) return 0;
        long sum = 0; int n = 0;
        for (var b : buckets) { if (b.total > 0) { sum += b.total; n++; } }
        return n == 0 ? 0 : ((double) sum / n);
    }

    // ----------- last active time --------------
    private String[] lastActiveTimeHourly(List<HourlySummaryDto.Bucket> buckets) {
        HourlySummaryDto.Bucket b = lastActiveBucketHourly(buckets);
        if (b == null) return new String[] {
                null, null
        };
        return new String[] {
                b.hourStart,
                b.hourStartLocal
        };
    }

    private String[] lastActiveTimeDaily(List<DailySummaryDto.Bucket> buckets) {
        DailySummaryDto.Bucket b = lastActiveBucketDaily(buckets);
        if (b == null) return new String[] {
                null, null
        };
        return new String[] {
                b.dayStart,
                b.dayStartLocal
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private ZoneId safeZone(String tz) {
        try {
            return (tz == null || tz.isBlank()) ? ZoneId.of("America/Mexico_City") : ZoneId.of(tz.trim());
        } catch (Exception e) {
            return ZoneId.of("America/Mexico_City");
        }
    }

    private Instant parseInstantSafe(String iso) {
        try { return (iso == null) ? null : Instant.parse(iso); }
        catch (Exception e) { return null; }
    }

    private String toLocal(Instant i, ZoneId zone) {
        if (i == null || zone == null) return null;
        return i.atZone(zone).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String normKey(String s) {
        if (s == null) return null;
        String x = s.trim();
        return x.isEmpty() ? null : x;
    }

    private long safeLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); }
        catch (Exception e) { return 0L; }
    }

    // --------- merge helpers ---------
    private void mergeTopItemMap(Map<String, Long> acc, List<? extends HourlySummaryDto.TopItem> items) {
        if (acc == null || items == null) return;
        for (var it : items) {
            if (it == null) continue;
            String k = normKey(it.name);
            if (k == null) continue;
            acc.merge(k, Math.max(0, it.count), Long::sum);
        }
    }

    private void mergeTopErrorMap(Map<String, Long> acc, List<? extends HourlySummaryDto.TopError> items) {
        if (acc == null || items == null) return;
        for (var e : items) {
            if (e == null) continue;
            String k = normKey(e.key);
            if (k == null) continue;
            acc.merge(k, Math.max(0, e.count), Long::sum);
        }
    }

    private List<SummaryInsightsDto.TopItem> toTopItems(Map<String, Long> m, int limit) {
        if (m == null || m.isEmpty()) return List.of();
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        return m.entrySet().stream()
                .sorted((a,b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(safeLimit)
                .map(e -> new SummaryInsightsDto.TopItem(e.getKey(), e.getValue()))
                .toList();
    }

    private List<SummaryInsightsDto.TopError> toTopErrors(Map<String, Long> m, int limit) {
        if (m == null || m.isEmpty()) return List.of();
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        return m.entrySet().stream()
                .sorted((a,b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(safeLimit)
                .map(e -> new SummaryInsightsDto.TopError(e.getKey(), e.getValue()))
                .toList();
    }

    private static <T> List<T> mutable(List<T> in) {
        if (in == null) return new ArrayList<>();
        return (in instanceof ArrayList<T>) ? in : new ArrayList<>(in);
    }

    /**
     * meta("k1", v1, "k2", v2...) -> ignora nulls y claves vacías.
     * Evita Map.of(...) porque truena con null.
     */
    private Map<String, Object> meta(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (kv == null) return m;

        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = (kv[i] == null) ? null : String.valueOf(kv[i]).trim();
            Object v = kv[i + 1];
            if (k == null || k.isEmpty() || v == null) continue;
            m.put(k, v);
        }
        return m;
    }

    private long sumErrorTotalHourly(List<HourlySummaryDto.Bucket> buckets) {
        if (buckets == null) return 0L;
        long sum = 0;
        for (var b : buckets) {
            if (b == null) continue;
            sum += Math.max(0, b.errorTotal);
        }
        return sum;
    }

    private long sumErrorTotalDaily(List<DailySummaryDto.Bucket> buckets) {
        if (buckets == null) return 0L;
        long sum = 0;
        for (var b : buckets) {
            if (b == null) continue;
            sum += Math.max(0, b.errorTotal);
        }
        return sum;
    }

    private String firstName(List<SummaryInsightsDto.TopItem> list) {
        if (list == null || list.isEmpty() || list.get(0) == null) return null;
        String n = list.get(0).name;
        return (n == null || n.isBlank()) ? null : n;
    }

    private Long firstCount(List<SummaryInsightsDto.TopItem> list) {
        if (list == null || list.isEmpty() || list.get(0) == null) return null;
        return list.get(0).count;
    }

    private String firstKey(List<SummaryInsightsDto.TopError> list) {
        if (list == null || list.isEmpty() || list.get(0) == null) return null;
        String k = list.get(0).key;
        return (k == null || k.isBlank()) ? null : k;
    }

    private Long firstCountErr(List<SummaryInsightsDto.TopError> list) {
        if (list == null || list.isEmpty() || list.get(0) == null) return null;
        return list.get(0).count;
    }

    private SummaryInsightsDto.TopItem firstNonEmpty(List<SummaryInsightsDto.TopItem> list) {
        if (list == null) return null;
        for (var it : list) {
            if (it == null) continue;
            if (it.name != null && !it.name.isBlank()) return it;
        }
        return null;
    }

}
