package dev.t3craft.mapgen;

import dev.t3craft.build.Placement;
import java.util.List;
import java.util.Objects;

/**
 * A placeable building block of a map (a central building, a module, a lamp post, a tree...): blocks in
 * the <b>piece frame</b>, which {@link SlotPlacer} maps onto a {@link Slot}.
 *
 * <p>Piece frame: {@code (0, 0, 0)} is the slot's {@link Slot#origin()} (the layer on top of the ground; a piece
 * may also write {@code y = -1} to replace the surface block), {@code +x} is east, {@code +z} is south, and the
 * piece's front (its door) faces <b>north</b> ({@code -z}). Placing it rotates the front onto the slot's facing.
 * Footprints are centred on the origin: a piece {@code across} wide covers {@code x in [-(across-1)/2, across/2]}.
 *
 * <p>This is the contract the piece catalog implements; the catalog is a separate thread of work and this class
 * must not depend on it.
 */
public record Piece(String id, Size size, List<Placement> blocks) {
	/** Footprint and height a piece needs, in blocks. */
	public record Size(int across, int deep, int height) {
		public Size {
			if (across < 1 || deep < 1 || height < 1) {
				throw new IllegalArgumentException("empty piece size " + across + "x" + deep + "x" + height);
			}
		}
	}

	public Piece {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(size, "size");
		blocks = List.copyOf(blocks);
	}
}
