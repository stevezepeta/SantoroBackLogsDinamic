package backlogs.dinamico.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateOrgRequest {

    @NotBlank
    @Size(min = 2, max = 100)
    private String orgName;

    @NotBlank
    @Size(min = 2, max = 60)
    private String orgDomain;

    @NotBlank
    @Size(min = 2, max = 20)
    private String orgCode;

    @NotBlank
    @Size(min = 2, max = 30)
    private String orgSlug;

    private String timezone;

    private Integer retentionDays;

    @NotBlank(message = "verificationToken es obligatorio")
    private String verificationToken;

    // ----------- SUPER ADMIN ---------------
    @NotBlank
    @Size(min = 2, max = 100)
    private String adminName;

    @NotBlank
    @Email
    private String adminEmail;


    /**
     * Password temporal asignada por el admin de Santoro.
     * El usuario deberá cambiarla en el primer login.
     * Si se omite, el sistema genera una automáticamente.
     */
    @Size(min = 8, max = 72)
    private String temporaryPassword;

}
