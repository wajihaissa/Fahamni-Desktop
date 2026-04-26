package tn.esprit.fahamni.services;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import tn.esprit.fahamni.utils.AppConfig;
import tn.esprit.fahamni.utils.OperationResult;

import java.util.Properties;

public class MailService {

    public boolean isConfigured() {
        return AppConfig.isMailConfigured();
    }

    public OperationResult sendPasswordResetCode(String recipientEmail, String fullName, String resetCode) {
        if (!isConfigured()) {
            return OperationResult.failure("La configuration email est incomplete. Verifiez MAILER_HOST, MAILER_PORT, MAILER_USERNAME, MAILER_PASSWORD et MAILER_FROM.");
        }

        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.host", AppConfig.getMailerHost());
        properties.put("mail.smtp.port", String.valueOf(AppConfig.getMailerPort()));
        properties.put("mail.smtp.ssl.enable", "true");
        properties.put("mail.smtp.starttls.enable", "false");

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(AppConfig.getMailerUsername(), AppConfig.getMailerPassword());
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(AppConfig.getMailerFrom(), "Fahamni"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject("Fahamni - Code de reinitialisation");
            message.setText(buildResetBody(fullName, resetCode));

            Transport.send(message);
            return OperationResult.success("Email de reinitialisation envoye.");
        } catch (Exception e) {
            System.out.println("MailService error: " + e.getMessage());
            return OperationResult.failure("Impossible d'envoyer l'email de reinitialisation : " + e.getMessage());
        }
    }

    private String buildResetBody(String fullName, String resetCode) {
        String displayName = fullName == null || fullName.isBlank() ? "Bonjour" : "Bonjour " + fullName;
        return displayName + ",\n\n"
            + "Vous avez demande la reinitialisation de votre mot de passe Fahamni.\n"
            + "Voici votre code de reinitialisation : " + resetCode + "\n\n"
            + "Ce code expirera dans 30 minutes et ne peut etre utilise qu'une seule fois.\n"
            + "Si vous n'etes pas a l'origine de cette demande, ignorez simplement cet email.\n\n"
            + "Equipe Fahamni";
    }
}
