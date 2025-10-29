package backlogs.dinamico.controller.admin;

import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.infra.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/invites")
@RequiredArgsConstructor
public class AdminInviteController {

    private final JwtTokenService tokens;

    public record InviteReq(String email, List<String> roles, Long ttlHours) {

    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> createInvite(@AuthenticationPrincipal AuthUser me,
                                            @RequestBody InviteReq req) {

        long ttl = (req.ttlHours() == null || req.ttlHours() <= 0) ? 48L : req.ttlHours();
        String token = tokens.createInvite(me.tenantId(), req.email(), req.roles(), ttl);

        return Map.of(
                "ok", true,
                "token", token,
                "expiresAt", Instant.now().plus(ttl, ChronoUnit.HOURS)
        );
    }

}
