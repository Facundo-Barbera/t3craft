package dev.t3craft.hq;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A named spot of the HQ that other modules use (NPC desks, the attention board, the entrance, the
 * spawn), relative to the plan origin like {@link Placement#rel()}.
 *
 * <ul>
 *   <li>{@code x, y, z}: the exact point (block corner = integer). For standing/sitting spots it is
 *   the centre of the cell at foot level; for surfaces (monitor, board) it is the centre of the
 *   visible face, on the face plane.</li>
 *   <li>{@code facing}: the direction the spot looks at. For a seat/standing spot it is where the
 *   occupant looks; for a surface it is the outward normal of its visible face.</li>
 *   <li>{@code block}: the block cell the anchor is about (seat block, monitor block, lamp block,
 *   board centre block). Modules that toggle blocks (status lamps) use this cell.</li>
 * </ul>
 */
public record Anchor(String name, double x, double y, double z, Direction facing, BlockPos block) {
	public Anchor {
		if (!facing.getAxis().isHorizontal()) {
			throw new IllegalArgumentException("anchor facing must be horizontal: " + facing);
		}
		block = block.immutable();
	}

	/** Minecraft yaw (degrees) for an entity at this anchor looking along {@link #facing}. */
	public float yaw() {
		return facing.toYRot();
	}

	/** The same spot under another name. */
	public Anchor renamed(String newName) {
		return new Anchor(newName, x, y, z, facing, block);
	}

	/** A spot standing/sitting in {@code cell} (centre, foot level), looking along {@code facing}. */
	static Anchor at(String name, BlockPos cell, Direction facing) {
		return new Anchor(name, cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, facing, cell);
	}

	/** The centre of {@code cell}'s face that points to {@code facing}. */
	static Anchor face(String name, BlockPos cell, Direction facing) {
		return new Anchor(name,
				cell.getX() + 0.5 + facing.getStepX() * 0.5,
				cell.getY() + 0.5,
				cell.getZ() + 0.5 + facing.getStepZ() * 0.5,
				facing, cell);
	}

	@Override
	public String toString() {
		return String.format(Locale.ROOT, "%s@(%.2f,%.2f,%.2f)->%s", name, x, y, z, facing.getSerializedName());
	}
}
