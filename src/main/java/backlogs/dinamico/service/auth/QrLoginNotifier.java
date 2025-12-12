package backlogs.dinamico.service.auth;

import backlogs.dinamico.api.dto.auth.LoginResponse;
import backlogs.dinamico.api.dto.auth.QrLoginStatusMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QrLoginNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    // Se notifica al navegador que el QR fue utilizado
    public void notifySuccess(String qrToken, LoginResponse login) {

        QrLoginStatusMessage msg = new QrLoginStatusMessage(
                "APROVED",
                login.accessToken(),
                login.refreshToken()
        );

        String destination = "/topic/qr-login/" + qrToken;
    
        messagingTemplate.convertAndSend(destination, msg);
    }

    // Notificar que el Qr expiro
    public void notifyExpired(String qrToken) {

        QrLoginStatusMessage msg = new QrLoginStatusMessage(
                "EXPIRED",
                null,
                null
        );

        String destination = "/topic/qr-login/" + qrToken;
        messagingTemplate.convertAndSend(destination, msg);
    }

}
