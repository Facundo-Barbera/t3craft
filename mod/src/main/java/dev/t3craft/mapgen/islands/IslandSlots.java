package dev.t3craft.mapgen.islands;

import dev.t3craft.mapgen.Area;
import dev.t3craft.mapgen.Noise;
import dev.t3craft.mapgen.Slot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Lays out the slots of an island: what each size asks for ({@link #wants}) and where it ends up. A wanted slot
 * has a preferred angle and distance from the centre; it is put at the first spot, searching inwards and a little
 * to either side, whose footprint lies inside the island with a block to spare and keeps a block of ground from
 * every slot already placed. When nothing fits it shrinks, down to its minimum, and is dropped after that.
 * Deterministic: same island, same slots.
 *
 * <p>Slots per size (half = half the side of the square footprint; a 7x7 slot has half 3):
 * <ul>
 *   <li><b>S</b>: a tree (half 2) and a small accent (lamp, planter, bench...).</li>
 *   <li><b>M</b>: a centre (half 3), a tree and two accents.</li>
 *   <li><b>L</b>: a big centre (half 5), two modules (half 3), two trees, two accents.</li>
 *   <li><b>XG</b>: the biggest centre (half 7), four modules (half 4) on the axes, four trees on the diagonals
 *       and four accents.</li>
 *   <li><b>spawn (L)</b>: the spawn spot at {@code surface() + (0, 0, 4)} is kept clear; the centre (half 4) stands
 *       north of it facing south, the plaza between, two modules east and west, four trees, two lamps flanking
 *       the spawn.</li>
 * </ul>
 * Tags on every slot: the size ({@code small}...), the biome id ({@code meadow}...) and a hint for the filler:
 * {@code building} (centre, module), {@code tree} or {@code accent}; the spawn island's slots carry {@code spawn}.
 */
final class IslandSlots {
	/** Ground kept between two footprints. */
	private static final int GAP = 1;
	/** Ground kept between a footprint and the edge. */
	private static final double EDGE_MARGIN = 1.0;
	/** Angles tried round the preferred one, in radians. */
	private static final double[] TURNS = {0, 0.3, -0.3, 0.6, -0.6, 0.9, -0.9, 1.3, -1.3};

	private static final String BUILDING = "building";
	private static final String TREE = "tree";
	private static final String ACCENT = "accent";

	private IslandSlots() {
	}

	private record Want(String name, Slot.Role role, int half, int minHalf, int height, double angle, double dist, @Nullable Direction facing,
			String hint) {
	}

	/** {@code bare} with its pads and slots. */
	static Island lay(Island bare) {
		List<Area> taken = new ArrayList<>();
		List<Slot> slots = new ArrayList<>();
		List<Island.Pad> pads = new ArrayList<>();
		if (bare.spawn()) {
			BlockPos sp = bare.surface().offset(0, 0, 4);
			Area a = new Area(sp.getX() - 1, sp.getZ() - 1, sp.getX() + 1, sp.getZ() + 1);
			taken.add(a);
			pads.add(new Island.Pad(a, 0));
			slots.add(new Slot(bare.id() + ":spawn", Slot.Role.SPAWN, sp, Direction.NORTH, 1, 1, 3, bare.id(), null, tags(bare, "plaza")));
		}
		for (Want w : wants(bare)) {
			place(bare, w, taken, slots, pads);
		}
		return bare.withLayout(pads, slots);
	}

	private static List<Want> wants(Island i) {
		double a0 = Noise.unit(i.seed(), i.gx(), i.gz(), 30) * 2 * Math.PI;
		double q = Math.PI / 2;
		List<Want> out = new ArrayList<>();
		if (i.spawn()) {
			out.add(new Want("centre", Slot.Role.CENTRE, 4, 3, 18, -q, 6, Direction.SOUTH, BUILDING));
			out.add(new Want("module_0", Slot.Role.MODULE, 3, 2, 10, 0, 11, null, BUILDING));
			out.add(new Want("module_1", Slot.Role.MODULE, 3, 2, 10, Math.PI, 11, null, BUILDING));
			out.add(new Want("tree_0", Slot.Role.DECOR, 3, 1, 14, q / 2, 11, null, TREE));
			out.add(new Want("tree_1", Slot.Role.DECOR, 3, 1, 14, 3 * q / 2, 11, null, TREE));
			out.add(new Want("tree_2", Slot.Role.DECOR, 2, 1, 14, -q / 2, 12, null, TREE));
			out.add(new Want("tree_3", Slot.Role.DECOR, 2, 1, 14, -3 * q / 2, 12, null, TREE));
			out.add(new Want("lamp_0", Slot.Role.DECOR, 0, 0, 6, StrictMath.atan2(4, 3), 5, null, ACCENT));
			out.add(new Want("lamp_1", Slot.Role.DECOR, 0, 0, 6, StrictMath.atan2(4, -3), 5, null, ACCENT));
			return out;
		}
		switch (i.size()) {
			case SMALL -> {
				out.add(new Want("tree_0", Slot.Role.DECOR, 2, 1, 12, a0, 0, null, TREE));
				out.add(new Want("accent_0", Slot.Role.DECOR, 1, 0, 6, a0 + Math.PI, 4, null, ACCENT));
			}
			case MEDIUM -> {
				out.add(new Want("centre", Slot.Role.CENTRE, 3, 2, 12, 0, 0, null, BUILDING));
				out.add(new Want("tree_0", Slot.Role.DECOR, 2, 1, 12, a0, 7, null, TREE));
				out.add(new Want("accent_0", Slot.Role.DECOR, 1, 0, 6, a0 + Math.PI, 6, null, ACCENT));
				out.add(new Want("accent_1", Slot.Role.DECOR, 1, 0, 6, a0 + q, 6, null, ACCENT));
			}
			case LARGE -> {
				out.add(new Want("centre", Slot.Role.CENTRE, 5, 3, 18, 0, 0, null, BUILDING));
				out.add(new Want("module_0", Slot.Role.MODULE, 3, 2, 10, a0, 11, null, BUILDING));
				out.add(new Want("module_1", Slot.Role.MODULE, 3, 2, 10, a0 + Math.PI, 11, null, BUILDING));
				out.add(new Want("tree_0", Slot.Role.DECOR, 3, 1, 14, a0 + q, 11, null, TREE));
				out.add(new Want("tree_1", Slot.Role.DECOR, 3, 1, 14, a0 - q, 11, null, TREE));
				out.add(new Want("accent_0", Slot.Role.DECOR, 1, 0, 6, a0 + q / 2, 9, null, ACCENT));
				out.add(new Want("accent_1", Slot.Role.DECOR, 1, 0, 6, a0 + Math.PI + q / 2, 9, null, ACCENT));
			}
			case XLARGE -> {
				out.add(new Want("centre", Slot.Role.CENTRE, 7, 4, 22, 0, 0, null, BUILDING));
				for (int k = 0; k < 4; k++) {
					out.add(new Want("module_" + k, Slot.Role.MODULE, 4, 2, 14, a0 + k * q, 15, null, BUILDING));
				}
				for (int k = 0; k < 4; k++) {
					out.add(new Want("tree_" + k, Slot.Role.DECOR, 3, 1, 14, a0 + q / 2 + k * q, 16, null, TREE));
				}
				for (int k = 0; k < 4; k++) {
					out.add(new Want("accent_" + k, Slot.Role.DECOR, 1, 0, 6, a0 + q / 4 + k * q, 11, null, ACCENT));
				}
			}
		}
		return out;
	}

	private static void place(Island i, Want w, List<Area> taken, List<Slot> slots, List<Island.Pad> pads) {
		int steps = (int) Math.ceil(w.dist());
		for (int half = w.half(); half >= w.minHalf(); half--) {
			for (int step = 0; step <= steps; step++) {
				double dist = Math.max(0, w.dist() - step);
				for (double turn : TURNS) {
					double a = w.angle() + turn;
					int dx = (int) Math.round(dist * StrictMath.cos(a));
					int dz = (int) Math.round(dist * StrictMath.sin(a));
					int x = i.cx() + dx;
					int z = i.cz() + dz;
					Area fp = new Area(x - half, z - half, x + half, z + half);
					if (!inside(i, fp) || blocked(fp, taken)) {
						if (dist == 0) {
							break; // every turn is the same spot
						}
						continue;
					}
					Direction facing = w.facing() != null ? w.facing() : towardsCentre(i, dx, dz);
					int lift = i.rawLift(x, z);
					BlockPos origin = new BlockPos(x, i.top() + 1 + lift, z);
					slots.add(new Slot(i.id() + ":" + w.name(), w.role(), origin, facing, half, half, w.height(), i.id(), null, tags(i, w.hint())));
					taken.add(fp);
					pads.add(new Island.Pad(fp, lift));
					return;
				}
			}
		}
	}

	private static boolean inside(Island i, Area fp) {
		for (int x = fp.minX(); x <= fp.maxX(); x++) {
			for (int z = fp.minZ(); z <= fp.maxZ(); z++) {
				if (!i.contains(x, z, -EDGE_MARGIN)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean blocked(Area fp, List<Area> taken) {
		Area grown = new Area(fp.minX() - GAP, fp.minZ() - GAP, fp.maxX() + GAP, fp.maxZ() + GAP);
		for (Area t : taken) {
			if (grown.intersects(t)) {
				return true;
			}
		}
		return false;
	}

	/** The front looks at the island's centre; a slot on the centre faces a direction chosen by the seed. */
	private static Direction towardsCentre(Island i, int dx, int dz) {
		if (dx == 0 && dz == 0) {
			Direction[] four = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
			return four[Noise.hash(i.seed(), i.gx(), i.gz(), 31, 4)];
		}
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx > 0 ? Direction.WEST : Direction.EAST;
		}
		return dz > 0 ? Direction.NORTH : Direction.SOUTH;
	}

	private static Set<String> tags(Island i, String hint) {
		Set<String> t = new HashSet<>();
		t.add(i.size().name().toLowerCase(Locale.ROOT));
		t.add(i.biome().id());
		t.add(hint);
		if (i.spawn()) {
			t.add("spawn");
		}
		return t;
	}
}
