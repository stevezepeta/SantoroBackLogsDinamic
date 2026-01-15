package backlogs.dinamico.model.core;

import backlogs.dinamico.model.base.BaseEntity;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@Document(collection = "user_roles")
@CompoundIndexes({
        // Un usuario no puede repetir el mismo rol dentro del mismo tenant id
    @CompoundIndex(
            name = "ux_user_role",
            def = "{ 'tenant_id': 1, 'user_id': 1, 'role_id': 1 }",
            unique = true),

    @CompoundIndex(
            name = "ix_user_role_user",
            def = "{ 'tenant_id': 1, 'user_id': 1 }"
    )
})
public class UserRole extends BaseEntity {

    @NotNull
    @Field("tenant_id")
    private ObjectId tenantId;

    @NotNull
    @Field("user_id")
    private ObjectId userId;

    @NotNull
    @Field("role_id")
    private ObjectId roleId;

    @Builder.Default
    @Field("allowed_systems")
    private Set<String> allowedSystems = new HashSet<>();

    public static UserRole of(ObjectId tenantId, ObjectId userId, ObjectId roleId) {
        return UserRole.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(roleId)
                .build();
    }

    public static UserRole of(ObjectId tenantId, ObjectId userId, ObjectId roleId, Set<String> allowedSystems) {
        return UserRole.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(roleId)
                .allowedSystems(allowedSystems != null ? allowedSystems : new HashSet<>())
                .build();
    }

}