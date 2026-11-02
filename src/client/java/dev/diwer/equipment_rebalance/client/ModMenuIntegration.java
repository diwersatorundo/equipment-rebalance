package dev.diwer.equipment_rebalance.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.diwer.equipment_rebalance.client.config.ERConfigScreen;

/** Mod Menu entrypoint; only loaded when Mod Menu is installed. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		ConfigScreenFactory<ERConfigScreen> factory = ERConfigScreen::new;
		return factory;
	}
}
