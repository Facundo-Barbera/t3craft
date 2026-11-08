package dev.t3craft.hq;

/** Public access to the shared test roster of {@link HqLayoutsPlanTest} for tests in other packages. */
public final class HqRosters {
	private HqRosters() {
	}

	/** {@code n} projects in three environments (see {@link HqLayoutsPlanTest#roster}). */
	public static HqInput projects(int n) {
		return HqLayoutsPlanTest.roster(n);
	}
}
