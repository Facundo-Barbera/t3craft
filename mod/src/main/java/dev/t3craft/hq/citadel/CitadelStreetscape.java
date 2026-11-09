package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * What stands on the roads of the citadel: the street furniture over the paving of
 * {@link CitadelPaving}. Pure and deterministic. The boulevards and the street rows are called column by
 * column from {@link CitadelGrounds#ground} right after the paving of that column is set (so they can read
 * the sidewalk they stand on); the crossings are one pass over the whole canvas ({@link #crossings}).
 *
 * <h2>Concept (struct/streets-plazas)</h2>
 *
 * Function over decoration, and light on mass. Every piece answers a question a walker has: where do I
 * sit in the shade, where do I put my rubbish, where am I, who sells here. Reference: the shaded avenues
 * of a Kyoto neighbourhood, a plaza with one tall paper lantern and a signpost. Palette: cherry wood,
 * pale smooth stone, dark deepslate, the warm paper light of the kit. What makes it memorable: every
 * crossing is a small plaza with the same four things (a lantern you can see from the avenue, benches
 * that face it, a sign, a stall), so the city reads at a glance.
 *
 * <h2>Pieces</h2>
 *
 * <ul>
 * <li><b>Boulevard</b> ({@link #avenue}): on the outer column of both sidewalks of every avenue, one cherry
 *     tree every {@link #STEP()} cells (a cell short of a lamp of {@code CitadelPlacement}: lamps and trees
 *     alternate), with a bench under its canopy (shade, and a view of the avenue) and, at every other tree,
 *     a composter on the other side of the trunk (the rubbish bin). Trunks stand on the sidewalk: the walking
 *     lane keeps its width. The avenue's lamps are the catalog's ({@code CitadelPlacement}) and the posts
 *     of {@link CitadelAmbience}; its gate is the catalog's too.</li>
 * <li><b>Street rows</b> ({@link #street}): on the outer column of the sidewalks of the inner street and
 *     the ring streets: a tree with a bench under it and a bin on the outer row every two steps, a bench
 *     facing the street on the inner row half way between them. None near an avenue mouth or a node.</li>
 * <li><b>Crossings</b> ({@link #crossings}): on every plazoleta ({@link CitadelPaving#plazoleta}) of rings 2
 *     and 3 and of the inner street (not the spawn's plaza): a focal point (a chiselled plinth, two posts
 *     and the kit's tall paper lantern, {@code paper_lantern_tall}, light 15), a bench facing the
 *     centre at each of the four corners, a bin beside each, a signpost (a log stele with a plaque: ring and
 *     compass heading of the avenue) and a small stall (counter, two posts, an awning of cherry slabs and
 *     a hanging lantern). It runs after the catalog's placement and only on free cells: where the
 *     catalog built a plaza centre of its own, the crossing is left to it.</li>
 * <li><b>Plaza</b> ({@link #plaza}): the plaza at the foot of the main ramp keeps its lanterns, benches and
 *     planters; the axis stays clear (the spawn is there).</li>
 * </ul>
 *
 * Nothing stands on a cell of a node or a lot: canopies skip those cells. Light: the street's lamps are
 * the catalog's lamps and {@link CitadelAmbience}'s posts; what stands here lights only where it is a
 * focal point (the tall lantern, the stall's lantern). Never on the lane of the walkers: everything stands
 * on a sidewalk, or on the plazoleta's own plinth.
 */
final class CitadelStreetscape {
	static final BlockState PLINTH = CitadelPaving.CHISELED;
	static final BlockState POST = b(Blocks.STONE_BRICK_WALL);
	static final BlockState LANTERN = b(Blocks.LANTERN);
	static final BlockState HANGING_LANTERN = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
	static final BlockState PIT = b(Blocks.ROOTED_DIRT);
	static final BlockState STELE = b(Blocks.STRIPPED_CHERRY_LOG);
	static final BlockState FENCE = b(Blocks.CHERRY_FENCE);
	static final BlockState AWNING = slab(Blocks.CHERRY_SLAB);
	static final BlockState BIN = b(Blocks.COMPOSTER);
	static final BlockState BASE = slab(Blocks.STONE_BRICK_SLAB);
	static final BlockState BARREL = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);

	/** The 16 points of the compass, clockwise from north (the label of an avenue's sign). */
	private static final String[] COMPASS = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};

	/** Cells between two trees of a boulevard or two lamps: the catalog's lamp step ({@code CitadelPlacement}), so lamps and trees take turns. */
	static int STEP() {
		return CitadelPlacement.LAMP_STEP();
	}
	/**
	 * Where an avenue's gate stood (the catalog's gate now): this many cells inside the ring, an avenue half width.
	 * {@code CitadelRelief} starts the avenue's ramp just past it, so the value is kept.
	 */
	static int GATE_IN() {
		return (int) Math.round(CitadelPaving.AVENUE_HALF());
	}
	/** The boulevard stops this many cells short of the street (the plazoleta's room). */
	static int END_ROOM() {
		return CitadelCanvas.dims().s(6);
	}
	/** Trees and benches keep this many cells clear of an avenue mouth, beyond its half width. */
	static double MOUTH_ROOM() {
		return CitadelCanvas.dims().sd(1.5);
	}
	/** A tree of the boulevards: trunk height. */
	static int TRUNK() {
		return CitadelCanvas.dims().sy(4);
	}
	/** Height of a lantern post of a plaza (wall blocks under the lantern). */
	static final int PLAZA_POST = 3;
	/** Height of the walls (stone-brick posts) under the tall lantern of a focal point. */
	static final int FOCAL_POSTS = 6;
	/** Lateral distance (cells from the avenue's axis) of the benches of a plazoleta: just past the mouth of the avenue. */
	static int BENCH_LAT() {
		return (int) Math.round(CitadelPaving.MOUTH_D() + 1.25);
	}

	private CitadelStreetscape() {
	}

	// ------------------------------------------------------------------ kit blocks (looked up by id: the module may be off)

	/** The kit's block {@code path} ({@code t3craft:path}), or {@code null} when the blocks module is off. */
	private static BlockState kit(String path) {
		Identifier id = Identifier.fromNamespaceAndPath("t3craft", path);
		return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id).defaultBlockState() : null;
	}

	private static <T extends Comparable<T>> BlockState withValue(BlockState s, Property<T> p, String value) {
		return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
	}

	/** The kit's plaza lantern ({@code t3craft:paper_lantern_tall}, two blocks, light 15), lower or upper half. Vanilla: a cherry fence and a lantern. */
	static BlockState tallLantern(boolean upper) {
		BlockState s = kit("paper_lantern_tall");
		if (s != null) {
			Property<?> p = s.getBlock().getStateDefinition().getProperty("half");
			return p == null ? s : withValue(s, p, upper ? "upper" : "lower");
		}
		return upper ? LANTERN : FENCE;
	}

	/** The kit's standing paper lantern ({@code t3craft:paper_lantern}, light 12). Vanilla: a lantern. */
	static BlockState andon() {
		BlockState s = kit("paper_lantern");
		return s != null ? s : LANTERN;
	}

	// ------------------------------------------------------------------ pieces

	/** A lantern post: {@code walls} wall blocks and a paper lantern. */
	static void lanternPost(CitadelCanvas c, int x, int z, int walls) {
		c.fill(x, 1, walls, z, POST);
		c.set(x, walls + 1, z, andon());
	}

	/** Whether a canopy leaf may stand over this cell: road-ish cells only (never a lot or a node). */
	private static boolean canopyOk(CitadelCanvas c, int x, int z) {
		Kind k = c.zoneAt(x, z).kind();
		return k == Kind.AVENUE || k == Kind.STREET || k == Kind.INNER_STREET || k == Kind.TERRACE || k == Kind.LAWN;
	}

	/** A cherry tree in a pit on the ground cell: a trunk and a canopy that stays over roads and keeps every leaf attached. */
	static void tree(CitadelCanvas c, int x, int z) {
		c.rigid(x, z);
		treeCells(c, x, z);
		c.unrigid();
	}

	private static void treeCells(CitadelCanvas c, int x, int z) {
		c.set(x, 0, z, PIT);
		c.fill(x, 1, TRUNK(), z, LOG);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int m = Math.abs(dx) + Math.abs(dz);
				if (m == 0 || m > 2 || !canopyOk(c, x + dx, z + dz)) {
					continue;
				}
				if (m == 2) {
					// attached through a neighbour that is also a leaf
					boolean fed = Math.abs(dx) == 2 ? canopyOk(c, x + dx / 2, z)
							: Math.abs(dz) == 2 ? canopyOk(c, x, z + dz / 2)
							: canopyOk(c, x + dx, z) || canopyOk(c, x, z + dz);
					if (!fed) {
						continue;
					}
				}
				c.set(x + dx, TRUNK(), z + dz, leaves(Blocks.CHERRY_LEAVES));
				if (m == 1) {
					c.set(x + dx, TRUNK() + 1, z + dz, leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
		c.set(x, TRUNK() + 1, z, leaves(Blocks.CHERRY_LEAVES));
		c.set(x, TRUNK() + 2, z, leaves(Blocks.CHERRY_LEAVES));
	}

	/** A bench of one stair whose back is towards {@code back}. */
	static void bench(CitadelCanvas c, int x, int z, Direction back) {
		c.set(x, 1, z, stairs(Blocks.CHERRY_STAIRS, back));
	}

	/** A bin: a composter. */
	static void bin(CitadelCanvas c, int x, int z) {
		c.set(x, 1, z, BIN);
	}

	/** The horizontal direction nearest to a vector. */
	static Direction nearest(double vx, double vz) {
		if (Math.abs(vx) > Math.abs(vz)) {
			return vx > 0 ? Direction.EAST : Direction.WEST;
		}
		return vz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** Whether a cell of a node is within {@code room} cells (the furniture keeps off the nodes' rooms). */
	private static boolean nearNode(CitadelCanvas c, int x, int z, int room) {
		for (int dx = -room; dx <= room; dx++) {
			for (int dz = -room; dz <= room; dz++) {
				if (c.zoneAt(x + dx, z + dz).kind() == Kind.NODE) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A focal point ({@code 3 + FOCAL_POSTS} high): a chiselled plinth on a 3x3 base of stone-brick slabs (half a
	 * block: a walker steps over it), wall posts, four cherry slabs at the top of the shaft each with a lantern
	 * hanging from it, and the kit's tall paper lantern on top.
	 */
	static void focalLantern(CitadelCanvas c, int x, int z) {
		c.set(x, 1, z, PLINTH);
		c.fill(x, 2, 1 + FOCAL_POSTS, z, POST);
		c.set(x, 2 + FOCAL_POSTS, z, tallLantern(false));
		c.set(x, 3 + FOCAL_POSTS, z, tallLantern(true));
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && freeColumn(c, x + dx, z + dz, 1, 1) && c.at(x + dx, 0, z + dz) != null) {
					c.set(x + dx, 1, z + dz, BASE);
				}
			}
		}
		int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] a : arms) {
			int ax = x + a[0];
			int az = z + a[1];
			if (freeColumn(c, ax, az, FOCAL_POSTS - 1, FOCAL_POSTS + 1) && c.at(ax, 0, az) != null) {
				c.set(ax, FOCAL_POSTS + 1, az, AWNING);
				c.set(ax, FOCAL_POSTS, az, HANGING_LANTERN);
			}
		}
	}

	// ------------------------------------------------------------------ avenues

	/**
	 * The boulevard of one avenue column: on the outer column of each sidewalk, a tree every {@link #STEP()} cells
	 * (half a step after the lamps of the catalog), a bench just past it, and at every other tree a bin on the
	 * other side.
	 */
	static void avenue(CitadelCanvas c, int x, int z, Zone zn) {
		int m = zn.ring();
		int n = RingCampusGeometry.lotsIn(m);
		double d = RingCampusGeometry.radialDistance(x, z, n);
		if (d <= CitadelPaving.AVENUE_HALF() - 1.0) {
			return; // the outermost column of the sidewalk only: the walkers keep the rest
		}
		double ang = CitadelPaving.radialAngle(x, z, n);
		double s = x * Math.sin(ang) - z * Math.cos(ang);
		double l = x * Math.cos(ang) + z * Math.sin(ang);
		double ds = s - Math.rint(s);
		if (Math.abs(ds) > 0.355) {
			return; // one cell per row, also on the diagonals
		}
		long row = Math.round(s);
		long from = (long) RingCampusGeometry.ringInner(m) + STEP();
		if (row < from || row > RingCampusGeometry.ringOuter(m) - END_ROOM()) {
			return;
		}
		int side = l >= 0 ? 1 : -1;
		Direction out = nearest(side * Math.cos(ang), side * Math.sin(ang));
		int phase = (int) Math.floorMod(row - from, (long) STEP());
		long index = Math.floorDiv(row - from, (long) STEP());
		if (phase == 0) {
			tree(c, x, z);
		} else if (phase == (side > 0 ? 1 : STEP() - 1)) {
			bench(c, x, z, out);
		} else if (phase == (side > 0 ? STEP() - 1 : 1) && index % 2 == 0) {
			bin(c, x, z);
		}
	}

	// ------------------------------------------------------------------ streets

	/** The furniture of one street column: trees, benches and bins on the outer column of the sidewalks. */
	static void street(CitadelCanvas c, int x, int z, double r, Zone zn) {
		boolean inner = zn.kind() == Kind.INNER_STREET;
		int m = zn.ring();
		double lo = inner ? CitadelCanvas.dims().rampR : RingCampusGeometry.ringOuter(m);
		double hi = inner ? RingCampusGeometry.ringInner(1) : RingCampusGeometry.streetOuter(m);
		double rc = (lo + hi) / 2;
		int nIn = inner ? 8 : RingCampusGeometry.lotsIn(m);
		int nOut = inner ? 8 : m < c.rings ? RingCampusGeometry.lotsIn(m + 1) : 0;
		if (inner) {
			plaza(c, x, z, lo, hi);
		}
		BlockState ground = c.at(x, 0, z);
		if (ground == null || !CitadelPaving.isAcera(ground) || Math.min(r - lo, hi - r) > 1.5) {
			return; // the outer column of a sidewalk only
		}
		double room = CitadelPaving.AVENUE_HALF() + MOUTH_ROOM();
		if (RingCampusGeometry.radialDistance(x, z, nIn) <= room
				|| nOut > 0 && RingCampusGeometry.radialDistance(x, z, nOut) <= room) {
			return;
		}
		double arc = RingCampusGeometry.theta(x, z) * rc;
		if (Math.abs(arc - Math.rint(arc)) > 0.45) {
			return;
		}
		int every = STEP();
		boolean outer = r > rc;
		// the two rows alternate: a tree at 0 of the outer row, at half a step of the inner row
		int p = (int) Math.floorMod(Math.round(arc) - (outer ? 0 : every / 2), (long) every);
		Direction toLots = outer ? nearest(x, z) : nearest(x, z).getOpposite();
		if (nearNode(c, x, z, 4)) {
			return;
		}
		if (p == 0 && RingCampusGeometry.radialDistance(x, z, nIn) > room + 2
				&& (nOut == 0 || RingCampusGeometry.radialDistance(x, z, nOut) > room + 2)) {
			tree(c, x, z);
		} else if (p == 1) {
			bench(c, x, z, toLots);
		} else if (p == every - 1) {
			bin(c, x, z);
		}
	}

	// ------------------------------------------------------------------ crossings

	/** A crossing's frame: its centre cell, the avenue's outward unit vector (u) and the lateral one (v). */
	private record Frame(int cx, int cz, double ux, double uz, double vx, double vz) {
		int x(int lat, int along) {
			return (int) Math.round(cx + lat * vx + along * ux);
		}

		int z(int lat, int along) {
			return (int) Math.round(cz + lat * vz + along * uz);
		}
	}

	/** Whether the cells {@code y1..y2} over a column are free air (nothing built there yet). */
	private static boolean freeColumn(CitadelCanvas c, int x, int z, int y1, int y2) {
		for (int y = y1; y <= y2; y++) {
			BlockState s = c.at(x, y, z);
			if (s != null && !s.isAir()) {
				return false;
			}
		}
		return true;
	}

	/** A street or inner-street cell with ground and free air over it up to {@code top}. */
	private static boolean groundFree(CitadelCanvas c, int x, int z, int top) {
		Kind k = c.zoneAt(x, z).kind();
		return (k == Kind.STREET || k == Kind.INNER_STREET) && c.at(x, 0, z) != null && freeColumn(c, x, z, 1, top);
	}

	/** A sidewalk cell of a street with free air over it up to {@code top}. */
	private static boolean sidewalkFree(CitadelCanvas c, int x, int z, int top) {
		return groundFree(c, x, z, top) && CitadelPaving.isAcera(c.at(x, 0, z));
	}

	/**
	 * The furniture of every plazoleta, in one pass over the open rings (called by {@link CitadelFinish#build}
	 * after the catalog's placement and before the relief, so it sees what the catalog built and moves with the
	 * terraces). Rings 2 and 3 and the inner street, not the spawn's plaza; ring 1's crossings are node rooms.
	 */
	static void crossings(CitadelCanvas c) {
		Set<Long> done = new HashSet<>();
		for (int m = 0; m <= c.rings; m++) {
			if (m == 1) {
				continue;
			}
			boolean inner = m == 0;
			double lo = inner ? CitadelCanvas.dims().rampR : RingCampusGeometry.ringOuter(m);
			double hi = inner ? RingCampusGeometry.ringInner(1) : RingCampusGeometry.streetOuter(m);
			double rc = (lo + hi) / 2;
			int[] counts = inner ? new int[] {8} : m < c.rings ? new int[] {RingCampusGeometry.lotsIn(m), RingCampusGeometry.lotsIn(m + 1)}
					: new int[] {RingCampusGeometry.lotsIn(m)};
			for (int n : counts) {
				for (int k = 0; k < n; k++) {
					if (inner && k == n / 2) {
						continue; // the spawn's plaza has its own furniture
					}
					double ang = k * 2 * Math.PI / n;
					BlockPos centre = RingCampusGeometry.polar(rc, ang, 0);
					if (done.add(CitadelCanvas.col(centre.getX(), centre.getZ()))) {
						crossing(c, m, ang, centre.getX(), centre.getZ(), hi - lo);
					}
				}
			}
		}
	}

	private static void crossing(CitadelCanvas c, int m, double ang, int cx, int cz, double width) {
		Kind kind = c.zoneAt(cx, cz).kind();
		if ((kind != Kind.STREET && kind != Kind.INNER_STREET) || c.at(cx, 0, cz) != PLINTH || !freeColumn(c, cx, cz, 1, 3 + FOCAL_POSTS)) {
			return; // not a plazoleta, or the catalog built a plaza centre of its own
		}
		Frame f = new Frame(cx, cz, Math.sin(ang), -Math.cos(ang), Math.cos(ang), Math.sin(ang));
		int edge = (int) Math.round(width / 2 - 1);
		int lat = BENCH_LAT();
		c.rigid(cx, cz);
		focalLantern(c, cx, cz);
		for (int sa = -1; sa <= 1; sa += 2) {
			Direction back = nearest(sa * f.ux(), sa * f.uz());
			for (int sl = -1; sl <= 1; sl += 2) {
				// a bench of three cells, back to the lots, facing the focal point; a bin between it and the avenue's mouth
				for (int i = 0; i < 3; i++) {
					int bx = f.x(sl * (lat + i), sa * edge);
					int bz = f.z(sl * (lat + i), sa * edge);
					if (sidewalkFree(c, bx, bz, 1)) {
						bench(c, bx, bz, back);
					}
				}
				int nx = f.x(sl * (lat - 1), sa * edge);
				int nz = f.z(sl * (lat - 1), sa * edge);
				if (sidewalkFree(c, nx, nz, 1)) {
					bin(c, nx, nz);
				}
			}
		}
		sign(c, f, m, ang, lat + 4, edge);
		stall(c, f, -(lat + 8), -edge);
		c.unrigid();
	}

	/** A signpost on the sidewalk at ({@code lat}, {@code along}): a log stele three high with a plaque facing the street (ring and heading of the avenue), and a cap. */
	private static void sign(CitadelCanvas c, Frame f, int m, double ang, int lat, int along) {
		int sx = f.x(lat, along);
		int sz = f.z(lat, along);
		int px = f.x(lat, along - 1);
		int pz = f.z(lat, along - 1);
		if (!sidewalkFree(c, sx, sz, 4) || !groundFree(c, px, pz, 3)) {
			return;
		}
		c.fill(sx, 1, 3, sz, STELE);
		c.set(sx, 4, sz, AWNING);
		Direction face = nearest(-f.ux(), -f.uz());
		long k = BlockPos.asLong(px, 3, pz);
		c.put(px, 3, pz, c.kit.plaque(face), CitadelCanvas.DECOR);
		String ring = m == 0 ? "INNER STREET" : "RING " + m;
		String way = COMPASS[Math.floorMod((int) Math.round(ang / (2 * Math.PI) * 16), 16)] + " AVENUE";
		c.data.put(k, c.kit.plaqueData(DyeColor.ORANGE, "", ring, way, ""));
		c.fixtures.add(k);
	}

	/**
	 * A small stall on the sidewalk, centred on ({@code lat}, {@code along}) with {@code along} the outer column:
	 * a barrel counter between two cherry posts, an awning of cherry slabs over that column and the next, and a
	 * lantern hanging under it. The inner column of the sidewalk stays free: the walkers pass beside it.
	 */
	private static void stall(CitadelCanvas c, Frame f, int lat, int along) {
		int inward = along > 0 ? -1 : 1;
		for (int dl = -2; dl <= 2; dl++) {
			if (!sidewalkFree(c, f.x(lat + dl, along), f.z(lat + dl, along), 4)
					|| !groundFree(c, f.x(lat + dl, along + inward), f.z(lat + dl, along + inward), 4)) {
				return;
			}
		}
		c.fill(f.x(lat - 2, along), 1, 3, f.z(lat - 2, along), FENCE);
		c.fill(f.x(lat + 2, along), 1, 3, f.z(lat + 2, along), FENCE);
		for (int dl = -1; dl <= 1; dl++) {
			c.set(f.x(lat + dl, along), 1, f.z(lat + dl, along), BARREL);
		}
		for (int dl = -2; dl <= 2; dl++) {
			c.set(f.x(lat + dl, along), 4, f.z(lat + dl, along), AWNING);
			c.set(f.x(lat + dl, along + inward), 4, f.z(lat + dl, along + inward), AWNING);
		}
		c.set(f.x(lat - 1, along + inward), 3, f.z(lat - 1, along + inward), HANGING_LANTERN);
		c.set(f.x(lat + 1, along + inward), 3, f.z(lat + 1, along + inward), HANGING_LANTERN);
	}

	// ------------------------------------------------------------------ plaza of the main ramp

	/**
	 * The plaza at the foot of the main ramp ({@code |x| <= PLAZA_FRAC * AVENUE_HALF}): a tall lantern on each
	 * side of the plazoleta's middle (the axis stays clear: the spawn is there), benches facing it, garden
	 * planters further out and two lantern posts at each end.
	 */
	private static void plaza(CitadelCanvas c, int x, int z, double lo, double hi) {
		if (z <= 0) {
			return;
		}
		int edge = (int) Math.round(CitadelPaving.PLAZA_FRAC * CitadelPaving.AVENUE_HALF());
		int mid = (int) Math.round((lo + hi) / 2);
		int step = (int) Math.max(1, Math.round((hi - lo) / 4)); // the benches stand this far from the middle line
		int ax = Math.abs(x);
		if (ax == (int) Math.round(CitadelPaving.AVENUE_HALF()) && z == mid) {
			c.rigid(x, z);
			focalLantern(c, x, z); // beside the plazoleta's middle, never on the axis: the spawn stands there
			c.unrigid();
		} else if (ax == edge && (z == Math.round(lo + 1) || z == Math.round(hi - 1))) {
			lanternPost(c, x, z, PLAZA_POST);
		} else if (ax == edge - 2 && (z == mid || z == mid - 1)) {
			c.set(x, 1, z, c.kit.gardenPlanter());
		} else if (ax == (int) Math.round(CitadelPaving.AVENUE_HALF()) + 1 && Math.abs(z - mid) == step) {
			bench(c, x, z, nearest(0, z - mid));
		}
	}
}
