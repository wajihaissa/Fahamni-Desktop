package tn.esprit.fahamni.controllers;

import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import javafx.stage.Window;
import tn.esprit.fahamni.services.conference.LocalCameraService;

public class VideoChatController {

    @FXML
    private ImageView localCameraView;

    private final LocalCameraService localCameraService = new LocalCameraService();

    @FXML
    private void initialize() {
        // Start local loopback preview: camera frames are rendered to the ImageView.
        localCameraService.startPreview(localCameraView::setImage);

        // Ensure camera is released when this window closes.
        localCameraView.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            Window window = newScene.getWindow();
            if (window != null) {
                window.setOnHidden(event -> localCameraService.stopPreview());
            }
            newScene.windowProperty().addListener((obsWindow, oldWindow, currentWindow) -> {
                if (currentWindow != null) {
                    currentWindow.setOnHidden(event -> localCameraService.stopPreview());
                }
            });
        });
    }

    @FXML
    private void stopPreview() {
        localCameraService.stopPreview();
    }
}
