package backlogs.dinamico.api.dto.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Cobertura del bloque `actor` de log_events: presencia, cardinalidad distinta
 * y distribución de tipos. Solo lectura; mide calidad de datos, no usuarios reales.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActorCoverageDto {

    private MetricValueDto actorIdCoveragePct;
    private MetricValueDto actorIdDistinct;
    private MetricValueDto usernameCoveragePct;
    private MetricValueDto usernameDistinct;
    private MetricValueDto fullNameCoveragePct;
    private MetricValueDto fullNameDistinct;
    private MetricValueDto typeCoveragePct;
    private MetricValueDto typeDistinct;

    /** Distribución cruda de actor.type (UNKNOWN incluye ausentes). */
    private List<ValueCountDto> typeDistribution;

    /** Conteos preliminares por identidad HUMAN/SERVICE/DEVICE/UNKNOWN. */
    private Map<String, Long> identityCounts;

    /** Nota sobre clasificación preliminar (DEGRADED si actor.type ausente en >20%). */
    private String identityNote;
}
