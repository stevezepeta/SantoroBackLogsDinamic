package backlogs.dinamico.api.dto.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cobertura de sesiones y autenticación: sessionId, authResult y
 * eventTypes relacionados con autenticación en la ventana.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionAuthDto {

    private MetricValueDto sessionIdCoveragePct;
    private MetricValueDto sessionIdDistinct;
    private MetricValueDto authResultCoveragePct;

    /** Distribución de remoteConnection.authResult (no vacío). */
    private List<ValueCountDto> authResultDistribution;

    /** Top 10 eventTypes que casan con la regex de autenticación. */
    private List<ValueCountDto> authRelatedEventTypes;
}
