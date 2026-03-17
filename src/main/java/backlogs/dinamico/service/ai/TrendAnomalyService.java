package backlogs.dinamico.service.ai;

import backlogs.dinamico.model.ai.AiMetricRecord;
import backlogs.dinamico.repository.ai.AiMetricRepository;
import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrendAnomalyService {

    private final AiMetricRepository metricRepo;

    // -------- Config (luego a yml) --------
    private static final int BASELINE_N = 30;     // puntos históricos por system
    private static final int MIN_BASELINE = 10;   // mínimo para calcular tendencia

    private static final long MIN_TOTAL_DAILY = 200;
    private static final long MIN_TOTAL_HOURLY = 50;

    // Robust z-score con MAD escalado
    private static final double MAD_SCALE = 1.4826; // convierte MAD->sigma aprox
    private static final double ZR_WARN = 3.0;
    private static final double ZR_CRIT = 4.5;

    // Fallback cuando sigma ~ 0 (baseline estable)
    private static final double DELTA_WARN = 0.02; // +2 puntos porcentuales
    private static final double DELTA_CRIT = 0.05; // +5 puntos porcentuales

    public List<SummaryInsightsDto.Alert> detectPerSystem(ObjectId tenantId, SummaryInsightsDto out) {
        if (tenantId == null || out == null) return List.of();
        if (out.topSystemsRange == null || out.topSystemsRange.isEmpty()) return List.of();

        int maxSystems = Math.min(out.topSystemsRange.size(), 5);
        List<SummaryInsightsDto.Alert> alerts = new ArrayList<>();

        for (int i = 0; i < maxSystems; i++) {
            SummaryInsightsDto.TopItem sys = out.topSystemsRange.get(i);
            if (sys == null || sys.name == null || sys.name.isBlank()) continue;

            String system = sys.name.trim();

            // Historial por system y granularidad (desc: reciente primero)
            List<AiMetricRecord> recent =
                    metricRepo.findTop120ByTenantIdAndGranularityAndSystemOrderByBucketStartDesc(
                            tenantId, out.granularity, system
                    );

            if (recent == null || recent.size() < (MIN_BASELINE + 1)) continue;

            // asc: histórico -> actual
            recent = new ArrayList<>(recent);
            recent.sort(Comparator.comparing(AiMetricRecord::getBucketStart));

            AiMetricRecord current = recent.get(recent.size() - 1);
            if (current.getBucketStart() == null) continue;

            long curTotal = current.getTotal();

            long minTotal = "hourly".equalsIgnoreCase(out.granularity) ? MIN_TOTAL_HOURLY : MIN_TOTAL_DAILY;
            if (curTotal < minTotal) continue;

            // baseline: últimos N antes del actual
            int n = Math.min(BASELINE_N, recent.size() - 1);
            if (n < MIN_BASELINE) continue;

            List<AiMetricRecord> baselineRecs = recent.subList(recent.size() - 1 - n, recent.size() - 1);

            List<Double> baseline = baselineRecs.stream()
                    .map(r -> r.getErrorRate()) // double -> autobox
                    .collect(Collectors.toList());

            if (baseline.size() < MIN_BASELINE) continue;

            double cur = current.getErrorRate();
            double med = median(baseline);

            double mad = mad(baseline, med);
            double sigma = MAD_SCALE * mad; // robust sigma aprox

            // baseline estable => fallback delta
            if (sigma < 1e-9) {
                double delta = cur - med;

                if (delta >= DELTA_CRIT) {
                    alerts.add(buildAlert(system, out, current, "CRIT",
                            "Anomalía crítica por tendencia en system '" + system + "': errorRate subió de "
                                    + pct(med) + " a " + pct(cur) + " (delta=" + pct(delta) + ").",
                            meta(
                                    "system", system,
                                    "granularity", out.granularity,
                                    "baselineN", baseline.size(),
                                    "baselineMedian", med,
                                    "currentErrorRate", cur,
                                    "delta", delta,
                                    "currentTotal", curTotal,
                                    "method", "delta_fallback"
                            )));
                } else if (delta >= DELTA_WARN) {
                    alerts.add(buildAlert(system, out, current, "WARN",
                            "Anomalía por tendencia en system '" + system + "': errorRate subió de "
                                    + pct(med) + " a " + pct(cur) + " (delta=" + pct(delta) + ").",
                            meta(
                                    "system", system,
                                    "granularity", out.granularity,
                                    "baselineN", baseline.size(),
                                    "baselineMedian", med,
                                    "currentErrorRate", cur,
                                    "delta", delta,
                                    "currentTotal", curTotal,
                                    "method", "delta_fallback"
                            )));
                }
                continue;
            }

            // z robusto
            double zr = (cur - med) / sigma;

            // solo alertamos si sube (evita alertar “mejoró”)
            if (cur > med && zr >= ZR_CRIT) {
                alerts.add(buildAlert(system, out, current, "CRIT",
                        "Anomalía crítica por tendencia en system '" + system + "': errorRate "
                                + pct(cur) + " vs baseline ~" + pct(med) + " (zr=" + round1(zr) + ").",
                        meta(
                                "system", system,
                                "granularity", out.granularity,
                                "baselineN", baseline.size(),
                                "baselineMedian", med,
                                "mad", mad,
                                "sigma", sigma,
                                "currentErrorRate", cur,
                                "zr", zr,
                                "currentTotal", curTotal,
                                "method", "median_mad_scaled"
                        )));
            } else if (cur > med && zr >= ZR_WARN) {
                alerts.add(buildAlert(system, out, current, "WARN",
                        "Anomalía por tendencia en system '" + system + "': errorRate "
                                + pct(cur) + " vs baseline ~" + pct(med) + " (zr=" + round1(zr) + ").",
                        meta(
                                "system", system,
                                "granularity", out.granularity,
                                "baselineN", baseline.size(),
                                "baselineMedian", med,
                                "mad", mad,
                                "sigma", sigma,
                                "currentErrorRate", cur,
                                "zr", zr,
                                "currentTotal", curTotal,
                                "method", "median_mad_scaled"
                        )));
            }
        }

        return alerts;
    }

    private static SummaryInsightsDto.Alert buildAlert(
            String system,
            SummaryInsightsDto out,
            AiMetricRecord current,
            String level,
            String message,
            Map<String, Object> meta
    ) {
        return new SummaryInsightsDto.Alert(
                "TREND_ANOMALY_SYSTEM",
                level,
                message,
                current.getBucketStart().toString(),
                current.getBucketStartLocal(),
                meta
        );
    }

    private static double median(List<Double> xs) {
        if (xs == null || xs.isEmpty()) return 0.0;
        double[] a = xs.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int n = a.length;
        if (n % 2 == 1) return a[n / 2];
        return (a[(n / 2) - 1] + a[n / 2]) / 2.0;
    }

    private static double mad(List<Double> xs, double med) {
        if (xs == null || xs.isEmpty()) return 0.0;
        List<Double> dev = xs.stream().map(x -> Math.abs(x - med)).toList();
        return median(dev);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.2f%%", v * 100.0);
    }

    private static String round1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static Map<String, Object> meta(Object... kv) {
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
}