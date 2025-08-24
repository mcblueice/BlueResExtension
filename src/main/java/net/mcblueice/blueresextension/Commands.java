package net.mcblueice.blueresextension;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;


public class Commands implements CommandExecutor {
    private final BlueResExtension plugin;

    public Commands(BlueResExtension plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "reload":
                    if (!sender.hasPermission("blueresextension.reload")) {
                        sender.sendMessage("§7§l[§a§l系統§7§l]§r§c你沒有權限使用此指令!");
                        return true;
                    }
                    plugin.reloadConfig();
                    sender.sendMessage("§7§l[§a§l系統§7§l]§r§aConfig已重新加載");
                    return true;
            }
        }
        sender.sendMessage("§7§l[§a§l系統§7§l]§r§c用法錯誤");
        return true;
    }
}
