package backlogs.dinamico.repository.core;

import backlogs.dinamico.model.core.UserRole;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserRoleRepository extends MongoRepository<UserRole, ObjectId> {

    void deleteByTenantIdAndUserId(ObjectId tenantId, ObjectId userId);

    List<UserRole> findByTenantIdAndUserId(ObjectId tenantId, ObjectId userId);
    void deleteByTenantIdAndUserIdAndRoleId(ObjectId tenantId, ObjectId userId, ObjectId roleId);
    boolean existsByTenantIdAndUserIdAndRoleId(ObjectId tenantId, ObjectId userId, ObjectId roleId);

}
