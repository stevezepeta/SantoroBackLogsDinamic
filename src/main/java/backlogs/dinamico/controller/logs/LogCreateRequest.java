package backlogs.dinamico.controller.logs;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;

public record LogCreateRequest(
        Instant timestamp,

        @NotBlank
        String level,                // INFO / ERROR / SUCCESS...

        @NotBlank
        String message,

        @NotBlank
        String processType,             // ELYCTIS_SCAN, FACECAPTURE, etc.

        @NotBlank
        String device,

        String scanDevice,
        String scanType,

        String personId,

        String officeId,

        String baseCode,
        String errorCode,
        String sessionToken,
        String system,
        String environment,

        Map<String, Object> extra
) {

}
