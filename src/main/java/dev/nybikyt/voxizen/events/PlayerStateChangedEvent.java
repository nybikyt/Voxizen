package dev.nybikyt.voxizen.events;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlayerStateChangedEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // player changes voice state
    //
    // @Group Voxizen
    //
    // @Cancellable true
    //
    // @Triggers when a player connects/disconnects, joins/leaves a group, or disables voice chat.
    //
    // @Context
    // <context.is_disabled> returns an ElementTag(Boolean) of whether the player has voice chat disabled.
    // <context.is_disconnected> returns an ElementTag(Boolean) of whether the player is disconnected from voice chat.
    //
    // @Player Always.
    //
    // -->

    public static PlayerStateChangedEvent instance;
    private de.maxhenkel.voicechat.api.events.PlayerStateChangedEvent playerStateChangedEvent;
    private Player player;
    private boolean isDisabled;
    private boolean isDisconnected;

    public PlayerStateChangedEvent() {
        instance = this;
        registerCouldMatcher("player changes voice state");
    }

    @Override
    public ObjectTag getContext(String name) {
        return switch (name) {
            case "is_disabled" -> new ElementTag(isDisabled);
            case "is_disconnected" -> new ElementTag(isDisconnected);
            default -> super.getContext(name);
        };
    }

    @Override
    public ScriptEntryData getScriptEntryData() {
        return new BukkitScriptEntryData(new PlayerTag(player), null);
    }

    @Override
    public void cancellationChanged() {
        if (cancelled && playerStateChangedEvent != null) {
            playerStateChangedEvent.cancel();
        }
        super.cancellationChanged();
    }

    public void handle(de.maxhenkel.voicechat.api.events.PlayerStateChangedEvent event) {
        this.playerStateChangedEvent = event;
        this.isDisabled = event.isDisabled();
        this.isDisconnected = event.isDisconnected();
        this.player = (Player) event.getConnection().getPlayer().getPlayer();

        if (player == null) return;

        fire();
    }
}