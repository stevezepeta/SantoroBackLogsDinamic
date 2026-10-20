package backlogs.dinamico.controller.analytics;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.intelligence.*;
import backlogs.dinamico.service.analytics.DataDiagnosticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Diagnóstico de cobertura de datos (Fase M0) — Inteligencia de Actividad y Usuarios.
 *
 * SOLO LECTURA sobre log_events. No modifica datos; sirve para evaluar si un
 * tenant+system tiene cobertura suficiente antes de un eventual backfill.
 */
@Slf4j
@RestController
@RequestMapping("/api/intelligence/diagnostics")
@RequiredArgsConstructor
@Tag(name = "Intelligence", description = "Inteligencia de actividad y usuarios - diagnóstico de cobertura (Fase M0)")
public class IntelligenceDiagnosticsController {

    private final DataDiagnosticsService dataDiagnosticsService;

    @Operation(
            summary = "Diagnóstico de cobertura de datos (M0)",
            description = "Evalúa cobertura de actor, sesiones/auth, temporalidad y conflictos de identidad "
                    + "sobre log_events en una ventana de días (default 30, máx 90). Solo lectura. "
                    + "Devuelve readiness READY_FOR_BACKFILL | READY_WITH_CAVEATS | NOT_READY."
    )
    @GetMapping("/coverage")
    @PreAuthorize("hasAuthority('PERM_LOG_READ')")
    public ApiResponse<CoverageReportDto> getCoverage(
            @RequestParam String system,
            @RequestParam(required = false) Integer days
    ) {
        try {
            CoverageReportDto report = dataDiagnosticsService.coverage(system, days);
            return ApiResponse.ok("Coverage report generated", "intelligence_coverage", report);
        } catch (Exception e) {
            log.warn("[IntelligenceDiagnostics] Coverage unavailable for system={}: {}", system, e.getMessage());
            return ApiResponse.ok(
                    "Coverage report unavailable",
                    "intelligence_coverage_fallback",
                    CoverageReportDto.builder()
                            .system(system != null ? system.toUpperCase() : null)
                            .windowDays(days)
                            .generatedAt(Instant.now())
                            .actorCoverage(ActorCoverageDto.builder()
                                    .actorIdCoveragePct(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .actorIdDistinct(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .usernameCoveragePct(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .usernameDistinct(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .fullNameCoveragePct(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .fullNameDistinct(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .typeCoveragePct(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .typeDistinct(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .typeDistribution(List.of())
                                    .identityCounts(Map.of())
                                    .identityNote("diagnostic_unavailable")
                                    .build())
                            .identityConflicts(IdentityConflictDto.builder()
                                    .conflictedUsernames(null)
                                    .conflicts(List.of())
                                    .build())
                            .sessionAuth(SessionAuthDto.builder()
                                    .sessionIdCoveragePct(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .sessionIdDistinct(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .authResultCoveragePct(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .authResultDistribution(List.of())
                                    .authRelatedEventTypes(List.of())
                                    .build())
                            .temporal(TemporalDto.builder()
                                    .totalEventsInWindow(0L)
                                    .dailyVolume(List.of())
                                    .firstEventTime(null)
                                    .lastEventTime(null)
                                    .ingestionLagAvgMin(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .ingestionLagMinMin(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .ingestionLagP95Min(MetricValueDto.notAvailable("diagnostic_unavailable"))
                                    .build())
                            .dataQuality(DataQualityDto.builder()
                                    .eventsWithoutEventTimeInWindow(null)
                                    .futureEventsInWindow(null)
                                    .potentialDuplicateGroups(null)
                                    .potentialDuplicateExtraEvents(null)
                                    .build())
                            .readiness("NOT_READY")
                            .readinessNotes(List.of("diagnostic_unavailable: " + e.getMessage()))
                            .build()
            );
        }
    }
}
