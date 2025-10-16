package backlogs.dinamico.model.catalog;


import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Field;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "environments")
@CompoundIndex(name = "ux_env_code", def = "{ 'tenant_id': 1, 'system_id': 1, 'code': 1 }", unique = true)
public class Environment extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
@Field("system_id")
private ObjectId systemId;
private String code; 
private String name;
private String status; 
}