package dev.diwer.equipment_rebalance.core;

import com.mojang.serialization.Codec;
import dev.diwer.equipment_rebalance.config.ERConfigData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The lifetime "uses" budget of an enchanting table. The budget is rolled
 * lazily when a freshly crafted table is first used (between tableUsesMin and
 * tableUsesMax) and persists on the block entity (and, after breaking, inside
 * the block item's custom data) until it is fully spent.
 */
public final class TableUses {
	public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("equipment_rebalance", "table_uses");

	public static final AttachmentType<Integer> TYPE =
			AttachmentRegistry.createPersistent(TYPE_ID, Codec.INT);

	private TableUses() {}

	public static int remaining(BlockEntity be, ERConfigData cfg) {
		if (be == null) return 0;
		Integer v = be.getAttached(TYPE);
		if (v == null) {
			v = rollNewBudget(cfg);
			be.setAttached(TYPE, v);
		}
		return Math.max(0, v);
	}

	public static void consume(BlockEntity be, ERConfigData cfg) {
		if (be == null) return;
		be.setAttached(TYPE, Math.max(0, remaining(be, cfg) - 1));
	}

	public static int rollNewBudget(ERConfigData cfg) {
		int min = Math.max(1, cfg.tableUsesMin);
		int max = Math.max(min, cfg.tableUsesMax);
		return min + RandomSource.create().nextInt(max - min + 1);
	}
}
