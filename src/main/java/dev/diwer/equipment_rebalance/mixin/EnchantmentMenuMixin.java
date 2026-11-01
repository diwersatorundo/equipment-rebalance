package dev.diwer.equipment_rebalance.mixin;

import dev.diwer.equipment_rebalance.config.ERConfig;
import dev.diwer.equipment_rebalance.config.ERConfigData;
import dev.diwer.equipment_rebalance.core.EnchEconomy;
import dev.diwer.equipment_rebalance.core.TableUses;
import dev.diwer.equipment_rebalance.repair.RepairState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Replaces the enchanting table's click economy for tracked items: no XP levels
 * and no lapis are consumed; the resource is the table's lifetime "uses" budget
 * (12-18) and the item's material profile (slots / capacity / max level) gates
 * what can be installed. Repair mode spends a table use for an XP-fueled
 * durability refill.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {

	@Shadow private Container enchantSlots;

	@Shadow private List<EnchantmentInstance> getEnchantmentList(RegistryAccess access, ItemStack stack, int row, int weightedLevel) {
		throw new IllegalStateException();
	}

	@Shadow public int[] costs;

	@Shadow private DataSlot enchantmentSeed;

	@Shadow private ContainerLevelAccess access;

	@Inject(method = "clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z", at = @At("HEAD"), cancellable = true)
	private void equipment_rebalance$onEnchantClick(Player player, int row, CallbackInfoReturnable<Boolean> cir) {
		ERConfigData cfg = ERConfig.get().current();
		if (!cfg.modEnabled) return;
		if (row < 0 || row >= this.costs.length) return;
		if (!(player instanceof ServerPlayer serverPlayer)) return;
		ItemStack probe = this.enchantSlots.getItem(0);
		if (probe.isEmpty()) return;
		if (EnchEconomy.profileFor(probe) == null) return;
		this.access.execute((Level level, BlockPos pos) -> this.equipment_rebalance$operate(level, pos, serverPlayer, row));
		cir.setReturnValue(true);
	}

	// ------------------------------------------------------------------
	// server flow
	// ------------------------------------------------------------------

	private void equipment_rebalance$operate(Level level, BlockPos pos, ServerPlayer player, int row) {
		ERConfigData cfg = ERConfig.get().current();
		if (!(level.getBlockEntity(pos) instanceof EnchantingTableBlockEntity be)) return;
		ItemStack stack = this.enchantSlots.getItem(0);
		if (stack.isEmpty()) return;
		if (TableUses.remaining(be, cfg) <= 0) return;

		if (RepairState.isRepairMode(player.getUUID())) {
			if (this.equipment_rebalance$repair(player, stack)) {
				TableUses.consume(be, cfg);
				this.enchantSlots.setChanged();
			}
			return;
		}

		if (this.costs[row] <= 0) return;
		ERConfigData.MaterialProfile profile = EnchEconomy.profileFor(stack);
		if (profile == null) return;
		List<EnchantmentInstance> list = this.getEnchantmentList(level.registryAccess(), stack, row, this.costs[row]);
		if (list == null || list.isEmpty()) return;

		boolean changed = false;
		for (EnchantmentInstance inst : list) {
			int target = EnchEconomy.limitCandidate(stack, profile, inst);
			if (target < 0) continue;
			Holder<Enchantment> ench = inst.enchantment();
			stack.enchant(ench, target);
			changed = true;
		}
		if (!changed) return;

		if (stack.getItem() == Items.BOOK) {
			stack = stack.transmuteCopy(Items.ENCHANTED_BOOK);
		}
		this.enchantSlots.setItem(0, stack);
		player.awardStat(Stats.ENCHANT_ITEM);
		this.enchantSlots.setChanged();
		this.enchantmentSeed.set(player.getEnchantmentSeed());
		TableUses.consume(be, cfg);
	}

	/** @return true when durability was actually restored (and XP charged). */
	private boolean equipment_rebalance$repair(ServerPlayer player, ItemStack stack) {
		ERConfigData cfg = ERConfig.get().current();
		int cost = RepairState.xpCost(stack, cfg);
		if (cost <= 0) return false;
		int max = stack.getMaxDamage();
		int dmg = stack.getDamageValue();
		if (max <= 0 || dmg <= 0) return false;
		if (player.totalExperience < cost) return false;
		stack.setDamageValue(0);
		player.giveExperiencePoints(-cost);
		return true;
	}
}
