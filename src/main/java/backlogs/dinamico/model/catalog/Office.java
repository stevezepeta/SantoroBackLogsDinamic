package backlogs.dinamico.model.catalog;



import  backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "offices")
@CompoundIndexes({
@CompoundIndex(name = "ix_office_tenant_name", def = "{ 'tenant_id': 1, 'name': 1 }"),
@CompoundIndex(name = "ix_office_geo", def = "{ 'country': 1, 'state': 1, 'city': 1 }")
})
public class Office extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
private String name;
private String address;
private String country;
private String state;
private String city;
private Document geo; // { type: "Point", coordinates: [lng, lat] }
private String status; // active|disabled
}