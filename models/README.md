# Offline Voice Assistant Model

Put a Vosk model folder here and name it:

```text
models/vosk-model
```

Recommended free starter model:

```text
vosk-model-small-en-us-0.15
```

After downloading and extracting it, rename the extracted folder to `vosk-model`.

The app already checks this path when you press `Listen` in the assistant panel. It still needs the Vosk Java dependency on the classpath before live speech recognition can run.
