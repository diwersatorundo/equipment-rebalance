package dev.diwer.equipment_rebalance.mixin;

import dev.diwer.equipment_rebalance.config.ERConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Toggles vanilla anvil "material repair" (repair consumables being the same
 * item) for items the economy does not consider repairable.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

	@Redirect(method = "createResult",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;isValidRepairItem(Lnet/minecraft/world/item/ItemStack;)Z"))
	private boolean equipment_rebalance$materialRepairItem(ItemStack self, ItemStack material) {
		if (!ERConfig.get().current().anvilMaterialRepairEnabled) return false;
		return self.isValidRepairItem(material);
	}
}
