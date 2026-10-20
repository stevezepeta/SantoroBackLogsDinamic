package backlogs.dinamico.api.dto.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Reporte de diagnóstico de cobertura (Fase M0 — solo lectura sobre log_events).
 *
 * Regla conservadora de readiness:
 * - 0 eventos en ventana ⇒ NOT_READY (todas las métricas NOT_AVAILABLE).
 * - Cobertura actor.id < 50% o actor.type ausente en > 20% ⇒ READY_WITH_CAVEATS,
 *   y si la gravedad es extrema (actor.id < 20% o actor.type ausente > 40%)
 *   ⇒ NOT_READY.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageReportDto {

    private String tenantId;
    private String system;
    private Integer windowDays;
    private Instant generatedAt;

    private ActorCoverageDto actorCoverage;
    private IdentityConflictDto identityConflicts;
    private SessionAuthDto sessionAuth;
    private TemporalDto temporal;

    /** Conteos de calidad de datos en ventana (legacy sin eventTime, futuros, duplicados). */
    private DataQualityDto dataQuality;

    /** READY_FOR_BACKFILL | READY_WITH_CAVEATS | NOT_READY */
    private String readiness;

    private List<String> readinessNotes;
}
