package backlogs.dinamico.api.dto.dashboard;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Punto de una serie temporal del dashboard.
 */
@Data
@Builder
public class DashboardSeriesItemDto {

    private String period;
    private long totalEvents;
    private long errorEvents;
    private Map<String, Long> byEventType;
}
