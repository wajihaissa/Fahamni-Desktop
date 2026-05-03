package tn.esprit.fahamni.services;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpellCheckService {

    private static final String API_URL  = "https://api.languagetool.org/v2/check";
    private static final String SEP      = "\n\n";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    // ── Modèles publics ──────────────────────────────────────────────────────

    public static class SpellError {
        public final int    offset;
        public final int    length;
        public final String badWord;
        public final String suggestion;
        public final String message;

        SpellError(int offset, int length, String badWord, String suggestion, String message) {
            this.offset     = offset;
            this.length     = length;
            this.badWord    = badWord;
            this.suggestion = suggestion;
            this.message    = message;
        }
    }

    public static class CombinedResult {
        public final List<SpellError> titreErrors;
        public final List<SpellError> contentErrors;
        CombinedResult(List<SpellError> t, List<SpellError> c) {
            this.titreErrors   = t;
            this.contentErrors = c;
        }
    }

    // ── Point d'entrée principal ─────────────────────────────────────────────

    public CombinedResult checkCombined(String titre, String content) {
        String t = titre   != null ? titre   : "";
        String c = content != null ? content : "";

        String combined  = t + SEP + c;
        int contentStart = t.length() + SEP.length();

        List<SpellError> all = callApi(combined);

        List<SpellError> titreErrors   = new ArrayList<>();
        List<SpellError> contentErrors = new ArrayList<>();

        for (SpellError e : all) {
            if (e.offset + e.length <= t.length()) {
                titreErrors.add(e);
            } else if (e.offset >= contentStart) {
                contentErrors.add(new SpellError(
                    e.offset - contentStart, e.length,
                    e.badWord, e.suggestion, e.message));
            }
        }
        return new CombinedResult(titreErrors, contentErrors);
    }

    // ── Appel HTTP ───────────────────────────────────────────────────────────

    private List<SpellError> callApi(String text) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        try {
            String body = "language=fr"
                + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200)
                return parse(res.body(), text);
            System.err.println("SpellCheckService HTTP " + res.statusCode() + " : " + res.body().substring(0, Math.min(200, res.body().length())));
        } catch (java.net.http.HttpTimeoutException e) {
            System.err.println("SpellCheckService: timeout - API indisponible");
        } catch (Exception e) {
            System.err.println("SpellCheckService: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    // ── Parseur JSON ─────────────────────────────────────────────────────────

    /*
     * Format de la réponse LanguageTool :
     * {"software":{...},"matches":[
     *   {"message":"...","shortMessage":"...","replacements":[{"value":"..."},...],
     *    "offset":N,"length":M,"context":{...},"rule":{...}},
     *   ...
     * ]}
     *
     * On utilise des regex pour extraire proprement chaque match.
     */

    // Regex : capture chaque objet match complet (entre accolades au niveau 1)
    private static final Pattern MATCH_BLOCK = Pattern.compile(
        "\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"" // message
        + ".*?"
        + "\"replacements\"\\s*:\\s*\\[(.*?)\\]"         // replacements array
        + ".*?"
        + "\"offset\"\\s*:\\s*(\\d+)"                    // offset
        + ".*?"
        + "\"length\"\\s*:\\s*(\\d+)",                   // length
        Pattern.DOTALL
    );

    private static final Pattern VALUE_PATTERN = Pattern.compile(
        "\"value\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private List<SpellError> parse(String json, String text) {
        List<SpellError> errors = new ArrayList<>();

        // Extraire le tableau "matches"
        int matchesIdx = json.indexOf("\"matches\"");
        if (matchesIdx < 0) return errors;
        String matchesSection = json.substring(matchesIdx);

        Matcher m = MATCH_BLOCK.matcher(matchesSection);
        while (m.find()) {
            try {
                String message      = unescape(m.group(1));
                String replacements = m.group(2);
                int    offset       = Integer.parseInt(m.group(3));
                int    length       = Integer.parseInt(m.group(4));

                if (offset < 0 || length <= 0 || offset + length > text.length()) continue;

                String badWord = text.substring(offset, offset + length);
                if (badWord.isBlank()) continue;

                // Collecter toutes les suggestions
                List<String> suggestions = new ArrayList<>();
                Matcher vm = VALUE_PATTERN.matcher(replacements);
                while (vm.find()) {
                    String v = unescape(vm.group(1));
                    if (!v.isBlank()) suggestions.add(v);
                }

                String best = chooseBest(badWord, suggestions);
                errors.add(new SpellError(offset, length, badWord, best, message));

            } catch (Exception ignored) {}
        }
        return errors;
    }

    // ── Sélection de la meilleure suggestion ─────────────────────────────────

    /**
     * Choisit la meilleure suggestion :
     * - Préfère un mot simple (sans tiret ni espace) si l'original est simple
     * - Préfère la suggestion dont la casse correspond à l'original
     * - Sinon prend la première disponible
     */
    private String chooseBest(String badWord, List<String> suggestions) {
        if (suggestions.isEmpty()) return "";

        boolean simpleWord     = !badWord.contains("-") && !badWord.contains(" ");
        boolean startsLower    = Character.isLowerCase(badWord.charAt(0));

        for (String s : suggestions) {
            if (simpleWord && (s.contains("-") || s.contains(" "))) continue;
            if (startsLower && Character.isUpperCase(s.charAt(0)))  continue;
            return s;
        }
        // Aucune parfaite → prendre la première sans tiret/espace si possible
        for (String s : suggestions) {
            if (simpleWord && !s.contains("-") && !s.contains(" ")) return s;
        }
        return suggestions.get(0);
    }

    // ── Utilitaire ───────────────────────────────────────────────────────────

    private String unescape(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t");
    }
}
