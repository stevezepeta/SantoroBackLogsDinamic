package backlogs.dinamico.api.dto.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Dimensión temporal: volumen en ventana, volumen por día, primer/último
 * eventTime global del tenant+system y lag de ingesta estimado.
 *
 * El lag se estima como ($toDate: "$_id" - eventTime) en minutos; es una
 * aproximación porque el timestamp de ingesta real no existe (no hay createdAt).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemporalDto {

    private Long totalEventsInWindow;
    private List<DailyCountDto> dailyVolume;

    /** Primer eventTime global (tenant+system, sin límite de ventana). */
    private Instant firstEventTime;

    /** Último eventTime global (tenant+system, sin límite de ventana). */
    private Instant lastEventTime;

    /** Lag promedio de ingesta en minutos — DEGRADED (estimated_from_objectid). */
    private MetricValueDto ingestionLagAvgMin;

    /** Lag mínimo de ingesta en minutos — DEGRADED (estimated_from_objectid). */
    private MetricValueDto ingestionLagMinMin;

    /** Lag p95 de ingesta en minutos — DEGRADED (estimated_from_objectid, p95 aproximado por buckets). */
    private MetricValueDto ingestionLagP95Min;
}
