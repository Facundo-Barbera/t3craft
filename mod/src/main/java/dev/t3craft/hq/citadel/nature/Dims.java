package dev.t3craft.hq.citadel.nature;

/**
 * The sizes of the living pieces in one place, so that the citadel's scale (the coordinator wraps
 * {@link #s} with {@code canvas.s} when the scale helpers land in the base) grows them together.
 * Today the factors are 1. Plots already derive their square from the lot ({@code court + 6}); what
 * is here are the fixed sizes inside a plot and the trees.
 *
 * <ul>
 * <li>{@link #s(int)}: a horizontal size (cells) of a plot's detail;</li>
 * <li>{@link #sy(int)}: a vertical size (a trunk, a post);</li>
 * <li>{@link #TREE}: the factor of every tree crown radius ({@code Trees}); trunks use {@link #sy}.</li>
 * </ul>
 * Desks, doors and gates are never scaled (a gate is a gap of 3 cells and stays so).
 */
public final class Dims {
	private Dims() {
	}

	/** Horizontal scale of the plots' detail. */
	public static final double HORIZONTAL = 1.0;
	/** Vertical scale of trunks and posts. */
	public static final double VERTICAL = 1.0;
	/** Scale of tree crowns. */
	public static final double TREE = 1.0;

	public static int s(int v) {
		return (int) Math.round(v * HORIZONTAL);
	}

	public static int sy(int v) {
		return (int) Math.round(v * VERTICAL);
	}

	public static double sd(double v) {
		return v * HORIZONTAL;
	}

	/** Largest offset a tree cell can have from its trunk (the weeping cherry's canopy is the widest). */
	public static int treeReach() {
		return (int) Math.ceil(3.2 * TREE) + 1;
	}
}
