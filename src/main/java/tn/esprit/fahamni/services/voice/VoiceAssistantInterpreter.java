package tn.esprit.fahamni.services.voice;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public class VoiceAssistantInterpreter {

    public record Interpretation(
        VoiceAssistantIntent intent,
        double confidence,
        String reply
    ) {
    }

    private record Rule(
        VoiceAssistantIntent intent,
        List<String> phrases,
        String reply
    ) {
    }

    private static final List<String> WAKE_PHRASES = List.of(
        "hey fahamni",
        "hi fahamni",
        "hello fahamni",
        "fahamni",
        "fahamni listen",
        "fahamni help me",
        "assistant"
    );

    private static final List<Rule> RULES = List.of(
        rule(VoiceAssistantIntent.HELP,
            "help", "what can i say", "show commands", "what can you do", "aide", "commande",
            "You can say open profile, go to security, login with voice, open dashboard, or logout."),
        rule(VoiceAssistantIntent.CANCEL,
            "cancel", "stop listening", "nevermind", "annuler", "stop",
            "Okay, I will stop listening."),
        rule(VoiceAssistantIntent.GO_BACK,
            "go back", "back", "return", "retour", "go previous",
            "Going back."),
        rule(VoiceAssistantIntent.OPEN_DASHBOARD,
            "open dashboard", "go home", "open home", "go to dashboard", "accueil", "dashboard",
            "Opening the dashboard."),
        rule(VoiceAssistantIntent.OPEN_PROFILE,
            "open profile", "my profile", "show profile", "go to profile", "account profile", "mon profil",
            "Opening your profile."),
        rule(VoiceAssistantIntent.OPEN_SETTINGS,
            "open settings", "account settings", "show settings", "parametres", "settings",
            "Opening account settings."),
        rule(VoiceAssistantIntent.OPEN_SECURITY,
            "open security", "security settings", "go to security", "voice settings", "securite",
            "Opening security settings."),
        rule(VoiceAssistantIntent.OPEN_RESERVATIONS,
            "find tutor", "open reservations", "open reservation", "trouver un tuteur", "reservation",
            "Opening tutor discovery."),
        rule(VoiceAssistantIntent.OPEN_CALENDAR,
            "open calendar", "open sessions", "calendar", "calendrier", "seances",
            "Opening the calendar."),
        rule(VoiceAssistantIntent.OPEN_PLANNER,
            "open planner", "study planner", "revision planner", "planner",
            "Opening the planner."),
        rule(VoiceAssistantIntent.OPEN_MESSENGER,
            "open messenger", "open messages", "messages", "messagerie", "chat",
            "Opening messages."),
        rule(VoiceAssistantIntent.OPEN_QUIZ,
            "open quiz", "start quiz", "quiz",
            "Opening quiz."),
        rule(VoiceAssistantIntent.OPEN_BLOG,
            "open blog", "resources", "blog", "ressources",
            "Opening blog and resources."),
        rule(VoiceAssistantIntent.OPEN_ABOUT,
            "about", "open about", "about fahamni", "a propos",
            "Opening the about page."),
        rule(VoiceAssistantIntent.START_VOICE_LOGIN,
            "login with voice", "voice login", "connect with voice", "sign in with voice", "connexion vocale",
            "Starting voice login."),
        rule(VoiceAssistantIntent.ENROLL_VOICE_PASS,
            "record my voice", "save my voice", "activate voice pass", "enroll voice pass", "enable voice login",
            "Starting Voice Pass enrollment."),
        rule(VoiceAssistantIntent.REMOVE_VOICE_PASS,
            "remove voice pass", "delete voice pass", "disable voice login", "supprimer voice pass",
            "Removing Voice Pass."),
        rule(VoiceAssistantIntent.LOGOUT,
            "logout", "log out", "sign out", "deconnexion", "disconnect",
            "Logging out.")
    );

    public Interpretation interpret(String transcript, boolean waitingForCommand) {
        String normalized = normalize(transcript);
        if (normalized.isBlank()) {
            return unknown();
        }

        if (!waitingForCommand && matchesAny(normalized, WAKE_PHRASES) >= 0.72) {
            return new Interpretation(VoiceAssistantIntent.WAKE, 0.96, "I am listening.");
        }

        double bestScore = 0.0;
        Rule bestRule = null;
        for (Rule rule : RULES) {
            double score = matchesAny(normalized, rule.phrases());
            if (score > bestScore) {
                bestScore = score;
                bestRule = rule;
            }
        }

        if (bestRule == null || bestScore < 0.58) {
            return unknown();
        }

        return new Interpretation(bestRule.intent(), bestScore, bestRule.reply());
    }

    private static Rule rule(VoiceAssistantIntent intent, String... values) {
        if (values.length < 2) {
            throw new IllegalArgumentException("A voice rule needs at least one phrase and one reply.");
        }
        return new Rule(
            intent,
            List.of(values).subList(0, values.length - 1),
            values[values.length - 1]
        );
    }

    private double matchesAny(String transcript, List<String> phrases) {
        double bestScore = 0.0;
        for (String phrase : phrases) {
            bestScore = Math.max(bestScore, score(transcript, normalize(phrase)));
        }
        return bestScore;
    }

    private double score(String transcript, String phrase) {
        if (transcript.equals(phrase)) {
            return 1.0;
        }
        if (transcript.contains(phrase)) {
            return 0.94;
        }

        String[] words = phrase.split(" ");
        int matchedWords = 0;
        for (String word : words) {
            if (word.length() > 2 && transcript.contains(word)) {
                matchedWords++;
            }
        }

        double wordScore = words.length == 0 ? 0.0 : matchedWords / (double) words.length;
        double distanceScore = 1.0 - Math.min(1.0, levenshtein(transcript, phrase) / (double) Math.max(transcript.length(), phrase.length()));
        return Math.max(wordScore * 0.82, distanceScore * 0.72);
    }

    private int levenshtein(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int j = 0; j <= second.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int cost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                    Math.min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[second.length()];
    }

    private Interpretation unknown() {
        return new Interpretation(
            VoiceAssistantIntent.UNKNOWN,
            0.0,
            "I did not catch that. Say help to hear what I can do."
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return withoutAccents
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
