package backlogs.dinamico.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record QrLoginRequest(
        @NotBlank
        String qrToken
) {

}
