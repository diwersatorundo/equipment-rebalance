package dev.diwer.equipment_rebalance.core;

import dev.diwer.equipment_rebalance.config.ERConfig;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Transfers the table "uses" budget into the dropped enchanting-table item
 * (custom data) and back again on placement (handled by the block entity
 * implicit-component mixin).
 */
public final class TableUsesTransfer {
	/** NBT key holding the remaining uses inside the item's custom data. */
	public static final String KEY = "equipment_rebalance_table_uses";

	private TableUsesTransfer() {}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			Identifier id = table.unwrapKey().map(k -> k.identifier()).orElse(null);
			if (id == null || !"minecraft".equals(id.getNamespace()) || !"blocks/enchanting_table".equals(id.getPath())) return;
			if (drops == null || drops.isEmpty()) return;
			BlockEntity be = context.getOptional(LootContextParams.BLOCK_ENTITY);
			if (be == null) return;
			int uses = TableUses.remaining(be, ERConfig.get().current());
			CompoundTag tag = new CompoundTag();
			tag.putInt(KEY, uses);
			for (ItemStack stack : drops) {
				if (stack.getItem() == Items.ENCHANTING_TABLE) CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
			}
		});
	}
}
