package backlogs.dinamico.controller.biometric;

import backlogs.dinamico.model.biometric.Person;
import backlogs.dinamico.service.biometric.BiometricService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/fingerprint")
@RequiredArgsConstructor
public class FingerPrintController {

    private final BiometricService service;

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestHeader("X-Tenant")ObjectId tenantId,
                                                      @RequestParam String curp,
                                                      @RequestParam(required = false) String fingerType,
                                                      @RequestParam Map<String, MultipartFile> files) throws IOException {
        var vr = service.verify(curp, files);
        var personOpt = service.findPerson(tenantId, curp);

        String nombre = personOpt.map(Person::getName).orElse(null);
        String personId =  personOpt.map(p -> p.getId().toHexString()).orElse(null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("match", vr.matched());
        body.put("nombreCompleto", nombre);
        body.put("id", personId);

        return ResponseEntity.ok(body);

    }

}
