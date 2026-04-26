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

    public static String getFaceApiKey() {
        return getEnv("FACEPP_API_KEY", "");
    }

    public static String getFaceApiSecret() {
        return getEnv("FACEPP_API_SECRET", "");
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
        return getEnv("APP_SECRET", "");
    }

    public static boolean isAppSecretConfigured() {
        return !getAppSecret().isBlank();
    }

    private static String getEnv(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
