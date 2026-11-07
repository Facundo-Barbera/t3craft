package dev.t3craft.client.npc.behavior;

import org.jspecify.annotations.Nullable;

/**
 * Moves an NPC body (0.6 × 1.8 box on its feet position) against the blocks, like
 * {@code Entity.move} with collision: the result never puts the body inside a block it was not
 * already in. {@link #NONE} moves freely (tests, no level).
 */
public interface Collider {
	/** Where the body ends up when it tries to go from {@code from} to {@code to}. */
	Pos move(Pos from, Pos to);

	/** Does the body at {@code p} overlap a block? */
	boolean embedded(Pos p);

	/** Nearest spot where the body fits and has ground (cell centre), or null when none is close. */
	@Nullable Pos nearestFree(Pos p);

	Collider NONE = new Collider() {
		@Override
		public Pos move(Pos from, Pos to) {
			return to;
		}

		@Override
		public boolean embedded(Pos p) {
			return false;
		}

		@Override
		public @Nullable Pos nearestFree(Pos p) {
			return p;
		}
	};
}
