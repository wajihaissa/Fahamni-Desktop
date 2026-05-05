package tn.esprit.fahamni.services;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranslationService {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Chaque segment : ["traduction","original",null,...] — le ,null filtre les métadonnées
    private static final Pattern GOOGLE_PATTERN = Pattern.compile(
        "\\[\"((?:[^\"\\\\]|\\\\.+)*)\",\"(?:[^\"\\\\]|\\\\.)*\",null"
    );

    /**
     * @param text       texte source en français
     * @param targetLang "en" ou "ar"
     */
    public String translate(String text, String targetLang) {
        if (text == null || text.isBlank()) return null;

        // Limiter à 800 caractères
        String input = text.length() > 800 ? text.substring(0, 800) + "…" : text;

        try {
            String encoded = URLEncoder.encode(input, StandardCharsets.UTF_8);
            String url = "https://translate.googleapis.com/translate_a/single"
                       + "?client=gtx&sl=fr&tl=" + targetLang + "&dt=t&q=" + encoded;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                System.err.println("TranslationService HTTP " + res.statusCode());
                return null;
            }
            if (res.statusCode() == 200) {
                // Extraire tous les segments traduits et les concaténer
                StringBuilder sb = new StringBuilder();
                Matcher m = GOOGLE_PATTERN.matcher(res.body());
                while (m.find()) {
                    String segment = m.group(1)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\/", "/")
                        .replace("\\\\", "\\");
                    sb.append(segment);
                }
                String result = sb.toString().trim();
                if (!result.isEmpty()) return result;
            }
        } catch (java.net.http.HttpTimeoutException e) {
            System.err.println("TranslationService: timeout - connexion lente ou indisponible");
        } catch (java.net.ConnectException e) {
            System.err.println("TranslationService: pas de connexion internet");
        } catch (Exception e) {
            System.err.println("TranslationService: " + e.getMessage());
        }
        return null;
    }
}
