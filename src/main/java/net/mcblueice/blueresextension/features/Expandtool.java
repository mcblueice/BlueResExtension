package net.mcblueice.blueresextension.features;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;


import net.mcblueice.blueresextension.BlueResExtension;
import org.bukkit.configuration.ConfigurationSection;
import net.mcblueice.blueresextension.utils.ConfigManager;
import net.mcblueice.blueresextension.utils.TaskScheduler;
import net.mcblueice.blueresextension.utils.ResidenceUtil;
import net.mcblueice.blueresextension.utils.PreviewUtil;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;

/**
 * 新版 Expandtool：
 * 1) 偵測玩家是否拿著 Residence 選取工具並對空氣左右鍵。
 * 2) 點擊空氣時檢查是否位於有效的領地或 Residence 選取區內。
 * 3) 依玩家面向方向（上/下/東/西/南/北）調整各方向格數（右+1、蹲右+10、左-1、蹲左-10）。
 * 其餘步驟（指令套用、清空機制、雙框預覽）會在後續步驟補完。
 */
public class Expandtool implements Listener {

	//region Types
	public enum Direction { UP, DOWN, EAST, WEST, SOUTH, NORTH }

	public static class Pending {
		public final UUID player;
	    public Location[] base; // {min,max}
		public final EnumMap<Direction, Integer> deltas = new EnumMap<>(Direction.class);
	    public Location[] preview; // 套用 deltas 後的新框
		public ClaimedResidence lockedResidence; // 一旦確定，鎖定此領地
	    public Pending(UUID player, Location[] base) {
			this.player = player;
			this.base = base;
			for (Direction d : Direction.values()) deltas.put(d, 0);
			this.preview = base;
		}
	}
	//endregion

	//region State & Config
	private final BlueResExtension plugin;
	private final ConfigManager lang;
	private final Map<UUID, Pending> pendings = new ConcurrentHashMap<>();

	// 預覽設定（可由 config 調整）
	private Particle baseFrameParticle = Particle.HAPPY_VILLAGER;
	private Particle baseSidesParticle = Particle.FLAME;
	private Particle previewFrameParticle = Particle.SOUL_FIRE_FLAME;
	private Particle previewSidesParticle = Particle.FLAME;
	private boolean baseDrawFrame = true;
	private boolean baseDrawSides = true;
	private boolean previewDrawFrame = true;
	private boolean previewDrawSides = true;
	private long baseInterval = 20L;
	private long previewInterval = 20L;
	private int baseFrameStep = 1;
	private int baseSidesStep = 1;
	private int previewFrameStep = 1;
	private int previewSidesStep = 1;
	//endregion

	public Expandtool(BlueResExtension plugin) {
		this.plugin = plugin;
		this.lang = plugin.getLanguageManager();
	    loadPreviewConfig();
	}

	//region ClickEvent
	@EventHandler
	public void onAirClick(PlayerInteractEvent event) {
		Action action = event.getAction();
		if (action != Action.LEFT_CLICK_AIR && action != Action.RIGHT_CLICK_AIR) return;
		if (event.getHand() != EquipmentSlot.HAND) return;

		Player player = event.getPlayer();
		if (!player.hasPermission("blueresextension.expandtool")) return;

		ItemStack item = player.getInventory().getItemInMainHand();
		if (!ResidenceUtil.isExpandTool(item)) return; // 步驟1：檢測拿著選取工具

		// 步驟2：檢測是否在有效領地或選取區（若已鎖定領地則優先）
		Pending pending = pendings.computeIfAbsent(player.getUniqueId(), id -> new Pending(id, null));
		Location[] base;
		// 僅允許以領地作為基準（取消選取區擴展）
		if (pending.lockedResidence != null) {
			base = ResidenceUtil.getResidenceMainAreaBounds(pending.lockedResidence);
		} else {
			ClaimedResidence here = ResidenceUtil.getResidenceAt(player.getLocation());
			if (here == null) {
				player.sendMessage(lang.get("residence.preview.notinres"));
				return;
			}
			pending.lockedResidence = here;
			base = ResidenceUtil.getResidenceMainAreaBounds(here);
		}
		if (base == null) {
			player.sendMessage(lang.get("residence.preview.notinres"));
			return;
		}

		// 初始化/更新 pending 狀態
		if (pending.base == null) pending.base = base;
		// 如玩家在其他領地/選區，重置基礎框
	    if (pending.base == null || pending.base[0].getWorld() != base[0].getWorld()) {
			pending.base = base;
			for (Direction d : Direction.values()) pending.deltas.put(d, 0);
		} else {
			// 若盒子位置差異很大也重置（簡單比較）
			Location pbMin = pending.base[0], pbMax = pending.base[1];
			Location bMin = base[0], bMax = base[1];
			if (pbMin.getBlockX() != bMin.getBlockX() || pbMin.getBlockY() != bMin.getBlockY() || pbMin.getBlockZ() != bMin.getBlockZ()
				|| pbMax.getBlockX() != bMax.getBlockX() || pbMax.getBlockY() != bMax.getBlockY() || pbMax.getBlockZ() != bMax.getBlockZ()) {
				pending.base = base;
				for (Direction d : Direction.values()) pending.deltas.put(d, 0);
			}
		}

		// 步驟3：依方向與點擊調整格數（方向改由 ResidenceUtil 判定；步長內聯）
		Direction dir = ResidenceUtil.playerFacing(player);
		int step = player.isSneaking() ? 10 : 1;
		int delta = (action == Action.RIGHT_CLICK_AIR) ? step : -step;
		pending.deltas.put(dir, pending.deltas.get(dir) + delta);

		// 若尚未鎖定領地，於第一次變更時鎖定玩家腳下領地
		if (pending.lockedResidence == null) {
			ClaimedResidence res = ResidenceUtil.getResidenceAt(player.getLocation());
			if (res != null) pending.lockedResidence = res;
		}

		// 計算新的預覽框（內聯 applyDeltas）
		Location min = pending.base[0];
		Location max = pending.base[1];
		int minX = min.getBlockX();
		int minY = min.getBlockY();
		int minZ = min.getBlockZ();
		int maxX = max.getBlockX();
		int maxY = max.getBlockY();
		int maxZ = max.getBlockZ();

		int east = pending.deltas.get(Direction.EAST);
		int west = pending.deltas.get(Direction.WEST);
		int south = pending.deltas.get(Direction.SOUTH);
		int north = pending.deltas.get(Direction.NORTH);
		int upD = pending.deltas.get(Direction.UP);
		int downD = pending.deltas.get(Direction.DOWN);

		maxX += east;
		minX -= west;
		maxZ += south;
		minZ -= north;
		maxY += upD;
		minY -= downD;


		Location nMin = new Location(min.getWorld(), minX, minY, minZ);
		Location nMax = new Location(min.getWorld(), maxX, maxY, maxZ);
		Location[] proposed = ResidenceUtil.normalize(nMin, nMax);

		// 依限制調整預覽（高度/邊界/與他領地間距等）
		Location[] adjusted = ResidenceUtil.adjustPreviewToConstraints(player, pending.lockedResidence, pending.base, proposed);
		boolean changed = adjusted != null && (adjusted[0].getBlockX() != proposed[0].getBlockX()
			|| adjusted[0].getBlockY() != proposed[0].getBlockY()
			|| adjusted[0].getBlockZ() != proposed[0].getBlockZ()
			|| adjusted[1].getBlockX() != proposed[1].getBlockX()
			|| adjusted[1].getBlockY() != proposed[1].getBlockY()
			|| adjusted[1].getBlockZ() != proposed[1].getBlockZ());

		Location[] newPreview = adjusted != null ? adjusted : proposed;
		boolean previewChangedFromLast = !boxEquals(pending.preview, newPreview);
		pending.preview = newPreview;

		// 若被限制夾住，將使用者的方向累計值回寫為「實際有效」的值，避免數值無限成長
		if (changed) {
			setDeltasFromBox(pending, pending.base, pending.preview);
			if (previewChangedFromLast) {
				player.sendMessage(lang.get("residence.preview.adjusted", ""));
			}
		}

		// 交給 utils 繪製舊/新框
		PreviewUtil.startOrUpdatePreview(player,
			pending.base[0],
			pending.base[1],
			baseDrawFrame ? baseFrameParticle : null,
			baseDrawSides ? baseSidesParticle : null,
			baseFrameStep,
			baseSidesStep,
			baseInterval,
			"expandtool-base");

		PreviewUtil.startOrUpdatePreview(player,
			pending.preview[0],
			pending.preview[1],
			previewDrawFrame ? previewFrameParticle : null,
			previewDrawSides ? previewSidesParticle : null,
			previewFrameStep,
			previewSidesStep,
			previewInterval,
			"expandtool-preview");

		// 彙總輸出所有非 0 的擴展方向-格數
		List<String> parts = new ArrayList<>();
		for (Direction d : Direction.values()) {
			int v = pending.deltas.get(d);
			if (v != 0) {
				parts.add("§a" + displayName(d) + "§r=§e" + v + "§r");
			}
		}
		String summary = parts.isEmpty() ? "§7無調整" : String.join(" §7, §r", parts);
		player.sendMessage("§7§l[§2§l領地§7§l]§r 擴展調整: " + summary);
	}
	//endregion

	//region CancelEvent
	@EventHandler
	public void onSlotChange(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();
		ItemStack handItem = player.getInventory().getItem(event.getNewSlot());
        if (!ResidenceUtil.isExpandTool(handItem)) clearPending(player.getUniqueId());
	}
	@EventHandler
	public void onSwapHand(PlayerSwapHandItemsEvent event) {
		Player player = event.getPlayer();
		TaskScheduler.runTask(player, plugin, () -> {
			ItemStack handItem = player.getInventory().getItemInMainHand();
			if (!ResidenceUtil.isExpandTool(handItem)) clearPending(player.getUniqueId());
		});
	}
	@EventHandler
	public void onDrop(PlayerDropItemEvent event) {
		Player player = event.getPlayer();
		TaskScheduler.runTask(player, plugin, () -> {
			ItemStack handItem = player.getInventory().getItemInMainHand();
			if (!ResidenceUtil.isExpandTool(handItem)) clearPending(player.getUniqueId());
		});
	}
	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player)) return;
		Player player = (Player) event.getWhoClicked();
		// 等待一次 tick 讓物品變更先套用，再檢查主手是否仍為選取工具
		TaskScheduler.runTask(player, plugin, () -> {
			ItemStack handItem = player.getInventory().getItemInMainHand();
			if (!ResidenceUtil.isExpandTool(handItem)) clearPending(player.getUniqueId());
		});
	}
	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		clearPending(event.getPlayer().getUniqueId());
	}
	//endregion

	//region Helpers
	private void clearPending(UUID uuid) {
        pendings.remove(uuid);
        PreviewUtil.clearAllPreviews(uuid);
	}

	private String displayName(Direction dir) {
		// 以語言檔對應鍵翻譯方向名稱，例如 residence.direction.up/down/...
		switch (dir) {
			case UP: return lang.get("residence.direction.up");
			case DOWN: return lang.get("residence.direction.down");
			case EAST: return lang.get("residence.direction.east");
			case WEST: return lang.get("residence.direction.west");
			case SOUTH: return lang.get("residence.direction.south");
			case NORTH: return lang.get("residence.direction.north");
			default: return dir.name();
		}
	}

	// 繪製邏輯改由 PreviewUtil 管理，方框由 ResidenceUtil.Cuboid 表示

	//region Config & API
	private void loadPreviewConfig() {
		ConfigurationSection previewSec = plugin.getConfig().getConfigurationSection("expandtool.preview");

		// New nested structure: expandtool.preview.base / expandtool.preview.preview
		ConfigurationSection baseSec = previewSec != null ? previewSec.getConfigurationSection("base") : null;
		ConfigurationSection prevSec = previewSec != null ? previewSec.getConfigurationSection("preview") : null;

		// Backward compatibility: flat keys under expandtool.preview or expandtool
		String flatPrefix = (previewSec != null) ? "expandtool.preview." : "expandtool.";

		// Base config
		this.baseInterval = Math.max(1L,
			baseSec != null ? baseSec.getLong("updateInterval", 20L) : plugin.getConfig().getLong(flatPrefix + "updateInterval", 20L));
		this.baseDrawFrame = baseSec != null ? baseSec.getBoolean("drawFrame", true) : plugin.getConfig().getBoolean(flatPrefix + "drawFrame", true);
		this.baseDrawSides = baseSec != null ? baseSec.getBoolean("drawSides", true) : plugin.getConfig().getBoolean(flatPrefix + "drawSides", true);
		this.baseFrameStep = Math.max(1,
			baseSec != null ? baseSec.getInt("FrameSpacing", 1) : plugin.getConfig().getInt(flatPrefix + "FrameSpacing", 1));
		this.baseSidesStep = Math.max(1,
			baseSec != null ? baseSec.getInt("SidesSpacing", 1) : plugin.getConfig().getInt(flatPrefix + "SidesSpacing", 1));
		this.baseFrameParticle = parseParticle(
			baseSec != null ? baseSec.getString("Frame", plugin.getConfig().getString(flatPrefix + "baseFrame", "happyVillager"))
							: plugin.getConfig().getString(flatPrefix + "baseFrame", "happyVillager"),
			Particle.HAPPY_VILLAGER);
		this.baseSidesParticle = parseParticle(
			baseSec != null ? baseSec.getString("Sides", plugin.getConfig().getString(flatPrefix + "baseSides", "reddust"))
							: plugin.getConfig().getString(flatPrefix + "baseSides", "reddust"),
			Particle.FLAME);

		// Preview config
		this.previewInterval = Math.max(1L,
			prevSec != null ? prevSec.getLong("updateInterval", 20L) : plugin.getConfig().getLong(flatPrefix + "updateInterval", 20L));
		this.previewDrawFrame = prevSec != null ? prevSec.getBoolean("drawFrame", true) : plugin.getConfig().getBoolean(flatPrefix + "drawFrame", true);
		this.previewDrawSides = prevSec != null ? prevSec.getBoolean("drawSides", true) : plugin.getConfig().getBoolean(flatPrefix + "drawSides", true);
		this.previewFrameStep = Math.max(1,
			prevSec != null ? prevSec.getInt("FrameSpacing", 1) : plugin.getConfig().getInt(flatPrefix + "FrameSpacing", 1));
		this.previewSidesStep = Math.max(1,
			prevSec != null ? prevSec.getInt("SidesSpacing", 1) : plugin.getConfig().getInt(flatPrefix + "SidesSpacing", 1));
		this.previewFrameParticle = parseParticle(
			prevSec != null ? prevSec.getString("Frame", plugin.getConfig().getString(flatPrefix + "previewFrame", "SOUL_FIRE_FLAME"))
							: plugin.getConfig().getString(flatPrefix + "previewFrame", "SOUL_FIRE_FLAME"),
			Particle.SOUL_FIRE_FLAME);
		this.previewSidesParticle = parseParticle(
			prevSec != null ? prevSec.getString("Sides", plugin.getConfig().getString(flatPrefix + "previewSides", "FLAME"))
							: plugin.getConfig().getString(flatPrefix + "previewSides", "FLAME"),
			Particle.FLAME);
	}

	private Particle parseParticle(String name, Particle def) {
		if (name == null || name.isEmpty()) return def;
		if (name.equalsIgnoreCase("OFF") || name.equalsIgnoreCase("NONE")) return null; // 明確關閉
		String key = name.trim();
		if (key.equalsIgnoreCase("reddust")) {
			try { return Particle.valueOf("REDSTONE"); } catch (IllegalArgumentException ignore) {}
			try { return Particle.valueOf("DUST"); } catch (IllegalArgumentException ignore) {}
		}
		if (key.equalsIgnoreCase("happyVillager")) return Particle.HAPPY_VILLAGER;
		try { return Particle.valueOf(key.toUpperCase()); } catch (IllegalArgumentException ex) { return def; }
	}

	public static Expandtool instance;

	public static Expandtool getInstance() { return instance; }

	public Pending getPending(UUID uuid) { return pendings.get(uuid); }

	public EnumMap<Direction, Integer> getDeltas(UUID uuid) {
		Pending p = pendings.get(uuid);
		return p != null ? new EnumMap<>(p.deltas) : null;
	}

	public Location[] getBase(UUID uuid) { Pending p = pendings.get(uuid); return p != null ? p.base : null; }
	public Location[] getPreview(UUID uuid) { Pending p = pendings.get(uuid); return p != null ? p.preview : null; }
	public void resetDeltas(UUID uuid) { Pending p = pendings.get(uuid); if (p != null) { for (Direction d : Direction.values()) p.deltas.put(d, 0); p.preview = p.base; } }
	public void clear(UUID uuid) { clearPending(uuid); }

	// 指令入口：僅顯示預計調整資訊到聊天室
	public static void command(Player player) {
		Expandtool inst = getInstance();
		if (inst == null) {
			player.sendMessage("§7§l[§a§l系統§7§l]§r§c擴展工具尚未註冊");
			return;
		}
		Pending pending = inst.getPending(player.getUniqueId());
		if (pending == null) {
			player.sendMessage("§7§l[§2§l領地§7§l]§r §7目前沒有任何擴展調整");
			return;
		}

		// 彙總方向
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Direction d : Direction.values()) {
			int v = pending.deltas.get(d);
			if (v != 0) {
				if (!first) sb.append(" §7, §r");
				sb.append("§a").append(inst.displayName(d)).append("§r=§e").append(v).append("§r");
				first = false;
			}
		}
		String deltas = first ? "§7無調整" : sb.toString();

		String baseBox = inst.boxStr(pending.base);
		String prevBox = inst.boxStr(pending.preview);

		player.sendMessage("§7§l[§2§l領地§7§l]§r 擴展方向: " + deltas);
		player.sendMessage("§7§l[§2§l領地§7§l]§r 原始範圍: " + baseBox);
		player.sendMessage("§7§l[§2§l領地§7§l]§r 預計範圍: " + prevBox);
	}

	private String boxStr(Location[] box) {
		if (box == null || box[0] == null || box[1] == null) return "§7(無)";
		Location a = box[0];
		Location b = box[1];
		return String.format("§b%s§r §f(%d,%d,%d) §7->§r §f(%d,%d,%d)",
			(a.getWorld() != null ? a.getWorld().getName() : "world"),
			a.getBlockX(), a.getBlockY(), a.getBlockZ(),
			b.getBlockX(), b.getBlockY(), b.getBlockZ()
		);
	}

	// 將目前預覽框換算為相對於 base 的各向增減量，寫回 pending.deltas
	private void setDeltasFromBox(Pending pending, Location[] base, Location[] box) {
		if (pending == null || base == null || box == null
			|| base[0] == null || base[1] == null || box[0] == null || box[1] == null) return;
		int up = box[1].getBlockY() - base[1].getBlockY();
		int down = base[0].getBlockY() - box[0].getBlockY();
		int east = box[1].getBlockX() - base[1].getBlockX();
		int west = base[0].getBlockX() - box[0].getBlockX();
		int south = box[1].getBlockZ() - base[1].getBlockZ();
		int north = base[0].getBlockZ() - box[0].getBlockZ();
		pending.deltas.put(Direction.UP, up);
		pending.deltas.put(Direction.DOWN, down);
		pending.deltas.put(Direction.EAST, east);
		pending.deltas.put(Direction.WEST, west);
		pending.deltas.put(Direction.SOUTH, south);
		pending.deltas.put(Direction.NORTH, north);
	}

	// 比較兩個方框是否相同（以整數方塊座標判斷）
	private boolean boxEquals(Location[] a, Location[] b) {
		if (a == null || b == null) return false;
		if (a[0] == null || a[1] == null || b[0] == null || b[1] == null) return false;
		return a[0].getWorld() == b[0].getWorld()
			&& a[0].getBlockX() == b[0].getBlockX()
			&& a[0].getBlockY() == b[0].getBlockY()
			&& a[0].getBlockZ() == b[0].getBlockZ()
			&& a[1].getBlockX() == b[1].getBlockX()
			&& a[1].getBlockY() == b[1].getBlockY()
			&& a[1].getBlockZ() == b[1].getBlockZ();
	}
	//endregion

	//endregion

	// 註冊器（尚未在主插件啟用，以免與舊版衝突）
	public static void register(BlueResExtension plugin) {
		Expandtool inst = new Expandtool(plugin);
		instance = inst;
		Bukkit.getPluginManager().registerEvents(inst, plugin);
	}
}
