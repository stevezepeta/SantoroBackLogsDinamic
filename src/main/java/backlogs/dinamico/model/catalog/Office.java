package backlogs.dinamico.model.catalog;


import  backlogs.dinamico.model.base.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "offices")
@CompoundIndexes({
        @CompoundIndex(
                name = "ix_office_tenant_org_name",
                def = "{'tenant_id':1, 'name':1}",
                unique = true
        ),
        @CompoundIndex(
                name = "ix_office_status",
                def = "{'status':1}"
        )
})
public class Office extends BaseEntity {

    @Field("tenant_id")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private ObjectId tenantId;

    private String name;
    private String address;

    @Field("country_id")
    private String countryId;

    @Field("state_id")
    private String stateId;

    @Field("municipality_id")
    private String municipalityId;

    private String status;

    @Field("geo")
    private GeoPoint geo;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoPoint {
        private String type;
        private List<Double> coordinates;
    }

    public String getIdHex() {
        return getId() != null ? getId().toHexString() : null;
    }

}