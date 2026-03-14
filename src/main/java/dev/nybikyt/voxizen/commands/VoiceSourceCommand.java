package dev.nybikyt.voxizen.commands;

import com.denizenscript.denizen.objects.EntityTag;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import com.denizenscript.denizencore.scripts.commands.generator.*;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import dev.nybikyt.voxizen.VoiceAddon;
import dev.nybikyt.voxizen.objects.VoiceSourceTag;
import dev.nybikyt.voxizen.records.VoiceSourceData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class VoiceSourceCommand extends AbstractCommand {

    public static final Map<String, VoiceSourceData> sources = new ConcurrentHashMap<>();

    public enum Instruction { CREATE, DELETE }

    public VoiceSourceCommand() {
        setName("voicesource");
        setSyntax("voicesource [create/delete] [id:<id>] (source:<entity>/<location>) (targets:<player>|...) (distance:<#>) (category:<category>)");
        setRequiredArguments(2, 6);
        isProcedural = false;
        autoCompile();
    }

    // <--[command]
    // @Name voicesource
    // @Syntax voicesource [create/delete] [id:<id>] (source:<entity>/<location>) (targets:<player>|...) (distance:<#>) (category:<category>)
    // @Required 2
    // @Maximum 6
    // @Short Creates or deletes a persistent Simple Voice Chat audio source.
    // @Group Voxizen
    //
    // @Description
    // Creates or deletes a persistent Voice Chat audio channel.
    // Use <@link command audio> with the same source to send audio to it.
    //
    // source:LocationTag — locational channel (positional audio in the world).
    // source:EntityTag   — entity channel (follows the entity).
    // no source          — static channel (explicit target list, no position).
    //
    // If targets is not specified, no filter is applied:
    //   - locational/entity channels are heard by ALL players in range, including
    //     players who join after the source was created.
    //   - static channels start with no targets — use <@link mechanism VoiceSourceTag.targets>
    //     to add listeners later.
    //
    // @Save voicesource
    // Returns the created VoiceSourceTag after a CREATE instruction.
    //
    // @Usage
    // # Open broadcast — anyone in range hears it, even after joining.
    // - voicesource create id:myradio source:<player.location> distance:20
    //   save:created
    // - narrate "Created: <entry[created].voicesource>"
    //
    // @Usage
    // # Restricted broadcast — only listed players hear it.
    // - voicesource create id:vip_radio source:<player.location> distance:20 targets:<server.online_players>
    //
    // @Usage
    // - voicesource delete id:myradio
    //
    // -->

    public static void autoExecute(
            ScriptEntry scriptEntry,
            @ArgName("instruction") Instruction instruction,
            @ArgPrefixed @ArgName("id") ElementTag id,
            @ArgPrefixed @ArgDefaultNull @ArgName("source") ObjectTag source,
            @ArgPrefixed @ArgDefaultNull @ArgName("targets") ObjectTag targets,
            @ArgPrefixed @ArgDefaultText("16") @ArgName("distance") ElementTag distance,
            @ArgPrefixed @ArgDefaultNull @ArgName("category") String category
    ) {
        switch (instruction) {
            case CREATE -> handleCreate(scriptEntry, id, source, targets, distance, category);
            case DELETE -> handleDelete(scriptEntry, id);
        }
    }

    private static void handleCreate(
            ScriptEntry scriptEntry,
            ElementTag id,
            ObjectTag source,
            ObjectTag targets,
            ElementTag distance,
            String category
    ) {
        String sourceId = id.asString();

        if (sourceId.isBlank()) {
            Debug.echoError(scriptEntry, "Voice source id cannot be empty!");
            return;
        }

        if (sources.containsKey(sourceId)) {
            Debug.echoError(scriptEntry, "Voice source '%s' already exists!".formatted(sourceId));
            return;
        }

        boolean hasExplicitTargets = targets != null;
        List<VoicechatConnection> connections = hasExplicitTargets
                ? resolveConnections(targets, scriptEntry)
                : List.of();

        AudioChannel channel = createChannel(sourceId, source, connections, hasExplicitTargets, distance.asFloat(), scriptEntry);

        if (channel == null) {
            return;
        }

        if (category != null) {
            channel.setCategory(category);
        }

        Set<UUID> targetUuids = hasExplicitTargets ? collectUuids(connections) : new HashSet<>();
        VoiceSourceData voiceSourceData = new VoiceSourceData(channel, source, targetUuids);
        sources.put(sourceId, voiceSourceData);

        scriptEntry.saveObject("id", id);
        scriptEntry.saveObject("voicesource", new VoiceSourceTag(sourceId, voiceSourceData));
    }

    private static void handleDelete(ScriptEntry scriptEntry, ElementTag id) {
        String sourceId = id.asString();
        VoiceSourceData data = sources.remove(sourceId);

        if (data == null) {
            Debug.echoError(scriptEntry, "Voice source '%s' not found!".formatted(sourceId));
            return;
        }

        try {
            data.channel().flush();
        } catch (Exception exception) {
            Debug.echoError(scriptEntry, "Error while flushing voice source '%s': %s".formatted(sourceId, exception.getMessage()));
        }
    }


    private static AudioChannel createChannel(
            String sourceId,
            ObjectTag source,
            List<VoicechatConnection> connections,
            boolean applyFilter,
            float distance,
            ScriptEntry scriptEntry
    ) {
        if (source instanceof LocationTag locationTag) {
            return createLocationalChannel(sourceId, locationTag, connections, applyFilter, distance, scriptEntry);
        }

        if (source instanceof EntityTag entityTag) {
            return createEntityChannel(sourceId, entityTag, connections, applyFilter, distance, scriptEntry);
        }

        return createStaticChannel(sourceId, connections, scriptEntry);
    }

    private static LocationalAudioChannel createLocationalChannel(
            String sourceId,
            LocationTag locationTag,
            List<VoicechatConnection> connections,
            boolean applyFilter,
            float distance,
            ScriptEntry scriptEntry
    ) {
        LocationalAudioChannel channel = VoiceAddon.getApi().createLocationalAudioChannel(
                toUUID(sourceId),
                VoiceAddon.getApi().fromServerLevel(locationTag.getWorld()),
                VoiceAddon.getApi().createPosition(locationTag.getX(), locationTag.getY(), locationTag.getZ())
        );

        if (channel == null) {
            Debug.echoError(scriptEntry, "Could not create locational channel!");
            return null;
        }

        channel.setDistance(distance);


        if (applyFilter) {
            channel.setFilter(buildFilter(collectUuids(connections)));
        }

        return channel;
    }

    private static EntityAudioChannel createEntityChannel(
            String sourceId,
            EntityTag entityTag,
            List<VoicechatConnection> connections,
            boolean applyFilter,
            float distance,
            ScriptEntry scriptEntry
    ) {
        EntityAudioChannel channel = VoiceAddon.getApi().createEntityAudioChannel(
                toUUID(sourceId),
                VoiceAddon.getApi().fromEntity(entityTag.getBukkitEntity())
        );

        if (channel == null) {
            Debug.echoError(scriptEntry, "Could not create entity channel!");
            return null;
        }

        channel.setDistance(distance);

        if (applyFilter) {
            channel.setFilter(buildFilter(collectUuids(connections)));
        }

        return channel;
    }

    private static StaticAudioChannel createStaticChannel(
            String sourceId,
            List<VoicechatConnection> connections,
            ScriptEntry scriptEntry
    ) {
        StaticAudioChannel channel = VoiceAddon.getApi().createStaticAudioChannel(toUUID(sourceId));

        if (channel == null) {
            Debug.echoError(scriptEntry, "Could not create static channel!");
            return null;
        }

        connections.forEach(channel::addTarget);
        return channel;
    }


    public static List<VoicechatConnection> resolveConnections(ObjectTag targets, ScriptEntry scriptEntry) {
        Stream<String> nameStream = targets instanceof ListTag listTag
                ? listTag.stream()
                : Stream.of(targets.toString());

        return nameStream
                .map(name -> PlayerTag.valueOf(name, scriptEntry.getContext()))
                .filter(playerTag -> playerTag != null && playerTag.isOnline())
                .map(playerTag -> VoiceAddon.getApi().getConnectionOf(playerTag.getPlayerEntity().getUniqueId()))
                .filter(Objects::nonNull)
                .toList();
    }

    public static Set<UUID> collectUuids(List<VoicechatConnection> connections) {
        Set<UUID> uuids = new HashSet<>();
        for (VoicechatConnection connection : connections) {
            uuids.add(connection.getPlayer().getUuid());
        }
        return uuids;
    }

    public static Predicate<ServerPlayer> buildFilter(Set<UUID> allowedUuids) {
        return serverPlayer -> allowedUuids.contains(serverPlayer.getUuid());
    }


    public static UUID toUUID(String id) {
        return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
    }
}