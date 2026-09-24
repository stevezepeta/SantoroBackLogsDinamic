package backlogs.dinamico.api.dto.analytics;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta del recorrido visual de un caso específico (User Journey).
 */
public record UserJourneyResponse(
        String caseId,
        String system,
        JourneySummary summary,
        List<JourneyStep> steps
) {

    public record JourneySummary(
            Instant startedAt,
            Instant finishedAt,
            String finalStatus,
            long totalSteps,
            long successfulSteps,
            long failedSteps,
            Long durationSeconds,
            String actorName,
            String locationName
    ) {
    }

    public record JourneyStep(
            int order,
            Instant timestamp,
            String stepLabel,
            OperationalStatus status,
            String title,
            String description,
            String suggestedAction,
            Double latitude,
            Double longitude,
            String locationName,
            boolean hasErrorExplanation,
            String errorExplanation,
            String durationFromPrevious
    ) {
    }
}
