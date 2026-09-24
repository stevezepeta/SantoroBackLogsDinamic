package backlogs.dinamico.api.dto.analytics;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta del engine de anomalías de asistencia.
 */
public record AttendanceAnomaliesResponse(
        String system,
        String date,
        boolean supportsAttendance,
        int totalAnomalies,
        List<OrphanClosureDto> orphanClosures,
        List<IncompleteShiftDto> incompleteShifts,
        List<GhostUserDto> ghostUsers
) {
}
