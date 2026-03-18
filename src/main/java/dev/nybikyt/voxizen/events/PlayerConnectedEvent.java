package dev.nybikyt.voxizen.events;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import org.bukkit.entity.Player;

public class PlayerConnectedEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // player connects to voicechat
    //
    // @Group Voxizen
    //
    // @Cancellable true
    //
    // @Triggers when a player connects to Simple Voice Chat.
    //
    // @Player Always.
    //
    // -->

    public static PlayerConnectedEvent instance;
    private de.maxhenkel.voicechat.api.events.PlayerConnectedEvent playerConnectedEvent;
    private Player player;

    public PlayerConnectedEvent() {
        instance = this;
        registerCouldMatcher("player connects to voicechat");
    }

    @Override
    public void cancellationChanged() {
        if (cancelled && playerConnectedEvent != null) {
            playerConnectedEvent.cancel();
        }
        super.cancellationChanged();
    }

    @Override
    public ScriptEntryData getScriptEntryData() {
        return new BukkitScriptEntryData(new PlayerTag(player), null);
    }

    public void handle(de.maxhenkel.voicechat.api.events.PlayerConnectedEvent event) {
        this.playerConnectedEvent = event;
        this.player = (Player) event.getConnection().getPlayer().getPlayer();
        fire();
    }
}