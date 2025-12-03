package backlogs.dinamico.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class KeyHasher {

    public static String sha256b64(String raw) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
