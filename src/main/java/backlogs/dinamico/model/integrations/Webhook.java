package backlogs.dinamico.model.integrations;

import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "webhooks")
@CompoundIndexes({
@CompoundIndex(name = "ix_webhook_tenant_system_status", def = "{ 'tenant_id': 1, 'system_id': 1, 'status': 1 }")
})
public class Webhook extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
@Field("system_id")
private ObjectId systemId;
private String name;
private String url;
private String secret;
private String status;
private Document events; // tipos de evento a enviar
}