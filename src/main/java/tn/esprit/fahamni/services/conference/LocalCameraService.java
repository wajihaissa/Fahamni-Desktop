package tn.esprit.fahamni.services.conference;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

public class LocalCameraService {

    private OpenCVFrameGrabber frameGrabber;
    private Java2DFrameConverter frameConverter;
    private Thread previewThread;
    private volatile boolean running;

    /**
     * Starts camera capture on a background thread and pushes JavaFX images to the callback.
     */
    public void startPreview(Consumer<Image> onFrameReady) {
        startPreview(onFrameReady, null);
    }

    /**
     * Starts camera capture on a background thread.
     * onFrameReady: pushes JavaFX Image for local UI preview.
     * onRawFrameReady: pushes BufferedImage for network processing (UDP sender).
     */
    public void startPreview(Consumer<Image> onFrameReady, Consumer<BufferedImage> onRawFrameReady) {
        if (running) {
            return;
        }

        running = true;

        previewThread = new Thread(() -> {
            frameConverter = new Java2DFrameConverter();
            frameGrabber = new OpenCVFrameGrabber(0); // 0 = default webcam

            try {
                // Keep defaults simple; these can be tuned later.
                frameGrabber.setImageWidth(640);
                frameGrabber.setImageHeight(480);
                frameGrabber.setFrameRate(24);
                frameGrabber.start();

                while (running) {
                    // Grab one frame from the camera.
                    Frame frame = frameGrabber.grab();
                    if (frame == null) {
                        continue;
                    }

                    // Convert JavaCV Frame -> BufferedImage.
                    BufferedImage bufferedImage = frameConverter.convert(frame);
                    if (bufferedImage == null) {
                        continue;
                    }

                    // Give raw frames to networking code (if provided).
                    if (onRawFrameReady != null) {
                        onRawFrameReady.accept(bufferedImage);
                    }

                    // Convert BufferedImage -> JavaFX Image.
                    Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);

                    // Update UI safely on JavaFX Application Thread.
                    Platform.runLater(() -> onFrameReady.accept(fxImage));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                stopInternal();
            }
        }, "local-camera-preview-thread");

        previewThread.setDaemon(true);
        previewThread.start();
    }

    /**
     * Stops capture and releases native camera resources.
     */
    public void stopPreview() {
        running = false;
        if (previewThread != null) {
            previewThread.interrupt();
        }
    }

    private void stopInternal() {
        try {
            if (frameGrabber != null) {
                frameGrabber.stop();
                frameGrabber.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            frameGrabber = null;
            frameConverter = null;
            previewThread = null;
        }
    }
}
