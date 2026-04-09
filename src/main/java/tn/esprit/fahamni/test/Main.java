package tn.esprit.fahamni.test;

import tn.esprit.fahamni.utils.SceneManager;
import javafx.application.Application;
import javafx.scene.Scene;
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
        Scene scene = SceneManager.loadScene(Main.class, SceneManager.LOGIN_VIEW, 900, 600);
        primaryStage.setScene(scene);
    }

    public static void showMain() throws Exception {
        Scene scene = SceneManager.loadScene(Main.class, SceneManager.MAIN_VIEW, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
    }

    public static void showBackoffice() throws Exception {
        Scene scene = SceneManager.loadScene(Main.class, SceneManager.BACKOFFICE_MAIN_VIEW, 1280, 820);
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
    }

    public static void main(String[] args) {
        launch();
    }
}


