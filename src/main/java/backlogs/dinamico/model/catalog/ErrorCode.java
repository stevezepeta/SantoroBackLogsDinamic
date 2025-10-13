package backlogs.dinamico.model.catalog;

import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@SuperBuilder
@Document(collection = "error_codes")
@CompoundIndexes({
  @CompoundIndex(name = "ux_error_code", def = "{ 'tenant_id': 1, 'system_id': 1, 'code': 1 }", unique = true),
  @CompoundIndex(name = "ix_error_severity", def = "{ 'severity': 1 }")
})
public class ErrorCode extends BaseEntity {

  @Field("tenant_id")
  private ObjectId tenantId;

  @Field("system_id")
  private ObjectId systemId;

  private String code;
  private String severity;

  @Field("message_template")
  private String messageTemplate;

  private String description;
}
