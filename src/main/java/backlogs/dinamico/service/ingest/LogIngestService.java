package backlogs.dinamico.service.ingest;

import backlogs.dinamico.api.ingest.dto.LogIngestRequest;
import backlogs.dinamico.model.log.LogEvent;
import backlogs.dinamico.tenant.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LogIngestService {

  private final MongoTemplate mongo;
  private final LogEventIndexManager indexManager;

  @Value("${multitenant.ensure-indexes:true}") boolean ensureIndexes;

  @Value("${multitenant.dev.fallback-tenant-id:}")
  private String fallbackTenantId;

  private ObjectId parseObjectId(String s) {
    return (s != null && ObjectId.isValid(s)) ? new ObjectId(s) : null;
  }

  public ObjectId ingest(LogIngestRequest in) {

    var ctx = TenantContextHolder.get();

    ObjectId tenantId = (ctx != null && ctx.getTenantId() != null)
        ? ctx.getTenantId()
        : parseObjectId(fallbackTenantId);

    ObjectId systemId = (ctx != null) ? ctx.getSystemId() : null;
    ObjectId environmentId = (ctx != null) ? ctx.getEnvironmentId() : null;

    String collection = indexManager.resolveCollectionName();
    if (ensureIndexes) indexManager.ensureIndexes(collection);

    LogEvent ev = LogEvent.builder()
        .tenantId(tenantId)             
        .systemId(systemId)
        .environmentId(environmentId)
        .ingestedAt(Instant.now())
        .eventAt(in.getEventAt() != null ? in.getEventAt() : Instant.now())
        .severity(in.getSeverity())
        .traceId(in.getTraceId())
        .spanId(in.getSpanId())
        .source(in.getSource() != null ? in.getSource() : new Document())
        .payload(in.getPayload() != null ? in.getPayload() : new Document())
        .build();

    mongo.save(ev, collection);
    return ev.getId();
  }
}
