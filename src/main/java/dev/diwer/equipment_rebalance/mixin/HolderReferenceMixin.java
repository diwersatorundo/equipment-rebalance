package dev.diwer.equipment_rebalance.mixin;

import dev.diwer.equipment_rebalance.core.EquipmentNormalizerHook;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Patches the bound component map of item holders so every equipment item
 * gets normalized stats before any ItemStack is created from it.
 */
@Mixin(Holder.Reference.class)
public abstract class HolderReferenceMixin<T> {

	@Inject(method = "components()Lnet/minecraft/core/component/DataComponentMap;",
			at = @At("RETURN"), cancellable = true)
	private void equipment_rebalance$normalizedComponents(CallbackInfoReturnable<DataComponentMap> cir) {
		DataComponentMap replaced = EquipmentNormalizerHook.patch((Holder.Reference<?>) (Object) this, cir.getReturnValue());
		if (replaced != null) cir.setReturnValue(replaced);
	}
}
