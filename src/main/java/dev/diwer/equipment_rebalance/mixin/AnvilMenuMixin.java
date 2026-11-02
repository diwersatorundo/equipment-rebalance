package dev.diwer.equipment_rebalance.mixin;

import dev.diwer.equipment_rebalance.config.ERConfig;
import dev.diwer.equipment_rebalance.config.ERConfigData;
import dev.diwer.equipment_rebalance.core.EnchEconomy;
import dev.diwer.equipment_rebalance.core.LegacyItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Objects;
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

	/**
	 * Combining can cap an enchantment at the material max level, but it must never
	 * downgrade existing levels. A result that exceeds slots/capacity is rejected.
	 * Pure renaming / material repair (enchantments unchanged) is left untouched.
	 */
	@Inject(method = "createResult", at = @At("TAIL"))
	private void equipment_rebalance$fitResult(CallbackInfo ci) {
		ERConfigData cfg = ERConfig.get().current();
		if (!cfg.modEnabled) return;
		ItemCombinerMenuAccessor acc = (ItemCombinerMenuAccessor) (Object) this;
		ItemStack out = acc.equipment_rebalance$resultSlots().getItem(0);
		if (out.isEmpty()) return;
		ERConfigData.MaterialProfile p = EnchEconomy.profileFor(out);
		if (p == null) return;

		ItemStack primary = acc.equipment_rebalance$inputSlots().getItem(0);
		ItemEnchantments inEnch = primary.isEmpty() ? null : primary.get(DataComponents.ENCHANTMENTS);
		ItemEnchantments outEnch = out.get(DataComponents.ENCHANTMENTS);
		if (Objects.equals(inEnch, outEnch)) return;

		if (!LegacyItems.fitAnvilResult(out, p)) {
			acc.equipment_rebalance$resultSlots().setItem(0, ItemStack.EMPTY);
			((AnvilMenuAccessor) (Object) this).equipment_rebalance$cost().set(0);
		}
	}

	@Redirect(method = "createResult",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;isValidRepairItem(Lnet/minecraft/world/item/ItemStack;)Z"))
	private boolean equipment_rebalance$materialRepairItem(ItemStack self, ItemStack material) {
		if (!ERConfig.get().current().anvilMaterialRepairEnabled) return false;
		return self.isValidRepairItem(material);
	}
}
