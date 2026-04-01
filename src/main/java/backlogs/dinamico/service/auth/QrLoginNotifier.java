package backlogs.dinamico.service.auth;

import backlogs.dinamico.api.dto.auth.LoginResponse;
import backlogs.dinamico.api.dto.auth.QrLoginStatusMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import backlogs.dinamico.api.dto.auth.QrLoginStatusMessage.Status;

@Slf4j
@Service
@RequiredArgsConstructor
public class QrLoginNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    private String destinationOf(String qrToken) {
        return "/topic/qr-login/" + qrToken;
    }

    public void safeSend(String qrToken, QrLoginStatusMessage msg) {
        if (!StringUtils.hasText(qrToken)) {
            log.warn("[QR-LOGIN] No se envió WS porque qrToken es null/vacío. msg={}", msg);
            return;
        }

        String destination = destinationOf(qrToken);
        log.info("[QR-LOGIN] Notificando {} a {}", msg.status(), destination);
        messagingTemplate.convertAndSend(destination, msg);
    }

    // Se notifica al navegador que el QR fue utilizado
    public void notifySuccess(String qrToken, LoginResponse login) {
        QrLoginStatusMessage msg = new QrLoginStatusMessage(
                Status.APPROVED,
                login != null ? login.accessToken() : null,
                login != null ? login.refreshToken() : null,
                login != null ? login.tokenType() : null,
                "QR login aprobado"
        );

        safeSend(qrToken, msg);
    }

    // Notificar que el Qr expiro
    public void notifyExpired(String qrToken) {
        QrLoginStatusMessage msg = new QrLoginStatusMessage(
                Status.EXPIRED,
                null,
                null,
                null,
                "El codigo QR ha expirado. Genera uno nuevo."
        );

        safeSend(qrToken, msg);
    }

    public void notifyAlreadyUsed(String qrToken) {
        QrLoginStatusMessage msg = new QrLoginStatusMessage(
                Status.ALREADY_USED,
                null,
                null,
                null,
                "Este codigo QR ya fue utilizado"
        );

        safeSend(qrToken, msg);
    }

    public void notifyError(String qrToken) {
        QrLoginStatusMessage msg = new QrLoginStatusMessage(
                Status.ERROR,
                null,
                null,
                null,
                "Ocurrio un error al procesar QR."
        );

        safeSend(qrToken, msg);
    }

}
