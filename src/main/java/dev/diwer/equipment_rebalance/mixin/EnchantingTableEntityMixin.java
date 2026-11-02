package dev.diwer.equipment_rebalance.mixin;

import dev.diwer.equipment_rebalance.core.TableUses;
import dev.diwer.equipment_rebalance.core.TableUsesTransfer;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries the table "uses" budget between the block entity and its item form
 * through the vanilla implicit-component mechanism (the same one vanilla uses
 * for custom names), so a broken table keeps its remaining charges.
 */
@Mixin(EnchantingTableBlockEntity.class)
public abstract class EnchantingTableEntityMixin {

	@Inject(method = "applyImplicitComponents", at = @At("TAIL"))
	private void equipment_rebalance$restoreUses(DataComponentGetter getter, CallbackInfo ci) {
		CustomData data = getter.get(DataComponents.CUSTOM_DATA);
		if (data == null) return;
		CompoundTag tag = data.copyTag();
		if (!tag.contains(TableUsesTransfer.KEY)) return;
		int uses = tag.getIntOr(TableUsesTransfer.KEY, 0);
		if (uses <= 0) return;
		((EnchantingTableBlockEntity) (Object) this).setAttached(TableUses.TYPE, uses);
	}

	@Inject(method = "collectImplicitComponents", at = @At("TAIL"))
	private void equipment_rebalance$collectUses(DataComponentMap.Builder builder, CallbackInfo ci) {
		EnchantingTableBlockEntity self = (EnchantingTableBlockEntity) (Object) this;
		Integer uses = self.getAttached(TableUses.TYPE);
		if (uses == null) return;
		CompoundTag tag = new CompoundTag();
		tag.putInt(TableUsesTransfer.KEY, uses);
		builder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}
}
