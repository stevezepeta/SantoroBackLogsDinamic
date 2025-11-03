package backlogs.dinamico.model.core;


import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    private String code; // SUPER_ADMIN | TENANT_ADMIN | DEVELOPER | VIEWER

    @Field("name")
    private String name;

    @Field("description")
    private String description;
}