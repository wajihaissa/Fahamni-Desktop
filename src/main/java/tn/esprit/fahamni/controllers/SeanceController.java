package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.services.SeanceService;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class SeanceController {

    private final SeanceService seanceService = new SeanceService();

    @FXML
    private ComboBox<String> subjectFilter;

    @FXML
    private TextField searchField;

    @FXML
    private void initialize() {
        subjectFilter.getItems().setAll(seanceService.getSubjects());
        subjectFilter.setValue(seanceService.getDefaultSubject());
    }

    @FXML
    private void handleSearch() {
        String searchText = searchField.getText();
        String selectedSubject = subjectFilter.getValue();
        System.out.println(seanceService.buildSearchSummary(searchText, selectedSubject));
    }
}

