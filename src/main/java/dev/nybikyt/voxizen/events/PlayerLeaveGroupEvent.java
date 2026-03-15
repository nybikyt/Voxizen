package dev.nybikyt.voxizen.events;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import de.maxhenkel.voicechat.api.events.LeaveGroupEvent;
import dev.nybikyt.voxizen.objects.VoiceGroupTag;
import org.bukkit.entity.Player;

public class PlayerLeaveGroupEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // player leaves group
    //
    // @Group Voxizen
    //
    // @Cancellable true
    //
    // @Switch id:<id> to only fire for a specific managed group id.
    //
    // @Triggers when a player leaves any Simple Voice Chat group —
    // both managed (created via voicegroup command) and unmanaged (player-created via SVC UI).
    //
    // @Context
    // <context.group> returns a VoiceGroupTag of the group being left.
    //   For managed groups the id is the string id (e.g. "staff").
    //   For unmanaged groups the id is the group's UUID string.
    //
    // @Determine
    // "cancelled" to prevent the player from leaving.
    //
    // -->

    public static PlayerLeaveGroupEvent instance;
    private LeaveGroupEvent leaveGroupEvent;
    private Player player;
    private VoiceGroupTag group;

    public PlayerLeaveGroupEvent() {
        instance = this;
        registerCouldMatcher("player leaves group");
    }

    @Override
    public boolean matches(ScriptPath path) {
        if (!runGenericSwitchCheck(path, "id", group.getId())) return false;
        return super.matches(path);
    }

    @Override
    public ObjectTag getContext(String name) {
        return switch (name) {
            case "group" -> group;
            default -> super.getContext(name);
        };
    }

    @Override
    public ScriptEntryData getScriptEntryData() {
        return new BukkitScriptEntryData(new PlayerTag(player), null);
    }

    @Override
    public void cancellationChanged() {
        if (cancelled && leaveGroupEvent != null) {
            leaveGroupEvent.cancel();
        }
        super.cancellationChanged();
    }

    public void handle(LeaveGroupEvent event) {
        this.leaveGroupEvent = event;
        this.player = (Player) event.getConnection().getPlayer().getPlayer();
        this.group = VoiceGroupTag.fromApiGroup(event.getGroup());

        fire();
    }
}