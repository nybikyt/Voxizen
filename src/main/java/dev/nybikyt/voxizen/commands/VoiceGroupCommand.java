package dev.nybikyt.voxizen.commands;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.*;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import dev.nybikyt.voxizen.VoiceAddon;
import dev.nybikyt.voxizen.events.GroupCreatedEvent;
import dev.nybikyt.voxizen.events.GroupRemovedEvent;
import dev.nybikyt.voxizen.objects.VoiceGroupTag;
import dev.nybikyt.voxizen.records.VoiceGroupData;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceGroupCommand extends AbstractCommand {

    public static final Map<String, VoiceGroupData> groups = new ConcurrentHashMap<>();

    public static final Map<UUID, String> uuidToId = new ConcurrentHashMap<>();

    public enum Instruction { CREATE, DELETE }

    public VoiceGroupCommand() {
        setName("voicegroup");
        setSyntax("voicegroup [create/delete] [id:<id>] (name:<n>) (type:isolated/{normal}/open) (password:<password>) (persistent:<boolean>)");
        setRequiredArguments(2, 6);
        isProcedural = false;
        autoCompile();
    }

    // <--[command]
    // @Name voicegroup
    // @Syntax voicegroup [create/delete] [id:<id>] (name:<n>) (type:isolated/{normal}/open) (password:<password>) (persistent:<boolean>)
    // @Required 2
    // @Maximum 6
    // @Short Creates or deletes a Simple Voice Chat group.
    // @Group Voxizen
    //
    // @Description
    // Creates or deletes a managed Simple Voice Chat voice group.
    //
    // type — group type. Defaults to normal.
    // > normal — group members hear nearby players not in any group.
    // > open — group members hear nearby players AND nearby players hear them.
    // > isolated — group members ONLY hear each other.
    //
    // persistent — if true, the group is not auto-removed when all members leave. Defaults to false.
    //
    // password — optional password for the group.
    //
    // @Tags
    // <entry[saveName].voicegroup> returns the created VoiceGroupTag after a CREATE instruction.
    // <entry[saveName].id> returns the string id of the created group after a CREATE instruction.
    //
    // @Usage
    // Create a persistent isolated group and save the result.
    // - voicegroup create id:staff name:Staff type:isolated persistent:true save:result
    // - narrate "Created group: <entry[result].voicegroup>"
    //
    // @Usage
    // Create a password-protected group.
    // - voicegroup create id:vip name:VIP password:secret123
    //
    // @Usage
    // Delete an existing group.
    // - voicegroup delete id:staff
    //
    // -->

    public static void autoExecute(
            ScriptEntry scriptEntry,
            @ArgName("instruction") Instruction instruction,
            @ArgPrefixed @ArgName("id") ElementTag id,
            @ArgPrefixed @ArgDefaultNull @ArgName("name") ElementTag name,
            @ArgPrefixed @ArgDefaultText("normal") @ArgName("type") ElementTag type,
            @ArgPrefixed @ArgDefaultNull @ArgName("password") ElementTag password,
            @ArgPrefixed @ArgDefaultText("false") @ArgName("persistent") ElementTag persistent
    ) {
        switch (instruction) {
            case CREATE -> handleCreate(scriptEntry, id, name, type, password, persistent);
            case DELETE -> handleDelete(scriptEntry, id);
        }
    }

    private static void handleCreate(
            ScriptEntry scriptEntry,
            ElementTag id,
            ElementTag name,
            ElementTag type,
            ElementTag password,
            ElementTag persistent
    ) {
        String groupId = id.asString();

        if (groupId.isBlank()) {
            Debug.echoError(scriptEntry, "Voice group id cannot be empty!");
            return;
        }

        if (groups.containsKey(groupId)) {
            Debug.echoError(scriptEntry, "Voice group '%s' already exists!".formatted(groupId));
            return;
        }

        if (name == null) {
            Debug.echoError(scriptEntry, "Voice group requires 'name'.");
            return;
        }

        Group.Type groupType = resolveType(type.asString());
        if (groupType == null) {
            Debug.echoError(scriptEntry, "Unknown group type '%s'. Valid values: normal, open, isolated.".formatted(type.asString()));
            return;
        }

        String rawPassword = (password != null && !password.asString().isBlank()) ? password.asString() : null;

        Group group = VoiceAddon.getApi().groupBuilder()
                .setName(name.asString())
                .setType(groupType)
                .setPassword(rawPassword)
                .setPersistent(persistent.asBoolean())
                .build();

        if (group == null) {
            Debug.echoError(scriptEntry, "Failed to create voice group '%s'!".formatted(groupId));
            return;
        }

        GroupCreatedEvent.instance.markCommandInitiated(group.getId());

        VoiceGroupData data = new VoiceGroupData(group, rawPassword);
        groups.put(groupId, data);
        uuidToId.put(group.getId(), groupId);

        VoiceGroupTag tag = new VoiceGroupTag(groupId, data);
        GroupCreatedEvent.instance.handleFromCommand(tag);

        if (!groups.containsKey(groupId)) {
            uuidToId.remove(group.getId());
            return;
        }

        scriptEntry.saveObject("id", id);
        scriptEntry.saveObject("voicegroup", tag);
    }

    private static void handleDelete(ScriptEntry scriptEntry, ElementTag id) {
        String groupId = id.asString();
        VoiceGroupData data = groups.remove(groupId);

        if (data == null) {
            Debug.echoError(scriptEntry, "Voice group '%s' not found!".formatted(groupId));
            return;
        }

        UUID groupUuid = data.group().getId();
        uuidToId.remove(groupUuid);

        VoiceGroupTag tag = new VoiceGroupTag(groupId, data);
        boolean removed = GroupRemovedEvent.instance.handleFromCommand(tag);

        if (!removed) {
            // Cancelled — restore
            groups.put(groupId, data);
            uuidToId.put(groupUuid, groupId);
            return;
        }

        GroupRemovedEvent.instance.markCommandInitiated(groupUuid);

        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(player.getUniqueId());
            if (connection == null) continue;
            if (connection.getGroup() != null && groupUuid.equals(connection.getGroup().getId())) {
                connection.setGroup(null);
            }
        }

        // Fix #2 — persistent groups must be explicitly removed, SVC won't auto-remove them
        if (data.group().isPersistent()) {
            VoiceAddon.getApi().removeGroup(groupUuid);
        }
    }


    public static VoiceGroupTag rebuildGroup(
            String groupId,
            String name,
            Group.Type type,
            String password,
            boolean persistent
    ) {
        VoiceGroupData old = groups.get(groupId);
        if (old == null) return null;

        UUID oldUuid = old.group().getId();

        Group newGroup = VoiceAddon.getApi().groupBuilder()
                .setName(name)
                .setType(type)
                .setPassword(password)
                .setPersistent(persistent)
                .build();

        if (newGroup == null) return null;

        // Fix #2/#3 — suppress SVC echo events for both old removal and new creation
        GroupRemovedEvent.instance.markCommandInitiated(oldUuid);
        GroupCreatedEvent.instance.markCommandInitiated(newGroup.getId());

        VoiceGroupData newData = new VoiceGroupData(newGroup, password);
        groups.put(groupId, newData);
        uuidToId.remove(oldUuid);
        uuidToId.put(newGroup.getId(), groupId);

        // Migrate members to new group
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(player.getUniqueId());
            if (connection == null) continue;
            if (connection.getGroup() != null && oldUuid.equals(connection.getGroup().getId())) {
                connection.setGroup(newGroup);
            }
        }

        // Fix #2 — remove old persistent group explicitly, SVC won't auto-remove it
        if (old.group().isPersistent()) {
            VoiceAddon.getApi().removeGroup(oldUuid);
        }

        return new VoiceGroupTag(groupId, newData);
    }


    public static Group.Type resolveType(String raw) {
        return switch (raw.toLowerCase()) {
            case "normal" -> Group.Type.NORMAL;
            case "open" -> Group.Type.OPEN;
            case "isolated" -> Group.Type.ISOLATED;
            default -> null;
        };
    }

    public static String resolveTypeName(Group.Type type) {
        if (type == Group.Type.NORMAL) return "normal";
        if (type == Group.Type.OPEN) return "open";
        if (type == Group.Type.ISOLATED) return "isolated";
        return "unknown";
    }

    public static String findGroupId(UUID groupUuid) {
        return uuidToId.get(groupUuid);
    }
}