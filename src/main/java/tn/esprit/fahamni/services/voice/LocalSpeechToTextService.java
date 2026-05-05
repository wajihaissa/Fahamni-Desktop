package tn.esprit.fahamni.services.voice;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalSpeechToTextService {

    private static final float SAMPLE_RATE = 16_000.0f;
    private static final int BUFFER_SIZE = 4096;
    private static final Path DEFAULT_MODEL_PATH = Path.of("models", "vosk-model");
    private static final Pattern TEXT_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");
    private static volatile boolean logLevelConfigured;

    private final Object modelLock = new Object();
    private AutoCloseable cachedModel;
    private Path cachedModelPath;

    public record SpeechResult(
        boolean success,
        String transcript,
        String message
    ) {
    }

    public SpeechResult listenOnce(Duration duration) {
        return listenOnce(duration, List.of());
    }

    public SpeechResult listenOnce(Duration duration, List<String> grammarPhrases) {
        if (!isVoskPresent()) {
            return new SpeechResult(false, "", "Vosk is not installed in this project yet.");
        }
        configureVoskLogLevel();
        Optional<Path> modelPath = resolveModelPath();
        if (modelPath.isEmpty()) {
            return new SpeechResult(false, "", "Missing offline speech model: " + DEFAULT_MODEL_PATH);
        }

        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            return new SpeechResult(false, "", "No compatible microphone was found.");
        }

        try {
            Class<?> modelClass = Class.forName("org.vosk.Model");
            Class<?> recognizerClass = Class.forName("org.vosk.Recognizer");
            Method acceptWaveForm = recognizerClass.getMethod("acceptWaveForm", byte[].class, int.class);
            Method finalResult = recognizerClass.getMethod("getFinalResult");

            AutoCloseable model = getOrLoadModel(modelClass, modelPath.get());
            try (AutoCloseable recognizer = createRecognizer(recognizerClass, modelClass, model, grammarPhrases);
                 TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info)) {

                line.open(format);
                line.start();
                byte[] buffer = new byte[BUFFER_SIZE];
                long endAt = System.currentTimeMillis() + Math.max(1_500L, duration.toMillis());
                while (System.currentTimeMillis() < endAt) {
                    int read = line.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        acceptWaveForm.invoke(recognizer, buffer, read);
                    }
                }
                line.stop();

                String resultJson = String.valueOf(finalResult.invoke(recognizer));
                String transcript = extractText(resultJson);
                if (transcript.isBlank()) {
                    return new SpeechResult(false, "", "I did not hear a command.");
                }
                return new SpeechResult(true, transcript, "Heard: " + transcript);
            }
        } catch (Exception exception) {
            return new SpeechResult(false, "", "Offline listening failed: " + exception.getMessage());
        }
    }

    private AutoCloseable createRecognizer(Class<?> recognizerClass, Class<?> modelClass, AutoCloseable model, List<String> grammarPhrases) throws Exception {
        List<String> safeGrammar = grammarPhrases == null ? List.of() : grammarPhrases.stream()
            .filter(phrase -> phrase != null && !phrase.isBlank())
            .distinct()
            .toList();
        if (safeGrammar.isEmpty()) {
            Constructor<?> recognizerConstructor = recognizerClass.getConstructor(modelClass, float.class);
            return (AutoCloseable) recognizerConstructor.newInstance(model, SAMPLE_RATE);
        }

        Constructor<?> grammarConstructor = recognizerClass.getConstructor(modelClass, float.class, String.class);
        return (AutoCloseable) grammarConstructor.newInstance(model, SAMPLE_RATE, toGrammarJson(safeGrammar));
    }

    private String toGrammarJson(List<String> phrases) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < phrases.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(escapeJson(phrases.get(i))).append('"');
        }
        builder.append(",\"[unk]\"]");
        return builder.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private AutoCloseable getOrLoadModel(Class<?> modelClass, Path modelPath) throws Exception {
        synchronized (modelLock) {
            Path normalizedPath = modelPath.toAbsolutePath().normalize();
            if (cachedModel != null && normalizedPath.equals(cachedModelPath)) {
                return cachedModel;
            }
            if (cachedModel != null) {
                cachedModel.close();
                cachedModel = null;
            }
            Constructor<?> modelConstructor = modelClass.getConstructor(String.class);
            cachedModel = (AutoCloseable) modelConstructor.newInstance(modelPath.toString());
            cachedModelPath = normalizedPath;
            return cachedModel;
        }
    }

    private void configureVoskLogLevel() {
        if (logLevelConfigured) {
            return;
        }
        synchronized (LocalSpeechToTextService.class) {
            if (logLevelConfigured) {
                return;
            }
            try {
                Class<?> libVoskClass = Class.forName("org.vosk.LibVosk");
                Method setLogLevel = libVoskClass.getMethod("vosk_set_log_level", int.class);
                setLogLevel.invoke(null, -1);
            } catch (Exception ignored) {
            }
            logLevelConfigured = true;
        }
    }

    private boolean isVoskPresent() {
        try {
            Class.forName("org.vosk.Model");
            Class.forName("org.vosk.Recognizer");
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private Optional<Path> resolveModelPath() {
        if (isVoskModelDirectory(DEFAULT_MODEL_PATH)) {
            return Optional.of(DEFAULT_MODEL_PATH);
        }
        if (!Files.isDirectory(DEFAULT_MODEL_PATH)) {
            return Optional.empty();
        }
        try (var children = Files.list(DEFAULT_MODEL_PATH)) {
            return children
                .filter(this::isVoskModelDirectory)
                .findFirst();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private boolean isVoskModelDirectory(Path path) {
        return Files.isDirectory(path)
            && Files.isDirectory(path.resolve("am"))
            && Files.isDirectory(path.resolve("conf"))
            && Files.isDirectory(path.resolve("graph"))
            && Files.isDirectory(path.resolve("ivector"));
    }

    private String extractText(String json) {
        if (json == null) {
            return "";
        }
        Matcher matcher = TEXT_PATTERN.matcher(json);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).replace("\\\"", "\"").trim();
    }
}
