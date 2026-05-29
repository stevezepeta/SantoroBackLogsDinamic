package backlogs.dinamico.service.email;

import backlogs.dinamico.service.ai.dto.AlertEmailDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Implementación de desarrollo — imprime el OTP en el log.
 * Activa cuando NO hay perfil sendgrid/ses.
 *
 * Para cambiar a producción, añade en application.properties:
 *   spring.profiles.active=sendgrid
 * y crea SendGridEmailSender con @Profile("sendgrid").
 */
@Slf4j
@Service
@Profile("!mail & !sendgrid & !ses")
public class ConsoleEmailSender implements EmailSenderPort {

    @Override
    public void sendOtp(String toEmail, String otp, int ttlMinutes) {
        log.info("""
                ╔══════════════════════════════════════════╗
                ║         EMAIL VERIFICATION OTP           ║
                ║  To      : {}
                ║  OTP     : {}
                ║  Expires : {} minutos
                ╚══════════════════════════════════════════╝
                """, toEmail, otp, ttlMinutes);
    }

    @Override
    public void sendInviteOtp(String toEmail, String otp, int ttlHours, String orgName) {
        log.info("""
                ╔══════════════════════════════════════════╗
                ║           INVITE OTP CODE                ║
                ║  To      : {}
                ║  Org     : {}
                ║  OTP     : {}
                ║  Usar en : POST /api/auth/accept-invite  ║
                ║  Expires : {} horas
                ╚══════════════════════════════════════════╝
                """, toEmail, orgName, otp, ttlHours);
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String userName, String code, int ttlMinutes) {
        log.info("""
                ╔══════════════════════════════════════════╗
                ║      PASSWORD RESET CODE                 ║
                ║  To      : {}
                ║  Name    : {}
                ║  Code    : {}
                ║  Usar en : POST /api/auth/reset-password ║
                ║  Expires : {} minutos
                ╚══════════════════════════════════════════╝
                """, toEmail, userName, code, ttlMinutes);
    }

    @Override
    public void sendAlertNotification(String toEmail, String toName, AlertEmailDto alert) {

    }

    @Override
    public void sendAlertWithPdf(String toEmail, String toName, String subject, String bodyHtml, byte[] pdfBytes, String fileName) {

    }
}