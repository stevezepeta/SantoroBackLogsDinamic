package backlogs.dinamico.api.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Métricas globales del embudo de conversión.
 */
@Data
@Builder
public class FunnelSummaryDto {

    /**
     * Total de casos únicos que iniciaron el flujo (paso 1)
     */
    private long totalStarted;

    /**
     * Total de casos únicos que completaron el flujo (último paso)
     */
    private long totalCompleted;

    /**
     * Tasa de conversión global (% del paso 1 al último paso)
     */
    private double globalConversionRate;
}

