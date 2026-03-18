package dev.nybikyt.voxizen.events;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import dev.nybikyt.voxizen.VoiceAddon;
import dev.nybikyt.voxizen.commands.VoiceGroupCommand;
import dev.nybikyt.voxizen.objects.VoiceGroupTag;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GroupCreatedEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // voice group created
    //
    // @Group Voxizen
    //
    // @Cancellable true
    //
    // @Switch id:<id> to only fire for a specific managed group id.
    //
    // @Triggers when any Simple Voice Chat group is created (also via the voicegroup command)
    //
    // @Context
    // <context.group> returns the VoiceGroupTag of the created group. For managed groups the id is the string id (e.g. "staff"). For unmanaged groups the id is the group's UUID string.
    //
    // <context.creator> returns the PlayerTag of the player who created the group, or null if the group was created via the voicegroup command.
    //
    // -->

    public static GroupCreatedEvent instance;
    private VoiceGroupTag group;
    private PlayerTag creator;

    private final Set<UUID> commandInitiated = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public GroupCreatedEvent() {
        instance = this;
        registerCouldMatcher("voice group created");
        registerSwitches("id");
    }

    @Override
    public boolean matches(ScriptPath path) {
        if (!runGenericSwitchCheck(path, "id", group.getId())) return false;
        return super.matches(path);
    }

    public void markCommandInitiated(UUID uuid) {
        commandInitiated.add(uuid);
    }

    @Override
    public ObjectTag getContext(String name) {
        return switch (name) {
            case "group" -> group;
            case "creator" -> creator;
            default -> super.getContext(name);
        };
    }

    public void handleFromCommand(VoiceGroupTag tag) {
        this.group = tag;
        this.creator = null;
        fire();

        if (cancelled) {
            GroupRemovedEvent.instance.markCommandInitiated(tag.getData().group().getId());
            VoiceGroupCommand.groups.remove(tag.getId());
            VoiceGroupCommand.uuidToId.remove(tag.getData().group().getId());
            VoiceAddon.getApi().removeGroup(tag.getData().group().getId());
        }
    }

    public void handleFromApi(CreateGroupEvent event) {
        if (commandInitiated.remove(event.getGroup().getId())) return;

        this.group = VoiceGroupTag.fromApiGroup(event.getGroup());
        this.creator = event.getConnection() != null
                ? new PlayerTag((Player) event.getConnection().getPlayer().getPlayer())
                : null;

        fire();

        if (cancelled) {
            event.cancel();
        }
    }
}