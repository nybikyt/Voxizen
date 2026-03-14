package dev.nybikyt.voxizen.objects;

import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.Fetchable;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.tags.Attribute;
import com.denizenscript.denizencore.tags.ObjectTagProcessor;
import com.denizenscript.denizencore.tags.TagContext;
import com.denizenscript.denizencore.tags.TagManager;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import dev.nybikyt.voxizen.VoiceAddon;
import dev.nybikyt.voxizen.commands.VoiceSourceCommand;
import dev.nybikyt.voxizen.records.VoiceSourceData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

// <--[ObjectType]
// @name VoiceSourceTag
// @prefix voicesource
// @base ElementTag
// @format
// The identity format for a voice source is voicesource@<id>
// For example: voicesource@myradio
//
// @description
// Represents a persistent Simple Voice Chat audio channel created
// by the <@link command voicesource> command.
// -->

public class VoiceSourceTag implements ObjectTag {

    public static ObjectTagProcessor<VoiceSourceTag> tagProcessor = new ObjectTagProcessor<>();

    public static boolean matches(String input) {
        if (input == null) {
            return false;
        }
        String id = input.startsWith("voicesource@") ? input.substring("voicesource@".length()) : input;
        return VoiceSourceCommand.sources.containsKey(id);
    }

    /** Dummy singleton used for static tags like <voicesource.list> that don't need a real instance. */
    public static final VoiceSourceTag STATIC = new VoiceSourceTag(null, null);

    @Fetchable("voicesource")
    public static VoiceSourceTag valueOf(String input, TagContext context) {
        if (input == null) {
            return null;
        }

        String id = input.startsWith("voicesource@") ? input.substring("voicesource@".length()) : input;

        if (id.isEmpty()) {
            return STATIC;
        }

        VoiceSourceData data = VoiceSourceCommand.sources.get(id);

        if (data == null) {
            if (context == null || context.showErrors()) {
                Debug.echoError("VoiceSourceTag.valueOf: voice source '" + id + "' does not exist.");
            }
            return null;
        }

        return new VoiceSourceTag(id, data);
    }


    private final String id;
    private final VoiceSourceData data;
    private String prefix = "voicesource";

    public VoiceSourceTag(String id, VoiceSourceData data) {
        this.id = id;
        this.data = data;
    }

    public String getId()            { return id; }
    public VoiceSourceData getData() { return data; }


    @Override
    public String getPrefix() { return prefix; }

    @Override
    public ObjectTag setPrefix(String newPrefix) { prefix = newPrefix; return this; }

    @Override
    public boolean isUnique() { return true; }

    @Override
    public String identify() { return id != null ? "voicesource@" + id : "voicesource"; }

    @Override
    public String identifySimple() { return identify(); }

    @Override
    public String toString() { return identify(); }

    @Override
    public ObjectTag getObjectAttribute(Attribute attribute) {
        return tagProcessor.getObjectAttribute(this, attribute);
    }

    public static void register() {

        // <--[tag]
        // @attribute <VoiceSourceTag.id>
        // @returns ElementTag
        // @description
        // Returns the string id used to reference this voice source.
        // @example
        // - narrate "Playing on source: <context.source.id>"
        // -->
        tagProcessor.registerTag(ElementTag.class, "id",
                (attribute, voiceSource) -> new ElementTag(voiceSource.id)
        );

        // <--[tag]
        // @attribute <VoiceSourceTag.list>
        // @returns ListTag(ElementTag)
        // @description
        // Returns a ListTag of ids of all currently registered voice sources.
        // @example
        // - foreach <voicesource.list> as:sourceId:
        //     - narrate "Active source: <[sourceId]>"
        // -->

        TagManager.registerTagHandler(ObjectTag.class, "voicesource", attribute -> {
            if (!attribute.hasParam()) {
                if (attribute.startsWith("list", 2)) {
                    attribute.fulfill(2);

                    ListTag result = new ListTag();
                    for (String sourceId : VoiceSourceCommand.sources.keySet()) {
                        result.addObject(new ElementTag(sourceId));
                    }
                    return result;
                }
                return null;
            }

            VoiceSourceTag voiceSource = VoiceSourceTag.valueOf(attribute.getParam(), attribute.context);
            if (voiceSource == null) {
                return null;
            }

            attribute.fulfill(1);
            return voiceSource.getObjectAttribute(attribute);
        });

        // <--[tag]
        // @attribute <VoiceSourceTag.source>
        // @returns LocationTag / EntityTag
        // @description
        // Returns the underlying source object of this channel:
        //   LocationTag for locational channels, EntityTag for entity channels.
        // Returns null for static channels — use || as a fallback or check .type first.
        // @example
        // - if <context.source.type> == locational:
        //     - narrate "Broadcasting from <context.source.source>"
        // -->
        tagProcessor.registerTag(ObjectTag.class, "source",
                (attribute, voiceSource) -> {
                    if (voiceSource.data.source() == null) {
                        if (!attribute.hasAlternative()) {
                            attribute.echoError("Voice source '" + voiceSource.id + "' is a static channel and has no location or entity source.");
                        }
                        return null;
                    }
                    return voiceSource.data.source();
                }
        );

        // <--[tag]
        // @attribute <VoiceSourceTag.type>
        // @returns ElementTag
        // @description
        // Returns the channel type: locational, entity, or static.
        // @example
        // - if <context.source.type> == static:
        //     - narrate "This is a broadcast channel."
        // -->
        tagProcessor.registerStaticTag(ElementTag.class, "type",
                (attribute, voiceSource) -> new ElementTag(resolveChannelType(voiceSource))
        );

        // <--[tag]
        // @attribute <VoiceSourceTag.distance>
        // @returns ElementTag(Decimal)
        // @mechanism VoiceSourceTag.distance
        // @description
        // Returns the hearing distance for locational and entity channels.
        // Returns null for static channels — use || as a fallback.
        // @example
        // - narrate "Audible within <context.source.distance||0> blocks"
        // -->
        tagProcessor.registerTag(ElementTag.class, "distance",
                (attribute, voiceSource) -> {
                    if (voiceSource.data.channel() instanceof LocationalAudioChannel locationalChannel) {
                        return new ElementTag(locationalChannel.getDistance());
                    }
                    if (voiceSource.data.channel() instanceof EntityAudioChannel entityChannel) {
                        return new ElementTag(entityChannel.getDistance());
                    }
                    if (!attribute.hasAlternative()) {
                        attribute.echoError("Voice source '" + voiceSource.id + "' is a static channel and has no distance.");
                    }
                    return null;
                }
        );

        // <--[tag]
        // @attribute <VoiceSourceTag.targets>
        // @returns ListTag(PlayerTag)
        // @mechanism VoiceSourceTag.targets
        // @description
        // Returns a ListTag of currently targeted online players.
        // Players who went offline are silently omitted from the result.
        // @example
        // - foreach <context.source.targets> as:listener:
        //     - narrate target:<[listener]> "You are listening to the broadcast."
        // -->
        tagProcessor.registerTag(ListTag.class, "targets",
                (attribute, voiceSource) -> {
                    ListTag result = new ListTag();
                    for (UUID uuid : voiceSource.data.targetUuids()) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            result.addObject(new PlayerTag(player));
                        }
                    }
                    return result;
                }
        );

        // <--[tag]
        // @attribute <VoiceSourceTag.category>
        // @returns ElementTag
        // @mechanism VoiceSourceTag.category
        // @description
        // Returns the audio category of this voice source, or null if none is set.
        // @example
        // - narrate "Category: <context.source.category||none>"
        // -->
        tagProcessor.registerTag(ElementTag.class, "category",
                (attribute, voiceSource) -> {
                    String category = voiceSource.data.channel().getCategory();
                    if (category == null) {
                        if (!attribute.hasAlternative()) {
                            attribute.echoError("Voice source '" + voiceSource.id + "' has no category set.");
                        }
                        return null;
                    }
                    return new ElementTag(category);
                }
        );

        // <--[mechanism]
        // @object VoiceSourceTag
        // @name targets
        // @input ListTag(PlayerTag)
        // @description
        // Replaces the full target list of this voice source.
        // For locational and entity channels the filter predicate is rebuilt.
        // For static channels old targets are removed and new ones are added.
        // @tags
        // <VoiceSourceTag.targets>
        // @example
        // - adjust <context.source> targets:<server.online_players>
        // -->
        tagProcessor.registerMechanism("targets", false, ListTag.class, (voiceSource, mechanism, input) -> {
            List<VoicechatConnection> newConnections = input.stream()
                    .map(name -> PlayerTag.valueOf(name, mechanism.context))
                    .filter(playerTag -> playerTag != null && playerTag.isOnline())
                    .map(playerTag -> VoiceAddon.getApi().getConnectionOf(playerTag.getPlayerEntity().getUniqueId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            Set<UUID> newUuids = VoiceSourceCommand.collectUuids(newConnections);

            if (voiceSource.data.channel() instanceof StaticAudioChannel staticChannel) {
                for (UUID existingUuid : new HashSet<>(voiceSource.data.targetUuids())) {
                    if (newUuids.contains(existingUuid)) continue;
                    VoicechatConnection connection = VoiceAddon.getApi().getConnectionOf(existingUuid);
                    if (connection != null) staticChannel.removeTarget(connection);
                }
                for (VoicechatConnection connection : newConnections) {
                    if (!voiceSource.data.targetUuids().contains(connection.getPlayer().getUuid())) {
                        staticChannel.addTarget(connection);
                    }
                }
            } else if (voiceSource.data.channel() instanceof LocationalAudioChannel locationalChannel) {
                locationalChannel.setFilter(VoiceSourceCommand.buildFilter(newUuids));
            } else if (voiceSource.data.channel() instanceof EntityAudioChannel entityChannel) {
                entityChannel.setFilter(VoiceSourceCommand.buildFilter(newUuids));
            }

            voiceSource.data.targetUuids().clear();
            voiceSource.data.targetUuids().addAll(newUuids);
        });

        // <--[mechanism]
        // @object VoiceSourceTag
        // @name distance
        // @input ElementTag(Decimal)
        // @description
        // Sets the hearing distance for locational or entity channels.
        // Has no effect on static channels — an error is logged instead.
        // @tags
        // <VoiceSourceTag.distance>
        // @example
        // - adjust <context.source> distance:32
        // -->
        tagProcessor.registerMechanism("distance", false, ElementTag.class, (voiceSource, mechanism, input) -> {
            float distance = input.asFloat();
            if (voiceSource.data.channel() instanceof LocationalAudioChannel locationalChannel) {
                locationalChannel.setDistance(distance);
            } else if (voiceSource.data.channel() instanceof EntityAudioChannel entityChannel) {
                entityChannel.setDistance(distance);
            } else {
                mechanism.echoError("Mechanism 'distance' is not applicable to static channel '" + voiceSource.id + "'.");
            }
        });

        // <--[mechanism]
        // @object VoiceSourceTag
        // @name category
        // @input ElementTag
        // @description
        // Sets the audio category of this voice source.
        // @tags
        // <VoiceSourceTag.category>
        // @example
        // - adjust <context.source> category:music
        // -->
        tagProcessor.registerMechanism("category", false, ElementTag.class, (voiceSource, mechanism, input) ->
                voiceSource.data.channel().setCategory(input.asString())
        );
    }

    private static String resolveChannelType(VoiceSourceTag voiceSource) {
        if (voiceSource.data.channel() instanceof LocationalAudioChannel) return "locational";
        if (voiceSource.data.channel() instanceof EntityAudioChannel)     return "entity";
        if (voiceSource.data.channel() instanceof StaticAudioChannel)     return "static";
        return "unknown";
    }
}