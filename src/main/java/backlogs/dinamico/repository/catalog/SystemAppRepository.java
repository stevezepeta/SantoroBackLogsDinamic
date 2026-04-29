package backlogs.dinamico.repository.catalog;

import backlogs.dinamico.model.catalog.SystemApp;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SystemAppRepository extends MongoRepository<SystemApp, ObjectId> {

  // UI paginada — sin restricción de sistemas
  Page<SystemApp> findByTenantId(ObjectId tenantId, Pageable pageable);
  Page<SystemApp> findByTenantIdAndStatus(ObjectId tenantId, String status, Pageable pageable);
  Page<SystemApp> findByTenantIdAndNameContainingIgnoreCase(ObjectId tenantId, String name, Pageable pageable);

  // UI paginada — restringida a sistemas permitidos (VIEWER / permisos por sistema)
  Page<SystemApp> findByTenantIdAndCodeIn(ObjectId tenantId, Collection<String> codes, Pageable pageable);
  Page<SystemApp> findByTenantIdAndCodeInAndStatus(ObjectId tenantId, Collection<String> codes, String status, Pageable pageable);
  Page<SystemApp> findByTenantIdAndCodeInAndNameContainingIgnoreCase(ObjectId tenantId, Collection<String> codes, String name, Pageable pageable);

  // Auth/Login
  List<SystemApp> findAllByTenantIdAndStatus(ObjectId tenantId, String status);
}
