package backlogs.dinamico.api.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Respuesta del endpoint de análisis de embudos (funnel).
 * Devuelve la estructura completa para renderizar un gráfico de embudo dinámico.
 */
@Data
@Builder
public class FunnelResponseDto {

    private String system;

    private String funnelName;

    private FunnelSummaryDto summary;

    private List<FunnelStepDto> steps;
}

