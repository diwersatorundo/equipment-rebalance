package dev.diwer.equipment_rebalance.mixin;

import dev.diwer.equipment_rebalance.config.ERConfig;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales every enchantment's max level by the global multiplier
 * (vanilla datapack ceiling stays at 2x, material caps gate the rest).
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
	@Inject(method = "getMaxLevel()I", at = @At("RETURN"), cancellable = true)
	private void equipment_rebalance$boostMaxLevel(CallbackInfoReturnable<Integer> cir) {
		if (!ERConfig.get().current().modEnabled) return;
		int vanilla = cir.getReturnValueI();
		if (vanilla <= 1) return;
		int boosted = (int) Math.round(vanilla * ERConfig.get().current().globalMaxLevelMultiplier);
		cir.setReturnValue(Math.max(1, Math.min(10, boosted)));
	}
}
