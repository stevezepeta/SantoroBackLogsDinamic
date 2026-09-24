package backlogs.dinamico.api.dto.dashboard;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Respuesta de funciones/eventos más usados, incluyendo el total histórico
 * de logs procesados para el sistema (independientemente del top N devuelto).
 */
@Data
@Builder
public class DashboardTopFunctionsResponseDto {

    private long totalProcessedLogs;
    private List<DashboardTopFunctionDto> functions;
}
