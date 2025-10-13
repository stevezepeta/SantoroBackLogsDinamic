package backlogs.dinamico.model.alerting;
import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "alert_channels")
@CompoundIndexes({
@CompoundIndex(name = "ix_channel_tenant_type", def = "{ 'tenant_id': 1, 'type': 1 }")
})
public class AlertChannel extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
private String type; // email|slack|webhook
private Document config; // parámetros del canal
private String name;
private String status;
}