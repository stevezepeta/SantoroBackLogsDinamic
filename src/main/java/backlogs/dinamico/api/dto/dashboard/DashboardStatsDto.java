package backlogs.dinamico.api.dto.dashboard;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Estadísticas agregadas de un sistema para el dashboard.
 */
@Data
@Builder
public class DashboardStatsDto {

    private String system;
    private Instant from;
    private Instant to;

    private long totalEvents;
    private long errorCount;
    private long successCount;
    private long warningEvents;
    private long infoEvents;
    private double errorRate;
    private String healthStatus; // STABLE | WARNING | CRITICAL

    private long totalHistoricalLogs;
    private long todayEvents;
}
