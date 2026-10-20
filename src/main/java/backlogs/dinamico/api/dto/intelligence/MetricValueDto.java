package backlogs.dinamico.api.dto.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Métrica autodescripta: valor + estado + nota.
 * Si state == NOT_AVAILABLE entonces value siempre es null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricValueDto {

    private Object value;
    private MetricState state;
    private String note;

    public static MetricValueDto available(Object value) {
        return MetricValueDto.builder()
                .value(value)
                .state(MetricState.AVAILABLE)
                .build();
    }

    public static MetricValueDto degraded(Object value, String note) {
        return MetricValueDto.builder()
                .value(value)
                .state(MetricState.DEGRADED)
                .note(note)
                .build();
    }

    public static MetricValueDto notAvailable(String note) {
        return MetricValueDto.builder()
                .value(null)
                .state(MetricState.NOT_AVAILABLE)
                .note(note)
                .build();
    }
}
