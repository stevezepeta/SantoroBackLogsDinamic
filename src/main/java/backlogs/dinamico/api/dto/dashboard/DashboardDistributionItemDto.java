package backlogs.dinamico.api.dto.dashboard;

import lombok.Builder;
import lombok.Data;

/**
 * Item genérico de distribución para métricas analíticas del dashboard.
 */
@Data
@Builder
public class DashboardDistributionItemDto {

    private String value;
    private long count;
    private double percentage;
}
