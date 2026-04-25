package tn.esprit.fahamni.utils;

public final class AppConfig {

    private static final String DEFAULT_MAILER_HOST = "smtp.gmail.com";
    private static final int DEFAULT_MAILER_PORT = 465;

    private AppConfig() {
    }

    public static String getMailerHost() {
        return getEnv("MAILER_HOST", DEFAULT_MAILER_HOST);
    }

    public static int getMailerPort() {
        String raw = getEnv("MAILER_PORT", String.valueOf(DEFAULT_MAILER_PORT));
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return DEFAULT_MAILER_PORT;
        }
    }

    public static String getMailerUsername() {
        return getEnv("MAILER_USERNAME", "");
    }

    public static String getMailerPassword() {
        return getEnv("MAILER_PASSWORD", "");
    }

    public static String getMailerFrom() {
        String configured = getEnv("MAILER_FROM", "");
        if (!configured.isBlank()) {
            return configured;
        }
        return getMailerUsername();
    }

    public static boolean isMailConfigured() {
        return !getMailerHost().isBlank()
            && getMailerPort() > 0
            && !getMailerUsername().isBlank()
            && !getMailerPassword().isBlank()
            && !getMailerFrom().isBlank();
    }

    public static String getGeminiApiKey() {
        String prop = System.getProperty("GEMINI_API_KEY", "");
        if (!prop.isBlank()) return prop;
        return getEnv("GEMINI_API_KEY", "");
    }

    public static boolean isGeminiConfigured() {
        return !getGeminiApiKey().isBlank();
    }

    private static final String DEFAULT_GROQ_KEY =
        "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";

    public static String getGroqApiKey() {
        String prop = System.getProperty("GROQ_API_KEY", "");
        if (!prop.isBlank()) return prop;
        String env = getEnv("GROQ_API_KEY", "");
        if (!env.isBlank()) return env;
        return DEFAULT_GROQ_KEY;
    }

    public static boolean isGroqConfigured() {
        return !getGroqApiKey().isBlank();
    }

    private static String getEnv(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
