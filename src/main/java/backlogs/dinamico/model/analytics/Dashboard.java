package backlogs.dinamico.model.analytics;



import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "dashboards")
@CompoundIndexes({
  @CompoundIndex(name = "ix_dash_tenant_owner", def = "{ 'tenant_id': 1, 'owner_id': 1 }"),
  @CompoundIndex(name = "ix_dash_public", def = "{ 'is_public': 1 }")
})
public class Dashboard extends BaseEntity {
  @Field("tenant_id") private ObjectId tenantId;
  @Field("owner_id") private ObjectId ownerId;
  private String name;
  private String description;
  private Document definition;
  @Field("is_public")
  private Boolean isPublic;   // << sin @Indexed aquí
}