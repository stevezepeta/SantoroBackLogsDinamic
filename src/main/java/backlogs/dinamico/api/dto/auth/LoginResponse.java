package backlogs.dinamico.api.dto.auth;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType // "Bearer"
) {
}
