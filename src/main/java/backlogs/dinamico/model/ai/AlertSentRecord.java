package backlogs.dinamico.model.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alert_sent_records")
@CompoundIndexes({
        @CompoundIndex(
                name = "ux_tenant_system_day",
                def = "{'tenant_id':1,'system':1,'dayKey':1}",
                unique = true
        )
})
public class AlertSentRecord {

    @Id
    private ObjectId id;

    @Field("tenant_id")
    private ObjectId tenantId;

    @Field("system")
    private String system;

    @Field("day_key")
    private String dayKey;

    @Field("sent_at")
    private Instant sentAt;

    @Field("error_rate")
    private double errorRate;

    @Field("total")
    private long total;

    @Field("type")
    private String type;

}
