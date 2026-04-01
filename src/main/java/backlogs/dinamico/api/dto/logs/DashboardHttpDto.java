package backlogs.dinamico.api.dto.logs;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardHttpDto {

    // LATENCIA p95 POR STATUSCODE X METODO HTTP
    private List<HttpBucket> latencyByStatusAndMethod;

    // RESUMEN GENERAL DE METODOS HTTP
    private List<MethodSummary> methodSummary;

    @Data
    @Builder
    public static class HttpBucket {
        private String statusCode;
        private String method;
        private double p95Ms;
        private double avgMs;
        private long count;
    }

    @Data
    @Builder
    public static class MethodSummary {
        private String method;
        private long count;
        private double avgMs;
    }

}
