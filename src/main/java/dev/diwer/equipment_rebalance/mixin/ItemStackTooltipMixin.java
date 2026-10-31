package dev.diwer.equipment_rebalance.mixin;

import dev.diwer.equipment_rebalance.config.ERConfig;
import dev.diwer.equipment_rebalance.config.ERConfigData;
import dev.diwer.equipment_rebalance.core.EnchEconomy;
import dev.diwer.equipment_rebalance.core.TooltipBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Appends the material economy meter (slots/power bars) to item tooltips.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {

	@Inject(method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
			at = @At("RETURN"), cancellable = true)
	private void equipment_rebalance$economyTooltip(Item.TooltipContext ctx, Player player, TooltipFlag flag,
			CallbackInfoReturnable<List<Component>> cir) {
		ERConfigData data = ERConfig.get().current();
		if (!data.tooltip.enabled() || !data.modEnabled) return;
		ItemStack self = (ItemStack) (Object) this;
		ERConfigData.MaterialProfile p = EnchEconomy.profileFor(self);
		if (p == null) return;
		List<Component> ret = cir.getReturnValue();
		if (ret == null) return;
		List<Component> copy = new ArrayList<>(ret);
		copy.addAll(TooltipBuilder.build(self, p, data));
		cir.setReturnValue(copy);
	}
}
