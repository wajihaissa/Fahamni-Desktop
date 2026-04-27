package tn.esprit.fahamni.services.conference;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

public class UdpAudioReceiver {

    // Keep identical to sender format.
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(16_000f, 16, 1, true, false);

    private final int listenPort;
    private DatagramSocket socket;
    private SourceDataLine speakerLine;
    private Thread receiveThread;
    private volatile boolean running;

    public UdpAudioReceiver(int listenPort) {
        this.listenPort = listenPort;
    }

    public void start() throws Exception {
        if (running) {
            return;
        }

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
        speakerLine = (SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
        speakerLine.open(AUDIO_FORMAT);
        speakerLine.start();

        socket = new DatagramSocket(listenPort);
        running = true;

        receiveThread = new Thread(() -> {
            byte[] receiveBuffer = new byte[2048];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    socket.receive(packet);
                    speakerLine.write(packet.getData(), 0, packet.getLength());
                } catch (Exception e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }
        }, "udp-audio-receiver-thread");

        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    public void stop() {
        running = false;

        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
        }

        if (speakerLine != null) {
            speakerLine.stop();
            speakerLine.close();
            speakerLine = null;
        }
    }
}
