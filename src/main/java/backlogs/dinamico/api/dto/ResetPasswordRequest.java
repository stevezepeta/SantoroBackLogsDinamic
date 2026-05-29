package backlogs.dinamico.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "email_required")
    @Email(message = "email_invalid")
    private String email;

    @NotBlank(message = "code_required")
    @Size(min = 6, max = 6, message = "code_must_be_6_digits")
    private String code;

    @NotBlank(message = "password_required")
    @Size(min = 8, message = "password_min_8_chars")
    private String newPassword;

}
