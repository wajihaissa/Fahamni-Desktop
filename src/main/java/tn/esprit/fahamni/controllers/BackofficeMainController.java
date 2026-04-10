package tn.esprit.fahamni.controllers;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.SceneManager;

public class BackofficeMainController {

    @FXML
    private AnchorPane contentPane;

    @FXML
    private Label pageTitle;

    @FXML
    private Label pageSubtitle;

    @FXML
    private Button dashboardButton;

    @FXML
    private Button usersButton;

    @FXML
    private Button sessionsButton;

    @FXML
    private Button reservationsButton;

    @FXML
    private Button contentButton;

    @FXML
    private Button infrastructureToggleButton;

    @FXML
    private VBox infrastructureSubmenu;

    @FXML
    private Label infrastructureChevron;

    @FXML
    private Button sallesButton;

    @FXML
    private Button reclamationsButton;

    @FXML
    private Button maintenanceButton;

    @FXML
    private Button equipementsButton;

    @FXML
    private Button infrastructureStatsButton;

    private boolean infrastructureExpanded;

    @FXML
    private void initialize() {
        setInfrastructureExpanded(false);
        showDashboard();
    }

    @FXML
    private void showDashboard() {
        loadView(
            "BackofficeDashboardView.fxml",
            "Dashboard Admin",
            "Vue d'ensemble des operations, contenus et activites du backoffice."
        );
        setActiveButton(dashboardButton);
    }

    @FXML
    private void showUsers() {
        loadView(
            "BackofficeUsersView.fxml",
            "Gestion des utilisateurs",
            "Suivez les comptes, les roles et les statuts visibles dans la plateforme."
        );
        setActiveButton(usersButton);
    }

    @FXML
    private void showSessions() {
        loadView(
            "BackofficeSessionsView.fxml",
            "Gestion des seances",
            "Planifiez les seances et gardez une vue claire sur leur publication."
        );
        setActiveButton(sessionsButton);
    }

    @FXML
    private void showReservations() {
        loadView(
            "BackofficeReservationsView.fxml",
            "Gestion des reservations",
            "Validez les demandes et ajustez rapidement leur statut administratif."
        );
        setActiveButton(reservationsButton);
    }

    @FXML
    private void showContent() {
        loadView(
            "BackofficeContentView.fxml",
            "Gestion du contenu",
            "Pilotez les articles, ressources et publications mises en avant."
        );
        setActiveButton(contentButton);
    }

    @FXML
    private void toggleInfrastructureMenu() {
        setInfrastructureExpanded(!infrastructureExpanded);
    }

    @FXML
    private void showSalles() {
        setInfrastructureExpanded(true);
        loadView(
            "BackofficeSallesView.fxml",
            "Gestion des salles",
            "Administrez vos espaces, leur capacite et leur niveau de disponibilite."
        );
        setActiveButton(sallesButton);
    }

    @FXML
    private void showReclamations() {
        setInfrastructureExpanded(true);
        loadView(
            "BackofficeReclamationsView.fxml",
            "Reclamations des salles",
            "Centralisez les signalements des tuteurs et transformez-les en actions administratives claires."
        );
        setActiveButton(reclamationsButton);
    }

    @FXML
    private void showMaintenance() {
        setInfrastructureExpanded(true);
        loadView(
            "BackofficeMaintenanceView.fxml",
            "Maintenance des salles",
            "Suivez les interventions techniques et gardez une vision claire de l'etat des salles."
        );
        setActiveButton(maintenanceButton);
    }

    @FXML
    private void showEquipements() {
        setInfrastructureExpanded(true);
        loadView(
            "BackofficeEquipementsView.fxml",
            "Gestion des equipements",
            "Suivez le parc materiel, les quantites disponibles et l'etat courant de chaque equipement."
        );
        setActiveButton(equipementsButton);
    }

    @FXML
    private void showInfrastructureStats() {
        setInfrastructureExpanded(true);
        showPlaceholder(
            "Statistiques infrastructure",
            "Vous pourrez ici suivre les indicateurs specifiques aux salles et equipements.",
            "Espace statistiques a venir",
            "Le dropdown est deja pret pour accueillir vos tableaux de bord metier."
        );
        setActiveButton(infrastructureStatsButton);
    }

    @FXML
    private void handleLogout() {
        try {
            Main.showLogin();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadView(String fxmlFile, String title, String subtitle) {
        try {
            Node view = SceneManager.loadView(Main.class, SceneManager.backofficeView(fxmlFile));
            displayContent(view);
            updateHeader(title, subtitle);
        } catch (Exception e) {
            e.printStackTrace();
            Label placeholder = new Label("Vue indisponible : " + fxmlFile);
            placeholder.getStyleClass().add("backoffice-empty-state");
            displayContent(placeholder);
            updateHeader(title, subtitle);
        }
    }

    private void showPlaceholder(String title, String subtitle, String panelTitle, String panelCopy) {
        VBox page = new VBox(20);
        page.setPadding(new Insets(24));
        page.setAlignment(Pos.TOP_LEFT);
        page.getStyleClass().add("backoffice-page");

        VBox panel = new VBox(10);
        panel.getStyleClass().add("backoffice-panel");

        Label titleLabel = new Label(panelTitle);
        titleLabel.getStyleClass().add("backoffice-section-title");

        Label copyLabel = new Label(panelCopy);
        copyLabel.setWrapText(true);
        copyLabel.getStyleClass().add("backoffice-panel-copy");

        panel.getChildren().addAll(titleLabel, copyLabel);
        page.getChildren().add(panel);

        displayContent(page);
        updateHeader(title, subtitle);
    }

    private void displayContent(Node content) {
        contentPane.getChildren().clear();
        AnchorPane.setTopAnchor(content, 0.0);
        AnchorPane.setBottomAnchor(content, 0.0);
        AnchorPane.setLeftAnchor(content, 0.0);
        AnchorPane.setRightAnchor(content, 0.0);
        contentPane.getChildren().add(content);
    }

    private void updateHeader(String title, String subtitle) {
        pageTitle.setText(title);
        pageSubtitle.setText(subtitle);
    }

    private void setActiveButton(Button activeButton) {
        dashboardButton.getStyleClass().remove("active");
        usersButton.getStyleClass().remove("active");
        sessionsButton.getStyleClass().remove("active");
        reservationsButton.getStyleClass().remove("active");
        contentButton.getStyleClass().remove("active");
        infrastructureToggleButton.getStyleClass().remove("active");
        sallesButton.getStyleClass().remove("active");
        reclamationsButton.getStyleClass().remove("active");
        maintenanceButton.getStyleClass().remove("active");
        equipementsButton.getStyleClass().remove("active");
        infrastructureStatsButton.getStyleClass().remove("active");

        if (!activeButton.getStyleClass().contains("active")) {
            activeButton.getStyleClass().add("active");
        }

        if (activeButton == sallesButton
            || activeButton == reclamationsButton
            || activeButton == maintenanceButton
            || activeButton == equipementsButton
            || activeButton == infrastructureStatsButton) {
            if (!infrastructureToggleButton.getStyleClass().contains("active")) {
                infrastructureToggleButton.getStyleClass().add("active");
            }
        }
    }

    private void setInfrastructureExpanded(boolean expanded) {
        infrastructureExpanded = expanded;
        infrastructureSubmenu.setManaged(expanded);
        infrastructureSubmenu.setVisible(expanded);
        infrastructureChevron.setText(expanded ? "v" : ">");
    }
}

