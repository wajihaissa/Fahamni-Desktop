package tn.esprit.fahamni.utils;

import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;

public final class SceneManager {

    public static final String LOGIN_VIEW = "/com/fahamni/frontoffice/front/LoginView.fxml";
    public static final String MAIN_VIEW = "/com/fahamni/MainView.fxml";
    public static final String BACKOFFICE_MAIN_VIEW = "/com/fahamni/backoffice/front/BackofficeMainView.fxml";
    public static final String FRONTOFFICE_VIEW_BASE = "/com/fahamni/frontoffice/front/";
    public static final String BACKOFFICE_VIEW_BASE = "/com/fahamni/backoffice/front/";

    private SceneManager() {
    }

    public static Scene loadScene(Class<?> resourceOwner, String fxmlPath, double width, double height) throws IOException {
        FXMLLoader loader = new FXMLLoader(resourceOwner.getResource(fxmlPath));
        Scene scene = new Scene(loader.load(), width, height);
        applyTheme(scene);
        return scene;
    }

    public static Node loadView(Class<?> resourceOwner, String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(resourceOwner.getResource(fxmlPath));
        return loader.load();
    }

    public static String frontofficeView(String fxmlFile) {
        return FRONTOFFICE_VIEW_BASE + fxmlFile;
    }

    public static String backofficeView(String fxmlFile) {
        return BACKOFFICE_VIEW_BASE + fxmlFile;
    }

    public static void applyTheme(Scene scene) {
        UiTheme.apply(scene);
    }
}

