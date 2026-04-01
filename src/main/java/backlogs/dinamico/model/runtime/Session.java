package backlogs.dinamico.model.runtime;


import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;


import java.time.Instant;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "sessions")
@CompoundIndexes({
@CompoundIndex(name = "ux_session_token", def = "{ 'tenant_id': 1, 'system_id': 1, 'session_token': 1 }", unique = true),
@CompoundIndex(name = "ix_session_started_at", def = "{ 'started_at': 1 }")
})
public class Session extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
@Field("system_id")
private ObjectId systemId;
@Field("environment_id")
private ObjectId environmentId;
@Field("session_token")
private String sessionToken; // token backend origen
@Field("user_ref")
private String userRef; // id/correo/curp del sistema origen
@Field("started_at")
private Instant startedAt;
@Field("ended_at")
private Instant endedAt;
private Document metadata;
}