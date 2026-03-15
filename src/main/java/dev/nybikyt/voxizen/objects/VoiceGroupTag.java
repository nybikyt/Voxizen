package dev.nybikyt.voxizen.objects;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.Fetchable;
import com.denizenscript.denizencore.objects.Mechanism;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.tags.TagManager;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import dev.nybikyt.voxizen.VoiceAddon;
import dev.nybikyt.voxizen.commands.VoiceGroupCommand;
import dev.nybikyt.voxizen.records.VoiceGroupData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// <--[ObjectType]
// @name VoiceGroupTag
// @prefix voicegroup
// @base ElementTag
// @format
// The identity format for a voice group is voicegroup@<id>
// For managed groups: voicegroup@staff
// For unmanaged (player-created) groups: voicegroup@<uuid>
//
// @description
// Represents a Simple Voice Chat group.
// Managed groups are created via the <@link command voicegroup> command and have a string id.
// Unmanaged groups are created by players via the SVC interface and use a UUID as id.
// -->

public class VoiceGroupTag implements ObjectTag {

    public static ObjectTagProcessor<VoiceGroupTag> tagProcessor = new ObjectTagProcessor<>();

    public static boolean matches(String input) {
        if (input == null) return false;
        String id = input.startsWith("voicegroup@") ? input.substring("voicegroup@".length()) : input;
        if (VoiceGroupCommand.groups.containsKey(id)) return true;
        // Fix #5 — guard against null API
        if (VoiceAddon.getApi() == null) return false;
        try {
            return VoiceAddon.getApi().getGroup(UUID.fromString(id)) != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** Wraps any API Group object into a VoiceGroupTag — managed or unmanaged. */
    public static VoiceGroupTag fromApiGroup(Group group) {
        String managedId = VoiceGroupCommand.findGroupId(group.getId());
        if (managedId != null) {
            VoiceGroupData data = VoiceGroupCommand.groups.get(managedId);
            if (data != null) return new VoiceGroupTag(managedId, data);
        }
        return new VoiceGroupTag(group.getId().toString(), new VoiceGroupData(group, null));
    }

    @Fetchable("voicegroup")
    public static VoiceGroupTag valueOf(String input, TagContext context) {
        if (input == null) return null;
        String id = input.startsWith("voicegroup@") ? input.substring("voicegroup@".length()) : input;
        if (id.isEmpty()) return null;

        // Managed group
        VoiceGroupData data = VoiceGroupCommand.groups.get(id);
        if (data != null) return new VoiceGroupTag(id, data);

        // Fix #5 — guard against null API before unmanaged lookup
        if (VoiceAddon.getApi() == null) {
            if (context == null || context.showErrors()) {
                Debug.echoError("VoiceGroupTag.valueOf: voice group '" + id + "' does not exist (API not ready).");
            }
            return null;
        }

        // Unmanaged group — look up by UUID via API
        try {
            UUID uuid = UUID.fromString(id);
            Group group = VoiceAddon.getApi().getGroup(uuid);
            if (group != null) return new VoiceGroupTag(id, new VoiceGroupData(group, null));
        } catch (IllegalArgumentException ignored) {}

        if (context == null || context.showErrors()) {
            Debug.echoError("VoiceGroupTag.valueOf: voice group '" + id + "' does not exist.");
        }
        return null;
    }


    private final String id;
    private final VoiceGroupData data;
    private String prefix = "voicegroup";

    public VoiceGroupTag(String id, VoiceGroupData data) {
        this.id = id;
        this.data = data;
    }

    public String getId() { return id; }
    public VoiceGroupData getData() { return data; }

    /** True if this group was created via the voicegroup command. */
    public boolean isManaged() {
        return VoiceGroupCommand.groups.containsKey(id);
    }


    @Override public String getPrefix() { return prefix; }
    @Override public ObjectTag setPrefix(String p) { prefix = p; return this; }
    @Override public boolean isUnique() { return true; }
    @Override public String identify() { return "voicegroup@" + id; }
    @Override public String identifySimple() { return identify(); }
    @Override public String toString() { return identify(); }

    @Override
    public ObjectTag getObjectAttribute(Attribute attribute) {
        return tagProcessor.getObjectAttribute(this, attribute);
    }


    public static void register() {

        // <--[tag]
        // @attribute <VoiceGroupTag.id>
        // @returns ElementTag
        // @description
        // Returns the string id of this group (managed) or UUID string (unmanaged).
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "id",
                (attribute, group) -> new ElementTag(group.id)
        );

        // <--[tag]
        // @attribute <VoiceGroupTag.name>
        // @returns ElementTag
        // @mechanism VoiceGroupTag.name
        // @description
        // Returns the display name of this voice group.
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "name",
                (attribute, group) -> new ElementTag(group.data.group().getName())
        );

        // <--[tag]
        // @attribute <VoiceGroupTag.password>
        // @returns ElementTag
        // @mechanism VoiceGroupTag.password
        // @description
        // Returns the password of this voice group, or null if none is set.
        // Only available for managed groups — use .has_password for unmanaged ones.
        // Use || to provide a fallback: <context.group.password||none>
        // -->
        tagProcessor.registerTag(ElementTag.class, "password",
                (attribute, group) -> {
                    String pw = group.data.password();
                    if (pw == null) {
                        if (!attribute.hasAlternative()) {
                            attribute.echoError("Voice group '" + group.id + "' has no password set.");
                        }
                        return null;
                    }
                    return new ElementTag(pw);
                }
        );

        // <--[tag]
        // @attribute <VoiceGroupTag.has_password>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this group has a password. Works for both managed and unmanaged groups.
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "has_password",
                (attribute, group) -> new ElementTag(group.data.group().hasPassword())
        );

        // <--[tag]
        // @attribute <VoiceGroupTag.type>
        // @returns ElementTag
        // @mechanism VoiceGroupTag.type
        // @description
        // Returns the type of this voice group: normal, open, or isolated.
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "type",
                (attribute, group) -> new ElementTag(VoiceGroupCommand.resolveTypeName(group.data.group().getType()))
        );

        // <--[tag]
        // @attribute <VoiceGroupTag.persistent>
        // @returns ElementTag(Boolean)
        // @mechanism VoiceGroupTag.persistent
        // @description
        // Returns whether this group is persistent (survives with no members).
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "persistent",
                (attribute, group) -> new ElementTag(group.data.group().isPersistent())
        );

        // <--[tag]
        // @attribute <VoiceGroupTag.managed>
        // @returns ElementTag(Boolean)
        // @description
        // Returns whether this group was created via the voicegroup command.
        // Unmanaged groups are created by players through the SVC interface.
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "managed",
                (attribute, group) -> new ElementTag(group.isManaged())
        );

        // <--[tag]
        // @attribute <VoiceGroupTag.members>
        // @returns ListTag(PlayerTag)
        // @mechanism VoiceGroupTag.members
        // @description
        // Returns a ListTag of all online players currently in this group.
        // Works for both managed and unmanaged groups.
        // -->
        tagProcessor.registerTag(ListTag.class, "members",
                (attribute, group) -> {
                    ListTag result = new ListTag();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(player.getUniqueId());
                        if (connection == null) continue;
                        if (connection.getGroup() != null
                                && group.data.group().getId().equals(connection.getGroup().getId())) {
                            result.addObject(new PlayerTag(player));
                        }
                    }
                    return result;
                }
        );

        TagManager.registerTagHandler(ObjectTag.class, "voicegroup", attribute -> {
            if (!attribute.hasParam()) return null;

            VoiceGroupTag group = VoiceGroupTag.valueOf(attribute.getParam(), attribute.context);
            if (group == null) return null;

            attribute.fulfill(1);
            return group.getObjectAttribute(attribute);
        });


        // <--[mechanism]
        // @object VoiceGroupTag
        // @name name
        // @input ElementTag
        // @description
        // Sets the display name of this voice group. Only works for managed groups.
        // @tags
        // <VoiceGroupTag.name>
        // -->
        tagProcessor.registerMechanism("name", false, ElementTag.class, (group, mechanism, input) -> {
            if (!requireManaged(group, mechanism)) return;
            VoiceGroupData current = VoiceGroupCommand.groups.get(group.id);
            if (current == null) { mechanism.echoError("Voice group '" + group.id + "' no longer exists."); return; }
            VoiceGroupCommand.rebuildGroup(group.id, input.asString(),
                    current.group().getType(), current.password(), current.group().isPersistent());
        });

        // <--[mechanism]
        // @object VoiceGroupTag
        // @name password
        // @input ElementTag
        // @description
        // Sets or clears the password. Provide <empty> to remove. Only works for managed groups.
        // @tags
        // <VoiceGroupTag.password>
        // -->
        tagProcessor.registerMechanism("password", false, ElementTag.class, (group, mechanism, input) -> {
            if (!requireManaged(group, mechanism)) return;
            VoiceGroupData current = VoiceGroupCommand.groups.get(group.id);
            if (current == null) { mechanism.echoError("Voice group '" + group.id + "' no longer exists."); return; }
            String newPassword = input.asString().isBlank() ? null : input.asString();
            VoiceGroupCommand.rebuildGroup(group.id, current.group().getName(),
                    current.group().getType(), newPassword, current.group().isPersistent());
        });

        // <--[mechanism]
        // @object VoiceGroupTag
        // @name type
        // @input ElementTag
        // @description
        // Sets the group type (normal, open, isolated). Only works for managed groups.
        // @tags
        // <VoiceGroupTag.type>
        // -->
        tagProcessor.registerMechanism("type", false, ElementTag.class, (group, mechanism, input) -> {
            if (!requireManaged(group, mechanism)) return;
            VoiceGroupData current = VoiceGroupCommand.groups.get(group.id);
            if (current == null) { mechanism.echoError("Voice group '" + group.id + "' no longer exists."); return; }
            Group.Type newType = VoiceGroupCommand.resolveType(input.asString());
            if (newType == null) { mechanism.echoError("Unknown group type '" + input.asString() + "'. Valid: normal, open, isolated."); return; }
            VoiceGroupCommand.rebuildGroup(group.id, current.group().getName(),
                    newType, current.password(), current.group().isPersistent());
        });

        // <--[mechanism]
        // @object VoiceGroupTag
        // @name persistent
        // @input ElementTag(Boolean)
        // @description
        // Sets whether this group is persistent. Only works for managed groups.
        // @tags
        // <VoiceGroupTag.persistent>
        // -->
        tagProcessor.registerMechanism("persistent", false, ElementTag.class, (group, mechanism, input) -> {
            if (!requireManaged(group, mechanism)) return;
            VoiceGroupData current = VoiceGroupCommand.groups.get(group.id);
            if (current == null) { mechanism.echoError("Voice group '" + group.id + "' no longer exists."); return; }
            VoiceGroupCommand.rebuildGroup(group.id, current.group().getName(),
                    current.group().getType(), current.password(), input.asBoolean());
        });

        // <--[mechanism]
        // @object VoiceGroupTag
        // @name members
        // @input ListTag(PlayerTag)
        // @description
        // Replaces the member list. Works for both managed and unmanaged groups.
        // Players not in the list are removed, players in the list are added.
        // Offline players are silently skipped.
        // @tags
        // <VoiceGroupTag.members>
        // -->
        tagProcessor.registerMechanism("members", false, ListTag.class, (group, mechanism, input) -> {
            Set<UUID> newUuids = new HashSet<>();
            for (String name : input) {
                PlayerTag playerTag = PlayerTag.valueOf(name, mechanism.context);
                if (playerTag != null && playerTag.isOnline()) {
                    newUuids.add(playerTag.getPlayerEntity().getUniqueId());
                }
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(player.getUniqueId());
                if (connection == null) continue;

                boolean isCurrentMember = connection.getGroup() != null
                        && group.data.group().getId().equals(connection.getGroup().getId());
                boolean shouldBeMember = newUuids.contains(player.getUniqueId());

                if (isCurrentMember && !shouldBeMember) connection.setGroup(null);
                else if (!isCurrentMember && shouldBeMember) connection.setGroup(group.data.group());
            }
        });
    }

    private static boolean requireManaged(VoiceGroupTag group, Mechanism mechanism) {
        if (!group.isManaged()) {
            mechanism.echoError("Mechanism '" + mechanism.getName()
                    + "' is not applicable to unmanaged group '" + group.id + "'.");
            return false;
        }
        return true;
    }
}