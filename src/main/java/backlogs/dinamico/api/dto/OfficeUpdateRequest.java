package backlogs.dinamico.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OfficeUpdateRequest {

    private String name;
    private String address;
    private String country;
    private String stateId;
    private String municipalityId;
    private String status;

}
