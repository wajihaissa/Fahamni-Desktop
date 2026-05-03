package tn.esprit.fahamni.services.conference;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

public class UdpVideoSender {

    private static final int HEADER_SIZE = 8;
    private static final int MAX_PACKET_SIZE = 1200;
    private static final int MAX_CHUNK_PAYLOAD = MAX_PACKET_SIZE - HEADER_SIZE;

    private final DatagramSocket socket;
    private final InetAddress targetAddress;
    private final int targetPort;
    private final AtomicInteger frameCounter = new AtomicInteger();

    public UdpVideoSender(String targetIp, int targetPort) throws Exception {
        this.socket = new DatagramSocket();
        this.targetAddress = InetAddress.getByName(targetIp);
        this.targetPort = targetPort;
    }

    public synchronized void sendFrame(BufferedImage sourceFrame) {
        if (sourceFrame == null || socket.isClosed()) {
            return;
        }

        try {
            // Resize first to keep packets small and stable on LAN.
            BufferedImage resizedFrame = resizeFrame(sourceFrame, 320, 240);

            // Encode to JPEG with quality controls to keep under UDP packet size.
            byte[] jpegBytes = encodeJpeg(resizedFrame, 0.45f);

            // If too large, try one more aggressive compression pass.
            if (jpegBytes.length > MAX_CHUNK_PAYLOAD * 65535) {
                BufferedImage smallerFrame = resizeFrame(sourceFrame, 240, 180);
                jpegBytes = encodeJpeg(smallerFrame, 0.35f);
            }

            int totalChunks = (int) Math.ceil((double) jpegBytes.length / MAX_CHUNK_PAYLOAD);
            if (totalChunks <= 0 || totalChunks > 65535) {
                return;
            }

            int frameId = frameCounter.incrementAndGet();
            for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
                int offset = chunkIndex * MAX_CHUNK_PAYLOAD;
                int chunkLength = Math.min(MAX_CHUNK_PAYLOAD, jpegBytes.length - offset);
                byte[] packetBytes = new byte[HEADER_SIZE + chunkLength];

                ByteBuffer.wrap(packetBytes)
                    .putInt(frameId)
                    .putShort((short) chunkIndex)
                    .putShort((short) totalChunks)
                    .put(jpegBytes, offset, chunkLength);

                DatagramPacket packet = new DatagramPacket(
                    packetBytes,
                    packetBytes.length,
                    targetAddress,
                    targetPort
                );
                socket.send(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void close() {
        if (!socket.isClosed()) {
            socket.close();
        }
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG writer found");
        }

        ImageWriter writer = writers.next();
        MemoryCacheImageOutputStream imageOutputStream = new MemoryCacheImageOutputStream(outputStream);
        writer.setOutput(imageOutputStream);

        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        if (writeParam.canWriteCompressed()) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(quality);
        }

        writer.write(null, new IIOImage(image, null, null), writeParam);
        imageOutputStream.close();
        writer.dispose();
        return outputStream.toByteArray();
    }

    private BufferedImage resizeFrame(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return resized;
    }
}
