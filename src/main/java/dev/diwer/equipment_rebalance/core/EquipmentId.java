package dev.diwer.equipment_rebalance.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class EquipmentId {
	public enum Family { ARMOR, TOOL, SPECIAL, OTHER }

	public static final List<String> ARMOR_SUFFIXES = List.of("helmet", "chestplate", "leggings", "boots");
	public static final List<String> TOOL_SUFFIXES = List.of("sword", "pickaxe", "axe", "shovel", "hoe");
	public static final List<String> SPECIAL_PATHS = List.of("elytra", "shield", "bow", "crossbow", "trident",
			"mace", "fishing_rod", "shears", "brush", "carrot_on_a_stick", "warped_fungus_on_a_stick");

	public final Family family;
	public final String path;
	public final String material;

	private EquipmentId(Family family, String path, String material) {
		this.family = family;
		this.path = path;
		this.material = material;
	}

	public static EquipmentId of(ItemStack stack) {
		if (stack.isEmpty()) return null;
		Item item = stack.getItem();
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return ofPath(id == null ? "" : id.getPath());
	}

	public static EquipmentId ofPath(String path) {
		for (String special : SPECIAL_PATHS) {
			if (path.equals(special)) return new EquipmentId(Family.SPECIAL, path, path);
		}
		if (path.equals("turtle_helmet")) return new EquipmentId(Family.SPECIAL, path, "turtle_helmet");
		for (String suffix : ARMOR_SUFFIXES) {
			if (path.endsWith(suffix)) {
				return new EquipmentId(Family.ARMOR, path, materialOf(path, suffix));
			}
		}
		for (String suffix : TOOL_SUFFIXES) {
			if (path.endsWith(suffix)) {
				return new EquipmentId(Family.TOOL, path, materialOf(path, suffix));
			}
		}
		return new EquipmentId(Family.OTHER, path, null);
	}

	/** "iron_helmet" + "helmet" -> "iron"; "golden_sword" + "sword" -> "gold". */
	private static String materialOf(String path, String suffix) {
		int end = path.length() - suffix.length();
		if (end > 0 && path.charAt(end - 1) == '_') end--;
		return normalizeMaterial(end > 0 ? path.substring(0, end) : "");
	}

	private static String normalizeMaterial(String raw) {
		return switch (raw) {
			case "wooden" -> "wood";
			case "golden" -> "gold";
			default -> raw;
		};
	}

	/** Suffix used for normalization references (helmet/chestplate/... or sword/pickaxe/...). */
	public String slotKey() {
		for (String suffix : ARMOR_SUFFIXES) if (path.endsWith(suffix)) return suffix;
		for (String suffix : TOOL_SUFFIXES) if (path.endsWith(suffix)) return suffix;
		return null;
	}

	@Override
	public String toString() {
		return family + ":" + path;
	}
}
