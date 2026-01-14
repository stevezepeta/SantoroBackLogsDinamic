package backlogs.dinamico.controller.admin;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.model.core.UserInvite;
import backlogs.dinamico.repository.core.OrganizationRepository;
import backlogs.dinamico.service.core.InviteServices;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    public record InviteReq(
            @NotBlank @Email String email,
            List<String> roles,
            @Min(1) Long ttlHours
    ) {}

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> createInvite(
            @AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody InviteReq req
    ) {

        long ttl = (req.ttlHours() == null || req.ttlHours() <= 0) ? 48L : req.ttlHours();

        String email = req.email().trim().toLowerCase(Locale.ROOT);

        List<String> roles = (req.roles() == null || req.roles().isEmpty())
                ? List.of("AGENT")
                : req.roles().stream()
                .filter(StringUtils::hasText)
                .map(r -> r.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        UserInvite inv = invites.create(
                me.tenantId(),
                email,
                roles,
                Duration.ofHours(ttl)
        );

        Map<String, Object> orgBlock = orgRepo.findById(me.tenantId())
                .<Map<String, Object>>map(o -> Map.of(
                        "id", o.getId().toHexString(),
                        "name", o.getName()
                ))
                .orElseGet(() -> Map.of(
                        "id", me.tenantId().toHexString(),
                        "name", "(unknown)"
                ));

        String inviteLink = invites.buildInviteLink(inv);

        Map<String, Object> data = Map.of(
                "organization", orgBlock,
                "email",        inv.getEmail(),
                "roles",        inv.getRoles(),
                "inviteToken",  inv.getToken(),     // en PROD idealmente NO regresarlo, solo mandar correo
                "inviteLink",   inviteLink,
                "expiresAt",    inv.getExpiresAt()
        );

        return ApiResponse.created("Invitación generada", "admin_invite_created", data);
    }
}
