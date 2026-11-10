package dev.t3craft.mapgen;

import org.jspecify.annotations.Nullable;

/**
 * The ground a generator lays out, as a pure function of the column: where there is ground and how far it
 * reaches. It says nothing about what is built on it (that is the {@link Slot}s and their pieces) nor which
 * blocks the ground is made of (the world builder decides that).
 */
public interface TerrainShape {
	/** Every column that can have ground lies in this area (for a window of an endless map: the window). */
	Area bounds();

	/** The ground of column {@code (x, z)} or null when there is none (void). */
	@Nullable Span column(int x, int z);

	default boolean contains(int x, int z) {
		return column(x, z) != null;
	}

	/** Lowest and highest ground block of a column; {@code top} is the walkable surface minus one. */
	record Span(int bottom, int top) {
		public Span {
			if (bottom > top) {
				throw new IllegalArgumentException("span " + bottom + ".." + top);
			}
		}
	}
}
