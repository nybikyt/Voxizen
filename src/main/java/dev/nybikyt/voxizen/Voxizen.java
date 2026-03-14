package dev.nybikyt.voxizen;

import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.scripts.commands.AbstractCommand;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.nybikyt.voxizen.commands.VoskScriptCommand;
import dev.nybikyt.voxizen.events.MicrophoneScriptEvent;
import dev.nybikyt.voxizen.misc.VoskService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Voxizen extends JavaPlugin {
    public static final Logger LOGGER = LogManager.getLogger(Voxizen.class.getSimpleName());
    private VoiceAddon voiceAddon;
    private static Voxizen instance;

    public VoskService getVoskService() {
        return voskService;
    }

    public static Voxizen getInstance() {
        return instance;
    }

    private VoskService voskService;

    @Override
    public void onLoad() {
        instance = this;
        ScriptEvent.registerScriptEvent(MicrophoneScriptEvent.class);
        DenizenCore.commandRegistry.registerCommand(VoskScriptCommand.class);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service != null) {
            try {
                voiceAddon = new VoiceAddon();
                service.registerPlugin(voiceAddon);
            }
            catch (Exception e) {
                LOGGER.error(e);
                Bukkit.getPluginManager().disablePlugin(this);
            }

        } else {
            LOGGER.error("Error while loading addon! Bye :(");
            Bukkit.getPluginManager().disablePlugin(this);
        }


        String modelName = getConfig().getString("vosk.model");
        String modelUrl = getConfig().getString("vosk.url");

        if (modelName == null || modelUrl == null) {
            getLogger().severe("vosk.model or vosk.url not set in config.yml! Disabling plugin.");
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
            } catch (Exception e) {
                LOGGER.error("Failed to load Vosk: " + e.getMessage());
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
