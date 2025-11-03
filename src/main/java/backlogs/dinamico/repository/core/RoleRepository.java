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

  // Búsqueda combinada (code OR name) dentro del mismo tenant
  @Query(value = "{ 'tenantId': ?0, $or: [ { 'code': { $regex: ?1, $options: 'i' } }, { 'name': { $regex: ?1, $options: 'i' } } ] }")
  Page<Role> searchByTenantAndCodeOrName(ObjectId tenantId, String q, Pageable pageable);

  Optional<Role> findByTenantIdAndCode(ObjectId tenantId, String code);

  Optional<Role> findByCode(String code);

  List<Role> findByCodeIn(List<String> codes);

  List<Role> findByTenantIdAndCodeIn(ObjectId tenantId, Collection<String> codes);

}
