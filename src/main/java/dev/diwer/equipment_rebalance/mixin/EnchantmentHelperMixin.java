package dev.diwer.equipment_rebalance.mixin;

import dev.diwer.equipment_rebalance.config.ERConfig;
import dev.diwer.equipment_rebalance.config.ERConfigData;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps Mending out of every candidate list unless the configured legacy
 * behavior keeps it functional (KEEP_WORKING).
 */
@Mixin(net.minecraft.world.item.enchantment.EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

	@Inject(method = "getAvailableEnchantmentResults", at = @At("RETURN"), cancellable = true)
	private static void equipment_rebalance$filterMending(CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
		ERConfigData data = ERConfig.get().current();
		if (data.mendingLegacyBehavior == ERConfigData.MendingBehavior.KEEP_WORKING) return;
		List<EnchantmentInstance> ret = cir.getReturnValue();
		if (ret == null || ret.isEmpty()) return;
		List<EnchantmentInstance> filtered = new ArrayList<>();
		for (EnchantmentInstance inst : ret) {
			Holder<Enchantment> h = inst.enchantment();
			boolean mending = h.unwrapKey()
					.map(k -> "minecraft:mending".equals(k.identifier().getNamespace() + ":" + k.identifier().getPath()))
					.orElse(false);
			if (!mending) filtered.add(inst);
		}
		cir.setReturnValue(filtered);
	}
}
