package dev.diwer.equipment_rebalance.client.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.renderState.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenInvokerMixin {

	@Invoker(method = "addRenderableWidget")
	public <T extends GuiEventListener & Renderable & NarratableEntry> T equipment_rebalance$addRenderableWidgetInv(T widget);
}
