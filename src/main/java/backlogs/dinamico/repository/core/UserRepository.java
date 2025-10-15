package backlogs.dinamico.repository.core;

import backlogs.dinamico.model.core.User;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, ObjectId> {

    Optional<User> findByTenantIdAndEmailIgnoreCase(ObjectId tenantId, String email);

    Page<User> findByTenantId(ObjectId tenantId, Pageable pageable);

    Page<User> findByTenantIdAndStatus(ObjectId tenantId, String status, Pageable pageable);

    // Search for name or email
    Page<User> findByTenantIdAndNameRegexIgnoreCase(ObjectId tenantId, String nameRegex, Pageable p);
    Page<User> findByTenantIdAndEmailRegexIgnoreCase(ObjectId tenantId, String emailRegex, Pageable p);

}
