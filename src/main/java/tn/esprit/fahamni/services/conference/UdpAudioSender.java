package tn.esprit.fahamni.services.conference;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

public class UdpAudioSender {

    // 16kHz, 16-bit, mono, signed PCM, little-endian
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(16_000f, 16, 1, true, false);
    // 20ms chunk: 16000 samples/sec * 2 bytes/sample * 0.02 sec = 640 bytes
    private static final int AUDIO_PACKET_SIZE = 640;

    private final InetAddress targetAddress;
    private final int targetPort;

    private DatagramSocket socket;
    private TargetDataLine microphoneLine;
    private Thread sendThread;
    private volatile boolean running;

    public UdpAudioSender(String targetIp, int targetPort) throws Exception {
        this.targetAddress = InetAddress.getByName(targetIp);
        this.targetPort = targetPort;
    }

    public void start() throws Exception {
        if (running) {
            return;
        }

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        microphoneLine = (TargetDataLine) javax.sound.sampled.AudioSystem.getLine(info);
        microphoneLine.open(AUDIO_FORMAT);
        microphoneLine.start();

        socket = new DatagramSocket();
        running = true;

        sendThread = new Thread(() -> {
            byte[] audioBuffer = new byte[AUDIO_PACKET_SIZE];

            while (running) {
                try {
                    int bytesRead = microphoneLine.read(audioBuffer, 0, audioBuffer.length);
                    if (bytesRead <= 0) {
                        continue;
                    }

                    DatagramPacket packet = new DatagramPacket(audioBuffer, bytesRead, targetAddress, targetPort);
                    socket.send(packet);
                } catch (Exception e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }
        }, "udp-audio-sender-thread");

        sendThread.setDaemon(true);
        sendThread.start();
    }

    public void stop() {
        running = false;

        if (microphoneLine != null) {
            microphoneLine.stop();
            microphoneLine.close();
            microphoneLine = null;
        }

        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
        }
    }
}
