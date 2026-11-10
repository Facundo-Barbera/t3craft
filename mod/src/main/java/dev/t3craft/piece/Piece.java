package dev.t3craft.piece;

/**
 * feat/piece-catalog-preview: one thing of the game that can be looked at on its own, at real scale: a part of
 * the citadel, a catalog variant, the kit's blocks, a room of a layout pack. See
 * {@code docs/features/piece-catalog-preview.md}.
 *
 * <p>{@link #build} is pure (no world) and may take seconds (a part of the citadel needs the whole plan): it runs on
 * a worker thread, never on the game's.
 */
public interface Piece {
	/** Unique, lower case, one word ({@code hq_hub}, {@code house_machiya}): what the commands take. */
	String id();

	/** The list it is shown under ({@code hq}, {@code classic}, {@code kit}, {@code pack}, or a catalog family such as {@code house}). */
	String group();

	/** A short human name. */
	String title();

	/** One line: what it is and where it comes from in the code. */
	String note();

	/** Whether {@link PieceSpec#size()}, {@link PieceSpec#front()} and {@link PieceSpec#seed()} change what is built. */
	default boolean parametric() {
		return false;
	}

	/**
	 * Builds the piece.
	 *
	 * @param fresh run the generator again instead of reusing a plan computed earlier (what {@code reload} asks for,
	 *              so code changed since, by a hot swap, shows)
	 */
	Built build(PieceSpec spec, boolean fresh) throws Exception;

	/**
	 * @param model  the blocks, in any frame (the stage moves them)
	 * @param detail what was built, for chat ("plot 11x11x14 facing S, seed 1")
	 */
	record Built(PieceModel model, String detail) {
	}
}
