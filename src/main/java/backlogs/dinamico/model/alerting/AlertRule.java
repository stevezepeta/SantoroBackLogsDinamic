package backlogs.dinamico.model.alerting;


import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;


import java.util.List;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "alert_rules")
@CompoundIndexes({
@CompoundIndex(name = "ix_rule_tenant_system_status", def = "{ 'tenant_id': 1, 'system_id': 1, 'status': 1 }")
})
public class AlertRule extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
@Field("system_id")
private ObjectId systemId;
private String name;
private Document query; 
private Document threshold;
private List<ObjectId> channels; 
private String status; 
}