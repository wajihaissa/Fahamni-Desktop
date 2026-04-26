package tn.esprit.fahamni.controllers;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import tn.esprit.fahamni.Models.Category;
import tn.esprit.fahamni.entities.Matiere;
import tn.esprit.fahamni.services.CategoryService;
import tn.esprit.fahamni.services.MatiereService;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.SceneManager;
import tn.esprit.fahamni.utils.ViewNavigator;

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
    private final Map<String, Category> categoriesByName = new LinkedHashMap<>();
    private String selectedImagePath;
    private Matiere selectedMatiere; // Tracks the matiere being edited (null = new matiere)

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadCategoriesIntoDropdown();
        refreshCards();
    }

    private void loadCategoriesIntoDropdown() {
        existingCategoriesCombo.getItems().clear();
        categoriesByName.clear();

        for (Category category : categoryService.afficherList()) {
            if (category.getName() == null || category.getName().isBlank()) {
                continue;
            }
            categoriesByName.put(normalizeCategoryName(category.getName()), category);
            existingCategoriesCombo.getItems().add(category.getName());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void handleSaveMatiere(ActionEvent event) {
        try {
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

            // 4. Save the entity to the Database (may throw RuntimeException)
            if (isNew) {
                matiereService.add(selectedMatiere);
            } else {
                matiereService.update(selectedMatiere);
            }

            // 5. Flush the Tags to the database (linking them to this Matiere)
            saveCategoriesToMatiere(selectedMatiere);

            // 6. Success: Show confirmation and return to list
            showAlert(Alert.AlertType.INFORMATION, "Succès", "La matière a été enregistrée avec succès !");
            refreshCards(); // Updates the grid with the new data
            showList();     // Swaps the view back to the cards

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur d'enregistrement", 
                "Impossible de sauvegarder la matière : " + e.getMessage());
            // Note: Stay on the editor view so user doesn't lose their work
        }
    }

    private void deleteMatiere(Matiere matiere) {
        if (matiere == null) {
            return;
        }

        try {
            matiereService.delete(matiere);
            if (selectedMatiere != null && selectedMatiere.getId() == matiere.getId()) {
                selectedMatiere = null;
                clearInputs();
            }
            refreshCards();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur de suppression",
                "Impossible de supprimer la matiere : " + e.getMessage());
        }
    }

    @FXML
    private void openCategories(ActionEvent event) {
        try {
            Node categoryView = SceneManager.loadView(
                Main.class,
                SceneManager.backofficeView("BackofficeCategoryView.fxml")
            );

            ViewNavigator navigator = ViewNavigator.getInstance();
            AnchorPane contentPane = navigator.getContentPane();
            
            if (contentPane == null) {
                System.err.println("ERROR: ContentPane is null in openCategories!");
                showAlert(Alert.AlertType.ERROR, "Erreur de Navigation", 
                    "Le système de navigation n'est pas initialisé correctement.");
                return;
            }

            contentPane.getChildren().clear();
            AnchorPane.setTopAnchor(categoryView, 0.0);
            AnchorPane.setBottomAnchor(categoryView, 0.0);
            AnchorPane.setLeftAnchor(categoryView, 0.0);
            AnchorPane.setRightAnchor(categoryView, 0.0);
            contentPane.getChildren().add(categoryView);

            Label pageTitle = navigator.getPageTitle();
            if (pageTitle != null) {
                pageTitle.setText("Gestion des categories");
            }
        } catch (Exception e) {
            System.err.println("Error opening categories: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur", 
                "Impossible d'ouvrir la page des catégories : " + e.getMessage());
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
            String copiedPath = copyCoverImage(selectedFile);
            selectedImagePath = copiedPath != null ? copiedPath : selectedFile.getAbsolutePath();
            imagePathLabel.setText(selectedImagePath);
        }
    }

    private String copyCoverImage(File sourceFile) {
        try {
            String uploadsDir = System.getProperty("user.dir") + "/src/main/resources/uploads/matieres";
            File dir = new File(uploadsDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String originalName = sourceFile.getName();
            int dotIndex = originalName.lastIndexOf('.');
            String extension = dotIndex >= 0 ? originalName.substring(dotIndex) : "";
            String newFileName = "matiere_" + System.currentTimeMillis() + extension;
            File destinationFile = new File(dir, newFileName);

            Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/matieres/" + newFileName;
        } catch (Exception e) {
            System.err.println("Error copying cover image: " + e.getMessage());
            return null;
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
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 4); -fx-padding: 15; -fx-pref-width: 280; -fx-min-height: 280;");
        card.setPadding(new javafx.geometry.Insets(15));

        Node coverNode = createCoverNode(matiere.getCoverImage());

        Label titleLabel = new Label(matiere.getTitre() == null ? "" : matiere.getTitre());
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        Label descLabel = new Label(matiere.getDescription() == null ? "" : matiere.getDescription());
        descLabel.setWrapText(true);
        descLabel.setMaxHeight(40);

        FlowPane categoryPreview = createCategoryPreview(matiere.getId());

        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnSupprimer = new Button("Supprimer");
        btnSupprimer.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold;");
        btnSupprimer.setOnAction(e -> deleteMatiere(matiere));

        Button btnGerer = new Button("G\u00e9rer");
        btnGerer.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold;");
        btnGerer.setOnAction(e -> {
            populateForm(matiere);
            showEditor();
        });

        actionBox.getChildren().addAll(btnSupprimer, btnGerer);

        card.getChildren().addAll(coverNode, titleLabel, descLabel, categoryPreview, actionBox);
        VBox.setVgrow(actionBox, Priority.ALWAYS);
        return card;
    }

    private Node createCoverNode(String storedCoverPath) {
        Region fallback = createCoverFallback();
        String imageSource = resolveImageSource(storedCoverPath);
        if (imageSource == null) {
            return fallback;
        }

        try {
            Image image = new Image(imageSource, false);
            if (image.isError()) {
                return fallback;
            }

            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(250);
            imageView.setFitHeight(140);
            imageView.setPreserveRatio(false);
            imageView.setSmooth(true);
            return imageView;
        } catch (Exception e) {
            return fallback;
        }
    }

    private Region createCoverFallback() {
        Region fallback = new Region();
        fallback.setStyle("-fx-background-color: #e2e8f0; -fx-pref-width: 250; -fx-pref-height: 140; -fx-background-radius: 8;");
        fallback.setPrefSize(250, 140);
        fallback.setMinSize(250, 140);
        fallback.setMaxSize(250, 140);
        return fallback;
    }

    private String resolveImageSource(String storedPath) {
        if (storedPath == null) {
            return null;
        }

        String path = storedPath.trim();
        if (path.isEmpty()) {
            return null;
        }

        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:")) {
            return path;
        }

        File directFile = new File(path);
        if (directFile.exists()) {
            return directFile.toURI().toString();
        }

        String normalizedPath = path.replace("\\", "/");
        String resourcesBase = System.getProperty("user.dir") + "/src/main/resources";

        File resourceFile = normalizedPath.startsWith("/")
            ? new File(resourcesBase + normalizedPath)
            : new File(resourcesBase, normalizedPath);
        if (resourceFile.exists()) {
            return resourceFile.toURI().toString();
        }

        File projectRelative = new File(System.getProperty("user.dir"), normalizedPath);
        if (projectRelative.exists()) {
            return projectRelative.toURI().toString();
        }

        return null;
    }

    private FlowPane createCategoryPreview(int matiereId) {
        FlowPane preview = new FlowPane();
        preview.setHgap(6);
        preview.setVgap(6);

        List<Category> linkedCategories = categoryService.findByMatiereId(matiereId);
        if (linkedCategories.isEmpty()) {
            preview.setManaged(false);
            preview.setVisible(false);
            return preview;
        }

        for (Category category : linkedCategories) {
            Label chip = new Label(category.getName());
            chip.setStyle("-fx-background-color: #e0e7ff; -fx-background-radius: 12; -fx-padding: 3 8; -fx-text-fill: #3730a3;");
            preview.getChildren().add(chip);
        }
        return preview;
    }

    private void populateForm(Matiere matiere) {
        if (matiere == null) {
            return;
        }

        Matiere hydratedMatiere = matiere;
        if (matiere.getId() > 0) {
            Matiere reloadedMatiere = matiereService.findById(matiere.getId());
            if (reloadedMatiere != null) {
                hydratedMatiere = reloadedMatiere;
            }
        }

        // Track the matiere being edited
        selectedMatiere = hydratedMatiere;

        titreField.setText(hydratedMatiere.getTitre());
        descriptionArea.setText(hydratedMatiere.getDescription());
        selectedImagePath = hydratedMatiere.getCoverImage();
        imagePathLabel.setText(selectedImagePath == null || selectedImagePath.isBlank() ? "Aucune image sélectionnée" : selectedImagePath);
        
        courseBuilderContainer.getChildren().clear();
        categoryTagsContainer.getChildren().clear();
        loadCategoriesForMatiere(hydratedMatiere);
        
        if (hydratedMatiere.getStructure() != null && !hydratedMatiere.getStructure().isEmpty()) {
            loadJsonToUI(hydratedMatiere.getStructure());
        }
    }

    private void clearInputs() {
        titreField.clear();
        descriptionArea.clear();
        courseBuilderContainer.getChildren().clear();
        categoryTagsContainer.getChildren().clear();
        existingCategoriesCombo.setValue(null);
        newCategoryField.clear();
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

        // Create resources container
        VBox resourcesContainer = new VBox(5);
        resourcesContainer.setStyle("-fx-padding: 5 0 5 15;");

        Button addResourceBtn = new Button("+ Ressource");
        addResourceBtn.setStyle("-fx-font-size: 9px; -fx-background-color: #f1f5f9;");
        addResourceBtn.setOnAction(e -> showAddResourceDialog(resourcesContainer));

        sectionBox.getChildren().addAll(header, resourcesContainer, addResourceBtn);
        parentContainer.getChildren().add(sectionBox);
    }

    // === RESOURCE MANAGEMENT METHODS ===

    private String copyResourceFile(File sourceFile, String type) {
        try {
            // Create uploads directory if it doesn't exist
            String uploadsDir = System.getProperty("user.dir") + "/src/main/resources/uploads/resources";
            File dir = new File(uploadsDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Generate unique filename
            String newFileName = System.currentTimeMillis() + "_" + sourceFile.getName();
            File destinationFile = new File(uploadsDir + "/" + newFileName);

            // Copy the file
            Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Return relative path
            return "/uploads/resources/" + newFileName;
        } catch (Exception e) {
            System.err.println("Error copying resource file: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Erreur de copie", "Impossible de copier le fichier : " + e.getMessage());
            return null;
        }
    }

    private void showAddResourceDialog(VBox resourcesContainer) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Ajouter une Ressource");
        dialog.setHeaderText("Ajouter une nouvelle ressource à cette section");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPrefWidth(400);

        // Type selector
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("Vidéo", "PDF", "Lien");
        typeCombo.setValue("Vidéo");
        grid.add(new Label("Type:"), 0, 0);
        grid.add(typeCombo, 1, 0);

        // Name field
        TextField nameField = new TextField();
        nameField.setPromptText("Nom de la ressource");
        grid.add(new Label("Nom:"), 0, 1);
        grid.add(nameField, 1, 1);

        // File/URL field
        TextField pathField = new TextField();
        pathField.setPromptText("Chemin ou URL");
        pathField.setPrefWidth(300);
        Button browseBtn = new Button("Parcourir");

        browseBtn.setOnAction(e -> {
            String selectedType = typeCombo.getValue();
            if ("Vidéo".equals(selectedType)) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Vidéos", "*.mp4", "*.avi", "*.mkv"));
                File selectedFile = fileChooser.showOpenDialog(new Stage());
                if (selectedFile != null) {
                    pathField.setText(selectedFile.getAbsolutePath());
                }
            } else if ("PDF".equals(selectedType)) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
                File selectedFile = fileChooser.showOpenDialog(new Stage());
                if (selectedFile != null) {
                    pathField.setText(selectedFile.getAbsolutePath());
                }
            }
            // For "Lien", user manually enters the URL
        });

        HBox pathBox = new HBox(5);
        pathBox.getChildren().addAll(pathField, browseBtn);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        grid.add(new Label("Fichier/Lien:"), 0, 2);
        grid.add(pathBox, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                String type = typeCombo.getValue();
                String name = nameField.getText().trim();
                String pathInput = pathField.getText().trim();

                if (name.isEmpty() || pathInput.isEmpty()) {
                    showAlert(Alert.AlertType.WARNING, "Champs requis", "Veuillez remplir tous les champs.");
                    return;
                }

                String finalPath = pathInput;

                // If it's a file (not a link), copy it
                if (!"Lien".equals(type)) {
                    File sourceFile = new File(pathInput);
                    if (!sourceFile.exists()) {
                        showAlert(Alert.AlertType.ERROR, "Fichier non trouvé", "Le fichier sélectionné est introuvable.");
                        return;
                    }
                    finalPath = copyResourceFile(sourceFile, type);
                    if (finalPath == null) {
                        return; // Error was already shown
                    }
                }

                createResourceNode(type, name, finalPath, resourcesContainer);
            }
        });
    }

    private void createResourceNode(String type, String name, String path, VBox container) {
        HBox resourceBox = new HBox(10);
        resourceBox.setAlignment(Pos.CENTER_LEFT);
        resourceBox.setStyle("-fx-background-color: #f3f4f6; -fx-padding: 8; -fx-background-radius: 5;");
        resourceBox.setUserData(new String[]{type, name, path});

        // Icon label
        String icon = type.equals("Vidéo") ? "🎬" : type.equals("PDF") ? "📄" : "🔗";
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 16;");

        // Name label
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-weight: bold;");

        // Path label
        Label pathLabel = new Label(path);
        pathLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-style: italic; -fx-font-size: 10;");
        pathLabel.setMaxWidth(200);
        pathLabel.setWrapText(true);

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Open button
        Button openBtn = new Button("Ouvrir");
        openBtn.setStyle("-fx-font-size: 10;");
        openBtn.setOnAction(e -> {
            try {
                if ("Lien".equals(type)) {
                    Desktop.getDesktop().browse(new URI(path));
                } else {
                    String fullPath = System.getProperty("user.dir") + "/src/main/resources" + path;
                    Desktop.getDesktop().open(new File(fullPath));
                }
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Erreur d'ouverture", 
                    "Impossible d'ouvrir la ressource : " + ex.getMessage());
            }
        });

        // Delete button
        Button deleteBtn = new Button("✕");
        deleteBtn.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12;");
        deleteBtn.setOnAction(e -> container.getChildren().remove(resourceBox));

        resourceBox.getChildren().addAll(iconLabel, nameLabel, pathLabel, spacer, openBtn, deleteBtn);
        container.getChildren().add(resourceBox);
    }
    @FXML
    private void addExistingCategory(ActionEvent event) {
        String categoryName = existingCategoriesCombo.getValue();
        if (categoryName != null && !categoryName.isEmpty()) {
            Category selectedCategory = categoriesByName.get(normalizeCategoryName(categoryName));
            if (selectedCategory != null) {
                addCategoryTag(selectedCategory);
            }
            existingCategoriesCombo.setValue(null);
        }
    }

    @FXML
    private void createNewCategory(ActionEvent event) {
        String newCategoryName = newCategoryField.getText().trim();
        if (!newCategoryName.isEmpty()) {
            try {
                Category newCategory = new Category();
                newCategory.setName(newCategoryName);
                newCategory.setSlug(buildSlug(newCategoryName));
                categoryService.ajouter(newCategory);

                loadCategoriesIntoDropdown();
                Category persistedCategory = categoriesByName.get(normalizeCategoryName(newCategoryName));
                if (persistedCategory != null) {
                    addCategoryTag(persistedCategory);
                }
                newCategoryField.clear();
            } catch (Exception e) {
                System.err.println("Error creating and saving category: " + e.getMessage());
                showAlert(Alert.AlertType.ERROR, "Erreur categorie", "Impossible de creer la categorie.");
            }
        }
    }

    private void addCategoryTag(Category category) {
        if (category == null || category.getName() == null || category.getName().isBlank()) {
            return;
        }

        int categoryId = category.getId();
        String categoryKey = normalizeCategoryName(category.getName());
        if (isCategoryAlreadyTagged(categoryId, categoryKey)) {
            return;
        }

        HBox tag = new HBox(8);
        tag.setStyle("-fx-background-color: #e0e7ff; -fx-background-radius: 15; -fx-padding: 5 10; -fx-alignment: CENTER;");
        tag.setUserData(categoryId > 0 ? Integer.valueOf(categoryId) : categoryKey);

        Label label = new Label(category.getName());
        Button removeBtn = new Button("x");
        removeBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 0; -fx-min-width: 20; -fx-min-height: 20;");
        removeBtn.setOnAction(e -> categoryTagsContainer.getChildren().remove(tag));

        tag.getChildren().addAll(label, removeBtn);
        categoryTagsContainer.getChildren().add(tag);
    }

    private boolean isCategoryAlreadyTagged(int categoryId, String categoryKey) {
        for (Node node : categoryTagsContainer.getChildren()) {
            if (!(node instanceof HBox)) {
                continue;
            }

            Object tagData = node.getUserData();
            if (tagData instanceof Integer && categoryId > 0 && ((Integer) tagData) == categoryId) {
                return true;
            }
            if (tagData instanceof String && tagData.equals(categoryKey)) {
                return true;
            }
        }
        return false;
    }

    private void loadCategoriesForMatiere(Matiere matiere) {
        categoryTagsContainer.getChildren().clear();
        if (matiere == null || matiere.getId() <= 0) {
            return;
        }

        List<Category> linkedCategories = categoryService.findByMatiereId(matiere.getId());
        for (Category category : linkedCategories) {
            addCategoryTag(category);
        }
    }

    private void saveCategoriesToMatiere(Matiere matiere) {
        if (matiere == null || matiere.getId() <= 0) {
            return;
        }

        List<Integer> categoryIds = new ArrayList<>();
        for (Node node : categoryTagsContainer.getChildren()) {
            if (!(node instanceof HBox)) {
                continue;
            }

            Object tagData = node.getUserData();
            if (tagData instanceof Integer && ((Integer) tagData) > 0) {
                categoryIds.add((Integer) tagData);
                continue;
            }

            if (tagData instanceof String) {
                Category category = categoriesByName.get((String) tagData);
                if (category != null && category.getId() > 0) {
                    categoryIds.add(category.getId());
                }
            }
        }

        categoryService.replaceMatiereCategories(matiere.getId(), categoryIds);
    }

    private String normalizeCategoryName(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String buildSlug(String source) {
        String slug = source == null ? "" : source.trim().toLowerCase();
        slug = slug.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            slug = "category-" + System.currentTimeMillis();
        }
        return slug;
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
                            VBox resourcesContainer = null;
                            
                            // Extract section title and resources container
                            for (Node sectionChild : sectionBox.getChildren()) {
                                if (sectionChild instanceof HBox) {
                                    HBox sectionHeader = (HBox) sectionChild;
                                    for (Node sectionHeaderChild : sectionHeader.getChildren()) {
                                        if (sectionHeaderChild instanceof TextField) {
                                            sectionTitle = ((TextField) sectionHeaderChild).getText();
                                        }
                                    }
                                } else if (sectionChild instanceof VBox && resourcesContainer == null) {
                                    resourcesContainer = (VBox) sectionChild;
                                }
                            }
                            
                            // Build section JSON with resources
                            if (sectionCount > 0) json.append(",");
                            json.append("{\"title\":\"").append(escapeJson(sectionTitle)).append("\",\"resources\":[");
                            
                            // Extract resources from the resources container
                            int resourceCount = 0;
                            if (resourcesContainer != null) {
                                for (Node resourceNode : resourcesContainer.getChildren()) {
                                    if (resourceNode instanceof HBox) {
                                        HBox resourceBox = (HBox) resourceNode;
                                        String[] resourceData = (String[]) resourceBox.getUserData();
                                        if (resourceData != null && resourceData.length == 3) {
                                            if (resourceCount > 0) json.append(",");
                                            String type = resourceData[0];
                                            String name = resourceData[1];
                                            String path = resourceData[2];
                                            json.append("{\"type\":\"").append(escapeJson(type))
                                                .append("\",\"name\":\"").append(escapeJson(name))
                                                .append("\",\"path\":\"").append(escapeJson(path))
                                                .append("\"}");
                                            resourceCount++;
                                        }
                                    }
                                }
                            }
                            
                            json.append("]}");
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
            if (json == null || json.trim().isEmpty()) {
                courseBuilderContainer.getChildren().clear();
                return;
            }

            courseBuilderContainer.getChildren().clear();

            Object parsedRoot = new JSONTokener(json).nextValue();
            JSONArray chapters;

            if (parsedRoot instanceof JSONObject) {
                chapters = ((JSONObject) parsedRoot).optJSONArray("chapters");
            } else if (parsedRoot instanceof JSONArray) {
                chapters = (JSONArray) parsedRoot;
            } else {
                return;
            }

            if (chapters == null) {
                return;
            }

            for (int chapterIndex = 0; chapterIndex < chapters.length(); chapterIndex++) {
                JSONObject chapterObject = chapters.optJSONObject(chapterIndex);
                if (chapterObject == null) {
                    continue;
                }

                createChapterNode();
                VBox lastChapter = (VBox) courseBuilderContainer.getChildren().get(courseBuilderContainer.getChildren().size() - 1);

                String chapterTitle = chapterObject.optString("title", "");
                VBox sectionsContainer = null;
                for (Node node : lastChapter.getChildren()) {
                    if (node instanceof HBox) {
                        HBox header = (HBox) node;
                        for (Node headerChild : header.getChildren()) {
                            if (headerChild instanceof TextField) {
                                ((TextField) headerChild).setText(chapterTitle);
                            }
                        }
                    } else if (node instanceof VBox && sectionsContainer == null) {
                        sectionsContainer = (VBox) node;
                    }
                }

                JSONArray sections = chapterObject.optJSONArray("sections");
                if (sections == null || sectionsContainer == null) {
                    continue;
                }

                for (int sectionIndex = 0; sectionIndex < sections.length(); sectionIndex++) {
                    JSONObject sectionObject = sections.optJSONObject(sectionIndex);
                    if (sectionObject == null) {
                        continue;
                    }

                    createSectionNode(sectionsContainer);
                    VBox lastSection = (VBox) sectionsContainer.getChildren().get(sectionsContainer.getChildren().size() - 1);
                    String sectionTitle = sectionObject.optString("title", "");

                    VBox resourcesContainer = null;
                    for (Node sectionNode : lastSection.getChildren()) {
                        if (sectionNode instanceof HBox) {
                            HBox sectionHeader = (HBox) sectionNode;
                            for (Node headerChild : sectionHeader.getChildren()) {
                                if (headerChild instanceof TextField) {
                                    ((TextField) headerChild).setText(sectionTitle);
                                }
                            }
                        } else if (sectionNode instanceof VBox && resourcesContainer == null) {
                            resourcesContainer = (VBox) sectionNode;
                        }
                    }

                    JSONArray resources = sectionObject.optJSONArray("resources");
                    if (resources == null || resourcesContainer == null) {
                        continue;
                    }

                    for (int resourceIndex = 0; resourceIndex < resources.length(); resourceIndex++) {
                        JSONObject resourceObject = resources.optJSONObject(resourceIndex);
                        if (resourceObject == null) {
                            continue;
                        }

                        createResourceNode(
                            resourceObject.optString("type", ""),
                            resourceObject.optString("name", ""),
                            resourceObject.optString("path", ""),
                            resourcesContainer
                        );
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Error parsing JSON structure (may be legacy format): " + e.getMessage());
            courseBuilderContainer.getChildren().clear();
        }
    }
}


