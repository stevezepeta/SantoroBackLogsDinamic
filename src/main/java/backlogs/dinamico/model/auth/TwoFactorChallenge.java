package backlogs.dinamico.model.auth;

import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Document("two_factor_challenges")
public class TwoFactorChallenge {

  @Id
  private ObjectId id;

  @Indexed
  private ObjectId userId;

  private String codeHash;       // NUNCA guardes el código en claro
  private Instant expiresAt;
  private Instant createdAt;

  private boolean used;
  private int attemptsLeft;

  private String purpose;        // "LOGIN" (o lo que quieras)
  private String ip;
  private String userAgent;
}
