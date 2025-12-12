package backlogs.dinamico.model.auth;


import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document("qr_login_sessions")
public class QrLoginSession {

    @Id
    private ObjectId id;

    @Indexed(unique = true)
    private String qrToken;

    @Indexed
    private String tenantId;

    @Indexed
    private String email;

    @Indexed
    private Instant expiresAt;

    private Instant createdAt;

    private boolean used;

}
