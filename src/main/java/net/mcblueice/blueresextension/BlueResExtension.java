package net.mcblueice.blueresextension;

import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

import de.Linus122.SafariNet.API.SafariNet;
import net.mcblueice.blueresextension.features.Expandtool;
import net.mcblueice.blueresextension.features.Safarinet;
import net.mcblueice.blueresextension.utils.ConfigManager;
public class BlueResExtension extends JavaPlugin {
    private static BlueResExtension instance;
    private ConfigManager languageManager;
    private Logger logger;
    private boolean enableSafariNet;
    private boolean enableExpandTool;

    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();
        saveDefaultConfig();
        languageManager = new ConfigManager(this);
        refreshFeatures();

        getCommand("blueresextension").setExecutor(new Commands(this));

        logger.info("BlueResExtension 已啟動");
    }

    @Override
    public void onDisable() {
        logger.info("BlueResExtension 已卸載");
    }

    public static BlueResExtension getInstance() {
        return instance;
    }

    public ConfigManager getLanguageManager() {
        return languageManager;
    }

    public boolean isSafariNetEnabled() {
        return enableSafariNet;
    }
    public boolean isExpandToolEnabled() {
        return enableExpandTool;
    }

    public void refreshFeatures() {
        enableSafariNet = getConfig().getBoolean("safarinet.enable", true);
        enableExpandTool = getConfig().getBoolean("expandtool.enable", true);

        if (enableSafariNet) {
            if (getServer().getPluginManager().getPlugin("SafariNet") != null) {
                getServer().getConsoleSender().sendMessage("§r[BlueResExtension] §aSafariNet 已啟用 已開啟 SafariNet 功能！");
                SafariNet.addListener(new Safarinet(this));
                enableSafariNet = true;
            } else {
                getServer().getConsoleSender().sendMessage("§r[BlueResExtension] §cSafariNet 未啟用 已關閉 SafariNet 功能！");
                enableSafariNet = false;
            }
        } else {
            getServer().getConsoleSender().sendMessage("§r[BlueResExtension] §cSafariNet 功能已關閉");
        }

        if (enableExpandTool) {
            getServer().getConsoleSender().sendMessage("§r[BlueResExtension] §a已開啟 ExpandTool 功能！");
            Expandtool.register(this);
            enableExpandTool = true;
        } else {
            getServer().getConsoleSender().sendMessage("§r[BlueResExtension] §cExpandTool 功能已關閉");
        }
    }
}
