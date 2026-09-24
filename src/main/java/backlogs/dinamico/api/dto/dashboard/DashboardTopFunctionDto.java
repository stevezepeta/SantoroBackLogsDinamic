package backlogs.dinamico.api.dto.dashboard;

import lombok.Builder;
import lombok.Data;

/**
 * Función/evento más usado en un sistema.
 */
@Data
@Builder
public class DashboardTopFunctionDto {

    private String eventType;
    private long count;
    private long rank;
}
