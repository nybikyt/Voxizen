package dev.nybikyt.voxizen.commands;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.Holdable;
import com.denizenscript.denizencore.scripts.commands.generator.*;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.nybikyt.voxizen.Voxizen;
import dev.nybikyt.voxizen.objects.VoiceSourceTag;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class AudioCommand extends AbstractCommand implements Holdable {

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
    // bytes — a single Base64-encoded Opus frame or a ListTag of frames.
    //         Required for the PLAY instruction, ignored for STOP.
    //
    // source — a VoiceSourceTag created via <@link command voicesource>.
    //          Determines who hears the audio and from where.
    //
    // @Usage
    // - audio play bytes:<context.bytes> source:<entry[created].voicesource>
    //
    // @Usage
    // - audio play bytes:<[frames]> source:voicesource@myradio
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
        if (bytes == null) {
            Debug.echoError(scriptEntry, "Instruction PLAY requires 'bytes' argument.");
            scriptEntry.setFinished(true);
            return;
        }

        Voxizen.getInstance().getServer().getScheduler().runTaskAsynchronously(Voxizen.getInstance(), () -> {
            try {
                byte[] opusData = resolveBytes(bytes);
                voiceSource.getData().channel().send(opusData);
            } catch (Exception exception) {
                Debug.echoError(scriptEntry, "Audio PLAY error: " + exception.getMessage());
            } finally {
                scriptEntry.setFinished(true);
            }
        });
    }

    private static void handleStop(ScriptEntry scriptEntry, VoiceSourceTag voiceSource) {
        Voxizen.getInstance().getServer().getScheduler().runTaskAsynchronously(Voxizen.getInstance(), () -> {
            try {
                voiceSource.getData().channel().flush();
            } catch (Exception exception) {
                Debug.echoError(scriptEntry, "Audio STOP error: " + exception.getMessage());
            } finally {
                scriptEntry.setFinished(true);
            }
        });
    }


    private static byte[] resolveBytes(ObjectTag bytes) throws Exception {
        if (bytes instanceof ListTag listTag) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            for (ObjectTag frame : listTag.objectForms) {
                buffer.write(Base64.getDecoder().decode(frame.toString()));
            }
            return buffer.toByteArray();
        }
        return Base64.getDecoder().decode(bytes.toString());
    }
}