package tn.esprit.fahamni.utils;

import java.util.Locale;

public final class AppConfig {

    private static final String DEFAULT_MAILER_HOST = "smtp.gmail.com";
    private static final int DEFAULT_MAILER_PORT = 465;
    private static final String DEFAULT_GOOGLE_MAPS_REGION_CODE = "tn";
    private static final String DEFAULT_GOOGLE_MAPS_LANGUAGE_CODE = "fr";
    private static final String DEFAULT_GROQ_KEY =
        "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";

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
        String configured = getPropertyOrEnv("GROQ_API_KEY", "");
        return !configured.isBlank() ? configured : DEFAULT_GROQ_KEY;
    }

    public static boolean isGroqConfigured() {
        return !getGroqApiKey().isBlank();
    }

    public static String getFaceApiKey() {
        return getEnv("FACEPP_API_KEY", "zJ4uLBQ77Y3sN8YLj_X9OtH-mwXsBe67");
    }

    public static String getFaceApiSecret() {
        return getEnv("FACEPP_API_SECRET", "X3uNLB7q2V_Vty3uFmpaJtGazC5sQ4qg");
    }

    public static String getFaceApiBaseUrl() {
        return getEnv("FACEPP_API_BASE_URL", "https://api-us.faceplusplus.com");
    }

    public static boolean isFaceConfigured() {
        return !getFaceApiKey().isBlank()
            && !getFaceApiSecret().isBlank()
            && !getFaceApiBaseUrl().isBlank();
    }

    public static String getAppSecret() {
        return getEnv("APP_SECRET", "fahamni_desktop_super_secret_2026_2fa_encrypt_key");
    }

    public static boolean isAppSecretConfigured() {
        return !getAppSecret().isBlank();
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
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank() && !prop.startsWith("${")) {
            return prop.trim();
        }

        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        return fallback;
    }
}
