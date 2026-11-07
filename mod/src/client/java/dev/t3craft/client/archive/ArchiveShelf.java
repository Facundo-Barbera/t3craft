package dev.t3craft.client.archive;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Where the "Archived" shelf's slots are, relative to the HQ origin: slot {@code i} is an anchor on
 * the visible face of the slot's cell ({@link Anchor#block()} = the cell, {@link Anchor#facing()} =
 * the face's outward normal, the point = the face centre). Order matters: slot 0 is the one the
 * newest-closed threads take first (lowest free slot).
 *
 * <ul>
 *   <li>{@link #fromPlan}: the shelf of an HQ plan, read from its named anchors (fix/hq-archive's
 *   {@code archiveShelfSlot(i)}). Pass the naming function so this module does not depend on it:
 *   {@code ArchiveShelf.fromPlan(plan, AnchorNames::archiveShelfSlot)}.</li>
 *   <li>{@link #FALLBACK}: until the plan has a shelf, two wings flanking the hub's attention board
 *   ({@code x = -7..-5} and {@code 5..7}, {@code z = -4}, {@code y = 1..4}, facing south like the
 *   board), minus the two bottom corners behind the planters: 22 slots in cells that are free, with
 *   air in front, in every current plan (a test checks it). Nothing is
 *   built there; the renderer shows client-only props while the cells are air.</li>
 * </ul>
 */
public interface ArchiveShelf {
	/** Slot anchors, relative to the HQ origin, in slot order. */
	List<Anchor> slots();

	default int capacity() {
		return slots().size();
	}

	/** The shelf in an HQ plan: anchors {@code name.apply(0)}, {@code name.apply(1)}, … until the first missing one. */
	static ArchiveShelf fromPlan(BuildPlan plan, IntFunction<String> name) {
		List<Anchor> out = new ArrayList<>();
		for (int i = 0; i < 4096; i++) {
			Anchor a = plan.anchor(name.apply(i));
			if (a == null) {
				break;
			}
			out.add(a);
		}
		List<Anchor> slots = List.copyOf(out);
		return () -> slots;
	}

	/** A fixed list of slot anchors. */
	static ArchiveShelf of(List<Anchor> slots) {
		List<Anchor> copy = List.copyOf(slots);
		return () -> copy;
	}

	/** Columns of the fallback shelf, nearest the board first, west wing then east wing. */
	int[] FALLBACK_COLUMNS = {-5, -6, -7, 5, 6, 7};
	/** Rows of the fallback shelf, eye level first. */
	int[] FALLBACK_ROWS = {2, 3, 1, 4};
	int FALLBACK_Z = -4;

	ArchiveShelf FALLBACK = of(fallbackSlots());

	private static List<Anchor> fallbackSlots() {
		List<Anchor> out = new ArrayList<>();
		for (int y : FALLBACK_ROWS) {
			for (int x : FALLBACK_COLUMNS) {
				if (y == 1 && Math.abs(x) == 7) {
					continue; // the hub's planters stand in front of these two
				}
				out.add(face("archive_shelf_fallback_" + out.size(), new BlockPos(x, y, FALLBACK_Z), Direction.SOUTH));
			}
		}
		return out;
	}

	/** The anchor on {@code cell}'s face towards {@code facing} (same convention as the HQ's screen anchors). */
	static Anchor face(String name, BlockPos cell, Direction facing) {
		return new Anchor(name, cell.getX() + 0.5 + facing.getStepX() * 0.5, cell.getY() + 0.5, cell.getZ() + 0.5 + facing.getStepZ() * 0.5,
				facing, cell);
	}

	/** One slot in world coordinates. */
	record WorldSlot(int slot, BlockPos cell, Anchor face) {
	}

	static WorldSlot world(ArchiveShelf shelf, HqSite site, int slot) {
		Anchor face = site.anchor(shelf.slots().get(slot));
		return new WorldSlot(slot, face.block(), face);
	}

	/**
	 * The shelf's title, in world coordinates: centred over the slots, just above the highest one,
	 * on the plane of their faces, facing like slot 0. Null for an empty shelf.
	 */
	static @Nullable Anchor header(ArchiveShelf shelf, HqSite site) {
		List<Anchor> slots = shelf.slots();
		if (slots.isEmpty()) {
			return null;
		}
		double sx = 0;
		double sz = 0;
		double top = Double.NEGATIVE_INFINITY;
		for (Anchor a : slots) {
			sx += a.x();
			sz += a.z();
			top = Math.max(top, a.block().getY() + 1);
		}
		Anchor first = slots.get(0);
		Anchor rel = new Anchor("archive_shelf_header", sx / slots.size(), top + 0.15, sz / slots.size(), first.facing(),
				BlockPos.containing(sx / slots.size(), top, sz / slots.size()));
		return site.anchor(Objects.requireNonNull(rel));
	}
}
