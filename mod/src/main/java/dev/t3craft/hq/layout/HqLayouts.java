package dev.t3craft.hq.layout;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * The registry of HQ layouts (the ground plan the HQ is built with). Every layout is painted with the
 * same visual style (cherry), plus the per-barrio / per-tower materials the layout brings.
 *
 * <ul>
 *   <li>{@code classic}: the original hub + spine + wings (what the survival site builds);</li>
 *   <li>{@code campus}: hexagonal barrios round a plaza (very large);</li>
 *   <li>{@code ring}: rings of sectors with radial avenues;</li>
 *   <li>{@code vertical}: towers with an atrium and skybridges;</li>
 *   <li>{@code hybrid-ringcampus}: a ringed citadel whose sectors are campus barrios (the default).</li>
 * </ul>
 *
 * <p>The choice lives in {@code config/t3craft.json} as the top-level key {@value #CONFIG_KEY}
 * (default {@code "hybrid-ringcampus"}, additive). An HQ that is already built keeps the layout it was built
 * with until an explicit reset + rebuild ({@code dev.t3craft.client.hq.layout.HqRebuild}).
 */
public final class HqLayouts {
	/** The default layout: the citadel. Also what any removed or unknown layout id falls back to ({@link #resolve}). */
	public static final String DEFAULT_ID = "hybrid-ringcampus";
	/** Top-level key of {@code config/t3craft.json}. */
	public static final String CONFIG_KEY = "hqLayout";

	public static final String CLASSIC = "classic";
	public static final String CAMPUS = "campus";
	public static final String RING = "ring";
	public static final String VERTICAL = "vertical";
	public static final String HYBRID_RINGCAMPUS = "hybrid-ringcampus";
	/**
	 * The main design, listed first in the HQ menu; every other layout is shown under "Legacy".
	 * Presentation only: it is not the {@link #DEFAULT_ID} and the registry order does not change.
	 */
	public static final String PRIMARY_ID = HYBRID_RINGCAMPUS;

	private static final List<HqLayoutInfo> ALL = List.of(
			info(CLASSIC, false),
			info(CAMPUS, true),
			info(RING, false),
			info(VERTICAL, false),
			info(HYBRID_RINGCAMPUS, false));

	private HqLayouts() {
	}

	private static HqLayoutInfo info(String id, boolean large) {
		return new HqLayoutInfo(id, "t3craft.hq.layout." + id + ".name", "t3craft.hq.layout." + id + ".desc", large,
				id.equals(PRIMARY_ID));
	}

	/**
	 * Every layout in HQ-menu order: the primary ones first ({@link #PRIMARY_ID}), then the legacy
	 * ones in registry order. Only for display; {@link #all()} keeps the stable registry order.
	 */
	public static List<HqLayoutInfo> menuOrder() {
		List<HqLayoutInfo> out = new java.util.ArrayList<>(ALL.size());
		for (HqLayoutInfo i : ALL) {
			if (i.primary()) {
				out.add(i);
			}
		}
		for (HqLayoutInfo i : ALL) {
			if (!i.primary()) {
				out.add(i);
			}
		}
		return List.copyOf(out);
	}

	/** Every layout, in menu order: classic, campus, ring, vertical, hybrid-ringcampus. */
	public static List<HqLayoutInfo> all() {
		return ALL;
	}

	/** The layout with this id (trimmed, case-insensitive), if any. */
	public static Optional<HqLayoutInfo> find(@Nullable String id) {
		if (id == null) {
			return Optional.empty();
		}
		String k = id.trim().toLowerCase(Locale.ROOT);
		for (HqLayoutInfo i : ALL) {
			if (i.id().equals(k)) {
				return Optional.of(i);
			}
		}
		return Optional.empty();
	}

	/**
	 * The layout with this id; null or blank gives {@link #DEFAULT_ID} silently. An id that is not
	 * registered (a layout that was removed, or a typo) also gives {@link #DEFAULT_ID}, with one WARN
	 * line per distinct old id and session. Nothing else is touched: no crash, no data loss.
	 */
	public static HqLayoutInfo resolve(@Nullable String id) {
		Optional<HqLayoutInfo> found = find(id);
		if (found.isPresent()) {
			return found.get();
		}
		if (id != null && !id.isBlank() && WARNED.add(id.trim().toLowerCase(Locale.ROOT))) {
			T3Craft.LOGGER.warn("[hq] layout '{}' no longer exists; using the default layout '{}'", id.trim(), DEFAULT_ID);
		}
		return find(DEFAULT_ID).orElseThrow(() -> new IllegalStateException("default layout " + DEFAULT_ID + " is not registered"));
	}

	/** Old ids already reported by {@link #resolve} (lower case), so each is logged once. */
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	// ------------------------------------------------------------------ config

	/** {@value #CONFIG_KEY} of a whole {@code t3craft.json} object (null or invalid: the default). */
	public static HqLayoutInfo fromConfig(@Nullable JsonObject root) {
		if (root == null || !root.has(CONFIG_KEY)) {
			return resolve(null);
		}
		JsonElement e = root.get(CONFIG_KEY);
		return resolve(e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null);
	}

	/** {@link #fromConfig} of a file (absent or unreadable: the default). */
	public static HqLayoutInfo fromConfig(@Nullable Path file) {
		if (file == null || !Files.isRegularFile(file)) {
			return resolve(null);
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file));
			return fromConfig(root.isJsonObject() ? root.getAsJsonObject() : null);
		} catch (java.io.IOException | RuntimeException e) {
			return resolve(null);
		}
	}

	// ------------------------------------------------------------------ active layout

	private static volatile @Nullable HqLayoutInfo active;

	/**
	 * The layout HQ plans are generated with right now ({@link #DEFAULT_ID} until set). The HQ world
	 * sets it to the layout it is built with (or, before the first build, the configured one), so the
	 * generator, footprints, builders, NPC anchors and validators all agree.
	 */
	public static HqLayoutInfo active() {
		HqLayoutInfo a = active;
		return a == null ? resolve(null) : a;
	}

	/** Sets the active layout ({@link #resolve} rules; null: back to the default). */
	public static void use(@Nullable String id) {
		active = id == null ? null : resolve(id);
	}
}
