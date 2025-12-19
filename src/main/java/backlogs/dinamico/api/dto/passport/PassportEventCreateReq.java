package backlogs.dinamico.api.dto.passport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record PassportEventCreateReq(

        @NotBlank
        String system,

        @NotBlank
        String operationType,

        @NotBlank
        String status,

        @NotNull
        Instant eventTime,

        @NotBlank
        String message,

        // ---- Datos del pasaporte ----
        @NotBlank
        String passportNumber,

        @NotBlank
        String personId,         // cédula / ID de la persona

        @NotBlank
        String fullName,

        @NotBlank
        String nationality,

        // ---- Oficina y canal ----
        @NotBlank
        String officeId,         // ID de la oficina

        String channel,          // "OFICINA", "WEB", "MOVIL", etc.

        // ---- Usuario operador ----
        @NotBlank
        String userId,

        // ---- Métricas de tiempo (opcionales) ----
        SlaInfo sla,             // info de tiempos

        // ---- Motivos (rechazos / cancelaciones) ----
        ReasonInfo reason,

        // ---- Extras ----
        Map<String, Object> meta // cualquier cosa adicional

) {

    public record SlaInfo(
            Instant startTime,
            Instant endTime,
            Long elapsedSeconds
    ) { }

    public record ReasonInfo(
            String code,
            String description
    ) { }

}
