package dev.nybikyt.voxizen.events;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlayerDisconnectedEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // player disconnects from voicechat
    //
    // @Group Voxizen
    //
    // @Cancellable true
    //
    // @Triggers when a player disconnects from Simple Voice Chat.
    //
    // @Player Always.
    //
    // -->

    public static PlayerDisconnectedEvent instance;
    private de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent playerDisconnectedEvent;
    private Player player;

    public PlayerDisconnectedEvent() {
        instance = this;
        registerCouldMatcher("player disconnects from voicechat");
    }

    @Override
    public void cancellationChanged() {
        if (cancelled && playerDisconnectedEvent != null) {
            playerDisconnectedEvent.cancel();
        }
        super.cancellationChanged();
    }

    @Override
    public ScriptEntryData getScriptEntryData() {
        return new BukkitScriptEntryData(new PlayerTag(player), null);
    }

    public void handle(de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent event) {
        this.playerDisconnectedEvent = event;
        this.player = Bukkit.getPlayer(event.getPlayerUuid());

        if (player == null) return;

        fire();
    }
}