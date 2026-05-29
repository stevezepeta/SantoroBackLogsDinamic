package backlogs.dinamico.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "email_required")
    @Email(message = "email_invalid")
    private String email;

}

