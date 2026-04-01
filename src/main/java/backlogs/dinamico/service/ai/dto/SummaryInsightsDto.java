package backlogs.dinamico.service.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public class SummaryInsightsDto {

    @Schema(example = "hourly")
    public String granularity; // hourly | daily

    @Schema(example = "America/Mexico_City")
    public String tz;

    public String from;
    public String to;

    @Schema(example = "2026-02-11T13:00:00-06:00")
    public String fromLocal;

    @Schema(example = "2026-02-12T13:00:00-06:00")
    public String toLocal;


    public Integer hours;
    public Integer days;

    public long total;
    public int activeBuckets;
    public int buckets;

    public String system;

    // 0..1
    public double errorRate;

    public Map<String, Long> severities;

    public List<TopItem> topSystems;
    public List<TopItem> topEventTypes;
    public List<TopItem> topStatus;
    public List<TopItem> topOutcome;
    public List<TopError> topErrors;

    // Top globales del rango
    public List<TopItem> topSystemsRange;
    public List<TopItem> topEventTypesRange;
    public List<TopItem> topStatusRange;
    public List<TopItem> topOutcomeRange;
    public List<TopError> topErrorsRange;

    // NUEVO
    @Schema(example = "WARN")
    public String status; // OK | WARN | CRIT

    // NUEVO
    public List<Alert> alerts;

    public List<String> highlights;
    public List<String> warnings;
    public List<String> recommendations;

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

    // NUEVO
    public static class Alert {
        @Schema(example = "HIGH_ERROR_RATE")
        public String type;

        @Schema(example = "CRIT")
        public String level; // INFO | WARN | CRIT

        // bucket donde se detectó (si aplica)
        public String bucketStart;       // UTC
        public String bucketStartLocal;  // local tz

        public String message;
        public Map<String, Object> meta;

        public Alert() {}

        public Alert(String type, String level, String message,
                     String bucketStart, String bucketStartLocal,
                     Map<String, Object> meta) {
            this.type = type;
            this.level = level;
            this.message = message;
            this.bucketStart = bucketStart;
            this.bucketStartLocal = bucketStartLocal;
            this.meta = meta;
        }
    }
}
