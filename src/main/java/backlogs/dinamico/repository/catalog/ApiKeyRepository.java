package backlogs.dinamico.repository.catalog;

import backlogs.dinamico.model.catalog.ApiKey;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ApiKeyRepository extends MongoRepository<ApiKey, org.bson.types.ObjectId> {
  Optional<ApiKey> findByKeyAndStatus(String key, String status);
}
