package tn.esprit.fahamni.utils;

import java.net.URI;
import java.time.Duration;

public record AiApiConfig(
    String apiKey,
    String model,
    URI baseUri,
    Duration timeout
) {
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public AiApiConfig {
        apiKey = normalize(apiKey);
        model = normalize(model) == null ? DEFAULT_MODEL : normalize(model);
        baseUri = baseUri == null ? URI.create(DEFAULT_BASE_URL) : baseUri;
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
    }

    public static AiApiConfig fromEnv() {
        return new AiApiConfig(
            System.getenv("GEMINI_API_KEY"),
            System.getenv("GEMINI_MODEL"),
            resolveBaseUri(System.getenv("GEMINI_BASE_URL")),
            resolveTimeout(System.getenv("GEMINI_TIMEOUT_SECONDS"))
        );
    }

    public boolean isConfigured() {
        return apiKey != null;
    }

    public void validate() {
        if (apiKey == null) {
            throw new IllegalStateException(
                "GEMINI_API_KEY absente. Configurez une cle API Gemini valide avant d'utiliser la conception AI."
            );
        }

        if (looksLikeOpenAiApiKey(apiKey)) {
            throw new IllegalStateException(
                "La valeur de GEMINI_API_KEY ressemble a une cle OpenAI et non a une cle Gemini. "
                    + "Verifiez que vous utilisez bien une cle issue de Google AI Studio."
            );
        }
    }

    public URI generateContentUri() {
        String normalizedBase = baseUri.toString().endsWith("/")
            ? baseUri.toString().substring(0, baseUri.toString().length() - 1)
            : baseUri.toString();
        return URI.create(normalizedBase + "/models/" + model + ":generateContent");
    }

    private static URI resolveBaseUri(String rawBaseUrl) {
        String normalizedBaseUrl = normalize(rawBaseUrl);
        return URI.create(normalizedBaseUrl == null ? DEFAULT_BASE_URL : normalizedBaseUrl);
    }

    private static Duration resolveTimeout(String rawTimeoutSeconds) {
        String normalizedTimeoutSeconds = normalize(rawTimeoutSeconds);
        if (normalizedTimeoutSeconds == null) {
            return DEFAULT_TIMEOUT;
        }

        try {
            int timeoutSeconds = Integer.parseInt(normalizedTimeoutSeconds);
            return timeoutSeconds <= 0 ? DEFAULT_TIMEOUT : Duration.ofSeconds(timeoutSeconds);
        } catch (NumberFormatException exception) {
            return DEFAULT_TIMEOUT;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.length() >= 2) {
            char first = normalized.charAt(0);
            char last = normalized.charAt(normalized.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
            }
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean looksLikeOpenAiApiKey(String value) {
        return value != null && value.startsWith("sk-");
    }
}
