package backlogs.dinamico.service.ingest;

import backlogs.dinamico.tenant.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Administra el nombre de la colección de logs y asegura los índices requeridos.
 */
@Component
@RequiredArgsConstructor
public class LogEventIndexManager {

  private final MongoTemplate mongoTemplate;

  // single | collection-per-tenant | database-per-tenant
  @Value("${multitenant.strategy:single}")
  private String strategy;

  // nombre base de la colección de logs (por ejemplo "log_events")
  @Value("${multitenant.log-collection:log_events}")
  private String baseCollection;

  /**
   * Devuelve el nombre de la colección de logs según la estrategia y el TenantContext.
   * - single                 -> baseCollection
   * - collection-per-tenant  -> baseCollection + ctx.collectionSuffix (p.ej. "__<tenantIdHex>")
   * - database-per-tenant    -> también baseCollection (la separación ya se hace por base de datos)
   */
  public String resolveCollectionName() {
    var ctx = TenantContextHolder.get(); // puede ser null si vienes de un endpoint sin filtro
    if ("collection-per-tenant".equalsIgnoreCase(strategy) && ctx != null) {
      var suf = ctx.getCollectionSuffix();
      if (suf != null && !suf.isBlank()) {
        return baseCollection + suf;
      }
    }
    return baseCollection;
  }

  /** Asegura los índices en la colección calculada. */
  public void ensureIndexes() {
    ensureIndexes(resolveCollectionName());
  }

  /** Asegura los índices en la colección indicada. */
  public void ensureIndexes(String collection) {
    IndexOperations ops = mongoTemplate.indexOps(collection);

    // Índices compuestos (con tenant_id para que coincidan con lo ya creado)
    ops.ensureIndex(new Index()
        .on("tenant_id", Sort.Direction.ASC)
        .on("system_id", Sort.Direction.ASC)
        .on("environment_id", Sort.Direction.ASC)
        .on("event_at", Sort.Direction.ASC)
        .named("ix_main_time"));

    ops.ensureIndex(new Index()
        .on("tenant_id", Sort.Direction.ASC)
        .on("severity", Sort.Direction.ASC)
        .on("event_at", Sort.Direction.ASC)
        .named("ix_sev_time"));

    ops.ensureIndex(new Index()
        .on("tenant_id", Sort.Direction.ASC)
        .on("error_code_id", Sort.Direction.ASC)
        .on("event_at", Sort.Direction.ASC)
        .named("ix_err_time"));

    // Índices simples: usar los NOMBRES que ya existen para evitar conflictos
    ops.ensureIndex(new Index().on("session_id", Sort.Direction.ASC).named("session_id"));
    ops.ensureIndex(new Index().on("trace_id",   Sort.Direction.ASC).named("trace_id"));
    ops.ensureIndex(new Index().on("device_id",  Sort.Direction.ASC).named("device_id"));
  }
}
