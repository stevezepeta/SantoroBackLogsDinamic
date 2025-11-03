package backlogs.dinamico.controller.auth;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.infra.security.JwtTokenService;
import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.model.core.UserRole;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import backlogs.dinamico.service.core.InviteServices;
import io.jsonwebtoken.Claims;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/auth")
@Validated
@RequiredArgsConstructor
@CrossOrigin
public class AcceptInviteController {

    private final InviteServices inviteServices;

    public record AcceptInviteReq(
            @NotBlank String token,
            @NotBlank String name,
            @NotBlank String password
    ) {

    }

    @PostMapping("/accept-invite")
    public ResponseEntity<ApiResponse<Map<String, Object>>> accept(@RequestBody AcceptInviteReq req) {

        var data = inviteServices.accept(req.token(), req.name(), req.password());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Usuario activado", null, data));

    }


}
