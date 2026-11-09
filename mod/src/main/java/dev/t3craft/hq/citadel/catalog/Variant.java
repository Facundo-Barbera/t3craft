package dev.t3craft.hq.citadel.catalog;

import dev.t3craft.hq.citadel.CitadelCanvas;

/**
 * One design of the citadel catalog: a house, a lamp, a fountain… that builds itself into any
 * {@link Plot} within its size range. One class per variant, in the family's sub-package
 * ({@code catalog/house/HouseTimberFrame.java}), registered with one line in {@link CatalogEntries}.
 * See {@code docs/tools/citadel-catalog.md}.
 *
 * <h2>Contract</h2>
 * <ul>
 * <li><b>Only writes inside the plot</b>, through {@link Plot#set} / {@link Plot#put} /
 *     {@link Plot#fill} (never {@code c.set} directly): those rotate the states and clip to the box.</li>
 * <li><b>Designed facing NORTH</b>: the front is {@code v = 0}, {@code u} goes left → right, {@code y = 0}
 *     stands on the ground. Directions in block states are local (NORTH = towards the front).</li>
 * <li><b>Deterministic</b>: the same plot (box, front, seed) gives the same blocks. Randomness only
 *     from {@link Plot#rng} / {@link Plot#hash}, never {@code Math.random} or static state.</li>
 * <li><b>Any size in its range</b>: {@code build} must look good for every {@code w} in
 *     {@code minW..maxW}, {@code d} in {@code minD..maxD} and {@code h ≥ minH}; extra height may stay empty.</li>
 * <li><b>No game anchors, workstations or fixtures</b> unless the family leader asks for them
 *     ({@link Plot#anchorPos} gives the position): a variant is decoration.</li>
 * <li>Writes air only where it means it (doorways, clearing its own box); it does not paint the
 *     ground under the plot ({@code y = -1} is outside the box anyway).</li>
 * </ul>
 */
public interface Variant {
	/** Unique id, lower-case, family prefix first: {@code house_timber_frame}, {@code lamp_cherry_post}. */
	String id();

	Family family();

	/** Smallest frontage this variant builds well. */
	int minW();

	/** Smallest depth. */
	int minD();

	/** Smallest height it needs. */
	int minH();

	/** Largest frontage it builds well (bigger plots are not given to it by {@link Catalog#pick}). */
	int maxW();

	/** Largest depth. */
	int maxD();

	/** Relative weight in {@link Catalog#pick} (1 = normal; 0 = only by id). */
	default int weight() {
		return 1;
	}

	/**
	 * Whether {@code p} is within this variant's size range. The largest frontage and depth grow with the
	 * citadel's scale ({@code maxW · √k}; identity at scale 1), so that the bigger plots
	 * of a scaled citadel still find variants: a variant builds to its plot's size, see the contract.
	 */
	default boolean fits(Plot p) {
		dev.t3craft.hq.RingCampusGeometry.Dims g = dev.t3craft.hq.citadel.CitadelCanvas.dims();
		return p.w() >= minW() && p.w() <= Math.max(maxW(), grow(maxW(), g.k, family())) && p.d() >= minD() && p.d() <= Math.max(maxD(), grow(maxD(), g.k, family())) && p.h() >= minH();
	}

	/**
	 * A maximum size at scale {@code k}. Plots grow by {@code √k} (see {@code CitadelPlacement#sp}), except the
	 * families sized to a street (gates, plaza centres, bridges), which grow by {@code k} like the street.
	 */
	private static int grow(int n, double k, Family f) {
		boolean street = f == Family.GATE || f == Family.PLAZA_CENTER || f == Family.BRIDGE;
		return (int) Math.round(n * (street ? k : Math.sqrt(k)));
	}

	/** Builds the variant into {@code p} on {@code c}. See the contract above. */
	void build(CitadelCanvas c, Plot p);
}
