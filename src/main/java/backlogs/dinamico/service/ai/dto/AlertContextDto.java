package backlogs.dinamico.service.ai.dto;

import backlogs.dinamico.service.ai.dto.SummaryInsightsDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public class AlertContextDto {

    @Schema(example = "America/Mexico_City")
    public String tz;

    public Alert alert;
    public BucketContext bucket;

    public List<LogSample> samples;

    // Correlación rápida (derivada de samples)
    public Map<String, Long> topRequestIds;
    public Map<String, Long> topCaseIds;
    public Map<String, Long> topActors;

    public static class Alert {
        public String id;
        public String tenantId;

        public String granularity;

        public String windowFrom;
        public String windowFromLocal;

        public String windowTo;
        public String windowToLocal;

        public String bucketStart;
        public String bucketStartLocal;

        public String createdAt;
        public String createdAtLocal;

        public String status;
        public long total;
        public double errorRate;

        public Map<String, Long> severities;
        public List<SummaryInsightsDto.Alert> alerts;

        public String fingerprint;

        public String state;
        public String ackedAt;
        public String ackedAtLocal;
        public String ackedBy;

        public String resolvedAt;
        public String resolvedAtLocal;
        public String resolvedBy;
    }

    public static class BucketContext {
        public String from;
        public String fromLocal;

        public String to;
        public String toLocal;

        public long total;
        public Map<String, Long> severities;

        public List<TopItem> topSystems;
        public List<TopItem> topEventTypes;
        public List<TopItem> topStatus;
        public List<TopItem> topOutcome;

        public List<TopError> topErrors;
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

    public static class LogSample {
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
