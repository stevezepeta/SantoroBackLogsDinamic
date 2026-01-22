package backlogs.dinamico.model.catalog;

import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "systems")
@CompoundIndexes({
    @CompoundIndex(name = "ux_system_code", def = "{ 'tenant_id': 1, 'code': 1 }", unique = true),
    @CompoundIndex(name = "ix_system_tenant_status", def = "{ 'tenant_id': 1, 'status': 1 }")
})
public class SystemApp extends BaseEntity {
    @Field("tenant_id")
    private ObjectId tenantId;

    private String name;

    private String code; // slug único por tenant

    private String description;

    private String status; // active|disabled
}