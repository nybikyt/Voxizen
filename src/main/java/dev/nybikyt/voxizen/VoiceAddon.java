package dev.nybikyt.voxizen;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.*;
import dev.nybikyt.voxizen.events.*;

public class VoiceAddon implements VoicechatPlugin {

    private static VoicechatServerApi api;

    public static VoicechatServerApi getApi() {
        return api;
    }

    @Override
    public String getPluginId() {
        return Voxizen.class.getSimpleName();
    }

    @Override
    public void initialize(VoicechatApi voicechatApi) {
        api = (VoicechatServerApi) voicechatApi;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            if (!PlayerMicrophoneEvent.instance.eventPaths.isEmpty()) {
                PlayerMicrophoneEvent.instance.handle(event);
            }
        });

        registration.registerEvent(JoinGroupEvent.class, event -> {
            if (!PlayerJoinGroupEvent.instance.eventPaths.isEmpty()) {
                PlayerJoinGroupEvent.instance.handle(event);
            }
        });

        registration.registerEvent(LeaveGroupEvent.class, event -> {
            if (!PlayerLeaveGroupEvent.instance.eventPaths.isEmpty()) {
                PlayerLeaveGroupEvent.instance.handle(event);
            }
        });

        registration.registerEvent(CreateGroupEvent.class, event ->
                GroupCreatedEvent.instance.handleFromApi(event)
        );

        registration.registerEvent(RemoveGroupEvent.class, event ->
                GroupRemovedEvent.instance.handleFromApi(event)
        );
    }
}