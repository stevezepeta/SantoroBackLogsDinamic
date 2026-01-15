package backlogs.dinamico.model.core;

import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@Document(collection = "roles")
@CompoundIndexes({
        @CompoundIndex(name="ux_role_tenant_code",
                def="{ 'tenant_id':1, 'code':1 }",
                unique=true),
        @CompoundIndex(name="ix_role_tenant_name",
                def="{ 'tenant_id':1, 'name':1 }")
})
public class Role extends BaseEntity {

    @Field("tenant_id")
    private ObjectId tenantId;

    @Field("code")
    private RoleCode code;

    private String name;
    private String description;

    private boolean orgWide;       // ve toda la organización
    private boolean systemScoped;  // requiere systems permitidos

    @Builder.Default
    private Set<PermissionCode> permissions = new HashSet<>();

    // Si no lo usas, bórralo. Si lo usas, implementa campos.
    public Role(RoleCode code, String name, String desc,
                boolean orgWide, boolean systemScoped,
                EnumSet<PermissionCode> perms) {

        this.code = code;
        this.name = name;
        this.description = desc;
        this.orgWide = orgWide;
        this.systemScoped = systemScoped;
        this.permissions = (perms == null) ? new HashSet<>() : new HashSet<>(perms);
    }
}
