package tn.esprit.fahamni.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EnvConfig {

    private static final String ENV_FILE_NAME = ".env";
    private static final Map<String, String> ENV_FILE_VALUES = loadEnvFile();

    private EnvConfig() {
    }

    public static String get(String key) {
        String systemValue = System.getenv(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }

        String fileValue = ENV_FILE_VALUES.get(key);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue.trim();
        }

        return null;
    }

    private static Map<String, String> loadEnvFile() {
        Map<String, String> values = new HashMap<>();
        Path envPath = Path.of(ENV_FILE_NAME);

        if (!Files.exists(envPath)) {
            return values;
        }

        try {
            List<String> lines = Files.readAllLines(envPath);
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();
                values.put(key, stripWrappingQuotes(value));
            }
        } catch (IOException ignored) {
            // If the .env file cannot be read, callers simply fall back to system env vars.
        }

        return values;
    }

    private static String stripWrappingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }

        boolean wrappedInDoubleQuotes = value.startsWith("\"") && value.endsWith("\"");
        boolean wrappedInSingleQuotes = value.startsWith("'") && value.endsWith("'");
        if (wrappedInDoubleQuotes || wrappedInSingleQuotes) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }
}
