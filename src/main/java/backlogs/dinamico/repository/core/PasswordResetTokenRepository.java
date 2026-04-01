package backlogs.dinamico.repository.core;

import backlogs.dinamico.model.core.PasswordResetToken;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, ObjectId> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    long deleteByExpirestAtBefore(Instant instant);

}
