package dev.t3craft.client.pieces;

import java.util.Locale;

/**
 * What the browser asks to see: one piece, which way it faces, the plot size to build it in and the seed of
 * its randomness. {@code w}, {@code d} and {@code h} are 0 when the piece has no size range ("build it at its
 * default size"); the same piece with another size, front or seed is another variant to look at.
 *
 * @param front one of {@link #FRONTS}: the side the plot faces
 * @param seed  the variant's seed ({@code Plot.rng}); the same seed gives the same blocks
 */
public record PieceRequest(PieceEntry piece, String front, int w, int d, int h, long seed) {
	public static final java.util.List<String> FRONTS = java.util.List.of("north", "east", "south", "west");

	/** The words of the preview command before the arguments (no leading slash). */
	public static final String COMMAND = "t3craft piece show";

	public PieceRequest {
		front = FRONTS.contains(front.toLowerCase(Locale.ROOT)) ? front.toLowerCase(Locale.ROOT) : "south";
		w = Math.max(0, w);
		d = Math.max(0, d);
		h = Math.max(0, h);
	}

	/**
	 * The command that shows this request in the world, without the slash:
	 * {@code t3craft piece show <id> <north|east|south|west> <w> <d> <h> <seed>}. All six arguments are always
	 * present, in this order; {@code 0 0 0} means "default size".
	 */
	public String command() {
		return COMMAND + " " + piece.id() + " " + front + " " + w + " " + d + " " + h + " " + seed;
	}
}
