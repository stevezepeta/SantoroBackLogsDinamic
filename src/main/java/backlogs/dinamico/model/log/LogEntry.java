package backlogs.dinamico.model.log;

import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "logs")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_ts_desc", def = "{ 'tenantId': 1, 'timestamp': -1 }"),
        @CompoundIndex(name = "tenant_proc_ts", def = "{ 'tenantId': 1, 'processType': 1, 'timestamp': -1 }"),
        @CompoundIndex(name = "tenant_error_ts", def = "{ 'tenantId': 1, 'errorCode': 1, 'timestamp': -1 }"),
        @CompoundIndex(name = "tenant_session", def = "{ 'tenantId': 1, 'sessionToken': 1 }"),
        @CompoundIndex(name = "tenant_office_ts", def = "{ 'tenantId': 1, 'officeId': 1, 'timestamp': -1 }")
})
public class LogEntry {

    @Id
    private ObjectId id;

    // Multi tenant
    @Indexed
    private ObjectId tenantId;

    @Indexed
    private Instant timestamp;

    @Indexed
    private String level;
    private String message;

    @Indexed
    private String processType;
    private String device;
    private String scanDevice;
    private String scanType;

    @Indexed
    private String officeId;

    @Indexed
    private String personId;

    @Indexed
    private String baseCode;

    @Indexed
    private String errorCode;

    @Indexed
    private String sessionToken;

    private String system;
    private String environment;

    private Map<String, Object> extra;

    @Indexed
    private Instant ingestAt;

}
