package tn.esprit.fahamni.utils;

import java.util.Locale;

public final class AppConfig {

    private static final String DEFAULT_MAILER_HOST = "smtp.gmail.com";
    private static final int DEFAULT_MAILER_PORT = 465;
    private static final String DEFAULT_GOOGLE_MAPS_REGION_CODE = "tn";
    private static final String DEFAULT_GOOGLE_MAPS_LANGUAGE_CODE = "fr";

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
        return getPropertyOrEnv("GEMINI_API_KEY", "");
    }

    public static boolean isGeminiConfigured() {
        return !getGeminiApiKey().isBlank();
    }

    public static String getGroqApiKey() {
        return getPropertyOrEnv("GROQ_API_KEY", "");
    }

    public static boolean isGroqConfigured() {
        return !getGroqApiKey().isBlank();
    }

    public static String getGoogleMapsApiKey() {
        return getPropertyOrEnv("GOOGLE_MAPS_API_KEY", "");
    }

    public static boolean isGoogleMapsConfigured() {
        return !getGoogleMapsApiKey().isBlank();
    }

    public static String getGoogleMapsRegionCode() {
        return getPropertyOrEnv("GOOGLE_MAPS_REGION_CODE", DEFAULT_GOOGLE_MAPS_REGION_CODE).toLowerCase(Locale.ROOT);
    }

    public static String getGoogleMapsLanguageCode() {
        return getPropertyOrEnv("GOOGLE_MAPS_LANGUAGE_CODE", DEFAULT_GOOGLE_MAPS_LANGUAGE_CODE).toLowerCase(Locale.ROOT);
    }

    private static String getPropertyOrEnv(String key, String fallback) {
        String prop = System.getProperty(key, "");
        if (!prop.isBlank()) {
            return prop.trim();
        }
        return getEnv(key, fallback);
    }

    private static String getEnv(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
