package dev.diwer.equipment_rebalance.core;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;

/**
 * Tiny indirection so the holder mixin does not reference the normalizer
 * field ordering directly; keeps mixin classloading cheap.
 */
public final class EquipmentNormalizerHook {
	private EquipmentNormalizerHook() {}

	public static DataComponentMap patch(Holder.Reference<?> holder, DataComponentMap original) {
		return EquipmentNormalizer.patch(holder, original);
	}
}
