package backlogs.dinamico.controller.biometric;

import backlogs.dinamico.service.biometric.BiometricService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/enrollCustomer/enroll")
@RequiredArgsConstructor
@Validated
public class EnrollCustomerController {

    private final BiometricService biometricService;

    @PostMapping(value = "/biographic", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> biographic(@RequestHeader("X-Tenant") ObjectId tenantId,
                                        @Valid @RequestBody BiographicReq req) {

        String curp            = trim(req.curp());
        String nombres         = trim(req.nombres());
        String primerApellido  = trim(req.primerApellido());
        String segundoApellido = trim(req.segundoApellido());

        if (curp.isBlank() || nombres.isBlank() || primerApellido.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "error", "Campos requeridos: curp, nombres, primerApellido"
            ));
        }

        var p = biometricService.upsertBiographic(
                tenantId, curp, nombres, primerApellido, segundoApellido
        );

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "CURP", p.getCurp(),
                "Nombre Completo", p.getName()
        ));
    }

    private static String trim(String v) { return v == null ? "" : v.trim(); }

    public record BiographicReq(
            @NotBlank String curp,
            @NotBlank String nombres,
            @NotBlank String primerApellido,
            String segundoApellido
    ) {}
}
