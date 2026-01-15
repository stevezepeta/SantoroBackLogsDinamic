package backlogs.dinamico.controller.core;

import backlogs.dinamico.service.core.InviteServices;
import backlogs.dinamico.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RequestMapping("")
@RequiredArgsConstructor
public class InviteAdminController {

    private final InviteServices invites;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TENANT_OWNER')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateInviteReq req) {

        ObjectId tenantId = TenantContext.getTenantId();

        var inv = invites.create(
                tenantId,
                req.email().trim().toLowerCase(),
                req.roles(),
                req.systems(),
                Duration.ofHours(req.ttlHours() == null ? 24 : req.ttlHours())
        );

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "token", inv.getToken(),     // SOLO dev
                "expiresAt", inv.getExpiresAt()
        ));
    }

    public record CreateInviteReq(
            @NotBlank @Email String email,
            List<String> roles,
            List<String> systems,
            Long ttlHours
    ) {}
}
