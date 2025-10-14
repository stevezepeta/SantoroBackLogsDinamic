package backlogs.dinamico.model.catalog;

import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@Document(collection = "api_keys")
@CompoundIndexes({
  @CompoundIndex(name = "ix_api_key_status", def = "{ 'status': 1 }")
})
public class ApiKey extends BaseEntity {
  @Field("tenant_id")  private ObjectId tenantId;
  @Field("system_id")  private ObjectId systemId;
  @Field("environment_id") private ObjectId environmentId;

  private String name;

  @Indexed(name = "ux_api_key", unique = true)
  private String key;          

  private String status;       
  @Field("rotates_at") private java.time.Instant rotatesAt;
}
