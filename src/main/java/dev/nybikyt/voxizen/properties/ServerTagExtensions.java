package dev.nybikyt.voxizen.properties;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import de.maxhenkel.voicechat.api.Group;
import dev.nybikyt.voxizen.VoiceAddon;
import dev.nybikyt.voxizen.commands.VoiceSourceCommand;
import dev.nybikyt.voxizen.commands.VolumeCategoryCommand;
import dev.nybikyt.voxizen.objects.VoiceGroupTag;

import static com.denizenscript.denizen.tags.core.ServerTagBase.instance;

public class ServerTagExtensions {

    public static void register() {


        // <--[tag]
        // @attribute <server.volume_categories>
        //
        // @returns ListTag(ElementTag)
        //
        // @description
        // Returns a sorted list of all registered volume category IDs.
        //
        // @example
        // - foreach <server.volume_categories> as:id:
        //     - announce "Category: <[id]>"
        //
        // -->
        instance.tagProcessor.registerStaticTag(ListTag.class, "volume_categories", (attribute, object) -> {
            ListTag result = new ListTag();
            VolumeCategoryCommand.categories.keySet()
                    .stream()
                    .sorted()
                    .map(ElementTag::new)
                    .forEach(result::addObject);
            return result;
        });

        // <--[tag]
        // @attribute <server.voice_sources>
        //
        // @returns ListTag(ElementTag)
        //
        // @description
        // Returns a sorted list of all active voice source IDs (created via voicesource command).
        //
        // @example
        // - foreach <server.voice_sources> as:id:
        //     - narrate "Source: <[id]>"
        //
        // -->
        instance.tagProcessor.registerStaticTag(ListTag.class, "voice_sources", (attribute, object) -> {
            ListTag result = new ListTag();
            VoiceSourceCommand.sources.keySet()
                    .stream()
                    .sorted()
                    .map(ElementTag::new)
                    .forEach(result::addObject);
            return result;
        });


        // <--[tag]
        // @attribute <server.voice_groups>
        //
        // @returns ListTag(VoiceGroupTag)
        //
        // @description
        // Returns a list of all voice groups on the server, including unmanaged (player-created) ones.
        // Managed groups are identified by their string id, unmanaged ones by their UUID.
        // Returns an empty list if the voice chat API is not yet ready.
        //
        // @example
        // - foreach <server.voice_groups> as:group:
        //     - narrate "<[group].name> (managed: <[group].managed>)"
        //
        // -->
        instance.tagProcessor.registerTag(ListTag.class, "voice_groups", (attribute, object) -> {
            // Fix #5 — guard against null API
            if (VoiceAddon.getApi() == null) return new ListTag();
            ListTag result = new ListTag();
            for (Group group : VoiceAddon.getApi().getGroups()) {
                result.addObject(VoiceGroupTag.fromApiGroup(group));
            }
            return result;
        });
    }
}