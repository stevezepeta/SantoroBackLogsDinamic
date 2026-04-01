package backlogs.dinamico.api.dto;

import backlogs.dinamico.model.catalog.Office;

public class OfficeMapper {

    public static OfficeResponse toResponse(Office e) {

        if (e == null) return null;

        OfficeResponse.Geo geo = null;
        if (e.getGeo() != null) {
            geo = OfficeResponse.Geo.builder()
                    .type(e.getGeo().getType())
                    .coordinates(e.getGeo().getCoordinates())
                    .build();
        }

        return OfficeResponse.builder()
                .tenantId(e.getId())
                .name(e.getName())
                .address(e.getAddress())
                .countryId(e.getCountryId())
                .stateId(e.getStateId())
                .municipalityId(e.getMunicipalityId())
                .status(e.getStatus())
                .geo(geo)
                .build();

    }

}
