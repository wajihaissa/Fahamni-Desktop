package tn.esprit.fahamni.controllers;

import tn.esprit.fahamni.Models.AdminDashboardSummary;
import tn.esprit.fahamni.Models.AdminFeedItem;
import tn.esprit.fahamni.services.AdminDashboardService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class BackofficeDashboardController {

    private final AdminDashboardService dashboardService = new AdminDashboardService();

    @FXML
    private Label usersValueLabel;

    @FXML
    private Label sessionsValueLabel;

    @FXML
    private Label reservationsValueLabel;

    @FXML
    private Label contentValueLabel;

    @FXML
    private VBox recentActivityBox;

    @FXML
    private VBox alertsBox;

    @FXML
    private void initialize() {
        AdminDashboardSummary summary = dashboardService.getSummary();
        usersValueLabel.setText(String.valueOf(summary.getUsersCount()));
        sessionsValueLabel.setText(String.valueOf(summary.getSessionsCount()));
        reservationsValueLabel.setText(String.valueOf(summary.getReservationsCount()));
        contentValueLabel.setText(String.valueOf(summary.getContentCount()));

        for (AdminFeedItem item : dashboardService.getRecentActivities()) {
            addActivity(item);
        }

        for (String alert : dashboardService.getAlerts()) {
            addAlert(alert);
        }
    }

    private void addActivity(AdminFeedItem item) {
        VBox card = new VBox(4);
        card.getStyleClass().add("backoffice-feed-card");

        Label titleLabel = new Label(item.getTitle());
        titleLabel.getStyleClass().add("backoffice-feed-title");

        Label descriptionLabel = new Label(item.getDescription());
        descriptionLabel.getStyleClass().add("backoffice-feed-copy");
        descriptionLabel.setWrapText(true);

        card.getChildren().addAll(titleLabel, descriptionLabel);
        recentActivityBox.getChildren().add(card);
    }

    private void addAlert(String text) {
        HBox row = new HBox();
        row.getStyleClass().add("backoffice-alert-row");

        Label label = new Label(text);
        label.getStyleClass().add("backoffice-alert-text");
        label.setWrapText(true);

        row.getChildren().add(label);
        alertsBox.getChildren().add(row);
    }
}

