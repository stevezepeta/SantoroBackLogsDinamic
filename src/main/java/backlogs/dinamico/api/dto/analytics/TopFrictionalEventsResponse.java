package backlogs.dinamico.api.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Respuesta del ranking de eventos que causan mayor fricción a los usuarios.
 */
@Data
@Builder
public class TopFrictionalEventsResponse {

    private LocalDate date;
    private List<FrictionalEvent> events;

    @Data
    @Builder
    public static class FrictionalEvent {
        private int rank;
        private String eventCode;
        private String title;
        private String description;
        private long occurrenceCount;
        private long affectedCases;
        private Double percentageOfTotalErrors;
        private String trend; // UP | DOWN | STABLE
        private Double trendPercentage;
        private String system;
        private String topLocation;
        private String recommendedAction;
    }
}
