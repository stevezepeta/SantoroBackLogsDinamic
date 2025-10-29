package backlogs.dinamico.model.core;


import backlogs.dinamico.model.base.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "roles")
//@CompoundIndex(name = "code_unique", def = "{ 'code': 1 }", unique = true)
@CompoundIndexes({
        @CompoundIndex(name="ux_role_tenant_code", def="{ 'tenant_id':1, 'code':1 }", unique=true),
        @CompoundIndex(name="ix_role_tenant_name", def="{ 'tenant_id':1, 'name':1 }")
})
public class Role extends BaseEntity {

    @Field("tenant_id")
    private ObjectId tenantId;

    private String code; // SUPER_ADMIN | TENANT_ADMIN | DEVELOPER | VIEWER

    private String name;

    private String description;
}