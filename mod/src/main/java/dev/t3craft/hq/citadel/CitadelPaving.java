package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.BAMBOO;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.terracotta;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The pavement of the citadel: which block goes on each walkable ground cell. Pure functions of the
 * cell (no state, no randomness per run: every variation is a hash of the coordinates), used by
 * {@link CitadelGrounds}. Nothing here lights the floor: the light is {@code citadel/1-light}'s, and
 * the decorative light (lanterns) belongs to {@link CitadelStreetscape}, which stands on this paving.
 *
 * <h2>Hierarchy (struct/streets-plazas)</h2>
 *
 * Four levels, from the most to the least important, each told apart by material, width and edge. Every
 * road has the same section, from the lots in: an <b>acera</b> (sidewalk: smooth stone with a stone-brick
 * course across it every {@link #ACERA_EVERY} cells; where the furniture stands), a one-cell dark
 * <b>gutter</b> (deepslate tiles) and the roadway. What changes with the level is the roadway:
 *
 * <ol>
 * <li><b>Avenues</b> (radial, 13 wide at scale 2, {@link #avenue}): a runner of the kit's floor (the hub's
 *     salmon boards) down the axis, a strip of the kit's stone tile, a course of stone bricks (the
 *     <i>cenefa</i>), the gutter and the acera. Cross joints of polished andesite every so often.</li>
 * <li><b>Streets</b> (the inner street and the ring streets, 10-12 wide, {@link #street}): a lane of one
 *     tone against each gutter and a middle band of another: polished andesite over stone bricks for the
 *     grey inner street and ring 1, mud bricks over packed mud for the warm streets of rings 2 and 3. A
 *     joint across the road every so often; almost no noise.</li>
 * <li><b>Crossings</b>: where an avenue (or a ramp) meets a street the kerb opens and a <i>plazoleta</i>
 *     ({@link #plazoleta}) spreads over the road: a chiselled plinth, a pinwheel of terracotta, a calcite
 *     ring, a polished ring, a field of tile with arcs of joints (the avenue's runner flows through it) and
 *     a polished rim. {@link CitadelStreetscape#crossings} stands its focal point, its benches, its sign
 *     and its stall on it. Ring 1's crossings hold the node rooms, so they keep the plain street.</li>
 * <li><b>Paths</b> of the patio and the garden ({@link #causeway}, {@link #patioRing}): bamboo
 *     runners and the paved ring of the patio. (The lanes of the lots are
 *     {@code CitadelPlacement}'s.)</li>
 * </ol>
 *
 * Gutters and aceras are flat (a walker never steps on one). Every function returns blocks of the vanilla
 * set or of the kit ({@code c.floor()}, {@code c.accent()}).
 *
 * <h2>Sizes</h2>
 *
 * Nothing is a fixed size: every width, radius and spacing is a fraction of the width of the zone it
 * paves (so it follows the scale of the geometry); the few constants below are those proportions, plus
 * mirrors of the geometry's own package-private numbers.
 */
final class CitadelPaving {
	static final BlockState BRICKS = b(Blocks.STONE_BRICKS);
	static final BlockState CRACKED = b(Blocks.CRACKED_STONE_BRICKS);
	static final BlockState MOSSY = b(Blocks.MOSSY_STONE_BRICKS);
	static final BlockState CHISELED = b(Blocks.CHISELED_STONE_BRICKS);
	static final BlockState JOINT = b(Blocks.POLISHED_ANDESITE);
	static final BlockState KERB_CRACKED = b(Blocks.CRACKED_DEEPSLATE_TILES);
	static final BlockState RUNNER = b(Blocks.BAMBOO_PLANKS);
	static final BlockState RUNNER_SLAB = slab(Blocks.BAMBOO_SLAB);
	static final BlockState KERB_SLAB = slab(Blocks.DEEPSLATE_TILE_SLAB);
	static final BlockState CALCITE = CitadelCanvas.CALCITE;
	static final BlockState POLISHED = b(Blocks.POLISHED_DEEPSLATE);
	static final BlockState SIDE = b(Blocks.SMOOTH_STONE);
	static final BlockState SIDE_SLAB = slab(Blocks.SMOOTH_STONE_SLAB);
	static final BlockState MUD = b(Blocks.MUD_BRICKS);
	static final BlockState PACKED_MUD = b(Blocks.PACKED_MUD);
	static final BlockState RIB_BAND = CitadelCanvas.RIB;
	/** The sidewalk of every road: smooth stone, with a course of stone brick across it every {@link #ACERA_EVERY} cells. */
	static final BlockState ACERA = SIDE;
	static final BlockState ACERA_PAVER = JOINT;
	/** Period (cells along the road) of the courses of the sidewalk: a pattern, not a size, so it is not scaled. */
	static final int ACERA_EVERY = 6;

	// Sizes: all proportions or mirrors of the geometry.

	/** Half width of an avenue zone ({@code Dims.avenueHalf}, 3.4 unscaled). */
	static double AVENUE_HALF() {
		return CitadelCanvas.dims().avenueHalf;
	}

	/** Half width of the bridges' deck ({@code Dims.bridgeHalf}, the {@code |x| <= 3} of {@code RingCampusGeometry.zone} unscaled). */
	static int BRIDGE_HALF() {
		return CitadelCanvas.dims().bridgeHalf;
	}
	/** A kerb is this fraction of its road's width (never under {@link #KERB_MIN}, which keeps it one line). */
	static final double KERB_FRAC = 0.18;
	static final double KERB_MIN = 1.0;
	/** The cenefa of an avenue and the worn lane of a street: this fraction of the road's width. */
	static final double EDGE_FRAC = 0.16;
	/** The polished middle of a street starts this fraction of its width from each kerb. */
	static final double CENTRE_FRAC = 0.33;
	/** An avenue's runner is this fraction of its half width, each side of the axis. */
	static final double RUNNER_FRAC = 0.22;
	/** Joints every this many widths of the road. */
	static final double JOINT_FRAC = 1.15;
	/** A sidewalk is this fraction of its road's width (never under {@link #ACERA_MIN} cells), and a gutter is one cell. */
	static final double ACERA_FRAC = 0.15;
	static final double ACERA_MIN = 1.5;
	static final double GUTTER_W = 1.0;
	/** The lane of a street against each gutter: this fraction of the street's width. */
	static final double LANE_FRAC = 0.12;
	/**
	 * A crossing's plazoleta, as fractions of the street's width: radius of the plinth (a 3x3 at the focal
	 * point), of the terracotta pinwheel, of the calcite ring, of the polished ring, and of the whole plazoleta
	 * (its last cell is a polished rim).
	 */
	static final double HEART_FRAC = 0.12;
	static final double DISC_FRAC = 0.30;
	static final double RING_FRAC = 0.40;
	static final double RIM_FRAC = 0.47;
	static final double PLAZA_R_FRAC = 0.92;
	/** The plaza at the foot of the main ramp: half width, in avenue half widths. */
	static final double PLAZA_FRAC = 2.35;
	/** Half width of a causeway over the patio's pool (the pool's paths): 1.2 unscaled. */
	static double CAUSEWAY_D() {
		return AVENUE_HALF() * 0.35;
	}

	/** Perpendicular distance from the axis within which an avenue's field is paved (its kerb is beyond). */
	static double KERB_D() {
		return AVENUE_HALF() - Math.max(KERB_MIN, KERB_FRAC * 2 * AVENUE_HALF());
	}

	/** Half width of an avenue's runner. */
	static double RUNNER_D() {
		return AVENUE_HALF() * RUNNER_FRAC;
	}

	/** Width of an avenue's cenefa (a course of stone bricks between the tile and the gutter): 1 cell at scale 2. */
	static double CENEFA_W() {
		return Math.max(1.0, 0.08 * 2 * AVENUE_HALF());
	}

	/** Width of the sidewalk of a road {@code width} wide. */
	static double ACERA_W(double width) {
		return Math.max(ACERA_MIN, ACERA_FRAC * width);
	}

	/** Lateral distance from an avenue's axis beyond which its column is sidewalk (4.8 at scale 2; the avenue's zone ends at 6.8). */
	static double ACERA_D() {
		return AVENUE_HALF() - ACERA_W(2 * AVENUE_HALF());
	}

	/** Lateral distance up to which an avenue is open roadway: where it meets a street the kerb opens this wide. The gutter lies beyond it. */
	static double MOUTH_D() {
		return ACERA_D() - GUTTER_W;
	}

	private CitadelPaving() {
	}

	// ------------------------------------------------------------------ geometry helpers

	/** Angle (from north, clockwise) of the radial of {@code n} nearest to a cell. */
	static double radialAngle(int x, int z, int n) {
		double a = RingCampusGeometry.theta(x, z);
		int k = (int) Math.round(a * n / (2 * Math.PI)) % n;
		return k * 2 * Math.PI / n;
	}

	/** Distance along the nearest of {@code n} radials (the cell's projection on its axis). */
	static double along(int x, int z, int n) {
		double ang = radialAngle(x, z, n);
		return x * Math.sin(ang) - z * Math.cos(ang);
	}

	/** Signed distance across the nearest of {@code n} radials. */
	static double across(int x, int z, int n) {
		double ang = radialAngle(x, z, n);
		return x * Math.cos(ang) + z * Math.sin(ang);
	}

	/** A deterministic hash of a cell in [0, 97). */
	static int hash(int x, int z) {
		int h = x * 73856093 ^ z * 19349663;
		h ^= h >>> 13;
		return Math.floorMod(h * 0x5bd1e995, 97);
	}

	/** Distance between joints of a road {@code width} wide. */
	static int jointEvery(double width) {
		return Math.max(3, (int) Math.round(width * JOINT_FRAC));
	}

	/** The cell is a joint of a road {@code width} wide running along {@code s} (one cell thick). */
	private static boolean joint(double s, double width) {
		return Math.floorMod((int) Math.round(s), jointEvery(width)) == 0;
	}

	/** Whether a block is a kerb of the roads (the gutter line). */
	static boolean isKerb(BlockState s) {
		return s == FOOT || s == KERB_CRACKED;
	}

	/** Whether a block is the sidewalk of a road (where the street furniture stands). */
	static boolean isAcera(BlockState s) {
		return s == ACERA || s == ACERA_PAVER;
	}

	/** A gutter block: deepslate tiles, one in nine cracked. */
	private static BlockState kerb(int x, int z) {
		return hash(x, z) % 9 == 0 ? KERB_CRACKED : FOOT;
	}

	/** A sidewalk cell {@code s} along its road: smooth stone with a course of stone brick across it every {@link #ACERA_EVERY} cells. */
	static BlockState sidewalk(double s) {
		return Math.floorMod((int) Math.round(s), ACERA_EVERY) == 0 ? ACERA_PAVER : ACERA;
	}

	// ------------------------------------------------------------------ avenues and ramps

	/** An avenue column of ring {@code zn.ring()}. */
	static BlockState avenue(CitadelCanvas c, int x, int z, Zone zn) {
		int n = RingCampusGeometry.lotsIn(zn.ring());
		double d = RingCampusGeometry.radialDistance(x, z, n);
		double s = along(x, z, n);
		if (d > ACERA_D()) {
			return sidewalk(s);
		}
		BlockState plaza = avenuePlaza(c, x, z, zn.ring(), n, d, s); // the plazoleta also swallows the gutter
		if (plaza != null) {
			return plaza;
		}
		if (d > MOUTH_D()) {
			return kerb(x, z); // the gutter
		}
		return avenueField(c, x, z, d, s);
	}

	/**
	 * The plazoleta over the end of an avenue, where it meets a street: at its inner end (the street inside, or
	 * the inner street for ring 1) and at its outer end (ring 2 and 3 only: ring 1's outer end is a node room).
	 * {@code null} away from the crossings.
	 */
	private static @Nullable BlockState avenuePlaza(CitadelCanvas c, int x, int z, int m, int n, double d, double s) {
		double l = across(x, z, n);
		double w = m == 1 ? RingCampusGeometry.ringInner(1) - CitadelCanvas.dims().rampR : CitadelCanvas.dims().street;
		double rc = m == 1 ? (CitadelCanvas.dims().rampR + RingCampusGeometry.ringInner(1)) / 2 : RingCampusGeometry.ringInner(m) - w / 2.0;
		double zebra = d > RUNNER_D() ? l : Double.NaN; // the crossing over the avenue's mouth: stripes along the avenue, the runner stays
		double md = Math.hypot(l, s - rc);
		if (md <= PLAZA_R_FRAC * w) {
			return plazoleta(c, md, l, s - rc, d, w, zebra);
		}
		if (m >= 2) {
			double wo = CitadelCanvas.dims().street;
			double rco = RingCampusGeometry.ringOuter(m) + wo / 2.0;
			double mdo = Math.hypot(l, s - rco);
			if (mdo <= PLAZA_R_FRAC * wo) {
				return plazoleta(c, mdo, l, s - rco, d, wo, zebra);
			}
		}
		return null;
	}

	/**
	 * The paved part of an avenue (or of its mouth into a street): a runner of the kit's floor down the axis, a
	 * strip of the kit's tile, a plain course of stone bricks (the cenefa; a chiselled brick at each joint) and
	 * polished joints across it every so often.
	 */
	private static BlockState avenueField(CitadelCanvas c, int x, int z, double d, double s) {
		if (d <= RUNNER_D()) {
			return c.floor();
		}
		boolean joint = joint(s, 2 * AVENUE_HALF());
		if (d > MOUTH_D() - CENEFA_W()) {
			return joint ? CHISELED : BRICKS;
		}
		return joint ? JOINT : c.accent();
	}

	/** Kind of a ramp column: 0 runner, 1 bamboo core, 2 smooth-stone side, 3 kerb. */
	static int rampBand(int x, int z) {
		double d = RingCampusGeometry.radialDistance(x, z, 8);
		return d <= RUNNER_D() ? 0 : d <= AVENUE_HALF() * 0.5 ? 1 : d <= KERB_D() ? 2 : 3;
	}

	/** The full block of a ramp step of band {@code band}. */
	static BlockState rampFull(int band) {
		return band == 0 ? RUNNER : band == 1 ? BAMBOO : band == 2 ? SIDE : FOOT;
	}

	/** The half block (when the step is a half) of a ramp column of band {@code band}. */
	static BlockState rampHalf(int band) {
		return band == 0 ? RUNNER_SLAB : band == 1 ? slab(Blocks.BAMBOO_MOSAIC_SLAB) : band == 2 ? SIDE_SLAB : KERB_SLAB;
	}

	// ------------------------------------------------------------------ streets and crossings

	/** A column of the inner street or of a ring street. */
	static BlockState street(CitadelCanvas c, int x, int z, double r, Zone zn) {
		boolean inner = zn.kind() == Kind.INNER_STREET;
		int m = zn.ring();
		double lo = inner ? CitadelCanvas.dims().rampR : RingCampusGeometry.ringOuter(m);
		double hi = inner ? RingCampusGeometry.ringInner(1) : RingCampusGeometry.streetOuter(m);
		double rc = (lo + hi) / 2;
		double width = hi - lo;
		int nIn = inner ? 8 : RingCampusGeometry.lotsIn(m);
		int nOut = inner ? 8 : m < c.rings ? RingCampusGeometry.lotsIn(m + 1) : 0;
		double dIn = RingCampusGeometry.radialDistance(x, z, nIn);
		double dOut = nOut == 0 ? 99 : RingCampusGeometry.radialDistance(x, z, nOut);
		boolean plaza = inner && z > 0 && Math.abs(x) <= PLAZA_FRAC * AVENUE_HALF();
		boolean mouthIn = dIn <= MOUTH_D() || plaza;
		boolean mouthOut = dOut <= MOUTH_D();
		// the edge: an acera against the lots, then a one-cell dark gutter against the roadway (open at an avenue's mouth)
		double acera = ACERA_W(width);
		double band = acera + GUTTER_W;
		boolean loEdge = r - lo <= band && !mouthIn;
		boolean hiEdge = hi - r < band && !mouthOut;
		if (loEdge || hiEdge) {
			double e = loEdge ? r - lo : hi - r;
			return e <= acera ? sidewalk(RingCampusGeometry.theta(x, z) * rc) : kerb(x, z);
		}
		// the plazoleta of a crossing, over the road only: ring 1's crossings are node rooms, so it has none
		if (inner || m >= 2) {
			double md = 99;
			double dx = 0;
			double dz = 0;
			double lat = 99;
			if (plaza) {
				md = Math.hypot(x, z - rc);
				dx = x;
				dz = z - rc;
				lat = Math.abs(x);
			} else {
				double ang = radialAngle(x, z, nIn);
				dx = x - rc * Math.sin(ang);
				dz = z + rc * Math.cos(ang);
				md = Math.hypot(dx, dz);
				lat = dIn;
				if (nOut > 0) {
					double ango = radialAngle(x, z, nOut);
					double ox = x - rc * Math.sin(ango);
					double oz = z + rc * Math.cos(ango);
					if (Math.hypot(ox, oz) < md) {
						md = Math.hypot(ox, oz);
						dx = ox;
						dz = oz;
						lat = dOut;
					}
				}
			}
			if (md <= PLAZA_R_FRAC * width) {
				return plazoleta(c, md, dx, dz, lat, width, lat > MOUTH_D() ? r : Double.NaN); // a crossing over the street's arms
			}
		}
		if (mouthIn || mouthOut) {
			// the avenue flows across the street
			double d = mouthOut && dOut <= dIn ? dOut : dIn;
			int n = mouthOut && dOut <= dIn ? nOut : nIn;
			return plaza ? plazaField(c, x, z, rc, width) : avenueField(c, x, z, d, along(x, z, n));
		}
		return m >= 2 ? warmField(x, z, r, lo, hi, band) : streetField(x, z, r, lo, hi, band);
	}

	/**
	 * The plazoleta of a crossing, {@code md} from its centre ({@code dx}, {@code dz} the offsets, {@code lat} the
	 * distance to the avenue's axis, {@code width} the street's): a chiselled-brick plinth (the focal point's
	 * base), a pinwheel of pink and magenta terracotta, a ring of calcite, a ring of polished deepslate, then a
	 * field of the kit's tile with an arc of joints every three cells (the avenue's runner flows through it) and
	 * a polished rim on the last cell. Covers {@code md <= PLAZA_R_FRAC * width}.
	 */
	static BlockState plazoleta(CitadelCanvas c, double md, double dx, double dz, double lat, double width, double zebra) {
		if (md <= HEART_FRAC * width) {
			return CHISELED;
		}
		if (md <= DISC_FRAC * width) {
			return terracotta((dx >= 0) == (dz >= 0) ? DyeColor.PINK : DyeColor.MAGENTA);
		}
		if (md <= RING_FRAC * width) {
			return CALCITE;
		}
		if (md <= RIM_FRAC * width) {
			return POLISHED;
		}
		if (md > PLAZA_R_FRAC * width - 1.0) {
			return POLISHED;
		}
		if (lat <= RUNNER_D()) {
			return c.floor();
		}
		// a zebra crossing near the rim, on the arms: stripes of calcite along the way, every other cell
		if (!Double.isNaN(zebra) && md >= PLAZA_R_FRAC * width - 4.0 && md <= PLAZA_R_FRAC * width - 1.5 && Math.floorMod((int) Math.round(zebra), 2) == 0) {
			return CALCITE;
		}
		// concentric rings of polished deepslate over polished andesite
		return Math.floorMod((int) Math.round(md), 3) == 0 ? POLISHED : JOINT;
	}

	/** The plaza at the foot of the main ramp: the avenue's tile, with arcs of joints round the rosette. */
	private static BlockState plazaField(CitadelCanvas c, int x, int z, double rc, double width) {
		double md = Math.hypot(x, z - rc);
		if (Math.abs(x) <= RUNNER_D()) {
			return c.floor();
		}
		return Math.floorMod((int) Math.round(md), Math.max(2, jointEvery(width) / 2)) == 0 ? JOINT : c.accent();
	}

	/**
	 * The roadway of a grey street (the inner street and ring 1): a lane of stone bricks against each gutter
	 * (one in twenty cracked) and a middle band of polished andesite with a course of brick across it at each
	 * joint. Two tones, no noise. {@code band} is the width of the acera and the gutter.
	 */
	private static BlockState streetField(int x, int z, double r, double lo, double hi, double band) {
		double width = hi - lo;
		double edge = Math.min(r - lo, hi - r) - band;
		double arc = RingCampusGeometry.theta(x, z) * (lo + hi) / 2;
		if (edge < LANE_FRAC * width) {
			return hash(x, z) < 5 ? CRACKED : BRICKS;
		}
		if (median(r, lo, hi)) {
			return POLISHED;
		}
		return joint(arc, width) ? BRICKS : JOINT;
	}

	/** The cell is on the median of a street: a band about two cells wide down the middle, in a tone of its own. */
	private static boolean median(double r, double lo, double hi) {
		return Math.abs(r - (lo + hi) / 2) < 0.9;
	}

	/**
	 * The roadway of a neighbourhood street (rings 2 and 3, warmer and humbler than the grey ones): a lane of
	 * packed mud against each gutter (one in twenty mossy) and a middle band of mud bricks with a packed-mud
	 * joint across it every so often.
	 */
	private static BlockState warmField(int x, int z, double r, double lo, double hi, double band) {
		double width = hi - lo;
		double edge = Math.min(r - lo, hi - r) - band;
		double arc = RingCampusGeometry.theta(x, z) * (lo + hi) / 2;
		if (edge < LANE_FRAC * width) {
			return hash(x, z) < 5 ? MOSSY : PACKED_MUD;
		}
		if (median(r, lo, hi)) {
			return BRICKS;
		}
		return joint(arc, width) ? PACKED_MUD : MUD;
	}

	// ------------------------------------------------------------------ verges

	/** Width of the verge of a lot against a road: 0.45 of an avenue half width (1.5 unscaled). */
	static double VERGE_W() {
		return AVENUE_HALF() * 0.45;
	}

	/**
	 * The ground of a lot cell: grass, with a verge of moss against the roads (its avenues, the street
	 * inside and the street outside) so that the pavement never meets the lawn in a clean cut: mostly
	 * moss against the kerb, thinning out over {@link #VERGE_W()}.
	 */
	static BlockState lotGround(CitadelCanvas c, int x, int z, Zone zn) {
		int m = zn.ring();
		double r = RingCampusGeometry.r(x, z);
		double d = RingCampusGeometry.radialDistance(x, z, RingCampusGeometry.lotsIn(m));
		double edge = Math.min(d - AVENUE_HALF(),
				Math.min(r - RingCampusGeometry.ringInner(m), RingCampusGeometry.ringOuter(m) - r));
		if (edge >= VERGE_W()) {
			return CitadelCanvas.GRASS;
		}
		int h = hash(x, z);
		int moss = edge < VERGE_W() / 2 ? 70 : 35;
		return h < moss ? CitadelCanvas.MOSS : h < moss + 12 ? b(Blocks.COARSE_DIRT) : CitadelCanvas.GRASS;
	}

	/**
	 * What lies on the verge of a lot at {@code y = 1}, or {@code null}: a low stone-brick slab (one in
	 * three mossy) on the first row against the kerb, which turns the kerb into a step down to the
	 * lawn, and a flowering bush on the second. Never inside the barrio's own square.
	 */
	static @Nullable BlockState vergeCover(CitadelCanvas c, int x, int z, Zone zn) {
		int m = zn.ring();
		double r = RingCampusGeometry.r(x, z);
		double d = RingCampusGeometry.radialDistance(x, z, RingCampusGeometry.lotsIn(m));
		double edge = Math.min(d - AVENUE_HALF(),
				Math.min(r - RingCampusGeometry.ringInner(m), RingCampusGeometry.ringOuter(m) - r));
		if (edge >= VERGE_W()) {
			return null;
		}
		RingCampusGeometry.Lot l = RingCampusGeometry.lot(zn.lot());
		if (Math.abs(l.a(x, z)) <= l.reach() && Math.abs(l.b(x, z)) <= l.reach()) {
			return null;
		}
		int h = hash(x, z);
		if (edge < VERGE_W() / 2) {
			return h % 3 == 0 ? slab(Blocks.MOSSY_STONE_BRICK_SLAB) : slab(Blocks.STONE_BRICK_SLAB);
		}
		return h < 14 ? CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES) : null;
	}

	// ------------------------------------------------------------------ patio and bridges

	/**
	 * The paved ring of the patio ({@code rIn < r <= PATIO_R}): the pool's coping, paving with a joint
	 * every so often, and a rim of the kit's floor outside, with bamboo runners on the radials. The
	 * coping and the rim are each a quarter of the ring's width.
	 */
	static BlockState patioRing(CitadelCanvas c, int x, int z, double r, double rIn) {
		double w = CitadelCanvas.dims().patioR - rIn;
		if (r <= rIn + 0.28 * w) {
			return FOOT;
		}
		if (RingCampusGeometry.radialDistance(x, z, 8) <= RUNNER_D()) {
			return BAMBOO;
		}
		if (r > CitadelCanvas.dims().patioR - 0.28 * w) {
			return c.floor();
		}
		return joint(RingCampusGeometry.theta(x, z) * (rIn + CitadelCanvas.dims().patioR) / 2, w) ? JOINT : c.accent();
	}

	/** A causeway cell over the patio's pool: a bamboo runner between two deepslate edges. */
	static BlockState causeway(int x, int z) {
		return RingCampusGeometry.radialDistance(x, z, 8) <= RUNNER_D() ? BAMBOO : FOOT;
	}

	/** The deck of a bridge: edge, tile, a runner down the axis and a joint every so often. */
	static BlockState bridgeDeck(CitadelCanvas c, int x, int z) {
		int ax = Math.abs(x);
		if (ax >= BRIDGE_HALF()) {
			return FOOT;
		}
		if (ax == 0) {
			return c.floor();
		}
		return joint(z, 2 * BRIDGE_HALF()) ? JOINT : c.accent();
	}

	// ------------------------------------------------------------------ promenade

	/** Width of the elevated ring. */
	static double promenadeWidth() {
		return CitadelCanvas.dims().galleryR - CitadelCanvas.dims().patioR;
	}

	/** Radius up to which the promenade is the inner edge (where the railing stands). */
	static double promenadeInner() {
		return CitadelCanvas.dims().patioR + 0.2 * promenadeWidth();
	}

	/** Radius from which the promenade is the outer rim. */
	static double promenadeRim() {
		return CitadelCanvas.dims().galleryR - promenadeWidth() / 6;
	}

	/**
	 * The deck of the elevated ring: a deepslate rim outside (open where a ramp leaves), a stripe of
	 * tile at each edge, the kit's floor between with a joint of tile every so often.
	 */
	static BlockState promenade(CitadelCanvas c, int x, int z, double r) {
		double w = promenadeWidth();
		boolean mouth = RingCampusGeometry.radialDistance(x, z, 8) <= KERB_D();
		if (r > promenadeRim()) {
			return mouth ? c.accent() : CitadelPodium.rim(r); // design/citadel-terraces2: a light coping on the outer edge
		}
		if (r > promenadeRim() - w / 6 || r <= promenadeInner()) {
			return c.accent();
		}
		return joint(RingCampusGeometry.theta(x, z) * (CitadelCanvas.dims().patioR + CitadelCanvas.dims().galleryR) / 2, w) ? c.accent() : c.floor();
	}
}
