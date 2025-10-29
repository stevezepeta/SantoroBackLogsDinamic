package backlogs.dinamico.controller.biometric;

import backlogs.dinamico.infra.security.JwtTokenService;
import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import backlogs.dinamico.service.biometric.BiometricService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/biometric")
@RequiredArgsConstructor
public class BiometricAuthController {

    private final BiometricService service;
    private final JwtTokenService tokens;
    private final UserRepository userRepo;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;

    @PostMapping("/enroll")
    public ResponseEntity<?> enroll(@RequestHeader("X-Tenant")ObjectId tenantId,
                                    @RequestParam String curp,
                                    @RequestParam(required = false) String name,
                                    @RequestParam Map<String, MultipartFile> files,
                                    @RequestParam(required = false, name = "face") MultipartFile face) throws IOException {

        service.enroll(curp, name, files, face);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    // Verificacion de huellas y si matchea
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestHeader("X-Tenant")ObjectId tenantId,
                                   @RequestParam String curp,
                                   @RequestParam Map<String, MultipartFile> files) throws IOException {


        var vr = service.verify(curp, files);
        if (!vr.matched()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("ok", false, "reason", "no_match"));

        // Si el CURP pertenece a un User del tenant, emite token
        var userOpt = userRepo.findByTenantIdAndEmailIgnoreCase(tenantId, curp + "@biometric.local");
        // o se puede agregar el campo curp al User
        if (userOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "reason", "user_not_linked"));

        var user = userOpt.get();
        var links = userRoleRepo.findByTenantIdAndUserId(tenantId, user.getId());
        List<Role> roles = links.isEmpty() ? List.of() : roleRepo.findAllById(links.stream().map(l -> l.getRoleId()).toList());
        String jwt = tokens.generate(user, roles, tenantId);

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "finger", vr.matchedFinger(),
                "score", vr.score(),
                "token", jwt
        ));
    }

}
