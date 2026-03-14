package dev.nybikyt.voxizen.misc;

import de.maxhenkel.voicechat.api.audio.AudioConverter;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import dev.nybikyt.voxizen.VoiceAddon;
import dev.nybikyt.voxizen.Voxizen;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VoskService {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final Model model;
    private final OpusDecoder decoder;
    private final AudioConverter converter;

    public VoskService(String modelName, String modelUrl) throws Exception {

        Path modelPath = Voxizen.getInstance().getDataFolder().toPath()
                .resolve("models")
                .resolve(modelName);

        if (Files.notExists(modelPath)) {
            if (modelUrl == null) {
                throw new IllegalStateException("Model '%s' not found and no URL provided!".formatted(modelName));
            }
            downloadAndExtract(modelName, modelUrl, modelPath.getParent());
        }

        this.model = new Model(modelPath.toString());
        this.decoder = VoiceAddon.getApi().createDecoder();
        this.converter = VoiceAddon.getApi().getAudioConverter();
    }

    private void downloadAndExtract(String modelName, String url, Path modelsDir) throws Exception {
        Files.createDirectories(modelsDir);
        Path zipPath = modelsDir.resolve(modelName + ".zip");

        Voxizen.LOGGER.info("Downloading Vosk model '%s'...".formatted(modelName));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HTTP.send(request, HttpResponse.BodyHandlers.ofFile(zipPath));

        Voxizen.LOGGER.info("Extracting model...");

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path outPath = modelsDir.resolve(entry.getName()).normalize();
                if (!outPath.startsWith(modelsDir)) {
                    throw new SecurityException("Zip path traversal detected: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zip, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }

        Files.delete(zipPath);
        Voxizen.LOGGER.info("Vosk model '%s' downloaded and extracted!".formatted(modelName));
    }

    public String recognize(byte[] opusData) throws Exception {
        short[] pcm48k = decoder.decode(opusData);

        short[] pcm16k = new short[pcm48k.length / 3];
        for (int i = 0; i < pcm16k.length; i++) {
            pcm16k[i] = pcm48k[i * 3];
        }

        byte[] pcmBytes = converter.shortsToBytes(pcm16k);

        try (Recognizer recognizer = new Recognizer(model, 16000f)) {
            recognizer.acceptWaveForm(pcmBytes, pcmBytes.length);
            return new JSONObject(recognizer.getFinalResult()).getString("text");
        }
    }

    public String recognize(List<byte[]> opusFrames) throws Exception {
        ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();

        for (byte[] frame : opusFrames) {
            short[] pcm48k = decoder.decode(frame);
            short[] pcm16k = new short[pcm48k.length / 3];
            for (int i = 0; i < pcm16k.length; i++) {
                pcm16k[i] = pcm48k[i * 3];
            }
            pcmBuffer.write(converter.shortsToBytes(pcm16k));
        }

        byte[] pcmBytes = pcmBuffer.toByteArray();

        try (Recognizer recognizer = new Recognizer(model, 16000f)) {
            recognizer.acceptWaveForm(pcmBytes, pcmBytes.length);
            return new JSONObject(recognizer.getFinalResult()).getString("text");
        }
    }

    public void close() {
        model.close();
        decoder.close();
    }
}