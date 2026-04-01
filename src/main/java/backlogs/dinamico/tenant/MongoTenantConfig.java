package backlogs.dinamico.tenant;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

@Configuration(proxyBeanMethods = false)
public class MongoTenantConfig {

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
}
