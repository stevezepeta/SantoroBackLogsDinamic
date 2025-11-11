package backlogs.dinamico.model.ingest;



import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;


import java.time.Instant;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "api_keys")
@CompoundIndexes({
        @CompoundIndex(name = "ix_tenant_sys_env_status",
                def = "{ 'tenant_id': 1, 'system_id': 1, 'environment_id': 1, 'status': 1 }"),
        @CompoundIndex(name = "ux_api_key_hash", def = "{ 'key_hash': 1 }", unique = true)
})
public class ApiKey extends BaseEntity {

    @Field("tenant_id")
    private ObjectId tenantId;

    @Field("system_id")
    private ObjectId systemId;

    @Field("environment_id")
    private ObjectId environmentId;

    private String name;

    @Field("key_hash")
    private String keyHash;

    private List<String> scopes;

    private String status;

    @Field("last_used_at")
    private Instant lastUsedAt;

    @Field("expires_at")
    private Instant expiresAt;

    @Field("rotates_at")
    private Instant rotatesAt;

    // ------ Helper Opcional ------
    public boolean isActiveNow() {
        return "active".equalsIgnoreCase(status)
                && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }

}