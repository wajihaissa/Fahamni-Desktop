package tn.esprit.fahamni.utils;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class LocalConfig {

    private static final Map<String, String> LOCAL_VALUES = loadLocalValues();

    private LocalConfig() {
    }

    public static String get(String key) {
        String value = normalize(System.getenv(key));
        if (value != null) {
            return value;
        }

        value = normalize(System.getProperty(key));
        if (value != null) {
            return value;
        }

        return normalize(LOCAL_VALUES.get(key));
    }

    private static Map<String, String> loadLocalValues() {
        Map<String, String> values = new LinkedHashMap<>();
        loadDotEnvFile(Path.of(".env.local"), values);
        loadPropertiesFile(Path.of("fahamni.local.properties"), values);
        return values;
    }

    private static void loadDotEnvFile(Path path, Map<String, String> target) {
        if (!Files.isRegularFile(path)) {
            return;
        }

        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring("export ".length()).trim();
                }

                int separatorIndex = trimmed.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                String key = trimmed.substring(0, separatorIndex).trim();
                String value = normalize(stripOptionalQuotes(trimmed.substring(separatorIndex + 1).trim()));
                if (!key.isEmpty() && value != null) {
                    target.putIfAbsent(key, value);
                }
            }
        } catch (IOException exception) {
            System.out.println("Local config ignored: " + path + " (" + exception.getMessage() + ")");
        }
    }

    private static void loadPropertiesFile(Path path, Map<String, String> target) {
        if (!Files.isRegularFile(path)) {
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            for (String key : properties.stringPropertyNames()) {
                String value = normalize(properties.getProperty(key));
                if (value != null) {
                    target.putIfAbsent(key, value);
                }
            }
        } catch (IOException exception) {
            System.out.println("Local config ignored: " + path + " (" + exception.getMessage() + ")");
        }
    }

    private static String stripOptionalQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }

        boolean quotedWithDoubleQuotes = value.startsWith("\"") && value.endsWith("\"");
        boolean quotedWithSingleQuotes = value.startsWith("'") && value.endsWith("'");
        return quotedWithDoubleQuotes || quotedWithSingleQuotes
            ? value.substring(1, value.length() - 1)
            : value;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
