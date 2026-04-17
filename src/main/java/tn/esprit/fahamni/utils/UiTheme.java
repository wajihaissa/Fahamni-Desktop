package tn.esprit.fahamni.utils;

import java.net.URL;
import javafx.scene.Parent;
import javafx.scene.Scene;

public final class UiTheme {

    private UiTheme() {
    }

    static void apply(Scene scene, String... stylesheetPaths) {
        if (scene == null) {
            return;
        }

        applyStylesheetContainer(scene.getStylesheets(), stylesheetPaths);
    }

    static void apply(Parent parent, String... stylesheetPaths) {
        if (parent == null) {
            return;
        }

        applyStylesheetContainer(parent.getStylesheets(), stylesheetPaths);
    }

    private static void applyStylesheetContainer(java.util.List<String> stylesheets, String... stylesheetPaths) {
        stylesheets.removeIf(existing -> existing.contains("/com/fahamni/styles/"));

        if (stylesheetPaths == null) {
            return;
        }

        for (String stylesheetPath : stylesheetPaths) {
            if (stylesheetPath == null || stylesheetPath.isBlank()) {
                continue;
            }

            URL stylesheet = UiTheme.class.getResource(stylesheetPath);
            if (stylesheet == null) {
                throw new IllegalStateException("Unable to locate stylesheet: " + stylesheetPath);
            }

            String stylesheetUri = stylesheet.toExternalForm();
            if (!stylesheets.contains(stylesheetUri)) {
                stylesheets.add(stylesheetUri);
            }
        }
    }
}
