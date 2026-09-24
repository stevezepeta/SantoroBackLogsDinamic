package backlogs.dinamico.api.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vista global agregada del resumen ejecutivo para el frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutiveSummaryGlobalDto {

    private String globalHealth;
    private long totalGlobalEvents;
    private double globalErrorRate;
    private long activeCases;

}
