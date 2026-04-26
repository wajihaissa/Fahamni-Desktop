package tn.esprit.fahamni.services;

import jakarta.mail.Authenticator;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import tn.esprit.fahamni.Models.User;
import tn.esprit.fahamni.Models.quiz.Quiz;
import tn.esprit.fahamni.Models.quiz.QuizResult;
import tn.esprit.fahamni.utils.EnvConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuizCertificationMailer {
    private static final ExecutorService MAIL_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "quiz-cert-mailer");
        thread.setDaemon(true);
        return thread;
    });
    private static final DateTimeFormatter CERTIFICATE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault());

    public CompletableFuture<MailDispatchResult> sendPassCertificateAsync(User user, Quiz quiz, QuizResult result) {
        if (!canSendCertificate(user, quiz, result)) {
            return CompletableFuture.completedFuture(
                    MailDispatchResult.skipped("Certificate email skipped: quiz result is not eligible for mailing.")
            );
        }

        MailSettings settings = MailSettings.fromEnvironment();
        if (!settings.isConfigured()) {
            String message = "Quiz certificate email skipped: SMTP settings are not configured.";
            System.out.println(message);
            return CompletableFuture.completedFuture(MailDispatchResult.skipped(message));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                sendPassCertificate(settings, user, quiz, result);
                String message = "Quiz certificate email sent to " + user.getEmail()
                        + " for quiz '" + safeText(quiz.getTitre(), "Quiz Achievement") + "'.";
                System.out.println(message);
                return MailDispatchResult.success(user.getEmail(), message);
            } catch (MessagingException e) {
                String message = "Error sending quiz certificate email to " + user.getEmail() + ": " + e.getMessage();
                System.err.println(message);
                return MailDispatchResult.failure(user.getEmail(), message);
            }
        }, MAIL_EXECUTOR);
    }

    private boolean canSendCertificate(User user, Quiz quiz, QuizResult result) {
        return user != null
                && user.getEmail() != null
                && !user.getEmail().isBlank()
                && quiz != null
                && !isGeneratedPracticeQuiz(quiz)
                && result != null
                && Boolean.TRUE.equals(result.getPassed());
    }

    private boolean isGeneratedPracticeQuiz(Quiz quiz) {
        String keyword = quiz.getKeyword();
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        return keyword.startsWith("adaptive-") || keyword.startsWith("mistake-retry-");
    }

    private void sendPassCertificate(MailSettings settings, User user, Quiz quiz, QuizResult result) throws MessagingException {
        Session session = Session.getInstance(settings.toProperties(), new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (settings.username == null || settings.username.isBlank()) {
                    return null;
                }
                return new PasswordAuthentication(settings.username, settings.password);
            }
        });

        MimeMessage message = new MimeMessage(session);
        InternetAddress fromAddress = new InternetAddress(settings.fromAddress);
        if (settings.fromName != null && !settings.fromName.isBlank()) {
            try {
                fromAddress.setPersonal(settings.fromName);
            } catch (java.io.UnsupportedEncodingException ignored) {
                // UTF-8 is expected to be available; fall back to the raw address if it is not.
            }
        }
        message.setFrom(fromAddress);
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(user.getEmail(), false));
        message.setSubject("Your Fahamni Quiz Certificate - " + safeText(quiz.getTitre(), "Quiz Achievement"));
        message.setContent(buildCertificateMessage(user, quiz, result));

        Transport.send(message);
    }

    private Multipart buildCertificateMessage(User user, Quiz quiz, QuizResult result) throws MessagingException {
        Multipart multipart = new MimeMultipart();

        BodyPart htmlBodyPart = new MimeBodyPart();
        htmlBodyPart.setContent(buildCertificateHtml(user, quiz, result), "text/html; charset=UTF-8");
        multipart.addBodyPart(htmlBodyPart);

        BodyPart pdfAttachmentPart = new MimeBodyPart();
        byte[] pdfBytes = buildCertificatePdf(user, quiz, result);
        pdfAttachmentPart.setDataHandler(new jakarta.activation.DataHandler(
                new ByteArrayDataSource(pdfBytes, "application/pdf")
        ));
        pdfAttachmentPart.setFileName(buildCertificateFileName(user, quiz));
        multipart.addBodyPart(pdfAttachmentPart);

        return multipart;
    }

    private String buildCertificateHtml(User user, Quiz quiz, QuizResult result) {
        String learnerName = escapeHtml(safeText(user.getFullName(), "Learner"));
        String quizTitle = escapeHtml(safeText(quiz.getTitre(), "Quiz Achievement"));
        String score = result.getScore() != null && result.getTotalQuestions() != null
                ? result.getScore() + "/" + result.getTotalQuestions()
                : "Completed";
        String percentage = result.getPercentage() != null ? Math.round(result.getPercentage()) + "%" : "Passed";
        String certificateDate = result.getCompletedAt() != null
                ? CERTIFICATE_DATE_FORMATTER.format(result.getCompletedAt())
                : CERTIFICATE_DATE_FORMATTER.format(java.time.Instant.now());

        return """
                <html>
                <body style="margin:0;padding:24px;background:#f4f6fb;font-family:'Segoe UI',Arial,sans-serif;color:#10203a;">
                    <div style="max-width:760px;margin:0 auto;background:#ffffff;border-radius:20px;overflow:hidden;box-shadow:0 24px 60px rgba(16,32,58,0.12);">
                        <div style="padding:28px 36px;background:linear-gradient(135deg,#1f2a44,#5b6fd8);color:#ffffff;">
                            <div style="font-size:13px;letter-spacing:2px;text-transform:uppercase;opacity:0.78;">Fahamni Certification</div>
                            <h1 style="margin:12px 0 8px;font-size:34px;line-height:1.15;">You passed your quiz.</h1>
                            <p style="margin:0;font-size:16px;opacity:0.9;">A polished little receipt for the brainpower you just spent.</p>
                        </div>
                        <div style="padding:32px 36px 18px;">
                            <p style="margin:0 0 18px;font-size:16px;">Hi %s,</p>
                            <p style="margin:0 0 24px;font-size:16px;line-height:1.7;">
                                Congratulations. You successfully passed <strong>%s</strong> on %s, and we generated your certificate below.
                            </p>
                            <div style="border:1px solid #d6def5;border-radius:18px;padding:28px;background:linear-gradient(180deg,#fbfcff,#f4f7ff);">
                                <div style="text-align:center;border:2px solid #8ea0ff;border-radius:14px;padding:28px 20px;background:#ffffff;">
                                    <div style="font-size:12px;letter-spacing:3px;text-transform:uppercase;color:#5b6fd8;margin-bottom:12px;">Certificate of Achievement</div>
                                    <div style="font-size:18px;color:#47536c;margin-bottom:10px;">This certifies that</div>
                                    <div style="font-size:34px;font-weight:700;color:#1b2b52;margin-bottom:14px;">%s</div>
                                    <div style="font-size:17px;color:#47536c;line-height:1.7;max-width:520px;margin:0 auto 20px;">
                                        has successfully passed the Fahamni quiz <strong>%s</strong> with a score of <strong>%s</strong> and a final result of <strong>%s</strong>.
                                    </div>
                                    <div style="display:inline-block;padding:10px 18px;border-radius:999px;background:#eef2ff;color:#3247aa;font-weight:600;">
                                        Issued on %s
                                    </div>
                                </div>
                            </div>
                            <p style="margin:24px 0 0;font-size:14px;line-height:1.7;color:#5e6984;">
                                Keep this email as your quiz certificate. More wins look good on you.
                            </p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(learnerName, quizTitle, certificateDate, learnerName, quizTitle, escapeHtml(score), escapeHtml(percentage), certificateDate);
    }

    private byte[] buildCertificatePdf(User user, Quiz quiz, QuizResult result) throws MessagingException {
        String learnerName = safeText(user.getFullName(), "Learner");
        String quizTitle = safeText(quiz.getTitre(), "Quiz Achievement");
        String score = result.getScore() != null && result.getTotalQuestions() != null
                ? result.getScore() + "/" + result.getTotalQuestions()
                : "Completed";
        String percentage = result.getPercentage() != null ? Math.round(result.getPercentage()) + "%" : "Passed";
        String certificateDate = result.getCompletedAt() != null
                ? CERTIFICATE_DATE_FORMATTER.format(result.getCompletedAt())
                : CERTIFICATE_DATE_FORMATTER.format(java.time.Instant.now());

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            org.apache.pdfbox.pdmodel.font.PDFont helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            org.apache.pdfbox.pdmodel.font.PDFont helveticaBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            org.apache.pdfbox.pdmodel.font.PDFont helveticaOblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float pageWidth = page.getMediaBox().getWidth();
                float pageHeight = page.getMediaBox().getHeight();
                float margin = 48f;

                setFillColor(content, 244, 246, 251);
                content.addRect(0, 0, pageWidth, pageHeight);
                content.fill();

                setFillColor(content, 31, 42, 68);
                content.addRect(margin, pageHeight - 145, pageWidth - (margin * 2), 92);
                content.fill();

                writeCenteredText(content, pageWidth, pageHeight - 88, helveticaBold, 28,
                        "Fahamni Certificate of Achievement", 255, 255, 255);
                writeCenteredText(content, pageWidth, pageHeight - 122, helvetica, 12,
                        "Recognizing your success in the quiz workspace", 230, 236, 255);

                writeCenteredText(content, pageWidth, pageHeight - 205, helvetica, 18,
                        "This certifies that", 71, 83, 108);
                writeCenteredText(content, pageWidth, pageHeight - 250, helveticaBold, 30,
                        learnerName, 27, 43, 82);
                writeCenteredText(content, pageWidth, pageHeight - 296, helvetica, 17,
                        "has successfully passed", 71, 83, 108);
                writeCenteredText(content, pageWidth, pageHeight - 334, helveticaBold, 22,
                        quizTitle, 50, 71, 170);

                writeCenteredText(content, pageWidth, pageHeight - 394, helvetica, 15,
                        "Score: " + score + "   |   Final result: " + percentage, 46, 58, 86);
                writeCenteredText(content, pageWidth, pageHeight - 432, helvetica, 15,
                        "Issued on " + certificateDate, 46, 58, 86);

                setStrokeColor(content, 142, 160, 255);
                content.setLineWidth(1.5f);
                content.addRect(margin + 18, 82, pageWidth - ((margin + 18) * 2), pageHeight - 188);
                content.stroke();

                writeText(content, margin + 28, 72, helveticaOblique, 11,
                        "Keep learning. Keep passing. Keep receipts.", 94, 105, 132);
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new MessagingException("Unable to generate quiz certificate PDF.", exception);
        }
    }

    private String buildCertificateFileName(User user, Quiz quiz) {
        String learnerToken = safeToken(user != null ? user.getFullName() : null, "learner");
        String quizToken = safeToken(quiz != null ? quiz.getTitre() : null, "quiz");
        return "Fahamni-Certificate-" + learnerToken + "-" + quizToken + ".pdf";
    }

    private void writeCenteredText(
            PDPageContentStream content,
            float pageWidth,
            float baselineY,
            org.apache.pdfbox.pdmodel.font.PDFont font,
            float fontSize,
            String text,
            int red,
            int green,
            int blue
    ) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000f * fontSize;
        float startX = Math.max(36f, (pageWidth - textWidth) / 2f);
        writeText(content, startX, baselineY, font, fontSize, text, red, green, blue);
    }

    private void writeText(
            PDPageContentStream content,
            float x,
            float y,
            org.apache.pdfbox.pdmodel.font.PDFont font,
            float fontSize,
            String text,
            int red,
            int green,
            int blue
    ) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        setFillColor(content, red, green, blue);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private void setFillColor(PDPageContentStream content, int red, int green, int blue) throws IOException {
        content.setNonStrokingColor(normalizeColor(red), normalizeColor(green), normalizeColor(blue));
    }

    private void setStrokeColor(PDPageContentStream content, int red, int green, int blue) throws IOException {
        content.setStrokingColor(normalizeColor(red), normalizeColor(green), normalizeColor(blue));
    }

    private float normalizeColor(int value) {
        return Math.max(0f, Math.min(1f, value / 255f));
    }

    private String safeToken(String value, String fallback) {
        String normalized = safeText(value, fallback)
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static final class MailSettings {
        private final String host;
        private final String port;
        private final String username;
        private final String password;
        private final String fromAddress;
        private final String fromName;
        private final boolean auth;
        private final boolean startTls;
        private final boolean ssl;

        private MailSettings(
                String host,
                String port,
                String username,
                String password,
                String fromAddress,
                String fromName,
                boolean auth,
                boolean startTls,
                boolean ssl
        ) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
            this.fromAddress = fromAddress;
            this.fromName = fromName;
            this.auth = auth;
            this.startTls = startTls;
            this.ssl = ssl;
        }

        private static MailSettings fromEnvironment() {
            String mailerDsn = readEnv("MAILER_DSN");
            if (mailerDsn != null && !mailerDsn.isBlank()) {
                MailSettings fromDsn = fromMailerDsn(mailerDsn);
                if (fromDsn != null) {
                    return fromDsn;
                }
            }

            String username = readEnv("FAHAMNI_SMTP_USERNAME");
            String fromAddress = readEnv("FAHAMNI_SMTP_FROM");
            return new MailSettings(
                    readEnv("FAHAMNI_SMTP_HOST"),
                    defaultIfBlank(readEnv("FAHAMNI_SMTP_PORT"), "587"),
                    username,
                    readEnv("FAHAMNI_SMTP_PASSWORD"),
                    defaultIfBlank(fromAddress, username),
                    defaultIfBlank(readEnv("FAHAMNI_SMTP_FROM_NAME"), "Fahamni Quiz Team"),
                    Boolean.parseBoolean(defaultIfBlank(readEnv("FAHAMNI_SMTP_AUTH"), "true")),
                    Boolean.parseBoolean(defaultIfBlank(readEnv("FAHAMNI_SMTP_STARTTLS"), "true")),
                    Boolean.parseBoolean(defaultIfBlank(readEnv("FAHAMNI_SMTP_SSL"), "false"))
            );
        }

        private static MailSettings fromMailerDsn(String dsn) {
            try {
                String normalizedDsn = dsn == null ? "" : dsn.trim();
                int schemeSeparator = normalizedDsn.indexOf("://");
                if (schemeSeparator <= 0) {
                    return null;
                }

                String scheme = normalizedDsn.substring(0, schemeSeparator).toLowerCase();
                String remainder = normalizedDsn.substring(schemeSeparator + 3);
                String authority = extractAuthority(remainder);
                String userInfo = extractUserInfo(authority);
                String username = null;
                String password = null;

                if (userInfo != null && !userInfo.isBlank()) {
                    String[] parts = userInfo.split(":", 2);
                    username = decode(parts[0]);
                    if (parts.length > 1) {
                        password = decode(parts[1]);
                    }
                }

                HostPort hostPort = extractHostPort(authority);
                String host = hostPort.host();
                String port = hostPort.port();
                boolean ssl = false;
                boolean startTls = true;

                if ("gmail".equals(scheme)) {
                    host = "smtp.gmail.com";
                    port = defaultIfBlank(port, "587");
                    startTls = true;
                } else if ("smtps".equals(scheme)) {
                    ssl = true;
                    startTls = false;
                    port = defaultIfBlank(port, "465");
                } else if ("smtp".equals(scheme)) {
                    port = defaultIfBlank(port, "587");
                } else {
                    return null;
                }

                String fromAddress = defaultIfBlank(readEnv("FAHAMNI_SMTP_FROM"), username);
                return new MailSettings(
                        host,
                        port,
                        username,
                        password,
                        fromAddress,
                        defaultIfBlank(readEnv("FAHAMNI_SMTP_FROM_NAME"), "Fahamni Quiz Team"),
                        true,
                        startTls,
                        ssl
                );
            } catch (IllegalArgumentException exception) {
                System.err.println("Invalid MAILER_DSN format: " + exception.getMessage());
                return null;
            }
        }

        private boolean isConfigured() {
            return host != null && !host.isBlank()
                    && fromAddress != null && !fromAddress.isBlank();
        }

        private Properties toProperties() {
            Properties properties = new Properties();
            properties.put("mail.smtp.host", host);
            properties.put("mail.smtp.port", port);
            properties.put("mail.smtp.auth", String.valueOf(auth));
            properties.put("mail.smtp.starttls.enable", String.valueOf(startTls));
            if (ssl) {
                properties.put("mail.smtp.ssl.enable", "true");
            }
            return properties;
        }

        private static String readEnv(String key) {
            String value = EnvConfig.get(key);
            return value == null ? null : value.trim();
        }

        private static String defaultIfBlank(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }

        private static String extractAuthority(String remainder) {
            int cutoff = remainder.length();

            int slashIndex = remainder.indexOf('/');
            if (slashIndex >= 0) {
                cutoff = Math.min(cutoff, slashIndex);
            }

            int queryIndex = remainder.indexOf('?');
            if (queryIndex >= 0) {
                cutoff = Math.min(cutoff, queryIndex);
            }

            int fragmentIndex = remainder.indexOf('#');
            if (fragmentIndex >= 0) {
                cutoff = Math.min(cutoff, fragmentIndex);
            }

            return remainder.substring(0, cutoff);
        }

        private static String extractUserInfo(String authority) {
            int atIndex = authority.lastIndexOf('@');
            if (atIndex <= 0) {
                return null;
            }
            return authority.substring(0, atIndex);
        }

        private static HostPort extractHostPort(String authority) {
            int atIndex = authority.lastIndexOf('@');
            String hostPort = atIndex >= 0 ? authority.substring(atIndex + 1) : authority;
            int colonIndex = hostPort.lastIndexOf(':');

            if (colonIndex > 0 && colonIndex < hostPort.length() - 1) {
                return new HostPort(hostPort.substring(0, colonIndex), hostPort.substring(colonIndex + 1));
            }

            return new HostPort(hostPort, null);
        }

        private record HostPort(String host, String port) {
        }
    }

    public record MailDispatchResult(String recipient, String message, boolean success, boolean skipped) {
        private static MailDispatchResult success(String recipient, String message) {
            return new MailDispatchResult(recipient, message, true, false);
        }

        private static MailDispatchResult failure(String recipient, String message) {
            return new MailDispatchResult(recipient, message, false, false);
        }

        private static MailDispatchResult skipped(String message) {
            return new MailDispatchResult(null, message, false, true);
        }
    }
}
