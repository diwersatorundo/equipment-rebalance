package dev.diwer.equipment_rebalance.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Root of the in-game configuration screen (Mod Menu). */
public class ERConfigScreen extends Screen {
	private final Screen parent;

	public ERConfigScreen(Screen parent) {
		super(Component.translatable("equipment_rebalance.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int w = 220;
		int h = 20;
		int y = Math.max(36, this.height / 2 - 82);

		Button title = this.addRenderableWidget(Button.builder(
				Component.translatable("equipment_rebalance.config.title"), b -> {
				}).bounds(cx - w / 2, y, w, h).build());
		title.active = false;
		y += 28;

		this.addRenderableWidget(Button.builder(
				Component.translatable("equipment_rebalance.config.group.tools"),
				b -> Minecraft.getInstance().setScreen(new ProfileScreen(this, "tool_materials",
						Component.translatable("equipment_rebalance.config.group.tools"))))
				.bounds(cx - w / 2, y, w, h).build());
		y += 24;
		this.addRenderableWidget(Button.builder(
				Component.translatable("equipment_rebalance.config.group.armor"),
				b -> Minecraft.getInstance().setScreen(new ProfileScreen(this, "armor_materials",
						Component.translatable("equipment_rebalance.config.group.armor"))))
				.bounds(cx - w / 2, y, w, h).build());
		y += 24;
		this.addRenderableWidget(Button.builder(
				Component.translatable("equipment_rebalance.config.group.special"),
				b -> Minecraft.getInstance().setScreen(new ProfileScreen(this, "special_items",
						Component.translatable("equipment_rebalance.config.group.special"))))
				.bounds(cx - w / 2, y, w, h).build());
		y += 30;

		this.addRenderableWidget(Button.builder(
				Component.translatable("equipment_rebalance.config.done"), b -> this.onClose())
				.bounds(cx - w / 2, y, w, h).build());
		y += 28;

		Button note = this.addRenderableWidget(Button.builder(
				Component.translatable("equipment_rebalance.config.server_note"), b -> {
				}).bounds(cx - w / 2, y, w, h).build());
		note.active = false;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(this.parent);
	}
}
