package backlogs.dinamico.repository.catalog;

import backlogs.dinamico.model.ingest.ApiKey;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;

public interface ApiKeyRep extends MongoRepository<ApiKey, ObjectId> {

  // Usa @Query explícita con el nombre real del campo en MongoDB
  @Query("{ 'key_hash': ?0, 'status': ?1 }")
  Optional<ApiKey> findByKeyHashAndStatus(String keyHash, String status);

  default Optional<ApiKey> findActiveByHash(String keyHash) {
    return findByKeyHashAndStatus(keyHash, "active");
  }

  List<ApiKey> findByTenantIdOrderByCreatedAtDesc(ObjectId tenantId);
  Optional<ApiKey> findByTenantIdAndId(ObjectId tenantId, ObjectId id);


  Page<ApiKey> findByTenantId(ObjectId tenantId, Pageable pageable);
  Page<ApiKey> findByTenantIdAndStatus(ObjectId tenantId, String status, Pageable pageable);
  Page<ApiKey> findByTenantIdAndNameContainingIgnoreCase(ObjectId tenantId, String name, Pageable pageable);
  Page<ApiKey> findByTenantIdAndStatusAndNameContainingIgnoreCase(ObjectId tenantId, String status, String name, Pageable pageable);

}