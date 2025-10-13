package backlogs.dinamico.model.core;

import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "user_roles")
@CompoundIndexes({
@CompoundIndex(name = "ux_user_role", def = "{ 'tenant_id': 1, 'user_id': 1, 'role_id': 1 }", unique = true),
@CompoundIndex(name = "ix_user_role_user", def = "{ 'user_id': 1 }")
})
public class UserRole extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
@Field("user_id")
private ObjectId userId; // ref users
@Field("role_id")
private ObjectId roleId; // ref roles
}