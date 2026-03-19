package backlogs.dinamico.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Implementación de producción usando JavaMailSender (Gmail / Google Workspace).
 * Misma config que el otro backend (tickets).
 *
 * Activar con: spring.profiles.active=mail
 *
 * Requiere en application.properties:
 *   spring.mail.host=smtp.gmail.com
 *   spring.mail.port=587
 *   spring.mail.username=soporte.tecnico@grupo-santoro.com.mx
 *   spring.mail.password=${MAIL_APP_PASSWORD}
 *   spring.mail.properties.mail.smtp.auth=true
 *   spring.mail.properties.mail.smtp.starttls.enable=true
 *   spring.mail.properties.mail.smtp.starttls.required=true
 *   app.mail.from=soporte.tecnico@grupo-santoro.com.mx
 */
@Slf4j
@Service
@Profile("mail")
@RequiredArgsConstructor
public class JavaMailEmailSender implements EmailSenderPort {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:soporte.tecnico@grupo-santoro.com.mx}")
    private String fromEmail;

    @Value("${app.mail.from-name:Backlogs Santoro}")
    private String fromName;

    @Value("${app.frontend.base-url:http://187.188.66.56:8032}")
    private String frontendBaseUrl;

    @Override
    public void sendOtp(String toEmail, String otp, int ttlMinutes) {
        sendEmail(toEmail, "Tu código de verificación - Backlogs Santoro",
                buildBaseTemplate(
                        "Verifica tu correo",
                        "Usa el siguiente código para completar tu verificación.",
                        ttlMinutes + " minutos", otp, null, null));
    }

    @Override
    public void sendInviteOtp(String toEmail, String otp, int ttlHours, String orgName) {
        String displayOrg = (orgName != null && !orgName.isBlank()) ? orgName : "tu organización";
        String acceptLink = frontendBaseUrl + "/accept-invite?otp=" + otp;
        sendEmail(toEmail,
                "Has sido invitado a " + displayOrg + " · DataLogs",
                buildBaseTemplate(
                        "Has sido invitado a<br><strong class='org-name'>" + displayOrg + "</strong>",
                        "Alguien de <strong>" + displayOrg + "</strong> te ha invitado a acceder<br>al panel de gestión de logs <strong>DataLogs</strong>.",
                        ttlHours + " horas", otp, acceptLink, "Aceptar invitación"));
    }

    private void sendEmail(String toEmail, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("[JavaMail] Email enviado a {}", toEmail);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("[JavaMail] Error enviando a {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("email_send_failed: " + e.getMessage());
        }
    }

    private String buildBaseTemplate(String title, String subtitle, String ttlLabel,
                                     String otp, String ctaUrl, String ctaLabel) {
        String ctaBlock = ctaUrl == null ? "" : """
            <div style="text-align:center;margin:28px 0 8px">
              <a href="%s" class="cta-btn"
                 style="display:inline-block;background:#111827;color:#ffffff;
                        text-decoration:none;font-size:14px;font-weight:700;
                        padding:13px 38px;border-radius:6px">
                %s &rarr;
              </a>
            </div>
            <p style="text-align:center;color:#6b7280;font-size:11px;margin:0 0 24px">
              O ingresa el código manualmente en la pantalla de invitación
            </p>
            """.formatted(ctaUrl, ctaLabel);

        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
            <meta charset="UTF-8">
            <meta name="color-scheme" content="light dark">
            <meta name="supported-color-schemes" content="light dark">
            <style>
              /* ── Tema claro (default) ── */
              body        { background:#f3f4f6 !important; }
              .email-wrap { background:#ffffff !important; border-color:#e5e7eb !important; }
              .body-td    { background:#ffffff !important; }
              .title      { color:#111827 !important; }
              .subtitle   { color:#6b7280 !important; }
              .org-name   { color:#111827 !important; }
              .otp-box    { background:#f9fafb !important; border-color:#e5e7eb !important; }
              .otp-label  { color:#9ca3af !important; }
              .otp-code   { color:#111827 !important; }
              .otp-ttl    { color:#6b7280 !important; }
              .otp-ttl strong { color:#374151 !important; }
              .warn-box   { background:#fefce8 !important; border-color:#fde68a !important; }
              .warn-text  { color:#92400e !important; }
              .footer-td  { background:#f9fafb !important; border-color:#e5e7eb !important; }
              .footer-p   { color:#9ca3af !important; }
              .footer-strong { color:#374151 !important; }
              .footer-a   { color:#374151 !important; }
              .hint-text  { color:#6b7280 !important; }
              .cta-btn    { background:#111827 !important; color:#ffffff !important; }

              /* ── Tema oscuro ── */
              @media (prefers-color-scheme: dark) {
                body        { background:#0f172a !important; }
                .email-wrap { background:#1e293b !important; border-color:#334155 !important; }
                .body-td    { background:#1e293b !important; }
                .title      { color:#f1f5f9 !important; }
                .subtitle   { color:#94a3b8 !important; }
                .org-name   { color:#e2e8f0 !important; }
                .otp-box    { background:#0f172a !important; border-color:#334155 !important; border-left-color:#6366f1 !important; }
                .otp-label  { color:#64748b !important; }
                .otp-code   { color:#e2e8f0 !important; }
                .otp-ttl    { color:#64748b !important; }
                .otp-ttl strong { color:#94a3b8 !important; }
                .warn-box   { background:rgba(120,53,15,0.3) !important; border-color:#92400e !important; }
                .warn-text  { color:#fbbf24 !important; }
                .footer-td  { background:#0f172a !important; border-color:#1e293b !important; }
                .footer-p   { color:#475569 !important; }
                .footer-strong { color:#64748b !important; }
                .footer-a   { color:#64748b !important; }
                .hint-text  { color:#475569 !important; }
                .cta-btn    { background:#6366f1 !important; color:#ffffff !important; }
              }
            </style>
            </head>
            <body style="margin:0;padding:0;font-family:Arial,sans-serif">
            <table width="100%%" cellpadding="0" cellspacing="0">
            <tr><td align="center" style="padding:40px 16px">
            <table width="540" cellpadding="0" cellspacing="0" class="email-wrap"
                   style="border-radius:8px;overflow:hidden;border:1px solid #e5e7eb">

              <!-- HEADER — siempre oscuro -->
              <tr>
                <td style="background:#111827;padding:24px 32px">
                  <div style="font-size:20px;font-weight:900;color:#ffffff;
                              letter-spacing:2px">DataLogs</div>
                  <div style="font-size:10px;color:#6b7280;letter-spacing:2px;margin-top:3px">
                    SISTEMA DE GESTIÓN DE LOGS
                  </div>
                </td>
              </tr>
              <tr><td style="height:3px;background:linear-gradient(90deg,#334155,#64748b,#334155)"></td></tr>

              <!-- BODY -->
              <tr>
                <td class="body-td" style="padding:36px 40px 28px">
                  <h1 class="title" style="font-size:20px;margin:0 0 12px;line-height:1.4">
                    %s
                  </h1>
                  <p class="subtitle" style="font-size:14px;line-height:1.7;margin:0 0 28px">
                    %s
                  </p>

                  %s

                  <!-- OTP BOX -->
                  <div class="otp-box"
                       style="border:1px solid #e5e7eb;border-left:4px solid #111827;
                              border-radius:4px;padding:24px;text-align:center;margin:0 0 24px">
                    <div class="otp-label"
                         style="font-size:10px;letter-spacing:3px;
                                text-transform:uppercase;margin-bottom:10px">
                      Código de acceso
                    </div>
                    <div class="otp-code"
                         style="font-size:44px;font-weight:900;letter-spacing:10px;
                                font-family:monospace">%s</div>
                    <div class="otp-ttl" style="font-size:11px;margin-top:10px">
                      Válido por <strong>%s</strong>
                    </div>
                  </div>

                  <!-- WARNING -->
                  <div class="warn-box"
                       style="background:#fefce8;border:1px solid #fde68a;
                              border-radius:4px;padding:12px 16px">
                    <p class="warn-text" style="margin:0;font-size:12px">
                      &#9888; Si no solicitaste esta invitación, ignora este mensaje.
                      Nadie de nuestro equipo te pedirá este código por teléfono.
                    </p>
                  </div>
                </td>
              </tr>

              <!-- FOOTER -->
              <tr>
                <td class="footer-td"
                    style="background:#f9fafb;padding:16px 40px;border-top:1px solid #e5e7eb">
                  <p class="footer-p" style="margin:0;font-size:11px">
                    <strong class="footer-strong">DataLogs</strong> &middot;
                    Grupo Santoro &middot;
                    <a class="footer-a" href="mailto:soporte.tecnico@grupo-santoro.com.mx"
                       style="text-decoration:none">
                      soporte.tecnico@grupo-santoro.com.mx
                    </a>
                  </p>
                </td>
              </tr>

            </table>
            </td></tr>
            </table>
            </body></html>
            """.formatted(title, subtitle, ctaBlock, otp, ttlLabel);
    }

}