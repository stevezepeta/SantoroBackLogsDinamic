package backlogs.dinamico.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Data
public class OfficeCreatedRequest {

    private String name;
    private String address;

    @JsonProperty("country")
    private String countryId;

    @JsonProperty("state")
    private String stateId;

    @JsonProperty("city")
    private String municipalityId;

    private String status;

    private GeoDTO geo;

    @Data
    public static class GeoDTO {
        private String type;
        private List<Double> coordinates;
    }

}
