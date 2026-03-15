package dev.nybikyt.voxizen.events;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import dev.nybikyt.voxizen.objects.VoiceGroupTag;
import org.bukkit.entity.Player;

public class PlayerJoinGroupEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // player joins group
    //
    // @Group Voxizen
    //
    // @Cancellable true
    //
    // @Switch id:<id> to only fire for a specific managed group id.
    //
    // @Triggers when a player joins any Simple Voice Chat group —
    // both managed (created via voicegroup command) and unmanaged (player-created via SVC UI).
    //
    // @Context
    // <context.group> returns a VoiceGroupTag of the group being joined.
    //   For managed groups the id is the string id (e.g. "staff").
    //   For unmanaged groups the id is the group's UUID string.
    //
    // @Determine
    // "cancelled" to prevent the player from joining.
    // VoiceGroupTag to redirect the player into a different group instead.
    //
    // -->

    public static PlayerJoinGroupEvent instance;
    private JoinGroupEvent joinGroupEvent;
    private Player player;
    private VoiceGroupTag group;
    private VoiceGroupTag redirectGroup;

    public PlayerJoinGroupEvent() {
        instance = this;
        registerCouldMatcher("player joins group");

        this.<PlayerJoinGroupEvent, VoiceGroupTag>registerOptionalDetermination(null, VoiceGroupTag.class,
                (event, context, value) -> {
                    event.redirectGroup = value;
                    return true;
                });
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
        if (cancelled && joinGroupEvent != null) {
            joinGroupEvent.cancel();
        }
        super.cancellationChanged();
    }

    public void handle(JoinGroupEvent event) {
        this.joinGroupEvent = event;
        this.player = (Player) event.getConnection().getPlayer().getPlayer();
        this.group = VoiceGroupTag.fromApiGroup(event.getGroup());
        this.redirectGroup = null;

        fire();

        if (cancelled) return;

        if (redirectGroup != null) {
            joinGroupEvent.cancel();
            VoicechatConnection connection = event.getConnection();
            connection.setGroup(redirectGroup.getData().group());
        }
    }
}