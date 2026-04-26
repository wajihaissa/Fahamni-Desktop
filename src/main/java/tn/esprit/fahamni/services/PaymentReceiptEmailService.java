package tn.esprit.fahamni.services;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import tn.esprit.fahamni.utils.LocalConfig;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;

public class PaymentReceiptEmailService {

    private static final String DEFAULT_FROM_NAME = "Fahamni";
    private static final int DEFAULT_SMTP_PORT = 587;
    private static final int DEFAULT_TIMEOUT_MS = 15000;
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public boolean isConfigured() {
        return normalize(LocalConfig.get("SMTP_HOST")) != null && normalize(resolveFromAddress()) != null;
    }

    public EmailDeliveryResult sendPaymentReceipt(PaymentReceipt receipt) {
        if (receipt == null) {
            return EmailDeliveryResult.failure("Le recu de paiement est introuvable.");
        }
        if (!isConfigured()) {
            return EmailDeliveryResult.failure("Le serveur SMTP n'est pas configure.");
        }

        String recipientEmail = normalize(receipt.participantEmail());
        if (recipientEmail == null) {
            return EmailDeliveryResult.failure("Aucune adresse email n'est disponible pour le recu.");
        }

        try {
            Session session = buildSession();
            MimeMessage message = new MimeMessage(session);
            message.setFrom(buildFromAddress());
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail, false));
            message.setSubject("Votre recu de paiement Fahamni - Reservation #" + receipt.reservationId(), "UTF-8");
            message.setContent(buildHtmlBody(receipt), "text/html; charset=UTF-8");
            Transport.send(message);
            return EmailDeliveryResult.success("Recu envoye a " + recipientEmail + ".");
        } catch (MessagingException | UnsupportedEncodingException exception) {
            return EmailDeliveryResult.failure(resolveFailureMessage(exception));
        }
    }

    private Session buildSession() {
        String host = normalize(LocalConfig.get("SMTP_HOST"));
        String username = normalize(LocalConfig.get("SMTP_USERNAME"));
        String password = normalize(LocalConfig.get("SMTP_PASSWORD"));
        boolean authenticationEnabled = username != null && password != null;
        boolean sslEnabled = resolveBoolean("SMTP_SSL", false);
        boolean startTlsEnabled = resolveBoolean("SMTP_STARTTLS", !sslEnabled);
        int port = resolveInt("SMTP_PORT", DEFAULT_SMTP_PORT);

        Properties properties = new Properties();
        properties.setProperty("mail.smtp.host", host);
        properties.setProperty("mail.smtp.port", String.valueOf(port));
        properties.setProperty("mail.smtp.auth", String.valueOf(authenticationEnabled));
        properties.setProperty("mail.smtp.starttls.enable", String.valueOf(startTlsEnabled));
        properties.setProperty("mail.smtp.ssl.enable", String.valueOf(sslEnabled));
        properties.setProperty("mail.smtp.connectiontimeout", String.valueOf(DEFAULT_TIMEOUT_MS));
        properties.setProperty("mail.smtp.timeout", String.valueOf(DEFAULT_TIMEOUT_MS));
        properties.setProperty("mail.smtp.writetimeout", String.valueOf(DEFAULT_TIMEOUT_MS));

        if (!authenticationEnabled) {
            return Session.getInstance(properties);
        }

        return Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    private InternetAddress buildFromAddress() throws UnsupportedEncodingException {
        return new InternetAddress(resolveFromAddress(), resolveFromName(), "UTF-8");
    }

    private String buildHtmlBody(PaymentReceipt receipt) {
        String participantName = fallback(receipt.participantName(), "Client Fahamni");
        String seanceTitle = fallback(receipt.seanceTitle(), "Reservation Fahamni");
        String tutorName = fallback(receipt.tutorName(), "Non renseigne");
        String paymentReference = fallback(receipt.paymentRef(), "Non renseigne");
        String paymentDate = formatDateTime(receipt.paymentCompletedAt());
        String seanceDate = formatDateTime(receipt.seanceStartAt());
        String duration = receipt.durationMin() > 0 ? receipt.durationMin() + " min" : "Non renseigne";
        String amount = formatAmountTnd(receipt.amountMillimes());

        return """
            <html>
              <body style="margin:0;padding:24px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                  <tr>
                    <td align="center">
                      <table role="presentation" width="640" cellspacing="0" cellpadding="0" style="background:#ffffff;border-radius:24px;border:1px solid #dce4f1;overflow:hidden;">
                        <tr>
                          <td style="padding:28px 32px;background:linear-gradient(90deg,#5e4dc7 0%%,#4c67cb 100%%);color:#ffffff;">
                            <div style="font-size:12px;letter-spacing:1px;font-weight:bold;text-transform:uppercase;opacity:0.9;">Fahamni</div>
                            <div style="margin-top:8px;font-size:28px;font-weight:bold;">Recu de paiement</div>
                            <div style="margin-top:10px;font-size:14px;line-height:1.6;opacity:0.92;">Merci %s, votre paiement a bien ete confirme pour la reservation #%d.</div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 32px;">
                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="margin-bottom:18px;background:#f7f9fe;border:1px solid #dbe3f0;border-radius:18px;">
                              <tr>
                                <td style="padding:18px 20px;">
                                  <div style="font-size:13px;color:#68758a;margin-bottom:8px;">Montant paye</div>
                                  <div style="font-size:30px;font-weight:bold;color:#243147;">%s</div>
                                  <div style="margin-top:8px;font-size:13px;color:#607086;">Paiement valide le %s</div>
                                </td>
                              </tr>
                            </table>
                            %s
                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:separate;border-spacing:0 10px;">
                              <tr>
                                <td style="width:45%%;font-size:13px;color:#6b7280;">Seance</td>
                                <td style="font-size:13px;font-weight:bold;color:#1f2937;">%s</td>
                              </tr>
                              <tr>
                                <td style="width:45%%;font-size:13px;color:#6b7280;">Date de la seance</td>
                                <td style="font-size:13px;font-weight:bold;color:#1f2937;">%s</td>
                              </tr>
                              <tr>
                                <td style="width:45%%;font-size:13px;color:#6b7280;">Duree</td>
                                <td style="font-size:13px;font-weight:bold;color:#1f2937;">%s</td>
                              </tr>
                              <tr>
                                <td style="width:45%%;font-size:13px;color:#6b7280;">Tuteur</td>
                                <td style="font-size:13px;font-weight:bold;color:#1f2937;">%s</td>
                              </tr>
                              <tr>
                                <td style="width:45%%;font-size:13px;color:#6b7280;">Reference Stripe</td>
                                <td style="font-size:13px;font-weight:bold;color:#1f2937;">%s</td>
                              </tr>
                            </table>
                            <div style="margin-top:24px;padding:16px 18px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:16px;font-size:13px;line-height:1.6;color:#5a667a;">
                              Ce message confirme la bonne reception de votre paiement pour Fahamni. Conservez-le comme recu de paiement.
                            </div>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(
            escapeHtml(participantName),
            receipt.reservationId(),
            escapeHtml(amount),
            escapeHtml(paymentDate),
            buildParticipantEmailBlock(receipt.participantEmail()),
            escapeHtml(seanceTitle),
            escapeHtml(seanceDate),
            escapeHtml(duration),
            escapeHtml(tutorName),
            escapeHtml(paymentReference)
        );
    }

    private String buildParticipantEmailBlock(String participantEmail) {
        String normalizedEmail = normalize(participantEmail);
        if (normalizedEmail == null) {
            return "";
        }
        return """
            <div style="margin:0 0 18px 0;padding:12px 14px;background:#eef4ff;border:1px solid #d7e2fb;border-radius:14px;font-size:13px;color:#35518b;">
              Recu envoye a <strong>%s</strong>
            </div>
            """.formatted(escapeHtml(normalizedEmail));
    }

    private String formatAmountTnd(int amountMillimes) {
        BigDecimal amount = BigDecimal.valueOf(Math.max(0, amountMillimes), 3);
        return String.format(Locale.ROOT, "%.3f TND", amount.doubleValue());
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.format(DISPLAY_FORMATTER) : "Non renseigne";
    }

    private int resolveInt(String key, int defaultValue) {
        String configuredValue = normalize(LocalConfig.get(key));
        if (configuredValue == null) {
            return defaultValue;
        }
        try {
            int parsedValue = Integer.parseInt(configuredValue);
            return parsedValue > 0 ? parsedValue : defaultValue;
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private boolean resolveBoolean(String key, boolean defaultValue) {
        String configuredValue = normalize(LocalConfig.get(key));
        if (configuredValue == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(configuredValue)
            || "1".equals(configuredValue)
            || "yes".equalsIgnoreCase(configuredValue)
            || "on".equalsIgnoreCase(configuredValue);
    }

    private String resolveFromAddress() {
        String explicitFrom = normalize(LocalConfig.get("SMTP_FROM"));
        if (explicitFrom != null) {
            return explicitFrom;
        }
        return normalize(LocalConfig.get("SMTP_USERNAME"));
    }

    private String resolveFromName() {
        String configuredName = normalize(LocalConfig.get("SMTP_FROM_NAME"));
        return configuredName != null ? configuredName : DEFAULT_FROM_NAME;
    }

    private String resolveFailureMessage(Exception exception) {
        Throwable rootCause = rootCause(exception);
        if (rootCause instanceof MessagingException) {
            return "La connexion SMTP a echoue.";
        }
        return "L'envoi du recu a echoue.";
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String fallback(String value, String fallbackValue) {
        String normalized = normalize(value);
        return normalized != null ? normalized : fallbackValue;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    public record PaymentReceipt(
        int reservationId,
        String participantName,
        String participantEmail,
        String tutorName,
        String seanceTitle,
        LocalDateTime seanceStartAt,
        int durationMin,
        int amountMillimes,
        String paymentRef,
        LocalDateTime paymentCompletedAt
    ) {
    }

    public record EmailDeliveryResult(boolean success, String message) {
        public static EmailDeliveryResult success(String message) {
            return new EmailDeliveryResult(true, message);
        }

        public static EmailDeliveryResult failure(String message) {
            return new EmailDeliveryResult(false, message);
        }
    }
}
