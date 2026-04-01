package backlogs.dinamico.repository.security;

import backlogs.dinamico.model.ingest.ApiKey;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ApiKeyRepository extends MongoRepository<ApiKey, ObjectId> {

    Optional<ApiKey> findByKeyHashAndStatus(String keyHash, String status);

}
