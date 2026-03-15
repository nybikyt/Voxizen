package dev.nybikyt.voxizen.events;

import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import dev.nybikyt.voxizen.commands.VoiceGroupCommand;
import dev.nybikyt.voxizen.objects.VoiceGroupTag;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GroupCreatedEvent extends ScriptEvent {

    // <--[event]
    // @Events
    // group created
    //
    // @Group Voxizen
    //
    // @Cancellable true
    //
    // @Switch id:<id> to only fire for a specific managed group id.
    //
    // @Triggers when any Simple Voice Chat group is created —
    // both via the voicegroup command and via the SVC player interface.
    //
    // @Context
    // <context.group> returns the VoiceGroupTag of the created group.
    //   For managed groups the id is the string id (e.g. "staff").
    //   For unmanaged groups the id is the group's UUID string.
    //
    // @Determine
    // "cancelled" to cancel the creation.
    //   For managed groups: also removes the group from the registry.
    //   For unmanaged groups: cancels the SVC CreateGroupEvent.
    //
    // -->

    public static GroupCreatedEvent instance;
    private VoiceGroupTag group;

    /** Fix #1 — UUIDs created by our command, suppresses SVC CreateGroupEvent echo. */
    private final Set<UUID> commandInitiated = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public GroupCreatedEvent() {
        instance = this;
        registerCouldMatcher("group created");
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

    /** Called from VoiceGroupCommand after a managed group is created. */
    public void handleFromCommand(VoiceGroupTag tag) {
        this.group = tag;
        fire();

        if (cancelled) {
            VoiceGroupCommand.groups.remove(tag.getId());
        }
    }

    /** Called from VoiceAddon when SVC fires a CreateGroupEvent (any group, including player-created). */
    public void handleFromApi(CreateGroupEvent event) {
        // Fix #1 — skip if this creation was initiated by our command
        if (commandInitiated.remove(event.getGroup().getId())) return;

        this.group = VoiceGroupTag.fromApiGroup(event.getGroup());
        fire();

        if (cancelled) {
            event.cancel();
        }
    }
}