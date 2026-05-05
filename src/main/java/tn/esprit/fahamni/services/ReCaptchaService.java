package tn.esprit.fahamni.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tn.esprit.fahamni.utils.LocalConfig;
import tn.esprit.fahamni.utils.OperationResult;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ReCaptchaService {

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    private final HttpClient httpClient;

    public ReCaptchaService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    }

    public boolean isConfigured() {
        return !getSiteKey().isBlank() && !getSecretKey().isBlank();
    }

    public String getSiteKey() {
        return getConfig("RECAPTCHA_SITE_KEY");
    }

    public OperationResult verifyToken(String token) {
        if (!isConfigured()) {
            return OperationResult.failure("reCAPTCHA n'est pas configure. Ajoutez RECAPTCHA_SITE_KEY et RECAPTCHA_SECRET_KEY.");
        }

        if (token == null || token.isBlank()) {
            return OperationResult.failure("Veuillez valider le reCAPTCHA avant de creer le compte.");
        }

        String form = "secret=" + encode(getSecretKey()) + "&response=" + encode(token);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(VERIFY_URL))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return OperationResult.failure("Verification reCAPTCHA indisponible. Reessayez.");
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            boolean success = json.has("success") && json.get("success").getAsBoolean();
            if (success) {
                return OperationResult.success("Verification reCAPTCHA validee.");
            }

            return OperationResult.failure("Verification reCAPTCHA refusee. Relancez le controle.");
        } catch (IOException exception) {
            return OperationResult.failure("Impossible de contacter reCAPTCHA. Verifiez votre connexion.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return OperationResult.failure("Verification reCAPTCHA interrompue.");
        } catch (RuntimeException exception) {
            return OperationResult.failure("Reponse reCAPTCHA invalide. Reessayez.");
        }
    }

    private String getSecretKey() {
        return getConfig("RECAPTCHA_SECRET_KEY");
    }

    private String getConfig(String key) {
        String value = LocalConfig.get(key);
        return value == null ? "" : value.trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
