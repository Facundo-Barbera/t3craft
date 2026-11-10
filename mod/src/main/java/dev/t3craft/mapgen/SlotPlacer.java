package dev.t3craft.mapgen;

import dev.t3craft.build.Placement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/** Maps pieces (piece frame, see {@link Piece}) onto slots (map coordinates). Pure. */
public final class SlotPlacer {
	private SlotPlacer() {
	}

	/** The rotation that turns a piece's north-facing front onto {@code facing}. */
	public static Rotation rotationFor(Direction facing) {
		Rotation r = Rotation.NONE;
		Direction d = Direction.NORTH;
		while (d != facing) {
			d = d.getClockWise();
			r = r.getRotated(Rotation.CLOCKWISE_90);
		}
		return r;
	}

	/** {@code piece} in map coordinates: rotated to the slot's facing and moved to its origin. */
	public static List<Placement> place(Slot slot, Piece piece) {
		Rotation rot = rotationFor(slot.facing());
		BlockPos origin = slot.origin();
		List<Placement> out = new ArrayList<>(piece.blocks().size());
		for (Placement p : piece.blocks()) {
			out.add(new Placement(origin.offset(p.pos().rotate(rot)), p.state().rotate(rot), p.blockEntity()));
		}
		return out;
	}

	/**
	 * Fills the slots of {@code map} (in the order of {@link MapLayout#slots()}, so the result is stable): slot id
	 * to its blocks. Slots the source leaves empty, and pieces that do not fit, are skipped.
	 */
	public static Map<String, List<Placement>> fill(MapLayout map, PieceSource source) {
		Map<String, List<Placement>> out = new LinkedHashMap<>();
		for (Slot s : map.slots()) {
			Piece piece = source.pieceFor(s, map);
			if (piece != null && s.fits(piece.size())) {
				out.put(s.id(), place(s, piece));
			}
		}
		return out;
	}
}
