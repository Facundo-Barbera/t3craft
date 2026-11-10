package dev.t3craft.mapgen;

import org.jspecify.annotations.Nullable;

/**
 * Chooses what stands in a {@link Slot}. The piece catalog implements this; until it does, a map type brings a
 * small built-in source (the floating islands have {@code IslandProps}).
 */
@FunctionalInterface
public interface PieceSource {
	/**
	 * A piece for {@code slot}, or null to leave it empty. The piece must {@link Slot#fits fit} the slot; the
	 * source decides by role, tags, size and the rest of {@code map} (neighbours, links).
	 */
	@Nullable Piece pieceFor(Slot slot, MapLayout map);
}
