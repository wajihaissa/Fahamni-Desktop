package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.services.BlogService;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class BlogController {

    private final BlogService blogService = new BlogService();

    @FXML
    private ComboBox<String> categoryFilter;

    @FXML
    private TextField searchField;

    @FXML
    private void initialize() {
        categoryFilter.getItems().setAll(blogService.getCategories());
        categoryFilter.setValue(blogService.getDefaultCategory());
    }
}

