package tn.esprit.fahamni.room3d;

import com.jme3.asset.AssetManager;
import com.jme3.export.Savable;
import com.jme3.export.binary.BinaryImporter;
import com.jme3.export.binary.BinaryExporter;
import com.jme3.scene.Spatial;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

public class Room3DExportService {

    private static final DateTimeFormatter EXPORT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String DEFAULT_EXPORT_FOLDER = "exports";
    private static final String ROOM3D_EXPORT_FOLDER = "room3d";

    public Path exportSceneAsJ3o(Spatial spatial, Room3DPreviewData previewData) {
        Objects.requireNonNull(spatial, "spatial");

        Path exportDirectory = getExportDirectory();
        try {
            Files.createDirectories(exportDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Export .j3o impossible : creation du dossier d'export echouee.",
                exception
            );
        }

        Path exportPath = exportDirectory.resolve(buildExportFileName(previewData));
        try {
            BinaryExporter.getInstance().save(spatial, exportPath.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Export .j3o impossible : ecriture du fichier echouee.",
                exception
            );
        }

        return exportPath.toAbsolutePath().normalize();
    }

    public Spatial loadSceneFromJ3o(Path exportPath, AssetManager assetManager) {
        Objects.requireNonNull(exportPath, "exportPath");
        Objects.requireNonNull(assetManager, "assetManager");

        Path normalizedExportPath = exportPath.toAbsolutePath().normalize();
        if (!Files.exists(normalizedExportPath) || !Files.isRegularFile(normalizedExportPath)) {
            throw new IllegalArgumentException("Le fichier .j3o selectionne est introuvable.");
        }
        if (!normalizedExportPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".j3o")) {
            throw new IllegalArgumentException("Selectionnez un fichier .j3o valide.");
        }

        try (InputStream inputStream = Files.newInputStream(normalizedExportPath)) {
            BinaryImporter importer = BinaryImporter.getInstance();
            importer.setAssetManager(assetManager);
            Savable savable = importer.load(inputStream);
            if (savable instanceof Spatial spatial) {
                return spatial;
            }
            throw new IllegalStateException("Le fichier .j3o ne contient pas de scene 3D exploitable.");
        } catch (IOException exception) {
            throw new IllegalStateException("Ouverture .j3o impossible : lecture du fichier echouee.", exception);
        }
    }

    public Path getExportDirectory() {
        String workingDirectory = System.getProperty("user.dir", ".");
        return Path.of(workingDirectory, DEFAULT_EXPORT_FOLDER, ROOM3D_EXPORT_FOLDER);
    }

    private String buildExportFileName(Room3DPreviewData previewData) {
        String roomSegment = previewData == null ? "salle-3d" : sanitizeFileSegment(previewData.roomName());
        String modeSegment = previewData == null ? "preview" : resolveModeSegment(previewData);
        String timestampSegment = LocalDateTime.now().format(EXPORT_TIMESTAMP_FORMATTER);
        return roomSegment + "-" + modeSegment + "-" + timestampSegment + ".j3o";
    }

    private String resolveModeSegment(Room3DPreviewData previewData) {
        if (previewData.isDesignReview()) {
            return "design-review";
        }
        if (previewData.supportsSeatSelection()) {
            return "selection";
        }
        return "preview";
    }

    private String sanitizeFileSegment(String value) {
        if (value == null || value.isBlank()) {
            return "salle-3d";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");

        return normalized.isBlank() ? "salle-3d" : normalized;
    }
}
