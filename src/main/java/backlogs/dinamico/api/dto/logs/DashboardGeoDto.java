package backlogs.dinamico.api.dto.logs;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardGeoDto {

    private long total;

    private List<GeoPoint> points;

    @Data
    @Builder
    public static class GeoPoint {
        private double lon;
        private double lat;
        private long count;
        private String caseId;      // Identificador único del dispositivo
        private String usuario;     // Nombre del usuario/actor del último log
        private String ip;          // IP del dispositivo
    }

}
