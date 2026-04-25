package tn.esprit.fahamni.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import tn.esprit.fahamni.utils.AiApiConfig;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class AiRoomDesignApiClient {

    private static final int MAX_OUTPUT_TOKENS = 1200;
    private static final Pattern SENSITIVE_TOKEN_PATTERN = Pattern.compile("\\b(?:sk-[A-Za-z0-9_-]+|AIza[A-Za-z0-9_-]{10,})\\b");

    private final AiApiConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AiRoomDesignApiClient(AiApiConfig config) {
        this(config, HttpClient.newBuilder().build(), new ObjectMapper());
    }

    AiRoomDesignApiClient(AiApiConfig config, HttpClient httpClient, ObjectMapper objectMapper) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public DesignSuggestion generateRoomDesign(String instructions, String prompt) {
        config.validate();

        try {
            HttpRequest request = buildRequest(instructions, prompt);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(resolveErrorMessage(response.body(), response.statusCode()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            String outputText = extractOutputText(root);
            if (outputText == null || outputText.isBlank()) {
                throw new IllegalStateException(resolveMissingOutputMessage(root));
            }

            JsonNode suggestionNode = objectMapper.readTree(outputText);
            return new DesignSuggestion(
                suggestionNode.path("room_name").asText(null),
                suggestionNode.path("building").asText(null),
                suggestionNode.path("location").asText(null),
                suggestionNode.path("room_type").asText(null),
                suggestionNode.path("disposition").asText(null),
                suggestionNode.path("capacity").asInt(0),
                suggestionNode.path("accessible").asBoolean(false),
                suggestionNode.path("summary").asText(null),
                suggestionNode.path("rationale").asText(null),
                readEquipmentPreferences(suggestionNode.path("equipment_preferences"))
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Erreur de lecture de la reponse Gemini: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel Gemini interrompu.", exception);
        }
    }

    private HttpRequest buildRequest(String instructions, String prompt) throws IOException {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode systemInstruction = payload.putObject("systemInstruction");
        ArrayNode systemParts = systemInstruction.putArray("parts");
        systemParts.addObject()
            .put("text", instructions);

        ArrayNode contents = payload.putArray("contents");
        ObjectNode userMessage = contents.addObject();
        userMessage.put("role", "user");
        ArrayNode parts = userMessage.putArray("parts");
        parts.addObject()
            .put("text", prompt);

        ObjectNode generationConfig = payload.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        generationConfig.set("responseJsonSchema", buildSchema());

        return HttpRequest.newBuilder(config.generateContentUri())
            .timeout(config.timeout())
            .header("x-goog-api-key", config.apiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
            .build();
    }

    private JsonNode buildSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode properties = schema.putObject("properties");
        properties.putObject("room_name").put("type", "string");
        properties.putObject("building").put("type", "string");
        properties.putObject("location").put("type", "string");

        ObjectNode roomType = properties.putObject("room_type");
        roomType.put("type", "string");
        ArrayNode roomTypeEnum = roomType.putArray("enum");
        roomTypeEnum.add("Cours");
        roomTypeEnum.add("Conference");
        roomTypeEnum.add("Laboratoire");
        roomTypeEnum.add("Amphitheatre");

        ObjectNode disposition = properties.putObject("disposition");
        disposition.put("type", "string");
        ArrayNode dispositionEnum = disposition.putArray("enum");
        dispositionEnum.add("classe");
        dispositionEnum.add("u");
        dispositionEnum.add("reunion");
        dispositionEnum.add("conference");
        dispositionEnum.add("atelier");
        dispositionEnum.add("informatique");

        ObjectNode capacity = properties.putObject("capacity");
        capacity.put("type", "integer");
        capacity.put("minimum", 1);
        capacity.put("maximum", 5000);

        properties.putObject("accessible").put("type", "boolean");
        properties.putObject("summary").put("type", "string");
        properties.putObject("rationale").put("type", "string");

        ObjectNode equipmentPreferences = properties.putObject("equipment_preferences");
        equipmentPreferences.put("type", "array");
        equipmentPreferences.putObject("items").put("type", "string");

        ArrayNode required = schema.putArray("required");
        required.add("room_name");
        required.add("building");
        required.add("location");
        required.add("room_type");
        required.add("disposition");
        required.add("capacity");
        required.add("accessible");
        required.add("summary");
        required.add("rationale");
        required.add("equipment_preferences");
        return schema;
    }

    private String extractOutputText(JsonNode root) {
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode candidate : candidates) {
            JsonNode content = candidate.path("content");
            JsonNode parts = content.path("parts");
            if (!parts.isArray()) {
                continue;
            }

            for (JsonNode part : parts) {
                String text = part.path("text").asText(null);
                if (text != null && !text.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }
        }

        return builder.isEmpty() ? null : builder.toString();
    }

    private List<String> readEquipmentPreferences(JsonNode node) {
        List<String> equipmentPreferences = new ArrayList<>();
        if (!node.isArray()) {
            return equipmentPreferences;
        }

        for (JsonNode item : node) {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                equipmentPreferences.add(item.asText().trim());
            }
        }
        return equipmentPreferences;
    }

    private String resolveErrorMessage(String responseBody, int statusCode) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = sanitizeSensitiveData(root.path("error").path("message").asText(null));
            if (message != null && !message.isBlank()) {
                return "Gemini a refuse la demande (" + statusCode + "): " + message;
            }
        } catch (IOException ignored) {
            // Keep the generic message below.
        }

        return "Gemini a retourne un statut HTTP " + statusCode + ".";
    }

    private String resolveMissingOutputMessage(JsonNode root) {
        String blockReason = root.path("promptFeedback").path("blockReason").asText(null);
        if (blockReason != null && !blockReason.isBlank()) {
            return "Gemini a bloque la demande pour raison de securite: " + blockReason + ".";
        }

        JsonNode candidates = root.path("candidates");
        if (candidates.isArray()) {
            for (JsonNode candidate : candidates) {
                String finishReason = candidate.path("finishReason").asText(null);
                if (finishReason != null && !finishReason.isBlank()) {
                    return "Gemini n'a retourne aucun texte exploitable (finishReason=" + finishReason + ").";
                }
            }
        }

        return "La reponse Gemini ne contient aucun texte exploitable.";
    }

    private String sanitizeSensitiveData(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        return SENSITIVE_TOKEN_PATTERN.matcher(message).replaceAll("[secret-redacted]");
    }

    public record DesignSuggestion(
        String roomName,
        String building,
        String location,
        String roomType,
        String disposition,
        int capacity,
        boolean accessible,
        String summary,
        String rationale,
        List<String> equipmentPreferences
    ) {
        public DesignSuggestion {
            roomName = normalize(roomName);
            building = normalize(building);
            location = normalize(location);
            roomType = normalize(roomType);
            disposition = normalize(disposition);
            capacity = Math.max(0, capacity);
            summary = normalize(summary);
            rationale = normalize(rationale);
            equipmentPreferences = equipmentPreferences == null ? List.of() : List.copyOf(equipmentPreferences);
        }

        private static String normalize(String value) {
            if (value == null) {
                return null;
            }

            String normalized = value.trim();
            return normalized.isEmpty() ? null : normalized;
        }
    }
}
