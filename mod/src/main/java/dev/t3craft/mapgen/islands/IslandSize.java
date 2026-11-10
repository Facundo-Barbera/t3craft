package dev.t3craft.mapgen.islands;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The four island classes: small (S), medium (M), large (L) and extra large (XG). {@code radius} is the nominal
 * ground radius (the outline wobbles round it, see {@link Island}), {@code depth} the deepest column of the
 * underside, grass included, before stalactites.
 */
public enum IslandSize {
	SMALL("S", "small", 6, 7),
	MEDIUM("M", "medium", 10, 11),
	LARGE("L", "large", 16, 15),
	XLARGE("XG", "extra large", 22, 20);

	private final String code;
	private final String label;
	private final int radius;
	private final int depth;

	IslandSize(String code, String label, int radius, int depth) {
		this.code = code;
		this.label = label;
		this.radius = radius;
		this.depth = depth;
	}

	/** {@code S}, {@code M}, {@code L} or {@code XG}: what a size mix ({@link IslandMix}) is written with. */
	public String code() {
		return code;
	}

	public String label() {
		return label;
	}

	public int radius() {
		return radius;
	}

	/** Blocks of the deepest column (grass included), stalactites not counted. */
	public int depth() {
		return depth;
	}

	/**
	 * The farthest any ground column of an island of this size can be from its centre: the outline at its widest
	 * lobe plus the most edge noise ({@link Island#MAX_LOBE}, {@link Island#EDGE_BASE}, {@link Island#EDGE_PER_RADIUS}),
	 * plus one block of rounding. What the spacing of the field is computed from.
	 */
	public int reach() {
		return (int) Math.ceil(radius * (1 + Island.MAX_LOBE) + Island.EDGE_BASE + Island.EDGE_PER_RADIUS * radius) + 1;
	}

	/** The size written as {@code S}, {@code M}, {@code L}, {@code XG} (or {@code XL}), any case; null otherwise. */
	public static @Nullable IslandSize byCode(String text) {
		return switch (text.trim().toUpperCase(Locale.ROOT)) {
			case "S" -> SMALL;
			case "M" -> MEDIUM;
			case "L" -> LARGE;
			case "XG", "XL" -> XLARGE;
			default -> null;
		};
	}
}
