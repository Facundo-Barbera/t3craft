package dev.t3craft.mapgen.islands;

/** The four island classes. {@code radius} is the nominal ground radius, {@code depth} the deepest column. */
public enum IslandSize {
	SMALL("small", 5, 6),
	MEDIUM("medium", 9, 9),
	LARGE("large", 14, 13),
	XLARGE("extra large", 20, 18);

	private final String label;
	private final int radius;
	private final int depth;

	IslandSize(String label, int radius, int depth) {
		this.label = label;
		this.radius = radius;
		this.depth = depth;
	}

	public String label() {
		return label;
	}

	public int radius() {
		return radius;
	}

	/** Blocks of the deepest column (grass included). */
	public int depth() {
		return depth;
	}

	/** The class of a cell from a hash in {@code 0..9}: 40% small, 30% medium, 20% large, 10% extra large. */
	static IslandSize pick(int tenth) {
		if (tenth < 4) {
			return SMALL;
		}
		if (tenth < 7) {
			return MEDIUM;
		}
		return tenth < 9 ? LARGE : XLARGE;
	}
}
