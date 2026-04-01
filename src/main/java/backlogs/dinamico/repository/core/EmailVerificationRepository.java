package backlogs.dinamico.repository.core;

import backlogs.dinamico.model.core.EmailVerification;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends MongoRepository<EmailVerification, ObjectId> {

    Optional<EmailVerification> findFirstByEmailCiAndStatusOrderByCreatedAtDesc(
            String emailCi, String status);

    Optional<EmailVerification> findByVerificationToken(String verificationToken);

    boolean existsByEmailCiAndStatus(String emailCi, String status);

}
