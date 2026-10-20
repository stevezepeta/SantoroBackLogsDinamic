package backlogs.dinamico.api.dto.intelligence;

/**
 * Estado de una métrica de diagnóstico de cobertura (Fase M0).
 *
 * - AVAILABLE: la fuente existe y el número es real.
 * - DEGRADED: aproximación o cobertura parcial (note obligatorio).
 * - NOT_AVAILABLE: no hay datos (value siempre null, note obligatorio).
 */
public enum MetricState {
    AVAILABLE,
    DEGRADED,
    NOT_AVAILABLE
}
