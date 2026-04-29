package backlogs.dinamico.model.log;

import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "devices_registry")
@CompoundIndexes({
        @CompoundIndex(name = "idx_device_tenant_deviceId", def = "{'tenantId': 1, 'deviceId': 1}", unique = true)
})
public class Device {

    @Id
    private ObjectId id;

    private ObjectId tenantId;

    private String deviceId;
    private String hostname;
    private String system;
    private String type;

    private String ip;
    private Double latitude;
    private Double longitude;
    private String locationName;

    private Instant firstSeen;
    private Instant lastSeen;

    private Instant lastPowerOn;
    private Instant lastPowerOff;

    private String status;

}
