package backlogs.dinamico.model.core;


import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "roles")
@CompoundIndex(name = "code_unique", def = "{ 'code': 1 }", unique = true)
public class Role extends BaseEntity {
private String code; // SUPER_ADMIN | TENANT_ADMIN | DEVELOPER | VIEWER
private String name;
private String description;
}