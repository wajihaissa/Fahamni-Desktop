package tn.esprit.fahamni.controllers;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import tn.esprit.fahamni.entities.Matiere;
import tn.esprit.fahamni.services.MatiereService;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.SceneManager;

public class BackofficeMatiereController implements Initializable {

    @FXML
    private TableView<Matiere> matiereTable;

    @FXML
    private VBox listView;

    @FXML
    private VBox editorView;

    @FXML
    private TableColumn<Matiere, Integer> idColumn;

    @FXML
    private TableColumn<Matiere, String> titreColumn;

    @FXML
    private TableColumn<Matiere, String> descriptionColumn;

    @FXML
    private TableColumn<Matiere, String> structureColumn;

    @FXML
    private TableColumn<Matiere, LocalDateTime> createdAtColumn;

    @FXML
    private TextField titreField;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private VBox courseBuilderContainer;

    @FXML
    private Label imagePathLabel;

    @FXML
    private Button ajouterButton;

    @FXML
    private Button modifierButton;

    @FXML
    private Button supprimerButton;

    @FXML
    private Button viderButton;

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
    private final ObservableList<Matiere> matieres = FXCollections.observableArrayList();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private String selectedImagePath;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        titreColumn.setCellValueFactory(new PropertyValueFactory<>("titre"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        structureColumn.setCellValueFactory(new PropertyValueFactory<>("structure"));
        structureColumn.setCellFactory(column -> new TableCell<>() {
            private final Button btn = new Button("Gérer");
            {
                btn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
                btn.setPrefWidth(80);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    btn.setOnAction(e -> {
                        Matiere matiere = getTableView().getItems().get(getIndex());
                        matiereTable.getSelectionModel().select(getIndex());
                        populateForm(matiere);
                        showEditor();
                    });
                    setGraphic(btn);
                }
            }
        });
        createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        createdAtColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                } else {
                    setText(item.format(dateFormatter));
                }
            }
        });

        matiereTable.setItems(matieres);
        refreshTable();

        matiereTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populateForm(newValue));
    }

    @FXML
    private void addMatiere(ActionEvent event) {
        Matiere matiere = new Matiere();
        matiere.setTitre(titreField.getText());
        matiere.setDescription(descriptionArea.getText());
        matiere.setStructure(buildJsonFromUI());
        matiere.setCoverImage(selectedImagePath);
        matiere.setCreatedAt(LocalDateTime.now());

        matiereService.add(matiere);
        refreshTable();
        showList();
    }

    @FXML
    private void updateMatiere(ActionEvent event) {
        Matiere selectedMatiere = matiereTable.getSelectionModel().getSelectedItem();
        if (selectedMatiere == null) {
            return;
        }

        selectedMatiere.setTitre(titreField.getText());
        selectedMatiere.setDescription(descriptionArea.getText());
        selectedMatiere.setStructure(buildJsonFromUI());
        selectedMatiere.setCoverImage(selectedImagePath);

        matiereService.update(selectedMatiere);
        refreshTable();
        showList();
    }

    @FXML
    private void deleteMatiere(ActionEvent event) {
        Matiere selectedMatiere = matiereTable.getSelectionModel().getSelectedItem();
        if (selectedMatiere == null) {
            return;
        }

        matiereService.delete(selectedMatiere);
        refreshTable();
        showList();
    }

    @FXML
    private void clearForm(ActionEvent event) {
        clearInputs();
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

    private void refreshTable() {
        matieres.setAll(matiereService.findAll());
        matiereTable.refresh();
    }

    private void populateForm(Matiere matiere) {
        if (matiere == null) {
            return;
        }

        titreField.setText(matiere.getTitre());
        descriptionArea.setText(matiere.getDescription());
        selectedImagePath = matiere.getCoverImage();
        imagePathLabel.setText(selectedImagePath == null || selectedImagePath.isBlank() ? "Aucune image sélectionnée" : selectedImagePath);
        
        courseBuilderContainer.getChildren().clear();
        categoryTagsContainer.getChildren().clear();
        
        if (matiere.getStructure() != null && !matiere.getStructure().isEmpty()) {
            loadJsonToUI(matiere.getStructure());
        }
    }

    private void clearInputs() {
        matiereTable.getSelectionModel().clearSelection();
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
        refreshTable();
    }

    @FXML
    private void handleCreateNew() {
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
        String newCategory = newCategoryField.getText().trim();
        if (!newCategory.isEmpty()) {
            addCategoryTag(newCategory);
            newCategoryField.clear();
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
            // Simple manual parsing for JSON structure
            courseBuilderContainer.getChildren().clear();
            
            // Extract chapters
            int chaptersStart = json.indexOf("[");
            int chaptersEnd = json.lastIndexOf("]");
            if (chaptersStart < 0 || chaptersEnd < 0) return;
            
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
        } catch (Exception e) {
            System.err.println("Error parsing JSON structure: " + e.getMessage());
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
