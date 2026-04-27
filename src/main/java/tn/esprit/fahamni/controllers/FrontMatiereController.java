package tn.esprit.fahamni.controllers;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import tn.esprit.fahamni.Models.Category;
import tn.esprit.fahamni.entities.Matiere;
import tn.esprit.fahamni.services.CategoryService;
import tn.esprit.fahamni.services.MatiereService;
import tn.esprit.fahamni.test.Main;
import tn.esprit.fahamni.utils.ApplicationState;
import tn.esprit.fahamni.utils.SceneManager;
import tn.esprit.fahamni.utils.ViewNavigator;

public class FrontMatiereController implements Initializable {

    @FXML
    private FlowPane coursesContainer;

    private final MatiereService matiereService = new MatiereService();
    private final CategoryService categoryService = new CategoryService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadCourses();
    }

    private void loadCourses() {
        coursesContainer.getChildren().clear();
        List<Matiere> matieres = matiereService.findAll();

        for (Matiere matiere : matieres) {
            coursesContainer.getChildren().add(createCourseCard(matiere));
        }
    }

    private VBox createCourseCard(Matiere matiere) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: white; "
            + "-fx-background-radius: 12; "
            + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 4); "
            + "-fx-padding: 15; "
            + "-fx-pref-width: 280; "
            + "-fx-min-height: 280;");
        card.setPadding(new javafx.geometry.Insets(15));

        Node coverNode = createCoverNode(matiere.getCoverImage());

        Label titleLabel = new Label(matiere.getTitre() == null ? "" : matiere.getTitre());
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #0f172a;");

        Label descriptionLabel = new Label(
            matiere.getDescription() == null ? "Aucune description disponible." : matiere.getDescription()
        );
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMaxHeight(40);
        descriptionLabel.setStyle("-fx-text-fill: #475569;");

        FlowPane categoryPreview = createCategoryPreview(matiere.getId());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button btnConsulter = new Button("Consulter le cours");
        btnConsulter.setMaxWidth(Double.MAX_VALUE);
        btnConsulter.setStyle("-fx-background-color: #4f46e5; "
            + "-fx-text-fill: white; "
            + "-fx-font-weight: bold; "
            + "-fx-padding: 10 20; "
            + "-fx-background-radius: 8;");
        btnConsulter.setOnAction(e -> openCoursePlayer(matiere));

        card.getChildren().addAll(coverNode, titleLabel, descriptionLabel, categoryPreview, spacer, btnConsulter);
        return card;
    }

    private Node createCoverNode(String storedCoverPath) {
        Region fallback = createCoverFallback();
        String imageSource = resolveImageSource(storedCoverPath);
        if (imageSource == null) {
            return fallback;
        }

        try {
            Image image = new Image(imageSource, false);
            if (image.isError()) {
                return fallback;
            }

            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(250);
            imageView.setFitHeight(140);
            imageView.setPreserveRatio(false);
            imageView.setSmooth(true);
            return imageView;
        } catch (Exception e) {
            return fallback;
        }
    }

    private Region createCoverFallback() {
        Region fallback = new Region();
        fallback.setStyle("-fx-background-color: #e2e8f0; -fx-pref-width: 250; -fx-pref-height: 140; -fx-background-radius: 8;");
        fallback.setPrefSize(250, 140);
        fallback.setMinSize(250, 140);
        fallback.setMaxSize(250, 140);
        return fallback;
    }

    private String resolveImageSource(String storedPath) {
        if (storedPath == null) {
            return null;
        }

        String path = storedPath.trim();
        if (path.isEmpty()) {
            return null;
        }

        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:")) {
            return path;
        }

        File directFile = new File(path);
        if (directFile.exists()) {
            return directFile.toURI().toString();
        }

        String normalizedPath = path.replace("\\", "/");
        String resourcesBase = System.getProperty("user.dir") + "/src/main/resources";

        File resourceFile = normalizedPath.startsWith("/")
            ? new File(resourcesBase + normalizedPath)
            : new File(resourcesBase, normalizedPath);
        if (resourceFile.exists()) {
            return resourceFile.toURI().toString();
        }

        File projectRelative = new File(System.getProperty("user.dir"), normalizedPath);
        if (projectRelative.exists()) {
            return projectRelative.toURI().toString();
        }

        return null;
    }

    private FlowPane createCategoryPreview(int matiereId) {
        FlowPane preview = new FlowPane();
        preview.setHgap(6);
        preview.setVgap(6);

        List<Category> linkedCategories = categoryService.findByMatiereId(matiereId);
        if (linkedCategories.isEmpty()) {
            preview.setManaged(false);
            preview.setVisible(false);
            return preview;
        }

        for (Category category : linkedCategories) {
            Label chip = new Label(category.getName());
            chip.setStyle("-fx-background-color: #e0e7ff; "
                + "-fx-background-radius: 12; "
                + "-fx-padding: 3 8; "
                + "-fx-text-fill: #3730a3;");
            preview.getChildren().add(chip);
        }

        return preview;
    }

    private void openCoursePlayer(Matiere matiere) {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource(SceneManager.frontofficeView("FrontCoursePlayerView.fxml")));
            Node coursePlayerView = loader.load();

            FrontCoursePlayerController controller = loader.getController();
            controller.setMatiere(matiere);

            // Use ViewNavigator to properly display the course player view
            ViewNavigator navigator = ViewNavigator.getInstance();
            AnchorPane contentPane = navigator.getContentPane();
            
            if (contentPane == null) {
                System.err.println("ERROR: ContentPane is null - ViewNavigator not initialized!");
                showAlert("Erreur", "Le système de navigation n'est pas correctement initialisé.");
                return;
            }

            contentPane.getChildren().clear();
            AnchorPane.setTopAnchor(coursePlayerView, 0.0);
            AnchorPane.setBottomAnchor(coursePlayerView, 0.0);
            AnchorPane.setLeftAnchor(coursePlayerView, 0.0);
            AnchorPane.setRightAnchor(coursePlayerView, 0.0);
            contentPane.getChildren().add(coursePlayerView);

            // Update page title
            Label pageTitle = navigator.getPageTitle();
            if (pageTitle != null) {
                pageTitle.setText("Cours: " + (matiere.getTitre() == null ? "Sans titre" : matiere.getTitre()));
            }

            ApplicationState.getInstance().setCurrentMatiere(matiere);
            ApplicationState.getInstance().setCurrentView(
                "Cours: " + (matiere.getTitre() == null ? "Sans titre" : matiere.getTitre())
            );
        } catch (Exception e) {
            System.err.println("Error opening course player: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            showAlert("Erreur", "Impossible d'ouvrir le lecteur de cours: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
