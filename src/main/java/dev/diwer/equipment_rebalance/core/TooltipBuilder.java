package dev.diwer.equipment_rebalance.core;

import dev.diwer.equipment_rebalance.config.ERConfigData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the per-item economy meter lines shown in the tooltip
 * (slots bar + capacity bar, colored via config).
 */
public final class TooltipBuilder {
	private TooltipBuilder() {}

	public static List<Component> build(ItemStack stack, ERConfigData.MaterialProfile p, ERConfigData data) {
		List<Component> out = new ArrayList<>();
		ERConfigData.TooltipSettings tt = data.tooltip;
		if (!tt.enabled()) return out;

		ChatFormatting fill = parseColor(tt.filledColor(), ChatFormatting.DARK_GREEN);
		ChatFormatting empty = parseColor(tt.emptyColor(), ChatFormatting.DARK_GRAY);

		out.add(Component.empty()); // spacer

		int usedSlots = EnchEconomy.usedSlots(stack);
		net.minecraft.network.chat.MutableComponent slotsLine = Component.empty()
				.append(Component.translatable("equipment_rebalance.tooltip.slots").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(" "))
				.append(bar(usedSlots, p.slots(), Math.max(1, tt.slotsBarLength()), fill, empty))
				.append(Component.translatable("equipment_rebalance.tooltip.value", usedSlots, p.slots())
						.withStyle(ChatFormatting.GRAY));
		if (tt.showMaxLevel()) {
			slotsLine.append(Component.translatable("equipment_rebalance.tooltip.max_level", p.maxLevel())
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		out.add(slotsLine);

		if (p.capacity() != Integer.MAX_VALUE) {
			int usedCap = EnchEconomy.usedCapacity(stack);
			out.add(Component.empty()
					.append(Component.translatable("equipment_rebalance.tooltip.power").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(" "))
					.append(bar(usedCap, p.capacity(), Math.max(1, tt.capacityBarLength()), fill, empty))
					.append(Component.translatable("equipment_rebalance.tooltip.value", usedCap, p.capacity())
							.withStyle(ChatFormatting.GRAY)));
			if (tt.warnOverCapacity() && usedCap > p.capacity()) {
				out.add(Component.translatable("equipment_rebalance.tooltip.over_capacity").withStyle(ChatFormatting.RED));
			}
		}
		return out;
	}

	private static Component bar(int used, int total, int length, ChatFormatting fill, ChatFormatting empty) {
		int filled = total <= 0 ? 0 : (int) Math.round(Math.min(1.0, (double) used / total) * length);
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < filled && i < length; i++) s.append('█');
		String filledStr = s.toString();
		StringBuilder e = new StringBuilder();
		for (int i = filled; i < length; i++) e.append('░');
		return Component.literal(filledStr).withStyle(fill)
				.append(Component.literal(e.toString()).withStyle(empty));
	}

	private static ChatFormatting parseColor(String name, ChatFormatting fallback) {
		if (name == null || name.isBlank()) return fallback;
		try {
			ChatFormatting f = ChatFormatting.valueOf(name.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
			return f == null ? fallback : f;
		} catch (IllegalArgumentException ex) {
			return fallback;
		}
	}
}
