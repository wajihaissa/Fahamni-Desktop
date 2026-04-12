package tn.esprit.fahamni.controllers;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import tn.esprit.fahamni.entities.Matiere;
import tn.esprit.fahamni.services.CategoryService;
import tn.esprit.fahamni.services.MatiereService;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.SceneManager;

public class BackofficeMatiereController implements Initializable {

    @FXML
    private VBox listView;

    @FXML
    private VBox editorView;

    @FXML
    private FlowPane cardsContainer;

    @FXML
    private TextField titreField;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private VBox courseBuilderContainer;

    @FXML
    private Label imagePathLabel;

    @FXML
    private Button gererCategoriesButton;

    @FXML
    private Button chooseImageButton;

    @FXML
    private FlowPane categoryTagsContainer;

    @FXML
    private ComboBox<String> existingCategoriesCombo;

    @FXML
    private TextField newCategoryField;

    private final MatiereService matiereService = new MatiereService();
    private final CategoryService categoryService = new CategoryService();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private String selectedImagePath;
    private Matiere selectedMatiere; // Tracks the matiere being edited (null = new matiere)

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadCategoriesIntoDropdown();
        refreshCards();
    }

    private void loadCategoriesIntoDropdown() {
        existingCategoriesCombo.getItems().clear();
        java.util.List<?> categories = categoryService.afficherList();
        for (Object cat : categories) {
            try {
                // Try to get name via reflection or direct method
                String categoryName = cat.getClass().getMethod("getName").invoke(cat).toString();
                existingCategoriesCombo.getItems().add(categoryName);
            } catch (Exception e) {
                System.err.println("Error loading category: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleSaveMatiere(ActionEvent event) {
        boolean isNew = (selectedMatiere == null);

        if (isNew) {
            selectedMatiere = new Matiere();
            selectedMatiere.setCreatedAt(LocalDateTime.now());
        }

        // 1. Flush standard text fields to the object
        selectedMatiere.setTitre(titreField.getText());
        selectedMatiere.setDescription(descriptionArea.getText());

        // 2. Flush the Course Builder UI to the JSON string
        selectedMatiere.setStructure(buildJsonFromUI());

        // 3. Set the cover image path
        selectedMatiere.setCoverImage(selectedImagePath);

        // 4. Save the entity to the Database
        if (isNew) {
            matiereService.add(selectedMatiere);
            // Note: If your ORM requires the Matiere to have an ID before saving many-to-many tags,
            // ensure the add() method updates the object's ID.
        } else {
            matiereService.update(selectedMatiere);
        }

        // 5. Flush the Tags to the database (linking them to this Matiere)
        saveCategoriesToMatiere(selectedMatiere);

        // 6. Update the App UI and return to the list
        refreshCards(); // Updates the grid with the new data
        showList();     // Swaps the view back to the cards
    }

    @FXML
    private void deleteMatiere(ActionEvent event) {
        String titleToDelete = titreField.getText();
        java.util.List<Matiere> allMatieres = matiereService.findAll();
        
        for (Matiere m : allMatieres) {
            if (m.getTitre().equals(titleToDelete)) {
                matiereService.delete(m);
                break;
            }
        }
        
        refreshCards();
        showList();
    }

    @FXML
    private void openCategories(ActionEvent event) {
        try {
            Node categoryView = SceneManager.loadView(
                Main.class,
                SceneManager.backofficeView("BackofficeCategoryView.fxml")
            );

            Node source = (Node) event.getSource();
            AnchorPane contentPane = (AnchorPane) source.getScene().lookup("#contentPane");
            if (contentPane == null) {
                return;
            }

            contentPane.getChildren().clear();
            AnchorPane.setTopAnchor(categoryView, 0.0);
            AnchorPane.setBottomAnchor(categoryView, 0.0);
            AnchorPane.setLeftAnchor(categoryView, 0.0);
            AnchorPane.setRightAnchor(categoryView, 0.0);
            contentPane.getChildren().add(categoryView);

            Label pageTitle = (Label) source.getScene().lookup("#pageTitle");
            if (pageTitle != null) {
                pageTitle.setText("Gestion des categories");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void chooseImage(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir une image");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );

        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            selectedImagePath = selectedFile.getAbsolutePath();
            imagePathLabel.setText(selectedImagePath);
        }
    }

    private void refreshCards() {
        cardsContainer.getChildren().clear();
        java.util.List<Matiere> matiereList = matiereService.findAll();
        for (Matiere matiere : matiereList) {
            cardsContainer.getChildren().add(createMatiereCard(matiere));
        }
    }

    private VBox createMatiereCard(Matiere matiere) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 4); -fx-pref-width: 280; -fx-pref-height: 320;");
        card.setPadding(new javafx.geometry.Insets(15));

        // Cover Image
        ImageView imageView = new ImageView();
        imageView.setFitWidth(250);
        imageView.setFitHeight(140);
        imageView.setPreserveRatio(true);
        
        if (matiere.getCoverImage() != null && !matiere.getCoverImage().isEmpty()) {
            try {
                Image image = new Image("file:" + matiere.getCoverImage());
                imageView.setImage(image);
            } catch (Exception e) {
                // Use a placeholder color region
                javafx.scene.layout.Region placeholder = new javafx.scene.layout.Region();
                placeholder.setStyle("-fx-background-color: #e5e7eb;");
                placeholder.setPrefWidth(250);
                placeholder.setPrefHeight(140);
                card.getChildren().add(placeholder);
            }
        } else {
            javafx.scene.layout.Region placeholder = new javafx.scene.layout.Region();
            placeholder.setStyle("-fx-background-color: #e5e7eb;");
            placeholder.setPrefWidth(250);
            placeholder.setPrefHeight(140);
            card.getChildren().add(placeholder);
        }
        
        if (imageView.getImage() != null) {
            card.getChildren().add(imageView);
        }

        // Title
        Label titleLabel = new Label(matiere.getTitre());
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16;");

        // Description
        Label descLabel = new Label(matiere.getDescription());
        descLabel.setWrapText(true);
        descLabel.setMaxHeight(60);

        // Action Button
        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button btnGerer = new Button("Gérer");
        btnGerer.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 16;");
        btnGerer.setOnAction(e -> {
            populateForm(matiere);
            showEditor();
        });
        
        actionBox.getChildren().add(btnGerer);

        card.getChildren().addAll(titleLabel, descLabel, actionBox);
        VBox.setVgrow(actionBox, Priority.ALWAYS);
        return card;
    }

    private void populateForm(Matiere matiere) {
        if (matiere == null) {
            return;
        }

        // Track the matiere being edited
        selectedMatiere = matiere;

        titreField.setText(matiere.getTitre());
        descriptionArea.setText(matiere.getDescription());
        selectedImagePath = matiere.getCoverImage();
        imagePathLabel.setText(selectedImagePath == null || selectedImagePath.isBlank() ? "Aucune image sélectionnée" : selectedImagePath);
        
        courseBuilderContainer.getChildren().clear();
        categoryTagsContainer.getChildren().clear();
        
        // Load tags/categories associated with this matiere from the database
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Object> matiereCategories = (java.util.List<Object>) matiere.getClass().getMethod("getCategories").invoke(matiere);
            if (matiereCategories != null) {
                for (Object cat : matiereCategories) {
                    String categoryName = cat.getClass().getMethod("getName").invoke(cat).toString();
                    addCategoryTag(categoryName);
                }
            }
        } catch (Exception e) {
            System.err.println("Could not load categories for matiere: " + e.getMessage());
        }
        
        if (matiere.getStructure() != null && !matiere.getStructure().isEmpty()) {
            loadJsonToUI(matiere.getStructure());
        }
    }

    private void clearInputs() {
        titreField.clear();
        descriptionArea.clear();
        courseBuilderContainer.getChildren().clear();
        selectedImagePath = null;
        imagePathLabel.setText("Aucune image s\u00e9lectionn\u00e9e");
    }

    private void showEditor() {
        listView.setVisible(false);
        listView.setManaged(false);
        editorView.setVisible(true);
        editorView.setManaged(true);
    }

    @FXML
    private void showList() {
        editorView.setVisible(false);
        editorView.setManaged(false);
        listView.setVisible(true);
        listView.setManaged(true);
        clearInputs();
        refreshCards();
    }

    @FXML
    private void handleCreateNew() {
        selectedMatiere = null; // Signal that we're creating a new matiere
        clearInputs();
        showEditor();
    }

    @FXML
    private void addChapterUI(ActionEvent event) {
        createChapterNode();
    }

    private void createChapterNode() {
        VBox chapterBox = new VBox(10);
        chapterBox.setStyle("-fx-border-color: #cbd5e1; -fx-border-radius: 5; -fx-padding: 10; -fx-background-color: white;");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        TextField chapterTitle = new TextField();
        chapterTitle.setPromptText("Titre du Chapitre");
        HBox.setHgrow(chapterTitle, Priority.ALWAYS);

        Button deleteChapBtn = new Button("X");
        deleteChapBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white;");
        deleteChapBtn.setOnAction(e -> courseBuilderContainer.getChildren().remove(chapterBox));

        header.getChildren().addAll(new Label("Chapitre:"), chapterTitle, deleteChapBtn);

        VBox sectionsContainer = new VBox(5);
        sectionsContainer.setStyle("-fx-padding: 0 0 0 20;");

        Button addSectionBtn = new Button("+ Section");
        addSectionBtn.setStyle("-fx-font-size: 10px; -fx-background-color: #e2e8f0;");
        addSectionBtn.setOnAction(e -> createSectionNode(sectionsContainer));

        chapterBox.getChildren().addAll(header, sectionsContainer, addSectionBtn);
        courseBuilderContainer.getChildren().add(chapterBox);
    }

    private void createSectionNode(VBox parentContainer) {
        VBox sectionBox = new VBox(5);
        sectionBox.setStyle("-fx-border-color: #e2e8f0; -fx-border-style: dashed; -fx-padding: 5;");

        HBox header = new HBox(5);
        header.setAlignment(Pos.CENTER_LEFT);
        TextField sectionTitle = new TextField();
        sectionTitle.setPromptText("Titre de la Section");
        HBox.setHgrow(sectionTitle, Priority.ALWAYS);

        Button deleteSecBtn = new Button("X");
        deleteSecBtn.setStyle("-fx-background-color: #f87171; -fx-text-fill: white; -fx-font-size: 10px;");
        deleteSecBtn.setOnAction(e -> parentContainer.getChildren().remove(sectionBox));

        header.getChildren().addAll(new Label("Section:"), sectionTitle, deleteSecBtn);

        Button addResourceBtn = new Button("+ Ressource");
        addResourceBtn.setStyle("-fx-font-size: 9px; -fx-background-color: #f1f5f9;");

        sectionBox.getChildren().addAll(header, addResourceBtn);
        parentContainer.getChildren().add(sectionBox);
    }

    @FXML
    private void addExistingCategory(ActionEvent event) {
        String category = existingCategoriesCombo.getValue();
        if (category != null && !category.isEmpty()) {
            addCategoryTag(category);
            existingCategoriesCombo.setValue(null);
        }
    }

    @FXML
    private void createNewCategory(ActionEvent event) {
        String newCategoryName = newCategoryField.getText().trim();
        if (!newCategoryName.isEmpty()) {
            try {
                // Create and save the new category to database
                Object newCategoryObj = Class.forName("tn.esprit.fahamni.Models.Category").getDeclaredConstructor().newInstance();
                newCategoryObj.getClass().getMethod("setName", String.class).invoke(newCategoryObj, newCategoryName);
                
                // Cast to Category and save
                tn.esprit.fahamni.Models.Category newCategory = (tn.esprit.fahamni.Models.Category) newCategoryObj;
                categoryService.ajouter(newCategory);
                
                // Add tag to UI
                addCategoryTag(newCategoryName);
                newCategoryField.clear();
                
                // Refresh dropdown to include the new category
                loadCategoriesIntoDropdown();
            } catch (ClassCastException ce) {
                System.err.println("Error: Created object is not a Category instance");
                // Fallback: just add the tag visually
                addCategoryTag(newCategoryName);
                newCategoryField.clear();
            } catch (Exception e) {
                System.err.println("Error creating and saving category: " + e.getMessage());
                // Fallback: just add the tag visually
                addCategoryTag(newCategoryName);
                newCategoryField.clear();
            }
        }
    }

    private void addCategoryTag(String categoryName) {
        HBox tag = new HBox(8);
        tag.setStyle("-fx-background-color: #e0e7ff; -fx-background-radius: 15; -fx-padding: 5 10; -fx-alignment: CENTER;");
        
        Label label = new Label(categoryName);
        Button removeBtn = new Button("×");
        removeBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 0; -fx-min-width: 20; -fx-min-height: 20;");
        removeBtn.setOnAction(e -> categoryTagsContainer.getChildren().remove(tag));
        
        tag.getChildren().addAll(label, removeBtn);
        categoryTagsContainer.getChildren().add(tag);
    }

    private void saveCategoriesToMatiere(Object matiere) {
        try {
            // Get all tags from the UI container
            java.util.List<String> tagLabels = new java.util.ArrayList<>();
            for (javafx.scene.Node node : categoryTagsContainer.getChildren()) {
                if (node instanceof HBox tagBox) {
                    for (javafx.scene.Node child : tagBox.getChildren()) {
                        if (child instanceof Label label && !label.getText().equals("×")) {
                            tagLabels.add(label.getText());
                        }
                    }
                }
            }
            
            // Create or get Category objects for each tag
            java.util.List<Object> categories = new java.util.ArrayList<>();
            java.util.List<?> allCategories = categoryService.afficherList();
            
            for (String tagLabel : tagLabels) {
                // Try to find existing category by name
                Object existingCategory = null;
                for (Object cat : allCategories) {
                    try {
                        String catName = (String) cat.getClass().getMethod("getName").invoke(cat);
                        if (catName.equals(tagLabel)) {
                            existingCategory = cat;
                            break;
                        }
                    } catch (Exception ignore) {
                        // continue searching
                    }
                }
                
                if (existingCategory != null) {
                    categories.add(existingCategory);
                }
            }
            
            // Set categories on matiere using reflection
            matiere.getClass().getMethod("setCategories", java.util.List.class).invoke(matiere, categories);
        } catch (Exception e) {
            System.err.println("Error saving categories to matiere: " + e.getMessage());
            // Non-critical error - continue with matiere save
        }
    }

    private String buildJsonFromUI() {
        StringBuilder json = new StringBuilder("{\"chapters\":[");
        
        int chapterCount = 0;
        for (Node node : courseBuilderContainer.getChildren()) {
            if (node instanceof VBox) {
                VBox chapterBox = (VBox) node;
                
                String chapterTitle = "";
                VBox sectionsContainer = null;
                
                // Extract chapter title from first HBox (header)
                for (Node childNode : chapterBox.getChildren()) {
                    if (childNode instanceof HBox) {
                        HBox headerBox = (HBox) childNode;
                        for (Node headerChild : headerBox.getChildren()) {
                            if (headerChild instanceof TextField) {
                                chapterTitle = ((TextField) headerChild).getText();
                            }
                        }
                    } else if (childNode instanceof VBox && sectionsContainer == null) {
                        // Assuming second VBox is the sections container
                        sectionsContainer = (VBox) childNode;
                    }
                }
                
                if (chapterCount > 0) json.append(",");
                json.append("{\"title\":\"").append(escapeJson(chapterTitle)).append("\",\"sections\":[");
                
                int sectionCount = 0;
                if (sectionsContainer != null) {
                    for (Node sectionNode : sectionsContainer.getChildren()) {
                        if (sectionNode instanceof VBox) {
                            VBox sectionBox = (VBox) sectionNode;
                            String sectionTitle = "";
                            
                            for (Node sectionChild : sectionBox.getChildren()) {
                                if (sectionChild instanceof HBox) {
                                    HBox sectionHeader = (HBox) sectionChild;
                                    for (Node sectionHeaderChild : sectionHeader.getChildren()) {
                                        if (sectionHeaderChild instanceof TextField) {
                                            sectionTitle = ((TextField) sectionHeaderChild).getText();
                                        }
                                    }
                                }
                            }
                            
                            if (sectionCount > 0) json.append(",");
                            json.append("{\"title\":\"").append(escapeJson(sectionTitle)).append("\"}");
                            sectionCount++;
                        }
                    }
                }
                
                json.append("]}");
                chapterCount++;
            }
        }
        
        json.append("]}");
        return json.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void loadJsonToUI(String json) {
        try {
            // Handle null or empty JSON
            if (json == null || json.trim().isEmpty()) {
                courseBuilderContainer.getChildren().clear();
                return;
            }
            
            // Simple manual parsing for JSON structure
            courseBuilderContainer.getChildren().clear();
            
            // Extract chapters
            int chaptersStart = json.indexOf("[");
            int chaptersEnd = json.lastIndexOf("]");
            if (chaptersStart < 0 || chaptersEnd < 0) {
                System.err.println("Warning: No valid JSON array found. Starting with empty builder.");
                return;
            }
            
            String chaptersContent = json.substring(chaptersStart + 1, chaptersEnd);
            String[] chapters = splitSafely(chaptersContent, "},{");
            
            for (String chapter : chapters) {
                chapter = chapter.trim().replaceAll("[{}\\[\\]]", "");
                if (chapter.isEmpty()) continue;
                
                // Extract chapter title
                int titleStart = chapter.indexOf("\"title\":\"") + 9;
                int titleEnd = chapter.indexOf("\"", titleStart);
                String chapterTitle = titleEnd > titleStart ? unescapeJson(chapter.substring(titleStart, titleEnd)) : "";
                
                createChapterNode();
                VBox lastChapter = (VBox) courseBuilderContainer.getChildren().get(courseBuilderContainer.getChildren().size() - 1);
                
                // Set chapter title
                for (Node node : lastChapter.getChildren()) {
                    if (node instanceof HBox) {
                        HBox header = (HBox) node;
                        for (Node headerChild : header.getChildren()) {
                            if (headerChild instanceof TextField) {
                                ((TextField) headerChild).setText(chapterTitle);
                            }
                        }
                    } else if (node instanceof VBox) {
                        // Extract and create sections
                        VBox sectionsContainer = (VBox) node;
                        int sectionsStart = chapter.indexOf("[");
                        int sectionsEnd = chapter.lastIndexOf("]");
                        if (sectionsStart > 0 && sectionsEnd > sectionsStart) {
                            String sectionsContent = chapter.substring(sectionsStart + 1, sectionsEnd);
                            String[] sections = splitSafely(sectionsContent, "},{");
                            
                            for (String section : sections) {
                                section = section.trim().replaceAll("[{}\\[\\]]", "");
                                if (section.isEmpty()) continue;
                                
                                int secTitleStart = section.indexOf("\"title\":\"") + 9;
                                int secTitleEnd = section.indexOf("\"", secTitleStart);
                                String sectionTitle = secTitleEnd > secTitleStart ? unescapeJson(section.substring(secTitleStart, secTitleEnd)) : "";
                                
                                createSectionNode(sectionsContainer);
                                VBox lastSection = (VBox) sectionsContainer.getChildren().get(sectionsContainer.getChildren().size() - 1);
                                
                                // Set section title
                                for (Node secNode : lastSection.getChildren()) {
                                    if (secNode instanceof HBox) {
                                        HBox secHeader = (HBox) secNode;
                                        for (Node secHeaderChild : secHeader.getChildren()) {
                                            if (secHeaderChild instanceof TextField) {
                                                ((TextField) secHeaderChild).setText(sectionTitle);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (StringIndexOutOfBoundsException e) {
            // Likely legacy or malformed JSON format
            System.err.println("Warning: JSON format incompatible with current parser. Detected legacy or malformed data. Starting with empty builder.");
            System.err.println("Details: " + e.getMessage());
            courseBuilderContainer.getChildren().clear();
        } catch (Exception e) {
            // Catch all other parsing errors gracefully
            System.err.println("Warning: Error parsing JSON structure (may be legacy format): " + e.getMessage());
            System.err.println("Starting with empty builder. You can rebuild the course structure manually.");
            courseBuilderContainer.getChildren().clear();
        }
    }

    private String[] splitSafely(String str, String delimiter) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            
            if (str.startsWith(delimiter, i) && depth == 0) {
                parts.add(current.toString());
                current = new StringBuilder();
                i += delimiter.length() - 1;
            } else {
                current.append(c);
            }
        }
        
        parts.add(current.toString());
        return parts.toArray(new String[0]);
    }

    private String unescapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\\\", "\\");
    }
}
