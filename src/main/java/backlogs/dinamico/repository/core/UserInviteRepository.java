package backlogs.dinamico.repository.core;

import backlogs.dinamico.model.core.UserInvite;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserInviteRepository extends MongoRepository<UserInvite, ObjectId> {

    Optional<UserInvite> findByTokenAndStatus(String token, String status);
    Optional<UserInvite> findFirstByTenantIdAndEmailCiAndStatus(ObjectId tenantId, String emailCi, String status);

}
