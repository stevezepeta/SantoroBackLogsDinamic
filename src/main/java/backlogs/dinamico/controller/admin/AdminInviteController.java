package backlogs.dinamico.controller.admin;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.infra.security.JwtTokenService;
import backlogs.dinamico.model.core.UserInvite;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.service.core.InviteServices;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/invites")
@RequiredArgsConstructor
public class AdminInviteController {

    private final InviteServices invites;
    private final JwtTokenService tokens;
    private final OrganizationRepository orgRepo;

    public record InviteReq(String email, List<String> roles, Long ttlHours) {

    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> createInvite(@AuthenticationPrincipal AuthUser me,
                                            @RequestBody InviteReq req) {

        long ttl = (req.ttlHours() == null || req.ttlHours() <= 0) ? 48L : req.ttlHours();

        UserInvite inv = invites.create(
                me.tenantId(),
                req.email(),
                (req.roles() == null || req.roles().isEmpty()) ? List.of("AGENT") : req.roles(),
                Duration.ofHours(ttl)
        );

        // Generacion del token de invitacion
        Map<String, Object> orgBlock = orgRepo.findById(me.tenantId())
                .<Map<String, Object>>map(o -> Map.of(
                        "id", o.getId().toHexString(),
                        "name", o.getName()
                ))
                .orElseGet(() -> Map.of(
                   "id", me.tenantId().toHexString(),
                   "name", "(unknown)"
                ));

        Map<String, Object> data = Map.of(
                "organization", orgBlock,
                "email",        inv.getEmail(),
                "roles",        inv.getRoles(),
                "inviteToken",  inv.getToken(),   // <- este pegas en /api/auth/accept-invite
                "expiresAt",    inv.getExpiresAt()
        );

        return ApiResponse.created("Invitacion generada", null, data);
    }

}
