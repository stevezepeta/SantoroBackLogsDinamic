package backlogs.dinamico.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@Profile("mail")
public class MailConfig {

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol",        "smtp");
        props.put("mail.smtp.auth",                  "true");
        props.put("mail.smtp.starttls.enable",        "true");
        props.put("mail.smtp.starttls.required",      "true");
        props.put("mail.smtp.ssl.enable",             "false");
        props.put("mail.smtp.ssl.trust",              "smtp.gmail.com");
        props.put("mail.smtp.connectiontimeout",      "10000");
        props.put("mail.smtp.timeout",                "10000");
        props.put("mail.smtp.writetimeout",           "10000");
        props.put("mail.debug",                       "false");   // true solo para debug

        return mailSender;
    }


}
