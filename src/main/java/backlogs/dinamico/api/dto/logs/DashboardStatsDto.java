package backlogs.dinamico.api.dto.logs;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardStatsDto {

    // Total de eventos en el rango
    private long total;

    private List<DistItem> topEventTypes;

    private List<DistItem> outcomes;

    private List<DistItem> severities;

    private List<DistItem> statuses;

    private List<DistItem> topTags;

    private List<DistItem> topLocations;

    private List<DistItem> topActors;

    private List<DistItem> environments;

    @Data
    @Builder
    public static class DistItem {
        private String value;
        private long count;
        private double pct;   // porcentaje sobre el total
    }

}
