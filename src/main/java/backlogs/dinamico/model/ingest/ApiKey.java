package backlogs.dinamico.model.ingest;



import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;


import java.time.Instant;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "api_keys")
@CompoundIndexes({
@CompoundIndex(name = "ix_key_sys_env_status", def = "{ 'system_id': 1, 'environment_id': 1, 'status': 1 }"),
@CompoundIndex(name = "ux_api_key", def = "{ 'key': 1 }", unique = true)
})
public class ApiKey extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
@Field("system_id")
private ObjectId systemId;
@Field("environment_id")
private ObjectId environmentId;
private String name;
private String key; // secreto
private String status; // active|revoked
@Field("rotates_at")
private Instant rotatesAt;
}