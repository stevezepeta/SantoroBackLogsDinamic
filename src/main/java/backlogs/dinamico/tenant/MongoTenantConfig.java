package backlogs.dinamico.tenant;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableMongoRepositories(basePackages = "backlogs.dinamico.repository")
public class MongoTenantConfig {

  private static final String LOG_EVENTS_COLLECTION = "log_events";

  @Value("${spring.data.mongodb.uri}")
  private String mongoUri;

  @Bean
  public MongoClient mongoClient() {
    return MongoClients.create(mongoUri);
  }

  @Bean
  @Primary
  public MongoDatabaseFactory mongoDatabaseFactory(
      MongoClient mongoClient,
      @Value("${multitenant.base-database}") String baseDatabase
  ) {
    return new TenantRoutingMongoDbFactory(mongoClient, baseDatabase);
  }

  @Bean
  @Primary
  public MongoTemplate mongoTemplate(MongoDatabaseFactory factory, MongoConverter converter) {
    return new MongoTemplate(factory, converter);
  }

  /**
   * Crea los índices operativos de log_events de forma segura al arrancar.
   * Los conflictos con índices existentes (mismo patrón, otro nombre) se ignoran
   * para que la aplicación arranque limpiamente.
   */
  @Bean
  public ApplicationRunner logEventsIndexInitializer(MongoTemplate mongoTemplate) {
    return (ApplicationArguments args) -> {
      IndexOperations indexOps = mongoTemplate.indexOps(LOG_EVENTS_COLLECTION);

      ensureIndex(indexOps, new Index()
          .on("system", Sort.Direction.ASC)
          .on("eventTime", Sort.Direction.DESC)
          .named("idx_system_time_v1"));

      ensureIndex(indexOps, new Index()
          .on("system", Sort.Direction.ASC)
          .on("eventTime", Sort.Direction.DESC)
          .on("status", Sort.Direction.ASC)
          .on("outcome", Sort.Direction.ASC)
          .on("severity", Sort.Direction.ASC)
          .named("idx_system_time_status_outcome_severity_v1"));

      ensureIndex(indexOps, new Index()
          .on("eventTime", Sort.Direction.DESC)
          .on("status", Sort.Direction.ASC)
          .named("idx_time_status_v1"));
    };
  }

  private void ensureIndex(IndexOperations indexOps, Index index) {
    try {
      indexOps.ensureIndex(index);
      log.info("[MongoIndex] Índice verificado: {}", index.getIndexOptions().getString("name"));
    } catch (DataIntegrityViolationException | MongoCommandException e) {
      log.warn("[MongoIndex] Índice omitido por conflicto o existencia previa: {}", e.getMessage());
    }
  }
}
