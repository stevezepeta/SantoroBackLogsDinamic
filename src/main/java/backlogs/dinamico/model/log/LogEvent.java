package backlogs.dinamico.model.log;

import backlogs.dinamico.model.base.BaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class LogEvent extends BaseEntity {
  @Field("tenant_id")       private ObjectId tenantId;
  @Field("system_id")       private ObjectId systemId;
  @Field("environment_id")  private ObjectId environmentId;

  @Field("schema_id")       private ObjectId schemaId;    // opcional
  @Field("session_id")      private ObjectId sessionId;   // opcional
  @Field("device_id")       private ObjectId deviceId;    // opcional
  @Field("office_id")       private ObjectId officeId;    // opcional
  @Field("event_type_id")   private ObjectId eventTypeId; // opcional
  @Field("error_code_id")   private ObjectId errorCodeId; // opcional

  @Field("ingested_at")     private Instant ingestedAt;
  @Field("event_at")        private Instant eventAt;
  private String severity;   // DEBUG|INFO|WARN|ERROR|FATAL
  @Field("trace_id")        private String traceId;
  @Field("span_id")         private String spanId;

  private Document source;   // ip, host, agent...
  private Document payload;  // cuerpo libre
}
