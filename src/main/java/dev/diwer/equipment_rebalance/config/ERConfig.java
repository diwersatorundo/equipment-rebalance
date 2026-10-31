package dev.diwer.equipment_rebalance.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.diwer.equipment_rebalance.EquipmentRebalance;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads, validates and stores the Equipment Rebalance configuration.
 *
 * <p>Three files under {@code config/equipment_rebalance/}:</p>
 * <ul>
 *   <li>{@code settings.json} — general gameplay parameters.</li>
 *   <li>{@code profiles.json} — material/equipment profiles (slots, capacity, max level, normalization).</li>
 *   <li>{@code enchantments.json} — enchantment cost and multiplier configuration.</li>
 * </ul>
 *
 * <p>Missing keys receive defaults; invalid values are corrected in memory and
 * rewritten to disk. Values never crash the loader.</p>
 */
public final class ERConfig {
	private static final ERConfigData DEFAULTS = Defaults.build();

	private volatile ERConfigData data = DEFAULTS;
	private volatile ERConfigData serverSnapshot = DEFAULTS;
	private volatile JsonObject rawSettings = Defaults.settings();
	private volatile JsonObject rawProfiles = Defaults.profiles();
	private volatile JsonObject rawEnchantments = Defaults.enchantments();
	private static final ERConfig INSTANCE = new ERConfig();

	public static ERConfig get() {
		return INSTANCE;
	}

	public ERConfigData current() {
		return data;
	}

	/** Raw JSON objects loaded from disk (used for the client sync payload). */
	public JsonObject rawSettings() {
		return rawSettings;
	}

	public JsonObject rawProfiles() {
		return rawProfiles;
	}

	public JsonObject rawEnchantments() {
		return rawEnchantments;
	}

	/** Authoritative data for servers; mirrors the config on singleplayer. */
	public void adoptServerSnapshot(ERConfigData snapshot) {
		if (snapshot != null) serverSnapshot = snapshot;
	}

	public ERConfigData serverData() {
		return serverSnapshot;
	}

	/** Adopts an authoritative server snapshot built from raw trio (client side). */
	public void adoptServerSnapshot(JsonObject settings, JsonObject profiles, JsonObject enchantments) {
		if (settings == null || profiles == null || enchantments == null) return;
		try {
			ERConfigData rebuilt = Parser.merge(Defaults.build(), settings, profiles, enchantments);
			this.data = rebuilt;
			this.serverSnapshot = rebuilt;
		} catch (Exception e) {
			EquipmentRebalance.LOGGER.warn("Equipment Rebalance: failed to adopt server snapshot", e);
		}
	}

	public void reload() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve(ERConfigData.CONFIG_DIR);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			EquipmentRebalance.LOGGER.error("Cannot create config dir {}", dir, e);
			return;
		}
		ERConfigData rebuilt = Defaults.build();
		try {
			JsonObject settings = load(dir.resolve("settings.json"), Defaults::settings);
			JsonObject profiles = load(dir.resolve("profiles.json"), Defaults::profiles);
			JsonObject ench = load(dir.resolve("enchantments.json"), Defaults::enchantments);
			this.rawSettings = settings;
			this.rawProfiles = profiles;
			this.rawEnchantments = ench;
			rebuilt = Parser.merge(rebuilt, settings, profiles, ench);
		} catch (Exception e) {
			EquipmentRebalance.LOGGER.error("Equipment Rebalance: failed to read config — falling back to defaults", e);
			return;
		}
		data = rebuilt;
		serverSnapshot = rebuilt;
	}

	/**
	 * Reads a config file; if it is missing, a defaults template is created on
	 * disk and returned in-memory. Unreadable content is replaced by defaults.
	 */
	private static JsonObject load(Path p, java.util.function.Supplier<JsonObject> defaults) {
		JsonObject parsed;
		if (Files.exists(p)) {
			try {
				JsonElement el = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
				parsed = el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
			} catch (Exception e) {
				EquipmentRebalance.LOGGER.warn("{} unreadable; recreating with defaults", p, e);
				parsed = defaults.get();
				write(p, parsed);
				return parsed;
			}
			return parsed;
		}
		parsed = defaults.get();
		write(p, parsed);
		return parsed;
	}

	private static void write(Path p, JsonObject parsed) {
		try {
			Files.writeString(p, pretty(parsed), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	public static String pretty(JsonObject o) {
		var sb = new StringBuilder();
		writePretty(o, sb, 0);
		sb.append('\n');
		return sb.toString();
	}

	/** Best-effort parse of a raw JSON string; null on garbage input. */
	public static JsonObject tryParse(String json) {
		if (json == null || json.isBlank()) return null;
		try {
			JsonElement el = JsonParser.parseString(json);
			return el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static void writePretty(JsonElement el, StringBuilder sb, int depth) {
		String pad = "\t".repeat(depth);
		if (el.isJsonObject()) {
			JsonObject o = el.getAsJsonObject();
			if (o.size() == 0) { sb.append("{}"); return; }
			sb.append("{\n");
			int i = 0;
			for (java.util.Map.Entry<String, JsonElement> entry : o.entrySet()) {
				sb.append(pad).append('\t').append('"').append(entry.getKey()).append("\": ");
				writePretty(entry.getValue(), sb, depth + 1);
				sb.append(++i == o.size() ? "" : ",").append('\n');
			}
			sb.append(pad).append('}');
		} else if (el.isJsonArray()) {
			JsonArray arr = el.getAsJsonArray();
			if (arr.isEmpty()) { sb.append("[]"); return; }
			sb.append("[");
			for (int i = 0; i < arr.size(); i++) {
				if (i > 0) sb.append(", ");
				writePretty(arr.get(i), sb, depth + 1);
			}
			sb.append("]");
		} else if (el.isJsonPrimitive()) {
			JsonPrimitive pr = el.getAsJsonPrimitive();
			if (pr.isString()) {
				String s = pr.getAsString();
				sb.append('\"').append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append('\"');
			} else {
				sb.append(pr);
			}
		} else {
			sb.append(el);
		}
	}

	/** Simple structural JSON merger/decoder for building a data snapshot. */
	public static final class Parser {
		@SuppressWarnings("unchecked")
		public static ERConfigData merge(ERConfigData base, JsonObject settings, JsonObject profiles, JsonObject ench) {
			boolean modEnabled = bx(settings, "enabled", base.modEnabled);
			JsonObject tt = orNull(settings, "tooltip");
			ERConfigData.TooltipSettings tooltip = mergeTooltip(base.tooltip, tt);

			JsonObject repair = orNull(settings, "repair");
			int fullXp = orInt(repair, "full_repair_xp_cost", base.repairFullXpCost, 0, 1_000_000);
			int minXp = orInt(repair, "min_xp_cost", base.repairMinXpCost, 0, 1000);
			minXp = Math.max(minXp, 1);
			int usesMin = orInt(repair, "table_uses_min", base.tableUsesMin, 0, 10000);
			int usesMax = orInt(repair, "table_uses_max", base.tableUsesMax, usesMin, 100000);
			boolean anvilRepair = bx(repair, "anvil_material_repair_allowed", base.anvilMaterialRepairEnabled);

			JsonObject legacy = orNull(settings, "legacy_items");
			ERConfigData.MendingBehavior mb = orEnum(legacy, "mending_legacy_behavior", base.mendingLegacyBehavior, ERConfigData.MendingBehavior.class);
			boolean overWarn = bx(legacy, "over_capacity_warn", base.overCapacityWarn);
			ERConfigData.OverCapacityBehavior over = orEnum(legacy, "over_capacity_behavior", base.overCapacityBehavior, ERConfigData.OverCapacityBehavior.class);

			JsonObject eco = orNull(ench, "enchantment_economy");
			int[] levc = base.levelBaseCost;
			JsonElement leEl = eco == null ? null : eco.get("level_base_cost");
			JsonArray levcJson = leEl != null && leEl.isJsonArray() ? leEl.getAsJsonArray() : null;
			if (levcJson != null && levcJson.size() >= 10) {
				int[] buf = new int[11];
				for (int i = 1; i <= 10; i++) {
					JsonElement p = levcJson.get(i - 1);
					buf[i] = p != null && p.isJsonPrimitive() ? clampInt(p.getAsInt(), 0, 100000) : Defaults.LEVEL_BASE_COST[i];
				}
				levc = buf;
			} else {
				levc = base.levelBaseCost.clone();
			}
			double defaultMul = orDouble(eco, "default_multiplier", base.customMultiplierDefault, 0.0, 1000.0);
			boolean cursesFree = bx(eco, "curse_no_slot_no_capacity", base.cursesFree);
			double globalMul = orDouble(ench, "global_max_level_multiplier", base.globalMaxLevelMultiplier, 1.0, 100.0);
			boolean vanishCounts = bx(ench, "vanishing_curse_counts", base.vanishingCurseCounts);

			// profiles
			Map<String, ERConfigData.MaterialProfile> armor = mergeProfiles(base.armorProfiles, orNull(profiles, "armor_materials"));
			Map<String, ERConfigData.MaterialProfile> tools = mergeProfiles(base.toolProfiles, orNull(profiles, "tool_materials"));
			Map<String, ERConfigData.MaterialProfile> special = mergeProfiles(base.specialProfiles, orNull(profiles, "special_items"));

			// normalization
			JsonObject norm = orNull(profiles, "normalization");
			boolean normEnabled = bx(norm, "enabled", base.normalizeEnabled);
			Map<String, ERConfigData.NormalizedArmorStats> nArmor = new HashMap<>(base.normalizedArmor);
			if (norm != null && orNull(norm, "armor_reference") != null) {
				for (var e : orNull(norm, "armor_reference").entrySet()) {
					JsonObject v = e.getValue() != null && e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : null;
					nArmor.put(e.getKey(), new ERConfigData.NormalizedArmorStats(
							v == null ? null : nullableInt(v, "durability"),
							v == null ? null : nullableFloat(v, "armor"),
							v == null ? null : nullableFloat(v, "toughness")));
				}
			}
			Map<String, ERConfigData.NormalizedToolStats> nTools = new HashMap<>(base.normalizedTools);
			if (norm != null && orNull(norm, "tool_reference") != null) {
				for (var e : orNull(norm, "tool_reference").entrySet()) {
					JsonObject v = e.getValue() != null && e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : null;
					nTools.put(e.getKey(), new ERConfigData.NormalizedToolStats(
							v == null ? null : nullableInt(v, "durability"),
							v == null ? null : nullableFloat(v, "attack_damage"),
							v == null ? null : nullableFloat(v, "attack_speed"),
							v == null ? null : nullableFloat(v, "mining_speed")));
				}
			}

			// enchantment factors
			Map<String, Double> mult = new HashMap<>(base.enchantMultipliers);
			if (ench != null) {
				JsonElement mEl = ench.get("enchantment_multipliers");
				if (mEl != null && mEl.isJsonObject()) {
					for (var e : mEl.getAsJsonObject().entrySet()) {
						if (e.getValue().isJsonPrimitive()) mult.put(e.getKey(), clampDouble(e.getValue().getAsDouble(), 0.0, 1000));
					}
				}
			}

			return new ERConfigData(
					modEnabled, tooltip,
					fullXp, minXp, usesMin, usesMax, anvilRepair,
					mb, overWarn, over,
					levc, defaultMul, cursesFree, globalMul, vanishCounts,
					armor, tools, special,
					normEnabled, nArmor, nTools, mult);
		}

		private static ERConfigData.TooltipSettings mergeTooltip(ERConfigData.TooltipSettings base, JsonObject tt) {
			if (tt == null) return base;
			JsonObject style = orNull(tt, "style");
			return new ERConfigData.TooltipSettings(
					bx(tt, "enabled", base.enabled()),
					bx(tt, "show_max_level", base.showMaxLevel()),
					bx(tt, "warn_over_capacity", base.warnOverCapacity()),
					orInt(style, "slots_bar_length", base.slotsBarLength(), 1, 64),
					orInt(style, "capacity_bar_length", base.capacityBarLength(), 1, 64),
					orString(style, "filled_color", base.filledColor()),
					orString(style, "empty_color", base.emptyColor()));
		}

		private static Map<String, ERConfigData.MaterialProfile> mergeProfiles(Map<String, ERConfigData.MaterialProfile> base, JsonObject o) {
			Map<String, ERConfigData.MaterialProfile> out = new HashMap<>(base);
			if (o == null) return out;
			for (var e : o.entrySet()) {
				if (e.getValue().isJsonObject()) {
					JsonObject v = e.getValue().getAsJsonObject();
					int slots = orInt(v, "slots", 2, 0, 32);
					int maxLevel = orInt(v, "max_level", 3, 1, 100);
					int capacity = orInt(v, "capacity", 6, 0, 100000);
					out.put(e.getKey(), new ERConfigData.MaterialProfile(slots, maxLevel, capacity));
				}
			}
			return out;
		}

		private static <T extends Enum<T>> T orEnum(JsonObject o, String key, T def, Class<T> type) {
			if (o == null) return def;
			try {
				String s = o.get(key) != null ? o.get(key).getAsString() : null;
				if (s == null) return def;
				return T.valueOf(type, s.toUpperCase(Locale.ROOT));
			} catch (Exception e) {
				return def;
			}
		}

		private static JsonObject orNull(JsonObject o, String key) {
			if (o == null || !o.isJsonObject()) return null;
			JsonElement el = o.get(key);
			return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
		}

		private static int orInt(JsonObject o, String key, int def, int min, int max) {
			if (o == null) return def;
			try {
				JsonElement el = o.get(key);
				return el != null && el.isJsonPrimitive() ? clampInt(el.getAsInt(), min, max) : def;
			} catch (Exception e) {
				return def;
			}
		}

		private static double orDouble(JsonObject o, String key, double def, double min, double max) {
			if (o == null) return def;
			try {
				JsonElement el = o.get(key);
				return el != null && el.isJsonPrimitive() ? clampDouble(el.getAsDouble(), min, max) : def;
			} catch (Exception e) {
				return def;
			}
		}

		private static boolean bx(JsonObject o, String key, boolean def) {
			if (o == null) return def;
			try {
				JsonElement el = o.get(key);
				return el != null && el.isJsonPrimitive() ? el.getAsBoolean() : def;
			} catch (Exception e) {
				return def;
			}
		}

		private static String orString(JsonObject o, String key, String def) {
			if (o == null) return def;
			try {
				JsonElement el = o.get(key);
				return el != null && el.isJsonPrimitive() ? el.getAsString() : def;
			} catch (Exception e) {
				return def;
			}
		}

		private static Integer nullableInt(JsonObject o, String key) {
			JsonElement el = o.get(key);
			return el != null && el.isJsonPrimitive() ? clampInt(el.getAsInt(), 0, 10000) : null;
		}

		private static Float nullableFloat(JsonObject o, String key) {
			JsonElement el = o.get(key);
			return el != null && el.isJsonPrimitive() ? (float) clampDouble(el.getAsDouble(), -400, 400) : null;
		}

		private static int clampInt(int v, int min, int max) {
			return Math.min(max, Math.max(min, v));
		}

		private static double clampDouble(double v, double min, double max) {
			return Math.min(max, Math.max(min, v));
		}
	}

	/** All file-rendering & default value definitions live here. */
	public static final class Defaults {
		public static final int[] LEVEL_BASE_COST = {0, 1, 2, 3, 5, 7, 9, 12, 15, 18, 22};

		/** The default configuration snapshot. */
		static ERConfigData build() {
			Map<String, ERConfigData.MaterialProfile> a = armorProfiles();
			Map<String, ERConfigData.MaterialProfile> t = toolProfiles();
			Map<String, ERConfigData.MaterialProfile> s = specialProfiles();

			Map<String, Double> mult = new HashMap<>();
			double ONE = 1.0;
			for (String id : List.of(
					"minecraft:protection", "minecraft:sharpness", "minecraft:power", "minecraft:efficiency",
					"minecraft:smite", "minecraft:bane_of_arthropods", "minecraft:impaling", "minecraft:aqua_affinity",
					"minecraft:feather_falling", "minecraft:respiration", "minecraft:fire_protection",
					"minecraft:blast_protection", "minecraft:projectile_protection", "minecraft:loyalty", "minecraft:multishot",
					"minecraft:piercing", "minecraft:punch", "minecraft:lunge", "minecraft:breach", "minecraft:density",
					"minecraft:flame", "minecraft:frost_walker", "minecraft:fire_aspect", "minecraft:knockback",
					"minecraft:soul_speed", "minecraft:sweeping_edge", "minecraft:swift_sneak", "minecraft:wind_burst",
					"minecraft:quick_charge", "minecraft:channeling", "minecraft:riptide", "minecraft:lure",
					"minecraft:luck_of_the_sea", "minecraft:infinity", "minecraft:silk_touch", "minecraft:binding_curse",
					"minecraft:vanishing_curse", "minecraft:unbreaking", "minecraft:fortune", "minecraft:looting",
					"minecraft:mending", "minecraft:thorns")) {
				mult.put(id, ONE);
			}
			mult.put("minecraft:thorns", 1.5);
			mult.put("minecraft:fire_aspect", 1.5);
			mult.put("minecraft:knockback", 1.5);
			mult.put("minecraft:soul_speed", 1.5);
			mult.put("minecraft:depth_strider", 1.5);
			mult.put("minecraft:fortune", 2.0);
			mult.put("minecraft:looting", 2.0);
			mult.put("minecraft:silk_touch", 2.0);
			mult.put("minecraft:unbreaking", 2.0);

			Map<String, ERConfigData.NormalizedArmorStats> nArmor = new HashMap<>();
			nArmor.put("helmet", new ERConfigData.NormalizedArmorStats(55, 1.0f, 0.0f));
			nArmor.put("chestplate", new ERConfigData.NormalizedArmorStats(80, 3.0f, 0.0f));
			nArmor.put("leggings", new ERConfigData.NormalizedArmorStats(75, 2.0f, 0.0f));
			nArmor.put("boots", new ERConfigData.NormalizedArmorStats(65, 1.0f, 0.0f));
			Map<String, ERConfigData.NormalizedToolStats> nTools = new HashMap<>();
			nTools.put("sword", new ERConfigData.NormalizedToolStats(59, 3.0f, -2.4f, null));
			nTools.put("pickaxe", new ERConfigData.NormalizedToolStats(59, 1.0f, -2.8f, 2.0f));
			nTools.put("axe", new ERConfigData.NormalizedToolStats(59, 6.0f, -3.2f, 2.0f));
			nTools.put("shovel", new ERConfigData.NormalizedToolStats(59, 1.5f, -3.0f, 2.0f));
			nTools.put("hoe", new ERConfigData.NormalizedToolStats(59, 0.0f, -3.0f, 2.0f));

			return new ERConfigData(
					true,
					new ERConfigData.TooltipSettings(true, true, true, 6, 15, "gold", "dark_gray"),
					25, 1, 12, 18, true,
					ERConfigData.MendingBehavior.KEEP_WORKING, true, ERConfigData.OverCapacityBehavior.WARN,
					LEVEL_BASE_COST.clone(), 1.0, true, 2.0, false,
					a, t, s,
					true, nArmor, nTools, mult);
		}

		private static java.util.Map<String, ERConfigData.MaterialProfile> armorProfiles() {
			Map<String, ERConfigData.MaterialProfile> m = new HashMap<>();
			m.put("leather", new ERConfigData.MaterialProfile(2, 3, 6));
			m.put("gold", new ERConfigData.MaterialProfile(3, 4, 10));
			m.put("chainmail", new ERConfigData.MaterialProfile(3, 4, 11));
			m.put("copper", new ERConfigData.MaterialProfile(3, 4, 12));
			m.put("iron", new ERConfigData.MaterialProfile(3, 5, 14));
			m.put("diamond", new ERConfigData.MaterialProfile(4, 6, 20));
			m.put("netherite", new ERConfigData.MaterialProfile(6, 7, 26));
			return m;
		}

		private static java.util.Map<String, ERConfigData.MaterialProfile> toolProfiles() {
			Map<String, ERConfigData.MaterialProfile> m = new HashMap<>();
			m.put("wood", new ERConfigData.MaterialProfile(2, 3, 6));
			m.put("stone", new ERConfigData.MaterialProfile(2, 4, 8));
			m.put("copper", new ERConfigData.MaterialProfile(3, 4, 11));
			m.put("gold", new ERConfigData.MaterialProfile(3, 4, 12));
			m.put("iron", new ERConfigData.MaterialProfile(3, 5, 14));
			m.put("diamond", new ERConfigData.MaterialProfile(4, 6, 20));
			m.put("netherite", new ERConfigData.MaterialProfile(6, 7, 26));
			return m;
		}

		private static java.util.Map<String, ERConfigData.MaterialProfile> specialProfiles() {
			Map<String, ERConfigData.MaterialProfile> m = new HashMap<>();
			m.put("elytra", new ERConfigData.MaterialProfile(3, 4, 12));
			m.put("shield", new ERConfigData.MaterialProfile(2, 4, 8));
			m.put("bow", new ERConfigData.MaterialProfile(3, 5, 14));
			m.put("crossbow", new ERConfigData.MaterialProfile(3, 5, 14));
			m.put("trident", new ERConfigData.MaterialProfile(3, 6, 16));
			m.put("mace", new ERConfigData.MaterialProfile(3, 5, 14));
			m.put("fishing_rod", new ERConfigData.MaterialProfile(2, 4, 8));
			m.put("shears", new ERConfigData.MaterialProfile(2, 4, 8));
			m.put("brush", new ERConfigData.MaterialProfile(2, 4, 8));
			m.put("carrot_on_a_stick", new ERConfigData.MaterialProfile(1, 1, 1));
			m.put("warped_fungus_on_a_stick", new ERConfigData.MaterialProfile(1, 1, 1));
			m.put("turtle_helmet", new ERConfigData.MaterialProfile(3, 4, 11));
			m.put("other", new ERConfigData.MaterialProfile(2, 3, 6));
			return m;
		}

		private static JsonObject settings() {
			JsonObject o = new JsonObject();
			o.addProperty("schema_version", ERConfigData.SCHEMA_VERSION);

			JsonArray tipCmt = new JsonArray();
			o.add("_doc_tooltip", docBlock("Show enchantment slots/capacity meter in the item tooltip; style controls bar lengths and colors (named from ChatFormatting, e.g. gold/dark_gray)."));
			JsonObject tooltip = new JsonObject();
			tooltip.addProperty("enabled", true);
			tooltip.addProperty("show_max_level", true);
			tooltip.addProperty("warn_over_capacity", true);
			JsonObject style = new JsonObject();
			style.addProperty("slots_bar_length", 6);
			style.addProperty("capacity_bar_length", 15);
			style.addProperty("filled_color", "gold");
			style.addProperty("empty_color", "dark_gray");
			tooltip.add("style", style);
			o.add("tooltip", tooltip);

			JsonArray repairCmt = new JsonArray();
			o.add("_doc_repair", docBlock("XP-based full repair of a damaged item placed in the enchanting table's middle slot, via the service tab of the vanilla enchanting screen. full_repair_xp_cost is raw XP points for 100% durability, scaled proportionally to missing durability; min_xp_cost for any successful repair. table_uses_min/max is the random per-placed-table budget of repair operations; the LIFETIME budget travels with the table item (never reset by placing/breaking). anvil_material_repair_allowed turns vanilla material-repair of items in the anvil on/off."));
			JsonObject repair = new JsonObject();
			repair.addProperty("full_repair_xp_cost", 25);
			repair.addProperty("min_xp_cost", 1);
			repair.addProperty("table_uses_min", 12);
			repair.addProperty("table_uses_max", 18);
			repair.addProperty("anvil_material_repair_allowed", true);
			o.add("repair", repair);

			JsonArray legacyCmt = new JsonArray();
			o.add("_doc_legacy_items", docBlock("Treatment of pre-existing enchanted items whose state violates the new limits. mending_legacy_behavior: KEEP_WORKING / DISABLE_EFFECT / REMOVE_ON_VALIDATE. over_capacity_behavior: WARN / IGNORE / CLAMP."));
			JsonObject legacy = new JsonObject();
			legacy.addProperty("mending_legacy_behavior", "KEEP_WORKING");
			legacy.addProperty("over_capacity_warn", true);
			legacy.addProperty("over_capacity_behavior", "WARN");
			o.add("legacy_items", legacy);

			o.addProperty("enabled", true);
			return o;
		}

		private static JsonObject profiles() {
			JsonObject o = new JsonObject();
			o.addProperty("schema_version", ERConfigData.SCHEMA_VERSION);
			JsonArray doc = new JsonArray();
			doc.add("Each material family defines three enchantment-economy parameters: how many");
			doc.add("different enchantments may be installed (slots), maximal single-enchantment level");
			doc.add("(max_level) and the total capacity across all enchantments. Material normalization");
			doc.add("makes the same *type* of armor/tool identical across materials: the numbers below");
			doc.add("(armor_reference/tool_reference) are the reference stats applied to every family");
			doc.add("member. Mining tier tags and repair items stay vanilla.");
			o.add("_doc", doc);

			JsonObject armor = new JsonObject();
			armorProfiles().forEach((k, v) -> {
				JsonObject p = new JsonObject();
				p.addProperty("slots", v.slots());
				p.addProperty("max_level", v.maxLevel());
				p.addProperty("capacity", v.capacity());
				armor.add(k, p);
			});
			o.add("armor_materials", armor);

			JsonObject tools = new JsonObject();
			toolProfiles().forEach((k, v) -> {
				JsonObject p = new JsonObject();
				p.addProperty("slots", v.slots());
				p.addProperty("max_level", v.maxLevel());
				p.addProperty("capacity", v.capacity());
				tools.add(k, p);
			});
			o.add("tool_materials", tools);

			JsonObject special = new JsonObject();
			specialProfiles().forEach((k, v) -> {
				JsonObject p = new JsonObject();
				p.addProperty("slots", v.slots());
				p.addProperty("max_level", v.maxLevel());
				p.addProperty("capacity", v.capacity());
				special.add(k, p);
			});
			o.add("special_items", special);

			JsonObject normalization = new JsonObject();
			normalization.addProperty("enabled", true);
			JsonObject armorRef = new JsonObject();
			addArmorRef(armorRef, "helmet", "leather_helmet");
			addArmorRef(armorRef, "chestplate", "leather_chestplate");
			addArmorRef(armorRef, "leggings", "leather_leggings");
			addArmorRef(armorRef, "boots", "leather_boots");
			normalization.add("armor_reference", armorRef);
			JsonObject toolRef = new JsonObject();
			addToolRef(toolRef, "sword", "wooden_sword");
			addToolRef(toolRef, "pickaxe", "wooden_pickaxe");
			addToolRef(toolRef, "axe", "wooden_axe");
			addToolRef(toolRef, "shovel", "wooden_shovel");
			addToolRef(toolRef, "hoe", "wooden_hoe");
			normalization.add("tool_reference", toolRef);
			o.add("normalization", normalization);
			return o;
		}

		private static void addArmorRef(JsonObject parent, String key, String refId) {
			JsonObject e = new JsonObject();
			switch (key) {
				case "helmet" -> { e.addProperty("durability", 55); e.addProperty("armor", 1.0f); e.addProperty("toughness", 0.0f); }
				case "chestplate" -> { e.addProperty("durability", 80); e.addProperty("armor", 3.0f); e.addProperty("toughness", 0.0f); }
				case "leggings" -> { e.addProperty("durability", 75); e.addProperty("armor", 2.0f); e.addProperty("toughness", 0.0f); }
				case "boots" -> { e.addProperty("durability", 65); e.addProperty("armor", 1.0f); e.addProperty("toughness", 0.0f); }
			}
			parent.add(key, e);
		}

		private static void addToolRef(JsonObject parent, String key, String refId) {
			JsonObject e = new JsonObject();
			switch (key) {
				case "sword" -> { e.addProperty("durability", 59); e.addProperty("attack_damage", 3.0f); e.addProperty("attack_speed", -2.4f); }
				case "pickaxe" -> { e.addProperty("durability", 59); e.addProperty("attack_damage", 1.0f); e.addProperty("attack_speed", -2.8f); e.addProperty("mining_speed", 2.0f); }
				case "axe" -> { e.addProperty("durability", 59); e.addProperty("attack_damage", 6.0f); e.addProperty("attack_speed", -3.2f); e.addProperty("mining_speed", 2.0f); }
				case "shovel" -> { e.addProperty("durability", 59); e.addProperty("attack_damage", 1.5f); e.addProperty("attack_speed", -3.0f); e.addProperty("mining_speed", 2.0f); }
				case "hoe" -> { e.addProperty("durability", 59); e.addProperty("attack_damage", 0.0f); e.addProperty("attack_speed", -3.0f); e.addProperty("mining_speed", 2.0f); }
			}
			parent.add(key, e);
		}

		private static JsonObject enchantments() {
			JsonObject o = new JsonObject();
			o.addProperty("schema_version", ERConfigData.SCHEMA_VERSION);
			JsonArray doc = new JsonArray();
			doc.add("cost: the base cost of a level is looked up in level_base_cost (index = level");
			doc.add("(1-based); the level capacity of an enchantment = ceil(base * multiplier).");
			doc.add("global_max_level_multiplier multiplies every vanilla enchantment's max_level");
			doc.add("(static datapack ceiling is 2x; material caps gate the rest).");
			doc.add("vanishing_curse_counts: if true, Vanishing Curse participates in slot/capacity accounting.");
			doc.add("enchantment_multipliers: per-enchant multiplier; absent enchantments use default_multiplier.");
			o.add("_doc", doc);

			JsonObject eco = new JsonObject();
			JsonArray levc = new JsonArray();
			for (int i = 1; i <= 10; i++) levc.add(LEVEL_BASE_COST[i]);
			eco.add("level_base_cost", levc);
			eco.addProperty("default_multiplier", 1.0);
			eco.addProperty("curse_no_slot_no_capacity", true);
			o.add("enchantment_economy", eco);

			o.addProperty("global_max_level_multiplier", 2.0);
			o.addProperty("vanishing_curse_counts", false);

			JsonObject mult = new JsonObject();
			putMult(mult, 1.0, "protection", "sharpness", "power", "efficiency", "smite", "bane_of_arthropods", "impaling",
					"aqua_affinity", "feather_falling", "respiration", "fire_protection", "blast_protection", "projectile_protection",
					"loyalty", "multishot", "piercing", "punch", "lunge", "breach", "density", "flame", "frost_walker", "depth_strider",
					"fire_aspect", "knockback", "soul_speed", "sweeping_edge", "swift_sneak", "wind_burst", "quick_charge", "channeling",
					"riptide", "lure", "luck_of_the_sea", "infinity", "silk_touch", "binding_curse", "vanishing_curse", "unbreaking",
					"fortune", "looting", "mending", "thorns");
			mult.addProperty("minecraft:thorns", 1.5);
			mult.addProperty("minecraft:fire_aspect", 1.5);
			mult.addProperty("minecraft:knockback", 1.5);
			mult.addProperty("minecraft:soul_speed", 1.5);
			mult.addProperty("minecraft:depth_strider", 1.5);
			mult.addProperty("minecraft:fortune", 2.0);
			mult.addProperty("minecraft:looting", 2.0);
			mult.addProperty("minecraft:silk_touch", 2.0);
			mult.addProperty("minecraft:unbreaking", 2.0);
			o.add("enchantment_multipliers", mult);
			return o;
		}

		private static void putMult(JsonObject o, double v, String... ids) {
			for (String s : ids) o.addProperty("minecraft:" + s, v);
		}

		private static JsonArray docBlock(String line) {
			JsonArray arr = new JsonArray();
			arr.add(line);
			return arr;
		}
	}
}

