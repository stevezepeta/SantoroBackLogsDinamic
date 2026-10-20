package backlogs.dinamico.api.dto.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Calidad de datos dentro de la ventana del diagnóstico (Fase M0 — solo lectura).
 *
 * Todas las métricas son conteos exactos derivados de agregaciones acotadas
 * (hint + maxTime 3s). No contienen valores personales, solo conteos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataQualityDto {

    /**
     * Documentos en ventana sin eventTime (ni null): invisibles para toda
     * consulta acotada por tiempo. Indicador de emisores rotos o esquema legacy.
     */
    private Long eventsWithoutEventTimeInWindow;

    /**
     * Eventos en ventana con eventTime posterior al momento de generación.
     * Excluidos del cálculo de lag para no contaminar el promedio.
     */
    private Long futureEventsInWindow;

    /**
     * Grupos de posibles duplicados: misma tupla (eventType, actor.id, eventTime)
     * con más de un documento. Heurística conservadora — no implica duplicado real.
     */
    private Long potentialDuplicateGroups;

    /**
     * Documentos "extra" dentro de los grupos de posibles duplicados
     * (suma de count-1 por grupo). Sobrecostó potencial de almacenamiento/ingesta.
     */
    private Long potentialDuplicateExtraEvents;
}
