package tn.esprit.fahamni.test;

import tn.esprit.fahamni.utils.SceneManager;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class Main extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        showLogin();
        stage.setTitle("Fahamni - Educational Platform");
        stage.show();
    }

    public static void showLogin() throws Exception {
        showResponsiveView(SceneManager.LOGIN_VIEW, 900, 600);
    }

    public static void showMain() throws Exception {
        showResponsiveView(SceneManager.MAIN_VIEW, 1440, 860);
    }

    public static void showBackoffice() throws Exception {
        showResponsiveView(SceneManager.BACKOFFICE_MAIN_VIEW, 1360, 840);
    }

    private static void showResponsiveView(String fxmlPath, double designWidth, double designHeight) throws Exception {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double availableWidth = Math.max(640, bounds.getWidth() - 48);
        double availableHeight = Math.max(520, bounds.getHeight() - 56);
        double sceneWidth = Math.min(designWidth, availableWidth);
        double sceneHeight = Math.min(designHeight, availableHeight);

        Scene scene = SceneManager.loadScene(Main.class, fxmlPath, sceneWidth, sceneHeight);
        primaryStage.setMaximized(false);
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setWidth(sceneWidth);
        primaryStage.setHeight(sceneHeight);
        primaryStage.centerOnScreen();
    }

    public static void main(String[] args) {
        launch();
    }
}


