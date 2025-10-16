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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LogIngestService {

  private final MongoTemplate mongo;
  private final LogEventIndexManager indexManager;

  @Value("${multitenant.ensure-indexes:true}")
  private boolean ensureIndexes;

  /** Para entornos dev, si no viene tenant por header/contexto. Debe ser un ObjectId válido (24 hex). */
  @Value("${multitenant.dev.fallback-tenant-id:}")
  private String fallbackTenantId;

  private static ObjectId parseObjectId(String s) {
    return (s != null && ObjectId.isValid(s)) ? new ObjectId(s) : null;
  }

  public ObjectId ingest(LogIngestRequest in) {
    // Contexto multi-tenant desde filtro de headers
    var ctx = TenantContextHolder.get();

    ObjectId tenantId = (ctx != null && ctx.getTenantId() != null)
        ? ctx.getTenantId() : parseObjectId(fallbackTenantId);

    ObjectId systemId = (ctx != null) ? ctx.getSystemId() : null;
    ObjectId environmentId = (ctx != null) ? ctx.getEnvironmentId() : null;

    // Colección según estrategia (single / collection-per-tenant / database-per-tenant)
    String collection = indexManager.resolveCollectionName();
    if (ensureIndexes) {
      indexManager.ensureIndexes(collection);
    }

    // Normaliza documentos fuente/payload para evitar nulls
    Document source  = in.getSource()  != null ? new Document(in.getSource())  : new Document();
    Document payload = in.getPayload() != null ? new Document(in.getPayload()) : new Document();

    // Defaults seguros
    Instant now = Instant.now();
    Instant eventAt = in.getEventAt() != null ? in.getEventAt() : now;
    String severity = Objects.requireNonNullElse(in.getSeverity(), "INFO");

    // Construir evento
    LogEvent ev = LogEvent.builder()
        .tenantId(tenantId)
        .systemId(systemId)
        .environmentId(environmentId)
        .ingestedAt(now)
        .eventAt(eventAt)
        .severity(severity)
        .traceId(in.getTraceId())
        .spanId(in.getSpanId())
        .source(source)
        .payload(payload)
        .build();

    // Guardar
    mongo.save(ev, collection);
    return ev.getId();
  }
}
