package backlogs.dinamico.controller.auth;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.auth.LoginResponse;
import backlogs.dinamico.api.dto.auth.QrLoginRequest;
import backlogs.dinamico.api.dto.auth.QrTokenResponse;
import backlogs.dinamico.api.dto.auth.RefreshTokenRequest;
import backlogs.dinamico.infra.security.JwtTokenService;
import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import backlogs.dinamico.service.auth.QrLoginService;
import backlogs.dinamico.service.core.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.bson.types.ObjectId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

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

    private final QrLoginService qrLoginService;

    private final SimpMessagingTemplate messagingTemplate;

    // -------------------- Login --------------------
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

        if (!"active".equalsIgnoreCase(u.getStatus())) {
            throw new ResponseStatusException(FORBIDDEN, "inactive_user");
        }
        if (!passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
            throw new BadCredentialsException("bad");
        }

        ObjectId tenantId = u.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "user_without_tenant");
        }

        List<Role> roles = userRoleRepo.findByTenantIdAndUserId(tenantId, u.getId())
                .stream()
                .map(link -> roleRepo.findById(link.getRoleId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        // Access Token
        String accessToken = tokens.generate(u, roles, tenantId);

        // Refresh Token
        String refreshToken = tokens.generateRefresh(u, roles, tenantId);

        Map<String, Object> data = Map.of(
                "organization", orgBlock(tenantId),
                "user", Map.of(
                        "id", u.getId().toHexString(),
                        "email", u.getEmail(),
                        "name", u.getName()
                ),
                "roles", roles.stream().map(Role::getCode).toList(),
                "accessToken", accessToken,
                "refreshToken", refreshToken
        );

        return ApiResponse.ok("Login exitoso", null, data);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse> refresh(@Valid @RequestBody RefreshTokenRequest req) {

        try {
            Claims claims = tokens.verifyRefresh(req.refreshToken());

            String email = claims.get("email", String.class);
            String tenantIdHex = claims.get("tenantId", String.class);

            if (email == null || tenantIdHex == null) {
                throw new IllegalArgumentException("Invalid refresh token claims");
            }

            ObjectId tenantId = new ObjectId(tenantIdHex);

            User user = userService.findByEmail(tenantId, email)
                    .orElseThrow(() -> new IllegalStateException("User not found for refresh token"));

            String newAccessToken = tokens.generate(user, null, tenantId);
            String refreshToken = req.refreshToken();

            LoginResponse resp = new LoginResponse(newAccessToken, refreshToken, "Bearer");

            return ResponseEntity.ok(
                    ApiResponse.success("Token refreshed", resp)
            );

        } catch (JwtException | IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(ApiResponse.error(
                            "invalid_refresh_token",
                            ex.getMessage(),
                            null
                    ));
        }

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

    // Login desde la PC el usuario ya autenticado pide el QR
    @GetMapping("/qr-token")
    public ResponseEntity<ApiResponse> createQrToken() {

        QrTokenResponse qrToken = qrLoginService.createQrToken();
        return ResponseEntity.ok(
                ApiResponse.success("Qr Token generado", qrToken)
        );

    }

    // Desde el celular escanear el QR
    @PostMapping("/qr-login")
    public ResponseEntity<ApiResponse> qrLogin(@Valid
                                               @RequestBody QrLoginRequest request,
                                               Authentication auth) {

        // Marca la sesion como usada
        LoginResponse login = qrLoginService.loginWithQrToken(request.qrToken(), auth);

        // Se construye el payload que se recibira en el navegador
        Map<String, Object> wsPayload = Map.of(
                "status", "APPROVED",
                "accesToken", login.accessToken(),
                "refreshToken", login.refreshToken(),
                "tokeType", login.tokenType()
        );

        // Se envia el mensaje al topic
        String dest = "/topic/qr-login/" + request.qrToken();

        // Respuesta simpel al movil
        Map<String, Object> httpData = Map.of(
                "qrToke", request.qrToken(),
                "status", "linked"
        );

        return ResponseEntity.ok(
                ApiResponse.success("Qr Login successful", login)
        );

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
