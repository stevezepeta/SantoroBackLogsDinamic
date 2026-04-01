package backlogs.dinamico.model.runtime;



import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "attachments")
@CompoundIndexes({
@CompoundIndex(name = "ix_attachment_tenant_created", def = "{ 'tenant_id': 1, 'created_at': 1 }")
})
public class Attachment extends BaseEntity {
@Field("tenant_id")
private ObjectId tenantId;
@Field("log_event_id")
@Indexed
private ObjectId logEventId;
private String kind; // image|pdf|json|zip|other
private String storage; // gridfs|s3|gcs|fs
private String path; // bucket/key o gridfs id
@Field("size_bytes")
private Integer sizeBytes;
}