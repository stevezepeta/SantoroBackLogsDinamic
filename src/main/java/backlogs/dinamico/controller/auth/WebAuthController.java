package backlogs.dinamico.controller.auth;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.infra.security.JwtTokenService;
import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import backlogs.dinamico.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class WebAuthController {

    private final UserRepository userRepo;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;
    private final OrganizationRepository orgRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;

    // -------------------- Register --------------------
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> register(@RequestHeader("X-Tenant") ObjectId tenantId,
                                      @Valid @RequestBody RegisterReq req) {

        String email = req.getEmail().trim().toLowerCase();

        if (userRepo.existsByTenantIdAndEmailIgnoreCase(tenantId, email)) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "email_taken"));
        }

        User u = new User();
        u.setTenantId(tenantId);
        u.setEmail(email);
        u.setName(req.getName());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setStatus("active");
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());

        u = userRepo.insert(u);

        // Rol por defecto
        var defaultRole = roleRepo.findByCode("TENANT_USER").orElse(null);
        if (defaultRole != null) {
            var link = new UserRole();
            link.setTenantId(tenantId);
            link.setUserId(u.getId());
            link.setRoleId(defaultRole.getId());
            link.setCreatedAt(Instant.now());
            userRoleRepo.save(link);
        }

        // Generación del token
        var links = userRoleRepo.findByTenantIdAndUserId(tenantId, u.getId());
        List<Role> roles = links.isEmpty()
                ? List.of()
                : roleRepo.findAllById(links.stream().map(UserRole::getRoleId).toList());

        String jwt = tokens.generate(u, roles, tenantId);

        return ResponseEntity.status(201).body(Map.of(
                "ok", true,
                "user", Map.of("id", u.getId(), "email", u.getEmail(), "name", u.getName(), "status", u.getStatus()),
                "roles", roles.stream().map(Role::getCode).toList(),
                "token", jwt
        ));
    }

    // -------------------- Login --------------------
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> login(
            @RequestBody LoginReq req,
            @RequestHeader(value = "X-Org-Code", required = false) String orgCode,
            @RequestHeader(value = "X-Tenant", required = false) ObjectId tenantHeader) {

        // 1) Resuelve tenant desde el ThreadLocal que setea el filtro (TenantHeaderFilter)
        ObjectId tenantId = TenantContext.getTenantId();
        if (tenantId == null) tenantId = tenantHeader;
        if (tenantId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "missing_tenant");
        }

        String email = req.getEmail().trim().toLowerCase();
        User u = userRepo.findByTenantIdAndEmailIgnoreCase(tenantId, email)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "bad_credentials"));

        if (!"active".equalsIgnoreCase(u.getStatus())) {
            throw new ResponseStatusException(FORBIDDEN, "inactive_user");
        }
        if (!passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "bad_credentials");
        }

        List<Role> roles = userRoleRepo.findByTenantIdAndUserId(tenantId, u.getId())
                .stream()
                .map(link -> roleRepo.findById(link.getRoleId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        String jwt = tokens.generate(u, roles, tenantId);

        Map<String, Object> data = Map.of(
                "organization", orgBlock(tenantId),
                "user", Map.of(
                        "id", u.getId().toHexString(),
                        "email", u.getEmail(),
                        "name", u.getName()
                ),
                "roles", roles.stream().map(Role::getCode).toList(),
                "token", jwt
        );

        return ApiResponse.ok("Login exitoso", null, data);
    }

    private Map<String, Object> orgBlock(ObjectId tenantId) {
        return orgRepo.findById(tenantId)
                .<Map<String, Object>>map(o -> Map.of(
                        "id",   o.getId().toHexString(),
                        "name", o.getName()
                ))
                .orElseGet(() -> Map.of(
                        "id",   tenantId.toHexString(),
                        "name", "(unknown)"
                ));
    }


    // -------------------- DTOs --------------------
    @Data
    public static class RegisterReq {
        private String email;
        private String password;
        private String name;
    }

    @Data
    public static class LoginReq {
        private String email;
        private String password;
    }
}
