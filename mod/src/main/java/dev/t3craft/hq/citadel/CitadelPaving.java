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

/**
 * The pavement of the citadel: which block goes on each walkable ground cell. Pure functions of the
 * cell (no state, no randomness per run: every variation is a hash of the coordinates), used by
 * {@link CitadelGrounds}. Nothing here lights the floor: the light is {@code citadel/1-light}'s, and
 * the decorative light (lanterns) belongs to {@link CitadelStreetscape}, which stands on this paving.
 *
 * <h2>Hierarchy</h2>
 *
 * Four levels, from the most to the least important, each told apart by material, width and edge:
 *
 * <ol>
 * <li><b>Avenues</b> (radial, 7 wide, {@link #avenue}): from the axis out, a runner of the kit's floor
 *     (the hub's own salmon boards), the kit's stone tile with a polished joint every so often, a
 *     <i>cenefa</i> of stone bricks with a chiselled brick every other cell, and a deepslate kerb. The
 *     ramps ({@link #rampBand}) carry the same cross-section in bamboo.</li>
 * <li><b>Streets</b> (the inner street and the ring streets, 5-6 wide, {@link #street}): a gradient
 *     across the width, polished in the middle (polished andesite), stone bricks, then a worn lane
 *     (mossy and cracked bricks, a chiselled brick every third cell) against the kerb, and a deepslate
 *     kerb that is itself sometimes cracked.</li>
 * <li><b>Crossings</b>: where an avenue (or a ramp) opens onto a street the kerb breaks, the avenue's
 *     tile flows across the street and a <i>rosette</i> of terracotta marks the middle (pink disc, white
 *     ring with red spokes, bamboo rim; {@link #rosette}). {@link CitadelStreetscape} stands a
 *     landmark (a lantern post) on the very middle. The foot of the main (south) ramp opens wider, as a
 *     plaza.</li>
 * <li><b>Paths</b> of the patio and the garden ({@link #causeway}, {@link #patioRing}): bamboo
 *     runners and the paved ring of the patio.</li>
 * </ol>
 *
 * Kerbs are flat (a walker never steps on one). Every function returns blocks of the vanilla set or
 * of the kit ({@code c.floor()}, {@code c.accent()}).
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

	// Sizes: all proportions or mirrors of the geometry.

	/** Half width of an avenue zone: mirror of {@code RingCampusGeometry.AVENUE_HALF} (package-private there). */
	static final double AVENUE_HALF = 3.4;
	/** Half width of the bridges' deck: mirror of the {@code |x| <= 3} of {@code RingCampusGeometry.zone}. */
	static final int BRIDGE_HALF = 3;
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
	/** A crossing's rosette: radius of the heart, of the terracotta disc, of the calcite ring and of the polished rim, as fractions of the street's width. */
	static final double HEART_FRAC = 0.1;
	static final double DISC_FRAC = 0.28;
	static final double RING_FRAC = 0.4;
	static final double RIM_FRAC = 0.5;
	/** The plaza at the foot of the main ramp: half width, in avenue half widths. */
	static final double PLAZA_FRAC = 2.35;
	/** Half width of a causeway over the patio's pool (the pool's paths): 1.2 unscaled. */
	static final double CAUSEWAY_D = AVENUE_HALF * 0.35;

	/** Perpendicular distance from the axis within which an avenue's field is paved (its kerb is beyond). */
	static final double KERB_D = AVENUE_HALF - Math.max(KERB_MIN, KERB_FRAC * 2 * AVENUE_HALF);
	/** Half width of an avenue's runner. */
	static final double RUNNER_D = AVENUE_HALF * RUNNER_FRAC;
	/** Width of an avenue's cenefa. */
	static final double CENEFA_W = EDGE_FRAC * 2 * AVENUE_HALF;

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

	/** Whether a block is a kerb of the roads (so that objects standing on kerbs can find them). */
	static boolean isKerb(BlockState s) {
		return s == FOOT || s == KERB_CRACKED;
	}

	/** A kerb block: deepslate tiles, one in nine cracked. */
	private static BlockState kerb(int x, int z) {
		return hash(x, z) % 9 == 0 ? KERB_CRACKED : FOOT;
	}

	// ------------------------------------------------------------------ avenues and ramps

	/** An avenue column of ring {@code zn.ring()}. */
	static BlockState avenue(CitadelCanvas c, int x, int z, Zone zn) {
		int n = RingCampusGeometry.lotsIn(zn.ring());
		double d = RingCampusGeometry.radialDistance(x, z, n);
		if (d > KERB_D) {
			return kerb(x, z);
		}
		double s = along(x, z, n);
		long gate = (long) RingCampusGeometry.ringInner(zn.ring()) + CitadelStreetscape.GATE_IN;
		if (d > RUNNER_D && Math.abs(Math.round(s) - gate) == 1) {
			return RIB_BAND; // the threshold on both sides of the gate
		}
		return avenueField(c, x, z, d, s);
	}

	/** The paved part of an avenue (or of its mouth into a street): runner, tile, joints and the cenefa. */
	private static BlockState avenueField(CitadelCanvas c, int x, int z, double d, double s) {
		if (d <= RUNNER_D) {
			return c.floor();
		}
		if (d > KERB_D - CENEFA_W) {
			int h = hash(x, z);
			if (Math.floorMod((int) Math.round(s), 2) == 0) {
				return CALCITE;
			}
			return h < 14 ? MOSSY : h < 22 ? CRACKED : BRICKS;
		}
		return joint(s, 2 * AVENUE_HALF) ? JOINT : c.accent();
	}

	/** Kind of a ramp column: 0 runner, 1 bamboo core, 2 smooth-stone side, 3 kerb. */
	static int rampBand(int x, int z) {
		double d = RingCampusGeometry.radialDistance(x, z, 8);
		return d <= RUNNER_D ? 0 : d <= AVENUE_HALF * 0.5 ? 1 : d <= KERB_D ? 2 : 3;
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
		double lo = inner ? RingCampusGeometry.RAMP_R : RingCampusGeometry.ringOuter(m);
		double hi = inner ? RingCampusGeometry.ringInner(1) : RingCampusGeometry.streetOuter(m);
		double rc = (lo + hi) / 2;
		double width = hi - lo;
		double kerbT = Math.max(KERB_MIN, KERB_FRAC * width);
		int nIn = inner ? 8 : RingCampusGeometry.lotsIn(m);
		int nOut = inner ? 8 : m < c.rings ? RingCampusGeometry.lotsIn(m + 1) : 0;
		double dIn = RingCampusGeometry.radialDistance(x, z, nIn);
		double dOut = nOut == 0 ? 99 : RingCampusGeometry.radialDistance(x, z, nOut);
		boolean plaza = inner && z > 0 && Math.abs(x) <= PLAZA_FRAC * AVENUE_HALF;
		boolean mouthIn = dIn <= KERB_D || plaza;
		boolean mouthOut = dOut <= KERB_D;
		// rosette at the crossing of the street's middle line and the avenue's axis
		if (mouthIn || mouthOut) {
			double md = 99;
			double dx = 0;
			double dz = 0;
			if (plaza) {
				md = Math.hypot(x, z - rc);
				dx = x;
				dz = z - rc;
			} else if (dIn <= KERB_D) {
				double ang = radialAngle(x, z, nIn);
				dx = x - rc * Math.sin(ang);
				dz = z + rc * Math.cos(ang);
				md = Math.hypot(dx, dz);
			}
			if (dOut <= KERB_D) {
				double ang = radialAngle(x, z, nOut);
				double ox = x - rc * Math.sin(ang);
				double oz = z + rc * Math.cos(ang);
				if (Math.hypot(ox, oz) < md) {
					md = Math.hypot(ox, oz);
					dx = ox;
					dz = oz;
				}
			}
			BlockState ros = rosette(md, dx, dz, width);
			if (ros != null) {
				return ros;
			}
		}
		boolean kerb = (r - lo <= kerbT && !mouthIn) || (hi - r < kerbT && !mouthOut);
		if (kerb) {
			return kerb(x, z);
		}
		if (mouthIn || mouthOut) {
			// the avenue flows across the street
			double d = mouthOut && dOut <= dIn ? dOut : dIn;
			int n = mouthOut && dOut <= dIn ? nOut : nIn;
			return plaza ? plazaField(c, x, z, rc, width) : avenueField(c, x, z, d, along(x, z, n));
		}
		return m >= 2 ? warmField(x, z, r, lo, hi, kerbT) : streetField(x, z, r, lo, hi, kerbT);
	}

	/**
	 * The rosette of a crossing, {@code md} from its centre ({@code dx}, {@code dz} the offsets): a
	 * chiselled-brick heart (the landmark's plinth), a pinwheel of pink and magenta terracotta, a ring
	 * of calcite and a rim of polished deepslate; {@code null} outside it.
	 */
	static BlockState rosette(double md, double dx, double dz, double width) {
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
		return null;
	}

	/** The plaza at the foot of the main ramp: the avenue's tile, with arcs of joints round the rosette. */
	private static BlockState plazaField(CitadelCanvas c, int x, int z, double rc, double width) {
		double md = Math.hypot(x, z - rc);
		if (Math.abs(x) <= RUNNER_D) {
			return c.floor();
		}
		return Math.floorMod((int) Math.round(md), Math.max(2, jointEvery(width) / 2)) == 0 ? JOINT : c.accent();
	}

	/**
	 * The paved field of a street, a gradient across its width: polished in the middle, stone bricks
	 * (with a polished joint every so often along the arc), and a worn lane against each kerb (mossy
	 * and cracked bricks, a chiselled brick every third cell).
	 */
	private static BlockState streetField(int x, int z, double r, double lo, double hi, double kerbT) {
		double width = hi - lo;
		double edge = Math.min(r - lo, hi - r) - kerbT;
		double rc = (lo + hi) / 2;
		double arc = RingCampusGeometry.theta(x, z) * rc;
		int h = hash(x, z);
		if (edge < EDGE_FRAC * width) {
			if (Math.floorMod((int) Math.round(arc), 3) == 0) {
				return CHISELED;
			}
			return h < 34 ? MOSSY : h < 52 ? CRACKED : BRICKS;
		}
		if (edge >= CENTRE_FRAC * width - kerbT) {
			return joint(arc, width) ? BRICKS : JOINT;
		}
		if (joint(arc, width)) {
			return JOINT;
		}
		return h < 6 ? MOSSY : h < 10 ? CRACKED : BRICKS;
	}

	/**
	 * The field of a neighbourhood street (the streets of ring 2 and 3, warmer and humbler than the
	 * grey ring-1 street): mud bricks, a packed-mud lane against each kerb and a joint every so often.
	 */
	private static BlockState warmField(int x, int z, double r, double lo, double hi, double kerbT) {
		double width = hi - lo;
		double edge = Math.min(r - lo, hi - r) - kerbT;
		double arc = RingCampusGeometry.theta(x, z) * (lo + hi) / 2;
		int h = hash(x, z);
		if (edge < EDGE_FRAC * width) {
			return h < 30 ? MOSSY : PACKED_MUD;
		}
		if (joint(arc, width)) {
			return PACKED_MUD;
		}
		return h < 8 ? CRACKED : MUD;
	}

	// ------------------------------------------------------------------ verges

	/** Width of the verge of a lot against a road: 0.45 of an avenue half width (1.5 unscaled). */
	static final double VERGE_W = AVENUE_HALF * 0.45;

	/**
	 * The ground of a lot cell: grass, with a verge of moss against the roads (its avenues, the street
	 * inside and the street outside) so that the pavement never meets the lawn in a clean cut: mostly
	 * moss against the kerb, thinning out over {@link #VERGE_W}.
	 */
	static BlockState lotGround(CitadelCanvas c, int x, int z, Zone zn) {
		int m = zn.ring();
		double r = RingCampusGeometry.r(x, z);
		double d = RingCampusGeometry.radialDistance(x, z, RingCampusGeometry.lotsIn(m));
		double edge = Math.min(d - AVENUE_HALF,
				Math.min(r - RingCampusGeometry.ringInner(m), RingCampusGeometry.ringOuter(m) - r));
		if (edge >= VERGE_W) {
			return CitadelCanvas.GRASS;
		}
		int h = hash(x, z);
		int moss = edge < VERGE_W / 2 ? 70 : 35;
		return h < moss ? CitadelCanvas.MOSS : h < moss + 12 ? b(Blocks.COARSE_DIRT) : CitadelCanvas.GRASS;
	}

	/**
	 * What lies on the verge of a lot at {@code y = 1}, or {@code null}: a low stone-brick slab (one in
	 * three mossy) on the first row against the kerb, which turns the kerb into a step down to the
	 * lawn, and a flowering bush on the second. Never inside the barrio's own square.
	 */
	static BlockState vergeCover(CitadelCanvas c, int x, int z, Zone zn) {
		int m = zn.ring();
		double r = RingCampusGeometry.r(x, z);
		double d = RingCampusGeometry.radialDistance(x, z, RingCampusGeometry.lotsIn(m));
		double edge = Math.min(d - AVENUE_HALF,
				Math.min(r - RingCampusGeometry.ringInner(m), RingCampusGeometry.ringOuter(m) - r));
		if (edge >= VERGE_W) {
			return null;
		}
		RingCampusGeometry.Lot l = RingCampusGeometry.lot(zn.lot());
		if (Math.abs(l.a(x, z)) <= l.reach() && Math.abs(l.b(x, z)) <= l.reach()) {
			return null;
		}
		int h = hash(x, z);
		if (edge < VERGE_W / 2) {
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
		double w = RingCampusGeometry.PATIO_R - rIn;
		if (r <= rIn + 0.28 * w) {
			return FOOT;
		}
		if (RingCampusGeometry.radialDistance(x, z, 8) <= RUNNER_D) {
			return BAMBOO;
		}
		if (r > RingCampusGeometry.PATIO_R - 0.28 * w) {
			return c.floor();
		}
		return joint(RingCampusGeometry.theta(x, z) * (rIn + RingCampusGeometry.PATIO_R) / 2, w) ? JOINT : c.accent();
	}

	/** A causeway cell over the patio's pool: a bamboo runner between two deepslate edges. */
	static BlockState causeway(int x, int z) {
		return RingCampusGeometry.radialDistance(x, z, 8) <= RUNNER_D ? BAMBOO : FOOT;
	}

	/** The deck of a bridge: edge, tile, a runner down the axis and a joint every so often. */
	static BlockState bridgeDeck(CitadelCanvas c, int x, int z) {
		int ax = Math.abs(x);
		if (ax >= BRIDGE_HALF) {
			return FOOT;
		}
		if (ax == 0) {
			return c.floor();
		}
		return joint(z, 2 * BRIDGE_HALF) ? JOINT : c.accent();
	}

	// ------------------------------------------------------------------ promenade

	/** Width of the elevated ring. */
	static double promenadeWidth() {
		return RingCampusGeometry.GALLERY_R - RingCampusGeometry.PATIO_R;
	}

	/** Radius up to which the promenade is the inner edge (where the railing stands). */
	static double promenadeInner() {
		return RingCampusGeometry.PATIO_R + 0.2 * promenadeWidth();
	}

	/** Radius from which the promenade is the outer rim. */
	static double promenadeRim() {
		return RingCampusGeometry.GALLERY_R - promenadeWidth() / 6;
	}

	/**
	 * The deck of the elevated ring: a deepslate rim outside (open where a ramp leaves), a stripe of
	 * tile at each edge, the kit's floor between with a joint of tile every so often.
	 */
	static BlockState promenade(CitadelCanvas c, int x, int z, double r) {
		double w = promenadeWidth();
		boolean mouth = RingCampusGeometry.radialDistance(x, z, 8) <= KERB_D;
		if (r > promenadeRim()) {
			return mouth ? c.accent() : FOOT;
		}
		if (r > promenadeRim() - w / 6 || r <= promenadeInner()) {
			return c.accent();
		}
		return joint(RingCampusGeometry.theta(x, z) * (RingCampusGeometry.PATIO_R + RingCampusGeometry.GALLERY_R) / 2, w) ? c.accent() : c.floor();
	}
}
