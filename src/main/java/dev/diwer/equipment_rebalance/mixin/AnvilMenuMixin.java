package dev.diwer.equipment_rebalance.mixin;

import dev.diwer.equipment_rebalance.config.ERConfig;
import dev.diwer.equipment_rebalance.config.ERConfigData;
import dev.diwer.equipment_rebalance.core.EnchEconomy;
import dev.diwer.equipment_rebalance.core.LegacyItems;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Toggles vanilla anvil "material repair" (repair consumables being the same
 * item) for items the economy does not consider repairable.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

	/** Combining two items can never exceed the result's material profile. */
	@Inject(method = "createResult", at = @At("TAIL"))
	private void equipment_rebalance$clampResult(CallbackInfo ci) {
		ERConfigData cfg = ERConfig.get().current();
		if (!cfg.modEnabled) return;
		ItemStack out = ((ItemCombinerMenuAccessor) (Object) this).equipment_rebalance$resultSlots().getItem(0);
		if (out.isEmpty()) return;
		ERConfigData.MaterialProfile p = EnchEconomy.profileFor(out);
		if (p == null) return;
		LegacyItems.clamp(out, p);
	}

	@Redirect(method = "createResult",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;isValidRepairItem(Lnet/minecraft/world/item/ItemStack;)Z"))
	private boolean equipment_rebalance$materialRepairItem(ItemStack self, ItemStack material) {
		if (!ERConfig.get().current().anvilMaterialRepairEnabled) return false;
		return self.isValidRepairItem(material);
	}
}
