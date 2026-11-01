package dev.diwer.equipment_rebalance.core;

import dev.diwer.equipment_rebalance.config.ERConfigData;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Applies the "legacy item" policies to a tracked stack when it is presented to
 * the enchanting table: remove Mending (REMOVE_ON_VALIDATE) and/or clamp
 * over-capacity / over-slot / over-max-level enchantments (CLAMP).
 */
public final class LegacyItems {
	private LegacyItems() {}

	public static boolean isMending(Holder<Enchantment> h) {
		return h.unwrapKey()
				.map(k -> "minecraft:mending".equals(k.identifier().getNamespace() + ":" + k.identifier().getPath()))
				.orElse(false);
	}

	/** @return true when the stack was modified. */
	public static boolean validate(ItemStack stack, ERConfigData cfg) {
		if (stack == null || stack.isEmpty()) return false;
		ItemEnchantments current = stack.get(DataComponents.ENCHANTMENTS);
		if (current == null || current.isEmpty()) return false;

		boolean changed = false;

		if (cfg.mendingLegacyBehavior == ERConfigData.MendingBehavior.REMOVE_ON_VALIDATE) {
			boolean hasMending = false;
			for (Holder<Enchantment> h : current.keySet()) {
				if (isMending(h)) {
					hasMending = true;
					break;
				}
			}
			if (hasMending) {
				ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(current);
				mut.removeIf(LegacyItems::isMending);
				stack.set(DataComponents.ENCHANTMENTS, mut.toImmutable());
				changed = true;
			}
		}

		ERConfigData.MaterialProfile p = EnchEconomy.profileFor(stack);
		if (p != null && cfg.overCapacityBehavior == ERConfigData.OverCapacityBehavior.CLAMP) {
			changed |= clamp(stack, p);
		}
		return changed;
	}

	/** Enforces slots / max level / capacity on a stack. @return true when modified. */
	public static boolean clamp(ItemStack stack, ERConfigData.MaterialProfile p) {
		boolean changed = false;

		// 1. per-enchantment level cap
		ItemEnchantments cur = stack.get(DataComponents.ENCHANTMENTS);
		if (cur != null && !cur.isEmpty()) {
			ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(cur);
			boolean levelChanged = false;
			for (Holder<Enchantment> h : cur.keySet()) {
				int lvl = cur.getLevel(h);
				if (lvl > p.maxLevel()) {
					mut.set(h, p.maxLevel());
					levelChanged = true;
				}
			}
			if (levelChanged) {
				stack.set(DataComponents.ENCHANTMENTS, mut.toImmutable());
				changed = true;
			}
		}

		// 2. slot cap: drop the lowest-level non-free enchantments first
		int guard = 0;
		while (EnchEconomy.usedSlots(stack) > p.slots() && guard++ < 64) {
			ItemEnchantments c = stack.get(DataComponents.ENCHANTMENTS);
			if (c == null || c.isEmpty()) break;
			Holder<Enchantment> victim = null;
			int lowest = Integer.MAX_VALUE;
			for (Holder<Enchantment> h : c.keySet()) {
				if (EnchEconomy.isFreeEnchantment(h)) continue;
				int lvl = c.getLevel(h);
				if (lvl < lowest) {
					lowest = lvl;
					victim = h;
				}
			}
			if (victim == null) break;
			ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(c);
			Holder<Enchantment> toRemove = victim;
			mut.removeIf(h -> h == toRemove);
			stack.set(DataComponents.ENCHANTMENTS, mut.toImmutable());
			changed = true;
		}

		// 3. capacity cap: level down the highest-level enchantments
		guard = 0;
		while (EnchEconomy.usedCapacity(stack) > p.capacity() && guard++ < 256) {
			ItemEnchantments c = stack.get(DataComponents.ENCHANTMENTS);
			if (c == null || c.isEmpty()) break;
			Holder<Enchantment> victim = null;
			int highest = 0;
			for (Holder<Enchantment> h : c.keySet()) {
				if (EnchEconomy.isFreeEnchantment(h)) continue;
				int lvl = c.getLevel(h);
				if (lvl > highest) {
					highest = lvl;
					victim = h;
				}
			}
			if (victim == null) break;
			ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(c);
			if (highest <= 1) {
				Holder<Enchantment> toRemove = victim;
				mut.removeIf(h -> h == toRemove);
			} else {
				mut.set(victim, highest - 1);
			}
			stack.set(DataComponents.ENCHANTMENTS, mut.toImmutable());
			changed = true;
		}
		return changed;
	}
}
