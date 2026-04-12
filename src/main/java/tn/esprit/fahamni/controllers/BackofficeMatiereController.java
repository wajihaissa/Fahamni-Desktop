package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.entities.Matiere;
import tn.esprit.fahamni.services.MatiereService;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.SceneManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.io.File;

public class BackofficeMatiereController implements Initializable {

    @FXML
    private TableView<Matiere> matiereTable;

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
    private TextArea structureArea;

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
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText("");
                    return;
                }

                String value = item == null ? "" : item.trim();
                if (value.isEmpty() || "{}".equals(value) || "[]".equals(value)) {
                    setText("Vide");
                } else if (value.length() > 30) {
                    setText(value.substring(0, 30) + "...");
                } else {
                    setText(value);
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
        matiere.setStructure(structureArea.getText());
        matiere.setCoverImage(selectedImagePath);
        matiere.setCreatedAt(LocalDateTime.now());

        matiereService.add(matiere);
        refreshTable();
        clearInputs();
    }

    @FXML
    private void updateMatiere(ActionEvent event) {
        Matiere selectedMatiere = matiereTable.getSelectionModel().getSelectedItem();
        if (selectedMatiere == null) {
            return;
        }

        selectedMatiere.setTitre(titreField.getText());
        selectedMatiere.setDescription(descriptionArea.getText());
        selectedMatiere.setStructure(structureArea.getText());
        selectedMatiere.setCoverImage(selectedImagePath);

        matiereService.update(selectedMatiere);
        refreshTable();
        clearInputs();
    }

    @FXML
    private void deleteMatiere(ActionEvent event) {
        Matiere selectedMatiere = matiereTable.getSelectionModel().getSelectedItem();
        if (selectedMatiere == null) {
            return;
        }

        matiereService.delete(selectedMatiere);
        refreshTable();
        clearInputs();
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
        structureArea.setText(matiere.getStructure());
        selectedImagePath = matiere.getCoverImage();
        imagePathLabel.setText(selectedImagePath == null || selectedImagePath.isBlank() ? "Aucune image s\u00e9lectionn\u00e9e" : selectedImagePath);
    }

    private void clearInputs() {
        matiereTable.getSelectionModel().clearSelection();
        titreField.clear();
        descriptionArea.clear();
        structureArea.clear();
        selectedImagePath = null;
        imagePathLabel.setText("Aucune image s\u00e9lectionn\u00e9e");
    }
}
