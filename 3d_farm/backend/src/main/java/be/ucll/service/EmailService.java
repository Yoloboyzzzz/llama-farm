package be.ucll.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.mail.from}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Verify your 3D Farm account");
        message.setText(
            "Welcome to 3D Farm!\n\n" +
            "Please click the link below to verify your email address and activate your account:\n\n" +
            baseUrl + "/verify-email?token=" + token + "\n\n" +
            "If you did not create an account, you can safely ignore this email."
        );
        mailSender.send(message);
    }
}
