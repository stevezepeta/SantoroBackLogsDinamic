package backlogs.dinamico.service.auth;

import backlogs.dinamico.api.dto.auth.LoginResponse;
import backlogs.dinamico.api.dto.auth.QrLoginStatusMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QrLoginNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    private String destinationOf(String qrToken) {
        return "/topic/qr-login/" + qrToken;
    }

    // Se notifica al navegador que el QR fue utilizado
    public void notifySuccess(String qrToken, LoginResponse login) {

        QrLoginStatusMessage msg = new QrLoginStatusMessage(
                "APROVED",
                login.accessToken(),
                login.refreshToken(),
                login.tokenType(),
                "QR login aprobado"
        );

        String destination = "/topic/qr-login/" + qrToken;
        log.info("[QR-LOGIN] Notificando APPROVED a {}", destination);
        messagingTemplate.convertAndSend(destination, msg);
    }

    // Notificar que el Qr expiro
    public void notifyExpired(String qrToken) {
        QrLoginStatusMessage msg = new QrLoginStatusMessage(
                "EXPIRED",
                null,
                null,
                null,
                "El codigo QR ha expirado. Genera uno nuevo."
        );

        String destination = "/topic/qr-login/" + qrToken;
        log.info("[QR-LOGIN] Notificando EXPIRED a {}", destination);
        messagingTemplate.convertAndSend(destination, msg);
    }

    public void notifyAlreadyUsed(String qrToken) {
        QrLoginStatusMessage msg = new QrLoginStatusMessage(
                "ALREADY_USED",
                null,
                null,
                null,
                "Este codigo QR ya fue utilizado"
        );

        String destination = destinationOf(qrToken);
        log.info("[QR-LOGIN] Notificando ALREADY_USED a {}", destination);
        messagingTemplate.convertAndSend(destination, msg);
    }

    public void notifyError(String qrToken) {
        QrLoginStatusMessage msg = new QrLoginStatusMessage(
                "ERROR",
                null,
                null,
                null,
                "Ocurrio un error al procesar QR."
        );

        String destination = destinationOf(qrToken);
        log.info("[QR-LOGIN] Notificando ERROR a {}", destination);
        messagingTemplate.convertAndSend(destination, msg);
    }

}
