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

	/**
	 * A deterministic, non-negative seed for the piece of {@code slot}: from the map's {@link MapLayout#seed()}, the
	 * slot's column ({@code x}, {@code z} of its origin) and its {@link Slot#id()}. The height is left out on purpose,
	 * so a slot keeps its seed when a painter lifts it (terraces). Same map seed and slot, same number: a source that
	 * draws its randomness from it gives the same piece every run.
	 */
	static long seedFor(Slot slot, MapLayout map) {
		long k = map.seed() * 0x9E3779B97F4A7C15L + slot.origin().getX() * 73856093L + slot.origin().getZ() * 83492791L
				+ slot.id().hashCode() * 19349663L;
		k ^= k >>> 29;
		k *= 0xBF58476D1CE4E5B9L;
		k ^= k >>> 32;
		return k & Long.MAX_VALUE;
	}
}
