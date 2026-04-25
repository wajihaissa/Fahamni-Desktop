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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import tn.esprit.fahamni.utils.LocalConfig;

public class GeminiMatchingAnalysisService {

    private static final String GEMINI_API_URL_TEMPLATE =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final Pattern FIELD_PATTERN_TEMPLATE = Pattern.compile("\"%s\"\\s*:");

    public boolean isConfigured() {
        return normalizeEnv("GEMINI_API_KEY") != null;
    }

    public MatchingAiAttempt analyzeNeed(MatchingAiContext context) {
        String apiKey = normalizeEnv("GEMINI_API_KEY");
        if (apiKey == null) {
            return MatchingAiAttempt.failure("La cle Gemini n'est pas configuree. Ajoutez GEMINI_API_KEY.");
        }
        if (context == null || context.objectiveText() == null || context.objectiveText().isBlank()) {
            return MatchingAiAttempt.failure("Veuillez decrire votre besoin pour lancer le matching.");
        }

        String model = resolveModel();
        String endpoint = GEMINI_API_URL_TEMPLATE.formatted(model);
        String payload = buildPayload(context);

        try {
            HttpResponse<String> response = sendRequest(endpoint, apiKey, payload);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorDetail = extractApiErrorMessage(response.body());
                System.out.println("Gemini matching analysis unavailable: HTTP "
                    + response.statusCode()
                    + (errorDetail == null ? "" : " - " + errorDetail));
                return MatchingAiAttempt.failure(resolveHttpFailureMessage(response.statusCode(), errorDetail));
            }

            String outputJson = extractOutputJson(response.body());
            if (outputJson == null) {
                System.out.println("Gemini matching analysis unavailable: response parsing failed.");
                return MatchingAiAttempt.failure("La reponse Gemini n'a pas pu etre interpretee.");
            }

            String summary = extractStringField(outputJson, "summary");
            String level = extractStringField(outputJson, "level");
            List<String> keywords = extractStringArrayField(outputJson, "keywords");
            if (summary == null || level == null || keywords.isEmpty()) {
                System.out.println("Gemini matching analysis unavailable: structured payload incomplete.");
                return MatchingAiAttempt.failure("La reponse Gemini est incomplete pour cette analyse.");
            }

            return MatchingAiAttempt.success(new MatchingAiAnalysis(summary, level, keywords, "Gemini"));
        } catch (InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logTransportFailure("Gemini matching analysis interrupted", e);
            return MatchingAiAttempt.failure("La connexion a Gemini a ete interrompue. Reessayez.");
        } catch (IOException e) {
            logTransportFailure("Gemini matching analysis unavailable", e);
            try {
                HttpResponse<String> retriedResponse = sendRequest(endpoint, apiKey, payload);
                if (retriedResponse.statusCode() < 200 || retriedResponse.statusCode() >= 300) {
                    String errorDetail = extractApiErrorMessage(retriedResponse.body());
                    System.out.println("Gemini matching analysis unavailable after retry: HTTP "
                        + retriedResponse.statusCode()
                        + (errorDetail == null ? "" : " - " + errorDetail));
                    return MatchingAiAttempt.failure(resolveHttpFailureMessage(retriedResponse.statusCode(), errorDetail));
                }

                String retriedOutputJson = extractOutputJson(retriedResponse.body());
                if (retriedOutputJson == null) {
                    System.out.println("Gemini matching analysis unavailable after retry: response parsing failed.");
                    return MatchingAiAttempt.failure("La reponse Gemini n'a pas pu etre interpretee.");
                }

                String summary = extractStringField(retriedOutputJson, "summary");
                String level = extractStringField(retriedOutputJson, "level");
                List<String> keywords = extractStringArrayField(retriedOutputJson, "keywords");
                if (summary == null || level == null || keywords.isEmpty()) {
                    System.out.println("Gemini matching analysis unavailable after retry: structured payload incomplete.");
                    return MatchingAiAttempt.failure("La reponse Gemini est incomplete pour cette analyse.");
                }

                return MatchingAiAttempt.success(new MatchingAiAnalysis(summary, level, keywords, "Gemini"));
            } catch (InterruptedException retryInterruptedException) {
                Thread.currentThread().interrupt();
                logTransportFailure("Gemini matching analysis interrupted after retry", retryInterruptedException);
                return MatchingAiAttempt.failure("La connexion a Gemini a ete interrompue. Reessayez.");
            } catch (IOException retryException) {
                logTransportFailure("Gemini matching analysis unavailable after retry", retryException);
                return MatchingAiAttempt.failure(resolveTransportFailureMessage(retryException));
            }
        }
    }

    private HttpResponse<String> sendRequest(String endpoint, String apiKey, String payload)
        throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();

        return buildHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private HttpClient buildHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }

    private String resolveModel() {
        String model = normalizeEnv("MATCHING_AI_MODEL");
        if (model != null) {
            return model;
        }

        model = normalizeEnv("GEMINI_MODEL");
        return model != null ? model : DEFAULT_GEMINI_MODEL;
    }

    private String buildPayload(MatchingAiContext context) {
        String prompt = """
            You are an academic matching analysis assistant for a tutoring platform.
            Return JSON only, matching the provided schema exactly.
            Write the summary in concise professional French.
            The level must be exactly one of: Debutant, Intermediaire, Avance, Niveau non precise.
            Keywords must contain 3 to 5 short distinct French items useful for pedagogical matching.
            Base your analysis on the subject, objective text, preferred mode, visibility scope, and requested duration.

            Analyse this tutoring need for matching.
            Subject: %s
            Objective: %s
            Mode: %s
            Visibility: %s
            Duration: %d minutes
            """.formatted(
            context.subject(),
            context.objectiveText(),
            context.mode(),
            context.visibilityScope(),
            context.durationMin()
        );

        String schema = """
            {
              "type": "object",
              "properties": {
                "summary": {
                  "type": "string",
                  "description": "Concise French summary of the student's need for matching."
                },
                "level": {
                  "type": "string",
                  "enum": ["Debutant", "Intermediaire", "Avance", "Niveau non precise"]
                },
                "keywords": {
                  "type": "array",
                  "minItems": 3,
                  "maxItems": 5,
                  "items": {
                    "type": "string"
                  }
                }
              },
              "required": ["summary", "level", "keywords"],
              "additionalProperties": false
            }
            """;

        return "{"
            + "\"contents\":[{"
            + "\"role\":\"user\","
            + "\"parts\":[{\"text\":" + quote(prompt) + "}]"
            + "}],"
            + "\"generationConfig\":{"
            + "\"responseMimeType\":\"application/json\","
            + "\"responseJsonSchema\":" + compact(schema) + ","
            + "\"thinkingConfig\":{\"thinkingBudget\":0}"
            + "}"
            + "}";
    }

    private String extractOutputJson(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        int candidatesIndex = findFieldIndex(responseBody, "candidates", 0);
        int textFieldIndex = findFieldIndex(responseBody, "text", Math.max(0, candidatesIndex));
        if (textFieldIndex < 0) {
            textFieldIndex = findFieldIndex(responseBody, "text", 0);
        }
        if (textFieldIndex < 0) {
            return null;
        }

        int firstQuote = findNextQuoteAfterColon(responseBody, textFieldIndex);
        if (firstQuote < 0) {
            return null;
        }
        return parseJsonStringLiteral(responseBody, firstQuote);
    }

    private String extractStringField(String json, String fieldName) {
        int fieldIndex = findFieldIndex(json, fieldName, 0);
        if (fieldIndex < 0) {
            return null;
        }

        int firstQuote = findNextQuoteAfterColon(json, fieldIndex);
        if (firstQuote < 0) {
            return null;
        }

        return parseJsonStringLiteral(json, firstQuote);
    }

    private List<String> extractStringArrayField(String json, String fieldName) {
        int fieldIndex = findFieldIndex(json, fieldName, 0);
        if (fieldIndex < 0) {
            return List.of();
        }

        int colonIndex = json.indexOf(':', fieldIndex);
        if (colonIndex < 0) {
            return List.of();
        }

        int arrayStart = -1;
        for (int i = colonIndex + 1; i < json.length(); i++) {
            char current = json.charAt(i);
            if (Character.isWhitespace(current)) {
                continue;
            }
            if (current == '[') {
                arrayStart = i;
            }
            break;
        }

        if (arrayStart < 0) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        int index = arrayStart + 1;
        while (index < json.length()) {
            char current = json.charAt(index);
            if (Character.isWhitespace(current) || current == ',') {
                index++;
                continue;
            }
            if (current == ']') {
                break;
            }
            if (current != '"') {
                return List.of();
            }
            String value = parseJsonStringLiteral(json, index);
            if (value == null || value.isBlank()) {
                return List.of();
            }
            values.add(value);
            index = skipJsonStringLiteral(json, index);
            if (index < 0) {
                return List.of();
            }
        }
        return sanitizeKeywords(values);
    }

    private int skipJsonStringLiteral(String source, int startQuoteIndex) {
        boolean escaping = false;
        for (int index = startQuoteIndex + 1; index < source.length(); index++) {
            char current = source.charAt(index);
            if (escaping) {
                if (current == 'u') {
                    index += 4;
                }
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                return index + 1;
            }
        }
        return -1;
    }

    private String parseJsonStringLiteral(String source, int startQuoteIndex) {
        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int index = startQuoteIndex + 1; index < source.length(); index++) {
            char current = source.charAt(index);
            if (escaping) {
                switch (current) {
                    case '"', '\\', '/' -> builder.append(current);
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        if (index + 4 >= source.length()) {
                            return null;
                        }
                        String hex = source.substring(index + 1, index + 5);
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException exception) {
                            return null;
                        }
                        index += 4;
                    }
                    default -> builder.append(current);
                }
                escaping = false;
                continue;
            }

            if (current == '\\') {
                escaping = true;
                continue;
            }

            if (current == '"') {
                return builder.toString();
            }

            builder.append(current);
        }
        return null;
    }

    private int findFieldIndex(String json, String fieldName, int searchFrom) {
        Pattern fieldPattern = Pattern.compile(FIELD_PATTERN_TEMPLATE.pattern().formatted(Pattern.quote(fieldName)));
        Matcher matcher = fieldPattern.matcher(json);
        if (matcher.find(Math.max(0, searchFrom))) {
            return matcher.start();
        }
        return -1;
    }

    private int findNextQuoteAfterColon(String json, int fieldIndex) {
        int colonIndex = json.indexOf(':', fieldIndex);
        if (colonIndex < 0) {
            return -1;
        }

        for (int index = colonIndex + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (Character.isWhitespace(current)) {
                continue;
            }
            return current == '"' ? index : -1;
        }
        return -1;
    }

    private List<String> sanitizeKeywords(List<String> rawKeywords) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String keyword : rawKeywords) {
            if (keyword == null) {
                continue;
            }
            String normalized = keyword.trim().replaceAll("\\s+", " ");
            if (!normalized.isEmpty()) {
                unique.add(normalized);
            }
            if (unique.size() == 5) {
                break;
            }
        }
        return unique.isEmpty() ? List.of() : new ArrayList<>(unique);
    }

    private String normalizeEnv(String key) {
        return LocalConfig.get(key);
    }

    private String extractApiErrorMessage(String responseBody) {
        String message = extractStringField(responseBody, "message");
        if (message == null) {
            return null;
        }

        String normalized = message.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveHttpFailureMessage(int statusCode, String errorDetail) {
        return switch (statusCode) {
            case 400 -> errorDetail != null
                ? "La requete Gemini est invalide: " + errorDetail
                : "La requete Gemini est invalide.";
            case 401, 403 -> "L'appel Gemini a ete refuse. Verifiez GEMINI_API_KEY.";
            case 429 -> "La limite Gemini est atteinte temporairement. Veuillez reessayer dans un instant.";
            default -> errorDetail != null
                ? "Gemini a retourne une erreur HTTP " + statusCode + ": " + errorDetail
                : "Gemini a retourne une erreur HTTP " + statusCode + ".";
        };
    }

    private String resolveTransportFailureMessage(IOException exception) {
        Throwable rootCause = rootCause(exception);
        if (rootCause instanceof HttpTimeoutException) {
            return "Gemini met trop de temps a repondre. Veuillez reessayer dans un instant.";
        }
        if (rootCause instanceof UnknownHostException || rootCause instanceof ConnectException) {
            return "Connexion a Gemini impossible pour le moment. Verifiez Internet puis reessayez.";
        }
        if (rootCause instanceof SSLException) {
            return "La connexion securisee vers Gemini a echoue. Redemarrez l'application puis reessayez.";
        }
        return "Connexion a Gemini impossible pour le moment. Veuillez reessayer.";
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private void logTransportFailure(String prefix, Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        String detail = rootCause.getMessage();
        System.out.println(prefix + ": "
            + rootCause.getClass().getSimpleName()
            + (detail == null || detail.isBlank() ? "" : " - " + detail));
    }

    private String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String quote(String value) {
        if (value == null) {
            return "null";
        }

        StringBuilder builder = new StringBuilder("\"");
        for (char current : value.toCharArray()) {
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 0x20) {
                        builder.append(String.format(Locale.ROOT, "\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }

    public record MatchingAiContext(String subject,
                                    String objectiveText,
                                    String mode,
                                    String visibilityScope,
                                    int durationMin) {
    }

    public record MatchingAiAnalysis(String summary,
                                     String level,
                                     List<String> keywords,
                                     String source) {
    }

    public record MatchingAiAttempt(boolean success,
                                    MatchingAiAnalysis analysis,
                                    String errorMessage) {
        public static MatchingAiAttempt success(MatchingAiAnalysis analysis) {
            return new MatchingAiAttempt(true, analysis, null);
        }

        public static MatchingAiAttempt failure(String errorMessage) {
            return new MatchingAiAttempt(false, null, errorMessage);
        }
    }
}
