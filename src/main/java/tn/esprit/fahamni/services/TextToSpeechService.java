package tn.esprit.fahamni.services;

public class TextToSpeechService {

    private Process currentProcess;
    private volatile boolean speaking = false;

    public void speak(String text) {
        stop();
        if (text == null || text.isBlank()) return;

        // Sanitize : supprimer tout caractère dangereux pour PowerShell
        String safe = text
                .replace("'",  " ").replace("`",  " ")
                .replace("\"", " ").replace("$",  " ")
                .replace("\r", " ").replace("\n", " ")
                .replace(";",  " ").replace("&",  " ")
                .replace("|",  " ").replace("<",  " ")
                .replace(">",  " ").replace("(",  " ")
                .replace(")",  " ").replace("{",  " ")
                .replace("}",  " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (safe.isEmpty()) return;
        if (safe.length() > 900) safe = safe.substring(0, 900);

        String script =
            "Add-Type -AssemblyName System.Speech; " +
            "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
            "try { $s.SelectVoiceByHints('Female','Adult',0," +
            "[System.Globalization.CultureInfo]::new('fr-FR')) } catch {}; " +
            "$s.Rate = 1; " +
            "$s.Speak('" + safe + "');";

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NonInteractive", "-NoProfile", "-Command", script);
            pb.redirectErrorStream(true);
            currentProcess = pb.start();
            speaking = true;
            new Thread(() -> {
                try { currentProcess.waitFor(); } catch (Exception ignored) {}
                speaking = false;
            }, "tts-monitor").start();
        } catch (Exception e) {
            System.err.println("TTS: " + e.getMessage());
            speaking = false;
        }
    }

    public void stop() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
        }
        speaking = false;
    }

    public boolean isSpeaking() { return speaking; }
}
