package backlogs.dinamico.infra.ws;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Evento publicado por WebSocket cuando llegan nuevos logs.
 * Topic: /topic/dashboard/{tenantId}/{system}
 *
 * El frontend recibe este evento y re-llama:
 *   GET /api/logs/dashboard/stats?system={system}
 *   GET /api/logs/dashboard/series?system={system}
 */
@Data
@Builder
public class DashboardEvent {

    /** Tipo de evento */
    private String type;          // "NEW_LOGS"

    /** System afectado */
    private String system;

    /** Cuántos logs nuevos llegaron en este batch */
    private int count;

    /** Timestamp del evento */
    private Instant timestamp;

    public static DashboardEvent newLogs(String system, int count) {
        return DashboardEvent.builder()
                .type("NEW_LOGS")
                .system(system)
                .count(count)
                .timestamp(Instant.now())
                .build();
    }
}