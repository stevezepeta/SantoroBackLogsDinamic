package backlogs.dinamico.api.dto.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Salud de un sistema para el catálogo /systems-health.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealthDto {

    private String systemCode;
    private String systemName;
    private String status;
    private long totalEvents;
    private long errorEvents;
    private double errorRate;

}
