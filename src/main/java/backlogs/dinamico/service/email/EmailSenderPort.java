package backlogs.dinamico.service.email;

import backlogs.dinamico.service.ai.dto.AlertEmailDto;

public interface EmailSenderPort {

    void sendOtp(String toEmail, String otp, int ttlMinutes);

    void sendInviteOtp(String toEmail, String otp, int ttlHours, String orgName);

    void sendPasswordResetCode(String toEmail, String userName, String code, int ttlMinutes);

    void sendAlertNotification(String toEmail, String toName, AlertEmailDto alert);

    void sendAlertWithPdf(String toEmail, String toName,
                          String subject, String bodyHtml,
                          byte[] pdfBytes, String fileName);

}
