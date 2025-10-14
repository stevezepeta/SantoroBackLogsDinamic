package backlogs.dinamico.model.catalog;



import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "devices")
@CompoundIndexes({
@CompoundIndex(name = "ux_device_unique", def = "{ 'tenant_id': 1, 'system_id': 1, 'code': 1 }", unique = true),
@CompoundIndex(name = "ix_device_location", def = "{ 'location_id': 1 }")
})
public class Device extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
@Field("system_id")
private ObjectId systemId;
@Field("location_id")
private ObjectId locationId; 
private String code; 
private String model;
private String status; 
}