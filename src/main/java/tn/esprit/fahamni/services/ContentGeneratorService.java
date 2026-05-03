package tn.esprit.fahamni.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ContentGeneratorService {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL    = "llama-3.1-8b-instant";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public static boolean hasApiKey() {
        return tn.esprit.fahamni.utils.AppConfig.isGroqConfigured();
    }

    public String generate(String titre, String categorie) {
        String apiKey = tn.esprit.fahamni.utils.AppConfig.getGroqApiKey();
        if (apiKey.isBlank()) {
            System.err.println("ContentGeneratorService: clé API Groq manquante.");
            return null;
        }
        try {
            String prompt = buildPrompt(titre, categorie);
            String body   = buildJson(prompt);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<byte[]> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            System.out.println("Groq HTTP " + res.statusCode());

            if (res.statusCode() == 200) {
                String responseText = new String(res.body(), StandardCharsets.UTF_8);
                System.out.println("Groq body: " + responseText.substring(0, Math.min(200, responseText.length())));
                return extractContent(responseText);
            } else {
                System.err.println("Groq error: " + new String(res.body(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            System.err.println("ContentGeneratorService: " + e.getMessage());
        }
        return null;
    }

    private String buildPrompt(String titre, String categorie) {
        return "Écris exactement 2 phrases en français résumant le sujet \""
             + titre + "\" (catégorie : " + categorie + "). "
             + "Maximum 200 caractères au total. Texte brut uniquement.";
    }

    private String buildJson(String prompt) {
        String escaped = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        return "{"
             + "\"model\":\"" + MODEL + "\","
             + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escaped + "\"}],"
             + "\"temperature\":0.7,"
             + "\"max_tokens\":80"
             + "}";
    }

    private String extractContent(String json) {
        // Locate "content" after "role":"assistant" to skip the request echo
        int roleIdx = json.indexOf("\"role\":\"assistant\"");
        int start = json.indexOf("\"content\":", roleIdx < 0 ? 0 : roleIdx);
        if (start < 0) return null;
        start += 10;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        start++;

        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') break;
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n':  sb.append('\n'); break;
                    case 't':  sb.append('\t'); break;
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    default:   sb.append(next); break;
                }
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }
}
