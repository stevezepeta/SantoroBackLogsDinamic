package backlogs.dinamico.model.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseEntity {

  @Id
  @JsonIgnore
  protected ObjectId id;

  @JsonProperty("id")  // Se expone el id de forma entendible
  public String getIdHex() {
    return id != null ? id.toHexString() : null;
  }

  @JsonProperty("id")
  public void setIdHex(String hex) {
    this.id = (hex != null && ObjectId.isValid(hex)) ? new ObjectId(hex) : null;
  }

  @CreatedDate
  @Field("created_at")
  protected Instant createdAt;

  @LastModifiedDate
  @Field("updated_at")
  protected Instant updatedAt;
}
