package dev.nybikyt.voxizen.commands;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.Holdable;
import com.denizenscript.denizencore.scripts.commands.generator.ArgName;
import com.denizenscript.denizencore.scripts.commands.generator.ArgPrefixed;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import dev.nybikyt.voxizen.Voxizen;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class VoskCommand extends AbstractCommand implements Holdable {

    public VoskCommand() {
        setName("vosk");
        setSyntax("vosk [bytes:<base64>|...]");
        setRequiredArguments(1, 1);
        isProcedural = false;
        autoCompile();
    }

    // <--[command]
    // @Name vosk
    // @Syntax vosk [bytes:<base64>|...]
    // @Required 1
    // @Maximum 1
    // @Short Transcribes Opus audio data using Vosk.
    // @Group Voxizen
    //
    // @Description
    // Transcribes Base64-encoded Opus audio data using the Vosk speech recognition engine.
    // The bytes argument should be the Base64-encoded Opus audio data.
    // The command should be ~waited for. See <@link language ~waitable>.
    //
    // @Tags
    // <entry[saveName].text> returns the transcribed text as an ElementTag.
    //
    // @Usage
    // Use to transcribe audio from a microphone event.
    // on microphone:
    //   - ~vosk bytes:<[bytes]> save:vosk
    //   - narrate <entry[vosk].text>
    //
    // -->

    public static void autoExecute(ScriptEntry scriptEntry, @ArgPrefixed @ArgName("bytes") ObjectTag bytes) {
        Runnable task = () -> {
            try {
                String text;

                if (bytes instanceof ListTag list) {
                    List<byte[]> frames = new ArrayList<>();
                    for (ObjectTag entry : list.objectForms) {
                        frames.add(Base64.getDecoder().decode(entry.toString()));
                    }
                    text = Voxizen.getInstance().getVoskService().recognize(frames);

                } else {
                    text = Voxizen.getInstance().getVoskService().recognize(
                            Base64.getDecoder().decode(bytes.toString())
                    );
                }

                scriptEntry.saveObject("text", new ElementTag(text));
            } catch (Exception e) {
                Debug.echoError(scriptEntry, "Vosk error: " + e.getMessage());
                scriptEntry.saveObject("text", new ElementTag(""));
            } finally {
                scriptEntry.setFinished(true);
            }
        };
        Voxizen.getInstance().getServer().getScheduler().runTaskAsynchronously(Voxizen.getInstance(), task);
    }
}