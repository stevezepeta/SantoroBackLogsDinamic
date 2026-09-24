package backlogs.dinamico.api.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Respuesta del endpoint de explicación de errores con IA bajo demanda.
 */
@Data
@Builder
public class ErrorExplanationResponse {
    private String logId;
    private String system;
    private String caseId;
    private String eventCode;
    private ErrorExplanation explanation;
    private Instant generatedAt;
    private String source; // LLM | HEURISTIC | NOT_APPLICABLE
}
