package tn.esprit.fahamni.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class WebAssetBridge {

    private static final String DEFAULT_APP_BASE_URL = "http://127.0.0.1:8000";
    private static final String SYMFONY_PUBLIC_DIR_KEY = "SYMFONY_PUBLIC_DIR";
    private static final Path JAVA_RESOURCES_ROOT = Path.of("src/main/resources").toAbsolutePath().normalize();

    private WebAssetBridge() {
    }

    public static String getAppBaseUrl() {
        String configured = LocalConfig.get("APP_BASE_URL");
        return configured == null || configured.isBlank()
            ? DEFAULT_APP_BASE_URL
            : configured.replaceAll("/+$", "");
    }

    public static String buildPublicUrl(String relativePath) {
        String normalizedRelativePath = normalizeRelativePath(relativePath);
        return getAppBaseUrl() + "/" + normalizedRelativePath;
    }

    public static Path resolveSymfonyPublicDir() {
        Path configured = normalizeSymfonyPublicDir(LocalConfig.get(SYMFONY_PUBLIC_DIR_KEY));
        if (configured != null) {
            return configured;
        }

        Path currentProjectDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path parentDir = currentProjectDir.getParent();
        if (parentDir == null) {
            return null;
        }

        try (Stream<Path> siblings = Files.list(parentDir)) {
            return siblings
                .filter(Files::isDirectory)
                .map(WebAssetBridge::resolvePublicDirFromProjectRoot)
                .filter(WebAssetBridge::isSymfonyPublicDir)
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            System.err.println("Unable to auto-detect Symfony public directory: " + e.getMessage());
            return null;
        }
    }

    public static Path resolveSymfonyPublicFile(String relativePath) {
        Path publicDir = resolveSymfonyPublicDir();
        if (publicDir == null) {
            return null;
        }

        return publicDir.resolve(normalizeRelativePath(relativePath)).normalize();
    }

    public static Path resolveStoredPathToLocalFile(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }

        String trimmed = rawPath.trim();
        Path absolutePath = toExistingAbsolutePath(trimmed);
        if (absolutePath != null) {
            return absolutePath;
        }

        String publicRelativePath = extractPublicRelativePath(trimmed);
        if (publicRelativePath != null) {
            Path symfonyPublicFile = resolveSymfonyPublicFile(publicRelativePath);
            if (symfonyPublicFile != null && Files.exists(symfonyPublicFile)) {
                return symfonyPublicFile;
            }

            Path javaResourcesFile = JAVA_RESOURCES_ROOT.resolve(publicRelativePath).normalize();
            if (Files.exists(javaResourcesFile)) {
                return javaResourcesFile;
            }
        }

        String normalized = trimmed.replace("\\", "/");
        Path inJavaResources = normalized.startsWith("/")
            ? JAVA_RESOURCES_ROOT.resolve(normalized.substring(1))
            : JAVA_RESOURCES_ROOT.resolve(normalized);
        if (Files.exists(inJavaResources)) {
            return inJavaResources.normalize();
        }

        Path inProject = Path.of(System.getProperty("user.dir")).resolve(normalized).normalize();
        if (Files.exists(inProject)) {
            return inProject;
        }

        return null;
    }

    private static Path toExistingAbsolutePath(String rawPath) {
        try {
            Path candidate = Path.of(rawPath);
            if (candidate.isAbsolute() && Files.exists(candidate)) {
                return candidate.normalize();
            }
        } catch (Exception ignored) {
            // Non-local values simply fall through to URL/public-path resolution.
        }
        return null;
    }

    private static String extractPublicRelativePath(String rawPath) {
        String normalized = rawPath.replace("\\", "/").trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            try {
                URI uri = URI.create(normalized);
                String path = uri.getPath();
                if (path == null || path.isBlank()) {
                    return null;
                }
                return normalizeRelativePath(URLDecoder.decode(path, StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                return null;
            }
        }

        if (normalized.startsWith("/")) {
            return normalizeRelativePath(normalized);
        }

        if (normalized.startsWith("uploads/") || normalized.startsWith("images/")) {
            return normalizeRelativePath(normalized);
        }

        return null;
    }

    private static String normalizeRelativePath(String rawPath) {
        String normalized = rawPath == null ? "" : rawPath.trim().replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static Path normalizeSymfonyPublicDir(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }

        try {
            Path configured = Path.of(configuredPath).toAbsolutePath().normalize();
            if (isSymfonyPublicDir(configured)) {
                return configured;
            }

            Path nestedPublic = resolvePublicDirFromProjectRoot(configured);
            return isSymfonyPublicDir(nestedPublic) ? nestedPublic : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Path resolvePublicDirFromProjectRoot(Path projectRoot) {
        if (projectRoot == null) {
            return null;
        }

        Path directPublic = projectRoot.resolve("public");
        if (isSymfonyPublicDir(directPublic) && Files.isRegularFile(projectRoot.resolve("bin").resolve("console"))) {
            return directPublic;
        }

        Path parent = projectRoot.getParent();
        if (isSymfonyPublicDir(projectRoot)
            && parent != null
            && Files.isRegularFile(parent.resolve("bin").resolve("console"))) {
            return projectRoot;
        }

        return null;
    }

    private static boolean isSymfonyPublicDir(Path publicDir) {
        return publicDir != null
            && Files.isDirectory(publicDir)
            && Files.isRegularFile(publicDir.resolve("index.php"));
    }
}
