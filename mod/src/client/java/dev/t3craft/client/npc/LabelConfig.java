package dev.t3craft.client.npc;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * How the text in the world is laid out, from its own additive block in {@code config/t3craft.json}
 * (docs/features/world-text.md):
 *
 * <pre>{@code
 * "labels": {
 *   "mode": "compact", "detailDistance": 4.0, "titleMax": 24,
 *   "style": "hologram", "nearDistance": 12, "maxVisible": 6, "maxPanels": 1, "declutter": true,
 *   "hologram": { "npcTags": true, "workstationScreens": true, "hubHologram": true, "scheduleBoard": true }
 * }}</pre>
 *
 * {@code compact} (default): one short name tag per NPC, shown only within {@code nearDistance}
 * blocks or while aimed at, at most {@code maxVisible} at once (aimed first, then the nearest), and
 * never overlapping each other ({@code declutter}); the detail panel only appears within
 * {@code detailDistance} blocks, aimed at or left-clicked, and only {@code maxPanels} at a time.
 * {@code detailed}: the old layout (chips on the name tag, panel always on, no caps). {@code
 * nearDistance: 0}, {@code maxVisible: 0}, {@code maxPanels: 0} and {@code declutter: false} bring
 * back the uncapped compact layout. {@code hologram.*} switches each world-text source on or off.
 * Missing block, key or bad value → default. Same polling as {@link ChipsConfig}.
 *
 * @param nearDistance blocks; name tags of NPCs further away hide (0 = any distance)
 * @param maxVisible   name tags shown at once (0 = no cap)
 * @param maxPanels    detail panels open at once (0 = no cap)
 * @param declutter    hide the lower-ranked of two overlapping NPC plates
 * @param sources      which world-text sources are drawn at all
 * @param hologramStyle {@code "style": "hologram"} (default): one pill plate per NPC instead of
 *                     the vanilla tag, speech bubbles only on events (fading out), the hub card
 *                     with a progress ring and the decision podium bubble; {@code "classic"}: the
 *                     look before (vanilla tag, bubble while near, hub text lines, no podium bubble)
 */
public record LabelConfig(boolean compact, double detailDistance, int titleMax, double nearDistance, int maxVisible, int maxPanels,
		boolean declutter, Sources sources, boolean hologramStyle) {
	public static final String KEY = "labels";

	/**
	 * {@code labels.hologram}: one switch per world-text source.
	 *
	 * @param npcTags            NPC name tags / plates and detail panels
	 * @param workstationScreens the floating screens of the desks' computers
	 * @param hubHologram        the card over the hub (also needs {@code hud.hologram})
	 * @param scheduleBoard      the schedule board's text
	 */
	public record Sources(boolean npcTags, boolean workstationScreens, boolean hubHologram, boolean scheduleBoard) {
		public static final Sources ALL = new Sources(true, true, true, true);
	}

	public static final LabelConfig DEFAULTS = new LabelConfig(true, 4.0, 24, 12.0, 6, 1, true, Sources.ALL, true);
	private static final Gson GSON = new Gson();
	private static final long POLL_MS = 2000;

	private static volatile LabelConfig current = DEFAULTS;
	private static @Nullable FileTime stamp;
	private static long lastPoll = Long.MIN_VALUE;

	/** The pre-world-text record (mode, detail distance, title length); the new keys keep their defaults. */
	public LabelConfig(boolean compact, double detailDistance, int titleMax) {
		this(compact, detailDistance, titleMax, DEFAULTS.nearDistance, DEFAULTS.maxVisible, DEFAULTS.maxPanels, DEFAULTS.declutter,
				DEFAULTS.sources, DEFAULTS.hologramStyle);
	}

	/** Pill plates, event bubbles and the podium bubble: hologram style in compact mode (detailed is the old layout). */
	public boolean pills() {
		return hologramStyle && compact;
	}

	public static LabelConfig parse(@Nullable JsonObject root) {
		if (root == null || !(root.get(KEY) instanceof JsonObject o)) {
			return DEFAULTS;
		}
		boolean compact = DEFAULTS.compact;
		if (o.get("mode") instanceof JsonElement m && m.isJsonPrimitive() && m.getAsJsonPrimitive().isString()) {
			String s = m.getAsString().trim();
			if (s.equalsIgnoreCase("detailed")) {
				compact = false;
			} else if (s.equalsIgnoreCase("compact")) {
				compact = true;
			}
		}
		double dist = num(o, "detailDistance", DEFAULTS.detailDistance);
		if (!(dist >= 0 && dist <= 64)) {
			dist = DEFAULTS.detailDistance;
		}
		int max = (int) num(o, "titleMax", DEFAULTS.titleMax);
		if (max < 4 || max > 128) {
			max = DEFAULTS.titleMax;
		}
		double near = num(o, "nearDistance", DEFAULTS.nearDistance);
		if (!(near >= 0 && near <= 128)) {
			near = DEFAULTS.nearDistance;
		}
		int visible = (int) num(o, "maxVisible", DEFAULTS.maxVisible);
		if (visible < 0 || visible > 256) {
			visible = DEFAULTS.maxVisible;
		}
		int panels = (int) num(o, "maxPanels", DEFAULTS.maxPanels);
		if (panels < 0 || panels > 256) {
			panels = DEFAULTS.maxPanels;
		}
		boolean declutter = bool(o, "declutter", DEFAULTS.declutter);
		boolean hologram = DEFAULTS.hologramStyle;
		if (o.get("style") instanceof JsonElement st && st.isJsonPrimitive() && st.getAsJsonPrimitive().isString()) {
			String v = st.getAsString().trim();
			if (v.equalsIgnoreCase("classic")) {
				hologram = false;
			} else if (v.equalsIgnoreCase("hologram")) {
				hologram = true;
			}
		}
		Sources src = DEFAULTS.sources;
		if (o.get("hologram") instanceof JsonObject h) {
			src = new Sources(bool(h, "npcTags", src.npcTags()), bool(h, "workstationScreens", src.workstationScreens()),
					bool(h, "hubHologram", src.hubHologram()), bool(h, "scheduleBoard", src.scheduleBoard()));
		} else if (o.get("hologram") instanceof JsonElement hb && hb.isJsonPrimitive() && hb.getAsJsonPrimitive().isBoolean()) {
			boolean all = hb.getAsBoolean(); // "hologram": false switches every source off at once
			src = new Sources(all, all, all, all);
		}
		return new LabelConfig(compact, dist, max, near, visible, panels, declutter, src, hologram);
	}

	/** Missing or unreadable file → defaults (never throws). */
	public static LabelConfig load(Path file) {
		try {
			if (!Files.exists(file)) {
				return DEFAULTS;
			}
			return parse(GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class));
		} catch (IOException | JsonParseException | IllegalStateException e) {
			return DEFAULTS;
		}
	}

	/** The live config, re-read from {@code config/t3craft.json} when its mtime changes (polled ≤ every 2 s). */
	public static LabelConfig current() {
		long now = System.currentTimeMillis();
		if (now - lastPoll >= POLL_MS || lastPoll == Long.MIN_VALUE) {
			lastPoll = now;
			try {
				Path f = FabricLoader.getInstance().getConfigDir().resolve("t3craft.json");
				FileTime t = Files.exists(f) ? Files.getLastModifiedTime(f) : null;
				if (!Objects.equals(t, stamp)) {
					stamp = t;
					current = load(f);
				}
			} catch (IOException | RuntimeException e) {
				// keep the last known config
			}
		}
		return current;
	}

	private static double num(JsonObject o, String key, double def) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : def;
	}

	private static boolean bool(JsonObject o, String key, boolean def) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : def;
	}
}
