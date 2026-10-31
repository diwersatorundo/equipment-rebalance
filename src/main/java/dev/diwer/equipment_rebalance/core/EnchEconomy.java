package dev.diwer.equipment_rebalance.core;

import dev.diwer.equipment_rebalance.config.ERConfig;
import dev.diwer.equipment_rebalance.config.ERConfigData;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Pure economy helpers: profiles, enchantment costs, slot/capacity accounting.
 * All data comes from the config snapshot; nothing here touches registries.
 */
public final class EnchEconomy {
	private EnchEconomy() {}

	public static ERConfigData cfg() {
		return ERConfig.get().current();
	}

	// ------------------------------------------------------------------
	// equipment identity → profile
	// ------------------------------------------------------------------

	/** Profile for an item; null means the item is handled by vanilla behavior. */
	public static ERConfigData.MaterialProfile profileFor(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		EquipmentId eid = EquipmentId.of(stack);
		if (eid == null) return null;
		ERConfigData cfg = cfg();
		return switch (eid.family) {
			case SPECIAL -> eid.material == null ? null : cfg.specialProfiles.get(eid.material);
			case ARMOR -> eid.material == null ? null : cfg.armorProfiles.get(eid.material);
			case TOOL -> eid.material == null ? null : cfg.toolProfiles.get(eid.material);
			case OTHER -> null;
		};
	}

	// ------------------------------------------------------------------
	// slots / capacity limits
	// ------------------------------------------------------------------

	public static int slotsOf(ItemStack stack) {
		ERConfigData.MaterialProfile p = profileFor(stack);
		return p == null ? 3 : p.slots();
	}

	public static int capacityOf(ItemStack stack) {
		ERConfigData.MaterialProfile p = profileFor(stack);
		return p == null ? Integer.MAX_VALUE : p.capacity();
	}

	public static int maxLevelFor(ItemStack stack) {
		ERConfigData.MaterialProfile p = profileFor(stack);
		return p == null ? 5 : p.maxLevel();
	}

	// ------------------------------------------------------------------
	// curse handling
	// ------------------------------------------------------------------

	public static boolean isCurse(Holder<Enchantment> ench) {
		return ench.unwrapKey().map(k -> k.identifier().getPath().endsWith("_curse")).orElse(false);
	}

	/** Curses (per config) occupy neither slots nor capacity. */
	public static boolean isFreeEnchantment(Holder<Enchantment> ench) {
		ERConfigData cfg = cfg();
		return cfg.cursesFree && isCurse(ench);
	}

	// ------------------------------------------------------------------
	// accounting
	// ------------------------------------------------------------------

	public static int usedSlots(ItemStack stack) {
		ItemEnchantments in = stack.get(DataComponents.ENCHANTMENTS);
		if (in == null) return 0;
		int n = 0;
		for (Holder<Enchantment> h : in.keySet()) {
			if (isFreeEnchantment(h)) continue;
			n++;
		}
		return n;
	}

	/** Total capacity currently used (sum of per-level costs over levels 1..cur). */
	public static int usedCapacity(ItemStack stack) {
		ItemEnchantments in = stack.get(DataComponents.ENCHANTMENTS);
		if (in == null) return 0;
		int total = 0;
		for (var entry : in.entrySet()) {
			Holder<Enchantment> h = entry.getKey();
			int level = entry.getIntValue();
			if (isFreeEnchantment(h)) continue;
			total += capacityForUpgrade(h, 0, level);
		}
		return total;
	}

	// ------------------------------------------------------------------
	// capacity / level costs
	// ------------------------------------------------------------------

	public static int capacityForLevel(Holder<Enchantment> ench, int level) {
		if (level <= 0) return 0;
		int[] base = cfg().levelBaseCost;
		int idx = Math.min(10, Math.max(0, level));
		return Math.max(0, (int) Math.ceil(base[idx] * multiplierFor(ench)));
	}

	public static int capacityForUpgrade(Holder<Enchantment> ench, int curLevel, int targetLevel) {
		int total = 0;
		for (int lvl = curLevel + 1; lvl <= targetLevel; lvl++) total += capacityForLevel(ench, lvl);
		return total;
	}

	public static double multiplierFor(Holder<Enchantment> ench) {
		ERConfigData cfg = cfg();
		String key = enchantKey(ench);
		return key == null ? cfg.customMultiplierDefault
				: cfg.enchantMultipliers.getOrDefault(key, cfg.customMultiplierDefault);
	}

	/** "minecraft:protection" — or null when the enchantment is unregistered. */
	public static String enchantKey(Holder<Enchantment> ench) {
		ResourceKey<Enchantment> k = ench.unwrapKey().orElse(null);
		if (k == null) return null;
		net.minecraft.resources.Identifier id = k.identifier();
		return id.getNamespace() + ":" + id.getPath();
	}

	// ------------------------------------------------------------------
	// candidate application used by the enchanting table flow
	// ------------------------------------------------------------------

	/**
	 * Shrinks the raw vanilla candidate to fit the item's profile (material level cap,
	 * free slots and capacity). Returns the effective level to apply, or -1 when the
	 * candidate can not be installed at all.
	 */
	public static int limitCandidate(ItemStack stack, ERConfigData.MaterialProfile profile, EnchantmentInstance inst) {
		Holder<Enchantment> ench = inst.enchantment();
		int rollLevel = inst.level();
		int existing = existingLevel(stack, ench);
		int cap = profile.maxLevel();

		// free curses still respect the material level cap
		if (isFreeEnchantment(ench)) {
			return existing >= cap ? -1 : Math.min(Math.max(rollLevel, existing + 1), Math.min(cap, 5));
		}

		int target;
		if (existing > 0) {
			if (existing >= cap) return -1;
			target = Math.min(Math.max(rollLevel, existing + 1), cap);
		} else {
			if (usedSlots(stack) >= profile.slots()) return -1;
			target = Math.min(rollLevel, cap);
		}
		int capacityFree = profile.capacity() - usedCapacity(stack);
		int need = capacityForUpgrade(ench, existing, target);
		while (need > capacityFree) {
			target--;
			if (target <= existing || target < 1) return -1;
			need = capacityForUpgrade(ench, existing, target);
		}
		return target;
	}

	public static int existingLevel(ItemStack stack, Holder<Enchantment> ench) {
		ItemEnchantments in = stack.get(DataComponents.ENCHANTMENTS);
		return in == null ? 0 : in.getLevel(ench);
	}
}
