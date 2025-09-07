package net.mcblueice.blueresextension.utils;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.api.ResidenceApi;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.CuboidArea;

import net.mcblueice.blueresextension.BlueResExtension;
import net.mcblueice.blueresextension.features.Expandtool.Direction;

/**
 * Residence 相關共用工具。
 */
public final class ResidenceUtil {

    private ResidenceUtil() {}

    // 已移除 Cuboid；改以 Location[min,max] 代表邊界

    //region Selection tool
    /**
     * 以 Residence 的 Global.SelectionToolId 判定是否為選取工具。
     */
    public static boolean isExpandTool(ItemStack item) {
        if (item == null) return false;
        String toolName = Residence.getInstance().getConfig().getString("Global.SelectionToolId", "WOODEN_HOE");
        if (!BlueResExtension.getInstance().getConfig().getBoolean("expandtool.tool.useResidenceSelectionTool", true)){
            toolName = BlueResExtension.getInstance().getConfig().getString("expandtool.tool.selectionTool", "FEATHER");
        }
        Material toolMat = Material.matchMaterial(toolName);
        return toolMat != null && item.getType() == toolMat;
    }
    //endregion

    //region Selection / Residence bounds
    /** 取得玩家目前的 Residence 選取框邊界（min,max）。若不存在或跨世界則回傳 null。 */
    public static Location[] getPlayerSelectionBounds(Player player) {
        try {
            var selection = Residence.getInstance().getSelectionManager().getSelection(player);
            if (selection == null) return null;
            Location loc1 = selection.getBaseLoc1();
            Location loc2 = selection.getBaseLoc2();
            if (loc1 == null || loc2 == null) return null;
            if (loc1.getWorld() != loc2.getWorld()) return null;
            return normalize(loc1, loc2);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 取得指定地點所在的領地主區域方框。 */
    @SuppressWarnings("deprecation")
    public static Location[] getResidenceMainAreaBoundsAt(Location loc) {
        if (loc == null) return null;
        ClaimedResidence res = Residence.getInstance().getResidenceManager().getByLoc(loc);
        if (res == null) return null;
        CuboidArea area = res.getMainArea();
        if (area == null) return null;
        Location low = area.getLowLoc();
        Location high = area.getHighLoc();
        if (low == null || high == null) return null;
        return normalize(low, high);
    }

    /** 直接由 ClaimedResidence 取得主區域方框。 */
    @SuppressWarnings("deprecation")
    public static Location[] getResidenceMainAreaBounds(ClaimedResidence res) {
        if (res == null) return null;
        CuboidArea area = res.getMainArea();
        if (area == null) return null;
        Location low = area.getLowLoc();
        Location high = area.getHighLoc();
        if (low == null || high == null) return null;
        return normalize(low, high);
    }

    /** 取得指定地點所在的領地（可為 null）。 */
    public static ClaimedResidence getResidenceAt(Location loc) {
        if (loc == null) return null;
        return Residence.getInstance().getResidenceManager().getByLoc(loc);
    }

    /**
     * 依序回傳：選取框（可選擇是否要求玩家站在框內），否則回退為所在領地主區域。
     */
    public static Location[] getEffectiveBounds(Player player, boolean requireInsideSelection) {
        Location[] sel = getPlayerSelectionBounds(player);
        if (sel != null) {
            if (!requireInsideSelection || contains(sel[0], sel[1], player.getLocation())) return sel;
        }
        return getResidenceMainAreaBoundsAt(player.getLocation());
    }

    // 工具：正規化與包含判斷（方塊座標）
    public static Location[] normalize(Location a, Location b) {
        World w = a.getWorld();
        if (w != b.getWorld()) return new Location[] { a, a };
        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxX = Math.max(a.getBlockX(), b.getBlockX());
        int maxY = Math.max(a.getBlockY(), b.getBlockY());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        Location min = new Location(w, minX, minY, minZ);
        Location max = new Location(w, maxX, maxY, maxZ);
        return new Location[] { min, max };
    }

    public static boolean contains(Location min, Location max, Location p) {
        if (min == null || max == null || p == null) return false;
        if (min.getWorld() != max.getWorld() || min.getWorld() != p.getWorld()) return false;
        int x = p.getBlockX(), y = p.getBlockY(), z = p.getBlockZ();
        return x >= min.getBlockX() && x <= max.getBlockX()
            && y >= min.getBlockY() && y <= max.getBlockY()
            && z >= min.getBlockZ() && z <= max.getBlockZ();
    }
    //endregion

    // 預覽繪製已抽離至 PreviewUtil

    //region Facing detection
    /**
     * 依玩家目前的 yaw/pitch 判定朝向，上/下優先，與 Expandtool 既有邏輯一致。
     */
    public static Direction playerFacing(Player player) {
        double yaw = player.getLocation().getYaw() + 180;    // 0..360
        double pitch = player.getLocation().getPitch() + 90; // 0..180
        if (pitch < 5) return Direction.UP;
        if (pitch > 175) return Direction.DOWN;
        if ((yaw >= 315 && yaw <= 360) || (yaw >= 0 && yaw < 45)) return Direction.NORTH;
        if (yaw >= 45 && yaw < 135) return Direction.EAST;
        if (yaw >= 135 && yaw < 225) return Direction.SOUTH;
        if (yaw >= 225 && yaw < 315) return Direction.WEST;
        return Direction.NORTH;
    }
    //endregion

    //region Constraints adjustment
    /**
     * 依伺服器限制調整預覽邊界：
     * - 目前僅夾在世界高度範圍內；未來可加入與他領地/Gap 邏輯。
     * @param res 鎖定的領地
     * @param base 原始方框（min,max）
     * @param proposed 擴展後提案方框（min,max）
     * @return 調整後的方框（min,max），或 null 表示沿用 proposed
     */
    public static Location[] adjustPreviewToConstraints(Player actor, ClaimedResidence res, Location[] base, Location[] proposed) {
        if (proposed == null || proposed[0] == null || proposed[1] == null) return proposed;
        Location min = proposed[0];
        Location max = proposed[1];
        World world = min.getWorld();
        if (world == null) return proposed;
        int minBuild = world.getMinHeight();
        int maxBuild = world.getMaxHeight() - 1; // inclusive top

        int minX = min.getBlockX();
        int minY = Math.max(minBuild, min.getBlockY());
        int minZ = min.getBlockZ();
        int maxX = max.getBlockX();
        int maxY = Math.min(maxBuild, max.getBlockY());
        int maxZ = max.getBlockZ();

        // 先夾在世界高度
        Location adjMin = new Location(world, minX, minY, minZ);
        Location adjMax = new Location(world, maxX, maxY, maxZ);
        Location[] normalized = normalize(adjMin, adjMax);

        // 讀取玩家群組限制（尺寸與高度）；優先使用實際玩家群組，取不到時回退預設群組
        var pm = ResidenceApi.getPlayerManager();
        var group = pm != null ? (actor != null ? pm.getGroup(actor.getName()) : pm.getGroup(null)) : null;

        // 絶對高度限制（若提供）
        int gMinH = group != null ? group.getMinHeight() : minBuild;
        int gMaxH = group != null ? group.getMaxHeight() : maxBuild;
        gMinH = Math.max(gMinH, minBuild);
        gMaxH = Math.min(gMaxH, maxBuild);

        int nMinX = normalized[0].getBlockX();
        int nMinY = Math.max(gMinH, normalized[0].getBlockY());
        int nMinZ = normalized[0].getBlockZ();
        int nMaxX = normalized[1].getBlockX();
        int nMaxY = Math.min(gMaxH, normalized[1].getBlockY());
        int nMaxZ = normalized[1].getBlockZ();

        // 軸向尺寸限制（若 > 0 才生效）
        int gMaxXSize = group != null ? group.getMaxX() : 0;
        int gMaxYSize = group != null ? group.getMaxY() : 0;
        int gMaxZSize = group != null ? group.getMaxZ() : 0;

        int bMinX = base != null && base[0] != null ? base[0].getBlockX() : nMinX;
        int bMaxX = base != null && base[1] != null ? base[1].getBlockX() : nMaxX;
        int bMinY = base != null && base[0] != null ? base[0].getBlockY() : nMinY;
        int bMaxY = base != null && base[1] != null ? base[1].getBlockY() : nMaxY;
        int bMinZ = base != null && base[0] != null ? base[0].getBlockZ() : nMinZ;
        int bMaxZ = base != null && base[1] != null ? base[1].getBlockZ() : nMaxZ;

        int[] adjX = adjustAxisWithMaxSize(bMinX, bMaxX, nMinX, nMaxX, gMaxXSize);
        nMinX = adjX[0]; nMaxX = adjX[1];
        int[] adjY = adjustAxisWithMaxSize(bMinY, bMaxY, nMinY, nMaxY, gMaxYSize);
        nMinY = adjY[0]; nMaxY = adjY[1];
        int[] adjZ = adjustAxisWithMaxSize(bMinZ, bMaxZ, nMinZ, nMaxZ, gMaxZSize);
        nMinZ = adjZ[0]; nMaxZ = adjZ[1];

        Location outMin = new Location(world, nMinX, nMinY, nMinZ);
        Location outMax = new Location(world, nMaxX, nMaxY, nMaxZ);
        Location[] out = normalize(outMin, outMax);

        // TODO: 插入與其他領地的碰撞檢查與 gap 調整
        return out;
    }

    private static int[] adjustAxisWithMaxSize(int baseMin, int baseMax, int curMin, int curMax, int maxSize) {
        if (maxSize <= 0) return new int[] { curMin, curMax }; // 無限制
        int baseSpan = Math.max(1, baseMax - baseMin + 1);
        int curSpan = Math.max(1, curMax - curMin + 1);
        if (curSpan <= maxSize) return new int[] { curMin, curMax };

        int allowedInc = Math.max(0, maxSize - baseSpan);
        int incNeg = Math.max(0, baseMin - curMin); // 向負方向擴張量
        int incPos = Math.max(0, curMax - baseMax); // 向正方向擴張量
        int totalInc = incNeg + incPos;

        int outMin = curMin, outMax = curMax;
        if (allowedInc <= 0) {
            // 不允許任何擴張，退回基準邊對應的一側（保留縮小）
            if (incNeg > 0) outMin = baseMin;
            if (incPos > 0) outMax = baseMax;
            return new int[] { Math.min(outMin, outMax), Math.max(outMin, outMax) };
        }

        if (totalInc <= allowedInc) {
            return new int[] { curMin, curMax }; // 理論上不會到這裡，保險
        }

        if (incNeg > 0 && incPos > 0) {
            double scale = allowedInc / (double) totalInc;
            int keepNeg = (int) Math.floor(incNeg * scale);
            int keepPos = allowedInc - keepNeg;
            outMin = baseMin - keepNeg;
            outMax = baseMax + keepPos;
        } else if (incPos > 0) {
            int keepPos = Math.min(incPos, allowedInc);
            outMax = baseMax + keepPos;
        } else if (incNeg > 0) {
            int keepNeg = Math.min(incNeg, allowedInc);
            outMin = baseMin - keepNeg;
        }
        return new int[] { Math.min(outMin, outMax), Math.max(outMin, outMax) };
    }
    //endregion

    //region RangeGap parsing (Residence Global.AntiGreef.RangeGaps)
    /**
     * 取得 Residence 設定中的 RangeGap（與其他領地必須保留的間距，單位：方塊）。
     * 優先匹配世界名，其次使用 all / default 項，若均無則回傳 0。
     * 例：
     * Global:
     *   AntiGreef:
     *     RangeGaps:
     *     - all-0
     *     - world-5
     */
    public static int getRangeGap(World world) {
        if (world == null) return 0;
        return getRangeGap(world.getName());
    }

    /** 以地點取得 RangeGap。*/
    public static int getRangeGap(Location loc) {
        return loc != null ? getRangeGap(loc.getWorld()) : 0;
    }

    /** 以世界名稱取得 RangeGap。*/
    public static int getRangeGap(String worldName) {
        if (worldName == null || worldName.isEmpty()) return 0;
        var res = Residence.getInstance();
        if (res == null) return 0;

        // 兼容拼寫：有些版本為 AntiGrief，有些配置寫成 AntiGreef
        List<String> entries = res.getConfig().getStringList("Global.AntiGreef.RangeGaps");
        if (entries == null || entries.isEmpty()) {
            entries = res.getConfig().getStringList("Global.AntiGrief.RangeGaps");
        }
        if (entries == null || entries.isEmpty()) return 0;

        String wn = worldName.toLowerCase();
        Integer fallback = null; // all/default
        for (String raw : entries) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isEmpty()) continue;

            // 格式：<world>-<number> 或 all-<number>；容忍多個破折號，取最後一個作為分隔
            int idx = s.lastIndexOf('-');
            String key;
            String numStr;
            if (idx > 0) {
                key = s.substring(0, idx).trim();
                numStr = s.substring(idx + 1).trim();
            } else {
                key = "all"; // 單獨數字，視為全域
                numStr = s;
            }

            Integer val = tryParseInt(numStr);
            if (val == null || val < 0) continue; // 忽略無效值

            String k = key.toLowerCase();
            if (k.equals("all") || k.equals("default")) {
                fallback = val;
            } else if (k.equals(wn)) {
                return val;
            }
        }
        return fallback != null ? fallback : 0;
    }

    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
    //endregion
}
