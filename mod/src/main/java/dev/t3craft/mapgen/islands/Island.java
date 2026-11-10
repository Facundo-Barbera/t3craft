package dev.t3craft.mapgen.islands;

import dev.t3craft.mapgen.Area;
import dev.t3craft.mapgen.Noise;
import dev.t3craft.mapgen.Slot;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * One floating island: where it is, the shape of its ground and the slots on it. Pure data and functions of the
 * column; the blocks are {@link IslandBlocks}'. {@link IslandField} makes islands (and caches them).
 *
 * <p>Shape (all deterministic from the seed and the cell):
 * <ul>
 *   <li><b>Outline</b>: a radius that wobbles with the angle (harmonics 2, 3 and 5: an oval with lobes), plus
 *       fine edge noise ({@link IslandNoise}), so the edge is ragged and no island is a disc.</li>
 *   <li><b>Underside</b>: a cone (or a bowl: the exponent {@link Form#belly}) from 2 blocks at the edge to about
 *       {@code size.depth()} in the middle, with lumpy noise, and <b>stalactites</b> ({@link #hang}) in patches
 *       and the odd spike; roots or dripstone tips hang under some columns (the biome's {@code tip}).</li>
 *   <li><b>Terraces</b> on large (1 step) and extra large (2 steps) islands: the inner ground rises one block per
 *       step in noisy blobs, never near the edge, so bridges always land on {@link #top}. Each slot's footprint
 *       (and one block round it) is flattened to the level of its centre ({@link Pad}).</li>
 * </ul>
 *
 * @param id     {@code i_<gx>_<gz>}, stable
 * @param top    y of the grass layer at the edge and on the base level (players stand at {@code top + 1});
 *               terraces stand up to two blocks higher ({@link #surfaceY})
 * @param seed   the field's seed
 * @param pads   the flattened footprints of the slots
 * @param slots  where pieces go, in map coordinates (see {@link IslandSlots})
 */
public record Island(String id, int gx, int gz, IslandSize size, IslandBiome biome, int cx, int top, int cz, long seed, Form form,
		List<Pad> pads, List<Slot> slots) {

	/** Largest sum of the outline harmonics' amplitudes (0.12 + 0.08 + 0.05). */
	static final double MAX_LOBE = 0.25;
	/** Edge noise amplitude, in blocks: {@code EDGE_BASE + EDGE_PER_RADIUS * radius} at most. */
	static final double EDGE_BASE = 1.0;
	static final double EDGE_PER_RADIUS = 0.06;
	/** Highest a piece may reach above {@link #top} (slot heights plus terraces), for clearing an island. */
	static final int HEADROOM = 28;

	/**
	 * The shape parameters of one island.
	 *
	 * @param a2 amplitude of harmonic 2 (relative to the radius), {@code p2} its phase; likewise 3 and 5
	 * @param edge     amplitude of the edge noise in blocks
	 * @param belly    exponent of the underside's profile: below 1 a full bowl, above 1 a sharp cone
	 * @param terraces how many terrace steps the inner ground may rise (0..2)
	 * @param flatCore radius round the centre kept at {@link #top} (the spawn island's plaza), 0 for none
	 */
	public record Form(double a2, double p2, double a3, double p3, double a5, double p5, double edge, double belly, int terraces, int flatCore) {
	}

	/** A slot's footprint flattened to {@code top + lift}. */
	public record Pad(Area area, int lift) {
	}

	public Island {
		pads = List.copyOf(pads);
		slots = List.copyOf(slots);
	}

	/** This island with its slots laid out. */
	Island withLayout(List<Pad> newPads, List<Slot> newSlots) {
		return new Island(id, gx, gz, size, biome, cx, top, cz, seed, form, newPads, newSlots);
	}

	public int radius() {
		return size.radius();
	}

	/** The centre on the base level (the spawn island's plaza; other islands may have a terrace there). */
	public BlockPos surface() {
		return new BlockPos(cx, top + 1, cz);
	}

	/** Whether this is the spawn island (cell 0, 0). */
	public boolean spawn() {
		return gx == 0 && gz == 0;
	}

	private static double dist(int dx, int dz) {
		return StrictMath.sqrt((double) dx * dx + (double) dz * dz);
	}

	/** Noise seed of this island's shape. */
	private long shapeSeed() {
		return Noise.mix(seed, gx, gz, 50);
	}

	/** Outline radius towards {@code (dx, dz)} from the centre, before the edge noise. */
	double outline(int dx, int dz) {
		double a = StrictMath.atan2(dz, dx);
		return size.radius() * (1 + form.a2() * StrictMath.sin(2 * a + form.p2()) + form.a3() * StrictMath.sin(3 * a + form.p3())
				+ form.a5() * StrictMath.sin(5 * a + form.p5()));
	}

	/** The edge's distance from the centre in the direction of column {@code (x, z)}: outline plus edge noise. */
	double rim(int x, int z) {
		return outline(x - cx, z - cz) + form.edge() * IslandNoise.signed2(shapeSeed(), x, z, 5.0, 1);
	}

	/** Whether the column has ground; {@code slack} widens the outline (negative: shrinks it). */
	public boolean contains(int x, int z, double slack) {
		return dist(x - cx, z - cz) <= rim(x, z) + slack + 1e-9;
	}

	/** 0 at the centre, 1 at the edge (clamped). */
	double edgeness(int x, int z) {
		return Math.min(1.0, dist(x - cx, z - cz) / Math.max(1.0, rim(x, z)));
	}

	/** Blocks of ground under the base level in the column, grass included: about {@code size.depth()} in the middle, 2 at the edge. */
	public int depth(int x, int z) {
		double t = edgeness(x, z);
		double base = 2 + (size.depth() - 2) * StrictMath.pow(1.0 - t, form.belly());
		double lumps = (IslandNoise.smooth(shapeSeed(), x, z, 6.0, 2) - 0.5) * 4 * (1 - t);
		int d = (int) Math.round(base + lumps);
		return Math.max(2, Math.min(size.depth() + 2, d));
	}

	/** Extra blocks hanging below the underside: stalactites in patches and the odd lone spike. 0 for most columns. */
	public int hang(int x, int z) {
		double t = edgeness(x, z);
		if (t > 0.85) {
			return 0;
		}
		int max = Math.max(2, size.depth() / 3);
		double n = IslandNoise.smooth(shapeSeed(), x, z, 2.6, 4);
		int len = 0;
		if (n > 0.8) {
			len = 1 + (int) ((n - 0.8) / 0.2 * max * (1 - 0.5 * t));
		}
		if (Noise.hash(shapeSeed(), x, z, 5, 23) == 0) {
			len = Math.max(len, 1 + Noise.hash(shapeSeed(), x, z, 6, 3));
		}
		return len;
	}

	/** Longest stalactite an island of this size can have. */
	int maxHang() {
		return Math.max(2, size.depth() / 3) + 1;
	}

	/** Terrace steps of the column before the pads flatten it. */
	int rawLift(int x, int z) {
		if (form.terraces() <= 0) {
			return 0;
		}
		double d = dist(x - cx, z - cz);
		if (d < form.flatCore()) {
			return 0;
		}
		double t = edgeness(x, z);
		if (t > 0.7) {
			return 0;
		}
		double v = IslandNoise.smooth(shapeSeed(), x, z, Math.max(4.0, size.radius() * 0.45), 3) * 0.7 + (1 - t) * 0.5;
		int lift = v > 0.62 ? 1 : 0;
		if (form.terraces() >= 2 && v > 0.8) {
			lift = 2;
		}
		return lift;
	}

	/** Terrace steps of the column: a pad's level inside (or next to) a slot's footprint, else {@link #rawLift}. */
	public int lift(int x, int z) {
		for (Pad p : pads) {
			if (p.area().contains(x, z)) {
				return p.lift();
			}
		}
		for (Pad p : pads) {
			Area a = p.area();
			if (x >= a.minX() - 1 && x <= a.maxX() + 1 && z >= a.minZ() - 1 && z <= a.maxZ() + 1) {
				return p.lift();
			}
		}
		return rawLift(x, z);
	}

	/** y of the surface block of the column (the grass), terraces included. */
	public int surfaceY(int x, int z) {
		return top + lift(x, z);
	}

	/** y of the lowest block of the underside (stalactites not counted). */
	public int bottom(int x, int z) {
		return top - depth(x, z) + 1;
	}

	/** Every column the island can cover. */
	public Area area() {
		int r = size.reach();
		return new Area(cx - r, cz - r, cx + r, cz + r);
	}

	/** Lowest y any block of the island can be at (tips included). */
	public int lowestY() {
		return top - (size.depth() + 2) - maxHang() - 1;
	}

	/** Highest y any block of the island or its pieces can be at. */
	public int highestY() {
		return top + HEADROOM;
	}

	/** Horizontal distance from a column to the island's centre. */
	public double distanceTo(int x, int z) {
		return dist(x - cx, z - cz);
	}
}
