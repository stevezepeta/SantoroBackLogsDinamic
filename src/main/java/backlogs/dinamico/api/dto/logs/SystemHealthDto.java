package backlogs.dinamico.api.dto.logs;

public record SystemHealthDto(
        String system,
        String status,      // "HEALTHY", "WARN", "CRIT", "INACTIVE"
        double errorRate,
        long   total,
        long   failures
) {
}
