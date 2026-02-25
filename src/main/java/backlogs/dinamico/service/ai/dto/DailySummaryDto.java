package backlogs.dinamico.service.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public class DailySummaryDto {

    @Schema(example = "America/Mexico_City")
    public String tz;

    @Schema(example = "2026-02-04T00:00:00Z")
    public String from;

    @Schema(example = "2026-02-11T00:00:00Z")
    public String to;

    @Schema(example = "2026-02-05T00:00:00-06:00")
    public String fromLocal;

    @Schema(example = "2026-02-12T00:00:00-06:00")
    public String toLocal;


    @Schema(example = "7")
    public int days;

    public List<Bucket> buckets;

    public static class Bucket {
        @Schema(example = "2026-02-10T00:00:00Z")
        public String dayStart;        // UTC

        @Schema(example = "2026-02-09T18:00:00-06:00")
        public String dayStartLocal;   // UI-friendly

        @Schema(example = "1200")
        public long total;

        public Map<String, Long> severities;

        public List<TopItem> topSystems;
        public List<TopItem> topEventTypes;
        public List<TopItem> topStatus;
        public List<TopItem> topOutcome;

        public List<TopError> topErrors;

        @Schema(example = "12")
        public long errorTotal;
    }

    public static class TopItem {
        @Schema(example = "BIOMETRIC")
        public String name;

        @Schema(example = "120")
        public long count;

        public TopItem() {}
        public TopItem(String name, long count) { this.name = name; this.count = count; }
    }

    public static class TopError {
        @Schema(example = "Timeout while calling service X...")
        public String key;

        @Schema(example = "9")
        public long count;

        public TopError() {}
        public TopError(String key, long count) { this.key = key; this.count = count; }
    }

}
