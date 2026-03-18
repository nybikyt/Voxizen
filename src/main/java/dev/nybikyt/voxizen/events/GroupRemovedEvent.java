package dev.nybikyt.voxizen.events;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import de.maxhenkel.voicechat.api.events.RemoveGroupEvent;
import dev.nybikyt.voxizen.commands.VoiceGroupCommand;
import dev.nybikyt.voxizen.objects.VoiceGroupTag;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GroupRemovedEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // voice group removed
    //
    // @Group Voxizen
    //
    // @Cancellable true
    //
    // @Switch id:<id> to only fire for a specific managed group id.
    //
    // @Triggers when any Simple Voice Chat group is removed (also via the voicegroup command)
    //
    // @Context
    // <context.group> returns the VoiceGroupTag of the removed group. For managed groups the id is the string id (e.g. "staff"). For unmanaged groups the id is the group's UUID string.
    //
    // -->

    public static GroupRemovedEvent instance;
    private VoiceGroupTag group;

    private final Set<UUID> commandInitiated = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public GroupRemovedEvent() {
        instance = this;
        registerCouldMatcher("voice group removed");
        registerSwitches("id");
    }

    public void markCommandInitiated(UUID uuid) {
        commandInitiated.add(uuid);
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

    /**
     * Called from VoiceGroupCommand after a managed group is deleted.
     * Returns true if the removal was NOT cancelled.
     */
    public boolean handleFromCommand(VoiceGroupTag tag) {
        this.group = tag;
        fire();

        if (cancelled) {
            VoiceGroupCommand.groups.put(tag.getId(), tag.getData());
            VoiceGroupCommand.uuidToId.put(tag.getData().group().getId(), tag.getId());
            return false;
        }
        return true;
    }

    /** Called from VoiceAddon when SVC fires a RemoveGroupEvent (any group, including player-created). */
    public void handleFromApi(RemoveGroupEvent event) {
        UUID groupUuid = event.getGroup().getId();

        // Fix #4 — skip if this removal was initiated by our command
        if (commandInitiated.remove(groupUuid)) return;

        this.group = VoiceGroupTag.fromApiGroup(event.getGroup());
        fire();

        if (cancelled) {
            event.cancel();
        } else {
            // Clean up registry if it was a managed group
            String managedId = VoiceGroupCommand.uuidToId.remove(groupUuid);
            if (managedId != null) {
                VoiceGroupCommand.groups.remove(managedId);
            }
        }
    }
}