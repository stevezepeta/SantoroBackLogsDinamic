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
    }

}
