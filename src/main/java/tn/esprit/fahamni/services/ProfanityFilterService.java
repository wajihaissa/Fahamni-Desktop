package tn.esprit.fahamni.services;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ProfanityFilterService {

    private static final List<String> WORDS = Arrays.asList(
        // Français — insultes directes uniquement (pas de mots éducatifs)
        "merde", "putain", "connard", "connasse", "salope", "enculé", "encule",
        "fdp", "fils de pute", "ta gueule", "va te faire", "nique", "niquer",
        "bâtard", "batard", "bordel", "couille", "couilles", "bite", "con",
        "conne", "chier", "pute", "idiot", "idiote", "imbécile", "imbecile",
        "crétin", "cretin", "débile", "debile", "abruti", "abrutie",
        "demeuré", "demeure", "raciste", "nazisme", "nazi", "hitler",
        // Arabe translittéré courant
        "ahmak", "msatk", "msatek", "yaayf", "zebala", "hmar", "hmare",
        // Anglais
        "fuck", "shit", "bitch", "asshole", "bastard", "dick", "cunt",
        "motherfucker", "faggot", "nigger", "whore", "slut", "damn", "crap"
    );

    // Utilise \p{L} et \p{N} pour les frontières Unicode (fonctionne avec les accents)
    private static final String WB_BEFORE = "(?<![\\p{L}\\p{N}])";
    private static final String WB_AFTER  = "(?![\\p{L}\\p{N}])";

    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();
    static {
        for (String word : WORDS) {
            PATTERNS.put(word, Pattern.compile(
                WB_BEFORE + Pattern.quote(word) + WB_AFTER,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }

    public static class FilterResult {
        public final boolean blocked;
        public final String detectedWord;

        FilterResult(boolean blocked, String detectedWord) {
            this.blocked = blocked;
            this.detectedWord = detectedWord;
        }
    }

    public FilterResult check(String text) {
        if (text == null || text.isBlank()) return new FilterResult(false, null);

        // Normalisation leet-speak étendue
        String lower = text.toLowerCase()
                .replace("@", "a").replace("3", "e").replace("0", "o")
                .replace("1", "i").replace("$", "s").replace("€", "e")
                .replace("4", "a").replace("5", "s").replace("7", "t")
                .replace("|", "i").replace("!", "i").replace("8", "b");

        for (Map.Entry<String, Pattern> entry : PATTERNS.entrySet()) {
            if (entry.getValue().matcher(lower).find())
                return new FilterResult(true, entry.getKey());
        }
        return new FilterResult(false, null);
    }
}
