package backlogs.dinamico.controller.auth;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.auth.LoginResponse;
import backlogs.dinamico.api.dto.auth.QrLoginRequest;
import backlogs.dinamico.api.dto.auth.QrTokenResponse;
import backlogs.dinamico.api.dto.auth.RefreshTokenRequest;
import backlogs.dinamico.infra.security.JwtTokenService;
import backlogs.dinamico.model.core.Organization;
import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import backlogs.dinamico.security.auth.AuthorizationContext;
import backlogs.dinamico.security.auth.AuthorizationContextService;
import backlogs.dinamico.service.auth.QrLoginService;
import backlogs.dinamico.service.core.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class WebAuthController {

    private final UserRepository userRepo;
    private final UserService userService;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;
    private final OrganizationRepository orgRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final AuthorizationContextService authorizationContextService;
    private final QrLoginService qrLoginService;

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginReq req) {

        if (req == null ||
                !StringUtils.hasText(req.getEmail()) ||
                !StringUtils.hasText(req.getPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "El email y password es requerido");
        }

        String email = req.getEmail().trim().toLowerCase();

        User u = userRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("bad"));

        // 1. Validar que el usuario esté activo
        assertActive(u);

        // 2. Validar password
        if (!passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
            throw new BadCredentialsException("bad");
        }

        ObjectId tenantId = u.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "user_without_tenant");
        }

        // ── NUEVO: 3. Validar que la organización esté activa ─────────────────
        Organization org = orgRepo.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "organization_not_found"));

        if (!"active".equalsIgnoreCase(org.getStatus())) {
            throw new ResponseStatusException(FORBIDDEN, "organization_disabled");
        }
        // ─────────────────────────────────────────────────────────────────────

        // Contexto completo (roles + perms + scope)
        AuthorizationContext ctx = authorizationContextService.build(tenantId, u.getId());

        // Tokens
        String accessToken  = tokens.generateAccess(u, tenantId, ctx);
        String refreshToken = tokens.generateRefresh(u, tenantId, ctx);

        // ── NUEVO: 4. Flag mustChangePassword ─────────────────────────────────
        // Si el usuario tiene contraseña temporal, se le indica al frontend.
        // El frontend debe redirigir al formulario de cambio de password.
        // No se bloquea el token — el frontend es responsable de forzar el flujo.
        boolean mustChange = u.isMustChangePassword();
        // ─────────────────────────────────────────────────────────────────────

        Map<String, Object> data = new HashMap<>();
        data.put("organization", orgBlock(org));
        data.put("user", Map.of(
                "id",    u.getId().toHexString(),
                "email", u.getEmail(),
                "name",  u.getName()
        ));
        data.put("authz", Map.of(
                "roles",       ctx.getRoles(),
                "permissions", ctx.getPermissions(),
                "orgWide",     ctx.isOrgWide(),
                "systems",     ctx.getAllowedSystems(),
                "ver",         1
        ));
        data.put("accessToken",          accessToken);
        data.put("refreshToken",         refreshToken);
        data.put("mustChangePassword",   mustChange);  // ← NUEVO campo en la respuesta

        return ApiResponse.ok("Login exitoso", null, data);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(
            @Valid @RequestBody RefreshTokenRequest req) {

        try {
            Claims claims = tokens.verifyRefresh(req.refreshToken());

            String email = claims.getSubject();
            if (!StringUtils.hasText(email)) {
                email = claims.get("email", String.class);
            }

            String tenantIdHex = claims.get("tenantId", String.class);

            if (!StringUtils.hasText(email) ||
                    !StringUtils.hasText(tenantIdHex) ||
                    !ObjectId.isValid(tenantIdHex)) {
                throw new IllegalArgumentException("Invalid refresh token claims");
            }

            ObjectId tenantId = new ObjectId(tenantIdHex);

            User user = userService.findByEmail(tenantId, email)
                    .orElseThrow(() -> new IllegalStateException("User not found for refresh token"));

            assertActive(user);

            // ── NUEVO: verificar org activa también en refresh ────────────────
            Organization org = orgRepo.findById(tenantId).orElse(null);
            if (org == null || !"active".equalsIgnoreCase(org.getStatus())) {
                return ResponseEntity.status(FORBIDDEN)
                        .body(ApiResponse.error("organization_disabled",
                                "La organización está deshabilitada.", null));
            }
            // ─────────────────────────────────────────────────────────────────

            var ctx = authorizationContextService.build(tenantId, user.getId());

            String newAccessToken = tokens.generateAccess(user);
            String refreshToken   = req.refreshToken();

            Map<String, Object> data = Map.of(
                    "accessToken",  newAccessToken,
                    "refreshToken", refreshToken,
                    "tokenType",    "Bearer",
                    "authz", Map.of(
                            "roles",       ctx.getRoles(),
                            "permissions", ctx.getPermissions(),
                            "orgWide",     ctx.isOrgWide(),
                            "systems",     ctx.getAllowedSystems(),
                            "ver",         1
                    )
            );

            return ResponseEntity.ok(ApiResponse.success("Token refreshed", data));

        } catch (JwtException | IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(ApiResponse.error("invalid_refresh_token", ex.getMessage(), null));
        }
    }

    // ── QR ────────────────────────────────────────────────────────────────────

    @GetMapping("/qr-token")
    public ResponseEntity<ApiResponse> createQrToken() {
        QrTokenResponse qrToken = qrLoginService.createQrToken();
        return ResponseEntity.ok(ApiResponse.success("Qr Token generado", qrToken));
    }

    @PostMapping("/qr-login")
    public ResponseEntity<ApiResponse> qrLogin(
            @Valid @RequestBody QrLoginRequest request,
            org.springframework.security.core.Authentication auth) {

        LoginResponse login = qrLoginService.loginWithQrToken(request.qrToken(), auth);
        return ResponseEntity.ok(ApiResponse.success("Qr Login successful", login));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertActive(User u) {
        if (u == null) throw new BadCredentialsException("bad");
        if (!"active".equalsIgnoreCase(u.getStatus())) {
            throw new ResponseStatusException(FORBIDDEN, "inactive_user");
        }
    }

    private List<Role> loadRoles(ObjectId tenantId, ObjectId userId) {
        return userRoleRepo.findByTenantIdAndUserId(tenantId, userId)
                .stream()
                .map(link -> roleRepo.findById(link.getRoleId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    // Versión original — recibe ObjectId, consulta la org
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

    // Sobrecarga — recibe la org ya cargada (evita segunda consulta a MongoDB)
    private Map<String, Object> orgBlock(Organization org) {
        return Map.of(
                "id",   org.getId().toHexString(),
                "name", org.getName()
        );
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

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