package dev.diwer.equipment_rebalance.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Immutable snapshot of all configurable values used by Equipment Rebalance.
 *
 * <p>All numbers are validated/range-checked on load; the mod never throws on
 * broken user configuration (values fall back to defaults).</p>
 */
public final class ERConfigData {

	public static final int SCHEMA_VERSION = 1;
	public static final String CONFIG_DIR = "equipment_rebalance";

	// --------------------------------------------------------------------
	// general
	// --------------------------------------------------------------------
	public final boolean modEnabled;
	public final TooltipSettings tooltip;

	// --------------------------------------------------------------------
	// repair (enchanting table service mode)
	// --------------------------------------------------------------------
	public final int repairFullXpCost;
	public final int repairMinXpCost;
	public final int tableUsesMin;
	public final int tableUsesMax;
	public final boolean anvilMaterialRepairEnabled;

	// --------------------------------------------------------------------
	// legacy items / mending
	// --------------------------------------------------------------------
	public final MendingBehavior mendingLegacyBehavior;
	public final boolean overCapacityWarn;
	public final OverCapacityBehavior overCapacityBehavior;

	// --------------------------------------------------------------------
	// enchantment economy
	// --------------------------------------------------------------------
	public final int[] levelBaseCost;

	public final double customMultiplierDefault;
	public final boolean cursesFree;
	public final double globalMaxLevelMultiplier;
	public final boolean vanishingCurseCounts;

	// --------------------------------------------------------------------
	// material profiles (armor / tools). key: material id used by this mod
	// --------------------------------------------------------------------
	public final java.util.Map<String, MaterialProfile> armorProfiles;
	public final java.util.Map<String, MaterialProfile> toolProfiles;
	public final java.util.Map<String, MaterialProfile> specialProfiles;

	// normalization
	public final boolean normalizeEnabled;
	public final java.util.Map<String, NormalizedArmorStats> normalizedArmor;
	public final java.util.Map<String, NormalizedToolStats> normalizedTools;

	// --------------------------------------------------------------------
	// enchantment handling
	// --------------------------------------------------------------------
	public final java.util.Map<String, Double> enchantMultipliers;

	public ERConfigData(
			boolean modEnabled, TooltipSettings tooltip,
			int repairFullXpCost, int repairMinXpCost, int tableUsesMin, int tableUsesMax, boolean anvilMaterialRepairEnabled,
			MendingBehavior mendingLegacyBehavior, boolean overCapacityWarn, OverCapacityBehavior overCapacityBehavior,
			int[] levelBaseCost, double customMultiplierDefault, boolean cursesFree,
			double globalMaxLevelMultiplier, boolean vanishingCurseCounts,
			java.util.Map<String, MaterialProfile> armorProfiles,
			java.util.Map<String, MaterialProfile> toolProfiles,
			java.util.Map<String, MaterialProfile> specialProfiles,
			boolean normalizeEnabled,
			java.util.Map<String, NormalizedArmorStats> normalizedArmor,
			java.util.Map<String, NormalizedToolStats> normalizedTools,
			java.util.Map<String, Double> enchantMultipliers) {
		this.modEnabled = modEnabled;
		this.tooltip = tooltip;
		this.repairFullXpCost = repairFullXpCost;
		this.repairMinXpCost = repairMinXpCost;
		this.tableUsesMin = tableUsesMin;
		this.tableUsesMax = tableUsesMax;
		this.anvilMaterialRepairEnabled = anvilMaterialRepairEnabled;
		this.mendingLegacyBehavior = mendingLegacyBehavior;
		this.overCapacityWarn = overCapacityWarn;
		this.overCapacityBehavior = overCapacityBehavior;
		this.levelBaseCost = levelBaseCost;
		this.customMultiplierDefault = customMultiplierDefault;
		this.cursesFree = cursesFree;
		this.globalMaxLevelMultiplier = globalMaxLevelMultiplier;
		this.vanishingCurseCounts = vanishingCurseCounts;
		this.armorProfiles = java.util.Collections.unmodifiableMap(armorProfiles);
		this.toolProfiles = java.util.Collections.unmodifiableMap(toolProfiles);
		this.specialProfiles = java.util.Collections.unmodifiableMap(specialProfiles);
		this.normalizeEnabled = normalizeEnabled;
		this.normalizedArmor = java.util.Collections.unmodifiableMap(normalizedArmor);
		this.normalizedTools = java.util.Collections.unmodifiableMap(normalizedTools);
		this.enchantMultipliers = java.util.Collections.unmodifiableMap(enchantMultipliers);
	}

	public record MaterialProfile(int slots, int maxLevel, int capacity) {}

	public record TooltipSettings(boolean enabled, boolean showMaxLevel, boolean warnOverCapacity, int slotsBarLength,
			int capacityBarLength, String filledColor, String emptyColor) {}

	public record NormalizedArmorStats(Integer durability, Float armor, Float toughness) {}

	public record NormalizedToolStats(Integer durability, Float attackDamage, Float attackSpeed, Float miningSpeed) {}

	public enum MendingBehavior {
		/** Keep pre-existing Mending on legacy items functional (configurable grace). */
		KEEP_WORKING,
		/** Disable the effect for legacy items (enchantment stays, does nothing). */
		DISABLE_EFFECT,
		/** Remove the enchantment from legacy items on validation. */
		REMOVE_ON_VALIDATE
	}

	public enum OverCapacityBehavior {
		WARN, IGNORE, CLAMP
	}
}
