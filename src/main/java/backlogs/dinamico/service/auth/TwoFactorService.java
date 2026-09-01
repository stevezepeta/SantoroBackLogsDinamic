package backlogs.dinamico.service.auth;


import backlogs.dinamico.model.auth.TwoFactorChallenge;
import backlogs.dinamico.repository.auth.TwoFactorChallengeRepo;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class TwoFactorService {

  private final TwoFactorChallengeRepo repo;
  private final JavaMailSender mailSender;
  private final PasswordEncoder encoder; // BCrypt recomendado

  private static final SecureRandom RNG = new SecureRandom();

  public ObjectId createAndSendLoginChallenge(ObjectId userId, String email, String ip, String ua) {
    String code = generate6Digits();

    TwoFactorChallenge ch = TwoFactorChallenge.builder()
        .userId(userId)
        .codeHash(encoder.encode(code))
        .createdAt(Instant.now())
        .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
        .attemptsLeft(5)
        .used(false)
        .purpose("LOGIN")
        .ip(ip)
        .userAgent(ua)
        .build();

    repo.save(ch);
    sendEmailOtp(email, code);
    return ch.getId();
  }

  public void verify(ObjectId challengeId, ObjectId userId, String code) {
    TwoFactorChallenge ch = repo.findByIdAndUsedFalse(challengeId)
        .orElseThrow(() -> new IllegalArgumentException("Invalid challenge"));

    if (!ch.getUserId().equals(userId)) {
      throw new IllegalArgumentException("Invalid challenge");
    }
    if (Instant.now().isAfter(ch.getExpiresAt())) {
      throw new IllegalArgumentException("Code expired");
    }
    if (ch.getAttemptsLeft() <= 0) {
      throw new IllegalArgumentException("Too many attempts");
    }

    boolean ok = encoder.matches(code, ch.getCodeHash());
    if (!ok) {
      ch.setAttemptsLeft(ch.getAttemptsLeft() - 1);
      repo.save(ch);
      throw new IllegalArgumentException("Invalid code");
    }

    ch.setUsed(true);
    repo.save(ch);
  }

  private String generate6Digits() {
    int n = RNG.nextInt(1_000_000);
    return String.format("%06d", n);
  }

  private void sendEmailOtp(String to, String code) {
    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setTo(to);
    msg.setSubject("Your verification code");
    msg.setText("Your verification code is: " + code + "\nExpires in 10 minutes.");
    mailSender.send(msg);
  }
}
