package dev.t3craft.mapgen;

/**
 * An algorithmic map generator: parameters in, {@link MapLayout} out (the shape of the ground and the slots
 * where things go, never the finished buildings).
 *
 * <p>Contract: pure and deterministic. No world access, no randomness other than {@link Noise} of the params'
 * seed, no clock, no static mutable state; the same params give an equal layout on every call, so a layout can
 * be recomputed instead of stored, and slot ids stay stable between runs.
 *
 * <p>What goes into the slots is chosen later: {@link SlotPlacer#fill} asks a {@link PieceSource} (the piece
 * catalog) for a {@link Piece} per slot and maps it onto the slot's frame.
 *
 * @param <P> the generator's parameters: a roster-derived {@code HqLayout} for the citadel, a window for an
 *            endless map
 */
public interface MapGenerator<P> {
	/** Stable id, also the id of its {@link MapTypes} entry. */
	String id();

	MapLayout generate(P params);
}
