package backlogs.dinamico.service.ai.dto;

import java.util.List;
import java.util.Map;

public class ErrorCorrelationDto {

    public String tz;

    public String from;
    public String to;

    public int inputCount;

    public List<Cluster> clusters;

    public List<Hypothesis> hypotheses;
    public List<String> nextSteps;
    public Map<String, Object> suggestedFilters;

    public static class Cluster {
        public String clusterId;
        public String representative;
        public List<String> members;
        public double similarityAvg;

        // Evidencia real (Mongo)
        public long matched;
        public Map<String, Long> topRequestIds;
        public Map<String, Long> topTraceIds;
        public Map<String, Long> topCaseIds;
        public Map<String, Long> topActors;
        public Map<String, Long> topStatus;
        public Map<String, Long> topOutcome;

        public List<Sample> samples;
        public Map<String, Object> suggestedFilters;
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
        public String messageKey;
        public String message;

        public String requestId;
        public String traceId;
        public String caseId;

        public String actorUsername;
        public String actorFullName;

        public String locationName;
    }

    public static class Hypothesis {
        public String type;
        public String confidence;
        public String summary;
        public List<String> evidence;
    }

}
