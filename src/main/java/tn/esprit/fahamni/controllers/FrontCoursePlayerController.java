package tn.esprit.fahamni.controllers;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import tn.esprit.fahamni.Models.Category;
import tn.esprit.fahamni.entities.Matiere;
import tn.esprit.fahamni.services.CategoryService;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.SceneManager;
import tn.esprit.fahamni.utils.ViewNavigator;

public class FrontCoursePlayerController {

    @FXML
    private Label courseTitleLabel;

    @FXML
    private Label courseDescriptionLabel;

    @FXML
    private FlowPane courseCategoriesContainer;

    @FXML
    private VBox chaptersContainer;

    private final CategoryService categoryService = new CategoryService();
    private Matiere currentMatiere;

    public void setMatiere(Matiere matiere) {
        this.currentMatiere = matiere;
        renderCourse();
    }

    @FXML
    private void goBackToCourses(ActionEvent event) {
        try {
            Node coursesView = SceneManager.loadView(
                Main.class,
                SceneManager.frontofficeView("FrontMatiereView.fxml")
            );

            ViewNavigator navigator = ViewNavigator.getInstance();
            AnchorPane contentPane = navigator.getContentPane();
            
            if (contentPane == null) {
                System.err.println("ERROR: ContentPane is null when going back to courses!");
                showError("Le système de navigation n'est pas correctement initialisé.", null);
                return;
            }

            contentPane.getChildren().clear();
            AnchorPane.setTopAnchor(coursesView, 0.0);
            AnchorPane.setBottomAnchor(coursesView, 0.0);
            AnchorPane.setLeftAnchor(coursesView, 0.0);
            AnchorPane.setRightAnchor(coursesView, 0.0);
            contentPane.getChildren().add(coursesView);

            Label pageTitle = navigator.getPageTitle();
            if (pageTitle != null) {
                pageTitle.setText("Cours");
            }
        } catch (Exception e) {
            System.err.println("Error going back to courses: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            showError("Impossible de revenir a la liste des cours: " + e.getMessage(), e);
        }
    }

    private void renderCourse() {
        if (currentMatiere == null) {
            return;
        }

        courseTitleLabel.setText(currentMatiere.getTitre() == null || currentMatiere.getTitre().isBlank()
            ? "Cours sans titre"
            : currentMatiere.getTitre());
        courseDescriptionLabel.setText(currentMatiere.getDescription() == null || currentMatiere.getDescription().isBlank()
            ? "Aucune description disponible pour ce cours."
            : currentMatiere.getDescription());

        renderCategoryChips();
        renderChapters();
    }

    private void renderCategoryChips() {
        courseCategoriesContainer.getChildren().clear();
        if (currentMatiere == null || currentMatiere.getId() <= 0) {
            return;
        }

        try {
            if (categoryService == null) {
                System.err.println("CategoryService is null - skipping category loading");
                return;
            }
            
            List<Category> categories = categoryService.findByMatiereId(currentMatiere.getId());
            for (Category category : categories) {
                Label chip = new Label(category.getName());
                chip.setStyle("-fx-background-color: #e0e7ff; "
                    + "-fx-background-radius: 12; "
                    + "-fx-padding: 4 10; "
                    + "-fx-text-fill: #3730a3;");
                courseCategoriesContainer.getChildren().add(chip);
            }
        } catch (Exception e) {
            System.err.println("Error loading categories: " + e.getMessage());
            e.printStackTrace();
            // Don't show error alert - categories are optional
        }
    }

    private void renderChapters() {
        chaptersContainer.getChildren().clear();

        List<ChapterNode> chapters = parseCourseStructure(currentMatiere.getStructure());
        if (chapters.isEmpty()) {
            Label empty = new Label("Ce cours ne contient pas encore de chapitres, sections ou ressources.");
            empty.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-padding: 14; -fx-text-fill: #475569;");
            chaptersContainer.getChildren().add(empty);
            return;
        }

        int chapterIndex = 1;
        for (ChapterNode chapter : chapters) {
            VBox chapterCard = new VBox(10);
            chapterCard.setStyle("-fx-background-color: white; "
                + "-fx-background-radius: 12; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 8, 0, 0, 3); "
                + "-fx-padding: 14;");

            Label chapterTitle = new Label("Chapitre " + chapterIndex + ": " + safe(chapter.title, "Sans titre"));
            chapterTitle.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
            chapterCard.getChildren().add(chapterTitle);

            if (chapter.sections.isEmpty()) {
                Label noSection = new Label("Aucune section.");
                noSection.setStyle("-fx-text-fill: #64748b;");
                chapterCard.getChildren().add(noSection);
            } else {
                int sectionIndex = 1;
                for (SectionNode section : chapter.sections) {
                    VBox sectionBox = new VBox(8);
                    sectionBox.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-padding: 10;");

                    Label sectionTitle = new Label("Section " + sectionIndex + ": " + safe(section.title, "Sans titre"));
                    sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
                    sectionBox.getChildren().add(sectionTitle);

                    if (section.resources.isEmpty()) {
                        Label noRes = new Label("Aucune ressource.");
                        noRes.setStyle("-fx-text-fill: #64748b;");
                        sectionBox.getChildren().add(noRes);
                    } else {
                        for (ResourceNode resource : section.resources) {
                            sectionBox.getChildren().add(createResourceRow(resource));
                        }
                    }

                    chapterCard.getChildren().add(sectionBox);
                    sectionIndex++;
                }
            }

            chaptersContainer.getChildren().add(chapterCard);
            chapterIndex++;
        }
    }

    private HBox createResourceRow(ResourceNode resource) {
        HBox row = new HBox(10);
        row.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-padding: 8;");

        Label nameLabel = new Label(safe(resource.name, "Ressource"));
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label typeLabel = new Label("[" + safe(resource.type, "Fichier") + "]");
        typeLabel.setStyle("-fx-text-fill: #475569;");

        Label pathLabel = new Label(safe(resource.path, ""));
        pathLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        pathLabel.setWrapText(true);
        pathLabel.setMaxWidth(380);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openBtn = new Button("Ouvrir");
        openBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold;");
        openBtn.setOnAction(e -> openResource(resource));

        row.getChildren().addAll(nameLabel, typeLabel, pathLabel, spacer, openBtn);
        return row;
    }

    private void openResource(ResourceNode resource) {
        try {
            String path = resource.path == null ? "" : resource.path.trim();
            if (path.isEmpty()) {
                showWarning("Cette ressource n'a pas de chemin defini.");
                return;
            }

            if (path.startsWith("http://") || path.startsWith("https://") || "lien".equalsIgnoreCase(resource.type)) {
                Desktop.getDesktop().browse(new URI(path));
                return;
            }

            File resolvedFile = resolveResourceFile(path);
            if (resolvedFile == null || !resolvedFile.exists()) {
                showWarning("Fichier introuvable: " + path);
                return;
            }

            Desktop.getDesktop().open(resolvedFile);
        } catch (Exception e) {
            showError("Impossible d'ouvrir la ressource.", e);
        }
    }

    private File resolveResourceFile(String path) {
        File direct = new File(path);
        if (direct.exists()) {
            return direct;
        }

        String normalizedPath = path.replace("\\", "/");
        String resourcesBase = System.getProperty("user.dir") + "/src/main/resources";

        File resourceFile = normalizedPath.startsWith("/")
            ? new File(resourcesBase + normalizedPath)
            : new File(resourcesBase, normalizedPath);
        if (resourceFile.exists()) {
            return resourceFile;
        }

        File projectRelative = new File(System.getProperty("user.dir"), normalizedPath);
        return projectRelative.exists() ? projectRelative : null;
    }

    private List<ChapterNode> parseCourseStructure(String json) {
        List<ChapterNode> chapters = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return chapters;
        }

        try {
            String chaptersArray = extractArrayField(json, "chapters");
            for (String chapterObject : splitTopLevelObjects(chaptersArray)) {
                ChapterNode chapter = new ChapterNode();
                chapter.title = extractStringField(chapterObject, "title");

                String sectionsArray = extractArrayField(chapterObject, "sections");
                for (String sectionObject : splitTopLevelObjects(sectionsArray)) {
                    SectionNode section = new SectionNode();
                    section.title = extractStringField(sectionObject, "title");

                    String resourcesArray = extractArrayField(sectionObject, "resources");
                    for (String resourceObject : splitTopLevelObjects(resourcesArray)) {
                        ResourceNode resource = new ResourceNode();
                        resource.type = extractStringField(resourceObject, "type");
                        resource.name = extractStringField(resourceObject, "name");
                        resource.path = extractStringField(resourceObject, "path");
                        section.resources.add(resource);
                    }

                    chapter.sections.add(section);
                }

                chapters.add(chapter);
            }
        } catch (Exception e) {
            System.err.println("Could not parse course structure JSON: " + e.getMessage());
        }

        return chapters;
    }

    private String extractArrayField(String jsonObject, String fieldName) {
        int keyIndex = jsonObject.indexOf("\"" + fieldName + "\"");
        if (keyIndex < 0) {
            return "";
        }

        int arrayStart = jsonObject.indexOf('[', keyIndex);
        if (arrayStart < 0) {
            return "";
        }

        int arrayEnd = findMatchingBracket(jsonObject, arrayStart, '[', ']');
        if (arrayEnd < 0) {
            return "";
        }

        return jsonObject.substring(arrayStart + 1, arrayEnd);
    }

    private String extractStringField(String jsonObject, String fieldName) {
        int keyIndex = jsonObject.indexOf("\"" + fieldName + "\"");
        if (keyIndex < 0) {
            return "";
        }

        int colonIndex = jsonObject.indexOf(':', keyIndex);
        if (colonIndex < 0) {
            return "";
        }

        int firstQuote = jsonObject.indexOf('"', colonIndex + 1);
        if (firstQuote < 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = firstQuote + 1; i < jsonObject.length(); i++) {
            char c = jsonObject.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n': builder.append('\n'); break;
                    case 'r': builder.append('\r'); break;
                    case 't': builder.append('\t'); break;
                    case '\\': builder.append('\\'); break;
                    case '"': builder.append('"'); break;
                    default: builder.append(c); break;
                }
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                break;
            }

            builder.append(c);
        }

        return builder.toString();
    }

    private List<String> splitTopLevelObjects(String arrayContent) {
        List<String> objects = new ArrayList<>();
        if (arrayContent == null || arrayContent.isBlank()) {
            return objects;
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int startIndex = -1;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{') {
                if (depth == 0) {
                    startIndex = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && startIndex >= 0) {
                    objects.add(arrayContent.substring(startIndex, i + 1));
                    startIndex = -1;
                }
            }
        }

        return objects;
    }

    private int findMatchingBracket(String value, int openIndex, char openChar, char closeChar) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = openIndex; i < value.length(); i++) {
            char c = value.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }

            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Ressource");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message, Exception e) {
        if (e != null) {
            e.printStackTrace();
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static class ChapterNode {
        private String title;
        private final List<SectionNode> sections = new ArrayList<>();
    }

    private static class SectionNode {
        private String title;
        private final List<ResourceNode> resources = new ArrayList<>();
    }

    private static class ResourceNode {
        private String type;
        private String name;
        private String path;
    }
}
