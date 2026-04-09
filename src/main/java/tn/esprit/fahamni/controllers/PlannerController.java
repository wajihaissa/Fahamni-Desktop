package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.StudyPlanRequest;
import tn.esprit.fahamni.services.PlannerService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;

public class PlannerController {

    private final PlannerService plannerService = new PlannerService();

    @FXML
    private DatePicker examDatePicker;

    @FXML
    private ComboBox<String> subjectCombo;

    @FXML
    private ComboBox<String> difficultyCombo;

    @FXML
    private Button generateButton;

    @FXML
    private void initialize() {
        subjectCombo.setValue(plannerService.getDefaultSubject());
        difficultyCombo.setValue(plannerService.getDefaultDifficulty());
    }

    @FXML
    private void generatePlan() {
        StudyPlanRequest request = new StudyPlanRequest(
            examDatePicker.getValue(),
            subjectCombo.getValue(),
            difficultyCombo.getValue()
        );
        System.out.println(plannerService.generatePlan(request));
    }
}

