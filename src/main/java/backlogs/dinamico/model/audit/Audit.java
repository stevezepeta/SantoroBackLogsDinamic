package backlogs.dinamico.model.audit;


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
@org.springframework.data.mongodb.core.mapping.Document(collection = "audits")
@CompoundIndexes({
@CompoundIndex(name = "ix_audit_tenant_at", def = "{ 'tenant_id': 1, 'at': 1 }"),
@CompoundIndex(name = "ix_audit_actor_at", def = "{ 'actor_id': 1, 'at': 1 }")
})
public class Audit extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
@Field("actor_id")
private ObjectId actorId; 
private String action; 
private Document target; 
private Document metadata;
@Indexed
private Instant at;
}