package dev.t3craft.client.t3ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.t3craft.client.config.ConfigManager;
import org.jspecify.annotations.Nullable;

/**
 * The {@code layout} block of {@code config/t3craft.json} (#39): the main T3 screen's panel widths and right
 * panel, remembered across restarts. Top-level and additive: the config codec keeps it like any unknown key
 * ({@link ConfigManager#extra} / {@link ConfigManager#putExtra}, as {@code hqLayout} does), so
 * {@code client/config} needs no change.
 *
 * <pre>{@code
 * "layout": {
 *   "sidebarWidth": 180,      // px, 0 = default (27 % of the frame); 120..360
 *   "rightWidth": 300,        // px, 0 = default (280); at least 180, at most half the frame
 *   "rightOpen": true,
 *   "rightPanel": "pieces",   // a RightPanels id
 *   "maxWidth": 1280,         // optional: frame cap, at least 900 (default 1280)
 *   "maxHeight": 720          // optional: frame cap, at least 520 (default 720)
 * }
 * }</pre>
 *
 * <p>The screen writes the first four when a drag ends or the panel opens or closes (debounced, never per
 * frame), keeping every other key of the block as it is (the caps are only read, so their default can still
 * change). It re-reads the block every second, so {@code /t3craft config reload} or the reload key applies
 * a hand edit to an open screen.
 */
public record T3LayoutPrefs(int sidebarWidth, int rightWidth, boolean rightOpen, @Nullable String rightPanel, int maxWidth, int maxHeight) {
	public static final String CONFIG_KEY = "layout";
	public static final T3LayoutPrefs DEFAULTS = new T3LayoutPrefs(0, 0, false, null, T3UiLayout.MAX_W, T3UiLayout.MAX_H);
	/** Largest width or cap read from the file (anything larger is a typo). */
	private static final int SANE_MAX = 8192;

	/** The block as last loaded or saved, or null (absent, or the config cannot be read). */
	public static @Nullable JsonElement raw() {
		try {
			return ConfigManager.get().extra(CONFIG_KEY);
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

	public static T3LayoutPrefs load() {
		return parse(raw());
	}

	/** Lenient: a missing or wrong key falls back to its default; widths are clamped to what the layout accepts. */
	public static T3LayoutPrefs parse(@Nullable JsonElement e) {
		if (e == null || !e.isJsonObject()) {
			return DEFAULTS;
		}
		JsonObject o = e.getAsJsonObject();
		int sw = num(o, "sidebarWidth", 0);
		sw = sw <= 0 ? 0 : T3UiLayout.clampSidebar(sw);
		int rw = num(o, "rightWidth", 0);
		rw = rw <= 0 ? 0 : Math.max(T3UiLayout.RIGHT_MIN, Math.min(SANE_MAX, rw));
		boolean open = bool(o, "rightOpen", false);
		String panel = str(o, "rightPanel");
		int mw = Math.max(T3UiLayout.MAX_W_MIN, Math.min(SANE_MAX, num(o, "maxWidth", T3UiLayout.MAX_W)));
		int mh = Math.max(T3UiLayout.MAX_H_MIN, Math.min(SANE_MAX, num(o, "maxHeight", T3UiLayout.MAX_H)));
		return new T3LayoutPrefs(sw, rw, open, panel, mw, mh);
	}

	/**
	 * Writes the player's four keys into the block (other keys kept) and saves the file. Returns the block as
	 * written, or null when the config could not be reached.
	 */
	public static @Nullable JsonObject save(int sidebarWidth, int rightWidth, boolean rightOpen, @Nullable String rightPanel) {
		try {
			JsonElement before = ConfigManager.get().extra(CONFIG_KEY);
			JsonObject o = before != null && before.isJsonObject() ? before.getAsJsonObject().deepCopy() : new JsonObject();
			o.add("sidebarWidth", new JsonPrimitive(Math.max(0, sidebarWidth)));
			o.add("rightWidth", new JsonPrimitive(Math.max(0, rightWidth)));
			o.add("rightOpen", new JsonPrimitive(rightOpen));
			if (rightPanel != null && !rightPanel.isBlank()) {
				o.add("rightPanel", new JsonPrimitive(rightPanel));
			}
			ConfigManager.SaveResult r = ConfigManager.get().putExtra(CONFIG_KEY, o);
			if (!r.ok()) {
				dev.t3craft.T3Craft.LOGGER.warn("[t3ui] layout not saved: {}", r.message());
			}
			return o;
		} catch (RuntimeException | LinkageError e) {
			dev.t3craft.T3Craft.LOGGER.warn("[t3ui] layout not saved: {}", e.toString());
			return null;
		}
	}

	private static int num(JsonObject o, String k, int dflt) {
		JsonElement e = o.get(k);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
			return dflt;
		}
		double d = e.getAsDouble();
		return Double.isFinite(d) ? (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(d))) : dflt;
	}

	private static boolean bool(JsonObject o, String k, boolean dflt) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : dflt;
	}

	private static @Nullable String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
			return null;
		}
		String s = e.getAsString().strip();
		return s.isEmpty() ? null : s;
	}
}
