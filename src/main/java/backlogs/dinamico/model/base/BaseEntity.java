package backlogs.dinamico.model.base;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.Instant;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@SuperBuilder
public abstract class BaseEntity {
  @Id
  protected ObjectId id;

  @CreatedDate @Field("created_at")
  protected Instant createdAt;

  @LastModifiedDate @Field("updated_at")
  protected Instant updatedAt;
}
