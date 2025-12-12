package backlogs.dinamico.api.dto.auth;

public record QrTokenResponse(
        String qrToken,
        long expiresInSeconds
) {
}
