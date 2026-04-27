package tn.esprit.fahamni.services.conference;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javax.imageio.ImageIO;

public class UdpVideoReceiver {

    private static final int HEADER_SIZE = 8;
    private static final long FRAME_TTL_MILLIS = 3000L;

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
            Map<Integer, FrameAssembly> pendingFrames = new HashMap<>();

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    socket.receive(packet);
                    cleanupExpiredFrames(pendingFrames);

                    if (packet.getLength() <= HEADER_SIZE) {
                        continue;
                    }

                    ByteBuffer header = ByteBuffer.wrap(packet.getData(), 0, HEADER_SIZE);
                    int frameId = header.getInt();
                    int chunkIndex = Short.toUnsignedInt(header.getShort());
                    int totalChunks = Short.toUnsignedInt(header.getShort());
                    if (totalChunks <= 0 || chunkIndex >= totalChunks) {
                        continue;
                    }

                    FrameAssembly assembly = pendingFrames.computeIfAbsent(frameId, id -> new FrameAssembly(totalChunks));
                    if (assembly.totalChunks != totalChunks) {
                        pendingFrames.remove(frameId);
                        continue;
                    }

                    int dataLength = packet.getLength() - HEADER_SIZE;
                    assembly.addChunk(chunkIndex, Arrays.copyOfRange(packet.getData(), HEADER_SIZE, HEADER_SIZE + dataLength));
                    if (!assembly.isComplete()) {
                        continue;
                    }

                    pendingFrames.remove(frameId);
                    BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(assembly.join()));
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

    private void cleanupExpiredFrames(Map<Integer, FrameAssembly> pendingFrames) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, FrameAssembly>> iterator = pendingFrames.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, FrameAssembly> entry = iterator.next();
            if (now - entry.getValue().createdAt > FRAME_TTL_MILLIS) {
                iterator.remove();
            }
        }
    }

    private static class FrameAssembly {
        private final int totalChunks;
        private final byte[][] chunks;
        private final boolean[] received;
        private final long createdAt = System.currentTimeMillis();
        private int receivedCount;

        private FrameAssembly(int totalChunks) {
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
            this.received = new boolean[totalChunks];
        }

        private void addChunk(int index, byte[] data) {
            if (received[index]) {
                return;
            }
            chunks[index] = data;
            received[index] = true;
            receivedCount++;
        }

        private boolean isComplete() {
            return receivedCount == totalChunks;
        }

        private byte[] join() throws Exception {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (byte[] chunk : chunks) {
                output.write(chunk);
            }
            return output.toByteArray();
        }
    }
}
