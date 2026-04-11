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
    private void initialize() {
        welcomeTitleLabel.setText("Bon retour, " + UserSession.getDisplayName() + " !");
        welcomeSubtitleLabel.setText("Continuez votre parcours sur Fahamni avec vos outils personnels a portee de clic.");
    }
}
