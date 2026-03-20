package dev.nybikyt.voxizen;

import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectFetcher;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.nybikyt.voxizen.commands.AudioCommand;
import dev.nybikyt.voxizen.commands.VoiceGroupCommand;
import dev.nybikyt.voxizen.commands.VoiceSourceCommand;
import dev.nybikyt.voxizen.commands.VolumeCategoryCommand;
import dev.nybikyt.voxizen.commands.VoskCommand;
import dev.nybikyt.voxizen.events.GroupCreatedEvent;
import dev.nybikyt.voxizen.events.GroupRemovedEvent;
import dev.nybikyt.voxizen.events.PlayerJoinGroupEvent;
import dev.nybikyt.voxizen.events.PlayerLeaveGroupEvent;
import dev.nybikyt.voxizen.events.PlayerMicrophoneEvent;
import dev.nybikyt.voxizen.misc.VoskService;
import dev.nybikyt.voxizen.objects.VoiceGroupTag;
import dev.nybikyt.voxizen.objects.VoiceSourceTag;
import dev.nybikyt.voxizen.properties.PlayerTagExtensions;
import dev.nybikyt.voxizen.properties.ServerTagExtensions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Voxizen extends JavaPlugin {

    public static final Logger LOGGER = LogManager.getLogger(Voxizen.class.getSimpleName());

    private static Voxizen instance;
    private VoiceAddon voiceAddon;
    private VoskService voskService;

    public static Voxizen getInstance() {
        return instance;
    }

    public VoskService getVoskService() {
        return voskService;
    }

    @Override
    public void onLoad() {
        instance = this;

        ScriptEvent.registerScriptEvent(PlayerMicrophoneEvent.class);
        ScriptEvent.registerScriptEvent(PlayerJoinGroupEvent.class);
        ScriptEvent.registerScriptEvent(PlayerLeaveGroupEvent.class);
        ScriptEvent.registerScriptEvent(GroupCreatedEvent.class);
        ScriptEvent.registerScriptEvent(GroupRemovedEvent.class);

        DenizenCore.commandRegistry.registerCommand(VoskCommand.class);
        DenizenCore.commandRegistry.registerCommand(AudioCommand.class);
        DenizenCore.commandRegistry.registerCommand(VoiceSourceCommand.class);
        DenizenCore.commandRegistry.registerCommand(VolumeCategoryCommand.class);
        DenizenCore.commandRegistry.registerCommand(VoiceGroupCommand.class);

        ObjectFetcher.registerWithObjectFetcher(VoiceSourceTag.class, VoiceSourceTag.tagProcessor).generateBaseTag();
        ObjectFetcher.registerWithObjectFetcher(VoiceGroupTag.class, VoiceGroupTag.tagProcessor).generateBaseTag();
    }

    @Override
    public void onEnable() {
        ServerTagExtensions.register();
        PlayerTagExtensions.register();

        saveDefaultConfig();

        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            try {
                voiceAddon = new VoiceAddon();
                service.registerPlugin(voiceAddon);
            } catch (Exception exception) {
                LOGGER.error("Failed to register VoiceAddon: {}", exception.getMessage());
                Bukkit.getPluginManager().disablePlugin(this);
            }
        } else {
            LOGGER.error("BukkitVoicechatService not found! Is Simple Voice Chat installed?");
            Bukkit.getPluginManager().disablePlugin(this);
        }

        String modelName = getConfig().getString("vosk.model");
        String modelUrl = getConfig().getString("vosk.url");

        if (modelName == null || modelUrl == null) {
            LOGGER.error("vosk.model or vosk.url not set in config.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Path modelPath = getDataFolder().toPath().resolve("models").resolve(modelName);

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                if (Files.notExists(modelPath)) {
                    voskService = new VoskService(modelName, modelUrl);
                    getConfig().set("vosk.model", modelName);
                    saveConfig();
                } else {
                    voskService = new VoskService(modelName, null);
                }
                LOGGER.info("Vosk ready! Join our Discord: https://dsc.gg/dsng");
            } catch (Exception exception) {
                LOGGER.error("Failed to load Vosk: {}", exception.getMessage());
            }
        });
    }

    @Override
    public void onDisable() {
        AudioCommand.activeTasks.values().forEach(task -> task.cancel(false));
        AudioCommand.SCHEDULER.shutdown();

        if (voiceAddon != null) {
            getServer().getServicesManager().unregister(voiceAddon);
        }
        if (voskService != null) {
            voskService.close();
        }
    }
}