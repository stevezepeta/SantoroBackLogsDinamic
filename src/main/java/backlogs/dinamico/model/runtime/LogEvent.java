package backlogs.dinamico.model.runtime;


import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;


import java.time.Instant;


@Data @NoArgsConstructor @AllArgsConstructor @Builder
@org.springframework.data.mongodb.core.mapping.Document(collection = "log_events")
@CompoundIndexes({
@CompoundIndex(name = "ix_main_time", def = "{ 'tenant_id': 1, 'system_id': 1, 'environment_id': 1, 'event_at': 1 }"),
@CompoundIndex(name = "ix_sev_time", def = "{ 'tenant_id': 1, 'severity': 1, 'event_at': 1 }"),
@CompoundIndex(name = "ix_err_time", def = "{ 'tenant_id': 1, 'error_code_id': 1, 'event_at': 1 }")
})
public class LogEvent extends BaseEntity {
    @Field("tenant_id")
    private ObjectId tenantId;
    @Field("system_id")
    private ObjectId systemId;
    @Field("environment_id")
    private ObjectId environmentId;
    @Field("schema_id")
    private ObjectId schemaId;
    @Field("session_id")
    @Indexed
    private ObjectId sessionId;
    @Field("device_id")
    @Indexed
    private ObjectId deviceId;
    @Field("office_id")
    private ObjectId officeId;
    @Field("event_type_id")
    private ObjectId eventTypeId;
    @Field("error_code_id")
    private ObjectId errorCodeId;
    @Field("ingested_at")
    private Instant ingestedAt; // cuando llegó al backend
    @Field("event_at")
    private Instant eventAt; // cuando ocurrió en origen
    private String severity; // DEBUG|INFO|WARN|ERROR|FATAL
    @Field("trace_id")
    @Indexed
    private String traceId;
    @Field("span_id")
    private String spanId;
    private Document source; // ip, host, agent, env vars
    private Document payload; // cuerpo validado con schema activo
}