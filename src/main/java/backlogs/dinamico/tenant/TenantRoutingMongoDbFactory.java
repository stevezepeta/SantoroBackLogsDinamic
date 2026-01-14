package backlogs.dinamico.tenant;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

public class TenantRoutingMongoDbFactory extends SimpleMongoClientDatabaseFactory {

  private final String defaultDb;

  public TenantRoutingMongoDbFactory(MongoClient mongoClient, String defaultDb) {
    super(mongoClient, defaultDb);
    this.defaultDb = defaultDb;
  }

  private String resolveDbName() {
    String dbName = TenantContext.getDbName(); // <- YA no TenantContextHolder
    if (StringUtils.hasText(dbName)) {
      return dbName;
    }
    return defaultDb;
  }

  @Override
  public @NonNull MongoDatabase getMongoDatabase() {
    return super.getMongoDatabase(resolveDbName());
  }

  @Override
  public @NonNull MongoDatabase getMongoDatabase(@NonNull String dbName) {
    return super.getMongoDatabase(dbName);
  }
}
