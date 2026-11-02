package dev.diwer.equipment_rebalance.sync;

import dev.diwer.equipment_rebalance.EquipmentRebalance;
import dev.diwer.equipment_rebalance.config.ERConfig;
import dev.diwer.equipment_rebalance.repair.RepairState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server side payload registration and authoritativeness duties.
 */
public final class ServerSync {
	private static final Logger LOG = LoggerFactory.getLogger("equipment_rebalance.server");

	private ServerSync() {}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ERPayloads.PROFILES, ERPayloads.ProfilesPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ERPayloads.REPAIR_TOGGLE, ERPayloads.RepairTogglePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ERPayloads.REPAIR_TOGGLE, ERPayloads.RepairTogglePayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ERPayloads.REPAIR_TOGGLE, (payload, context) -> {
			ServerPlayer player = context.player();
			RepairState.setRepairMode(player.getUUID(), payload.repairMode());
			if (ServerPlayNetworking.canSend(player, ERPayloads.REPAIR_TOGGLE)) {
				ServerPlayNetworking.send(player, new ERPayloads.RepairTogglePayload(payload.repairMode()));
			}
		});
	}

	/** Pushes the current (authoritative) snapshot to one player. */
	public static void sendProfiles(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, ERPayloads.PROFILES)) return;
		ERConfig cfg = ERConfig.get();
		ServerPlayNetworking.send(player, new ERPayloads.ProfilesPayload(
				ERConfig.pretty(cfg.rawSettings()),
				ERConfig.pretty(cfg.rawProfiles()),
				ERConfig.pretty(cfg.rawEnchantments())));
	}

	/** Pushes the snapshot to every online player (after config reload). */
	public static void broadcastProfiles(Iterable<ServerPlayer> players) {
		for (ServerPlayer p : players) sendProfiles(p);
	}
}
