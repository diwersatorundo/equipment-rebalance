package dev.diwer.equipment_rebalance.core;

import dev.diwer.equipment_rebalance.config.ERConfig;
import dev.diwer.equipment_rebalance.config.ERConfigData;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemAttributeModifiers.Entry;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Tool.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Rewrites default item components so every material of a slot type shares
 * identical stats (durability / armor / toughness / attack / mining speed).
 * The rewrite happens on the registry Holder, so every ItemStack built from
 * it (inventory, ground drops, creative menu) inherits the balanced values.
 *
 * Mining tier tags, repair items and enchantability stay vanilla.
 */
public final class EquipmentNormalizer {
	private static final WeakHashMap<Item, DataComponentMap> CACHE = new WeakHashMap<>();

	private EquipmentNormalizer() {}

	/** Must be invalidated when the config is (re)loaded. */
	public static void onConfigReload() {
		CACHE.clear();
	}

	/**
	 * Returns the balanced component map for this holder's item, or null when no
	 * rewrite applies (non-equipment item or normalization disabled).
	 */
	public static DataComponentMap patch(Holder.Reference<?> holder, DataComponentMap original) {
		if (!ERConfig.get().current().modEnabled) return null;
		if (!ERConfig.get().current().normalizeEnabled) return null;
		if (holder == null) return null;
		if (!(holder.value() instanceof Item item)) return null;
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		if (id == null) return null;
		EquipmentId eid = EquipmentId.ofPath(id.getPath());
		if (eid.slotKey() == null) return null; // armor/tools only
		DataComponentMap cached = CACHE.get(item);
		if (cached != null) return cached;
		DataComponentMap built = build(cfg(), eid.slotKey(), original);
		if (built == null) return null;
		CACHE.put(item, built);
		return built;
	}

	// ------------------------------------------------------------------
	// map assembly
	// ------------------------------------------------------------------

	private static DataComponentMap build(ERConfigData cfg, String slotKey, DataComponentMap original) {
		DataComponentMap.Builder builder = DataComponentMap.builder();
		builder.addAll(original);
		boolean changed = false;

		if (cfg.normalizedArmor.containsKey(slotKey)) {
			ERConfigData.NormalizedArmorStats ref = cfg.normalizedArmor.get(slotKey);
			if (ref.durability() != null) {
				builder.set(DataComponents.MAX_DAMAGE, ref.durability());
				changed = true;
			}
			Integer dmg = orDefault(original, DataComponents.DAMAGE);
			if (dmg != null) {
				builder.set(DataComponents.DAMAGE, Math.min(dmg, ref.durability() == null ? dmg : ref.durability()));
				changed = true;
			}
			ItemAttributeModifiers attrs = original.get(DataComponents.ATTRIBUTE_MODIFIERS);
			ItemAttributeModifiers rebuilt = rebuildArmorAttributes(attrs == null ? ItemAttributeModifiers.EMPTY : attrs, ref);
			if (rebuilt != null) {
				builder.set(DataComponents.ATTRIBUTE_MODIFIERS, rebuilt);
				changed = true;
			}
		} else if (cfg.normalizedTools.containsKey(slotKey)) {
			ERConfigData.NormalizedToolStats ref = cfg.normalizedTools.get(slotKey);
			if (ref.durability() != null) {
				builder.set(DataComponents.MAX_DAMAGE, ref.durability());
				changed = true;
			}
			Integer dmg = orDefault(original, DataComponents.DAMAGE);
			if (dmg != null) {
				builder.set(DataComponents.DAMAGE, Math.min(dmg, ref.durability() == null ? dmg : ref.durability()));
				changed = true;
			}
			ItemAttributeModifiers attrs = original.get(DataComponents.ATTRIBUTE_MODIFIERS);
			ItemAttributeModifiers rebuilt = rebuildToolAttributes(attrs == null ? ItemAttributeModifiers.EMPTY : attrs, ref);
			if (rebuilt != null) {
				builder.set(DataComponents.ATTRIBUTE_MODIFIERS, rebuilt);
				changed = true;
			}
			Tool tool = original.get(DataComponents.TOOL);
			Tool rebuiltTool = rebuildTool(tool, ref);
			if (rebuiltTool != null) {
				builder.set(DataComponents.TOOL, rebuiltTool);
				changed = true;
			}
		}
		return changed ? builder.build() : null;
	}

	private static Integer orDefault(DataComponentMap original, net.minecraft.core.component.DataComponentType<Integer> type) {
		return original.get(type);
	}

	// ------------------------------------------------------------------
	// attribute modifier rewriting
	// ------------------------------------------------------------------

	private static ItemAttributeModifiers rebuildArmorAttributes(ItemAttributeModifiers base, ERConfigData.NormalizedArmorStats ref) {
		if (ref.armor() == null && ref.toughness() == null) return null;
		List<Entry> out = new ArrayList<>();
		boolean changed = false;
		for (Entry e : base.modifiers()) {
			AttributeModifier m = e.modifier();
			Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr = e.attribute();
			if (ref.armor() != null && attr.value() == Attributes.ARMOR.value()) {
				out.add(new Entry(e.attribute(), new AttributeModifier(m.id(), ref.armor(), m.operation()), e.slot(), e.display()));
				changed = true;
			} else if (ref.toughness() != null && attr.value() == Attributes.ARMOR_TOUGHNESS.value()) {
				out.add(new Entry(e.attribute(), new AttributeModifier(m.id(), ref.toughness(), m.operation()), e.slot(), e.display()));
				changed = true;
			} else {
				out.add(e);
			}
		}
		return changed ? new ItemAttributeModifiers(List.copyOf(out)) : null;
	}

	private static ItemAttributeModifiers rebuildToolAttributes(ItemAttributeModifiers base, ERConfigData.NormalizedToolStats ref) {
		if (ref.attackDamage() == null && ref.attackSpeed() == null) return null;
		List<Entry> out = new ArrayList<>();
		boolean changed = false;
		for (Entry e : base.modifiers()) {
			AttributeModifier m = e.modifier();
			Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr = e.attribute();
			if (ref.attackDamage() != null && attr.value() == Attributes.ATTACK_DAMAGE.value()) {
				out.add(new Entry(e.attribute(), new AttributeModifier(m.id(), ref.attackDamage(), m.operation()), e.slot(), e.display()));
				changed = true;
			} else if (ref.attackSpeed() != null && attr.value() == Attributes.ATTACK_SPEED.value()) {
				out.add(new Entry(e.attribute(), new AttributeModifier(m.id(), ref.attackSpeed(), m.operation()), e.slot(), e.display()));
				changed = true;
			} else {
				out.add(e);
			}
		}
		return changed ? new ItemAttributeModifiers(List.copyOf(out)) : null;
	}

	/** Replaces mining speeds (rule speeds and the default) with the reference value. */
	private static Tool rebuildTool(Tool tool, ERConfigData.NormalizedToolStats ref) {
		if (tool == null || ref.miningSpeed() == null) return null;
		List<Rule> out = new ArrayList<>();
		for (Rule r : tool.rules()) {
			Optional<Float> speed = r.speed();
			if (speed.isPresent()) {
				out.add(new Rule(r.blocks(), Optional.of(ref.miningSpeed()), r.correctForDrops()));
			} else {
				out.add(r);
			}
		}
		return new Tool(out, ref.miningSpeed(), tool.damagePerBlock(), tool.canDestroyBlocksInCreative());
	}
}
