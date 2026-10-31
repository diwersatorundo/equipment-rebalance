package dev.diwer.equipment_rebalance.client;

import dev.diwer.equipment_rebalance.config.ERConfig;
import dev.diwer.equipment_rebalance.sync.ERPayloads;
import dev.diwer.equipment_rebalance.repair.RepairState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client side: adopts the server snapshot and mirrors the repair mode state.
 */
public final class ClientSync {
	private ClientSync() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ERPayloads.PROFILES, (payload, context) -> {
			context.client().execute(() -> ERConfig.get().adoptServerSnapshot(
					dev.diwer.equipment_rebalance.config.ERConfig.tryParse(payload.settings()),
					dev.diwer.equipment_rebalance.config.ERConfig.tryParse(payload.profiles()),
					dev.diwer.equipment_rebalance.config.ERConfig.tryParse(payload.enchantments())));
		});
		ClientPlayNetworking.registerGlobalReceiver(ERPayloads.REPAIR_TOGGLE, (payload, context) -> {
			dev.diwer.equipment_rebalance.repair.RepairState.setRepairMode(context.player().getUUID(), payload.repairMode());
		});
	}
}
