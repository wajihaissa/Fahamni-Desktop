package tn.esprit.fahamni.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GroqMatchingAnalysisService {

    private static final String GROQ_RESPONSES_API_URL = "https://api.groq.com/openai/v1/responses";
    private static final String DEFAULT_GROQ_MODEL = "openai/gpt-oss-20b";
    private static final String MATCHING_SCHEMA_NAME = "matching_need_profile";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final Pattern OUTPUT_TEXT_TYPE_PATTERN = Pattern.compile("\"type\"\\s*:\\s*\"output_text\"");

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .build();

    public boolean isConfigured() {
        return normalizeEnv("GROQ_API_KEY") != null;
    }

    public MatchingAiAttempt analyzeNeed(MatchingAiContext context) {
        String apiKey = normalizeEnv("GROQ_API_KEY");
        if (apiKey == null) {
            return MatchingAiAttempt.failure("La variable d'environnement GROQ_API_KEY est introuvable.");
        }
        if (context == null || context.objectiveText() == null || context.objectiveText().isBlank()) {
            return MatchingAiAttempt.failure("Le besoin etudiant doit etre renseigne pour lancer l'analyse Groq.");
        }

        String model = resolveModel();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(GROQ_RESPONSES_API_URL))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .header("X-Client-Request-Id", UUID.randomUUID().toString())
            .POST(HttpRequest.BodyPublishers.ofString(buildPayload(context, model), StandardCharsets.UTF_8));

        try {
            HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorDetail = extractApiErrorMessage(response.body());
                System.out.println("Groq matching analysis unavailable: HTTP "
                    + response.statusCode()
                    + (errorDetail == null ? "" : " - " + errorDetail));
                return MatchingAiAttempt.failure(resolveHttpFailureMessage(response.statusCode(), errorDetail));
            }

            String outputJson = extractOutputJson(response.body());
            if (outputJson == null) {
                System.out.println("Groq matching analysis unavailable: response parsing failed.");
                return MatchingAiAttempt.failure("La reponse Groq n'a pas pu etre interpretee pour le matching.");
            }

            String summary = extractStringField(outputJson, "summary");
            String level = extractStringField(outputJson, "level");
            List<String> keywords = extractStringArrayField(outputJson, "keywords");
            if (summary == null || level == null || keywords.isEmpty()) {
                System.out.println("Groq matching analysis unavailable: structured payload incomplete.");
                return MatchingAiAttempt.failure("La reponse Groq est incomplete pour cette analyse de matching.");
            }

            return MatchingAiAttempt.success(new MatchingAiAnalysis(summary, level, keywords, "Groq externe"));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Groq matching analysis unavailable: " + e.getMessage());
            return MatchingAiAttempt.failure("L'analyse Groq est momentanement indisponible: " + e.getMessage());
        }
    }

    private String resolveModel() {
        String model = normalizeEnv("MATCHING_AI_MODEL");
        if (model != null) {
            return model;
        }

        model = normalizeEnv("GROQ_MODEL");
        return model != null ? model : DEFAULT_GROQ_MODEL;
    }

    private String buildPayload(MatchingAiContext context, String model) {
        String instructions = """
            You are an academic matching analysis assistant for a tutoring platform.
            Return JSON only, matching the provided schema exactly.
            Write the summary in concise professional French.
            The level must be exactly one of: Debutant, Intermediaire, Avance, Niveau non precise.
            Keywords must contain 3 to 5 short distinct French items useful for pedagogical matching.
            Base your analysis on the subject, objective text, preferred mode, visibility scope, and requested duration.
            """;

        String userPrompt = """
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
              "additionalProperties": false,
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
              "required": ["summary", "level", "keywords"]
            }
            """;

        return "{"
            + "\"model\":" + quote(model) + ","
            + "\"reasoning\":{\"effort\":\"low\"},"
            + "\"text\":{\"format\":{\"type\":\"json_schema\",\"name\":" + quote(MATCHING_SCHEMA_NAME)
            + ",\"strict\":true,\"schema\":" + compact(schema) + "}},"
            + "\"instructions\":" + quote(instructions) + ","
            + "\"input\":[{\"role\":\"user\",\"content\":[{\"type\":\"input_text\",\"text\":" + quote(userPrompt) + "}]}]"
            + "}";
    }

    private String extractOutputJson(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        Matcher matcher = OUTPUT_TEXT_TYPE_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
            return null;
        }

        int textFieldIndex = findFieldIndex(responseBody, "text", matcher.end());
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
        Pattern fieldPattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:");
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
        String value = System.getenv(key);
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
                ? "La requete envoyee a Groq est invalide: " + errorDetail
                : "La requete envoyee a Groq est invalide. Verifiez le modele et le schema JSON transmis.";
            case 401 -> "L'appel Groq a ete refuse. Verifiez la cle API configuree.";
            case 403 -> "L'appel Groq est interdit pour ce projet ou cette organisation.";
            case 429 -> "La limite Groq est atteinte temporairement. Reessayez dans un instant.";
            default -> errorDetail != null
                ? "Groq a retourne une erreur HTTP " + statusCode + " pendant l'analyse du matching: " + errorDetail
                : "Groq a retourne une erreur HTTP " + statusCode + " pendant l'analyse du matching.";
        };
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
