package backlogs.dinamico.model.catalog;



import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "event_types")
@CompoundIndexes({
@CompoundIndex(name = "ux_evt_type_code", def = "{ 'tenant_id': 1, 'code': 1 }", unique = true)
})
public class EventType extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
private String code; // LOGIN, ENROLL, VERIFY, ...
private String name;
private String description;
}