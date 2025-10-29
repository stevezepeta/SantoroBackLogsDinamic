package backlogs.dinamico.repository.core;

import backlogs.dinamico.model.core.Role;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends MongoRepository<Role, ObjectId> {

  Page<Role> findByTenantId(ObjectId tenantId, Pageable pageable);

  Page<Role> findByTenantIdAndCodeContainingIgnoreCase(ObjectId tenantId, String codeContains, Pageable pageable);

  Page<Role> findByTenantIdAndNameContainingIgnoreCase(ObjectId tenantId, String nameContains, Pageable pageable);

  // Búsqueda combinada (code OR name) dentro del mismo tenant
  @Query(value = "{ 'tenantId': ?0, $or: [ { 'code': { $regex: ?1, $options: 'i' } }, { 'name': { $regex: ?1, $options: 'i' } } ] }")
  Page<Role> searchByTenantAndCodeOrName(ObjectId tenantId, String q, Pageable pageable);

  Optional<Role> findByTenantIdAndCode(ObjectId tenantId, String code);

  List<Role> findByTenantIdAndCodeIn(ObjectId tenantId, List<String> codes);

  long countByTenantIdAndIdIn(ObjectId tenantId, Collection<ObjectId> ids);

  Optional<Role> findByCode(String code);

  List<Role> findByCodeIn(List<String> codes);

}
