package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.AdminArticle;
import tn.esprit.fahamni.services.AdminContentService;
import tn.esprit.fahamni.utils.OperationResult;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class BackofficeContentController {

    @FXML
    private TableView<AdminArticle> contentTable;

    @FXML
    private TableColumn<AdminArticle, String> titleColumn;

    @FXML
    private TableColumn<AdminArticle, String> categoryColumn;

    @FXML
    private TableColumn<AdminArticle, String> authorColumn;

    @FXML
    private TableColumn<AdminArticle, String> statusColumn;

    @FXML
    private TextField titleField;

    @FXML
    private TextField authorField;

    @FXML
    private ComboBox<String> categoryComboBox;

    @FXML
    private ComboBox<String> statusComboBox;

    @FXML
    private Label feedbackLabel;

    private final AdminContentService contentService = new AdminContentService();

    @FXML
    private void initialize() {
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        authorColumn.setCellValueFactory(new PropertyValueFactory<>("author"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        categoryComboBox.getItems().setAll(contentService.getAvailableCategories());
        statusComboBox.getItems().setAll(contentService.getAvailableStatuses());
        categoryComboBox.setValue("Study Tips");
        statusComboBox.setValue("Draft");

        contentTable.setItems(contentService.getArticles());
        contentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> populateForm(newValue));

        hideFeedback();
    }

    @FXML
    private void handleCreateDraft() {
        hideFeedback();

        OperationResult result = contentService.createDraft(
            titleField.getText(),
            categoryComboBox.getValue(),
            authorField.getText()
        );
        if (result.isSuccess()) {
            clearForm();
        }
        showFeedback(result.getMessage(), result.isSuccess());
    }

    @FXML
    private void handlePublishContent() {
        hideFeedback();

        OperationResult result = contentService.updateArticle(
            contentTable.getSelectionModel().getSelectedItem(),
            titleField.getText(),
            categoryComboBox.getValue(),
            authorField.getText(),
            statusComboBox.getValue()
        );
        if (result.isSuccess()) {
            contentTable.refresh();
        }
        showFeedback(result.getMessage(), result.isSuccess());
    }

    @FXML
    private void handleResetContent() {
        contentTable.getSelectionModel().clearSelection();
        clearForm();
        hideFeedback();
    }

    private void populateForm(AdminArticle article) {
        if (article == null) {
            return;
        }

        titleField.setText(article.getTitle());
        authorField.setText(article.getAuthor());
        categoryComboBox.setValue(article.getCategory());
        statusComboBox.setValue(article.getStatus());
    }

    private void clearForm() {
        titleField.clear();
        authorField.clear();
        categoryComboBox.setValue("Study Tips");
        statusComboBox.setValue("Draft");
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().setAll("backoffice-feedback", success ? "success" : "error");
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
    }

    private void hideFeedback() {
        feedbackLabel.setText("");
        feedbackLabel.getStyleClass().setAll("backoffice-feedback");
        feedbackLabel.setManaged(false);
        feedbackLabel.setVisible(false);
    }
}

