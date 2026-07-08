package backlogs.dinamico.api.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Representa un paso individual en el embudo de conversión.
 */
@Data
@Builder
public class FunnelStepDto {

    private int step;

    /**
     * Etiqueta descriptiva del paso para mostrar en el frontend
     */
    private String label;

    /**
     * Tipo de evento que define este paso
     */
    private String eventType;

    /**
     * Cantidad de casos únicos (caseId) que alcanzaron este paso
     */
    private long count;

    /**
     * Porcentaje de conversión respecto al paso 1 (siempre 100% en el paso 1)
     */
    private double conversionRate;

    /**
     * Porcentaje de abandono respecto al paso inmediato anterior (0% en el paso 1)
     */
    private double dropRate;
}

