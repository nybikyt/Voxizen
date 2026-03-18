package dev.nybikyt.voxizen.events;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class PlayerMicrophoneEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // player microphone
    //
    // @Group Voxizen
    //
    // @Cancellable true
    //
    // @Triggers when a player sends a microphone packet via Simple Voice Chat.
    //
    // @Context
    // <context.is_whispering> returns an ElementTag(Boolean) of whether the player is whispering.
    // <context.bytes> returns an ElementTag of the Base64-encoded Opus audio data.
    //
    // @Determine
    // "ElementTag" to replace the Opus audio bytes (Base64-encoded)
    //
    // @Player Always.
    //
    // -->

    public static PlayerMicrophoneEvent instance;
    private MicrophonePacketEvent microphonePacketEvent;

    public PlayerMicrophoneEvent() {
        instance = this;
        registerCouldMatcher("player microphone");

        this.<PlayerMicrophoneEvent, ObjectTag>registerOptionalDetermination(null, ObjectTag.class,
                (playerMicrophoneEvent, context, value) -> {
                    try {
                        playerMicrophoneEvent.opusData = resolveBytes(value);
                        return true;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private boolean isWhispering;
    private byte[] opusData;
    private Player player;

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

    @Override
    public ObjectTag getContext(String name) {
        return switch (name) {
            case "is_whispering" -> new ElementTag(isWhispering);
            case "bytes" -> new ElementTag(Base64.getEncoder().encodeToString(opusData));
            default -> super.getContext(name);
        };
    }

    @Override
    public ScriptEntryData getScriptEntryData() {
        return new BukkitScriptEntryData(new PlayerTag(player), null);
    }

    @Override
    public void cancellationChanged() {
        if (microphonePacketEvent != null) {
            microphonePacketEvent.cancel();
        }
        super.cancellationChanged();
    }

    public void handle(MicrophonePacketEvent event) {
        this.microphonePacketEvent = event;
        this.isWhispering = event.getPacket().isWhispering();
        this.opusData = event.getPacket().getOpusEncodedData();
        this.player = (Player) event.getSenderConnection().getPlayer().getPlayer();

        fire();
        if (!cancelled) event.getPacket().setOpusEncodedData(opusData);
    }
}