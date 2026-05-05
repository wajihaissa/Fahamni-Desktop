package tn.esprit.fahamni.services.ai;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

public class GeminiService {

    private static final String API_KEY_ENV = "GEMINI_API_KEY";
    private static final String MODEL_ENV = "GEMINI_MODEL";
    private static final String DEFAULT_MODEL = "gemini-1.5-flash";
    private static final String[] FALLBACK_MODELS = {
        "gemini-1.0-pro",
        "gemini-1.5-pro",
        "gemini-pro"
    };
    private static final String API_BASE_TEMPLATE =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=";

    public String askGemini(String prompt) throws Exception {
        return askGeminiInternal(prompt);
    }

    public String askGeminiWithContext(String prompt, String context) throws Exception {
        String finalPrompt =
            "You are Fahamni AI, an expert teaching assistant. Use the following course materials to answer the student's question accurately. If the answer is not in the text, use your general knowledge but mention that it is not explicitly in the course.\n\nCourse Materials:\n"
                + context
                + "\n\nStudent Question:\n"
                + prompt;
        return askGeminiInternal(finalPrompt);
    }

    private String askGeminiInternal(String prompt) throws Exception {
        System.out.println("[GeminiService] Request started - prompt length: " + prompt.length());
        
        String apiKey = resolveApiKey();
        System.out.println("[GeminiService] API Key resolved");
        
        String model = resolveModel();
        System.out.println("[GeminiService] Model resolved: " + model);
        
        // Try primary model first, then fallback models
        String response = tryCallModel(prompt, apiKey, model);
        if (response != null) {
            return response;
        }
        
        // If primary model fails, try fallback models
        System.out.println("[GeminiService] Primary model unavailable, trying fallback models...");
        for (String fallbackModel : FALLBACK_MODELS) {
            System.out.println("[GeminiService] Trying fallback model: " + fallbackModel);
            response = tryCallModel(prompt, apiKey, fallbackModel);
            if (response != null) {
                return response;
            }
        }
        
        throw new RuntimeException("All Gemini models are currently unavailable. Please try again later.");
    }

    private String tryCallModel(String prompt, String apiKey, String model) throws Exception {
        String apiBase = API_BASE_TEMPLATE.formatted(model);
        System.out.println("[GeminiService] API Base URL constructed");
        
        URL url = new URL(apiBase + apiKey);
        HttpURLConnection connection = null;

        try {
            System.out.println("[GeminiService] Opening connection...");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(20000);  // 20 seconds
            connection.setReadTimeout(45000);     // 45 seconds

            JSONObject payload = new JSONObject()
                .put("contents", new JSONArray()
                    .put(new JSONObject()
                        .put("parts", new JSONArray()
                            .put(new JSONObject().put("text", prompt)))));

            byte[] jsonBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            System.out.println("[GeminiService] Writing request body - size: " + jsonBytes.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBytes);
                os.flush();
            }

            System.out.println("[GeminiService] Waiting for response...");
            int statusCode = connection.getResponseCode();
            System.out.println("[GeminiService] API Response Status: " + statusCode);
            
            InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

            System.out.println("[GeminiService] Reading response stream...");
            String responseBody = readStream(stream);
            System.out.println("[GeminiService] Response length: " + responseBody.length());
            
            if (statusCode == 503) {
                System.out.println("[GeminiService] Model " + model + " is temporarily unavailable (503)");
                return null;  // Try next fallback model
            }
            
            if (statusCode < 200 || statusCode >= 300) {
                System.err.println("[GeminiService] Error response: " + responseBody);
                throw new RuntimeException("Gemini request failed (" + statusCode + "): " + responseBody);
            }

            System.out.println("[GeminiService] Parsing JSON response...");
            JSONObject responseJson = new JSONObject(responseBody);
            JSONArray candidates = responseJson.optJSONArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return "No response candidate returned by Gemini.";
            }

            JSONObject firstCandidate = candidates.optJSONObject(0);
            if (firstCandidate == null) {
                return "Invalid candidate format returned by Gemini.";
            }

            JSONObject content = firstCandidate.optJSONObject("content");
            if (content == null) {
                return "No content returned by Gemini.";
            }

            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.isEmpty()) {
                return "No text parts returned by Gemini.";
            }

            JSONObject firstPart = parts.optJSONObject(0);
            if (firstPart == null) {
                return "Invalid text part returned by Gemini.";
            }

            String text = firstPart.optString("text", "").trim();
            return text.isEmpty() ? "Gemini returned an empty response." : text;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String resolveApiKey() {
        // 1) Environment variable
        String key = System.getenv(API_KEY_ENV);
        if (key != null && !key.isBlank()) {
            return key.trim();
        }

        // 2) JVM argument: -Dgemini.api.key=...
        key = System.getProperty("gemini.api.key");
        if (key != null && !key.isBlank()) {
            return key.trim();
        }

        // 3) Local .env file in project root: GEMINI_API_KEY=...
        key = readKeyFromDotEnv();
        if (key != null && !key.isBlank()) {
            return key.trim();
        }

        throw new IllegalStateException(
            "Gemini API key is missing. Provide one of: "
                + API_KEY_ENV
                + " env var, JVM arg -Dgemini.api.key=..., or .env file (GEMINI_API_KEY=...)."
                + " user.dir=" + System.getProperty("user.dir")
                + ", searched=" + candidateDotEnvPaths()
        );
    }

    private String resolveModel() {
        // 1) Environment variable
        String model = System.getenv(MODEL_ENV);
        if (model != null && !model.isBlank()) {
            return model.trim();
        }

        // 2) JVM argument: -Dgemini.model=...
        model = System.getProperty("gemini.model");
        if (model != null && !model.isBlank()) {
            return model.trim();
        }

        // 3) .env file
        model = readValueFromDotEnv(MODEL_ENV);
        if (model != null && !model.isBlank()) {
            return model.trim();
        }

        return DEFAULT_MODEL;
    }

    private String readKeyFromDotEnv() {
        return readValueFromDotEnv(API_KEY_ENV);
    }

    private String readValueFromDotEnv(String key) {
        try {
            for (Path envPath : candidateDotEnvPaths()) {
                if (!Files.exists(envPath)) {
                    continue;
                }
                String value = readValueFromEnvFile(envPath, key);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Path> candidateDotEnvPaths() {
        Set<Path> candidates = new LinkedHashSet<>();
        
        // Limit search depth to avoid hanging on deep directory traversals
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        addDotEnvCandidates(candidates, start, 0, 3);  // Limit to 3 levels up
        
        try {
            URL codeSource = GeminiService.class.getProtectionDomain().getCodeSource().getLocation();
            if (codeSource != null) {
                Path location = Path.of(codeSource.toURI()).toAbsolutePath().normalize();
                Path base = Files.isDirectory(location) ? location : location.getParent();
                addDotEnvCandidates(candidates, base, 0, 2);  // Limit to 2 levels up
            }
        } catch (Exception ignored) {
            // Ignore and keep the other candidate locations.
        }

        return new ArrayList<>(candidates);
    }

    private void addDotEnvCandidates(Set<Path> candidates, Path start, int depth, int maxDepth) {
        if (start == null || depth > maxDepth) {
            return;
        }
        candidates.add(start.resolve(".env"));
        Path parent = start.getParent();
        if (parent != null && !parent.equals(start)) {  // Stop at root
            addDotEnvCandidates(candidates, parent, depth + 1, maxDepth);
        }
    }

    private String readKeyFromEnvFile(Path envPath) throws Exception {
        return readValueFromEnvFile(envPath, API_KEY_ENV);
    }

    private String readValueFromEnvFile(Path envPath, String targetKey) throws Exception {
        List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine.replace("\uFEFF", "").trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith(targetKey + "=")) {
                String value = line.substring((targetKey + "=").length()).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private String readStream(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}