package backlogs.dinamico.api.dto.dashboard;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Respuesta paginada del widget de actividad reciente.
 */
@Data
@Builder
public class DashboardRecentEventsResponseDto {

    private long totalElements;
    private List<DashboardRecentEventDto> events;
}
