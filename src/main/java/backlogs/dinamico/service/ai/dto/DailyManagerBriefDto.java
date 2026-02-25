package backlogs.dinamico.service.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public class DailyManagerBriefDto {

    @Schema(example = "America/Mexico_City")
    public String tz;

    // Día objetivo (bucket)
    public String dayStart;        // UTC ISO
    public String dayStartLocal;   // ISO con offset

    // Ventana usada para calcular (normalmente día completo)
    public String windowFrom;        // UTC ISO
    public String windowFromLocal;   // ISO con offset
    public String windowTo;          // UTC ISO
    public String windowToLocal;     // ISO con offset

    // KPIs
    public long total;
    public long errorCount;
    public double errorRate; // 0..1
    public Map<String, Long> severities;

    // Tops del día
    public List<TopItem> topSystems;
    public List<TopItem> topEventTypes;
    public List<TopItem> topStatus;
    public List<TopItem> topOutcome;
    public List<TopError> topErrors;

    // Comparativo vs día anterior (si existe)
    public Trend trend;

    // Texto “para gerente”
    public String executiveSummary;
    public List<String> keyPoints;
    public List<String> actions;

    public static class Trend {
        public Long prevTotal;
        public Double prevErrorRate;

        public Long deltaTotal;
        public Double deltaTotalPct;     // -1..+inf (ej 0.25 = +25%)
        public Double deltaErrorRate;    // puntos (ej +0.05 = +5 pp)

        public String label; // “Subió / Bajó / Sin cambio”
    }

    public static class TopItem {
        public String name;
        public long count;
        public TopItem() {}
        public TopItem(String name, long count) { this.name = name; this.count = count; }
    }

    public static class TopError {
        public String key;
        public long count;
        public TopError() {}
        public TopError(String key, long count) { this.key = key; this.count = count; }
    }
}
