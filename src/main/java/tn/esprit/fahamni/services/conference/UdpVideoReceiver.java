package tn.esprit.fahamni.services.conference;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javax.imageio.ImageIO;

public class UdpVideoReceiver {

    private final int listenPort;
    private DatagramSocket socket;
    private Thread receiveThread;
    private volatile boolean running;

    public UdpVideoReceiver(int listenPort) {
        this.listenPort = listenPort;
    }

    /**
     * Starts listening for JPEG UDP packets and pushes decoded JavaFX Images to callback.
     */
    public void start(Consumer<Image> onFrameReceived) throws Exception {
        if (running) {
            return;
        }

        socket = new DatagramSocket(listenPort);
        running = true;

        receiveThread = new Thread(() -> {
            byte[] receiveBuffer = new byte[65_507]; // Max UDP payload buffer.

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    socket.receive(packet);
                    System.out.println("[VIDEO RX] Received frame: " + packet.getLength() + " bytes");

                    BufferedImage bufferedImage = ImageIO.read(
                        new ByteArrayInputStream(packet.getData(), 0, packet.getLength())
                    );
                    if (bufferedImage == null) {
                        continue;
                    }

                    Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
                    Platform.runLater(() -> onFrameReceived.accept(fxImage));
                } catch (Exception e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }
        }, "udp-video-receiver-thread");

        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = null;
    }
}
