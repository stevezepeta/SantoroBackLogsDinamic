package backlogs.dinamico.api.dto.auth;

public record QrLoginStatusMessage(
        Status status, // "APPROVED", "EXPIRED", "ALREADY_USED", "ERROR"
        String accessToken,
        String refreshToken,
        String tokenType,
        String message
) {

    public enum Status {
        APPROVED,
        EXPIRED,
        ALREADY_USED,
        ERROR
    }

}
