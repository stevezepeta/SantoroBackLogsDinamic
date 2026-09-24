package backlogs.dinamico.api.dto.dashboard;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Evento reciente para el widget de actividad del dashboard.
 */
@Data
@Builder
public class DashboardRecentEventDto {

    private String id;
    private Instant eventTime;
    private String system;
    private String eventType;
    private String status;
    private String severity;
    private String message;
    private String caseId;
    private String actorName;
    private String locationName;
}
