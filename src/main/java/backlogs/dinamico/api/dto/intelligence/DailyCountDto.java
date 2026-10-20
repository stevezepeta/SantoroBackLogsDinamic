package backlogs.dinamico.api.dto.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Volumen de eventos para un día (YYYY-MM-DD). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyCountDto {
    private String date;
    private Long count;
}
