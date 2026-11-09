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
 * "hq": { "style": "cherry" }     // cherry (default) | classic | campus | ring | vertical | ensanche (own layouts)
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
	 * Styles with their own layout ({@link HqStyle#ownLayout()}): selectable by id like the others,
	 * but not in {@link #all()}, which lists the styles that paint the shared spine geometry.
	 * layout/campus: {@link CampusStyle}; layout/ring: {@link RingStyle}; layout/vertical: {@link VerticalStyle};
	 * layout/blocks: {@link EnsancheStyle}; design/hybrid-ringcampus: {@link RingCampusStyle};
	 * design/hybrid-towncenter: {@link TowncenterStyle};
	 * design/hybrid-terraces: {@link TerraceStyle}.
	 */
	private static final List<HqStyle> LAYOUTS = List.of(new CampusStyle(), new RingStyle(), new VerticalStyle(), new EnsancheStyle(),
			new VillageStyle(), new RingCampusStyle(), new TowncenterStyle(), new TerraceStyle());
	private static volatile @Nullable HqStyle active;

	private HqStyles() {
	}

	/** Every style that paints the shared spine geometry, default first. */
	public static List<HqStyle> all() {
		return ALL;
	}

	/** The styles with their own layout (campus, ring...); not in {@link #all()}. */
	public static List<HqStyle> layouts() {
		return LAYOUTS;
	}

	/** Styles with their own ground plan as a {@link TownLayout} (layout/blocks: {@code ensanche}). */
	public static List<HqStyle> towns() {
		return LAYOUTS.stream().filter(s -> s instanceof EnsancheStyle).toList();
	}

	/** Ids of every selectable style ({@link #all()} then {@link #layouts()}). */
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

	/**
	 * The style that draws layout {@code layoutId} ({@link dev.t3craft.hq.layout.HqLayouts}): for
	 * {@code classic}, the configured spine style ({@link #active()}, cherry by default, exactly as
	 * before layouts existed; a configured own-layout style falls back to the default); for every
	 * other layout its own style (cherry palette plus the layout's own materials). Unknown id: classic.
	 */
	public static HqStyle forLayout(@Nullable String layoutId) {
		String id = dev.t3craft.hq.layout.HqLayouts.resolve(layoutId).id();
		if (id.equals(dev.t3craft.hq.layout.HqLayouts.CLASSIC)) {
			HqStyle s = active();
			return s.ownLayout() ? defaultStyle() : s;
		}
		HqStyle s = byId(id);
		if (s == null) {
			throw new IllegalStateException("no style for layout " + id);
		}
		return s;
	}

	/** {@link #forLayout} of the active layout ({@link dev.t3craft.hq.layout.HqLayouts#active()}): what plans are generated with. */
	public static HqStyle current() {
		return forLayout(dev.t3craft.hq.layout.HqLayouts.active().id());
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
		if (style != null) {
			return new Selection(style, List.of());
		}
		// a removed or unknown style: the warning names the old id so reload() logs exactly one line with it
		String old = e.isJsonPrimitive() ? e.getAsString() : e.toString();
		return new Selection(defaultStyle(), List.of(SECTION + "." + KEY + " '" + old + "'"));
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
			T3Craft.LOGGER.warn("[hq] invalid or removed config values, using defaults for: {} (known styles: {})", sel.warnings(), ids());
		}
		T3Craft.LOGGER.info("[hq] style {}", sel.style().id());
		return sel.warnings();
	}
}
