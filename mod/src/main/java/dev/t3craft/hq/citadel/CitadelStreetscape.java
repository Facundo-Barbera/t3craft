package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Zone;
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
 * crossing is an octagonal plaza with the same things (a lantern you can see from the avenue, planters
 * with benches that face it, a sign), so the city reads at a glance. struct/streets-plazas2: the
 * plazoletas became octagons with a raised kerb and planters; the stall went (its place is the planters).
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
 * <li><b>Crossings</b> ({@link #crossings}): on every octagonal plaza ({@link CitadelPlaza}) of rings 2 and 3
 *     and of the inner street (not the spawn's plaza): the focal point (a chiselled plinth, a stone shaft 9
 *     high with buttresses, four lantern arms and the kit's tall paper lantern, {@code paper_lantern_tall},
 *     light 15: 12 high, a landmark from the avenue), the kerb (a tile slab half a block over the floor, walked
 *     over), and in each of the four quadrants between the roads a planter (low brick wall, flowering azalea,
 *     a cherry tree at one flank, a lantern post at the other, a bin in the wall) with three cherry benches in
 *     front of it facing the focal point; one quadrant per plaza holds the signpost (a log stele with a plaque:
 *     ring and compass heading of the avenue). It runs after the catalog's placement and only on free cells:
 *     where the catalog built a plaza centre of its own, the crossing is left to it.</li>
 * <li><b>Plaza</b> ({@link #plaza}): the plaza at the foot of the main ramp keeps its lanterns, benches and
 *     planters; the axis stays clear (the spawn is there).</li>
 * </ul>
 *
 * Nothing stands on a cell of a node or a lot: canopies skip those cells (except over a plaza). Light: the
 * street's lamps are the catalog's lamps and {@link CitadelAmbience}'s posts; what stands here lights only where
 * it is a focal point (the tall lantern, the lantern posts of the planters). Never on the lane of the walkers:
 * everything stands on a sidewalk, or in the quadrants of a plaza, never on the arms of its roads.
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
		return CitadelCanvas.dims().s(7);
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
	static final int FOCAL_POSTS = 9;
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
		return k == Kind.AVENUE || k == Kind.STREET || k == Kind.INNER_STREET || k == Kind.TERRACE || k == Kind.LAWN
				|| CitadelPlaza.at(c, x, z) != null;
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
		c.set(x, FOCAL_POSTS - 1, z, PLINTH); // a collar of chiselled brick on the shaft
		int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] a : arms) {
			int ax = x + a[0];
			int az = z + a[1];
			// a buttress of wall blocks on each face of the shaft, so it is not a pole
			if (freeColumn(c, ax, az, 2, 5) && c.at(ax, 0, az) != null) {
				c.fill(ax, 2, 5, az, POST);
			}
			// the arm: two cherry slabs at the top of the shaft, a lantern hanging from the far one
			int bx = x + 2 * a[0];
			int bz = z + 2 * a[1];
			if (freeColumn(c, ax, az, FOCAL_POSTS, FOCAL_POSTS + 1) && freeColumn(c, bx, bz, FOCAL_POSTS, FOCAL_POSTS + 1) && c.at(bx, 0, bz) != null) {
				c.set(ax, FOCAL_POSTS + 1, az, AWNING);
				c.set(bx, FOCAL_POSTS + 1, bz, AWNING);
				c.set(bx, FOCAL_POSTS, bz, HANGING_LANTERN);
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
		if (CitadelPlaza.at(c, x, z) != null) {
			return; // the plaza of a crossing has its own furniture
		}
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
		if (ground == null || !CitadelPaving.isAcera(ground) || Math.min(r - lo, hi - r) > 1.5 || CitadelPlaza.at(c, x, z) != null) {
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

	/**
	 * The furniture of every octagonal plaza ({@link CitadelPlaza}), in one pass over the open rings (called by
	 * {@link CitadelFinish#build} after the catalog's placement and before the relief, so it sees what the catalog
	 * built and moves with the terraces). Rings 2 and 3 and the inner street, not the spawn's plaza; ring 1's
	 * crossings are node rooms.
	 */
	static void crossings(CitadelCanvas c) {
		for (CitadelPlaza.Plaza p : CitadelPlaza.all(c)) {
			crossing(c, p);
		}
	}

	/** A cell of the plaza with ground and free air over it up to {@code top}. */
	private static boolean plazaFree(CitadelCanvas c, CitadelPlaza.Plaza p, int x, int z, int top) {
		return c.at(x, 0, z) != null && p.contains(c, x, z) && freeColumn(c, x, z, 1, top);
	}

	private static void crossing(CitadelCanvas c, CitadelPlaza.Plaza p) {
		int cx = p.cx();
		int cz = p.cz();
		if (c.at(cx, 0, cz) != PLINTH || !freeColumn(c, cx, cz, 1, 3 + FOCAL_POSTS)) {
			return; // not a plaza of ours, or the catalog built a plaza centre of its own
		}
		int reach = (int) Math.ceil(CitadelPlaza.KERB_K / Math.min(p.ku(), p.kv())) + 1;
		c.rigid(cx, cz);
		focalLantern(c, cx, cz);
		for (int x = cx - reach; x <= cx + reach; x++) {
			for (int z = cz - reach; z <= cz + reach; z++) {
				if (p.metric(x, z) >= CitadelPlaza.KERB_K + 0.5 || !plazaFree(c, p, x, z, 1)) {
					continue;
				}
				int k = p.band(x, z);
				if (k >= CitadelPlaza.KERB_K) {
					c.set(x, 1, z, CitadelPlaza.KERB_TOP); // the kerb: half a block over the floor, walked over
				} else if (p.quadrant(x, z)) {
					if (k == CitadelPlaza.BENCH_K) {
						bench(c, x, z, nearest(x - cx, z - cz)); // back to the planter, facing the focal point
					} else if (k == CitadelPlaza.BED_K) {
						c.set(x, 1, z, BASE); // the planter's low wall
					} else if (k == CitadelPlaza.BED_K + 1) {
						c.set(x, 1, z, leaves(CitadelPaving.hash(x, z) % 3 == 0 ? Blocks.AZALEA_LEAVES : Blocks.FLOWERING_AZALEA_LEAVES));
					}
				}
			}
		}
		if (p.planters()) {
			for (int sl = -1; sl <= 1; sl += 2) {
				for (int sa = -1; sa <= 1; sa += 2) {
					quadrantPieces(c, p, sl, sa);
				}
			}
		}
		c.unrigid();
	}

	/** The planter of one quadrant: a cherry tree at one flank, a lantern post at the other, a bin in the wall, and (one quadrant per plaza) the signpost. */
	private static void quadrantPieces(CitadelCanvas c, CitadelPlaza.Plaza p, int sl, int sa) {
		BlockPos tree = p.cell(sl * 5, sa * 10);
		BlockPos lamp = p.cell(sl * 10, sa * 5);
		BlockPos bin = p.cell(sl * 5, sa * 8);
		if (plantable(c, p, tree.getX(), tree.getZ()) && freeColumn(c, tree.getX(), tree.getZ(), 2, TRUNK() + 2)) {
			tree(c, tree.getX(), tree.getZ());
		}
		if (plantable(c, p, lamp.getX(), lamp.getZ()) && freeColumn(c, lamp.getX(), lamp.getZ(), 2, 4)) {
			lanternPost(c, lamp.getX(), lamp.getZ(), 3);
		}
		if (CitadelPlaza.BED_K == p.band(bin.getX(), bin.getZ()) && p.quadrant(bin.getX(), bin.getZ())) {
			bin(c, bin.getX(), bin.getZ());
		}
		if (sl > 0 && sa > 0) {
			sign(c, p);
		}
	}

	/** A bed cell of a planter (ring 9 or 10, in a quadrant), whose wall or bush a piece may replace. */
	private static boolean plantable(CitadelCanvas c, CitadelPlaza.Plaza p, int x, int z) {
		int k = p.band(x, z);
		return p.quadrant(x, z) && k >= CitadelPlaza.BED_K && k < CitadelPlaza.KERB_K && p.contains(c, x, z) && c.at(x, 0, z) != null;
	}

	/** A signpost in the planter at (8, 6): a log stele three high with a plaque facing the centre (ring and heading of the avenue), and a cap. */
	private static void sign(CitadelCanvas c, CitadelPlaza.Plaza p) {
		BlockPos st = p.cell(8, 6);
		BlockPos pl = p.cell(8, 5);
		int sx = st.getX();
		int sz = st.getZ();
		int px = pl.getX();
		int pz = pl.getZ();
		if (!plantable(c, p, sx, sz) || !plantable(c, p, px, pz) || sx == px && sz == pz || !freeColumn(c, sx, sz, 2, 4)
				|| !freeColumn(c, px, pz, 2, 3)) {
			return;
		}
		c.fill(sx, 1, 3, sz, STELE);
		c.set(sx, 4, sz, AWNING);
		Direction face = nearest(px - sx, pz - sz);
		long k = BlockPos.asLong(px, 3, pz);
		c.put(px, 3, pz, c.kit.plaque(face), CitadelCanvas.DECOR);
		double ang = Math.atan2(p.ux(), -p.uz());
		String ring = p.m() == 0 ? "INNER STREET" : "RING " + p.m();
		String way = COMPASS[Math.floorMod((int) Math.round(ang / (2 * Math.PI) * 16), 16)] + " AVENUE";
		c.data.put(k, c.kit.plaqueData(DyeColor.ORANGE, "", ring, way, ""));
		c.fixtures.add(k);
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
