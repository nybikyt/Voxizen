package dev.nybikyt.voxizen.commands;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.BinaryTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.Holdable;
import com.denizenscript.denizencore.scripts.commands.generator.*;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.nybikyt.voxizen.VoiceAddon;
import dev.nybikyt.voxizen.objects.VoiceSourceTag;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AudioCommand extends AbstractCommand implements Holdable {

    public static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    public static final Map<String, ScheduledFuture<?>> activeTasks = new ConcurrentHashMap<>();

    private static final AudioFormat OPUS_FORMAT = new AudioFormat(48000, 16, 1, true, false);
    private static final int FRAME_SIZE = 960; // 20ms @ 48kHz

    public enum Instruction { PLAY, STOP }

    public AudioCommand() {
        setName("audio");
        setSyntax("audio [play/stop] (bytes:<base64>|...) [source:<sourceTag>]");
        setRequiredArguments(2, 3);
        isProcedural = false;
        autoCompile();
    }

    // <--[command]
    // @Name audio
    // @Syntax audio [play/stop] (bytes:<base64>|...) [source:<sourceTag>]
    // @Required 2
    // @Maximum 3
    // @Short Plays or stops Opus audio on a persistent voice source.
    // @Group Voxizen
    //
    // @Description
    // Sends Opus audio frames to a persistent voice source created by <@link command voicesource>,
    // or flushes and stops it.
    //
    // bytes — one of:
    //         BinaryTag (raw WAV bytes) — automatically encoded to Opus asynchronously.
    //         ListTag of Base64-encoded Opus frames — sent with 20ms interval.
    //         Single Base64-encoded Opus frame — sent immediately (e.g. from microphone event).
    //         Required for the PLAY instruction, ignored for STOP.
    //
    // source — a VoiceSourceTag created via <@link command voicesource>.
    //          Determines who hears the audio and from where.
    //
    // @Usage
    // # Play a WAV file — encoding and sending happens fully asynchronously.
    // - ~fileread path:data/my.wav save:read
    // - ~audio play bytes:<entry[read].data> source:voicesource@myradio
    //
    // @Usage
    // # Send a single frame from a microphone event (no delay needed).
    // - audio play bytes:<context.bytes> source:<[src]>
    //
    // @Usage
    // - audio stop source:voicesource@myradio
    //
    // -->

    public static void autoExecute(
            ScriptEntry scriptEntry,
            @ArgName("instruction") Instruction instruction,
            @ArgPrefixed @ArgDefaultNull @ArgName("bytes") ObjectTag bytes,
            @ArgPrefixed @ArgName("source") VoiceSourceTag voiceSource
    ) {
        switch (instruction) {
            case PLAY -> handlePlay(scriptEntry, bytes, voiceSource);
            case STOP -> handleStop(scriptEntry, voiceSource);
        }
    }

    private static void handlePlay(ScriptEntry scriptEntry, ObjectTag bytes, VoiceSourceTag voiceSource) {
        switch (bytes) {
            case null -> {
                Debug.echoError(scriptEntry, "Instruction PLAY requires 'bytes' argument.");
                scriptEntry.setFinished(true);
            }
            case BinaryTag binaryTag -> SCHEDULER.execute(() -> {
                try {
                    List<byte[]> frames = encodeWavToOpusFrames(binaryTag.data);
                    scheduleFrames(scriptEntry, voiceSource, frames);
                } catch (Exception e) {
                    Debug.echoError(scriptEntry, "Audio PLAY encode error: " + e.getMessage());
                    scriptEntry.setFinished(true);
                }
            });
            case ListTag listTag -> SCHEDULER.execute(() -> {
                try {
                    List<byte[]> frames = new ArrayList<>();
                    for (ObjectTag frame : listTag.objectForms) {
                        frames.add(Base64.getDecoder().decode(frame.toString()));
                    }
                    scheduleFrames(scriptEntry, voiceSource, frames);
                } catch (Exception e) {
                    Debug.echoError(scriptEntry, "Audio PLAY error: " + e.getMessage());
                    scriptEntry.setFinished(true);
                }
            });
            default -> SCHEDULER.execute(() -> {
                try {
                    voiceSource.getData().channel().send(Base64.getDecoder().decode(bytes.toString()));
                } catch (Exception e) {
                    Debug.echoError(scriptEntry, "Audio PLAY error: " + e.getMessage());
                } finally {
                    scriptEntry.setFinished(true);
                }
            });
        }
    }

    private static void handleStop(ScriptEntry scriptEntry, VoiceSourceTag voiceSource) {
        SCHEDULER.execute(() -> {
            try {
                cancelTask(voiceSource.getId());
                voiceSource.getData().channel().flush();
            } catch (Exception e) {
                Debug.echoError(scriptEntry, "Audio STOP error: " + e.getMessage());
            } finally {
                scriptEntry.setFinished(true);
            }
        });
    }


    private static void scheduleFrames(ScriptEntry scriptEntry, VoiceSourceTag voiceSource, List<byte[]> frames) {
        cancelTask(voiceSource.getId());
        AtomicInteger index = new AtomicInteger(0);
        ScheduledFuture<?>[] future = new ScheduledFuture<?>[1];

        future[0] = SCHEDULER.scheduleAtFixedRate(() -> {
            int i = index.getAndIncrement();

            if (i >= frames.size()) {
                cancelTask(voiceSource.getId());
                scriptEntry.setFinished(true);
                return;
            }

            try {
                voiceSource.getData().channel().send(frames.get(i));
            } catch (Exception e) {
                Debug.echoError(scriptEntry, "Audio PLAY send error: " + e.getMessage());
                cancelTask(voiceSource.getId());
                scriptEntry.setFinished(true);
            }
        }, 0, 20, TimeUnit.MILLISECONDS);

        activeTasks.put(voiceSource.getId(), future[0]);
    }

    private static void cancelTask(String sourceId) {
        ScheduledFuture<?> task = activeTasks.remove(sourceId);
        if (task != null) {
            task.cancel(false);
        }
    }

    private static List<byte[]> encodeWavToOpusFrames(byte[] data) throws Exception {
        boolean isWav = data.length > 4
                && data[0] == 'R' && data[1] == 'I'
                && data[2] == 'F' && data[3] == 'F';

        AudioInputStream pcm;

        if (isWav) {
            AudioInputStream raw = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
            pcm = AudioSystem.getAudioInputStream(OPUS_FORMAT, raw);
        } else {
            pcm = new AudioInputStream(
                    new ByteArrayInputStream(data),
                    OPUS_FORMAT,
                    data.length / 2L
            );
        }

        var encoder = VoiceAddon.getApi().createEncoder();
        List<byte[]> frames = new ArrayList<>();

        try {
            byte[] buffer = new byte[FRAME_SIZE * 2];
            int read;
            while ((read = pcm.read(buffer)) > 0) {
                if (read < buffer.length) Arrays.fill(buffer, read, buffer.length, (byte) 0);
                short[] pcmShorts = VoiceAddon.getApi().getAudioConverter().bytesToShorts(buffer);
                frames.add(encoder.encode(pcmShorts));
            }
        } finally {
            encoder.close();
        }

        return frames;
    }
}