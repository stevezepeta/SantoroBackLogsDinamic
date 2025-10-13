package backlogs.dinamico.model.core;


import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.bson.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;


@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@org.springframework.data.mongodb.core.mapping.Document(collection = "organizations")
@CompoundIndexes({
@CompoundIndex(name = "ux_org_domain", def = "{ 'domain': 1 }", unique = true),
@CompoundIndex(name = "ix_org_status", def = "{ 'status': 1 }")
})
public class Organization extends BaseEntity {
private String name; // Nombre comercial
@Indexed(unique = true)
private String domain; // Dominio principal
private String status; // active|disabled
private Document settings; // Retención, zona horaria, límites, etc.
}