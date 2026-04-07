package backlogs.dinamico.model.log;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Document("log_events")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@CompoundIndexes({
        // Renombrados a v2 para evitar colisiones con índices viejos tenant_id vs tenantId
        @CompoundIndex(name = "idx_tenant_system_time_v2",
                def = "{'tenant_id':1,'system':1,'eventTime':-1}"),

        @CompoundIndex(name = "idx_tenant_system_case_time_v2",
                def = "{'tenant_id':1,'system':1,'caseId':1,'eventTime':-1}"),

        @CompoundIndex(name = "idx_tenant_system_eventType_time_v2",
                def = "{'tenant_id':1,'system':1,'eventType':1,'eventTime':-1}"),

        @CompoundIndex(name = "idx_tenant_system_status_time_v2",
                def = "{'tenant_id':1,'system':1,'status':1,'eventTime':-1}"),

        @CompoundIndex(name = "idx_tenant_system_outcome_time_v2",
                def = "{'tenant_id':1,'system':1,'outcome':1,'eventTime':-1}"),

        @CompoundIndex(name = "idx_tenant_system_severity_time_v2",
                def = "{'tenant_id':1,'system':1,'severity':1,'eventTime':-1}"),

        @CompoundIndex(name = "idx_tenant_system_requestId_time_v2",
                def = "{'tenant_id':1,'system':1,'correlation.requestId':1,'eventTime':-1}"),

        @CompoundIndex(name = "idx_tenant_system_actor_time_v2",
                def = "{'tenant_id':1,'system':1,'actor.id':1,'eventTime':-1}"),

        @CompoundIndex(name = "idx_tenant_system_location_time_v2",
                def = "{'tenant_id':1,'system':1,'location.id':1,'eventTime':-1}"),

        @CompoundIndex(name = "idx_tenant_time_isError_msgKey_v1",
                def = "{'tenant_id':1,'eventTime':-1,'isError':1,'messageKey':1}"),
        @CompoundIndex(name = "idx_tenant_system_eventCode_time_v2",
                def = "{'tenant_id':1,'system':1,'eventCode':1,'eventTime':-1}"),
})
public class LogEvent {

  @Id
  private ObjectId id;

  @Field("tenant_id")
  private ObjectId tenantId;

  @Builder.Default
  @Field("schemaVersion")
  private Integer schemaVersion = 1;

  @Field("system")
  private String system;
  @Field("environment")
  private String environment;

  @Field("caseId")
  private String caseId;
  @Field("eventTime")
  private Instant eventTime;

  @Field("eventType")
  private String eventType;

  @Field("eventCode")
  private String eventCode;

  @Field("eventTypeRaw")
  private String eventTypeRaw;

  @Field("status")
  private String status;
  @Field("outcome")
  private String outcome;

  @Field("severity")
  private String severity;
  @Field("severityRaw")
  private String severityRaw;

  @Field("message")
  private String message;

  // GeoJSON Point => coords: [lng, lat]
  @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE, name = "geo_2dsphere")
  @Field("geo")
  private GeoPoint geo;

  @Field("actor")
  private Actor actor;
  @Field("location")
  private Location location;
  @Field("correlation")
  private Correlation correlation;
  @Field("http")
  private HttpInfo http;
  @Field("sla")
  private SlaInfo sla;
  @Field("reason")
  private ReasonInfo reason;

  @Builder.Default
  @Field("tags")
  private List<String> tags = new ArrayList<>();

  @Field("payload")
  private Map<String, Object> payload;
  @Field("meta")
  private Map<String, Object> meta;


  @Field("messageKey")
  private String messageKey;
  @Field("isError")
  private Boolean isError;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class GeoPoint {
    @Field("type")
    private String type;                 // "Point"

    @Field("coordinates")
    private List<Double> coordinates;    // [lng, lat]

    @Field("accuracyMeters")
    private Integer accuracyMeters;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Actor {

    @Field("id")
    private String id;       // actorId real

    @Field("type")
    private String type;     // USER | SERVICE | DEVICE | etc

    @Field("username")
    private String username;

    @Field("fullName")
    private String fullName;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Location {

    @Field("id")
    private String id;       // locationId real

    @Field("name")
    private String name;     // locationName real

    @Field("city")
    private String city;

    @Field("country")
    private String country;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Correlation {

    @Field("requestId")
    private String requestId;

    @Field("traceId")
    private String traceId;

    @Field("spanId")
    private String spanId;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class HttpInfo {

    @Field("method")
    private String method;

    @Field("path")
    private String path;

    @Field("statusCode")
    private Integer statusCode;

    @Field("latencyMs")
    private Long latencyMs;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SlaInfo {

    @Field("startTime")
    private Instant startTime;

    @Field("endTime")
    private Instant endTime;

    @Field("elapsedSeconds")
    private Long elapsedSeconds;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ReasonInfo {

    @Field("code")
    private String code;

    @Field("description")
    private String description;
  }
}
