package tn.esprit.fahamni.services;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import tn.esprit.fahamni.utils.LocalConfig;

public class KonnectPaymentService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final String DEFAULT_SANDBOX_BASE_URL = "https://api.sandbox.konnect.network/api/v2/";
    private static final Pattern STRING_FIELD_PATTERN_TEMPLATE = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern NUMBER_FIELD_PATTERN_TEMPLATE = Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");

    public boolean isConfigured() {
        return normalizeConfig("KONNECT_API_KEY") != null && normalizeConfig("KONNECT_WALLET_ID") != null;
    }

    public PaymentInitialization initializePayment(CreatePaymentRequest request) {
        if (!isConfigured()) {
            return PaymentInitialization.failure(
                "Konnect Sandbox n'est pas configure. Ajoutez KONNECT_API_KEY et KONNECT_WALLET_ID."
            );
        }
        if (request == null || request.amountMillimes() <= 0) {
            return PaymentInitialization.failure("Le montant Konnect doit etre superieur a zero.");
        }

        String endpoint = resolveBaseUrl() + "payments/init-payment";
        String payload = buildCreatePaymentPayload(request);
        try {
            HttpResponse<String> response = sendJsonRequest(
                endpoint,
                "POST",
                payload
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return PaymentInitialization.failure(resolveHttpFailureMessage(response.statusCode(), response.body()));
            }

            String payUrl = extractStringField(response.body(), "payUrl");
            String paymentRef = extractStringField(response.body(), "paymentRef");
            if (payUrl == null || paymentRef == null) {
                return PaymentInitialization.failure("Konnect Sandbox a retourne une reponse incomplete.");
            }
            return PaymentInitialization.success(
                "Lien de paiement Konnect genere avec succes.",
                paymentRef,
                payUrl,
                response.body()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PaymentInitialization.failure("La connexion a Konnect a ete interrompue.");
        } catch (IOException exception) {
            return PaymentInitialization.failure(resolveTransportFailureMessage(exception));
        }
    }

    public PaymentStatusCheck fetchPaymentDetails(String paymentRef) {
        if (!isConfigured()) {
            return PaymentStatusCheck.failure(
                "Konnect Sandbox n'est pas configure. Ajoutez KONNECT_API_KEY et KONNECT_WALLET_ID."
            );
        }

        String normalizedPaymentRef = normalizeValue(paymentRef);
        if (normalizedPaymentRef == null) {
            return PaymentStatusCheck.failure("Aucune reference Konnect n'est disponible pour cette reservation.");
        }

        String endpoint = resolveBaseUrl() + "payments/" + normalizedPaymentRef;
        try {
            HttpResponse<String> response = sendJsonRequest(endpoint, "GET", null);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return PaymentStatusCheck.failure(resolveHttpFailureMessage(response.statusCode(), response.body()));
            }

            String paymentJson = extractPaymentObject(response.body());
            if (paymentJson == null) {
                return PaymentStatusCheck.failure("La verification Konnect a retourne une reponse incomplete.");
            }

            String providerStatus = extractStringField(paymentJson, "status");
            String payUrl = extractStringField(paymentJson, "link");
            Integer amount = extractIntegerField(paymentJson, "amount");
            Integer amountDue = extractIntegerField(paymentJson, "amountDue");
            Integer reachedAmount = extractIntegerField(paymentJson, "reachedAmount");
            String expirationDate = extractStringField(paymentJson, "expirationDate");
            String transactionStatus = extractFirstTransactionStatus(paymentJson);
            String normalizedStatus = normalizePaymentStatus(providerStatus, transactionStatus, expirationDate);

            return PaymentStatusCheck.success(
                "Statut Konnect recupere avec succes.",
                normalizedPaymentRef,
                payUrl,
                providerStatus,
                transactionStatus,
                normalizedStatus,
                amount,
                amountDue,
                reachedAmount,
                response.body()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PaymentStatusCheck.failure("La verification Konnect a ete interrompue.");
        } catch (IOException exception) {
            return PaymentStatusCheck.failure(resolveTransportFailureMessage(exception));
        }
    }

    private HttpResponse<String> sendJsonRequest(String endpoint, String method, String payload)
        throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .header("x-api-key", normalizeConfig("KONNECT_API_KEY"));

        if ("POST".equalsIgnoreCase(method)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(payload == null ? "" : payload, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }

        return HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build()
            .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String buildCreatePaymentPayload(CreatePaymentRequest request) {
        return "{"
            + "\"receiverWalletId\":" + quote(normalizeConfig("KONNECT_WALLET_ID")) + ","
            + "\"token\":\"TND\","
            + "\"amount\":" + request.amountMillimes() + ","
            + "\"type\":\"immediate\","
            + "\"description\":" + quote(request.description()) + ","
            + "\"acceptedPaymentMethods\":[\"bank_card\",\"e-DINAR\"],"
            + "\"lifespan\":15,"
            + "\"checkoutForm\":true,"
            + "\"addPaymentFeesToAmount\":false,"
            + optionalStringField("firstName", request.firstName()) + ","
            + optionalStringField("lastName", request.lastName()) + ","
            + optionalStringField("email", request.email()) + ","
            + optionalStringField("orderId", request.orderId()) + ","
            + "\"theme\":\"light\""
            + "}";
    }

    private String optionalStringField(String fieldName, String value) {
        String normalized = normalizeValue(value);
        return normalized == null
            ? "\"" + fieldName + "\":null"
            : "\"" + fieldName + "\":" + quote(normalized);
    }

    private String extractPaymentObject(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        int paymentKeyIndex = responseBody.indexOf("\"payment\"");
        if (paymentKeyIndex < 0) {
            return null;
        }

        int objectStart = responseBody.indexOf('{', paymentKeyIndex);
        if (objectStart < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = objectStart; index < responseBody.length(); index++) {
            char current = responseBody.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return responseBody.substring(objectStart, index + 1);
                }
            }
        }
        return null;
    }

    private String extractFirstTransactionStatus(String paymentJson) {
        if (paymentJson == null || paymentJson.isBlank()) {
            return null;
        }

        int transactionsIndex = paymentJson.indexOf("\"transactions\"");
        if (transactionsIndex < 0) {
            return null;
        }
        String transactionSection = paymentJson.substring(transactionsIndex);
        return extractStringField(transactionSection, "status");
    }

    private String extractStringField(String json, String fieldName) {
        if (json == null || json.isBlank() || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile(STRING_FIELD_PATTERN_TEMPLATE.pattern().formatted(Pattern.quote(fieldName)))
            .matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return normalizeValue(unescapeJson(matcher.group(1)));
    }

    private Integer extractIntegerField(String json, String fieldName) {
        if (json == null || json.isBlank() || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile(NUMBER_FIELD_PATTERN_TEMPLATE.pattern().formatted(Pattern.quote(fieldName)))
            .matcher(json);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizePaymentStatus(String paymentStatus, String transactionStatus, String expirationDate) {
        String normalizedPaymentStatus = normalizeValue(paymentStatus);
        if (normalizedPaymentStatus != null && "completed".equalsIgnoreCase(normalizedPaymentStatus)) {
            return ReservationService.PAYMENT_STATUS_COMPLETED;
        }

        String normalizedTransactionStatus = normalizeValue(transactionStatus);
        if (normalizedTransactionStatus != null) {
            String lowered = normalizedTransactionStatus.toLowerCase(Locale.ROOT);
            if ("success".equals(lowered)) {
                return ReservationService.PAYMENT_STATUS_COMPLETED;
            }
            if (lowered.contains("fail") || lowered.contains("declin") || lowered.contains("error") || lowered.contains("cancel")) {
                return ReservationService.PAYMENT_STATUS_FAILED;
            }
        }

        String normalizedExpirationDate = normalizeValue(expirationDate);
        if (normalizedExpirationDate != null) {
            try {
                if (LocalDate.parse(normalizedExpirationDate).isBefore(LocalDate.now())) {
                    return ReservationService.PAYMENT_STATUS_EXPIRED;
                }
            } catch (RuntimeException ignored) {
                // Keep pending fallback when the provider date format changes.
            }
        }
        return ReservationService.PAYMENT_STATUS_PENDING;
    }

    private String resolveBaseUrl() {
        String configuredBaseUrl = normalizeConfig("KONNECT_API_BASE_URL");
        String baseUrl = configuredBaseUrl != null ? configuredBaseUrl : DEFAULT_SANDBOX_BASE_URL;
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
            + "\"";
    }

    private String unescapeJson(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t");
    }

    private String resolveHttpFailureMessage(int statusCode, String body) {
        String providerMessage = extractStringField(body, "message");
        if (providerMessage != null) {
            return "Konnect Sandbox a retourne HTTP " + statusCode + ": " + providerMessage;
        }
        return "Konnect Sandbox a retourne HTTP " + statusCode + ".";
    }

    private String resolveTransportFailureMessage(IOException exception) {
        Throwable rootCause = rootCause(exception);
        if (rootCause instanceof HttpTimeoutException) {
            return "Konnect Sandbox met trop de temps a repondre.";
        }
        if (rootCause instanceof UnknownHostException || rootCause instanceof ConnectException) {
            return "Connexion a Konnect Sandbox impossible pour le moment.";
        }
        if (rootCause instanceof SSLException) {
            return "La connexion securisee vers Konnect Sandbox a echoue.";
        }
        return "Connexion a Konnect Sandbox impossible pour le moment.";
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String normalizeConfig(String key) {
        return LocalConfig.get(key);
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record CreatePaymentRequest(
        String orderId,
        int amountMillimes,
        String description,
        String email,
        String firstName,
        String lastName
    ) {
    }

    public record PaymentInitialization(
        boolean success,
        String message,
        String paymentRef,
        String paymentUrl,
        String providerPayload
    ) {
        public static PaymentInitialization success(String message, String paymentRef, String paymentUrl, String providerPayload) {
            return new PaymentInitialization(true, message, paymentRef, paymentUrl, providerPayload);
        }

        public static PaymentInitialization failure(String message) {
            return new PaymentInitialization(false, message, null, null, null);
        }
    }

    public record PaymentStatusCheck(
        boolean success,
        String message,
        String paymentRef,
        String paymentUrl,
        String providerStatus,
        String transactionStatus,
        String normalizedStatus,
        Integer amountMillimes,
        Integer amountDueMillimes,
        Integer reachedAmountMillimes,
        String providerPayload
    ) {
        public static PaymentStatusCheck success(
            String message,
            String paymentRef,
            String paymentUrl,
            String providerStatus,
            String transactionStatus,
            String normalizedStatus,
            Integer amountMillimes,
            Integer amountDueMillimes,
            Integer reachedAmountMillimes,
            String providerPayload
        ) {
            return new PaymentStatusCheck(
                true,
                message,
                paymentRef,
                paymentUrl,
                providerStatus,
                transactionStatus,
                normalizedStatus,
                amountMillimes,
                amountDueMillimes,
                reachedAmountMillimes,
                providerPayload
            );
        }

        public static PaymentStatusCheck failure(String message) {
            return new PaymentStatusCheck(false, message, null, null, null, null, null, null, null, null, null);
        }
    }
}
