package dev.t3craft.client.pieces;

import java.util.List;
import java.util.Locale;

/**
 * What the browser asks to see: one piece, which way it faces, the plot size to build it in and the seed of
 * its randomness. The same piece with another size, front or seed is another variant to look at.
 *
 * <p>{@link #command()} speaks the grammar {@code client/piece/PieceClient} registers (issue #30, T12):
 * <pre>
 * t3craft piece show &lt;id&gt; &lt;size&gt; &lt;front&gt; &lt;seed&gt;
 *   size  = min | mid | max | WxDxH   (frontage x depth x height, what PieceSpec.validSize takes)
 *   front = n | e | s | w
 *   seed  = a whole number, 0 or more (the command's LongArgumentType.longArg(0))
 * </pre>
 * The browser always sends all three optional arguments.
 *
 * @param front one of {@link #FRONTS}: the side the plot faces (the full word; the command gets its first letter)
 * @param size  {@code min}, {@code mid}, {@code max} or {@code WxDxH}; anything else becomes {@code mid}
 * @param seed  the variant's seed ({@code Plot.rng}); the same seed gives the same blocks; negative becomes 0
 */
public record PieceRequest(PieceEntry piece, String front, String size, long seed) {
	public static final List<String> FRONTS = List.of("north", "east", "south", "west");

	/** The words of the preview command before the arguments (no leading slash). */
	public static final String COMMAND = "t3craft piece show";

	/** The size keywords of the command. */
	public static final String MIN = "min";
	public static final String MID = "mid";
	public static final String MAX = "max";

	public PieceRequest {
		String f = front == null ? "" : front.toLowerCase(Locale.ROOT);
		front = FRONTS.contains(f) ? f : "south";
		size = validSize(size) ? size.toLowerCase(Locale.ROOT) : MID;
		seed = Math.max(0, seed);
	}

	/** An exact plot: {@code WxDxH}. */
	public static String box(int w, int d, int h) {
		return w + "x" + d + "x" + h;
	}

	/** {@code min}, {@code mid}, {@code max} or {@code WxDxH} with every side from 1 to 200 (the rule of {@code PieceSpec.validSize}). */
	public static boolean validSize(String s) {
		if (s == null) {
			return false;
		}
		String t = s.toLowerCase(Locale.ROOT);
		if (t.equals(MIN) || t.equals(MID) || t.equals(MAX)) {
			return true;
		}
		String[] p = t.split("x");
		if (p.length != 3) {
			return false;
		}
		for (String n : p) {
			try {
				int v = Integer.parseInt(n.trim());
				if (v < 1 || v > 200) {
					return false;
				}
			} catch (NumberFormatException e) {
				return false;
			}
		}
		return true;
	}

	/** {@code n}, {@code e}, {@code s} or {@code w}: what the command takes for {@link #front}. */
	public String frontLetter() {
		return front.substring(0, 1);
	}

	/**
	 * The command that shows this request in the world, without the slash:
	 * {@code t3craft piece show <id> <min|mid|max|WxDxH> <n|e|s|w> <seed>}.
	 */
	public String command() {
		return COMMAND + " " + piece.id() + " " + size + " " + frontLetter() + " " + seed;
	}
}
