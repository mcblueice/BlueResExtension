package net.mcblueice.blueresextension.features;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.containers.Flags;

import de.Linus122.SafariNet.API.Status;
import de.Linus122.SafariNet.API.Listener;

import net.mcblueice.blueresextension.BlueResExtension;
import net.mcblueice.blueresextension.utils.ConfigManager;

public class Safarinet implements Listener {

    private final BlueResExtension plugin;
    private final ConfigManager lang;

    public Safarinet(BlueResExtension plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public void playerCatchEntity(Player player, Entity entity, Status status) {
        Block target = player.getTargetBlockExact(100);
        Location loc = (target != null ? target.getLocation() : player.getLocation());
        ClaimedResidence res = Residence.getInstance().getResidenceManager().getByLoc(loc);
        if (res != null) {
            String catchFlag = plugin.getConfig().getString("safarinet.catch_flag", "destroy");
            Flags flag = Flags.valueOf(catchFlag);
            boolean hasPermission = res.getPermissions().playerHas(player, flag, true);
            if (!hasPermission) {
                status.setCancelled(true);
                player.sendMessage(lang.get("safarinet.noperm", flag.getName()));
            }
        }
    }

    @Override
    public void playerReleaseEntity(Player player, Entity entity, Status status) {
        Block target = player.getTargetBlockExact(100);
        Location loc = (target != null ? target.getLocation() : player.getLocation());
        ClaimedResidence res = Residence.getInstance().getResidenceManager().getByLoc(loc);
        if (res != null) {
            String releaseFlag = plugin.getConfig().getString("safarinet.release_flag", "place");
            Flags flag = Flags.valueOf(releaseFlag);
            boolean hasPermission = res.getPermissions().playerHas(player, flag, true);
            if (!hasPermission) {
                status.setCancelled(true);
                player.sendMessage(lang.get("safarinet.noperm", flag.getName()));
            }
        }
    }
}
