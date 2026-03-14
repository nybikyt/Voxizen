package dev.nybikyt.voxizen;

import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectFetcher;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.nybikyt.voxizen.commands.AudioCommand;
import dev.nybikyt.voxizen.commands.VoiceSourceCommand;
import dev.nybikyt.voxizen.commands.VoskCommand;
import dev.nybikyt.voxizen.events.MicrophoneEvent;
import dev.nybikyt.voxizen.misc.VoskService;
import dev.nybikyt.voxizen.objects.VoiceSourceTag;
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

        ScriptEvent.registerScriptEvent(MicrophoneEvent.class);

        DenizenCore.commandRegistry.registerCommand(VoskCommand.class);
        DenizenCore.commandRegistry.registerCommand(AudioCommand.class);
        DenizenCore.commandRegistry.registerCommand(VoiceSourceCommand.class);

        ObjectFetcher.registerWithObjectFetcher(VoiceSourceTag.class, VoiceSourceTag.tagProcessor);
    }

    @Override
    public void onEnable() {
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
                LOGGER.info("Vosk ready!");
            } catch (Exception exception) {
                LOGGER.error("Failed to load Vosk: {}", exception.getMessage());
            }
        });
    }

    @Override
    public void onDisable() {
        if (voiceAddon != null) {
            getServer().getServicesManager().unregister(voiceAddon);
        }
        if (voskService != null) {
            voskService.close();
        }
    }
}