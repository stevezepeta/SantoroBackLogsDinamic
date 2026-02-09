package backlogs.dinamico.controller.admin;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.api.dto.InviteCreateRequest;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.model.core.RoleCode;
import backlogs.dinamico.model.core.UserInvite;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.service.core.InviteServices;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/invites")
@RequiredArgsConstructor
public class AdminInviteController {

    private final InviteServices invites;
    private final OrganizationRepository orgRepo;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_USERS_MANAGE') or hasAuthority('PERM_ROLES_ASSIGN')")
    public ApiResponse<Map<String, Object>> createInvite(
            @AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody InviteCreateRequest req
    ) {
        // TTL default 48h si no viene
        long ttl = (req.getTtlHours() == null || req.getTtlHours() <= 0) ? 48L : req.getTtlHours().longValue();

        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);

        RoleCode role = req.getRoles().get(0);

        List<String> roles = List.of(role.name());

        // systems dinámicos: normaliza a UPPER + trim + distinct
        List<String> systems = (req.getSystems() == null)
                ? List.of()
                : req.getSystems().stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        UserInvite inv = invites.create(
                me.getTenantId(),
                email,
                roles,
                systems,
                Duration.ofHours(ttl)
        );

        Map<String, Object> orgBlock = orgRepo.findById(me.getTenantId())
                .<Map<String, Object>>map(o -> Map.of(
                        "id", o.getId().toHexString(),
                        "name", o.getName()
                ))
                .orElseGet(() -> Map.of(
                        "id", me.getTenantId().toHexString(),
                        "name", "(unknown)"
                ));

        String inviteLink = invites.buildInviteLink(inv);

        Map<String, Object> data = Map.of(
                "organization", orgBlock,
                "email",        inv.getEmail(),
                "roles",        inv.getRoles(),
                "systems",      inv.getSystems(),
                "inviteToken",  inv.getToken(),
                "inviteLink",   inviteLink,
                "expiresAt",    inv.getExpiresAt()
        );

        return ApiResponse.created("Invitación generada", "admin_invite_created", data);
    }
}
