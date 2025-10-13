package backlogs.dinamico.model.base;


import lombok.*;
import lombok.experimental.SuperBuilder;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Field;


import java.time.Instant;


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseEntity {
@Id
protected ObjectId id;


@CreatedDate
@Field("created_at")
protected Instant createdAt;


@LastModifiedDate
@Field("updated_at")
protected Instant updatedAt;
}