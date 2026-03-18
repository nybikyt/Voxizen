package dev.nybikyt.voxizen.properties;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import dev.nybikyt.voxizen.VoiceAddon;
import dev.nybikyt.voxizen.objects.VoiceGroupTag;

public class PlayerTagExtensions {

    public static void register() {


        // <--[tag]
        // @attribute <PlayerTag.voice_group>
        //
        // @returns VoiceGroupTag
        //
        // @mechanism PlayerTag.voice_group
        //
        // @description
        // Returns the voice group the player is currently in, or null if not in any group.
        // Use || to provide a fallback: <player.voice_group||none>
        //
        // @example
        // - narrate targets:<server.online_players> "Your voice group is: <player.voice_group||none>" per_player
        //
        // -->
        PlayerTag.tagProcessor.registerTag(VoiceGroupTag.class, "voice_group", (attribute, object) -> {
            VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(object.getUUID());

            if (connection == null) {
                if (!attribute.hasAlternative()) {
                    attribute.echoError("Player '" + object.getName() + "' has no voice chat connection.");
                }
                return null;
            }

            Group group = connection.getGroup();

            if (group == null) {
                if (!attribute.hasAlternative()) {
                    attribute.echoError("Player '" + object.getName() + "' is not in any voice group.");
                }
                return null;
            }

            return VoiceGroupTag.fromApiGroup(group);
        });


        // <--[tag]
        // @attribute <PlayerTag.in_voice_group>
        //
        // @returns ElementTag(Boolean)
        //
        // @description
        // Returns whether the player is currently in a voice group.
        //
        // @example
        // - narrate "You are in voice group: <player.in_voice_group>"
        //
        // -->
        PlayerTag.tagProcessor.registerTag(ElementTag.class, "in_voice_group", (attribute, object) -> {
            VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(object.getUUID());
            if (connection == null) return new ElementTag(false);
            return new ElementTag(connection.isInGroup());
        });


        // <--[tag]
        // @attribute <PlayerTag.voice_connected>
        //
        // @returns ElementTag(Boolean)
        //
        // @mechanism PlayerTag.voice_connected
        //
        // @description
        // Returns whether the player is actively connected to voice chat.
        // Note: may return false if the connection was disabled via setConnected(false).
        //
        // @example
        // - narrate "You are connected to voice chat: <player.voice_connected>"
        //
        // -->
        PlayerTag.tagProcessor.registerTag(ElementTag.class, "voice_connected", (attribute, object) -> {
            VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(object.getUUID());
            if (connection == null) return new ElementTag(false);
            return new ElementTag(connection.isConnected());
        });


        // <--[tag]
        // @attribute <PlayerTag.voice_installed>
        //
        // @returns ElementTag(Boolean)
        //
        // @description
        // Returns whether the player has the voice chat mod installed.
        //
        // @example
        // - narrate "You have voice chat: <player.voice_installed>"
        //
        // -->
        PlayerTag.tagProcessor.registerTag(ElementTag.class, "voice_installed", (attribute, object) -> {
            VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(object.getUUID());
            if (connection == null) return new ElementTag(false);
            return new ElementTag(connection.isInstalled());
        });


        // <--[tag]
        // @attribute <PlayerTag.voice_disabled>
        //
        // @returns ElementTag(Boolean)
        //
        // @mechanism PlayerTag.voice_disabled
        //
        // @description
        // Returns whether the player has voice chat disabled on their end.
        //
        // @example
        // - narrate "Your voice chat is disabled: <player.voice_disabled>"
        //
        // -->
        PlayerTag.tagProcessor.registerTag(ElementTag.class, "voice_disabled", (attribute, object) -> {
            VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(object.getUUID());
            if (connection == null) return new ElementTag(true);
            return new ElementTag(connection.isDisabled());
        });


        // <--[mechanism]
        //
        // @object PlayerTag
        //
        // @name voice_group
        //
        // @input ElementTag
        //
        // @description
        // Moves the player into the specified voice group.
        // Accepts a VoiceGroupTag identity string (e.g. voicegroup@staff) or a plain group id.
        // Specify null or empty to remove the player from their current group.
        //
        // @example
        // - adjust <player> voice_group:<voicegroup[vip]>
        //
        // @tags
        // <PlayerTag.voice_group>
        // <PlayerTag.in_voice_group>
        //
        // -->
        PlayerTag.tagProcessor.registerMechanism("voice_group", false, ElementTag.class, (object, mechanism, input) -> {
            VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(object.getUUID());
            if (connection == null) {
                mechanism.echoError("Player '" + object.getName() + "' has no voice chat connection.");
                return;
            }

            String raw = input.asString();
            if (raw.equalsIgnoreCase("null") || raw.isBlank()) {
                connection.setGroup(null);
                return;
            }

            VoiceGroupTag groupTag = VoiceGroupTag.valueOf(raw, mechanism.context);
            if (groupTag == null) {
                mechanism.echoError("Voice group '" + raw + "' not found.");
                return;
            }

            connection.setGroup(groupTag.getData().group());
        });


        // <--[mechanism]
        //
        // @object PlayerTag
        //
        // @name voice_connected
        //
        // @input ElementTag(Boolean)
        //
        // @description
        // Sets whether the player is connected to voice chat.
        // Note: resets automatically on reconnect or if SVC changes the disconnected state.
        //
        // @example
        // - adjust <player> voice_connected:true
        //
        // @tags
        // <PlayerTag.voice_connected>
        //
        // -->
        PlayerTag.tagProcessor.registerMechanism("voice_connected", false, ElementTag.class, (object, mechanism, input) -> {
            VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(object.getUUID());
            if (connection == null) {
                mechanism.echoError("Player '" + object.getName() + "' has no voice chat connection.");
                return;
            }
            connection.setConnected(input.asBoolean());
        });

        // <--[mechanism]
        //
        // @object PlayerTag
        //
        // @name voice_disabled
        //
        // @input ElementTag(Boolean)
        //
        // @description
        // Sets the player's voice chat disabled state.
        //
        // @example
        // - adjust <player> voice_disabled:false
        //
        // @tags
        // <PlayerTag.voice_disabled>
        //
        // -->
        PlayerTag.tagProcessor.registerMechanism("voice_disabled", false, ElementTag.class, (object, mechanism, input) -> {
            VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(object.getUUID());
            if (connection == null) {
                mechanism.echoError("Player '" + object.getName() + "' has no voice chat connection.");
                return;
            }
            connection.setDisabled(input.asBoolean());
        });
    }
}