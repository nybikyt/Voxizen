package dev.nybikyt.voxizen;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import dev.nybikyt.voxizen.events.MicrophoneScriptEvent;

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
    public void registerEvents(EventRegistration eventRegistration) {
        eventRegistration.registerEvent(MicrophonePacketEvent.class, event -> {
            if (!MicrophoneScriptEvent.instance.eventPaths.isEmpty()) {
                MicrophoneScriptEvent.instance.handle(event);
            }
        });
    }
}