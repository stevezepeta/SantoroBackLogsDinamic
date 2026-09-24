package backlogs.dinamico.api.dto.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estado simplificado de un sistema para el menú desplegable (últimas 24h).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatus24hItemDto {

    private String system;
    private String color; // positive | warning | negative | grey-6

}
