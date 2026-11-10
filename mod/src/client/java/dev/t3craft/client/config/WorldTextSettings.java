package dev.t3craft.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.client.npc.LabelConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The "World text" page of Settings (docs/features/world-text.md): the {@code labels} keys that {@link LabelConfig} reads,
 * as an editable draft. {@link #current()} reads them as they are now, {@link #save()} writes them back into the
 * {@code labels} block of {@code config/t3craft.json} (a block owned outside {@link T3CraftSettings}, like
 * {@code hqLayout}), keeping every other key of the block ({@code mode}, {@code detailDistance}, {@code titleMax}, anything
 * unknown). {@link LabelConfig} polls the file, so a save takes effect in about two seconds, no restart.
 *
 * @param style      {@link #HOLOGRAM} or {@link #CLASSIC}
 * @param nearDistance blocks, 0 to {@link LabelConfig#NEAR_MAX}; 0 = any distance
 * @param maxVisible plates shown at once, 0 to {@link LabelConfig#COUNT_MAX}; 0 = no cap
 * @param maxPanels  detail panels open at once, 0 to {@link LabelConfig#COUNT_MAX}; 0 = no cap
 */
public record WorldTextSettings(String style, double nearDistance, int maxVisible, int maxPanels, boolean declutter, boolean npcTags,
		boolean workstationScreens, boolean hubHologram, boolean scheduleBoard) {
	public static final String HOLOGRAM = "hologram";
	public static final String CLASSIC = "classic";
	public static final List<String> STYLES = List.of(HOLOGRAM, CLASSIC);
	/** What {@link LabelConfig} uses with no {@code labels} block: the "Reset to defaults" button. */
	public static final WorldTextSettings DEFAULTS = of(LabelConfig.DEFAULTS);

	public static WorldTextSettings of(LabelConfig c) {
		LabelConfig.Sources s = c.sources();
		return new WorldTextSettings(c.hologramStyle() ? HOLOGRAM : CLASSIC, c.nearDistance(), c.maxVisible(), c.maxPanels(), c.declutter(),
				s.npcTags(), s.workstationScreens(), s.hubHologram(), s.scheduleBoard());
	}

	/** The values in {@code config/t3craft.json} right now (defaults for what is missing or bad). */
	public static WorldTextSettings current() {
		return of(LabelConfig.load(ConfigManager.get().file()));
	}

	public WorldTextSettings withStyle(String v) {
		return new WorldTextSettings(v, nearDistance, maxVisible, maxPanels, declutter, npcTags, workstationScreens, hubHologram, scheduleBoard);
	}

	public WorldTextSettings withDeclutter(boolean v) {
		return new WorldTextSettings(style, nearDistance, maxVisible, maxPanels, v, npcTags, workstationScreens, hubHologram, scheduleBoard);
	}

	public WorldTextSettings withNpcTags(boolean v) {
		return new WorldTextSettings(style, nearDistance, maxVisible, maxPanels, declutter, v, workstationScreens, hubHologram, scheduleBoard);
	}

	public WorldTextSettings withWorkstationScreens(boolean v) {
		return new WorldTextSettings(style, nearDistance, maxVisible, maxPanels, declutter, npcTags, v, hubHologram, scheduleBoard);
	}

	public WorldTextSettings withHubHologram(boolean v) {
		return new WorldTextSettings(style, nearDistance, maxVisible, maxPanels, declutter, npcTags, workstationScreens, v, scheduleBoard);
	}

	public WorldTextSettings withScheduleBoard(boolean v) {
		return new WorldTextSettings(style, nearDistance, maxVisible, maxPanels, declutter, npcTags, workstationScreens, hubHologram, v);
	}

	/**
	 * These settings with the three number fields of the page applied.
	 *
	 * @throws IllegalArgumentException with a message for the status line when a field is not valid
	 */
	public WorldTextSettings withNumbers(String near, String visible, String panels) {
		Double n = parseNumber(near);
		if (n == null || !(n >= 0 && n <= LabelConfig.NEAR_MAX)) {
			throw new IllegalArgumentException("Plate distance must be a number of blocks, 0 to " + (int) LabelConfig.NEAR_MAX);
		}
		Integer v = parseCount(visible);
		if (v == null) {
			throw new IllegalArgumentException("Plates at once must be a whole number, 0 to " + LabelConfig.COUNT_MAX);
		}
		Integer p = parseCount(panels);
		if (p == null) {
			throw new IllegalArgumentException("Detail panels at once must be a whole number, 0 to " + LabelConfig.COUNT_MAX);
		}
		return new WorldTextSettings(style, n, v, p, declutter, npcTags, workstationScreens, hubHologram, scheduleBoard);
	}

	private static @Nullable Double parseNumber(String raw) {
		try {
			double v = Double.parseDouble(raw.trim().replace(',', '.'));
			return Double.isFinite(v) ? v : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static @Nullable Integer parseCount(String raw) {
		try {
			int v = Integer.parseInt(raw.trim());
			return v >= 0 && v <= LabelConfig.COUNT_MAX ? v : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * The {@code labels} block with these values over {@code base} (a copy of it; null = a new block): keys this page does
	 * not own stay, and a {@code "hologram": false} shorthand becomes the four explicit switches.
	 */
	public JsonObject toJson(@Nullable JsonElement base) {
		JsonObject o = base != null && base.isJsonObject() ? base.getAsJsonObject().deepCopy() : new JsonObject();
		o.addProperty("style", style);
		if (nearDistance == Math.rint(nearDistance)) {
			o.addProperty("nearDistance", (long) nearDistance);
		} else {
			o.addProperty("nearDistance", nearDistance);
		}
		o.addProperty("maxVisible", maxVisible);
		o.addProperty("maxPanels", maxPanels);
		o.addProperty("declutter", declutter);
		JsonObject h = o.get("hologram") instanceof JsonObject existing ? existing : new JsonObject();
		h.addProperty("npcTags", npcTags);
		h.addProperty("workstationScreens", workstationScreens);
		h.addProperty("hubHologram", hubHologram);
		h.addProperty("scheduleBoard", scheduleBoard);
		o.add("hologram", h);
		return o;
	}

	/** Writes these values into the {@code labels} block of {@code config/t3craft.json}. */
	public ConfigManager.SaveResult save() {
		return ConfigManager.get().putExtra(LabelConfig.KEY, toJson(onDisk()));
	}

	/**
	 * The {@code labels} block as the file holds it now, so a key edited by hand since the last load survives the save;
	 * else the block as last loaded; else null.
	 */
	private static @Nullable JsonElement onDisk() {
		Path f = ConfigManager.get().file();
		try {
			if (Files.isRegularFile(f)) {
				JsonElement root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
				if (root.isJsonObject() && root.getAsJsonObject().get(LabelConfig.KEY) != null) {
					return root.getAsJsonObject().get(LabelConfig.KEY);
				}
			}
		} catch (IOException | RuntimeException e) {
			// unreadable right now: fall back to what was loaded
		}
		return ConfigManager.get().extra(LabelConfig.KEY);
	}
}
