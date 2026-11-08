package dev.t3craft.hq.layout;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The registry of HQ layouts (the ground plan the HQ is built with). Every layout is painted with the
 * same visual style (cherry), plus the per-barrio / per-tower materials the layout brings.
 *
 * <ul>
 *   <li>{@code classic}: the original hub + spine + wings ({@link #DEFAULT_ID});</li>
 *   <li>{@code campus}: hexagonal barrios round a plaza (very large);</li>
 *   <li>{@code ring}: rings of sectors with radial avenues;</li>
 *   <li>{@code vertical}: towers with an atrium and skybridges;</li>
 *   <li>{@code ensanche}: blocks with patios on a street grid;</li>
 *   <li>{@code village}: a hill village on terraces;</li>
 *   <li>{@code hybrid-towncenter}: town centre with towers (ensanche blocks, civic plaza, towers and skybridges).</li>
 * </ul>
 *
 * <p>The choice lives in {@code config/t3craft.json} as the top-level key {@value #CONFIG_KEY}
 * (default {@code "classic"}, additive). An HQ that is already built keeps the layout it was built
 * with until an explicit reset + rebuild ({@code dev.t3craft.client.hq.layout.HqRebuild}).
 */
public final class HqLayouts {
	/** The layout of main before layouts existed: hub + spine + wings, cherry style. */
	public static final String DEFAULT_ID = "classic";
	/** Top-level key of {@code config/t3craft.json}. */
	public static final String CONFIG_KEY = "hqLayout";

	public static final String CLASSIC = "classic";
	public static final String CAMPUS = "campus";
	public static final String RING = "ring";
	public static final String VERTICAL = "vertical";
	public static final String ENSANCHE = "ensanche";
	public static final String VILLAGE = "village";
	public static final String HYBRID_TOWNCENTER = "hybrid-towncenter";

	private static final List<HqLayoutInfo> ALL = List.of(
			info(CLASSIC, false),
			info(CAMPUS, true),
			info(RING, false),
			info(VERTICAL, false),
			info(ENSANCHE, false),
			info(VILLAGE, false),
			info(HYBRID_TOWNCENTER, false));

	private HqLayouts() {
	}

	private static HqLayoutInfo info(String id, boolean large) {
		return new HqLayoutInfo(id, "t3craft.hq.layout." + id + ".name", "t3craft.hq.layout." + id + ".desc", large);
	}

	/** Every layout, in menu order: classic, campus, ring, vertical, ensanche, village, hybrid-towncenter. */
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

	/** The layout with this id; an unknown id or null gives {@link #DEFAULT_ID}. */
	public static HqLayoutInfo resolve(@Nullable String id) {
		return find(id).orElse(ALL.get(0));
	}

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
