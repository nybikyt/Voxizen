package dev.nybikyt.voxizen.events;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Base64;

public class MicrophoneScriptEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // microphone
    //
    // @Group Voxizen
    //
    // @Triggers when a player sends a microphone packet via Simple Voice Chat.
    //
    // @Context
    // <context.is_whispering> returns an ElementTag(Boolean) of whether the player is whispering.
    // <context.bytes> returns an ElementTag of the Base64-encoded Opus audio data.
    //
    // -->

    public static MicrophoneScriptEvent instance;

    public MicrophoneScriptEvent() {
        instance = this;
        registerCouldMatcher("microphone");
    }

    private boolean isWhispering;
    private byte[] opusData;
    private Player player;


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

    public void handle(MicrophonePacketEvent event) {
        this.isWhispering = event.getPacket().isWhispering();
        this.opusData = event.getPacket().getOpusEncodedData();
        this.player = (Player) event.getSenderConnection().getPlayer().getPlayer();
        fire();
    }
}