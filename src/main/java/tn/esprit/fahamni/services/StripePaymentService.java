package tn.esprit.fahamni.services;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import tn.esprit.fahamni.utils.LocalConfig;

public class StripePaymentService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final String DEFAULT_API_BASE_URL = "https://api.stripe.com/";
    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_LOCALE = "fr";
    private static final double DEFAULT_TND_EXCHANGE_RATE = 1.0;
    private static final int DEFAULT_MINOR_UNIT_FACTOR = 100;
    private static final String DEFAULT_SUCCESS_URL =
        "https://example.com/fahamni/stripe/success?session_id={CHECKOUT_SESSION_ID}";
    private static final String DEFAULT_CANCEL_URL = "https://example.com/fahamni/stripe/cancel";
    private static final Pattern STRING_FIELD_PATTERN = Pattern.compile(
        "\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
    );

    public boolean isConfigured() {
        return normalizeConfig("STRIPE_SECRET_KEY") != null;
    }

    public PaymentInitialization initializePayment(CreatePaymentRequest request) {
        if (!isConfigured()) {
            return PaymentInitialization.failure("Stripe n'est pas configure. Ajoutez STRIPE_SECRET_KEY.");
        }
        if (request == null || request.amountTnd() <= 0.0) {
            return PaymentInitialization.failure("Le montant Stripe doit etre superieur a zero.");
        }

        ResolvedAmount resolvedAmount = resolveAmount(request.amountTnd());
        String endpoint = resolveBaseUrl() + "v1/checkout/sessions";
        String payload = buildCreateCheckoutPayload(request, resolvedAmount);

        try {
            HttpResponse<String> response = sendRequest(endpoint, "POST", payload);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return PaymentInitialization.failure(resolveHttpFailureMessage(response.statusCode(), response.body()));
            }

            String sessionId = extractStringField(response.body(), "id");
            String checkoutUrl = extractStringField(response.body(), "url");
            String sessionStatus = extractStringField(response.body(), "status");
            String paymentStatus = extractStringField(response.body(), "payment_status");
            if (sessionId == null || checkoutUrl == null) {
                return PaymentInitialization.failure("Stripe a retourne une reponse incomplete.");
            }

            return PaymentInitialization.success(
                "Lien de paiement Stripe Checkout genere avec succes.",
                sessionId,
                checkoutUrl,
                sessionStatus,
                paymentStatus,
                resolvedAmount.currencyCode(),
                String.valueOf(resolvedAmount.amountMinorUnits()),
                response.body()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PaymentInitialization.failure("La connexion a Stripe a ete interrompue.");
        } catch (IOException exception) {
            return PaymentInitialization.failure(resolveTransportFailureMessage(exception));
        }
    }

    public PaymentSessionDetails fetchCheckoutSession(String sessionId) {
        if (!isConfigured()) {
            return PaymentSessionDetails.failure("Stripe n'est pas configure. Ajoutez STRIPE_SECRET_KEY.");
        }

        String normalizedSessionId = normalizeValue(sessionId);
        if (normalizedSessionId == null) {
            return PaymentSessionDetails.failure("Aucune session Stripe n'est disponible pour cette reservation.");
        }

        String endpoint = resolveBaseUrl() + "v1/checkout/sessions/" + urlEncodePath(normalizedSessionId);
        try {
            HttpResponse<String> response = sendRequest(endpoint, "GET", null);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return PaymentSessionDetails.failure(resolveHttpFailureMessage(response.statusCode(), response.body()));
            }

            String checkoutUrl = extractStringField(response.body(), "url");
            String sessionStatus = extractStringField(response.body(), "status");
            String paymentStatus = extractStringField(response.body(), "payment_status");

            return PaymentSessionDetails.success(
                "Statut Stripe recupere avec succes.",
                normalizedSessionId,
                checkoutUrl,
                sessionStatus,
                paymentStatus,
                normalizeSessionStatus(sessionStatus, paymentStatus),
                response.body()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PaymentSessionDetails.failure("La verification Stripe a ete interrompue.");
        } catch (IOException exception) {
            return PaymentSessionDetails.failure(resolveTransportFailureMessage(exception));
        }
    }

    private HttpResponse<String> sendRequest(String endpoint, String method, String payload)
        throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + normalizeConfig("STRIPE_SECRET_KEY"));

        if ("POST".equalsIgnoreCase(method)) {
            builder.header("Content-Type", "application/x-www-form-urlencoded");
            builder.POST(HttpRequest.BodyPublishers.ofString(payload == null ? "" : payload, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }

        return HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build()
            .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String buildCreateCheckoutPayload(CreatePaymentRequest request, ResolvedAmount resolvedAmount) {
        List<String> fields = new ArrayList<>();
        addFormField(fields, "mode", "payment");
        addFormField(fields, "success_url", resolveSuccessUrl());
        addFormField(fields, "cancel_url", resolveCancelUrl());
        addFormField(fields, "billing_address_collection", "required");
        addFormField(fields, "client_reference_id", request.orderId());
        addFormField(fields, "customer_creation", "always");
        addFormField(fields, "invoice_creation[enabled]", "true");
        addFormField(fields, "locale", resolveLocale());
        addFormField(fields, "name_collection[individual][enabled]", "true");
        addFormField(fields, "payment_method_types[0]", "card");
        addFormField(fields, "phone_number_collection[enabled]", "true");
        addFormField(fields, "line_items[0][quantity]", "1");
        addFormField(fields, "line_items[0][price_data][currency]", resolvedAmount.currencyCode().toLowerCase(Locale.ROOT));
        addFormField(fields, "line_items[0][price_data][unit_amount]", String.valueOf(resolvedAmount.amountMinorUnits()));
        addFormField(fields, "line_items[0][price_data][product_data][name]", request.description());
        addFormField(fields, "custom_text[submit][message]", "Paiement securise pour confirmer votre reservation Fahamni.");
        addFormField(fields, "custom_text[after_submit][message]", "Un recu sera envoye par email apres confirmation du paiement.");
        addFormField(fields, "metadata[order_id]", request.orderId());
        addFormField(fields, "metadata[source]", "fahamni-desktop");
        addFormField(fields, "submit_type", "pay");

        String normalizedEmail = normalizeValue(request.email());
        if (normalizedEmail != null) {
            addFormField(fields, "customer_email", normalizedEmail);
        }

        return String.join("&", fields);
    }

    private void addFormField(List<String> fields, String key, String value) {
        String normalizedKey = normalizeValue(key);
        String normalizedValue = normalizeValue(value);
        if (normalizedKey == null || normalizedValue == null) {
            return;
        }
        fields.add(urlEncode(normalizedKey) + "=" + urlEncode(normalizedValue));
    }

    private ResolvedAmount resolveAmount(double amountTnd) {
        String currencyCode = resolveCurrencyCode();
        double exchangeRate = resolveExchangeRate();
        int minorUnitFactor = resolveMinorUnitFactor();
        long amountMinorUnits = Math.max(1L, Math.round(amountTnd * exchangeRate * minorUnitFactor));
        return new ResolvedAmount(currencyCode, amountMinorUnits);
    }

    private String resolveCurrencyCode() {
        String currencyCode = normalizeConfig("STRIPE_CURRENCY");
        return currencyCode == null ? DEFAULT_CURRENCY : currencyCode.toUpperCase(Locale.ROOT);
    }

    private String resolveLocale() {
        String configuredLocale = normalizeConfig("STRIPE_LOCALE");
        return configuredLocale == null ? DEFAULT_LOCALE : configuredLocale;
    }

    private double resolveExchangeRate() {
        String configuredRate = normalizeConfig("STRIPE_TND_EXCHANGE_RATE");
        if (configuredRate == null) {
            return DEFAULT_TND_EXCHANGE_RATE;
        }
        try {
            double parsedRate = Double.parseDouble(configuredRate.replace(',', '.'));
            return parsedRate > 0.0 ? parsedRate : DEFAULT_TND_EXCHANGE_RATE;
        } catch (NumberFormatException exception) {
            return DEFAULT_TND_EXCHANGE_RATE;
        }
    }

    private int resolveMinorUnitFactor() {
        String configuredFactor = normalizeConfig("STRIPE_MINOR_UNIT_FACTOR");
        if (configuredFactor == null) {
            return DEFAULT_MINOR_UNIT_FACTOR;
        }
        try {
            int parsedFactor = Integer.parseInt(configuredFactor);
            return parsedFactor > 0 ? parsedFactor : DEFAULT_MINOR_UNIT_FACTOR;
        } catch (NumberFormatException exception) {
            return DEFAULT_MINOR_UNIT_FACTOR;
        }
    }

    private String resolveSuccessUrl() {
        String successUrl = normalizeConfig("STRIPE_SUCCESS_URL");
        return successUrl != null ? successUrl : DEFAULT_SUCCESS_URL;
    }

    private String resolveCancelUrl() {
        String cancelUrl = normalizeConfig("STRIPE_CANCEL_URL");
        return cancelUrl != null ? cancelUrl : DEFAULT_CANCEL_URL;
    }

    private String normalizeSessionStatus(String sessionStatus, String paymentStatus) {
        String normalizedPaymentStatus = normalizeValue(paymentStatus);
        if (normalizedPaymentStatus != null) {
            String loweredPaymentStatus = normalizedPaymentStatus.toLowerCase(Locale.ROOT);
            if ("paid".equals(loweredPaymentStatus) || "no_payment_required".equals(loweredPaymentStatus)) {
                return ReservationService.PAYMENT_STATUS_COMPLETED;
            }
        }

        String normalizedSessionStatus = normalizeValue(sessionStatus);
        if (normalizedSessionStatus != null) {
            String loweredSessionStatus = normalizedSessionStatus.toLowerCase(Locale.ROOT);
            if ("expired".equals(loweredSessionStatus)) {
                return ReservationService.PAYMENT_STATUS_EXPIRED;
            }
        }

        return ReservationService.PAYMENT_STATUS_PENDING;
    }

    private String resolveBaseUrl() {
        String configuredBaseUrl = normalizeConfig("STRIPE_API_BASE_URL");
        String baseUrl = configuredBaseUrl != null ? configuredBaseUrl : DEFAULT_API_BASE_URL;
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private String extractStringField(String json, String fieldName) {
        if (json == null || json.isBlank() || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile(STRING_FIELD_PATTERN.pattern().formatted(Pattern.quote(fieldName)))
            .matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return normalizeValue(unescapeJson(matcher.group(1)));
    }

    private String resolveHttpFailureMessage(int statusCode, String body) {
        String providerMessage = extractStringField(body, "message");
        if (providerMessage != null) {
            return "Stripe a retourne HTTP " + statusCode + ": " + providerMessage;
        }
        return "Stripe a retourne HTTP " + statusCode + ".";
    }

    private String resolveTransportFailureMessage(IOException exception) {
        Throwable rootCause = rootCause(exception);
        if (rootCause instanceof HttpTimeoutException) {
            return "Stripe met trop de temps a repondre.";
        }
        if (rootCause instanceof UnknownHostException || rootCause instanceof ConnectException) {
            return "Connexion a Stripe impossible pour le moment.";
        }
        if (rootCause instanceof SSLException) {
            return "La connexion securisee vers Stripe a echoue.";
        }
        return "Connexion a Stripe impossible pour le moment.";
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

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String urlEncodePath(String value) {
        return value.replace("%", "%25").replace("/", "%2F").replace(" ", "%20");
    }

    public record CreatePaymentRequest(
        String orderId,
        double amountTnd,
        String description,
        String email
    ) {
    }

    public record PaymentInitialization(
        boolean success,
        String message,
        String sessionId,
        String checkoutUrl,
        String sessionStatus,
        String paymentStatus,
        String externalCurrency,
        String externalAmount,
        String providerPayload
    ) {
        public static PaymentInitialization success(
            String message,
            String sessionId,
            String checkoutUrl,
            String sessionStatus,
            String paymentStatus,
            String externalCurrency,
            String externalAmount,
            String providerPayload
        ) {
            return new PaymentInitialization(
                true,
                message,
                sessionId,
                checkoutUrl,
                sessionStatus,
                paymentStatus,
                externalCurrency,
                externalAmount,
                providerPayload
            );
        }

        public static PaymentInitialization failure(String message) {
            return new PaymentInitialization(false, message, null, null, null, null, null, null, null);
        }
    }

    public record PaymentSessionDetails(
        boolean success,
        String message,
        String sessionId,
        String checkoutUrl,
        String sessionStatus,
        String paymentStatus,
        String normalizedStatus,
        String providerPayload
    ) {
        public static PaymentSessionDetails success(
            String message,
            String sessionId,
            String checkoutUrl,
            String sessionStatus,
            String paymentStatus,
            String normalizedStatus,
            String providerPayload
        ) {
            return new PaymentSessionDetails(
                true,
                message,
                sessionId,
                checkoutUrl,
                sessionStatus,
                paymentStatus,
                normalizedStatus,
                providerPayload
            );
        }

        public static PaymentSessionDetails failure(String message) {
            return new PaymentSessionDetails(false, message, null, null, null, null, null, null);
        }
    }

    private record ResolvedAmount(String currencyCode, long amountMinorUnits) {
    }
}
