package backlogs.dinamico.model.schema;



import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Field;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "schemas")
@CompoundIndexes({
@CompoundIndex(name = "ux_schema_ver", def = "{ 'system_id': 1, 'environment_id': 1, 'name': 1, 'version': 1 }", unique = true),
@CompoundIndex(name = "ix_schema_status", def = "{ 'status': 1 }")
})
public class SchemaDef extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
@Field("system_id")
private ObjectId systemId;
@Field("environment_id")
private ObjectId environmentId;
private String name; // p.ej. scan_log
private Integer version;
@Field("json_schema")
private org.bson.Document jsonSchema; // JSON Schema
private String status; // active|deprecated
}