package backlogs.dinamico.controller.notifications;

import backlogs.dinamico.api.ApiResponse;
import backlogs.dinamico.infra.security.AuthUser;
import backlogs.dinamico.service.notifications.FcmTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications/fcm")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    @PostMapping("/token")
    public ApiResponse<?> registerToken(
            @RequestBody java.util.Map<String, String> body,
            Authentication auth
    ) {
        AuthUser user  = (AuthUser) auth.getPrincipal();
        String   token = body.get("token");

        if (token == null || token.isBlank()) {
            return ApiResponse.error(null,"token_required", "El token FCM es requerido");
        }

        fcmTokenService.saveToken(user.getTenantId(), user.getUserId(), token);
        return ApiResponse.ok("Token FCM registrado", "fcm_token_saved", null);
    }
}