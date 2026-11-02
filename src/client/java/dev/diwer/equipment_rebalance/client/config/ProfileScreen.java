package dev.diwer.equipment_rebalance.client.config;

import com.google.gson.JsonObject;
import dev.diwer.equipment_rebalance.config.ERConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Edits one profile group (tools / armor / special items): a material selector,
 * per-material slots / max level / power, and an "apply to all materials" action.
 */
public class ProfileScreen extends Screen {
	private static final int MIN_SLOTS = 0, MAX_SLOTS = 32;
	private static final int MIN_LEVEL = 1, MAX_LEVEL = 100;
	private static final int MIN_POWER = 0, MAX_POWER = 100000;

	private final Screen parent;
	private final String groupKey;
	private final Component groupTitle;
	private final JsonObject profiles;
	private final JsonObject group;
	private final List<String> materials = new ArrayList<>();
	private int selected;

	private Button materialButton;
	private Button slotsButton;
	private Button maxLevelButton;
	private Button powerButton;
	private Button applyButton;
	private Button saveButton;

	public ProfileScreen(Screen parent, String groupKey, Component groupTitle) {
		super(Component.translatable("equipment_rebalance.config.title"));
		this.parent = parent;
		this.groupKey = groupKey;
		this.groupTitle = groupTitle;
		JsonObject raw = ERConfig.get().rawProfiles();
		this.profiles = raw == null ? new JsonObject() : raw.deepCopy();
		JsonObject g = this.profiles.has(groupKey) && this.profiles.get(groupKey).isJsonObject()
				? this.profiles.getAsJsonObject(groupKey) : new JsonObject();
		this.profiles.add(groupKey, g);
		this.group = g;
		for (String key : g.keySet()) {
			if (g.get(key).isJsonObject()) this.materials.add(key);
		}
		Collections.sort(this.materials);
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int w = 240;
		int h = 20;
		int y = Math.max(24, this.height / 2 - 96);

		Button title = this.addRenderableWidget(Button.builder(this.groupTitle, b -> {
		}).bounds(cx - w / 2, y, w, h).build());
		title.active = false;
		y += 26;

		this.materialButton = this.addRenderableWidget(Button.builder(materialLabel(), b -> {
			if (this.materials.isEmpty()) return;
			this.selected = (this.selected + 1) % this.materials.size();
			this.refresh();
		}).bounds(cx - w / 2, y, w, h).build());
		y += 24;

		this.addRenderableWidget(Button.builder(Component.literal("-"),
				b -> this.adjust("slots", -1)).bounds(cx - w / 2, y, 30, h).build());
		this.slotsButton = this.addRenderableWidget(Button.builder(slotsLabel(), b -> {
		}).bounds(cx - w / 2 + 32, y, w - 64, h).build());
		this.slotsButton.active = false;
		this.addRenderableWidget(Button.builder(Component.literal("+"),
				b -> this.adjust("slots", 1)).bounds(cx + w / 2 - 30, y, 30, h).build());
		y += 24;

		this.addRenderableWidget(Button.builder(Component.literal("-"),
				b -> this.adjust("max_level", -1)).bounds(cx - w / 2, y, 30, h).build());
		this.maxLevelButton = this.addRenderableWidget(Button.builder(levelLabel(), b -> {
		}).bounds(cx - w / 2 + 32, y, w - 64, h).build());
		this.maxLevelButton.active = false;
		this.addRenderableWidget(Button.builder(Component.literal("+"),
				b -> this.adjust("max_level", 1)).bounds(cx + w / 2 - 30, y, 30, h).build());
		y += 24;

		this.addRenderableWidget(Button.builder(Component.literal("-10"),
				b -> this.adjust("capacity", -10)).bounds(cx - w / 2, y, 36, h).build());
		this.addRenderableWidget(Button.builder(Component.literal("-1"),
				b -> this.adjust("capacity", -1)).bounds(cx - w / 2 + 38, y, 28, h).build());
		this.powerButton = this.addRenderableWidget(Button.builder(powerLabel(), b -> {
		}).bounds(cx - w / 2 + 68, y, w - 136, h).build());
		this.powerButton.active = false;
		this.addRenderableWidget(Button.builder(Component.literal("+1"),
				b -> this.adjust("capacity", 1)).bounds(cx + w / 2 - 66, y, 28, h).build());
		this.addRenderableWidget(Button.builder(Component.literal("+10"),
				b -> this.adjust("capacity", 10)).bounds(cx + w / 2 - 36, y, 36, h).build());
		y += 30;

		this.applyButton = this.addRenderableWidget(Button.builder(
				Component.translatable("equipment_rebalance.config.apply_all"), b -> this.applyAll())
				.bounds(cx - w / 2, y, w, h).build());
		y += 24;
		this.saveButton = this.addRenderableWidget(Button.builder(
				Component.translatable("equipment_rebalance.config.save"), b -> this.save())
				.bounds(cx - w / 2, y, w, h).build());
		y += 24;
		this.addRenderableWidget(Button.builder(
				Component.translatable("equipment_rebalance.config.back"), b -> this.onClose())
				.bounds(cx - w / 2, y, w, h).build());

		this.refresh();
	}

	private JsonObject current() {
		if (this.materials.isEmpty()) return null;
		String key = this.materials.get(this.selected);
		return this.group.get(key) != null && this.group.get(key).isJsonObject()
				? this.group.getAsJsonObject(key) : null;
	}

	private void adjust(String key, int delta) {
		JsonObject p = this.current();
		if (p == null) return;
		int min = key.equals("slots") ? MIN_SLOTS : key.equals("max_level") ? MIN_LEVEL : MIN_POWER;
		int max = key.equals("slots") ? MAX_SLOTS : key.equals("max_level") ? MAX_LEVEL : MAX_POWER;
		int value = clamp(getInt(p, key, defaultValue(key)) + delta, min, max);
		p.addProperty(key, value);
		this.refresh();
	}

	private void applyAll() {
		JsonObject src = this.current();
		if (src == null) return;
		int slots = getInt(src, "slots", 2);
		int level = getInt(src, "max_level", 3);
		int power = getInt(src, "capacity", 6);
		for (String key : this.materials) {
			JsonObject p = this.group.get(key) != null && this.group.get(key).isJsonObject()
					? this.group.getAsJsonObject(key) : null;
			if (p == null) continue;
			p.addProperty("slots", slots);
			p.addProperty("max_level", level);
			p.addProperty("capacity", power);
		}
		if (this.applyButton != null) {
			this.applyButton.setMessage(Component.translatable("equipment_rebalance.config.applied"));
		}
		this.refresh();
	}

	private void save() {
		ERConfig.get().saveProfiles(this.profiles);
		if (this.saveButton != null) {
			this.saveButton.setMessage(Component.translatable("equipment_rebalance.config.saved"));
		}
		this.refresh();
	}

	private void refresh() {
		if (this.materialButton != null) this.materialButton.setMessage(materialLabel());
		if (this.slotsButton != null) this.slotsButton.setMessage(slotsLabel());
		if (this.maxLevelButton != null) this.maxLevelButton.setMessage(levelLabel());
		if (this.powerButton != null) this.powerButton.setMessage(powerLabel());
	}

	private Component materialLabel() {
		if (this.materials.isEmpty()) {
			return Component.translatable("equipment_rebalance.config.material", Component.literal("-"));
		}
		String key = this.materials.get(this.selected);
		String pretty = key.substring(0, 1).toUpperCase() + key.substring(1).replace('_', ' ');
		return Component.translatable("equipment_rebalance.config.material", Component.literal(pretty));
	}

	private Component slotsLabel() {
		return Component.translatable("equipment_rebalance.config.slots", getInt(this.current(), "slots", 2));
	}

	private Component levelLabel() {
		return Component.translatable("equipment_rebalance.config.max_level", getInt(this.current(), "max_level", 3));
	}

	private Component powerLabel() {
		return Component.translatable("equipment_rebalance.config.capacity", getInt(this.current(), "capacity", 6));
	}

	private static int defaultValue(String key) {
		return switch (key) {
			case "slots" -> 2;
			case "max_level" -> 3;
			default -> 6;
		};
	}

	private static int getInt(JsonObject o, String key, int def) {
		try {
			if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) {
				return o.get(key).getAsInt();
			}
		} catch (Exception ignored) {
		}
		return def;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(this.parent);
	}
}
