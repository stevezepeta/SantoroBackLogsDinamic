package backlogs.dinamico.api.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Explicación de un error técnico en lenguaje natural para supervisores.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorExplanation {
    private String summary;
    private String likelyCause;
    private String businessImpact;
    private String recommendedAction;
    private String confidence;
}
