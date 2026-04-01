package backlogs.dinamico.repository.core;

import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.RoleCode;
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

  @Query(value = "{ 'tenant_id': ?0, $or: [ { 'code': { $regex: ?1, $options: 'i' } }, { 'name': { $regex: ?1, $options: 'i' } } ] }")
  Page<Role> searchByTenantAndCodeOrName(ObjectId tenantId, String q, Pageable pageable);

  Optional<Role> findByTenantIdAndCode(ObjectId tenantId, RoleCode code);

  // FIX: RoleCode, no String
  List<Role> findByTenantIdAndCodeIn(ObjectId tenantId, Collection<RoleCode> codes);

  // opcional: si tienes roles globales (sin tenant)
  Optional<Role> findByCode(RoleCode code);
  boolean existsByCode(RoleCode code);

  // este es el que debes usar en AuthorizationContextService
  List<Role> findByTenantIdAndIdIn(ObjectId tenantId, Collection<ObjectId> ids);
}

