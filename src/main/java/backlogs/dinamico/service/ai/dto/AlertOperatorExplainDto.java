package backlogs.dinamico.service.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public class AlertOperatorExplainDto {

    @Schema(example = "America/Mexico_City")
    public String tz;

    public String alertId;

    public String granularity;
    public String status;
    public String state;

    public String windowFrom;
    public String windowFromLocal;
    public String windowTo;
    public String windowToLocal;

    public String bucketFrom;
    public String bucketFromLocal;
    public String bucketTo;
    public String bucketToLocal;

    public String primaryType;
    public String primaryLevel;
    public String operatorMeaning;
    public String impact;
    public String triageSummary;

    // Quer haria en 3 pasos
    public List<Step> steps;

    // Evidencia rapida para actuar
    public List<TopItem> topSystems;
    public List<TopItem> topEventTypes;
    public List<TopError> topErrors;

    public List<Sample> samples;
    public Map<String, Long> topRequestIds;
    public Map<String, Long> topCaseIds;
    public Map<String, Long> topActors;

    public static class Step {
        public String title;
        public List<String> checks;
        public Map<String, Object> suggestedFilters;
        public Step() {}
        public Step(String title, List<String> checks, Map<String, Object> suggestedFilters) {
            this.title = title;
            this.checks = checks;
            this.suggestedFilters = suggestedFilters;
        }
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

    public static class Sample {
        public String id;
        public String eventTime;
        public String eventTimeLocal;

        public String system;
        public String eventType;
        public String status;
        public String outcome;
        public String severity;

        public String message;

        public String requestId;
        public String traceId;
        public String caseId;

        public String actorId;
        public String actorUsername;
        public String actorFullName;

        public String locationId;
        public String locationName;
    }

}
