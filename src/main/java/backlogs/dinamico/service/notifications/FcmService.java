package backlogs.dinamico.service.notifications;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FcmService {

    /**
     * Envía notificación push a un token FCM específico.
     */
    public void sendNotification(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("[FCM] Token vacío — omitiendo envío");
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setWebpushConfig(WebpushConfig.builder()
                            .setNotification(WebpushNotification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .setIcon("/icons/favicon-96x96.png")
                                    .setBadge("/icons/favicon-32x32.png")
                                    .setRequireInteraction(true)
                                    .build())
                            .putHeader("Urgency", "high")
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("[FCM] Notificación enviada — messageId: {}", response);

        } catch (FirebaseMessagingException e) {
            // Token inválido o expirado → limpiar de la BD
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("[FCM] Token inválido/expirado: {}", fcmToken);
            } else {
                log.error("[FCM] Error enviando notificación: {}", e.getMessage());
            }
        }
    }

    /**
     * Envía notificación a múltiples tokens a la vez.
     */
    public void sendToMultiple(java.util.List<String> tokens, String title, String body) {
        if (tokens == null || tokens.isEmpty()) return;
        tokens.forEach(token -> sendNotification(token, title, body));
    }
}