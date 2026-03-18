package dev.nybikyt.voxizen.events;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import org.bukkit.entity.Player;

public class VoiceDistanceEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // player voice distance calculated
    //
    // @Group Voxizen
    //
    // @Cancellable true
    //
    // @Triggers when a microphone packet arrives at the server and the voice distance is processed.
    //
    // @Context
    // <context.distance> returns an ElementTag(Decimal) of the current voice distance.
    // <context.is_whispering> returns an ElementTag(Boolean) of whether the player is whispering.
    //
    // @Determine
    // "ElementTag(Decimal)" to set a custom voice distance.
    //
    // -->

    public static VoiceDistanceEvent instance;
    private de.maxhenkel.voicechat.api.events.VoiceDistanceEvent voiceDistanceEvent;
    private Player player;
    private float distance;
    private boolean isWhispering;

    public VoiceDistanceEvent() {
        instance = this;
        registerCouldMatcher("player voice distance calculated");

        this.<VoiceDistanceEvent, ElementTag>registerOptionalDetermination(null, ElementTag.class,
                (event, context, value) -> {
                    if (!value.isFloat()) return false;
                    event.distance = value.asFloat();
                    return true;
                });
    }

    @Override
    public ObjectTag getContext(String name) {
        return switch (name) {
            case "distance" -> new ElementTag(distance);
            case "is_whispering" -> new ElementTag(isWhispering);
            default -> super.getContext(name);
        };
    }

    @Override
    public ScriptEntryData getScriptEntryData() {
        return new BukkitScriptEntryData(new PlayerTag(player), null);
    }

    @Override
    public void cancellationChanged() {
        if (cancelled && voiceDistanceEvent != null) {
            voiceDistanceEvent.cancel();
        }
        super.cancellationChanged();
    }

    public void handle(de.maxhenkel.voicechat.api.events.VoiceDistanceEvent event) {
        this.voiceDistanceEvent = event;
        this.distance = event.getDistance();
        this.isWhispering = event.getPacket().isWhispering();
        this.player = (Player) event.getSenderConnection().getPlayer().getPlayer();

        fire();

        if (!cancelled) {
            event.setDistance(distance);
        }
    }
}