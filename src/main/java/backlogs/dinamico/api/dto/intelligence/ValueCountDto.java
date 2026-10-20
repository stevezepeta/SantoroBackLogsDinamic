package backlogs.dinamico.api.dto.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Par valor/conteo para distribuciones (actor.type, authResult, eventTypes, etc.). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValueCountDto {
    private String value;
    private Long count;
}
