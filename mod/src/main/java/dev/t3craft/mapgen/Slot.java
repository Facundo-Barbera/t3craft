package dev.t3craft.mapgen;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * A hole a generator leaves for a piece: where it goes, which way it faces and how much room it has. A
 * generator decides the <b>shape</b> of the map and its slots; it does not decide what stands in them (that is
 * a {@link PieceSource}, normally the piece catalog).
 *
 * <p>Frame: the slot's footprint is centred on {@link #origin()} (the layer on top of the ground, in map
 * coordinates). {@link #facing()} is the direction the piece's front looks at (a building's door), so the
 * footprint is {@code across = 2*halfAcross+1} blocks perpendicular to it and {@code deep = 2*halfDeep+1} along
 * it. {@link #height()} is the free headroom above the origin.
 *
 * @param id     unique in its {@link MapLayout}, stable between runs (it is what a build records)
 * @param group  the landmass the slot belongs to (an island id, a citadel lot id...); links join groups
 * @param parent id of the slot that contains this one (a hall inside a lot), or null
 * @param tags   free labels for the filler to choose by ({@code "hall_0"}, {@code "ring_2"}, {@code "occupied"}...)
 */
public record Slot(String id, Role role, BlockPos origin, Direction facing, int halfAcross, int halfDeep, int height,
		String group, @Nullable String parent, Set<String> tags) {

	/** What a slot is for; the filler picks pieces by role, then by tags and size. */
	public enum Role {
		/** The main building of a landmass (the hub of the citadel, the pavilion of an island). */
		CENTRE,
		/** A secondary building (a lot, a hall, an annex). */
		MODULE,
		/** A special room at a fixed place (the citadel's ring-1 nodes). */
		NODE,
		/** An entrance building (a gatehouse). */
		GATE,
		/** Open paved or planted ground (a courtyard, a square). */
		PLAZA,
		/** A small piece of scenery (a tree, a lamp post, a planter). */
		DECOR,
		/** Where players or NPCs appear. */
		SPAWN
	}

	public Slot {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(role, "role");
		Objects.requireNonNull(group, "group");
		origin = origin.immutable();
		if (!facing.getAxis().isHorizontal()) {
			throw new IllegalArgumentException("slot " + id + " must face a horizontal direction");
		}
		if (halfAcross < 0 || halfDeep < 0 || height < 1) {
			throw new IllegalArgumentException("slot " + id + " has no room");
		}
		tags = Set.copyOf(tags);
	}

	public int across() {
		return 2 * halfAcross + 1;
	}

	public int deep() {
		return 2 * halfDeep + 1;
	}

	/** The xz columns the slot covers, in map coordinates. */
	public Area footprint() {
		boolean alongZ = facing.getAxis() == Direction.Axis.Z;
		int hx = alongZ ? halfAcross : halfDeep;
		int hz = alongZ ? halfDeep : halfAcross;
		return new Area(origin.getX() - hx, origin.getZ() - hz, origin.getX() + hx, origin.getZ() + hz);
	}

	/** Whether a piece of {@code size} fits in the slot. */
	public boolean fits(Piece.Size size) {
		return size.across() <= across() && size.deep() <= deep() && size.height() <= height;
	}

	public boolean has(String tag) {
		return tags.contains(tag);
	}

	public Slot withTag(String tag) {
		Set<String> t = new HashSet<>(tags);
		t.add(tag);
		return new Slot(id, role, origin, facing, halfAcross, halfDeep, height, group, parent, t);
	}
}
