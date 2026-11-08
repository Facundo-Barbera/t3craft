package dev.t3craft.build;

/**
 * What {@link CrewPlanner} decided for one plan: a lane per builder (plan indices in the order that
 * builder applies them), the ticks it walks before each one and where it stands for it, the
 * dependencies of every entry (entries that must be applied first: the one before at the same
 * position, the neighbour that supports it, the solid neighbours of decoration), and the global
 * order of the simulation (a topological order of the dependencies). Immutable once built.
 */
public final class CrewSchedule {
	private final int total;
	private final int[][] lanes;
	private final int[][] walk;
	private final int[][] stand;
	private final int[] order;
	private final int[] rank;
	private final int[][] deps;
	private final boolean[] forced;
	private final boolean[] relaxed;
	private final int[] owner;
	private final int[] startX;
	private final int[] startY;
	private final int[] startZ;
	private final double walked;

	CrewSchedule(int total, int[][] lanes, int[][] walk, int[][] stand, int[] order, int[][] deps, boolean[] forced, boolean[] relaxed, int[] owner,
			int[] startX, int[] startY, int[] startZ, double walked) {
		this.total = total;
		this.lanes = lanes;
		this.walk = walk;
		this.stand = stand;
		this.order = order;
		this.deps = deps;
		this.forced = forced;
		this.relaxed = relaxed;
		this.owner = owner;
		this.startX = startX;
		this.startY = startY;
		this.startZ = startZ;
		this.walked = walked;
		this.rank = new int[total];
		for (int r = 0; r < total; r++) {
			rank[order[r]] = r;
		}
	}

	/** Schedule of a {@link BuildPlan} (plan-relative cells; decoration = blocks that do not occlude). */
	public static CrewSchedule of(BuildPlan plan, int builders) {
		int n = plan.size();
		int[] x = new int[n];
		int[] y = new int[n];
		int[] z = new int[n];
		boolean[] air = new boolean[n];
		boolean[] decor = new boolean[n];
		for (int i = 0; i < n; i++) {
			Placement p = plan.get(i);
			x[i] = p.pos().getX();
			y[i] = p.pos().getY();
			z[i] = p.pos().getZ();
			air[i] = p.state().isAir();
			decor[i] = !air[i] && !p.state().canOcclude();
		}
		return CrewPlanner.plan(x, y, z, air, decor, builders);
	}

	public int total() {
		return total;
	}

	public int builders() {
		return lanes.length;
	}

	/** Plan indices builder {@code b} applies, in order. */
	public int[] lane(int b) {
		return lanes[b];
	}

	/** Ticks builder {@code b} walks before step {@code s} of its lane (0 = in reach already). */
	public int walk(int b, int s) {
		return walk[b][s];
	}

	/** Feet cell {@code {x, y, z}} (plan-relative) where builder {@code b} stands for step {@code s}. */
	public int[] stand(int b, int s) {
		return new int[] {stand[b][s * 3], stand[b][s * 3 + 1], stand[b][s * 3 + 2]};
	}

	/** Where builder {@code b} starts (plan-relative feet cell, outside the footprint). */
	public int[] start(int b) {
		return new int[] {startX[b], startY[b], startZ[b]};
	}

	/** Entries that must be applied before {@code i}. */
	public int[] deps(int i) {
		return deps[i];
	}

	/** Position of {@code i} in the simulated global order. */
	public int rank(int i) {
		return rank[i];
	}

	/** The simulated global order (plan indices). */
	public int orderAt(int r) {
		return order[r];
	}

	/** The plan left {@code i} without support: it was placed last, ignoring the support rule. */
	public boolean forced(int i) {
		return forced[i];
	}

	/** Decoration placed before some solid neighbour because that neighbour stands on it (supported all the same). */
	public boolean relaxed(int i) {
		return relaxed[i];
	}

	/** The builder (front) that applies {@code i}. */
	public int owner(int i) {
		return owner[i];
	}

	/** Total walking distance of the crew, blocks. */
	public double walked() {
		return walked;
	}
}
