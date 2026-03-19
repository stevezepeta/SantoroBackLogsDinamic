package backlogs.dinamico.service.email;

public interface EmailSenderPort {

    void sendOtp(String toEmail, String otp, int ttlMinutes);

    void sendInviteOtp(String toEmail, String otp, int ttlHours, String orgName);

}
