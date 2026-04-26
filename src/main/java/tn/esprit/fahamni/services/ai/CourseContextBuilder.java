package tn.esprit.fahamni.services.ai;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.vosk.Model;
import org.vosk.Recognizer;
import tn.esprit.fahamni.entities.Matiere;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CourseContextBuilder {

    public static String FFMPEG_PATH = "ffmpeg";
    public static String VOSK_MODEL_FR = "src/main/resources/vosk-model-fr";
    public static String VOSK_MODEL_EN = "src/main/resources/vosk-model-en";

    private static final Path RESOURCES_ROOT = Path.of("src/main/resources").toAbsolutePath().normalize();
    private static final double TITLE_CONFIDENCE_THRESHOLD = 0.85;

    private static final String TYPE_PDF = "PDF";
    private static final String TYPE_VIDEO = "Vid\u00e9o";
    private static final String TYPE_LINK = "Lien";

    private final LanguageDetector languageDetector = LanguageDetectorBuilder
        .fromLanguages(Language.ENGLISH, Language.FRENCH)
        .build();

    public CompletableFuture<String> buildCourseContext(Matiere matiere) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder context = new StringBuilder();
            if (matiere == null || matiere.getStructure() == null || matiere.getStructure().isBlank()) {
                return context.toString();
            }

            try {
                JSONObject structure = new JSONObject(matiere.getStructure());
                JSONArray chapters = structure.optJSONArray("chapters");
                if (chapters == null) {
                    return context.toString();
                }

                for (int i = 0; i < chapters.length(); i++) {
                    JSONObject chapter = chapters.optJSONObject(i);
                    if (chapter == null) {
                        continue;
                    }

                    JSONArray sections = chapter.optJSONArray("sections");
                    if (sections == null) {
                        continue;
                    }

                    for (int j = 0; j < sections.length(); j++) {
                        JSONObject section = sections.optJSONObject(j);
                        if (section == null) {
                            continue;
                        }

                        JSONArray resources = section.optJSONArray("resources");
                        if (resources == null) {
                            continue;
                        }

                        for (int k = 0; k < resources.length(); k++) {
                            JSONObject resource = resources.optJSONObject(k);
                            if (resource == null) {
                                continue;
                            }

                            String type = resource.optString("type", "").trim();
                            String name = resource.optString("name", "Ressource sans nom").trim();
                            String path = resource.optString("path", "").trim();

                            try {
                                String extracted = extractResourceText(type, name, path);
                                if (extracted == null || extracted.isBlank()) {
                                    continue;
                                }

                                if (TYPE_PDF.equalsIgnoreCase(type)) {
                                    context.append("\n--- [PDF: ").append(name).append("] ---\n")
                                        .append(extracted).append("\n");
                                } else if (TYPE_LINK.equalsIgnoreCase(type)) {
                                    context.append("\n--- [Lien: ").append(name).append("] ---\n")
                                        .append(extracted).append("\n");
                                } else if (TYPE_VIDEO.equalsIgnoreCase(type)) {
                                    String lang = detectVideoLanguageTag(name, path);
                                    context.append("\n--- [Vid\u00e9o: ").append(name).append("] (").append(lang).append(") ---\n")
                                        .append(extracted).append("\n");
                                }
                            } catch (Exception e) {
                                System.err.println("Failed to ingest resource [" + name + "]: " + e.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to parse matiere structure JSON: " + e.getMessage());
            }

            return context.toString();
        });
    }

    private String extractResourceText(String type, String name, String path) throws Exception {
        if (TYPE_PDF.equalsIgnoreCase(type)) {
            return extractPdfText(path);
        }
        if (TYPE_LINK.equalsIgnoreCase(type)) {
            return extractWebText(path);
        }
        if (TYPE_VIDEO.equalsIgnoreCase(type)) {
            return transcribeVideo(name, path);
        }
        return "";
    }

    private String extractPdfText(String path) throws Exception {
        org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();

        if (isHttpPath(path)) {
            try (InputStream in = new BufferedInputStream(new URL(path).openStream());
                 org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(in)) {
                return stripper.getText(doc);
            }
        }

        Path pdfPath = resolveResourcePath(path);
        if (pdfPath == null || !Files.exists(pdfPath)) {
            throw new IllegalArgumentException("PDF file not found: " + path);
        }

        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdfPath.toFile())) {
            return stripper.getText(doc);
        }
    }

    private String extractWebText(String url) throws Exception {
        return Jsoup.connect(url)
            .timeout(10_000)
            .get()
            .body()
            .text();
    }

    private String transcribeVideo(String resourceName, String resourcePath) {
        if (!isFfmpegAvailable()) {
            System.err.println("FFmpeg not found at configured path: " + FFMPEG_PATH + ". Video transcription skipped.");
            return "";
        }

        File frModelFolder = new File(VOSK_MODEL_FR);
        File enModelFolder = new File(VOSK_MODEL_EN);

        File sampleWav = null;
        File fullWav = null;
        try {
            String selectedLanguage = detectLanguageByTitle(resourceName);
            String selectedModelPath = null;

            if ("fr".equals(selectedLanguage) && frModelFolder.exists()) {
                selectedModelPath = frModelFolder.getAbsolutePath();
            } else if ("en".equals(selectedLanguage) && enModelFolder.exists()) {
                selectedModelPath = enModelFolder.getAbsolutePath();
            } else {
                String inputForFfmpeg = resolveFfmpegInput(resourcePath);
                sampleWav = createTempWavFile("sample_");
                runFfmpeg(inputForFfmpeg, sampleWav.getAbsolutePath(), 30);

                String frText = frModelFolder.exists()
                    ? transcribeWav(sampleWav, frModelFolder.getAbsolutePath(), resourceName)
                    : "";
                String enText = enModelFolder.exists()
                    ? transcribeWav(sampleWav, enModelFolder.getAbsolutePath(), resourceName)
                    : "";

                double frScore = scoreTranscription(frText);
                double enScore = scoreTranscription(enText);

                if (frScore >= enScore) {
                    selectedModelPath = frModelFolder.exists() ? frModelFolder.getAbsolutePath() : null;
                } else {
                    selectedModelPath = enModelFolder.exists() ? enModelFolder.getAbsolutePath() : null;
                }
            }

            if (selectedModelPath == null) {
                if (!frModelFolder.exists()) {
                    System.err.println("Vosk model not found at: " + VOSK_MODEL_FR + ". Transcription skipped for: " + resourceName + ".");
                }
                if (!enModelFolder.exists()) {
                    System.err.println("Vosk model not found at: " + VOSK_MODEL_EN + ". Transcription skipped for: " + resourceName + ".");
                }
                return "";
            }

            String inputForFfmpeg = resolveFfmpegInput(resourcePath);
            fullWav = createTempWavFile("full_");
            runFfmpeg(inputForFfmpeg, fullWav.getAbsolutePath(), null);
            return transcribeWav(fullWav, selectedModelPath, resourceName);
        } catch (Exception e) {
            System.err.println("Video transcription failed for [" + resourceName + "]: " + e.getMessage());
            return "";
        } finally {
            safeDelete(sampleWav);
            safeDelete(fullWav);
        }
    }

    private String detectVideoLanguageTag(String resourceName, String resourcePath) {
        String byTitle = detectLanguageByTitle(resourceName);
        if (!"unknown".equals(byTitle)) {
            return byTitle;
        }

        File frModelFolder = new File(VOSK_MODEL_FR);
        File enModelFolder = new File(VOSK_MODEL_EN);
        if (!frModelFolder.exists() && !enModelFolder.exists()) {
            return "unknown";
        }

        if (!isFfmpegAvailable()) {
            return "unknown";
        }

        File sampleWav = null;
        try {
            String inputForFfmpeg = resolveFfmpegInput(resourcePath);
            sampleWav = createTempWavFile("lang_");
            runFfmpeg(inputForFfmpeg, sampleWav.getAbsolutePath(), 30);

            String frText = frModelFolder.exists()
                ? transcribeWav(sampleWav, frModelFolder.getAbsolutePath(), resourceName)
                : "";
            String enText = enModelFolder.exists()
                ? transcribeWav(sampleWav, enModelFolder.getAbsolutePath(), resourceName)
                : "";

            return scoreTranscription(frText) >= scoreTranscription(enText) ? "fr" : "en";
        } catch (Exception e) {
            return "unknown";
        } finally {
            safeDelete(sampleWav);
        }
    }

    private String detectLanguageByTitle(String title) {
        if (title == null || title.isBlank()) {
            return "unknown";
        }
        try {
            Map<Language, Double> confidenceMap = languageDetector.computeLanguageConfidenceValues(title);
            double frConfidence = confidenceMap.getOrDefault(Language.FRENCH, 0.0);
            double enConfidence = confidenceMap.getOrDefault(Language.ENGLISH, 0.0);
            if (frConfidence > TITLE_CONFIDENCE_THRESHOLD || enConfidence > TITLE_CONFIDENCE_THRESHOLD) {
                return frConfidence >= enConfidence ? "fr" : "en";
            }
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private double scoreTranscription(String text) {
        if (text == null) {
            return 0;
        }
        String cleaned = text.trim();
        if (cleaned.isEmpty()) {
            return 0;
        }
        int wordCount = cleaned.split("\\s+").length;
        long alphaChars = cleaned.chars().filter(Character::isLetter).count();
        double alphaRatio = cleaned.isEmpty() ? 0 : (double) alphaChars / cleaned.length();
        return wordCount + (alphaRatio * 50.0);
    }

    private String transcribeWav(File wavFile, String modelPath, String resourceName) {
        File modelFolder = new File(modelPath);
        if (!modelFolder.exists()) {
            System.err.println("Vosk model not found at: " + modelPath + ". Transcription skipped for: " + resourceName + ".");
            return "";
        }

        try (Model model = new Model(modelPath);
             AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile);
             Recognizer recognizer = new Recognizer(model, 16000f)) {

            byte[] buffer = new byte[4096];
            StringBuilder text = new StringBuilder();
            int bytesRead;

            while ((bytesRead = ais.read(buffer)) > 0) {
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    JSONObject chunk = new JSONObject(recognizer.getResult());
                    String partial = chunk.optString("text", "");
                    if (!partial.isBlank()) {
                        text.append(partial).append(" ");
                    }
                }
            }

            JSONObject finalResult = new JSONObject(recognizer.getFinalResult());
            String lastChunk = finalResult.optString("text", "");
            if (!lastChunk.isBlank()) {
                text.append(lastChunk);
            }

            return text.toString().trim();
        } catch (Exception e) {
            System.err.println("Transcription failed for resource [" + resourceName + "]: " + e.getMessage());
            return "";
        }
    }

    private void runFfmpeg(String inputPathOrUrl, String outputWavPath, Integer maxSeconds) throws Exception {
        ProcessBuilder pb;
        if (maxSeconds == null) {
            pb = new ProcessBuilder(
                FFMPEG_PATH, "-y",
                "-i", inputPathOrUrl,
                "-ar", "16000",
                "-ac", "1",
                "-f", "wav",
                "-acodec", "pcm_s16le",
                outputWavPath
            );
        } else {
            pb = new ProcessBuilder(
                FFMPEG_PATH, "-y",
                "-i", inputPathOrUrl,
                "-t", String.valueOf(maxSeconds),
                "-ar", "16000",
                "-ac", "1",
                "-f", "wav",
                "-acodec", "pcm_s16le",
                outputWavPath
            );
        }

        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] logs = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed with exit code " + exitCode + ": " + new String(logs, StandardCharsets.UTF_8));
        }
    }

    private boolean isFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(FFMPEG_PATH, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveFfmpegInput(String resourcePath) {
        if (isHttpPath(resourcePath)) {
            return resourcePath;
        }
        Path local = resolveResourcePath(resourcePath);
        if (local == null) {
            throw new IllegalArgumentException("Video file not found: " + resourcePath);
        }
        return local.toAbsolutePath().toString();
    }

    private Path resolveResourcePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }

        String normalized = rawPath.replace("\\", "/").trim();
        Path p = Path.of(normalized);
        if (p.isAbsolute() && Files.exists(p)) {
            return p.normalize();
        }

        Path inResources = normalized.startsWith("/")
            ? RESOURCES_ROOT.resolve(normalized.substring(1))
            : RESOURCES_ROOT.resolve(normalized);
        if (Files.exists(inResources)) {
            return inResources.normalize();
        }

        Path inProject = Path.of(System.getProperty("user.dir")).resolve(normalized).normalize();
        if (Files.exists(inProject)) {
            return inProject;
        }
        return null;
    }

    private boolean isHttpPath(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.trim().toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private File createTempWavFile(String prefix) throws Exception {
        return Files.createTempFile(prefix, ".wav").toFile();
    }

    private void safeDelete(File file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (Exception ignored) {
            // intentionally ignored
        }
    }
}
