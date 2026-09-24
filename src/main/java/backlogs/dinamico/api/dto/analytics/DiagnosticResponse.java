package backlogs.dinamico.api.dto.analytics;

import backlogs.dinamico.api.dto.catalog.SystemHealthDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta consolidada del endpoint de diagnóstico técnico.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticResponse {

    private Instant from;
    private Instant to;
    private List<SystemHealthDto> systemsHealth;
    private TopFrictionalEventsResponse topFrictionalEvents;

}
