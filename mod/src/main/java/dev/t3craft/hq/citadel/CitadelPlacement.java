package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.y0;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.col;
import static dev.t3craft.hq.citadel.CitadelCanvas.colX;
import static dev.t3craft.hq.citadel.CitadelCanvas.colZ;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

import dev.t3craft.T3Craft;
import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.catalog.Catalog;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.pack.LayoutParams;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The family leader of the citadel: places the variants of the {@link Catalog} in the city once the
 * barrios, empty lots, streets and hub are painted. It only fills space that is still free (plain lot
 * grass, the edges of streets and avenues, the outer rim of the promenade); it never touches a hall,
 * a desk, a workstation, an anchor, a walk spot, the {@code spawn()} or the geometry of the zones.
 * Switched off with the pack parameter {@code catalog.enabled = 0}
 * ({@link LayoutParams#RINGCAMPUS_CATALOG}); then the city is exactly what it was before.
 *
 * <h2>Order ({@link #city})</h2>
 * <ol>
 * <li><b>Corridors</b>: every barrio gets a paved path (3 wide, with a 1-block curb each side) from the
 *     end of its gate arm straight out along {@code -out} to the first cell that is not its lot (the
 *     street or avenue). Painted first, so nothing placed later can block it. With the gatehouse
 *     passage and the courtyard it makes a walkable path at least 2 wide from every hall door to the
 *     street.</li>
 * <li><b>Streets</b> ({@link #streets}): a {@link Family#GATE} where each cardinal avenue enters its
 *     ring of barrios, a {@link Family#PLAZA_CENTER} at the crossings of a ring street and its avenues
 *     when the crossing (plus a 2-block walk ring) fits, {@link Family#LAMP}s at a regular rhythm on
 *     the edges of the avenues and ring streets, and {@link Family#BENCH}es between them. Everything
 *     stands on the edge band; the middle of every street stays clear.</li>
 * <li><b>Lots</b>: a barrio's free space ({@link #barrio}) or an empty lot ({@link #emptyLot}) is
 *     packed with plots by a deterministic {@link Packer}, then the leftover grass gets ground
 *     texture so that plain grass stays under {@value #PLAIN_MAX_PERCENT}% of the lot.</li>
 * <li><b>Hub</b> ({@link #hub}): a {@link Family#STATUE} (else a {@link Family#LAMP}, a brazier) on
 *     the outer rim of the promenade between the ramps, where there is room.</li>
 * </ol>
 *
 * <h2>Rules</h2>
 * Every plot is checked free on the canvas before it is built ({@link CitadelCanvas#zoneAt},
 * {@link CitadelCanvas#at}): in the right zone, on soft ground (grass, moss, an old lattice light) or the
 * street's edge, nothing above it up to its height (azalea bushes excepted: they are cleared), not a
 * hall column, an anchor column or the spawn. When {@link Catalog#pick} has nothing for a family the
 * plot is not used, except for trees (the citadel's cherry tree), gardens (a hedge garden) and
 * planters (the kit's planter). Everything is deterministic: one seed per lot or street position, a
 * fixed scan order, no shared state outside the canvas.
 *
 * <p>Scale: every distance that grows with the citadel is a named size below, a method at the plan's
 * scale ({@code sc/scy/scd/sOdd}, identity at scale 1). Plot requests ({@code req}) ask for frontages and
 * depths times the scale, so a bigger lot gets more and bigger plots, and {@code Variant#fits} accepts
 * plots up to its maximum times the scale; the lamps keep a rhythm of {@code LAMP_STEP()} blocks. See {@code docs/tools/citadel-catalog.md}, section "Placement", for the plot sizes
 * each family is asked for.
 */
public final class CitadelPlacement {
	private CitadelPlacement() {
	}

	// ------------------------------------------------------------------ named sizes (scale rule)

	/** This plan's scale helpers (see {@code CitadelCanvas}): identity at scale 1. */
	private static int sc(int n) {
		return CitadelCanvas.dims().s(n);
	}

	private static int scy(int n) {
		return CitadelCanvas.dims().sy(n);
	}

	private static double scd(double n) {
		return CitadelCanvas.dims().sd(n);
	}

	private static int sOdd(int n) {
		return RingCampusGeometry.sOdd(CitadelCanvas.dims().k, n);
	}

	/** A count of plots at scale 1, times {@code k^1.5} (a lot has k² the area and plots grow by √k, so this fills it a little denser than at scale 1; identity at scale 1; a request that finds no room is skipped). */
	private static int cnt(int n) {
		return (int) Math.round(n * Math.pow(CitadelCanvas.dims().k, 1.5));
	}

	/** A plot request with its sizes at scale 1: frontage and depth grow with {@code s}, the height is given scaled. */
	private static Req req(Family f, int minW, int maxW, int minD, int maxD, int h, Mode mode) {
		return new Req(f, sp(minW), sp(maxW), sp(minD), sp(maxD), h, mode);
	}

	/**
	 * A plot side at scale {@code k}: {@code n · √k}. Catalog variants keep their own proportions and detail, so
	 * the plots grow by the square root and their <i>count</i> by {@code k} ({@link #cnt}): a lot has {@code k²}
	 * times the area, plots {@code k} times, so the fill stays what it is at scale 1 (a lot never looks empty).
	 */
	static int sp(int n) {
		return Math.max(1, (int) Math.round(n * Math.sqrt(CitadelCanvas.dims().k)));
	}

	/**
	 * Whether this plan is big enough for the denser composition of the lots (struct/hq-density): houses with yards
	 * in the barrios, parcels with lanes in the empty lots. From scale 1.5; below it the lots are what they were
	 * (at scale 1 the plan is identical block by block).
	 */
	static boolean dense() {
		return CitadelCanvas.dims().k >= 1.5;
	}

	/** Cells of yard round a house plot: a hedge on the outside, a flower bed or the front terrace inside. */
	static final int YARD = 2;
	/** How far a house's lead path may run towards the nearest path (cells). */
	static int LEAD_MAX() {
		return sc(14);
	}
	/** Height asked for a house plot (the taller variants need 21 at scale 2). */
	static int HOUSE_H() {
		return scy(16);
	}
	/** Half width of a lane (callejón): 1 = 3 wide, with a one-cell curb each side. */
	static final int LANE_HALF = 1;
	/** Spacing of the lamps along a lane. */
	static int LANE_STEP() {
		return sc(6);
	}

	/** Free cells kept between two plots of a lot. */
	static final int MARGIN = 1;
	/** Half width of a barrio's corridor paving: {@code |b| <= 1}, 3 blocks. */
	static int CORRIDOR_HALF() {
		return sc(1);
	}
	/** The curb of the corridor: {@code |b| == 2}. */
	static int CURB() {
		return CORRIDOR_HALF() + 1;
	}
	/** Cells round the gate arm and the halls kept clear: free corners start at {@code court + 1 + CLUSTER_KEEP()}. */
	static int CLUSTER_KEEP() {
		return sc(2);
	}
	/** Longest corridor (cells), a guard: corridors are a few cells long. */
	static int CORRIDOR_MAX() {
		return sc(48);
	}
	/** Spacing of the furniture along a corridor or gate arm. */
	static int FURNITURE_STEP() {
		return sc(4);
	}
	/** Height a lot cell must be clear to take a plot. */
	static int CLEAR_TOP() {
		return scy(14);
	}
	/** Spacing of the lamps along avenues and ring streets (blocks of arc). */
	static int LAMP_STEP() {
		return sc(10);
	}
	/** One bench every this many lamp intervals. */
	static final int BENCH_EVERY = 3;
	/** The avenue's edge band starts at this distance from its axis (the axis half width is 3.4). */
	static double AVENUE_EDGE() {
		return scd(2.5);
	}
	/** Lamp and bench offset from the avenue axis. */
	static double AVENUE_SIDE() {
		return scd(3.0);
	}
	/** Keep street objects this far (blocks) from an avenue axis at a crossing. */
	static double CROSSING_KEEP() {
		return scd(5.0);
	}
	/** Keep street objects this far (Manhattan) from the spawn. */
	static int SPAWN_KEEP() {
		return sc(4);
	}
	/** A gate stands this many cells into the avenue from the ring's inner edge. */
	static final int GATE_SETBACK = 1;
	/** Second try further in, when the first spot is refused (the south gate of ring 1 is next to the spawn). */
	static int GATE_RETRY() {
		return sc(4);
	}
	static int GATE_H() {
		return scy(10);
	}
	/** Plaza centre sides tried, largest first. */
	static int[] PLAZA_SIDES() {
		return new int[] {sOdd(11), sOdd(9), sOdd(7)};
	}
	/** Walkable ring kept round a plaza centre. */
	static int PLAZA_RING() {
		return sc(2);
	}
	/** How far a plaza may slide inwards from the street along its avenue. */
	static int PLAZA_SHIFT() {
		return sc(10);
	}
	static int PLAZA_H() {
		return scy(10);
	}
	/** Side of the paved square of an empty-lot plaza (ring 1; rings 2 and 3 add 2). */
	static int EMPTY_PLAZA() {
		return sOdd(13);
	}
	static int LAMP_H() {
		return scy(5);
	}
	static final int BENCH_H = 3;
	static final int PLANTER_H = 3;
	static int STATUE_H() {
		return scy(8);
	}
	/** Hub statues: distance in from the promenade's outer edge, and angular offset from the light posts. */
	static double HUB_STATUE_IN() {
		return scd(1.5);
	}
	static final double HUB_STATUE_OFFSET = Math.PI / 16;
	/** Ground texture patches (cells per side). */
	static int PATCH() {
		return sc(4);
	}
	/** Target: plain grass on at most this share of a lot. */
	static final int PLAIN_MAX_PERCENT = 30;

	private static final BlockState SHORT_GRASS = b(Blocks.SHORT_GRASS);
	private static final BlockState FERN = b(Blocks.FERN);
	private static final BlockState BUSH = b(Blocks.BUSH);
	private static final BlockState MOSS_CARPET = b(Blocks.MOSS_CARPET);
	private static final BlockState[] FLOWERS = {
			b(Blocks.POPPY), b(Blocks.CORNFLOWER), b(Blocks.ALLIUM), b(Blocks.OXEYE_DAISY),
			b(Blocks.AZURE_BLUET), b(Blocks.LILY_OF_THE_VALLEY), b(Blocks.DANDELION)};
	private static final BlockState[] PETALS = {b(Blocks.PINK_PETALS), b(Blocks.WILDFLOWERS)};
	private static final BlockState[] EARTH = {b(Blocks.COARSE_DIRT), b(Blocks.ROOTED_DIRT), b(Blocks.PODZOL)};

	/** Whether the catalog placement runs for the plan being generated ({@code catalog.enabled}). */
	public static boolean enabled() {
		return LayoutParams.RINGCAMPUS_CATALOG.get() != 0;
	}

	// ------------------------------------------------------------------ city

	/** The state shared by one run: columns taken by street objects, paved by the leader, and the anchors. */
	private static final class City {
		final CitadelCanvas c;
		final Set<Long> taken = new HashSet<>();
		final Set<Long> paved = new HashSet<>();
		final Set<Long> anchorCols = new HashSet<>();

		City(CitadelCanvas c) {
			this.c = c;
			for (Anchor a : c.anchors.values()) {
				anchorCols.add(col(a.block().getX(), a.block().getZ()));
			}
			anchorCols.add(col(CitadelFinish.spawn().getX(), CitadelFinish.spawn().getZ()));
		}
	}

	/** Runs the whole placement on a painted canvas (after the barrios and empty lots, before the build). */
	public static void city(CitadelCanvas c) {
		City city = new City(c);
		int lotCount = RingCampusGeometry.base(c.rings + 1);
		Map<Integer, Integer> corridors = new HashMap<>();
		for (int lot = 0; lot < lotCount; lot++) {
			if (c.lots.get(lot) != null) {
				corridors.put(lot, corridor(city, RingCampusGeometry.lot(lot)));
			}
		}
		streets(city);
		for (int lot = 0; lot < lotCount; lot++) {
			if (c.lots.get(lot) != null) {
				barrio(city, RingCampusGeometry.lot(lot), corridors.get(lot));
			} else {
				emptyLot(city, RingCampusGeometry.lot(lot));
			}
		}
		hub(city);
	}

	// ------------------------------------------------------------------ helpers

	private static long mix(long a, long b) {
		long k = a * 0x9E3779B97F4A7C15L + b;
		k ^= k >>> 31;
		k *= 0xBF58476D1CE4E5B9L;
		k ^= k >>> 29;
		return k;
	}

	private static int hash(long seed, int x, int z, int n) {
		return (int) Math.floorMod(mix(mix(seed, x), z), (long) n);
	}

	private static long lotSeed(int lot) {
		return mix(0xC17ADE1L, lot);
	}

	private static boolean is(@Nullable BlockState s, Block b) {
		return s != null && s.is(b);
	}

	private static boolean clear(@Nullable BlockState s) {
		return s == null || s.isAir();
	}

	/** Clear for a plot: empty, or an azalea bush of the lot ground (cleared before building). */
	private static boolean clearOrBush(@Nullable BlockState s) {
		return clear(s) || is(s, Blocks.FLOWERING_AZALEA_LEAVES);
	}

	/** Lot ground nobody has paved: grass, moss, or a light let into the grass by the lattice. */
	private static boolean softGround(@Nullable BlockState s) {
		return is(s, Blocks.GRASS_BLOCK) || is(s, Blocks.MOSS_BLOCK) || is(s, Blocks.PEARLESCENT_FROGLIGHT);
	}

	/** Whether any variant of {@code f} fits a local {@code w × d × h} box. */
	static boolean fitsAny(Family f, int w, int d, int h) {
		if (w < 1 || d < 1 || h < 1) {
			return false;
		}
		Plot probe = new Plot(0, 0, 0, w, d, h, Direction.NORTH, 0L);
		for (Variant v : Catalog.of(f)) {
			if (v.weight() > 0 && v.fits(probe)) {
				return true;
			}
		}
		return false;
	}

	/** Families the leader can fill without the catalog, and the boxes it can fill. */
	private static boolean hasFallback(Family f, int w, int d, int h) {
		return switch (f) {
			case TREE -> w >= 5 && d >= 5 && h >= 7;
			case GARDEN -> w >= 5 && d >= 5 && h >= 2;
			case PLANTER -> w >= 1 && d >= 1 && h >= 1;
			default -> false;
		};
	}

	private static boolean placeable(Family f, int w, int d, int h) {
		return fitsAny(f, w, d, h) || hasFallback(f, w, d, h);
	}

	/** The cardinal a vector points to (dominant axis). */
	private static Direction cardinalOf(double vx, double vz) {
		if (Math.abs(vx) >= Math.abs(vz)) {
			return vx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return vz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/**
	 * Builds a variant of {@code f} (or its fallback) on the world box {@code sx × sz × h} at
	 * {@code (x, y0, z)} facing {@code front}. Returns false (nothing written) when nothing fits.
	 */
	private static boolean build(CitadelCanvas c, Family f, int x, int y0, int z, int sx, int sz, int h, Direction front, long seed) {
		return build(c, f, x, y0, z, sx, sz, h, front, seed, null);
	}

	/** {@link #build} with weights by variant id that override the catalog's (a quarter that wants more of one design). */
	private static boolean build(CitadelCanvas c, Family f, int x, int y0, int z, int sx, int sz, int h, Direction front, long seed,
			@Nullable Map<String, Integer> weights) {
		Plot p = Plot.ofWorld(x, y0, z, sx, sz, h, front, seed);
		// design/citadel-terraces: a piece never straddles a step of the relief (it would be cut by the retaining wall)
		int terrace = CitadelRelief.halves(c, x, z);
		for (int xx = x; xx < x + sx; xx++) {
			for (int zz = z; zz < z + sz; zz++) {
				if (CitadelRelief.halves(c, xx, zz) != terrace) {
					return false;
				}
			}
		}
		Variant v = Catalog.pick(f, p, weights);
		if (v == null && !hasFallback(f, p.w(), p.d(), h)) {
			return false;
		}
		for (int xx = x; xx < x + sx; xx++) {
			for (int zz = z; zz < z + sz; zz++) {
				for (int y = y0; y < y0 + 3; y++) {
					if (is(c.at(xx, y, zz), Blocks.FLOWERING_AZALEA_LEAVES)) {
						c.set(xx, y, zz, AIR);
					}
				}
			}
		}
		c.rigid(x + sx / 2, z + sz / 2);
		try {
			if (v == null) {
				fallback(c, f, p);
				return true;
			}
			v.build(c, p);
		} catch (RuntimeException e) {
			// a broken variant must not take the city down: what it wrote stays, the gallery shows the error
			T3Craft.LOGGER.warn("[citadel] catalog variant {} failed on {}: {}", v.id(), p, e.toString());
		} finally {
			c.unrigid();
		}
		return true;
	}

	/** The leader's own fillers when the catalog has nothing: cherry tree, hedge garden, kit planter. */
	private static void fallback(CitadelCanvas c, Family f, Plot p) {
		switch (f) {
			case TREE -> {
				BlockPos t = p.world((p.w() - 1) / 2, 0, (p.d() - 1) / 2);
				CitadelGrounds.tree(c, t.getX(), t.getZ());
			}
			case GARDEN -> hedgeGarden(c, p);
			case PLANTER -> {
				for (int u = 0; u < p.w(); u++) {
					p.set(c, u, 0, 0, c.kit.gardenPlanter());
				}
			}
			default -> {
			}
		}
	}

	/**
	 * A garden when the catalog has none: an azalea hedge with a gap at the front, a dirt path down the
	 * middle, beds of rooted dirt with rows of flowers and ferns, a barrel and a composter at the back.
	 */
	private static void hedgeGarden(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int mid = (w - 1) / 2;
		BlockState hedge = leaves(Blocks.AZALEA_LEAVES);
		BlockState bloom = leaves(Blocks.FLOWERING_AZALEA_LEAVES);
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				boolean path = u == mid || (w % 2 == 0 && u == mid + 1);
				boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
				BlockPos g = p.world(u, -1, v);
				boolean lit = is(c.at(g.getX(), g.getY(), g.getZ()), Blocks.PEARLESCENT_FROGLIGHT);
				if (edge && !(path && v == 0)) {
					p.set(c, u, 0, v, p.hash(u, 0, v, 3) == 0 ? bloom : hedge);
					continue;
				}
				if (lit) {
					continue; // keep a light let into the ground and plant nothing on it
				}
				if (path) {
					c.set(g, b(Blocks.DIRT_PATH));
				} else if (v == d - 2 && (u == 1 || u == w - 2)) {
					c.set(g, b(Blocks.ROOTED_DIRT));
					p.set(c, u, 0, v, u == 1 ? b(Blocks.BARREL) : b(Blocks.COMPOSTER));
				} else {
					c.set(g, b(Blocks.ROOTED_DIRT));
					p.set(c, u, 0, v, v % 2 == 1 ? FLOWERS[p.hash(u, 0, v, FLOWERS.length)] : (p.hash(u, 1, v, 3) == 0 ? BUSH : FERN));
				}
			}
		}
	}

	// ------------------------------------------------------------------ packer

	/** How a request ranks the free positions. */
	private enum Mode {
		/** Closest to the site's walk network (paths, plaza, centre). */
		NEAR,
		/** Anywhere, in a seeded order. */
		SCATTER
	}

	/** One plot wanted: a family, its local size range ({@code w} frontage, {@code d} depth), height and ranking. */
	private record Req(Family f, int minW, int maxW, int minD, int maxD, int h, Mode mode, @Nullable Map<String, Integer> weights) {
		Req(Family f, int minW, int maxW, int minD, int maxD, int h, Mode mode) {
			this(f, minW, maxW, minD, maxD, h, mode, null);
		}
	}

	/** A plot that was built: its world box (without the yard) and the way it faces. */
	private record Placed(int x, int z, int sx, int sz, Direction front) {
	}

	/** The lot being furnished: which way a plot at a point faces, and how far that point is from the paths. */
	private interface Site {
		Direction front(double x, double z);

		double pathDist(double x, double z);
	}

	/**
	 * The free cells of one lot as a grid, with the plots taken so far. A plot takes cells that are
	 * free (available, not used, not in another plot's margin) and leaves a {@value #MARGIN}-cell margin
	 * round itself. Rectangle tests are O(1) on prefix sums.
	 */
	private static final class Packer {
		final int x0;
		final int z0;
		final int nx;
		final int nz;
		final boolean[] avail;
		final boolean[] used;
		final boolean[] gap;
		private int[] blocked;
		private int[] usedSum;
		private boolean dirty = true;
		/** When set, only boxes inside this world rectangle fit (a parcel of a lot). */
		private boolean limited;
		private int lx0;
		private int lz0;
		private int lx1;
		private int lz1;

		Packer(int x0, int z0, int x1, int z1) {
			this.x0 = x0;
			this.z0 = z0;
			this.nx = x1 - x0 + 1;
			this.nz = z1 - z0 + 1;
			avail = new boolean[nx * nz];
			used = new boolean[nx * nz];
			gap = new boolean[nx * nz];
		}

		boolean in(int x, int z) {
			return x >= x0 && x < x0 + nx && z >= z0 && z < z0 + nz;
		}

		int i(int x, int z) {
			return (x - x0) * nz + (z - z0);
		}

		boolean isUsed(int x, int z) {
			return in(x, z) && used[i(x, z)];
		}

		private void rebuild() {
			int stride = nz + 1;
			blocked = new int[(nx + 1) * stride];
			usedSum = new int[(nx + 1) * stride];
			for (int ix = 0; ix < nx; ix++) {
				for (int iz = 0; iz < nz; iz++) {
					int k = ix * nz + iz;
					int bl = !avail[k] || used[k] || gap[k] ? 1 : 0;
					int us = used[k] ? 1 : 0;
					int p = (ix + 1) * stride + iz + 1;
					blocked[p] = bl + blocked[ix * stride + iz + 1] + blocked[(ix + 1) * stride + iz] - blocked[ix * stride + iz];
					usedSum[p] = us + usedSum[ix * stride + iz + 1] + usedSum[(ix + 1) * stride + iz] - usedSum[ix * stride + iz];
				}
			}
			dirty = false;
		}

		private int sum(int[] s, int xa, int za, int xb, int zb) {
			int ia = Math.max(0, xa - x0);
			int ja = Math.max(0, za - z0);
			int ib = Math.min(nx - 1, xb - x0);
			int jb = Math.min(nz - 1, zb - z0);
			if (ia > ib || ja > jb) {
				return 0;
			}
			int stride = nz + 1;
			return s[(ib + 1) * stride + jb + 1] - s[ia * stride + jb + 1] - s[(ib + 1) * stride + ja] + s[ia * stride + ja];
		}

		/** Whether the world box {@code sx × sz} at {@code (x, z)} is free with its margin. */
		boolean fits(int x, int z, int sx, int sz) {
			if (!in(x, z) || !in(x + sx - 1, z + sz - 1)) {
				return false;
			}
			if (limited && (x < lx0 || z < lz0 || x + sx - 1 > lx1 || z + sz - 1 > lz1)) {
				return false;
			}
			if (dirty) {
				rebuild();
			}
			return sum(blocked, x, z, x + sx - 1, z + sz - 1) == 0
					&& sum(usedSum, x - MARGIN, z - MARGIN, x + sx - 1 + MARGIN, z + sz - 1 + MARGIN) == 0;
		}

		/** From now on only boxes inside the world rectangle {@code (x0, z0)..(x1, z1)} (inclusive) fit. */
		void limit(int x0, int z0, int x1, int z1) {
			limited = true;
			lx0 = x0;
			lz0 = z0;
			lx1 = x1;
			lz1 = z1;
		}

		void unlimit() {
			limited = false;
		}

		/** The cell is paved or planted by the leader: no plot may take it any more. */
		void block(int x, int z) {
			if (in(x, z)) {
				avail[i(x, z)] = false;
				dirty = true;
			}
		}

		/** Marks the box used and its margin as gap. */
		void take(int x, int z, int sx, int sz) {
			for (int xx = x - MARGIN; xx < x + sx + MARGIN; xx++) {
				for (int zz = z - MARGIN; zz < z + sz + MARGIN; zz++) {
					if (!in(xx, zz)) {
						continue;
					}
					boolean inside = xx >= x && xx < x + sx && zz >= z && zz < z + sz;
					if (inside) {
						used[i(xx, zz)] = true;
					} else {
						gap[i(xx, zz)] = true;
					}
				}
			}
			dirty = true;
		}
	}

	/** The world sizes to try for a request: a seeded preferred size first, then every size by area, both orientations. */
	private static List<int[]> sizes(Req r, Random rng) {
		LinkedHashSet<Long> seen = new LinkedHashSet<>();
		List<int[]> out = new ArrayList<>();
		int pw = r.minW() + rng.nextInt(r.maxW() - r.minW() + 1);
		int pd = r.minD() + rng.nextInt(r.maxD() - r.minD() + 1);
		List<int[]> all = new ArrayList<>();
		all.add(new int[] {pw, pd});
		List<int[]> rest = new ArrayList<>();
		for (int w = r.maxW(); w >= r.minW(); w--) {
			for (int d = r.maxD(); d >= r.minD(); d--) {
				rest.add(new int[] {w, d});
			}
		}
		rest.sort((a, b) -> Integer.compare(b[0] * b[1], a[0] * a[1]));
		all.addAll(rest);
		for (int[] wd : all) {
			for (int o = 0; o < 2; o++) {
				int sx = o == 0 ? wd[0] : wd[1];
				int sz = o == 0 ? wd[1] : wd[0];
				if (seen.add(((long) sx << 32) | sz)) {
					out.add(new int[] {sx, sz});
				}
			}
		}
		return out;
	}

	/** Places one plot for {@code r} on the best free position of {@code pk}; false when nothing fits. */
	private static boolean place(CitadelCanvas c, Packer pk, Site site, Req r, long seed, int salt) {
		return placeBox(c, pk, site, r, seed, salt, 0) != null;
	}

	/**
	 * {@link #place} that reserves {@code pad} more cells all round the plot (a yard: the caller dresses them) and
	 * returns the box built, or null when nothing fits.
	 */
	private static @Nullable Placed placeBox(CitadelCanvas c, Packer pk, Site site, Req r, long seed, int salt, int pad) {
		long s = mix(seed, salt);
		for (int[] size : sizes(r, new Random(s))) {
			int sx = size[0];
			int sz = size[1];
			if (!placeable(r.f(), sx, sz, r.h()) && !placeable(r.f(), sz, sx, r.h())) {
				continue; // nothing in the family (nor a fallback) for this size in either orientation: skip the scan
			}
			int bx = 0;
			int bz = 0;
			Direction bf = null;
			double best = Double.MAX_VALUE;
			for (int x = pk.x0; x + sx <= pk.x0 + pk.nx; x++) {
				for (int z = pk.z0; z + sz <= pk.z0 + pk.nz; z++) {
					if (!pk.fits(x - pad, z - pad, sx + 2 * pad, sz + 2 * pad)) {
						continue;
					}
					double cx = x + (sx - 1) / 2.0;
					double cz = z + (sz - 1) / 2.0;
					Direction fr = site.front(cx, cz);
					boolean side = fr.getAxis() == Direction.Axis.X;
					if (!placeable(r.f(), side ? sz : sx, side ? sx : sz, r.h())) {
						continue;
					}
					double tie = hash(s, x, z, 1000) / 1000.0;
					double score = r.mode() == Mode.NEAR ? site.pathDist(cx, cz) + tie : tie;
					if (score < best) {
						best = score;
						bx = x;
						bz = z;
						bf = fr;
					}
				}
			}
			if (bf != null && build(c, r.f(), bx, 1, bz, sx, sz, r.h(), bf, mix(s, bx * 31L + bz), r.weights())) {
				pk.take(bx - pad, bz - pad, sx + 2 * pad, sz + 2 * pad);
				return new Placed(bx, bz, sx, sz, bf);
			}
		}
		return null;
	}

	/** The packer of a lot: every column of the lot that passes {@link #lotFree} and {@code reserved} says is not reserved. */
	private static Packer packer(City city, Lot l, java.util.function.BiPredicate<Integer, Integer> reserved) {
		List<Long> cols = city.c.lotCols.getOrDefault(l.id(), List.of());
		int x0 = Integer.MAX_VALUE;
		int z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int z1 = Integer.MIN_VALUE;
		for (long k : cols) {
			x0 = Math.min(x0, colX(k));
			z0 = Math.min(z0, colZ(k));
			x1 = Math.max(x1, colX(k));
			z1 = Math.max(z1, colZ(k));
		}
		if (cols.isEmpty()) {
			return new Packer(0, 0, 0, 0);
		}
		Packer pk = new Packer(x0, z0, x1, z1);
		for (long k : cols) {
			int x = colX(k);
			int z = colZ(k);
			pk.avail[pk.i(x, z)] = !reserved.test(x, z) && lotFree(city, l.id(), x, z, CLEAR_TOP());
		}
		return pk;
	}

	/**
	 * A lot column a plot may take: in the lot with all its 8 neighbours (a cell of margin from the
	 * streets), not a hall, an anchor or the spawn, not paved by the leader, soft ground, clear above.
	 */
	private static boolean lotFree(City city, int lot, int x, int z, int top) {
		CitadelCanvas c = city.c;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Zone zn = c.zoneAt(x + dx, z + dz);
				if (zn.kind() != Kind.LOT || zn.lot() != lot) {
					return false;
				}
			}
		}
		long k = col(x, z);
		if (c.hallCols.containsKey(k) || city.anchorCols.contains(k) || city.paved.contains(k)) {
			return false;
		}
		if (!softGround(c.at(x, 0, z))) {
			return false;
		}
		for (int y = 1; y <= top; y++) {
			if (!clearOrBush(c.at(x, y, z))) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ barrios

	/** World direction of a step along the lot's {@code a} ({@code out}) or {@code b} ({@code across}) axis. */
	private static Direction lotDir(Lot l, double da, double db) {
		if (Math.abs(da) >= Math.abs(db)) {
			return da >= 0 ? l.out() : l.out().getOpposite();
		}
		return db >= 0 ? l.across() : l.across().getOpposite();
	}

	private static double la(Lot l, double x, double z) {
		return (x - l.centre().getX()) * l.out().getStepX() + (z - l.centre().getZ()) * l.out().getStepZ();
	}

	private static double lb(Lot l, double x, double z) {
		return (x - l.centre().getX()) * l.across().getStepX() + (z - l.centre().getZ()) * l.across().getStepZ();
	}

	/**
	 * Paints a barrio's corridor: from just past the end of its gate arm ({@code a = -(reach + 1)})
	 * along {@code -out} until the lot ends, accent paving 3 wide and a {@code FOOT} curb each side.
	 * Returns its length in cells.
	 */
	private static int corridor(City city, Lot l) {
		CitadelCanvas c = city.c;
		int start = l.reach() + 1;
		int len = 0;
		while (len < CORRIDOR_MAX()) {
			BlockPos p = l.at(-(start + len), 0, 0);
			Zone zn = c.zoneAt(p.getX(), p.getZ());
			if (zn.kind() != Kind.LOT || zn.lot() != l.id()) {
				break;
			}
			len++;
		}
		for (int s = 0; s < len; s++) {
			// struct/hq-density: the last two rows open out one cell each side where the corridor meets the street
			int flare = dense() && s >= len - 2 ? 1 : 0;
			for (int bb = -(CURB() + flare); bb <= CURB() + flare; bb++) {
				BlockPos p = l.at(-(start + s), bb, 0);
				int x = p.getX();
				int z = p.getZ();
				Zone zn = c.zoneAt(x, z);
				if (zn.kind() != Kind.LOT || zn.lot() != l.id() || c.hallCols.containsKey(col(x, z))) {
					continue;
				}
				c.set(x, 0, z, Math.abs(bb) <= CORRIDOR_HALF() + flare ? c.accent() : FOOT);
				for (int y = 1; y <= 3; y++) {
					if (is(c.at(x, y, z), Blocks.FLOWERING_AZALEA_LEAVES)) {
						c.set(x, y, z, AIR);
					}
				}
				city.paved.add(col(x, z));
			}
		}
		return len;
	}

	/**
	 * A barrio's free space: lamps, benches and planters along its corridor and gate arm, then a
	 * fountain or well, annexes and gardens near the paths, trees scattered, small gardens and
	 * furniture in what is left, then ground texture. The courtyard, the halls, the gatehouse, the
	 * paved arms and a {@code CLUSTER_KEEP()}-cell walk round them, and the corridor stay as they are.
	 */
	private static void barrio(City city, Lot l, int corridorLen) {
		CitadelCanvas c = city.c;
		int g = l.court();
		int reach = l.reach();
		int box = reach + 1;
		int corner = g + 1 + CLUSTER_KEEP();
		int keep = CURB() + 1;
		long seed = lotSeed(l.id());
		Packer pk = packer(city, l, (x, z) -> {
			int a = l.a(x, z);
			int bb = l.b(x, z);
			if (Math.abs(a) <= box && Math.abs(bb) <= box) {
				return Math.min(Math.abs(a), Math.abs(bb)) < corner;
			}
			return a < -box && Math.abs(bb) <= keep;
		});
		Site site = new Site() {
			@Override
			public Direction front(double x, double z) {
				double a = la(l, x, z);
				double bb = lb(l, x, z);
				if (Math.abs(a) <= box && Math.abs(bb) <= box) {
					// a corner square: face the gate arm (inner corners) or the nearer arm (outer corners)
					if (a < 0 || Math.abs(bb) <= Math.abs(a)) {
						return lotDir(l, 0, -bb);
					}
					return lotDir(l, -a, 0);
				}
				if (a < -box && Math.abs(bb) <= box) {
					return lotDir(l, 0, -bb); // beside the corridor: face it
				}
				double ca = Math.max(-box, Math.min(box, a));
				double cb = Math.max(-box, Math.min(box, bb));
				return lotDir(l, ca - a, cb - bb);
			}

			@Override
			public double pathDist(double x, double z) {
				double a = la(l, x, z);
				double bb = lb(l, x, z);
				double toCorridor = a < -box ? Math.abs(bb) : Math.hypot(a + box, bb);
				double ca = Math.max(-box, Math.min(box, a));
				double cb = Math.max(-box, Math.min(box, bb));
				double toCluster = Math.hypot(ca - a, cb - bb);
				if (Math.abs(a) <= box && Math.abs(bb) <= box) {
					toCluster = Math.min(Math.abs(a), Math.abs(bb)) - g;
				}
				return Math.min(toCorridor, toCluster);
			}
		};
		int salt = 0;
		// furniture along the corridor: lamps alternating with benches and planters, both sides
		for (int s = 1; s < corridorLen - 1; s += FURNITURE_STEP()) {
			int step = s / FURNITURE_STEP();
			for (int side = -1; side <= 1; side += 2) {
				int a = -(box + s);
				int bb = side * keep;
				Direction face = lotDir(l, 0, -side);
				if ((step + (side > 0 ? 0 : 1)) % 2 == 0) {
					furniture(city, pk, l, Family.LAMP, a, a, bb, LAMP_H(), face, mix(seed, salt++));
				} else if (step % 4 < 2) {
					if (!furniture(city, pk, l, Family.BENCH, a - 1, a + 1, bb, BENCH_H, face, mix(seed, salt++))) {
						furniture(city, pk, l, Family.PLANTER, a, a, bb, PLANTER_H, face, mix(seed, salt++));
					}
				} else {
					furniture(city, pk, l, Family.PLANTER, a, a, bb, PLANTER_H, face, mix(seed, salt++));
				}
			}
		}
		// lamps along the gate arm, between the gatehouse and the end of the arm
		for (int a = -(g + l.gateD() + 2); a >= -(reach - 1); a -= FURNITURE_STEP()) {
			for (int side = -1; side <= 1; side += 2) {
				furniture(city, pk, l, Family.LAMP, a, a, side * (g + 2), LAMP_H(), lotDir(l, 0, -side), mix(seed, salt++));
			}
		}
		int ring = l.ring();
		List<Req> reqs = new ArrayList<>();
		if (hash(seed, 1, 0, 2) == 0) {
			reqs.add(req(Family.FOUNTAIN, 5, 7, 5, 7, scy(7), Mode.NEAR));
		} else {
			reqs.add(req(Family.WELL, 3, 5, 3, 5, scy(8), Mode.NEAR));
		}
		if (dense()) {
			// dwellings that grow with the lot: houses of the catalog (their frontage up to 15 · √k) with a yard each,
			// in the corner squares and the belt, before the annexes take the room
			for (int i = 0; i < (ring == 1 ? 2 : 3); i++) {
				reqs.add(new Req(Family.HOUSE, 9, sp(15), 9, sp(15), HOUSE_H(), Mode.NEAR));
			}
		}
		for (int i = 0; i < cnt((ring == 1 ? 2 : 3)); i++) {
			reqs.add(req(Family.ANNEX, 5, 8, 5, 7, scy(10), Mode.NEAR));
		}
		for (int i = 0; i < cnt((ring == 1 ? 2 : 3)); i++) {
			reqs.add(req(Family.GARDEN, 6, 11, 5, 9, scy(5), Mode.NEAR));
		}
		for (int i = 0; i < cnt(4 + 2 * ring); i++) {
			reqs.add(req(Family.TREE, 5, 7, 5, 7, CLEAR_TOP(), Mode.SCATTER));
		}
		for (int i = 0; i < cnt(2); i++) {
			reqs.add(req(Family.GARDEN, 5, 7, 5, 7, scy(5), Mode.SCATTER));
		}
		for (int i = 0; i < cnt(3); i++) {
			reqs.add(new Req(Family.BENCH, 2, 3, 1, 1, BENCH_H, Mode.NEAR));
		}
		for (int i = 0; i < cnt(6); i++) {
			reqs.add(new Req(Family.PLANTER, 1, 2, 1, 1, PLANTER_H, Mode.NEAR));
		}
		for (Req r : reqs) {
			placeReq(city, pk, site, r, seed, salt++);
		}
		texture(city, l, pk, seed);
	}

	/** Places one request: a house also gets its yard, anything else is just built. */
	private static void placeReq(City city, Packer pk, Site site, Req r, long seed, int salt) {
		if (r.f() != Family.HOUSE) {
			place(city.c, pk, site, r, seed, salt);
			return;
		}
		Placed pl = placeBox(city.c, pk, site, r, seed, salt, YARD);
		if (pl != null) {
			yard(city, pk, pl, mix(seed, salt));
		}
	}

	/**
	 * The yard of a house: the {@value #YARD} cells all round it. On the outside a low azalea hedge with a gate in
	 * front (two cherry posts with a lantern each, a 3-wide opening on the door's axis); inside it the front terrace
	 * (stone bricks, a barrel and a planter at its ends) and on the sides and back a flower bed. From the gate a dirt
	 * path runs on towards the nearest paved path, as far as the ground is free ({@link #leadCell}). All of it on
	 * cells the packer reserved with the house, so nothing else was going to be there.
	 */
	private static void yard(City city, Packer pk, Placed pl, long seed) {
		CitadelCanvas c = city.c;
		Plot yp = yardRing(c, pl, seed);
		int mid = (yp.w() - 1) / 2;
		// the lead path: 3 wide from the gate, away from the house, until something else is in the way
		for (int t = 1; t <= LEAD_MAX(); t++) {
			BlockPos m = yp.world(mid, -1, -t);
			if (leadCell(city, pk, m.getX(), m.getZ()) != 0) {
				break;
			}
			for (int du = -1; du <= 1; du += 2) {
				BlockPos s = yp.world(mid + du, -1, -t);
				leadCell(city, pk, s.getX(), s.getZ());
			}
		}
	}

	/** Whether the plan is dense enough for the barrios' denser composition (see {@link #dense}); public for the barrio package. */
	public static boolean isDense() {
		return dense();
	}

	/**
	 * A house of the catalog on an unused hall slot of a barrio (struct/hq-density): the world box {@code sx × sz} at
	 * {@code (x, z)} facing {@code front}, with its yard ({@link #yardRing}) all round it. False (nothing written)
	 * when no house fits that box. The box includes the yard: the house takes {@value #YARD} cells less each side.
	 */
	public static boolean slotHouse(CitadelCanvas c, int x, int z, int sx, int sz, Direction front, long seed) {
		int in = YARD;
		if (sx - 2 * in < 9 || sz - 2 * in < 9) {
			return false;
		}
		if (!build(c, Family.HOUSE, x + in, 1, z + in, sx - 2 * in, sz - 2 * in, HOUSE_H(), front, seed)) {
			return false;
		}
		yardRing(c, new Placed(x + in, z + in, sx - 2 * in, sz - 2 * in, front), seed);
		return true;
	}

	/** The ring of cells of a house's yard (see {@link #yard}) without the lead path; returns the yard's plot. */
	private static Plot yardRing(CitadelCanvas c, Placed pl, long seed) {
		Plot yp = Plot.ofWorld(pl.x() - YARD, 1, pl.z() - YARD, pl.sx() + 2 * YARD, pl.sz() + 2 * YARD, 4, pl.front(), seed);
		int w = yp.w();
		int d = yp.d();
		int mid = (w - 1) / 2;
		BlockState hedge = leaves(Blocks.AZALEA_LEAVES);
		BlockState bloom = leaves(Blocks.FLOWERING_AZALEA_LEAVES);
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				boolean ring2 = u == 0 || v == 0 || u == w - 1 || v == d - 1;
				boolean ring1 = u == 1 || v == 1 || u == w - 2 || v == d - 2;
				if (!ring2 && !ring1) {
					continue;
				}
				BlockPos g = yp.world(u, -1, v);
				if (!softGround(c.at(g.getX(), g.getY(), g.getZ())) || !clearOrBush(c.at(g.getX(), g.getY() + 1, g.getZ()))) {
					continue;
				}
				if (ring2) {
					boolean gate = v == 0 && Math.abs(u - mid) <= 1;
					boolean post = v == 0 && Math.abs(u - mid) == 2;
					if (gate) {
						c.set(g, b(Blocks.DIRT_PATH));
					} else if (post) {
						c.set(g, FOOT);
						yp.set(c, u, 0, v, b(Blocks.CHERRY_FENCE));
						yp.set(c, u, 1, v, b(Blocks.CHERRY_FENCE));
						yp.set(c, u, 2, v, Blocks.LANTERN.defaultBlockState());
					} else {
						yp.set(c, u, 0, v, yp.hash(u, 0, v, 4) == 0 ? bloom : hedge);
						if ((u == 0 || u == w - 1) && (v == 0 || v == d - 1)) {
							yp.set(c, u, 1, v, hedge);
						}
					}
				} else if (v == 1) {
					// the front terrace: stone bricks, a barrel and a planter at its two ends
					int roll = yp.hash(u, 0, v, 10);
					c.set(g, roll < 6 ? CitadelPaving.BRICKS : roll < 8 ? CitadelPaving.CRACKED : CitadelPaving.MOSSY);
					if (u == 1) {
						yp.set(c, u, 0, v, b(Blocks.BARREL));
					} else if (u == w - 2) {
						yp.set(c, u, 0, v, c.kit.gardenPlanter());
					}
				} else {
					c.set(g, b(Blocks.ROOTED_DIRT));
					yp.set(c, u, 0, v, yp.hash(u, 1, v, 4) == 0 ? BUSH : FLOWERS[yp.hash(u, 2, v, FLOWERS.length)]);
				}
			}
		}
		return yp;
	}

	/** One cell of a lead path: dirt path on free soft ground. 0 painted; 1 something is in the way (path, plot, wall, edge). */
	private static int leadCell(City city, Packer pk, int x, int z) {
		CitadelCanvas c = city.c;
		long k = col(x, z);
		if (!pk.in(x, z) || pk.isUsed(x, z) || city.paved.contains(k) || city.taken.contains(k) || city.anchorCols.contains(k)
				|| c.hallCols.containsKey(k)) {
			return 1;
		}
		if (!softGround(c.at(x, 0, z)) || !clearOrBush(c.at(x, 1, z)) || !clearOrBush(c.at(x, 2, z))) {
			return 1;
		}
		c.set(x, 0, z, b(Blocks.DIRT_PATH));
		for (int y = 1; y <= 2; y++) {
			if (is(c.at(x, y, z), Blocks.FLOWERING_AZALEA_LEAVES)) {
				c.set(x, y, z, AIR);
			}
		}
		city.paved.add(k);
		pk.block(x, z);
		return 0;
	}

	/**
	 * One piece of furniture on the lot cells {@code a1..a2} at {@code b} (a row along {@code out}),
	 * when every cell is free; marks it in the packer.
	 */
	private static boolean furniture(City city, Packer pk, Lot l, Family f, int a1, int a2, int bb, int h, Direction face, long seed) {
		return furnitureBox(city, pk, l, f, a1, a2, bb, bb, h, face, seed);
	}

	/** {@link #furniture} on the box of lot cells {@code a1..a2} × {@code b1..b2} (a row along either axis). */
	private static boolean furnitureBox(City city, Packer pk, Lot l, Family f, int a1, int a2, int b1, int b2, int h, Direction face, long seed) {
		CitadelCanvas c = city.c;
		int x0 = Integer.MAX_VALUE;
		int z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int z1 = Integer.MIN_VALUE;
		for (int a = a1; a <= a2; a++) {
			for (int bb = b1; bb <= b2; bb++) {
				BlockPos p = l.at(a, bb, 0);
				if (!lotFree(city, l.id(), p.getX(), p.getZ(), h) || pk.isUsed(p.getX(), p.getZ())) {
					return false;
				}
				x0 = Math.min(x0, p.getX());
				z0 = Math.min(z0, p.getZ());
				x1 = Math.max(x1, p.getX());
				z1 = Math.max(z1, p.getZ());
			}
		}
		int sx = x1 - x0 + 1;
		int sz = z1 - z0 + 1;
		boolean side = face.getAxis() == Direction.Axis.X;
		if (!placeable(f, side ? sz : sx, side ? sx : sz, h)) {
			return false;
		}
		if (!build(c, f, x0, 1, z0, sx, sz, h, face, seed)) {
			return false;
		}
		pk.take(x0, z0, sx, sz);
		return true;
	}

	// ------------------------------------------------------------------ empty lots

	/**
	 * A lot with no project: one of three compositions by the lot's seed. 0, a park: a large garden and
	 * a fountain at the centre, trees round them, benches. 1, a small plaza: a paved square at the
	 * centre with a plaza centrepiece (else a fountain, else a tree), lamps on its corners, benches,
	 * trees and gardens round it. 2, an orchard: a well at the centre, many trees, two gardens.
	 */
	private static void emptyLot(City city, Lot l) {
		CitadelCanvas c = city.c;
		long seed = lotSeed(l.id());
		int cx = l.centre().getX();
		int cz = l.centre().getZ();
		Packer pk = packer(city, l, (x, z) -> false);
		Site site = new Site() {
			@Override
			public Direction front(double x, double z) {
				return cardinalOf(cx - x, cz - z);
			}

			@Override
			public double pathDist(double x, double z) {
				return Math.hypot(x - cx, z - cz);
			}
		};
		int ring = l.ring();
		if (dense() && hash(seed, 3, 0, 8) != 0) {
			// struct/hq-density: seven lots in eight are a block of parcels with a use each; one in eight stays a park
			compound(city, l, pk, seed);
			texture(city, l, pk, seed);
			return;
		}
		int kind = hash(seed, 2, 0, 3);
		int salt = 100;
		List<Req> reqs = new ArrayList<>();
		if (kind == 1 && plaza(city, pk, l, seed)) {
			for (int i = 0; i < cnt(4); i++) {
				reqs.add(new Req(Family.BENCH, 2, 3, 1, 1, BENCH_H, Mode.NEAR));
			}
			for (int i = 0; i < cnt(2); i++) {
				reqs.add(req(Family.GARDEN, 6, 10, 5, 8, scy(5), Mode.NEAR));
			}
			for (int i = 0; i < cnt(6 + ring); i++) {
				reqs.add(req(Family.TREE, 5, 7, 5, 7, CLEAR_TOP(), Mode.NEAR));
			}
		} else if (kind == 2) {
			reqs.add(req(Family.WELL, 3, 5, 3, 5, scy(8), Mode.NEAR));
			for (int i = 0; i < cnt(10 + 2 * ring); i++) {
				reqs.add(req(Family.TREE, 5, 6, 5, 6, CLEAR_TOP(), Mode.NEAR));
			}
			for (int i = 0; i < cnt(2); i++) {
				reqs.add(req(Family.GARDEN, 6, 10, 5, 8, scy(5), Mode.SCATTER));
			}
			for (int i = 0; i < cnt(2); i++) {
				reqs.add(new Req(Family.BENCH, 2, 3, 1, 1, BENCH_H, Mode.NEAR));
			}
		} else {
			reqs.add(req(Family.FOUNTAIN, 5, 9, 5, 9, scy(7), Mode.NEAR));
			reqs.add(req(Family.GARDEN, 8, 12, 7, 12, scy(5), Mode.NEAR));
			for (int i = 0; i < cnt(3); i++) {
				reqs.add(new Req(Family.BENCH, 2, 3, 1, 1, BENCH_H, Mode.NEAR));
			}
			for (int i = 0; i < cnt(6 + 2 * ring); i++) {
				reqs.add(req(Family.TREE, 5, 7, 5, 7, CLEAR_TOP(), Mode.SCATTER));
			}
			for (int i = 0; i < cnt(2); i++) {
				reqs.add(req(Family.GARDEN, 5, 8, 5, 8, scy(5), Mode.SCATTER));
			}
		}
		for (int i = 0; i < cnt(4); i++) {
			reqs.add(new Req(Family.PLANTER, 1, 2, 1, 1, PLANTER_H, Mode.SCATTER));
		}
		for (Req r : reqs) {
			place(c, pk, site, r, seed, salt++);
		}
		texture(city, l, pk, seed);
	}

	// ------------------------------------------------------------------ compound lots (struct/hq-density)

	private static final BlockState COBBLE = b(Blocks.COBBLESTONE);
	private static final BlockState MOSSY_COBBLE = b(Blocks.MOSSY_COBBLESTONE);
	private static final BlockState GRAVEL = b(Blocks.GRAVEL);
	/** Uses a parcel can have: vegetable garden, workshop, patio house, sheds, market. */
	private static final int PROGRAMS = 5;

	private static boolean inLot(CitadelCanvas c, Lot l, int x, int z) {
		Zone zn = c.zoneAt(x, z);
		return zn.kind() == Kind.LOT && zn.lot() == l.id();
	}

	/**
	 * A lot with no project at a scale that can afford it: a small plaza at the centre, two lanes (callejones, 3
	 * wide with a curb, cobble / gravel / stone brick) crossing there from street to street and avenue to avenue,
	 * lamps along them, and four parcels, one per quadrant, each with its own use ({@link #quadrant}): a vegetable
	 * garden, a workshop with its house, a patio house, sheds and annexes, a market. The program of a quadrant is
	 * the lot's seed plus two per quadrant, so the four are always different. Nothing wide is left for a forest.
	 */
	private static void compound(City city, Lot l, Packer pk, long seed) {
		plaza(city, pk, l, seed);
		Set<Long> mine = new HashSet<>();
		lane(city, l, pk, mine, true, seed);
		lane(city, l, pk, mine, false, mix(seed, 1));
		laneLamps(city, pk, l, seed);
		int base = hash(seed, 7, 0, PROGRAMS);
		for (int q = 0; q < 4; q++) {
			quadrant(city, pk, l, (q & 1) == 0 ? 1 : -1, (q & 2) == 0 ? 1 : -1, (base + 2 * q) % PROGRAMS, mix(seed, 40 + q), 300 + 50 * q);
		}
		pk.unlimit();
	}

	/**
	 * A lane of the lot along its a-axis ({@code alongA}) or b-axis, through the centre, out both ways until the lot
	 * ends. Its last two rows open out one cell each side where it meets a street or an avenue.
	 */
	private static void lane(City city, Lot l, Packer pk, Set<Long> mine, boolean alongA, long seed) {
		CitadelCanvas c = city.c;
		for (int dir = -1; dir <= 1; dir += 2) {
			for (int t = dir < 0 ? 1 : 0; t < CORRIDOR_MAX(); t++) {
				int at = dir * t;
				BlockPos mid = alongA ? l.at(at, 0, 0) : l.at(0, at, 0);
				if (!inLot(c, l, mid.getX(), mid.getZ())) {
					break;
				}
				BlockPos ahead = alongA ? l.at(at + 2 * dir, 0, 0) : l.at(0, at + 2 * dir, 0);
				int half = inLot(c, l, ahead.getX(), ahead.getZ()) ? LANE_HALF : LANE_HALF + 1;
				for (int o = -(half + 1); o <= half + 1; o++) {
					BlockPos p = alongA ? l.at(at, o, 0) : l.at(o, at, 0);
					laneCell(city, pk, l, mine, p.getX(), p.getZ(), Math.abs(o), half, seed);
				}
			}
		}
	}

	/** One cell of a lane: {@code off} cells from its axis; lane paving up to {@code half}, a curb just outside it. */
	private static void laneCell(City city, Packer pk, Lot l, Set<Long> mine, int x, int z, int off, int half, long seed) {
		CitadelCanvas c = city.c;
		if (!inLot(c, l, x, z)) {
			return;
		}
		long k = col(x, z);
		boolean own = mine.contains(k);
		if (!own && (city.paved.contains(k) || city.taken.contains(k) || city.anchorCols.contains(k) || c.hallCols.containsKey(k)
				|| !softGround(c.at(x, 0, z)))) {
			return;
		}
		// the verge of a lot lays a slab or a bush on the first rows against the street: the lane takes those away
		BlockState above = c.at(x, 1, z);
		if (!clearOrBush(above) && !(above.getBlock() instanceof SlabBlock)) {
			return;
		}
		if (above != null && !above.isAir()) {
			c.set(x, 1, z, AIR);
		}
		int roll = hash(seed, x, z, 10);
		BlockState s;
		if (off > half) {
			s = roll == 0 ? CitadelPaving.KERB_CRACKED : FOOT;
		} else if (off == 0) {
			s = roll < 5 ? COBBLE : roll < 8 ? MOSSY_COBBLE : GRAVEL;
		} else {
			s = roll < 6 ? CitadelPaving.BRICKS : roll < 8 ? CitadelPaving.CRACKED : CitadelPaving.MOSSY;
		}
		c.set(x, 0, z, s);
		mine.add(k);
		city.paved.add(k);
		pk.block(x, z);
	}

	/** Lamps along both lanes every {@code LANE_STEP()} cells, alternating sides, just outside the curb. */
	private static void laneLamps(City city, Packer pk, Lot l, long seed) {
		int salt = 0;
		int side = LANE_HALF + 2;
		for (int t = LANE_STEP(), n = 0; t <= sc(60); t += LANE_STEP(), n++) {
			int sgn = n % 2 == 0 ? 1 : -1;
			for (int dir = -1; dir <= 1; dir += 2) {
				furnitureBox(city, pk, l, Family.LAMP, dir * t, dir * t, sgn * side, sgn * side, LAMP_H(), lotDir(l, 0, -sgn), mix(seed, 600 + salt++));
				furnitureBox(city, pk, l, Family.LAMP, sgn * side, sgn * side, dir * t, dir * t, LAMP_H(), lotDir(l, -sgn, 0), mix(seed, 600 + salt++));
			}
		}
	}

	/**
	 * One parcel of a compound lot: the quadrant {@code (sa, sb)} of the lot's frame outside the lanes, furnished
	 * for use {@code prog}. Every plot faces the nearer lane and goes as close to it as it can; houses bring their yard.
	 */
	private static void quadrant(City city, Packer pk, Lot l, int sa, int sb, int prog, long seed, int salt) {
		int m = LANE_HALF + 2;
		int far = sc(120);
		BlockPos p1 = l.at(sa * m, sb * m, 0);
		BlockPos p2 = l.at(sa * far, sb * far, 0);
		pk.limit(Math.min(p1.getX(), p2.getX()), Math.min(p1.getZ(), p2.getZ()), Math.max(p1.getX(), p2.getX()), Math.max(p1.getZ(), p2.getZ()));
		Site site = new Site() {
			@Override
			public Direction front(double x, double z) {
				double a = la(l, x, z);
				double bb = lb(l, x, z);
				return Math.abs(bb) <= Math.abs(a) ? lotDir(l, 0, -bb) : lotDir(l, -a, 0);
			}

			@Override
			public double pathDist(double x, double z) {
				return Math.min(Math.abs(la(l, x, z)), Math.abs(lb(l, x, z)));
			}
		};
		List<Req> reqs = new ArrayList<>();
		switch (prog) {
			case 0 -> {
				// huerto: raised beds, a shed, a well, a stall for the produce, a few fruit trees
				for (int i = 0; i < 3; i++) {
					reqs.add(req(Family.GARDEN, 7, 12, 7, 12, scy(5), Mode.NEAR));
				}
				reqs.add(req(Family.ANNEX, 5, 8, 5, 7, scy(10), Mode.NEAR));
				reqs.add(req(Family.WELL, 3, 5, 3, 5, scy(8), Mode.NEAR));
				reqs.add(req(Family.STALL, 3, 6, 3, 5, scy(6), Mode.NEAR));
				for (int i = 0; i < 3; i++) {
					reqs.add(req(Family.TREE, 5, 7, 5, 7, CLEAR_TOP(), Mode.SCATTER));
				}
			}
			case 1 -> {
				// taller: a forge or atelier house with its yard, two sheds, a stall, a garden, benches
				reqs.add(new Req(Family.HOUSE, 11, sp(12), 10, sp(12), HOUSE_H(), Mode.NEAR,
						Map.of("house_forge_atelier", 14, "house_stone_weathered", 3)));
				for (int i = 0; i < 2; i++) {
					reqs.add(req(Family.ANNEX, 5, 8, 5, 7, scy(10), Mode.NEAR));
				}
				reqs.add(req(Family.STALL, 3, 6, 3, 5, scy(6), Mode.NEAR));
				reqs.add(req(Family.GARDEN, 6, 9, 6, 9, scy(5), Mode.SCATTER));
				for (int i = 0; i < 2; i++) {
					reqs.add(new Req(Family.BENCH, 2, 3, 1, 1, BENCH_H, Mode.NEAR));
				}
			}
			case 2 -> {
				// patio: a dwelling with its yard, a gazebo, a fountain, trees and benches
				reqs.add(new Req(Family.HOUSE, 9, sp(12), 9, sp(12), HOUSE_H(), Mode.NEAR,
						Map.of("house_timber_cottage", 8, "house_machiya", 8, "house_japanese_engawa", 6, "house_japanese_teahouse", 6)));
				reqs.add(req(Family.GAZEBO, 5, 9, 5, 9, scy(9), Mode.NEAR));
				reqs.add(req(Family.FOUNTAIN, 5, 7, 5, 7, scy(7), Mode.SCATTER));
				for (int i = 0; i < 2; i++) {
					reqs.add(req(Family.TREE, 5, 7, 5, 7, CLEAR_TOP(), Mode.SCATTER));
					reqs.add(new Req(Family.BENCH, 2, 3, 1, 1, BENCH_H, Mode.NEAR));
				}
			}
			case 3 -> {
				// anexos: sheds, a greenhouse, a pergola, two gardens, a well
				for (int i = 0; i < 3; i++) {
					reqs.add(req(Family.ANNEX, 5, 8, 5, 8, scy(10), Mode.NEAR));
				}
				for (int i = 0; i < 2; i++) {
					reqs.add(req(Family.GARDEN, 6, 11, 6, 9, scy(5), Mode.NEAR));
				}
				reqs.add(req(Family.WELL, 3, 5, 3, 5, scy(8), Mode.NEAR));
				reqs.add(req(Family.STALL, 3, 6, 3, 5, scy(6), Mode.NEAR));
				reqs.add(req(Family.TREE, 5, 7, 5, 7, CLEAR_TOP(), Mode.SCATTER));
			}
			default -> {
				// mercado: a row of stalls on the lane, a shed behind, benches, shade trees
				for (int i = 0; i < 4; i++) {
					reqs.add(req(Family.STALL, 3, 6, 3, 5, scy(6), Mode.NEAR));
				}
				reqs.add(req(Family.ANNEX, 5, 8, 5, 7, scy(10), Mode.NEAR));
				for (int i = 0; i < 3; i++) {
					reqs.add(new Req(Family.BENCH, 2, 3, 1, 1, BENCH_H, Mode.NEAR));
				}
				for (int i = 0; i < 2; i++) {
					reqs.add(req(Family.TREE, 5, 7, 5, 7, CLEAR_TOP(), Mode.SCATTER));
				}
				for (int i = 0; i < 4; i++) {
					reqs.add(new Req(Family.PLANTER, 1, 2, 1, 1, PLANTER_H, Mode.NEAR));
				}
			}
		}
		// what every parcel gets after its own use, as far as the room goes: a dwelling for whoever works it (the
		// programs with a house of their own get a second, smaller one), a kitchen garden, shade, flower pots
		reqs.add(new Req(Family.HOUSE, 9, sp(11), 9, sp(11), HOUSE_H(), Mode.NEAR));
		for (int i = 0; i < 2; i++) {
			reqs.add(req(Family.GARDEN, 6, 9, 6, 9, scy(5), Mode.SCATTER));
			reqs.add(req(Family.TREE, 5, 7, 5, 7, CLEAR_TOP(), Mode.SCATTER));
		}
		for (int i = 0; i < 3; i++) {
			reqs.add(new Req(Family.PLANTER, 1, 2, 1, 1, PLANTER_H, Mode.NEAR));
		}
		for (Req r : reqs) {
			placeReq(city, pk, site, r, seed, salt++);
		}
	}

	/**
	 * The paved square of an empty-lot plaza, centred on the lot: a centrepiece in the middle with a
	 * {@code PLAZA_RING()}-cell walk round it, lamps on the corners. False (nothing written) when no
	 * square is free or nothing fits the middle.
	 */
	private static boolean plaza(City city, Packer pk, Lot l, long seed) {
		CitadelCanvas c = city.c;
		int cx = l.centre().getX();
		int cz = l.centre().getZ();
		Direction front = l.out().getOpposite();
		for (int side = EMPTY_PLAZA() + (l.ring() > 1 ? 2 : 0); side >= PLAZA_SIDES()[PLAZA_SIDES().length - 1] + 2 * PLAZA_RING(); side -= 2) {
			int x0 = cx - side / 2;
			int z0 = cz - side / 2;
			if (!pk.fits(x0, z0, side, side)) {
				continue;
			}
			int inner = side - 2 * PLAZA_RING();
			int ix = x0 + PLAZA_RING();
			int iz = z0 + PLAZA_RING();
			Family f = fitsAny(Family.PLAZA_CENTER, inner, inner, PLAZA_H()) ? Family.PLAZA_CENTER
					: fitsAny(Family.FOUNTAIN, inner, inner, PLAZA_H()) ? Family.FOUNTAIN : Family.TREE;
			if (!placeable(f, inner, inner, PLAZA_H())) {
				continue;
			}
			for (int x = x0; x < x0 + side; x++) {
				for (int z = z0; z < z0 + side; z++) {
					int ring = Math.max(Math.abs(x - cx), Math.abs(z - cz));
					c.set(x, 0, z, ring % 3 == 0 || ring == side / 2 ? FOOT : c.accent());
					for (int y = 1; y <= 3; y++) {
						if (is(c.at(x, y, z), Blocks.FLOWERING_AZALEA_LEAVES)) {
							c.set(x, y, z, AIR);
						}
					}
					city.paved.add(col(x, z));
				}
			}
			build(c, f, ix, 1, iz, inner, inner, PLAZA_H(), front, mix(seed, 7));
			int[][] corners = {{x0, z0}, {x0 + side - 1, z0}, {x0, z0 + side - 1}, {x0 + side - 1, z0 + side - 1}};
			for (int[] k : corners) {
				Direction in = cardinalOf(cx - k[0], cz - k[1]);
				if (fitsAny(Family.LAMP, 1, 1, LAMP_H())) {
					build(c, Family.LAMP, k[0], 1, k[1], 1, 1, LAMP_H(), in, mix(seed, k[0] * 31L + k[1]));
				}
			}
			pk.take(x0, z0, side, side);
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ ground texture

	/**
	 * Ground texture on the free grass of a lot, then a top-up so that plain grass (grass with nothing
	 * on it) covers at most {@value #PLAIN_MAX_PERCENT}% of the lot. Patches of {@code PATCH()} cells
	 * pick a mood (meadow, flowers, moss, bare earth, quiet grass); only plants without collision, so
	 * nothing here blocks a path.
	 */
	private static void texture(City city, Lot l, Packer pk, long seed) {
		CitadelCanvas c = city.c;
		long ts = mix(seed, 0x7E47L);
		for (int ix = 0; ix < pk.nx; ix++) {
			for (int iz = 0; iz < pk.nz; iz++) {
				int k = ix * pk.nz + iz;
				if (!pk.avail[k] || pk.used[k]) {
					continue;
				}
				int x = pk.x0 + ix;
				int z = pk.z0 + iz;
				if (!is(c.at(x, 0, z), Blocks.GRASS_BLOCK) || !clear(c.at(x, 1, z))) {
					continue;
				}
				int mood = hash(ts, Math.floorDiv(x, PATCH()), Math.floorDiv(z, PATCH()), 10);
				int roll = hash(ts, x, z, 100);
				switch (mood) {
					case 0, 1, 2 -> {
						if (roll < 45) {
							c.set(x, 1, z, SHORT_GRASS);
						} else if (roll < 55) {
							c.set(x, 1, z, FERN);
						} else if (roll < 62) {
							c.set(x, 1, z, FLOWERS[roll % FLOWERS.length]);
						}
					}
					case 3, 4 -> {
						if (roll < 35) {
							c.set(x, 1, z, PETALS[roll % PETALS.length]);
						} else if (roll < 65) {
							c.set(x, 1, z, FLOWERS[roll % FLOWERS.length]);
						} else if (roll < 80) {
							c.set(x, 1, z, SHORT_GRASS);
						}
					}
					case 5 -> {
						if (roll < 60) {
							c.set(x, 0, z, CitadelCanvas.MOSS);
						}
						if (roll < 30) {
							c.set(x, 1, z, MOSS_CARPET);
						} else if (roll >= 60 && roll < 80) {
							c.set(x, 1, z, FERN);
						}
					}
					case 6 -> {
						if (roll < 45) {
							c.set(x, 0, z, EARTH[roll % EARTH.length]);
						} else if (roll < 60) {
							c.set(x, 1, z, BUSH);
						} else if (roll < 75) {
							c.set(x, 1, z, SHORT_GRASS);
						}
					}
					default -> {
						if (roll < 25) {
							c.set(x, 1, z, SHORT_GRASS);
						}
					}
				}
			}
		}
		// top-up: thin the plain grass of the whole lot down to the target
		List<Long> cols = c.lotCols.getOrDefault(l.id(), List.of());
		List<Long> plain = new ArrayList<>();
		for (long k : cols) {
			int x = colX(k);
			int z = colZ(k);
			if (is(c.at(x, 0, z), Blocks.GRASS_BLOCK) && clear(c.at(x, 1, z)) && !c.hallCols.containsKey(k) && !city.anchorCols.contains(k)) {
				plain.add(k);
			}
		}
		int target = cols.size() * PLAIN_MAX_PERCENT / 100;
		if (plain.size() <= target) {
			return;
		}
		int keepPermille = (int) (1000L * target / plain.size());
		for (long k : plain) {
			int x = colX(k);
			int z = colZ(k);
			int roll = hash(ts ^ 0x5EEDL, x, z, 1000);
			if (roll >= keepPermille) {
				c.set(x, 1, z, roll % 7 == 0 ? FLOWERS[roll % FLOWERS.length] : (roll % 5 == 0 ? FERN : SHORT_GRASS));
			}
		}
	}

	// ------------------------------------------------------------------ streets

	/** Gates, plaza centres, lamps and benches on the open rings (see the class comment). */
	private static void streets(City city) {
		for (int m = 1; m <= city.c.rings; m++) {
			gates(city, m);
		}
		for (int m = 1; m <= city.c.rings; m++) {
			plazas(city, m);
		}
		for (int m = 1; m <= city.c.rings; m++) {
			avenueFurniture(city, m);
		}
		ringFurniture(city, 0);
		for (int m = 1; m <= city.c.rings; m++) {
			ringFurniture(city, m);
		}
	}

	/** Whether a street column can take an object: right zone, paved, clear above, away from nodes and the spawn. */
	private static boolean streetFree(City city, int x, int z, int top) {
		CitadelCanvas c = city.c;
		long k = col(x, z);
		if (city.taken.contains(k) || city.anchorCols.contains(k)) {
			return false;
		}
		BlockPos sp = CitadelFinish.spawn();
		if (Math.abs(x - sp.getX()) + Math.abs(z - sp.getZ()) <= SPAWN_KEEP()) {
			return false;
		}
		if (clear(c.at(x, 0, z))) {
			return false;
		}
		for (int y = 1; y <= top; y++) {
			if (!clear(c.at(x, y, z))) {
				return false;
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (c.zoneAt(x + dx, z + dz).kind() == Kind.NODE) {
					return false;
				}
			}
		}
		return true;
	}

	private static void takeBox(City city, int x0, int z0, int sx, int sz) {
		for (int x = x0; x < x0 + sx; x++) {
			for (int z = z0; z < z0 + sz; z++) {
				city.taken.add(col(x, z));
			}
		}
	}

	/** A gate where each cardinal avenue of ring {@code m} enters its band of lots; the path goes through its middle. */
	private static void gates(City city, int m) {
		CitadelCanvas c = city.c;
		int n = RingCampusGeometry.lotsIn(m);
		for (int k = 0; k < n; k++) {
			if ((4 * k) % n != 0) {
				continue; // only the cardinal avenues: an axis-aligned box spans them cleanly
			}
			double ang = k * 2 * Math.PI / n;
			Direction dir = RingCampusGeometry.cardinal(ang);
			Direction perp = dir.getClockWise();
			int inner = RingCampusGeometry.ringInner(m);
			gate:
			for (int t0 : new int[] {inner + GATE_SETBACK, inner + GATE_SETBACK + GATE_RETRY()}) {
				for (int w : new int[] {sOdd(7), sOdd(9), sOdd(5)}) {
					for (int d : new int[] {sc(3), sc(2), sc(4)}) {
						if (!fitsAny(Family.GATE, w, d, GATE_H())) {
							continue;
						}
						int x0 = Integer.MAX_VALUE;
						int z0 = Integer.MAX_VALUE;
						int x1 = Integer.MIN_VALUE;
						int z1 = Integer.MIN_VALUE;
						boolean ok = true;
						for (int i = 0; i < d && ok; i++) {
							for (int s = -(w / 2); s <= w / 2 && ok; s++) {
								BlockPos p = BlockPos.ZERO.relative(dir, t0 + i).relative(perp, s);
								int x = p.getX();
								int z = p.getZ();
								Zone zn = c.zoneAt(x, z);
								boolean road = zn.kind() == Kind.AVENUE && zn.ring() == m && streetFree(city, x, z, GATE_H());
								boolean edge = zn.kind() == Kind.LOT && zn.ring() == m && softGround(c.at(x, 0, z))
										&& !city.paved.contains(col(x, z)) && !city.taken.contains(col(x, z)) && clearAbove(c, x, z, GATE_H());
								ok = road || edge;
								x0 = Math.min(x0, x);
								z0 = Math.min(z0, z);
								x1 = Math.max(x1, x);
								z1 = Math.max(z1, z);
							}
						}
						if (!ok) {
							continue;
						}
						int sx = x1 - x0 + 1;
						int sz = z1 - z0 + 1;
						if (build(c, Family.GATE, x0, 1, z0, sx, sz, GATE_H(), dir.getOpposite(), mix(0x6A7EL, m * 64L + k))) {
							takeBox(city, x0, z0, sx, sz);
							break gate;
						}
					}
				}
			}
		}
	}

	private static boolean clearAbove(CitadelCanvas c, int x, int z, int top) {
		for (int y = 1; y <= top; y++) {
			if (!clearOrBush(c.at(x, y, z))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A plaza centre at each crossing of ring {@code m}'s street with its avenues, when a square (plus a
	 * {@code PLAZA_RING()}-cell walk ring) fits on cells of ring {@code m} only: its street and avenue,
	 * or free lot grass, which gets paved. Slides inwards along the avenue up to {@code PLAZA_SHIFT()}
	 * cells to find room. Ring 1's crossings hold the node rooms, so none there.
	 */
	private static void plazas(City city, int m) {
		CitadelCanvas c = city.c;
		int n = RingCampusGeometry.lotsIn(m);
		double edge = RingCampusGeometry.streetOuter(m);
		for (int k = 0; k < n; k++) {
			double ang = k * 2 * Math.PI / n;
			Direction front = RingCampusGeometry.cardinal(ang).getOpposite();
			plaza:
			for (int side : PLAZA_SIDES()) {
				Family f = fitsAny(Family.PLAZA_CENTER, side, side, PLAZA_H()) ? Family.PLAZA_CENTER
						: fitsAny(Family.FOUNTAIN, side, side, PLAZA_H()) ? Family.FOUNTAIN : null;
				if (f == null) {
					continue;
				}
				int all = side + 2 * PLAZA_RING();
				for (int shift = 0; shift <= PLAZA_SHIFT(); shift++) {
					double rr = edge - all / 2.0 - shift;
					BlockPos ctr = RingCampusGeometry.polar(rr, ang, 0);
					int x0 = ctr.getX() - all / 2;
					int z0 = ctr.getZ() - all / 2;
					if (!plazaFree(city, m, x0, z0, all)) {
						continue;
					}
					for (int x = x0; x < x0 + all; x++) {
						for (int z = z0; z < z0 + all; z++) {
							if (c.zoneAt(x, z).kind() == Kind.LOT) {
								boolean rim = x == x0 || z == z0 || x == x0 + all - 1 || z == z0 + all - 1;
								c.set(x, 0, z, rim ? FOOT : c.accent());
								for (int y = 1; y <= 3; y++) {
									if (is(c.at(x, y, z), Blocks.FLOWERING_AZALEA_LEAVES)) {
										c.set(x, y, z, AIR);
									}
								}
								city.paved.add(col(x, z));
							}
						}
					}
					build(c, f, x0 + PLAZA_RING(), 1, z0 + PLAZA_RING(), side, side, PLAZA_H(), front, mix(0x91A2AL, m * 64L + k));
					takeBox(city, x0, z0, all, all);
					break plaza;
				}
			}
		}
	}

	/** Every cell of the {@code all × all} square at {@code (x0, z0)} is ring {@code m}'s street, avenue or free lot grass. */
	private static boolean plazaFree(City city, int m, int x0, int z0, int all) {
		CitadelCanvas c = city.c;
		for (int x = x0; x < x0 + all; x++) {
			for (int z = z0; z < z0 + all; z++) {
				Zone zn = c.zoneAt(x, z);
				if (zn.ring() != m) {
					return false;
				}
				long k = col(x, z);
				switch (zn.kind()) {
					case STREET, AVENUE -> {
						if (!streetFree(city, x, z, PLAZA_H())) {
							return false;
						}
					}
					case LOT -> {
						if (c.hallCols.containsKey(k) || city.paved.contains(k) || city.taken.contains(k) || city.anchorCols.contains(k)
								|| !softGround(c.at(x, 0, z)) || !clearAbove(c, x, z, PLAZA_H())) {
							return false;
						}
					}
					default -> {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Lamps on both edges of ring {@code m}'s avenues every {@code LAMP_STEP()} blocks, a bench every {@value #BENCH_EVERY} intervals. */
	private static void avenueFurniture(City city, int m) {
		CitadelCanvas c = city.c;
		int n = RingCampusGeometry.lotsIn(m);
		int from = RingCampusGeometry.ringInner(m) + LAMP_STEP() / 2;
		int to = RingCampusGeometry.ringOuter(m) - 2;
		boolean lamps = fitsAny(Family.LAMP, 1, 1, LAMP_H());
		for (int k = 0; k < n; k++) {
			double ang = k * 2 * Math.PI / n;
			double dx = Math.sin(ang);
			double dz = -Math.cos(ang);
			double px = Math.cos(ang);
			double pz = Math.sin(ang);
			int step = 0;
			for (int t = from; t <= to; t += LAMP_STEP(), step++) {
				for (int side = -1; side <= 1; side += 2) {
					if (lamps) {
						int x = (int) Math.round(t * dx + side * AVENUE_SIDE() * px);
						int z = (int) Math.round(t * dz + side * AVENUE_SIDE() * pz);
						if (avenueEdge(city, m, n, x, z, LAMP_H())) {
							Direction face = cardinalOf(-side * px, -side * pz);
							if (build(c, Family.LAMP, x, 1, z, 1, 1, LAMP_H(), face, mix(0x1A4DL, x * 31L + z))) {
								city.taken.add(col(x, z));
							}
						}
					}
					if ((step + (side > 0 ? 0 : 1)) % BENCH_EVERY == 0 && t + LAMP_STEP() / 2 <= to) {
						double tm = t + LAMP_STEP() / 2.0;
						int x = (int) Math.round(tm * dx + side * AVENUE_SIDE() * px);
						int z = (int) Math.round(tm * dz + side * AVENUE_SIDE() * pz);
						Direction face = cardinalOf(-side * px, -side * pz);
						bench(city, x, z, face, (bx, bz) -> avenueEdge(city, m, n, bx, bz, BENCH_H));
					}
				}
			}
		}
	}

	/** An avenue column of ring {@code m} on the edge band (the middle stays clear), free. */
	private static boolean avenueEdge(City city, int m, int n, int x, int z, int top) {
		Zone zn = city.c.zoneAt(x, z);
		return zn.kind() == Kind.AVENUE && zn.ring() == m && RingCampusGeometry.radialDistance(x, z, n) >= AVENUE_EDGE()
				&& streetFree(city, x, z, top);
	}

	/**
	 * A bench of 3 (else 2) cells across {@code face} centred on {@code (x, z)}, when every cell passes
	 * {@code ok} and the catalog has a bench for it.
	 */
	private static void bench(City city, int x, int z, Direction face, java.util.function.BiPredicate<Integer, Integer> ok) {
		boolean alongX = face.getAxis() == Direction.Axis.Z;
		for (int len : new int[] {3, 2}) {
			if (!fitsAny(Family.BENCH, len, 1, BENCH_H)) {
				continue;
			}
			int x0 = alongX ? x - (len - 1) / 2 : x;
			int z0 = alongX ? z : z - (len - 1) / 2;
			int sx = alongX ? len : 1;
			int sz = alongX ? 1 : len;
			boolean free = true;
			for (int bx = x0; bx < x0 + sx && free; bx++) {
				for (int bz = z0; bz < z0 + sz && free; bz++) {
					free = ok.test(bx, bz);
				}
			}
			if (free && build(city.c, Family.BENCH, x0, 1, z0, sx, sz, BENCH_H, face, mix(0xBE4CL, x * 31L + z))) {
				takeBox(city, x0, z0, sx, sz);
				return;
			}
		}
	}

	/**
	 * Lamps round a ring street (m = 0: the inner street, its outer edge only; else both edges of ring
	 * {@code m}'s street) every {@code LAMP_STEP()} blocks of arc, away from the crossings, and benches
	 * on the outer edge every {@value #BENCH_EVERY} intervals, facing in.
	 */
	private static void ringFurniture(City city, int m) {
		CitadelCanvas c = city.c;
		double inner = m == 0 ? c.g.rampR : RingCampusGeometry.ringOuter(m);
		double outer = m == 0 ? RingCampusGeometry.ringInner(1) : RingCampusGeometry.streetOuter(m);
		double centre = (inner + outer) / 2;
		double band = (outer - inner) / 2 - 1;
		Kind kind = m == 0 ? Kind.INNER_STREET : Kind.STREET;
		int nA = m == 0 ? 8 : RingCampusGeometry.lotsIn(m);
		int nB = m < RingCampusGeometry.MAX_RINGS ? RingCampusGeometry.lotsIn(m + 1) : nA;
		boolean lamps = fitsAny(Family.LAMP, 1, 1, LAMP_H());
		double[] edges = m == 0 ? new double[] {outer - 0.5} : new double[] {inner + 0.5, outer - 0.5};
		for (double radius : edges) {
			int count = Math.max(8, (int) Math.round(2 * Math.PI * radius / LAMP_STEP()));
			boolean outerEdge = radius > centre;
			for (int i = 0; i < count; i++) {
				double ang = (i + 0.5) * 2 * Math.PI / count;
				if (lamps) {
					BlockPos p = RingCampusGeometry.polar(radius, ang, 0);
					if (ringEdge(city, kind, m, centre, band, nA, nB, p.getX(), p.getZ(), LAMP_H())) {
						Direction face = outerEdge ? RingCampusGeometry.cardinal(ang).getOpposite() : RingCampusGeometry.cardinal(ang);
						if (build(c, Family.LAMP, p.getX(), 1, p.getZ(), 1, 1, LAMP_H(), face, mix(0x1A4DL, p.getX() * 31L + p.getZ()))) {
							city.taken.add(col(p.getX(), p.getZ()));
						}
					}
				}
				if (outerEdge && i % BENCH_EVERY == 0) {
					double am = (i + 1.0) * 2 * Math.PI / count;
					BlockPos p = RingCampusGeometry.polar(radius, am, 0);
					Direction face = RingCampusGeometry.cardinal(am).getOpposite();
					bench(city, p.getX(), p.getZ(), face, (bx, bz) -> ringEdge(city, kind, m, centre, band, nA, nB, bx, bz, BENCH_H));
				}
			}
		}
	}

	/** A ring-street column on its edge band, away from the avenue crossings (of both neighbouring rings) and ramps, free. */
	private static boolean ringEdge(City city, Kind kind, int m, double centre, double band, int nA, int nB, int x, int z, int top) {
		Zone zn = city.c.zoneAt(x, z);
		if (zn.kind() != kind || (kind == Kind.STREET && zn.ring() != m)) {
			return false;
		}
		if (Math.abs(RingCampusGeometry.r(x, z) - centre) < band) {
			return false;
		}
		if (RingCampusGeometry.radialDistance(x, z, nA) <= CROSSING_KEEP() || RingCampusGeometry.radialDistance(x, z, nB) <= CROSSING_KEEP()) {
			return false;
		}
		return streetFree(city, x, z, top);
	}

	// ------------------------------------------------------------------ hub

	/**
	 * Statues (else braziers: a lamp) on the outer rim of the promenade, one between each pair of
	 * ramps, slightly off the light posts; only where the deck is there and the rim is clear, so the
	 * inner part of the promenade stays a free walk.
	 */
	private static void hub(City city) {
		CitadelCanvas c = city.c;
		for (int k = 0; k < 8; k++) {
			double ang = (k + 0.5) * Math.PI / 4 + HUB_STATUE_OFFSET;
			BlockPos ctr = RingCampusGeometry.polar(c.g.galleryR - HUB_STATUE_IN(), ang, 0);
			Direction face = RingCampusGeometry.cardinal(ang).getOpposite();
			for (int size = sc(2); size >= 1; size--) {
				Family f = size > 1 ? Family.STATUE : Family.LAMP;
				int h = size == 2 ? STATUE_H() : LAMP_H();
				if (!fitsAny(f, size, size, h)) {
					continue;
				}
				int x0 = ctr.getX() - (size - 1) / 2;
				int z0 = ctr.getZ() - (size - 1) / 2;
				if (!galleryFree(city, x0, z0, size, h)) {
					continue;
				}
				if (build(c, f, x0, y0() + 1, z0, size, size, h, face, mix(0x57A7L, k))) {
					break;
				}
			}
		}
	}

	/**
	 * The square is promenade deck with nothing on it, on the outer half of the promenade, off the
	 * anchors; the ring of promenade cells round it is clear too (cells of the ring past the promenade's
	 * edge, the terraces, are not checked).
	 */
	private static boolean galleryFree(City city, int x0, int z0, int size, int h) {
		CitadelCanvas c = city.c;
		for (int x = x0 - 1; x <= x0 + size; x++) {
			for (int z = z0 - 1; z <= z0 + size; z++) {
				boolean inside = x >= x0 && x < x0 + size && z >= z0 && z < z0 + size;
				boolean deck = c.zoneAt(x, z).kind() == Kind.GALLERY;
				if (!inside && !deck) {
					continue;
				}
				if (!deck || clear(c.at(x, y0(), z)) || city.anchorCols.contains(col(x, z))) {
					return false;
				}
				int top = inside ? h : 2;
				for (int y = y0() + 1; y <= y0() + top; y++) {
					if (!clear(c.at(x, y, z))) {
						return false;
					}
				}
				if (inside && RingCampusGeometry.r(x, z) < c.g.galleryR - c.sd(3)) {
					return false;
				}
			}
		}
		return true;
	}
}
