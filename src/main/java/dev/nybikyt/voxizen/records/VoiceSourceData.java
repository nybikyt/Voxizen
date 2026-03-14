package dev.nybikyt.voxizen.records;

import com.denizenscript.denizencore.objects.ObjectTag;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record VoiceSourceData(
        AudioChannel channel,
        @Nullable ObjectTag source,
        Set<UUID> targetUuids
) {
    public VoiceSourceData(AudioChannel channel, @Nullable ObjectTag source, Set<UUID> targetUuids) {
        this.channel = channel;
        this.source = source;
        this.targetUuids = new HashSet<>(targetUuids);
    }
}