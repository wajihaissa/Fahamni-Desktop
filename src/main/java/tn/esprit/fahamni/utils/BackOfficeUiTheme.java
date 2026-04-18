package tn.esprit.fahamni.utils;

import javafx.scene.Scene;

public final class BackOfficeUiTheme {

    private static final String STYLESHEET = "/com/fahamni/styles/backoffice-theme.css";

    private BackOfficeUiTheme() {
    }

    public static void apply(Scene scene) {
        UiTheme.apply(scene, STYLESHEET);
    }
}
