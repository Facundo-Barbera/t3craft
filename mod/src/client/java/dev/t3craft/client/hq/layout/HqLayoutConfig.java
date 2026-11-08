package dev.t3craft.client.hq.layout;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.t3craft.client.config.ConfigManager;
import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.hq.layout.HqLayouts;

/**
 * The {@code hqLayout} key of {@code config/t3craft.json} (top-level, default {@code "classic"};
 * additive: the config codec keeps it like any unknown key). Choosing a layout here does not touch
 * an HQ that is already built: it applies to the next build of an empty HQ world, or to
 * {@link HqRebuild#resetAndRebuild}.
 */
public final class HqLayoutConfig {
	private HqLayoutConfig() {
	}

	/** The configured layout ({@link HqLayouts#resolve} of {@code hqLayout}). */
	public static HqLayoutInfo selected() {
		JsonElement e = ConfigManager.get().extra(HqLayouts.CONFIG_KEY);
		return HqLayouts.resolve(e != null && e.isJsonPrimitive() ? e.getAsString() : null);
	}

	/** Saves {@code hqLayout} (resolved: unknown ids become the default). */
	public static ConfigManager.SaveResult select(String layoutId) {
		return ConfigManager.get().putExtra(HqLayouts.CONFIG_KEY, new JsonPrimitive(HqLayouts.resolve(layoutId).id()));
	}
}
