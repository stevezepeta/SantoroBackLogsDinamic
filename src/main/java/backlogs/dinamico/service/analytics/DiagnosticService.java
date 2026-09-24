package backlogs.dinamico.service.analytics;

import backlogs.dinamico.api.dto.analytics.DiagnosticResponse;
import backlogs.dinamico.api.dto.analytics.TopFrictionalEventsResponse;
import backlogs.dinamico.api.dto.catalog.SystemHealthDto;
import backlogs.dinamico.service.catalog.CatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Servicio de diagnóstico técnico consolidado.
 *
 * <p>Expone una vista unificada de salud de sistemas y top de eventos de
 * fricción para un periodo determinado. Si no se indica periodo, evalúa el
 * día actual en el huso horario local.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosticService {

    private final CatalogService catalogService;
    private final ExecutiveAnalyticsService executiveAnalyticsService;

    public DiagnosticResponse getDiagnostics(Authentication auth, Instant from, Instant to) {
        InstantRange range = resolveRange(from, to);

        try {
            List<SystemHealthDto> systemsHealth = catalogService.getSystemsHealth(null, range.from(), range.to());
            TopFrictionalEventsResponse topFrictionalEvents = executiveAnalyticsService.topFrictionalEvents(
                    auth, range.from(), range.to(), null, 5);

            return DiagnosticResponse.builder()
                    .from(range.from())
                    .to(range.to())
                    .systemsHealth(systemsHealth)
                    .topFrictionalEvents(topFrictionalEvents)
                    .build();
        } catch (Exception e) {
            log.warn("[getDiagnostics] Error obteniendo diagnóstico, retornando fallback: {}", e.getMessage());
            return DiagnosticResponse.builder()
                    .from(range.from())
                    .to(range.to())
                    .systemsHealth(List.of())
                    .topFrictionalEvents(TopFrictionalEventsResponse.builder()
                            .date(LocalDate.now())
                            .events(List.of())
                            .build())
                    .build();
        }
    }

    private InstantRange resolveRange(Instant from, Instant to) {
        Instant effectiveFrom = from != null ? from : Instant.EPOCH;
        Instant effectiveTo = to != null ? to : Instant.now();
        return new InstantRange(effectiveFrom, effectiveTo);
    }

    /**
     * Parsea una cadena de fecha de forma segura a {@link Instant}.
     * Soporta tanto fechas ISO ({@code 2026-08-07}) como date-time ISO
     * ({@code 2026-08-07T00:00:00Z}).
     * Si la cadena es nula, vacía, "null" o no es parseable, devuelve el
     * inicio del tiempo UNIX para fechas de inicio o el instante actual para
     * fechas de fin, permitiendo consultar el histórico completo.
     */
    private Instant parseToInstant(String dateStr, boolean isEnd) {
        if (dateStr == null || dateStr.isBlank()
                || dateStr.equalsIgnoreCase("null")
                || dateStr.startsWith("1970")) {
            return isEnd ? Instant.now() : Instant.EPOCH;
        }
        try {
            if (dateStr.contains("T")) {
                return Instant.parse(dateStr);
            }
            LocalDate localDate = LocalDate.parse(dateStr);
            return isEnd
                    ? localDate.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC)
                    : localDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            return isEnd ? Instant.now() : Instant.EPOCH;
        }
    }

    private record InstantRange(Instant from, Instant to) {
    }
}
