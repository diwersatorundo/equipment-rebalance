package dev.diwer.equipment_rebalance.sync;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Custom packet payloads used by Equipment Rebalance.
 * S2C "equipment_rebalance:profiles_v1" syncs the raw config JSON snapshot to
 * joining clients; C2S "equipment_rebalance:repair_toggle_v1" flips the state
 * of the enchanting-screen repair service tab.
 */
public final class ERPayloads {
	private ERPayloads() {}

	public static final CustomPacketPayload.Type<ProfilesPayload> PROFILES =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("equipment_rebalance", "profiles_v1"));

	public static final CustomPacketPayload.Type<RepairTogglePayload> REPAIR_TOGGLE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("equipment_rebalance", "repair_toggle_v1"));

	public record ProfilesPayload(String settings, String profiles, String enchantments) implements CustomPacketPayload {
		public static final StreamCodec<FriendlyByteBuf, ProfilesPayload> STREAM_CODEC = CustomPacketPayload.codec(
				(payload, buf) -> {
					buf.writeUtf(payload.settings());
					buf.writeUtf(payload.profiles());
					buf.writeUtf(payload.enchantments());
				},
				buf -> new ProfilesPayload(buf.readUtf(), buf.readUtf(), buf.readUtf()));

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return PROFILES;
		}
	}

	public record RepairTogglePayload(boolean repairMode) implements CustomPacketPayload {
		public static final StreamCodec<FriendlyByteBuf, RepairTogglePayload> STREAM_CODEC = CustomPacketPayload.codec(
				(payload, buf) -> buf.writeBoolean(payload.repairMode()),
				buf -> new RepairTogglePayload(buf.readBoolean()));

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return REPAIR_TOGGLE;
		}
	}
}
