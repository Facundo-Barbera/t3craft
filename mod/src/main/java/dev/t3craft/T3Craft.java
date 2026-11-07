package dev.t3craft;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint. T3Craft is client-only; this exists so the mod id and logger live in one place. */
public final class T3Craft implements ModInitializer {
	public static final String MOD_ID = "t3craft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("T3Craft loaded (client-only mod; nothing registered on the common side)");
	}
}
