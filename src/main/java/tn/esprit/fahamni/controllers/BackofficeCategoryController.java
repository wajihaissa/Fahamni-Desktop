package tn.esprit.fahamni.controllers;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import tn.esprit.fahamni.Models.Category;
import tn.esprit.fahamni.services.CategoryService;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.SceneManager;
import tn.esprit.fahamni.utils.ViewNavigator;

public class BackofficeCategoryController implements Initializable {

    @FXML
    private TableView<Category> categoryTable;

    @FXML
    private TableColumn<Category, Integer> idColumn;

    @FXML
    private TableColumn<Category, String> nameColumn;

    @FXML
    private TableColumn<Category, String> slugColumn;

    @FXML
    private TextField nameField;

    @FXML
    private TextField slugField;

    @FXML
    private Button addButton;

    @FXML
    private Button updateButton;

    @FXML
    private Button deleteButton;

    @FXML
    private Button backToMatieresButton;

    private final CategoryService categoryService = new CategoryService();
    private final ObservableList<Category> categoryList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        slugColumn.setCellValueFactory(new PropertyValueFactory<>("slug"));

        categoryTable.setItems(categoryList);
        refreshTable();

        categoryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                nameField.setText(newSelection.getName());
                slugField.setText(newSelection.getSlug());
            }
        });
    }

    @FXML
    private void handleAdd(ActionEvent event) {
        Category category = new Category();
        category.setName(nameField.getText());
        category.setSlug(slugField.getText());

        categoryService.ajouter(category);
        refreshTable();
        clearForm();
    }

    @FXML
    private void handleUpdate(ActionEvent event) {
        Category selectedCategory = categoryTable.getSelectionModel().getSelectedItem();
        if (selectedCategory == null) {
            return;
        }

        selectedCategory.setName(nameField.getText());
        selectedCategory.setSlug(slugField.getText());

        categoryService.modifier(selectedCategory);
        refreshTable();
        clearForm();
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        Category selectedCategory = categoryTable.getSelectionModel().getSelectedItem();
        if (selectedCategory == null) {
            return;
        }

        categoryService.supprimer(selectedCategory.getId());
        refreshTable();
        clearForm();
    }

    @FXML
    private void goBackToMatieres(ActionEvent event) {
        try {
            Node matiereView = SceneManager.loadView(
                Main.class,
                SceneManager.backofficeView("BackofficeMatiereView.fxml")
            );

            ViewNavigator navigator = ViewNavigator.getInstance();
            AnchorPane contentPane = navigator.getContentPane();
            
            if (contentPane == null) {
                System.err.println("ERROR: ContentPane is null when going back to matieres!");
                return;
            }

            contentPane.getChildren().clear();
            AnchorPane.setTopAnchor(matiereView, 0.0);
            AnchorPane.setBottomAnchor(matiereView, 0.0);
            AnchorPane.setLeftAnchor(matiereView, 0.0);
            AnchorPane.setRightAnchor(matiereView, 0.0);
            contentPane.getChildren().add(matiereView);

            Label pageTitle = navigator.getPageTitle();
            if (pageTitle != null) {
                pageTitle.setText("Gestion des matières");
            }
        } catch (Exception e) {
            System.err.println("Error going back to matieres: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void refreshTable() {
        categoryList.setAll(categoryService.afficherList());
        categoryTable.refresh();
    }

    private void clearForm() {
        categoryTable.getSelectionModel().clearSelection();
        nameField.clear();
        slugField.clear();
    }
}
