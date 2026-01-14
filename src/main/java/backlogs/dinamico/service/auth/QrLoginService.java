package backlogs.dinamico.service.auth;

import backlogs.dinamico.api.dto.auth.LoginResponse;
import backlogs.dinamico.api.dto.auth.QrTokenResponse;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.infra.security.JwtTokenService;
import backlogs.dinamico.model.auth.QrLoginSession;
import backlogs.dinamico.model.core.Role;
import backlogs.dinamico.model.core.User;
import backlogs.dinamico.repository.auth.QrLoginSessionRepository;
import backlogs.dinamico.repository.core.RoleRepository;
import backlogs.dinamico.repository.core.UserRoleRepository;
import backlogs.dinamico.service.core.UserService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QrLoginService {

    private final QrLoginSessionRepository qrRepo;
    private final UserService userService;
    private final JwtTokenService jwtTokenService;
    private final QrLoginNotifier qrLoginNotifier;
    private final UserRoleRepository userRoleRepo;
    private final RoleRepository roleRepo;

    // Duracion del token 2 min
    private static final Duration QR_TOKEN_TTL = Duration.ofMinutes(2);

    public QrTokenResponse createQrToken() {
        String qrToken = UUID.randomUUID().toString();
        Instant now = Instant.now();

        QrLoginSession session = new QrLoginSession();
        session.setQrToken(qrToken);
        session.setTenantId(null);
        session.setEmail(null);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(QR_TOKEN_TTL));
        session.setUsed(false);

        qrRepo.save(session);

        return new QrTokenResponse(qrToken, QR_TOKEN_TTL.toSeconds());
    }

    public LoginResponse loginWithQrToken(String qrToken, Authentication auth) {

        if (qrToken == null || qrToken.isBlank()) {
            throw new IllegalArgumentException("qrToken is required");
        }

        if (auth == null || auth.getPrincipal() == null) {
            qrLoginNotifier.notifyError(qrToken);
            throw new IllegalArgumentException("No autenticado");
        }

        if (!(auth.getPrincipal() instanceof AuthUser authUser)) {
            qrLoginNotifier.notifyError(qrToken);
            throw new IllegalArgumentException(
                    "Unsupported principal type for qr-login: " + auth.getPrincipal().getClass().getName()
            );
        }

        // Se obtienen datos desde el JWT
        ObjectId tenantId = authUser.tenantId();
        String email = authUser.email();

        if (tenantId == null || email == null || email.isBlank()) {
            qrLoginNotifier.notifyError(qrToken);
            throw new IllegalArgumentException("Token sin tenantId/email");
        }

        // 1) Cargar la sesión de QR
        QrLoginSession session = qrRepo.findByQrToken(qrToken)
                .orElseThrow(() -> {
                    // QR inexistente o inválido
                    qrLoginNotifier.notifyError(qrToken);
                    return new IllegalArgumentException("Token QR inválido");
                });


        Instant now = Instant.now();

        if (session.isUsed()) {
            qrLoginNotifier.notifyAlreadyUsed(qrToken);
            throw new IllegalArgumentException("QR token already used");
        }

        // Validar expiración
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(now)) {
            session.setUsed(true);
            qrRepo.save(session);

            qrLoginNotifier.notifyExpired(qrToken);
            throw new IllegalArgumentException("QR token expired");
        }

        // Se recupera el User
        User user = userService.findByEmail(tenantId, email)
                .orElseThrow(() -> {
                    qrLoginNotifier.notifyError(qrToken);
                    return new IllegalStateException("User not found");
                });

        if (!"active".equalsIgnoreCase(user.getStatus())) {
            qrLoginNotifier.notifyError(qrToken);
            throw new IllegalStateException("inactive_user");
        }

        // Cargar roles
        List<Role> roles = userRoleRepo.findByTenantIdAndUserId(tenantId, user.getId())
                .stream()
                .map(link -> roleRepo.findById(link.getRoleId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        // Generar tokens con roles
        String accessToken = jwtTokenService.generate(user, roles, tenantId);
        String refreshToken = jwtTokenService.generateRefresh(user, roles, tenantId);

        LoginResponse resp = new LoginResponse(accessToken, refreshToken, "Bearer");

        // Marcar sesion como usada (one-time)
        session.setUsed(true);
        session.setTenantId(tenantId.toHexString());
        session.setEmail(email);
        qrRepo.save(session);

        // Notificacion a la PC
        qrLoginNotifier.notifySuccess(qrToken, resp);

        return resp;
    }

}
