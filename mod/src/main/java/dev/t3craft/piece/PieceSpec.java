package dev.t3craft.piece;

import java.util.Locale;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * feat/piece-catalog-preview: what to show of a catalog piece: which one, and, for a piece that takes
 * parameters (a catalog variant is built into a plot), its box, the side it faces and its seed.
 *
 * @param id    the piece ({@link Piece#id})
 * @param size  {@code min}, {@code mid}, {@code max} or {@code WxDxH} (frontage x depth x height); null is {@code mid}
 * @param front the side the piece's front faces, a horizontal direction
 * @param seed  the piece's randomness
 */
public record PieceSpec(String id, @Nullable String size, Direction front, long seed) {
	public static final Direction DEFAULT_FRONT = Direction.SOUTH;
	public static final long DEFAULT_SEED = 1L;

	public PieceSpec {
		if (front.getAxis() == Direction.Axis.Y) {
			throw new IllegalArgumentException("front must be N, E, S or W");
		}
	}

	public static PieceSpec of(String id) {
		return new PieceSpec(id, null, DEFAULT_FRONT, DEFAULT_SEED);
	}

	public PieceSpec withId(String other) {
		return new PieceSpec(other, size, front, seed);
	}

	public PieceSpec withSize(@Nullable String s) {
		return new PieceSpec(id, s, front, seed);
	}

	public PieceSpec withFront(Direction f) {
		return new PieceSpec(id, size, f, seed);
	}

	public PieceSpec withSeed(long s) {
		return new PieceSpec(id, size, front, s);
	}

	/** True for {@code min}, {@code mid}, {@code max} and {@code WxDxH}. */
	public static boolean validSize(String s) {
		String t = s.toLowerCase(Locale.ROOT);
		if (t.equals("min") || t.equals("mid") || t.equals("max")) {
			return true;
		}
		return box(t) != null;
	}

	/** {@code {w, d, h}} of a {@code WxDxH} text, or null when it is not one. */
	public static int @Nullable [] box(String text) {
		String[] t = text.toLowerCase(Locale.ROOT).split("x");
		if (t.length != 3) {
			return null;
		}
		try {
			int w = Integer.parseInt(t[0].trim());
			int d = Integer.parseInt(t[1].trim());
			int h = Integer.parseInt(t[2].trim());
			return w >= 1 && d >= 1 && h >= 1 && w <= 200 && d <= 200 && h <= 200 ? new int[] {w, d, h} : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** {@code N}, {@code E}, {@code S} or {@code W} (or the full name), else null. */
	public static @Nullable Direction parseFront(String s) {
		return switch (s.trim().toLowerCase(Locale.ROOT)) {
			case "n", "north" -> Direction.NORTH;
			case "e", "east" -> Direction.EAST;
			case "s", "south" -> Direction.SOUTH;
			case "w", "west" -> Direction.WEST;
			default -> null;
		};
	}

	/** For chat: {@code id size front seed}. */
	public String describe() {
		return id + " " + (size == null ? "mid" : size) + " " + front.getSerializedName().charAt(0) + " seed " + seed;
	}
}
