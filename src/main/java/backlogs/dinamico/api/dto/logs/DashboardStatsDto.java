package backlogs.dinamico.api.dto.logs;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardStatsDto {

    // Total de eventos en el rango
    private long total;

    // Total de eventos en el rango (alias para el dashboard)
    private long totalEvents;

    // Conteo de errores en el rango (criterio multicampo)
    private long errorCount;

    // Conteo de éxitos en el rango
    private long successCount;

    // Tasa de error en el rango (%)
    private double errorRate;

    // Estado de salud del rango: STABLE, WARNING, CRITICAL o INACTIVE
    private String healthStatus;

    // Estado de salud general: STABLE, WARNING, CRITICAL o INACTIVE
    private String overallHealth;

    // Casos activos (caseIds únicos) en el rango
    private long activeCases;

    // Total histórico de logs del sistema (sin filtro de fecha)
    private long totalHistoricalLogs;

    private List<DistItem> topEventTypes;

    private List<DistItem> outcomes;

    private List<DistItem> severities;

    private List<DistItem> statuses;

    private List<DistItem> topTags;

    private List<DistItem> topLocations;

    private List<DistItem> topActors;

    private List<DistItem> environments;

    @Data
    @Builder
    public static class DistItem {
        private String value;
        private long count;
        private double pct;   // porcentaje sobre el total
    }

}
