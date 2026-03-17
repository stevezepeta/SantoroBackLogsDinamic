package backlogs.dinamico.repository.core;

import backlogs.dinamico.model.core.User;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, ObjectId> {

    Optional<User> findByTenantIdAndEmailIgnoreCase(ObjectId tenantId, String email);
    boolean existsByTenantIdAndEmailIgnoreCase(ObjectId tenantId, String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Page<User> findByTenantId(ObjectId tenantId, Pageable pageable);
    Page<User> findByTenantIdAndStatus(ObjectId tenantId, String status, Pageable pageable);

    // Search for name or email
    Page<User> findByTenantIdAndNameRegexIgnoreCaseOrTenantIdAndEmailRegexIgnoreCase(
            ObjectId tenantId1, String nameRegex,
            ObjectId tenantId2, String emailRegex,
            Pageable pageable
    );

    long countByTenantId(ObjectId tenantId);

    // ------------------------------------

    Page<User> findByTenantIdAndNameContainingIgnoreCaseOrTenantIdAndEmailContainingIgnoreCase(
            ObjectId tenantId1, String name,
            ObjectId tenantId2, String email,
            Pageable pageable);

    long countByTenantIdAndStatus(ObjectId tenantId, String status);

    // Para el panel Santoro — todos los tenants
    long countByStatus(String status);

    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<User> findByStatus(String status, Pageable pageable);

    Page<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String email, Pageable pageable);


}
