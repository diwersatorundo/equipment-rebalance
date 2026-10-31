package dev.diwer.equipment_rebalance.repair;

import dev.diwer.equipment_rebalance.config.ERConfigData;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player service-mode state ("repair tab" of the enchanting screen), plus
 * the XP cost formula for full repair.
 */
public final class RepairState {
	private static final Map<UUID, Boolean> REPAIR_MODE = new HashMap<>();

	private RepairState() {}

	public static boolean isRepairMode(UUID playerId) {
		Boolean b = REPAIR_MODE.get(playerId);
		return b != null && b;
	}

	public static void setRepairMode(UUID playerId, boolean on) {
		if (on) REPAIR_MODE.put(playerId, true);
		else REPAIR_MODE.remove(playerId);
	}

	public static void clearPlayer(UUID playerId) {
		REPAIR_MODE.remove(playerId);
	}

	/** Whole-life XP points cost to fully repair the item (scaled by missing durability). */
	public static int xpCost(ItemStack stack, ERConfigData cfg) {
		int max = stack.getMaxDamage();
		int dmg = stack.getDamageValue();
		if (max <= 0 || dmg <= 0) return 0;
		int full = Math.max(cfg.repairMinXpCost, cfg.repairFullXpCost);
		int cost = (int) Math.ceil(full * ((double) dmg / max));
		return Math.max(cfg.repairMinXpCost, cost);
	}
}
