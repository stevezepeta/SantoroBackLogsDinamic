package backlogs.dinamico.repository.catalog;

import backlogs.dinamico.model.ingest.ApiKey;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ApiKeyRep extends MongoRepository<ApiKey, ObjectId> {
  Optional<ApiKey> findByKeyHashAndStatus(String keyHash, String status);

}
