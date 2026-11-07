package dev.t3craft;

import dev.t3craft.portable.PortableItems;
import dev.t3craft.survival.SurvivalHooks;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. T3Craft is client-only (never loaded by dedicated servers); the only common
 * registration is the portable T3 Terminal item, which the integrated server needs for its recipe.
 */
public final class T3Craft implements ModInitializer {
	public static final String MOD_ID = "t3craft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PortableItems.register();
		SurvivalHooks.register();
		LOGGER.info("T3Craft loaded (client-only mod; common side: portable T3 Terminal item)");
	}
}
