package backlogs.dinamico.api.dto.auth;

public record QrLoginStatusMessage(
        String status, // "APROVED", "EXPIRED"
        String accessToken,
        String refreshToken

) {
}
