package backlogs.dinamico.tenant;

import com.mongodb.ClientSessionOptions;        
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoExceptionTranslator;
import org.springframework.lang.NonNull;


public class TenantRoutingMongoDbFactory implements MongoDatabaseFactory {

  private final MongoClient mongoClient;
  private final String defaultDb;
  private final PersistenceExceptionTranslator exceptionTranslator = new MongoExceptionTranslator();

  public TenantRoutingMongoDbFactory(MongoClient mongoClient, String defaultDb) {
    this.mongoClient = mongoClient;
    this.defaultDb = defaultDb;
  }

  private String resolveDbName() {
    var ctx = TenantContextHolder.get();
    if (ctx != null && ctx.getDbName() != null && !ctx.getDbName().isBlank()) {
      return ctx.getDbName();
    }
    return defaultDb;
  }


  @Override
  public @NonNull MongoDatabase getMongoDatabase() {
    return mongoClient.getDatabase(resolveDbName());
  }

  @Override
  public @NonNull MongoDatabase getMongoDatabase(@NonNull String dbName) {
    return mongoClient.getDatabase(dbName);
  }

  @Override
  public @NonNull PersistenceExceptionTranslator getExceptionTranslator() {
    return exceptionTranslator;
  }

  @Override
  public @NonNull ClientSession getSession(@NonNull ClientSessionOptions options) {
    return mongoClient.startSession(options);
  }

  @Override
  public @NonNull MongoDatabaseFactory withSession(@NonNull ClientSession session) {
    return new SessionBoundFactory(this, session);
  }

 
  private static final class SessionBoundFactory implements MongoDatabaseFactory {
    private final TenantRoutingMongoDbFactory parent;
    private final ClientSession session;

    private SessionBoundFactory(TenantRoutingMongoDbFactory parent, ClientSession session) {
      this.parent = parent;
      this.session = session;
    }

    @Override
    public @NonNull MongoDatabase getMongoDatabase() {
      return parent.getMongoDatabase(); 
  }
    @Override
    public @NonNull MongoDatabase getMongoDatabase(@NonNull String dbName) {
      return parent.getMongoDatabase(dbName);
    }

    @Override
    public @NonNull PersistenceExceptionTranslator getExceptionTranslator() {
      return parent.getExceptionTranslator();
    }

    @Override
    public @NonNull ClientSession getSession(@NonNull ClientSessionOptions options) {
      return session;
    }

    @Override
    public @NonNull MongoDatabaseFactory withSession(@NonNull ClientSession newSession) {
      return new SessionBoundFactory(parent, newSession);
    }
  }
}
