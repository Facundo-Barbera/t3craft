package dev.t3craft.hq;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The registry of {@link HqStyle}s and the active one. The active style comes from the {@code "hq"}
 * section of {@code config/t3craft.json} (read-only here; the client's ConfigStore keeps unknown
 * sections on save, so this key survives without this class ever writing):
 * <pre>{@code
 * "hq": { "style": "cherry" }     // cherry (default) | classic | vertical (own layout)
 * }</pre>
 * Absent file, section or key: {@link #DEFAULT_ID}. Unknown id: the default plus a warning.
 */
public final class HqStyles {
	/** Config section and key. */
	public static final String SECTION = "hq";
	public static final String KEY = "style";
	/** The default style: "Campus de cerezo" (docs/research/hq-styles.md §C). */
	public static final String DEFAULT_ID = CherryStyle.ID;

	private static final List<HqStyle> ALL = List.of(new CherryStyle(), new ClassicStyle());
	/**
	 * Styles with their own layout (layout/vertical): selectable by id like the others, but not in
	 * {@link #all()}, whose styles share one geometry.
	 */
	private static final List<HqStyle> LAYOUTS = List.of(new VerticalStyle());
	private static volatile @Nullable HqStyle active;

	private HqStyles() {
	}

	/** Every style, default first. */
	public static List<HqStyle> all() {
		return ALL;
	}

	public static List<String> ids() {
		List<String> out = new ArrayList<>();
		for (HqStyle s : ALL) {
			out.add(s.id());
		}
		for (HqStyle s : LAYOUTS) {
			out.add(s.id());
		}
		return out;
	}

	/** Styles that bring their own layout instead of the shared wing grid ({@code vertical}). */
	public static List<HqStyle> layouts() {
		return LAYOUTS;
	}

	/** The style with this id (case-insensitive, trimmed), or null. */
	public static @Nullable HqStyle byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		String k = id.trim().toLowerCase(Locale.ROOT);
		for (HqStyle s : ALL) {
			if (s.id().equals(k)) {
				return s;
			}
		}
		for (HqStyle s : LAYOUTS) {
			if (s.id().equals(k)) {
				return s;
			}
		}
		return null;
	}

	public static HqStyle defaultStyle() {
		return byId(DEFAULT_ID);
	}

	public static HqStyle classic() {
		return byId(ClassicStyle.ID);
	}

	/** The style plans are generated with ({@link #DEFAULT_ID} until the config says otherwise). */
	public static HqStyle active() {
		HqStyle s = active;
		return s == null ? defaultStyle() : s;
	}

	/** Sets the active style (null: back to the default). */
	public static void use(@Nullable HqStyle style) {
		active = style;
	}

	/** Result of reading the config: the style and the warnings (key names only). */
	public record Selection(HqStyle style, List<String> warnings) {
	}

	/** @param root the whole t3craft.json object (or null) */
	public static Selection parse(@Nullable JsonObject root) {
		if (root == null || !root.has(SECTION)) {
			return new Selection(defaultStyle(), List.of());
		}
		if (!root.get(SECTION).isJsonObject()) {
			return new Selection(defaultStyle(), List.of(SECTION));
		}
		JsonObject s = root.getAsJsonObject(SECTION);
		if (!s.has(KEY) || s.get(KEY).isJsonNull()) {
			return new Selection(defaultStyle(), List.of());
		}
		JsonElement e = s.get(KEY);
		HqStyle style = e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? byId(e.getAsString()) : null;
		return style == null ? new Selection(defaultStyle(), List.of(SECTION + "." + KEY)) : new Selection(style, List.of());
	}

	public static Selection load(@Nullable Path file) {
		if (file == null || !Files.isRegularFile(file)) {
			return new Selection(defaultStyle(), List.of());
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file));
			return parse(root.isJsonObject() ? root.getAsJsonObject() : null);
		} catch (IOException | RuntimeException e) {
			return new Selection(defaultStyle(), List.of(SECTION + " (unreadable config file)"));
		}
	}

	/** Re-reads {@code hq.style} from {@code config/t3craft.json} and makes it active; returns the warnings. */
	public static List<String> reload() {
		Selection sel = load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("t3craft.json"));
		use(sel.style());
		if (!sel.warnings().isEmpty()) {
			T3Craft.LOGGER.warn("[hq] invalid config keys, using defaults for: {} (known styles: {})", sel.warnings(), ids());
		}
		T3Craft.LOGGER.info("[hq] style {}", sel.style().id());
		return sel.warnings();
	}
}
