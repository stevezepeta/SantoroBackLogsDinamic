package backlogs.dinamico.service.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public class HourlySummaryDto {

    @Schema(example = "America/Mexico_City")
    public String tz;

    @Schema(example = "2026-02-10T15:45:25.988383100Z")
    public String from;

    @Schema(example = "2026-02-11T15:45:25.988383100Z")
    public String to;

    @Schema(example = "2026-02-10T13:00:00-06:00")
    public String fromLocal;

    @Schema(example = "2026-02-11T13:00:00-06:00")
    public String toLocal;



    @Schema(example = "24")
    public int hours;

    public List<Bucket> buckets;

    public static class Bucket {
        @Schema(example = "2026-02-11T00:00:00Z")
        public String hourStart;        // UTC

        @Schema(example = "2026-02-11T00:00:00-06:00")
        public String hourStartLocal;   // bonito para UI

        @Schema(example = "5")
        public long total;

        // {"ERROR": 5, "INFO": 10 ...}
        public Map<String, Long> severities;

        public List<TopItem> topSystems;
        public List<TopItem> topEventTypes;
        public List<TopItem> topStatus;
        public List<TopItem> topOutcome;

        public List<TopError> topErrors;

        @Schema(example = "3")
        public long errorTotal;
    }

    public static class TopItem {
        @Schema(example = "BIOMETRIC")
        public String name;

        @Schema(example = "5")
        public long count;

        public TopItem() {}
        public TopItem(String name, long count) { this.name = name; this.count = count; }
    }

    public static class TopError {
        @Schema(example = "This is a message...")
        public String key;

        @Schema(example = "2")
        public long count;

        public TopError() {}
        public TopError(String key, long count) { this.key = key; this.count = count; }
    }

}
