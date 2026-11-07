package dev.t3craft.blocks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Common entrypoint of the blocks module (own entry in {@code fabric.mod.json}). Reads the switch
 * ({@code -Dt3craft.blocks}, then {@code "blocks": {"enabled"}} in {@code config/t3craft.json}) and
 * calls {@link T3Blocks#init} once. With the switch off this registers nothing, and the rest of the
 * mod runs exactly as without the module.
 */
public final class BlocksInit implements ModInitializer {
	@Override
	public void onInitialize() {
		BlocksSwitch sw = BlocksSwitch.load(FabricLoader.getInstance().getConfigDir().resolve("t3craft.json"));
		T3Blocks.init(sw, BlockRegistrar.VANILLA);
	}
}
