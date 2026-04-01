package backlogs.dinamico.model.alerting;


import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;


import java.time.Instant;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "alert_incidents")
@CompoundIndexes({
@CompoundIndex(name = "ix_incident_tenant_rule_status", def = "{ 'tenant_id': 1, 'rule_id': 1, 'status': 1 }"),
@CompoundIndex(name = "ix_incident_opened_at", def = "{ 'opened_at': 1 }")
})
public class AlertIncident extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
@Field("rule_id")
private ObjectId ruleId;
@Field("opened_at")
private Instant openedAt;
@Field("closed_at")
private Instant closedAt;
private String status; 
private Document context;
}