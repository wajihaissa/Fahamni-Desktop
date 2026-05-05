package tn.esprit.fahamni.utils;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Worker;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Window;
import netscape.javascript.JSObject;

import java.util.Optional;

public final class ReCaptchaDialog {

    private ReCaptchaDialog() {
    }

    public static Optional<String> requestToken(Window owner, String siteKey) {
        if (siteKey == null || siteKey.isBlank()) {
            return Optional.empty();
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Verification reCAPTCHA");
        dialog.setHeaderText("Confirmez que vous n'etes pas un robot");

        ButtonType cancelButtonType = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType verifyButtonType = new ButtonType("Continuer", ButtonBar.ButtonData.OK_DONE);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().setAll(cancelButtonType, verifyButtonType);
        dialogPane.getStyleClass().add("login-dialog-pane");
        dialogPane.setPrefWidth(430);
        dialogPane.setPrefHeight(420);

        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.setPrefSize(360, 300);

        VBox content = new VBox(webView);
        content.getStyleClass().add("login-dialog-content");
        VBox.setVgrow(webView, Priority.ALWAYS);
        dialogPane.setGraphic(null);
        dialogPane.setContent(content);

        StringProperty tokenProperty = new SimpleStringProperty("");
        Button verifyButton = (Button) dialogPane.lookupButton(verifyButtonType);
        verifyButton.disableProperty().bind(tokenProperty.isEmpty());

        WebEngine engine = webView.getEngine();
        engine.getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("fahamniCaptcha", new Bridge(tokenProperty));
            }
        });
        engine.loadContent(buildChallengeHtml(siteKey));

        dialog.setResultConverter(buttonType -> buttonType == verifyButtonType ? tokenProperty.get() : null);

        if (owner != null) {
            dialog.initOwner(owner);
        }

        return dialog.showAndWait().filter(token -> token != null && !token.isBlank());
    }

    private static String buildChallengeHtml(String siteKey) {
        String safeSiteKey = escapeHtml(siteKey);
        return """
            <!doctype html>
            <html>
            <head>
                <meta charset="UTF-8">
                <script src="https://www.google.com/recaptcha/api.js" async defer></script>
                <style>
                    body {
                        margin: 0;
                        min-height: 280px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        background: transparent;
                        font-family: Arial, sans-serif;
                    }
                    .wrap {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        gap: 14px;
                    }
                    .hint {
                        color: #d6e3ff;
                        font-size: 13px;
                        text-align: center;
                    }
                </style>
            </head>
            <body>
                <div class="wrap">
                    <div class="g-recaptcha" data-sitekey="%s" data-callback="captchaSolved" data-expired-callback="captchaExpired"></div>
                    <div class="hint">La validation active le bouton Continuer.</div>
                </div>
                <script>
                    function captchaSolved(token) {
                        window.fahamniCaptcha.solved(token);
                    }
                    function captchaExpired() {
                        window.fahamniCaptcha.expired();
                    }
                </script>
            </body>
            </html>
            """.formatted(safeSiteKey);
    }

    private static String escapeHtml(String value) {
        return value == null
            ? ""
            : value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static class Bridge {
        private final StringProperty tokenProperty;

        public Bridge(StringProperty tokenProperty) {
            this.tokenProperty = tokenProperty;
        }

        public void solved(String token) {
            Platform.runLater(() -> tokenProperty.set(token == null ? "" : token));
        }

        public void expired() {
            Platform.runLater(() -> tokenProperty.set(""));
        }
    }
}
