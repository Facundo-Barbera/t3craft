package dev.t3craft.hq.pack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * feat/hq-layout-packs: the numbers of a layout a pack may change ({@code "params"} in
 * {@code config/t3craft/hq-layouts/<layoutId>.json}). Generators read them <b>while generating a
 * plan</b> ({@link Spec#get()}), never into a {@code static final}, so a reloaded pack takes effect
 * on the next plan. Every value is clamped to the spec's safe range: anchors, desks and walkable cells
 * never move, only the look changes (decoration density, lamp and pillar spacing, lighting grid).
 * Lighting is recomputed after every plan ({@code HqLighting}), so fewer decorative lamps still leave
 * every walkable cell lit.
 *
 * <p>The full list (also written by the export and documented in docs/feat-hq-layout-packs.md) is
 * {@link #all()}. Geometry that NPC anchors, footprints, survival sites and validators share (hall
 * widths, storeys, lot depths) stays in code: changing it needs a code change and a restart.
 */
public final class LayoutParams {
	/** Layout id of the parameters every layout has. */
	public static final String ANY = "*";

	private static final List<Spec> ALL = new ArrayList<>();

	private LayoutParams() {
	}

	/**
	 * One parameter: {@code layout} ({@link #ANY} for all), its key in the pack, the code default and the
	 * clamp range, and what it changes.
	 */
	public record Spec(String layout, String key, int def, int min, int max, String doc, int unit) {
		/** An integer parameter ({@code unit} 1). */
		public Spec(String layout, String key, int def, int min, int max, String doc) {
			this(layout, key, def, min, max, doc, 1);
		}

		/**
		 * A decimal parameter: the pack writes the real value (e.g. {@code 1.5}), it is kept as
		 * {@code round(value · unit)} and {@link #value} gives it back as a double.
		 */
		public double value() {
			return get() / (double) unit;
		}

		/** The integer a pack number is stored as: {@code round(n · unit)}. */
		public int fromPack(double n) {
			return (int) Math.round(n * unit);
		}

		/** A stored value as the pack writes it (an integer, or a decimal for {@code unit > 1}). */
		public Number toPack(int v) {
			return unit == 1 ? (Number) v : (Number) (v / (double) unit);
		}

		/** The value for the layout being generated (the spec's own layout; for {@link #ANY}, the active one). */
		public int get() {
			return get(layout.equals(ANY) ? HqLayoutPacks.generatingLayout() : layout);
		}

		/** The value for {@code layoutId}: the pack's, clamped, or the default. */
		public int get(String layoutId) {
			LayoutPack p = HqLayoutPacks.pack(layoutId);
			if (p == null) {
				return def;
			}
			Integer v = p.params().get(key);
			return v == null ? def : clamp(v);
		}

		public int clamp(int v) {
			return Math.max(min, Math.min(max, v));
		}

		/** True when this spec applies to {@code layoutId}. */
		public boolean appliesTo(String layoutId) {
			return layout.equals(ANY) || layout.equals(layoutId);
		}
	}

	private static Spec spec(String layout, String key, int def, int min, int max, String doc) {
		return spec(layout, key, def, min, max, doc, 1);
	}

	private static Spec spec(String layout, String key, int def, int min, int max, String doc, int unit) {
		Spec s = new Spec(layout, key, def, min, max, doc, unit);
		ALL.add(s);
		return s;
	}

	// ------------------------------------------------------------------ every layout (HqLighting)

	public static final Spec LIGHT_GRID = spec(ANY, "lighting.grid", 5, 3, 5,
			"legacy: spacing of the old floor-lamp grid; ignored since the built-in fill became invisible minecraft:light (docs/feat-hq-lighting.md)");
	public static final Spec LIGHT_LATTICE = spec(ANY, "lighting.lattice", 9, 5, 9,
			"legacy: period of the old floor-lamp lattice on outdoor grounds; ignored since the built-in fill became invisible minecraft:light");

	// ------------------------------------------------------------------ classic (CherryStyle / ClassicStyle)

	public static final Spec CLASSIC_PETALS = spec("classic", "portico.petalsEvery", 3, 2, 9,
			"pink petals on every Nth garden cell of the portico (rest moss)");
	public static final Spec CLASSIC_INLAY = spec("classic", "spine.inlayEvery", 4, 3, 12,
			"classic style: a copper inlay every N blocks down the spine's centre line");

	// ------------------------------------------------------------------ campus

	public static final Spec CAMPUS_ROOF_AZALEA = spec("campus", "roof.azaleaEvery", 7, 3, 31,
			"flat roof gardens: a flowering azalea on 1 cell in N");
	public static final Spec CAMPUS_ROOF_MOSS = spec("campus", "roof.mossEvery", 3, 2, 15,
			"flat roof gardens: moss carpet on 1 cell in N (of the rest)");
	public static final Spec CAMPUS_PLAZA_LAMPS = spec("campus", "plaza.lampPostEvery", 6, 4, 12,
			"hub plaza: a lamp post every N blocks along its outer ring");

	// ------------------------------------------------------------------ ring

	public static final Spec RING_TERRACE_BUSH = spec("ring", "terrace.bushEvery", 23, 11, 97,
			"terraces: an azalea bush on 1 cell in N");
	public static final Spec RING_PATIO_PLANTER = spec("ring", "patio.planterEvery", 11, 7, 47,
			"inner patio: a planter / azalea on 1 cell in N");

	// ------------------------------------------------------------------ vertical

	public static final Spec VERTICAL_FACADE_LIGHT = spec("vertical", "facade.lightEvery", 4, 3, 9,
			"lit panels in the tower facades every N blocks");

	// ------------------------------------------------------------------ ensanche

	public static final Spec ENSANCHE_CEILING_LIGHT = spec("ensanche", "hall.ceilingLightEvery", 6, 3, 9,
			"hall ceiling light panels on an N×N grid");

	// ------------------------------------------------------------------ village

	public static final Spec VILLAGE_CEILING_LIGHT = spec("village", "hall.ceilingLightEvery", 4, 3, 8,
			"hall ceiling light panels on an N×N grid");
	public static final Spec VILLAGE_LANE_LANTERN = spec("village", "lanes.lanternEvery", 11, 5, 41,
			"garden lanterns beside the lanes: 1 cell in N");

	// ------------------------------------------------------------------ hybrid-ringcampus

	public static final Spec RINGCAMPUS_STREET_LIGHT = spec("hybrid-ringcampus", "streets.lightEvery", 41, 17, 97,
			"lights let into the streets on a lattice: 1 cell in N");
	/**
	 * citadel/scale: size of the whole citadel (radii, streets, lots, buildings, rooms; heights by its
	 * square root), a decimal in [1.0, 3.0], default 2.0 (struct/hq-scale). {@code 1.0} is the original small plan block by block. Read with
	 * {@link Spec#value()}; see {@code CitadelCanvas} for the rule the pieces follow. Unlike the other
	 * parameters it moves anchors and desks: the next plan of an existing HQ is rebuilt at the new size.
	 */
	public static final Spec RINGCAMPUS_SCALE = spec("hybrid-ringcampus", "scale", 200, 100, 300,
			"size of the whole citadel (1.0 = the original small one, 2.0 = default; heights grow by its square root)", 100);
	public static final Spec RINGCAMPUS_TERRACE_BUSH = spec("hybrid-ringcampus", "terrace.bushEvery", 23, 11, 97,
			"terraces: an azalea bush on 1 cell in N");
	public static final Spec RINGCAMPUS_CATALOG = spec("hybrid-ringcampus", "catalog.enabled", 1, 0, 1,
			"catalog placement (CitadelPlacement): annexes, gardens, trees, street lamps, gates, plazas; 1 on, 0 off");
	/**
	 * design/citadel-terraces: soft campus relief (0..3). The core, each ring and the outer lawn stand on terraces a
	 * few blocks apart (at most 6 in all), joined by slab ramps on the avenues and stairs where paths cross a step,
	 * with low capped retaining walls, planters and lookouts. {@code 0} is the flat citadel block by block. See
	 * {@code CitadelRelief}. It moves anchors and desks up with their terrace.
	 */
	public static final Spec RINGCAMPUS_RELIEF = spec("hybrid-ringcampus", "terrain.relief", 2, 0, 3,
			"campus terraces: 0 flat, 1 gentle (steps of 1, 3 in all), 2 default (steps of 2, 6 in all), 3 acropolis (steps 3/2/1, 6 in all)");

	// ------------------------------------------------------------------ hybrid-towncenter

	public static final Spec TOWNCENTER_CEILING_LAMP = spec("hybrid-towncenter", "hall.ceilingLampEvery", 6, 4, 9,
			"civic hall ceiling lamps on an N×N grid");

	// ------------------------------------------------------------------ hybrid-terraces

	public static final Spec TERRACES_TEMPLE_LIGHT = spec("hybrid-terraces", "temple.ceilingLightEvery", 4, 3, 8,
			"temple ceiling light panels on an N×N grid");

	/** Every parameter, in declaration order. */
	public static List<Spec> all() {
		return Collections.unmodifiableList(ALL);
	}

	/** The parameters of {@code layoutId} (its own and the common ones). */
	public static List<Spec> of(String layoutId) {
		List<Spec> out = new ArrayList<>();
		for (Spec s : ALL) {
			if (s.appliesTo(layoutId)) {
				out.add(s);
			}
		}
		return out;
	}

	/** The spec of {@code layoutId} named {@code key}, or null. */
	public static Spec find(String layoutId, String key) {
		for (Spec s : ALL) {
			if (s.appliesTo(layoutId) && s.key().equals(key)) {
				return s;
			}
		}
		return null;
	}
}
