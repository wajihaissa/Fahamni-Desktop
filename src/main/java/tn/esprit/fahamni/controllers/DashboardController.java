package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.utils.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class DashboardController {

    @FXML
    private Label welcomeTitleLabel;

    @FXML
    private Label welcomeSubtitleLabel;

    @FXML
    private Label accountNameLabel;

    @FXML
    private Label accountEmailLabel;

    @FXML
    private Label accountRoleLabel;

    @FXML
    private Label focusTitleLabel;

    @FXML
    private Label focusCopyLabel;

    @FXML
    private void initialize() {
        welcomeTitleLabel.setText("Bon retour, " + UserSession.getDisplayName() + " !");
        welcomeSubtitleLabel.setText("Continuez votre parcours sur Fahamni avec vos outils personnels a portee de clic.");
        accountNameLabel.setText(UserSession.getDisplayName());
        accountEmailLabel.setText(UserSession.hasCurrentUser() ? UserSession.getCurrentUser().getEmail() : "Aucun email disponible");
        accountRoleLabel.setText(UserSession.getRoleLabel());
        focusTitleLabel.setText("Priorite du jour");
        focusCopyLabel.setText("Concentrez-vous sur une seance a venir, puis verifiez votre planner pour garder un rythme regulier.");
    }
}
