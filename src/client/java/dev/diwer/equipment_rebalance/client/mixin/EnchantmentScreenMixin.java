package dev.diwer.equipment_rebalance.client.mixin;

import dev.diwer.equipment_rebalance.repair.RepairState;
import dev.diwer.equipment_rebalance.sync.ERPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the "Repair" service-tab toggle button to the enchanting table screen.
 */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin {

	@Unique
	private Button er$repairButton;

	@Inject(method = "init()V", at = @At("TAIL"))
	private void equipment_rebalance$addRepairButton(CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		boolean on = RepairState.isRepairMode(mc.player.getUUID());
		EnchantmentScreen self = (EnchantmentScreen) (Object) this;

		Button built = ((ScreenInvokerMixin) (Object) this).callAddRenderableWidget(
				Button.builder(Component.literal("Repair: " + (on ? "ON" : "OFF")), b -> {
					Minecraft m = Minecraft.getInstance();
					if (m.player == null) return;
					boolean now = !RepairState.isRepairMode(m.player.getUUID());
					RepairState.setRepairMode(m.player.getUUID(), now);
					b.setMessage(Component.literal("Repair: " + (now ? "ON" : "OFF")));
					ClientPlayNetworking.send(new ERPayloads.RepairTogglePayload(now));
				})
						.bounds(((net.minecraft.client.gui.screens.Screen) (Object) self).width - 100, 6, 94, 18)
						.tooltip(Tooltip.create(Component.literal("Repair mode: use the enchanting table as a repair service (XP cost, uses a table charge)")))
						.build());
		this.er$repairButton = built;
	}

	@Unique
	public Button er$getRepairButton() {
		return this.er$repairButton;
	}
}
