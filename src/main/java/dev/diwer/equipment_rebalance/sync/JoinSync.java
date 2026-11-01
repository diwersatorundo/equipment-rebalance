package dev.diwer.equipment_rebalance.sync;

import dev.diwer.equipment_rebalance.config.ERConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends the authoritative config snapshot whenever a player joins (fabric
 * networking events), and clears per-player repair state when they leave.
 */
public final class JoinSync {
	private JoinSync() {}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerGamePacketListenerImpl h = handler;
			ServerPlayer player = h.player;
			if (player == null) return;
			ServerSync.sendProfiles(player);
			if (ServerPlayNetworking.canSend(player, ERPayloads.REPAIR_TOGGLE)) {
				ServerPlayNetworking.send(player, new ERPayloads.RepairTogglePayload(
						dev.diwer.equipment_rebalance.repair.RepairState.isRepairMode(player.getUUID())));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerGamePacketListenerImpl h = handler;
			if (h.player != null) dev.diwer.equipment_rebalance.repair.RepairState.clearPlayer(h.player.getUUID());
		});
	}
}
