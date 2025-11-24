package backlogs.dinamico.model.core;

import backlogs.dinamico.model.base.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "password_reset_tokens")
@CompoundIndexes({
        @CompoundIndex(
                name = "ix_reset_tenant_token",
                def = "{ 'tenant_id': 1, 'token_hash': 1 }",
                unique = true
        ),
        @CompoundIndex(
                name = "ix_reset_expires_at",
                def = "{ 'expires_at': 1 }"
        )
})
public class PasswordResetToken extends BaseEntity {

    @Field("tenant_id")
    private ObjectId tenantId;

    @Field("user_id")
    private ObjectId userId;

    @Field("token_hash")
    private String tokenHash;

    @Field("expirest_at")
    private Instant expirestAt;

    @Field("used_at")
    private Instant usedAt;

}
