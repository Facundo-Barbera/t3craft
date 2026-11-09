package dev.t3craft.hq.citadel.nature;

/**
 * The sizes of the living pieces in one place, at the citadel's scale ({@code LayoutParams.RINGCAMPUS_SCALE},
 * see {@code CitadelCanvas}): {@link #s}, {@link #sy} and {@link #sd} are the citadel's own helpers
 * ({@code RingCampusGeometry.Dims}), identity at scale 1. Plots already derive their square from the lot
 * ({@code court + 6}); what is here are the fixed sizes inside a plot and the trees.
 *
 * <ul>
 * <li>{@link #s(int)}: a horizontal size (cells) of a plot's detail;</li>
 * <li>{@link #sy(int)}: a vertical size (a trunk, a post);</li>
 * <li>{@link #TREE}: the factor of every tree crown radius ({@code Trees}); trunks use {@link #sy}. It
 *     stays 1: a tree keeps the size of its species at any scale (a scaled citadel plants more of them).</li>
 * </ul>
 * Desks, doors and gates are never scaled (a gate is a gap of 3 cells and stays so).
 */
public final class Dims {
	private Dims() {
	}

	/** Scale of tree crowns (not scaled, see the class comment). */
	public static final double TREE = 1.0;

	private static dev.t3craft.hq.RingCampusGeometry.Dims g() {
		return dev.t3craft.hq.RingCampusGeometry.dims();
	}

	public static int s(int v) {
		return g().s(v);
	}

	public static int sy(int v) {
		return g().sy(v);
	}

	public static double sd(double v) {
		return g().sd(v);
	}

	/** Largest offset a tree cell can have from its trunk (the weeping cherry's canopy is the widest). */
	public static int treeReach() {
		return (int) Math.ceil(3.2 * TREE) + 1;
	}
}
