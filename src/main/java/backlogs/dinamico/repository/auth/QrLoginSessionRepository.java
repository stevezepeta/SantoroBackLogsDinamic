package backlogs.dinamico.repository.auth;

import backlogs.dinamico.model.auth.QrLoginSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface QrLoginSessionRepository extends MongoRepository<QrLoginSession, String> {

    Optional<QrLoginSession> findByQrToken(String qrToken);

}
