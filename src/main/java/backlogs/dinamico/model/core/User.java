package backlogs.dinamico.model.core;
import  backlogs.dinamico.model.base.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;


import java.time.Instant;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "users")
@CompoundIndexes({
@CompoundIndex(name = "ux_user_tenant_email", def = "{ 'tenant_id': 1, 'email': 1 }", unique = true),
@CompoundIndex(name = "ix_user_tenant_status", def = "{ 'tenant_id': 1, 'status': 1 }")
})
public class User extends BaseEntity {

    @Field("tenant_id")
    @JsonIgnore
    private ObjectId tenantId; // ref organizations

    @JsonProperty("tenantId")        // expón string hex
    public String getTenantIdHex() {
        return tenantId != null ? tenantId.toHexString() : null;
    }


    private String email; // correo de acceso

    private String name;

    @Field("password_hash")
    @JsonIgnore
    private String passwordHash;

    private String status; // active|disabled|invited

    @Field("last_login_at")
    private Instant lastLoginAt;
}