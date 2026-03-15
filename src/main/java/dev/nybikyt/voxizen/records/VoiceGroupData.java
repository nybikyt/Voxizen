package dev.nybikyt.voxizen.records;

import de.maxhenkel.voicechat.api.Group;
import org.jetbrains.annotations.Nullable;

public record VoiceGroupData(
        Group group,
        @Nullable String password
) {}