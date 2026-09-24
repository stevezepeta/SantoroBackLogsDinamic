package backlogs.dinamico.api.dto.catalog;

public record ExecutiveSummaryDto(
        String globalHealth,
        long totalGlobalEvents,
        double globalErrorRate,
        long activeCases
) {
}
