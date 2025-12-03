package backlogs.dinamico.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Data
@Builder
public class OfficeResponse {

    private Long id;
    private String name;
    private String address;

    private String countryId;
    private String stateId;
    private String municipalityId;

    private String status;

    private Geo geo;

    @Data
    @Builder
    public static class Geo {
        private String type;
        private List<Double> coordinates;
    }

}
