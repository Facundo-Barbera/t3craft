package dev.t3craft.hq;

/**
 * Public view of the HQ's fixed geometry for whoever places it on real terrain
 * ({@code dev.t3craft.hq.construct}). Splits the ground plan into non-overlapping rectangles, one
 * per <b>grid slot</b>, in HQ coordinates (origin = hub floor centre, entrance south, growth north):
 *
 * <ul>
 *   <li>{@link #base()}: hub (with the "Archived" shelf inside it) and portico; there is no basement;</li>
 *   <li>{@link #slot(int)}: grid slot {@code i} = module {@code i}'s wing. The even (east) slot of a
 *   row also owns that row's stretch of the spine. Every row's rectangle is shifted one line north
 *   so it also owns the line where the spine cap stands while it is the last row (that line is the
 *   next row's garden strip once the next row exists); row 0 additionally keeps its own garden
 *   strip, next to the hub.</li>
 * </ul>
 *
 * Every placement of a plan with at most {@code n} modules lies in {@code base()} or in
 * {@code slot(0..n-1)} (a test checks this).
 */
public final class HqFootprints {
	/** An inclusive xz rectangle in HQ coordinates. */
	public record Area(int minX, int minZ, int maxX, int maxZ) {
		public boolean contains(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}

		public boolean intersects(Area o) {
			return minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ;
		}

		public Area union(Area o) {
			return new Area(Math.min(minX, o.minX), Math.min(minZ, o.minZ), Math.max(maxX, o.maxX), Math.max(maxZ, o.maxZ));
		}
	}

	private HqFootprints() {
	}

	/** Lowest y any HQ block uses: the floor layer (there is no basement). */
	public static int bottom() {
		return 0;
	}

	/** Highest y any HQ block uses. */
	public static int top() {
		return HqGeometry.TOP;
	}

	/** Hub (with the "Archived" shelf) + portico. */
	public static Area base() {
		Box b = HqGeometry.hub().union(HqGeometry.portico());
		return new Area(b.minX(), b.minZ(), b.maxX(), b.maxZ());
	}

	/** Grid slot of module {@code index} (see the class comment). */
	public static Area slot(int index) {
		if (index < 0) {
			throw new IllegalArgumentException("slot " + index);
		}
		int r = HqGeometry.row(index);
		int reach = HqGeometry.SPINE + 1 + HqGeometry.segmentU(HqLayout.SEGMENTS);
		int north = HqGeometry.rowNorth(r) - 1;
		int south = r == 0 ? HqGeometry.rowSouth(0) : HqGeometry.rowSouth(r) - 1;
		return HqGeometry.side(index) > 0
				? new Area(-HqGeometry.SPINE, north, reach, south)
				: new Area(-reach, north, -HqGeometry.SPINE - 1, south);
	}

	/** Everything a plan with at most {@code maxModules} modules may use. */
	public static Area envelope(int maxModules) {
		Area a = base();
		for (int i = 0; i < maxModules; i++) {
			a = a.union(slot(i));
		}
		return a;
	}
}
