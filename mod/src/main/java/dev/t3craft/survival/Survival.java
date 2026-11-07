package dev.t3craft.survival;

import dev.t3craft.T3Craft;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Where the live {@link SurvivalRules} is published (the package's only static state; typed to the
 * interface so a hot reload never finds a stale concrete class behind it). Always non-null: before
 * {@link #reload()} it is the default policy.
 */
public final class Survival {
	private static volatile SurvivalRules rules = new GameModePolicy(SurvivalSettings.DEFAULTS);

	private Survival() {
	}

	/** The policy to consult. Thread-safe, never null. */
	public static SurvivalRules rules() {
		return rules;
	}

	/** Replaces the policy (tests, or another feature wrapping it). Null restores the defaults. */
	public static void install(SurvivalRules r) {
		rules = r == null ? new GameModePolicy(SurvivalSettings.DEFAULTS) : r;
	}

	public static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("t3craft.json");
	}

	/** Re-reads the {@code survival} section of {@code config/t3craft.json}; returns the warnings (key names only). */
	public static List<String> reload() {
		SurvivalConfig.Result r = SurvivalConfig.load(configFile());
		install(new GameModePolicy(r.settings()));
		if (!r.warnings().isEmpty()) {
			T3Craft.LOGGER.warn("[survival] invalid config keys, using defaults for: {}", r.warnings());
		}
		T3Craft.LOGGER.info("[survival] {}", r.settings());
		return r.warnings();
	}
}
