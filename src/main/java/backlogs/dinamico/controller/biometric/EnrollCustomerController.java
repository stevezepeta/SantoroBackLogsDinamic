package backlogs.dinamico.controller.biometric;

import backlogs.dinamico.service.biometric.BiometricService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
        String sexo = trim(req.sexo());
        String nacionalidad = trim(req.nacionalidad());
        String direccion = trim(req.direccion());
        Long oficinaId = req.oficinaId();

        if (curp.isBlank() || nombres.isBlank() || primerApellido.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "error", "Campos requeridos: curp, nombres, primerApellido"
            ));
        }

        // Parsear fechaNacimiento
        LocalDate fechaNacimiento = null;
        if (StringUtils.hasText(req.fechaNacimiento())) {
            try {
                fechaNacimiento = LocalDate.parse(req.fechaNacimiento()); // formato: 2025-01-31
            } catch (DateTimeParseException ex) {
                return ResponseEntity.badRequest().body(Map.of(
                        "ok", false,
                        "error", "fechaNacimiento_invalida (use formato yyyy-MM-dd)"
                ));
            }
        }

        var p = biometricService.upsertBiographic(
                tenantId,
                curp,
                nombres,
                primerApellido,
                segundoApellido,
                fechaNacimiento,
                sexo,
                nacionalidad,
                direccion,
                oficinaId
        );

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "person", Map.of(
                        "id", p.getId() != null ? p.getId().toHexString() : null,
                        "curp", p.getCurp(),
                        "nombres", p.getName(),
                        "primerApellido", p.getPrimerApellido(),
                        "segundoApellido", p.getSegundoApellido(),
                        "fechaNacimiento", p.getFechaNacimiento(),
                        "sexo", p.getSexo(),
                        "nacionalidad", p.getNacionalidad(),
                        "direccion", p.getDireccion(),
                        "oficinaId", p.getOficinaId()
                )
        ));
    }

    // Listar todas las persons
    @GetMapping("/persons")
    public ResponseEntity<?> listPersons(@RequestHeader("X-Tenant") ObjectId tenantId) {

        var data = biometricService.listPersons(tenantId);

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "Message", "Listado de Persons",
                "data", data
        ));
    }

    // Optener persons por CURP
    @GetMapping("/persons/{curp}")
    public ResponseEntity<?> getPersonByCurp(@RequestHeader("X-Tenant") ObjectId tenantId,
                                             @PathVariable String curp) {

        String curpNorm = (curp == null ? "" : curp.trim().toUpperCase());
        if (curpNorm.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "code", "curp_required",
                    "message", "CURP requerida"
            ));
        }

        var opt = biometricService.findPerson(tenantId, curpNorm);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "ok", false,
                    "code", "person_not_found",
                    "message", "Persona no encontrada"
            ));
        }

        Map<String, Object> view = biometricService.toPersonLegacy(opt.get());

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "person", view
        ));
    }

    private static String trim(String v) { return v == null ? "" : v.trim(); }

    public record BiographicReq(
            @NotBlank String curp,
            @NotBlank String nombres,
            @NotBlank String primerApellido,
            String segundoApellido,
            String fechaNacimiento,
            String sexo,
            String nacionalidad,
            String direccion,
            Long oficinaId
    ) {}
}
