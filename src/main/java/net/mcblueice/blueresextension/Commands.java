package net.mcblueice.blueresextension;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.mcblueice.blueresextension.features.Expandtool;


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
                    plugin.getLanguageManager().reload();
                    sender.sendMessage("§7§l[§a§l系統§7§l]§r§aConfig已重新加載");
                    return true;
                case "expand":
                    if (!plugin.isExpandToolEnabled()) {
                        sender.sendMessage("§7§l[§a§l系統§7§l]§r§c擴展工具未啟用");
                        return true;
                    }
                    if (!sender.hasPermission("blueresextension.expand")) {
                        sender.sendMessage("§7§l[§a§l系統§7§l]§r§c你沒有權限使用此指令!");
                        return true;
                    }
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§c此指令僅限玩家");
                        return true;
                    }
                    Expandtool.command(player);
                    return true;
                default:
                    sender.sendMessage("§7§l[§a§l系統§7§l]§r§c用法錯誤 | /blueresextension reload|expand");
                    return true;
            }
        }
        sender.sendMessage("§7§l[§a§l系統§7§l]§r§c用法錯誤 | /blueresextension reload|expand");
        return true;
    }

    // 留白：輸出工作委派至 Expandtool.command(player)
}
