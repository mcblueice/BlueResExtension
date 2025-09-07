package net.mcblueice.blueresextension.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.mcblueice.blueresextension.BlueResExtension;

/**
 * 粒子預覽工具：集中管理方框邊/面繪製與排程。
 */
public final class PreviewUtil {

    private PreviewUtil() {}

    private static final Map<UUID, Map<String, TaskScheduler.RepeatingTaskHandler>> PREVIEWS = new ConcurrentHashMap<>();

    //region Public API
    /**
     * 依據 loc1/loc2 開始或更新一個預覽繪製任務（以 key 識別）。
     * - frameParticle 為 null 則不畫邊框；sidesParticle 為 null 則不畫側面。
     * - frameStep/sidesStep 為顯示點步距；intervalTicks 為每次顯示間隔時間（tick）。
     */
    public static void startOrUpdatePreview(Player viewer,
                                            Location loc1, Location loc2,
                                            Particle frameParticle, Particle sidesParticle,
                                            int frameStep,
                                            int sidesStep,
                                            long intervalTicks,
                                            String key) {
        if (viewer == null || loc1 == null || loc2 == null || key == null) return;
        BlueResExtension plugin = BlueResExtension.getInstance();
        if (plugin == null) return;

        // 取消舊任務
        stopPreview(viewer.getUniqueId(), key);

        // 正規化方框
        Box c = Box.from(loc1, loc2);
        int fs = Math.max(1, frameStep);
        int ss = Math.max(1, sidesStep);
        long period = Math.max(1L, intervalTicks);

    TaskScheduler.RepeatingTaskHandler h = TaskScheduler.runRepeatingTask(viewer, plugin, () -> {
            if (!viewer.isOnline()) return;
            if (viewer.getWorld() != c.world) return;
            if (frameParticle != null) drawEdgesOnce(viewer, c, frameParticle, fs);
            if (sidesParticle != null) drawFacesOnce(viewer, c, sidesParticle, ss);
    }, 1L, period);

        PREVIEWS.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(key, h);
    }

    /** 停止特定 key 的預覽。 */
    public static void stopPreview(UUID uuid, String key) {
        if (uuid == null || key == null) return;
        var map = PREVIEWS.get(uuid);
        if (map == null) return;
        TaskScheduler.RepeatingTaskHandler h = map.remove(key);
        if (h != null) h.cancel();
        if (map.isEmpty()) PREVIEWS.remove(uuid);
    }

    /** 停止該玩家的所有預覽。 */
    public static void clearAllPreviews(UUID uuid) {
        var map = PREVIEWS.remove(uuid);
        if (map == null) return;
        for (TaskScheduler.RepeatingTaskHandler h : map.values()) {
            try { if (h != null) h.cancel(); } catch (Throwable ignored) {}
        }
    }
    //endregion

    //region Internal: data model
    private static final class Box {
        final World world;
        final int minX, minY, minZ, maxX, maxY, maxZ;
        private Box(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.world = world;
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }
        static Box from(Location a, Location b) {
            if (a.getWorld() != b.getWorld()) {
                return new Box(a.getWorld(), a.getBlockX(), a.getBlockY(), a.getBlockZ(), a.getBlockX(), a.getBlockY(), a.getBlockZ());
            }
            return new Box(a.getWorld(),
                Math.min(a.getBlockX(), b.getBlockX()),
                Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()),
                Math.max(a.getBlockZ(), b.getBlockZ()));
        }
    }
    //endregion

    //region Internal: drawing helpers
    private static void drawLineX(Player viewer, Particle particle, int x0, int x1, int y, int z, int step) {
        for (int x = x0; x <= x1; x += step) {
            spawnExact(viewer, particle, x, y, z);
        }
    }

    private static void drawLineZ(Player viewer, Particle particle, int z0, int z1, int x, int y, int step) {
        for (int z = z0; z <= z1; z += step) {
            spawnExact(viewer, particle, x, y, z);
        }
    }

    private static void drawLineY(Player viewer, Particle particle, int y0, int y1, int x, int z, int step) {
        for (int y = y0; y <= y1; y += step) {
            spawnExact(viewer, particle, x, y, z);
        }
    }

    private static void drawEdgesOnce(Player viewer, Box box, Particle particle, int step) {
        // Use boundary planes: x=minX and x=maxX+1, y=minY and y=maxY+1, z=minZ and z=maxZ+1
        int x0 = box.minX, x1 = box.maxX + 1;
        int y0 = box.minY, y1 = box.maxY + 1;
        int z0 = box.minZ, z1 = box.maxZ + 1;

    // Bottom rectangle (y = y0)
    drawLineX(viewer, particle, x0, x1, y0, z0, step);
    drawLineX(viewer, particle, x0, x1, y0, z1, step);
    drawLineZ(viewer, particle, z0, z1, x0, y0, step);
    drawLineZ(viewer, particle, z0, z1, x1, y0, step);

    // Top rectangle (y = y1)
    drawLineX(viewer, particle, x0, x1, y1, z0, step);
    drawLineX(viewer, particle, x0, x1, y1, z1, step);
    drawLineZ(viewer, particle, z0, z1, x0, y1, step);
    drawLineZ(viewer, particle, z0, z1, x1, y1, step);

    // Vertical edges (four columns)
    drawLineY(viewer, particle, y0, y1, x0, z0, step);
    drawLineY(viewer, particle, y0, y1, x0, z1, step);
    drawLineY(viewer, particle, y0, y1, x1, z0, step);
    drawLineY(viewer, particle, y0, y1, x1, z1, step);

    // Always draw the 8 corner points to ensure visibility regardless of step spacing
    spawnExact(viewer, particle, x0, y0, z0);
    spawnExact(viewer, particle, x0, y0, z1);
    spawnExact(viewer, particle, x1, y0, z0);
    spawnExact(viewer, particle, x1, y0, z1);
    spawnExact(viewer, particle, x0, y1, z0);
    spawnExact(viewer, particle, x0, y1, z1);
    spawnExact(viewer, particle, x1, y1, z0);
    spawnExact(viewer, particle, x1, y1, z1);
    }

    private static void drawFacesOnce(Player viewer, Box box, Particle particle, int step) {
        int x0 = box.minX, x1 = box.maxX + 1;
        int y0 = box.minY, y1 = box.maxY + 1;
        int z0 = box.minZ, z1 = box.maxZ + 1;

        // Exclude borders to avoid overlapping with frame
        for (int x = x0 + step; x <= x1 - step; x += step) {
            for (int y = y0 + step; y <= y1 - step; y += step) {
                spawnExact(viewer, particle, x, y, z0);
                spawnExact(viewer, particle, x, y, z1);
            }
        }
        for (int z = z0 + step; z <= z1 - step; z += step) {
            for (int y = y0 + step; y <= y1 - step; y += step) {
                spawnExact(viewer, particle, x0, y, z);
                spawnExact(viewer, particle, x1, y, z);
            }
        }
        for (int x = x0 + step; x <= x1 - step; x += step) {
            for (int z = z0 + step; z <= z1 - step; z += step) {
                spawnExact(viewer, particle, x, y0, z);
                spawnExact(viewer, particle, x, y1, z);
            }
        }
    }

    private static void spawnExact(Player viewer, Particle particle, double x, double y, double z) {
        Location loc = new Location(viewer.getWorld(), x, y, z);
        String name = particle.name();
        if (name.equalsIgnoreCase("REDSTONE") || name.equalsIgnoreCase("DUST")) {
            try {
                Particle.DustOptions opts = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.0f);
                viewer.spawnParticle(particle, loc, 1, 0, 0, 0, 0, opts);
                return;
            } catch (Throwable ignored) {}
        }
        viewer.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
    }
    //endregion
}
