package backlogs.dinamico.model.core;

import backlogs.dinamico.model.core.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "ux_invite_token", def = "{ 'token': 1 }", unique = true),
        @CompoundIndex(name = "idx_invite_tenant_email_status",
                def = "{ 'tenant_id': 1, 'email_ci': 1, 'status': 1 }")
})
public class UserInvite {

    @Id
    private ObjectId id;

    @Field("tenant_id")
    private ObjectId tenantId;

    private String email;

    @Field("email_ci")
    private String emailCi;

    private List<String> roles;

    @Field("systems")
    private List<String> systems;

    /**
     * Filtros de visibilidad de logs asignados al invitado.
     * Se persisten aquí y se copian al UserRole cuando se acepta la invitación.
     */
    @Field("log_filters")
    private UserRole.LogFilter logFilters;

    private String token;
    private Instant expiresAt;

    // PENDING | ACCEPTED | EXPIRED | CANCELLED
    private String status;

    @Field("accepted_by")
    private ObjectId acceptedBy;

    private Instant createdAt;
    private Instant updateAt;
}