package tn.esprit.fahamni.services.ai;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

public class GeminiService {

    private static final String API_KEY_ENV = "GEMINI_API_KEY";
    private static final String MODEL = "gemini-2.5-flash";
    private static final String API_BASE =
        "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=";

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
        String apiKey = resolveApiKey();
        URL url = new URL(API_BASE + apiKey);
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);

            JSONObject payload = new JSONObject()
                .put("contents", new JSONArray()
                    .put(new JSONObject()
                        .put("parts", new JSONArray()
                            .put(new JSONObject().put("text", prompt)))));

            byte[] jsonBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBytes);
            }

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();

            String responseBody = readStream(stream);
            if (statusCode < 200 || statusCode >= 300) {
                throw new RuntimeException("Gemini request failed (" + statusCode + "): " + responseBody);
            }

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

    private String readKeyFromDotEnv() {
        try {
            for (Path envPath : candidateDotEnvPaths()) {
                if (!Files.exists(envPath)) {
                    continue;
                }
                String key = readKeyFromEnvFile(envPath);
                if (key != null && !key.isBlank()) {
                    return key;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Path> candidateDotEnvPaths() {
        Set<Path> candidates = new LinkedHashSet<>();
        addDotEnvCandidates(candidates, Path.of(System.getProperty("user.dir")));

        try {
            URL codeSource = GeminiService.class.getProtectionDomain().getCodeSource().getLocation();
            if (codeSource != null) {
                Path location = Path.of(codeSource.toURI()).toAbsolutePath().normalize();
                addDotEnvCandidates(candidates, Files.isDirectory(location) ? location : location.getParent());
            }
        } catch (Exception ignored) {
            // Ignore and keep the other candidate locations.
        }

        return new ArrayList<>(candidates);
    }

    private void addDotEnvCandidates(Set<Path> candidates, Path start) {
        if (start == null) {
            return;
        }
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            candidates.add(current.resolve(".env"));
            current = current.getParent();
        }
    }

    private String readKeyFromEnvFile(Path envPath) throws Exception {
        List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine.replace("\uFEFF", "").trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith(API_KEY_ENV + "=")) {
                String value = line.substring((API_KEY_ENV + "=").length()).trim();
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
