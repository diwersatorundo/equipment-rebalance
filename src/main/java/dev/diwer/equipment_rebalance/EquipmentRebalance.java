package dev.diwer.equipment_rebalance;

import dev.diwer.equipment_rebalance.config.ERConfig;
import dev.diwer.equipment_rebalance.config.ERConfigData;
import dev.diwer.equipment_rebalance.core.EquipmentNormalizer;
import dev.diwer.equipment_rebalance.core.TableUsesTransfer;
import dev.diwer.equipment_rebalance.sync.JoinSync;
import dev.diwer.equipment_rebalance.sync.ServerSync;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EquipmentRebalance implements ModInitializer {
	public static final String MOD_ID = "equipment_rebalance";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final String CORE_VERSION = "0.1";

	@Override
	public void onInitialize() {
		LOGGER.info("Equipment Rebalance v{} initializing (MC 26.3, loader 0.20.1)", CORE_VERSION);
		ServerSync.register();
		JoinSync.register();
		TableUsesTransfer.register();
		reloadConfig();
	}

	/** Loads config from disk; safe to call repeatedly (e.g. on client reload). */
	public static void reloadConfig() {
		ERConfig.get().reload();
		EquipmentNormalizer.onConfigReload();
		LOGGER.info("Equipment Rebalance config reloaded");
	}
}
