package net.mcblueice.blueresextension;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.ResidencePermissions;
import com.bekvon.bukkit.residence.containers.Flags;

import de.Linus122.SafariNet.API.Status;
import de.Linus122.SafariNet.API.Listener;

public class SafariNetListener implements Listener {

    @Override
    public void playerCatchEntity(Player player, Entity entity, Status status) {
        Location loc = player.getTargetBlock((Set)null, 100).getLocation();
        ClaimedResidence res = Residence.getInstance().getResidenceManager().getByLoc(loc);
        if (res != null) {
            ResidencePermissions perms = res.getPermissions();
            boolean hasPermission = perms.playerHas(player, Flags.destroy, true);
            if (!hasPermission) {
                status.setCancelled(true);
			    String name = Flags.destroy.getName();
                player.sendMessage("§7§l[§2§l領地§7§l]§r§c你沒有 §r" + name + " §c的權限");
            }
        }
    }

    @Override
    public void playerReleaseEntity(Player player, Entity entity, Status status) {
        Location loc = player.getTargetBlock((Set)null, 100).getLocation();
        ClaimedResidence res = Residence.getInstance().getResidenceManager().getByLoc(loc);
        if (res != null) {
            ResidencePermissions perms = res.getPermissions();
            boolean hasPermission = perms.playerHas(player, Flags.place, true);
            if (!hasPermission) {
                status.setCancelled(true);
			    String name = Flags.place.getName();
                player.sendMessage("§7§l[§2§l領地§7§l]§r§c你沒有 §r" + name + " §c的權限");
            }
        }
    }
}
