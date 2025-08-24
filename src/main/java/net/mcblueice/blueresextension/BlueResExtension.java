package net.mcblueice.blueresextension;

import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

import de.Linus122.SafariNet.API.SafariNet;
public class BlueResExtension extends JavaPlugin {
    private static BlueResExtension instance;
    private Logger logger;
    private boolean enableSafariNet;

    public BlueResExtension() {
    }

    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();
        saveDefaultConfig();
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

    public boolean isSafariNetEnabled() {
        return enableSafariNet;
    }

    public void refreshFeatures() {
        enableSafariNet = getConfig().getBoolean("features.safarinet", true);

        if (enableSafariNet) {
            if (getServer().getPluginManager().getPlugin("SafariNet") != null) {
                getServer().getConsoleSender().sendMessage("§r[BlueResExtension] §aSafariNet 已啟用 已開啟 SafariNet 功能！");
                SafariNet.addListener(new SafariNetListener());
                enableSafariNet = true;
            } else {
                getServer().getConsoleSender().sendMessage("§r[BlueResExtension] §cSafariNet 未啟用 已關閉 SafariNet 功能！");
                enableSafariNet = false;
            }
        } else {
            getServer().getConsoleSender().sendMessage("§r[BlueResExtension] §cSafariNet 功能已關閉");
        }
    }
}
