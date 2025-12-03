package backlogs.dinamico.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogIngestReq {

    private Instant timestamp;

    @Pattern(regexp = "INFO|ERROR|WARN|SUCCESS|START|END", message = "invalid_level")
    private String level;

    @NotBlank(message = "message_required")
    private String message;

    // Campos nucleos comunes
    private String processType;
    private String device;
    private String scanDevice;
    private String scanType;
    private String officeId;
    private String personId;
    private String baseCode;
    private String errorCode;
    private String sessionToken;

    // Entorno
    private String system;
    private String environment;

    private Map<String, Object> extra;

}


