package dev.t3craft.site;

/**
 * Inclusive rectangle in <b>plan-relative</b> coordinates around a site origin (see
 * {@link SiteFrame}: front = {@code -z}, centre line {@code x = 0}). Used for module footprints and
 * the growth envelope, so a site's layout survives a move to a place facing another way.
 */
public record Rect(int minX, int minZ, int maxX, int maxZ) {
	public Rect {
		if (maxX < minX || maxZ < minZ) {
			throw new IllegalArgumentException("empty rect " + minX + "," + minZ + " .. " + maxX + "," + maxZ);
		}
	}

	/** Width (x) × depth (z) centred on {@code x = 0}, front edge at {@code z = front}. */
	public static Rect centred(int width, int depth, int front) {
		int h = (width - 1) / 2;
		return new Rect(-h, front, -h + width - 1, front + depth - 1);
	}

	public int width() {
		return maxX - minX + 1;
	}

	public int depth() {
		return maxZ - minZ + 1;
	}

	public boolean contains(Rect o) {
		return o.minX >= minX && o.maxX <= maxX && o.minZ >= minZ && o.maxZ <= maxZ;
	}

	public boolean intersects(Rect o) {
		return minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ;
	}

	/** World xz box (y = {@code y}) of this rect at {@code origin}. */
	public Box toWorld(CellPlan.Origin origin, int y) {
		int[] a = SiteFrame.toWorld(origin, minX, 0, minZ);
		int[] b = SiteFrame.toWorld(origin, maxX, 0, maxZ);
		return new Box(Math.min(a[0], b[0]), y, Math.min(a[2], b[2]), Math.max(a[0], b[0]), y, Math.max(a[2], b[2]));
	}

	public int[] toArray() {
		return new int[] {minX, minZ, maxX, maxZ};
	}

	public static Rect fromArray(int[] a) {
		if (a == null || a.length != 4) {
			throw new IllegalArgumentException("rect needs 4 ints");
		}
		return new Rect(a[0], a[1], a[2], a[3]);
	}

	@Override
	public String toString() {
		return "[" + minX + ".." + maxX + "]x[" + minZ + ".." + maxZ + "]";
	}
}
