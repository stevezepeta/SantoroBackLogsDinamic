package backlogs.dinamico.api.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumen ejecutivo de salud operativa para supervisores.
 */
@Data
@Builder
public class ExecutiveSummaryResponse {

    private Instant generatedAt;
    private Period period;
    private String overallHealth; // global organizational health
    private List<SystemHealth> systems;
    private List<ExecutiveAlert> activeAlerts;
    private TopMetrics topMetrics;
    private List<TopFrictionalEventsResponse.FrictionalEvent> topFrictionalEvents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Period {
        private LocalDate from;
        private LocalDate to;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemHealth {
        private String system;
        private String systemLabel;
        private String displayName;
        private String status; // STABLE | WARNING | CRITICAL | INACTIVE
        private long totalEvents;
        private long errorCount;
        private double errorRate;
        private long activeCases;
        private Double completionRate;
        private Instant lastIncident;
        private Instant lastSeen;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutiveAlert {
        private String id;
        private String severity;
        private String title;
        private String message;
        private String system;
        private Instant timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopMetrics {
        private long totalEvents;
        private long totalErrors;
        private double globalErrorRate;
        private long activeCases;
    }
}
