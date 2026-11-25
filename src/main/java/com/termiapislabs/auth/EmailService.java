package com.termiapislabs.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail-from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendConfirmationCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Código de confirmação — TermiAPILabs");
        message.setText("Seu código de confirmação é: " + code + "\n\nEle expira em 15 minutos.");
        mailSender.send(message);
    }

    public void sendPasswordResetCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Recuperação de senha — TermiAPILabs");
        message.setText("Seu código para redefinição de senha é: " + code
            + "\n\nSe você não solicitou esta ação, ignore este e-mail.");
        mailSender.send(message);
    }
}
