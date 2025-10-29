package backlogs.dinamico.controller.core;

import backlogs.dinamico.service.core.InviteServices;
import backlogs.dinamico.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
public class InviteAdminController {

    private final InviteServices invites;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@RequestBody CreateInviteReq req) {

        ObjectId tenantId = TenantContext.getTenantId();
        var inv = invites.create(tenantId, req.email(), req.roles(), Duration.ofHours(req.ttlHours() == null ? 24 : req.ttlHours()));

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "token", inv.getToken(),     // devuélvelo sólo en pruebas; en prod se manda por correo
                "expiresAt", inv.getExpiresAt()
        ));
    }

    public record CreateInviteReq(String email, List<String> roles, Long ttlHours) {}

}
