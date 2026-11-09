package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What stands on the roads of the citadel: the street furniture over the paving of
 * {@link CitadelPaving}. Like it, pure and deterministic, called column by column from
 * {@link CitadelGrounds#ground} right after the paving of that column is set (so it can read the
 * kerb it stands on).
 *
 * <h2>Pieces</h2>
 *
 * <ul>
 * <li><b>Boulevard</b> ({@link #avenue}): along both outer kerbs of every avenue a row of cherry trees
 *     in coarse-dirt pits, with a lantern post between two trees and a cherry bench (stairs, back to
 *     the lots) on alternating sides. Trunks and posts stand on the kerb, so the walking lane keeps
 *     its width; canopies start at y = 4. The trees face each other across the avenue.</li>
 * <li><b>Gate</b> ({@link #gate}): where an avenue enters its ring, a torii of cherry logs: two piers
 *     on the kerbs (blackstone-brick foot, logs to y = 5), a tie beam of planks across at y = 4 with a
 *     hanging lantern in the middle (3 clear above the floor) and a deepslate lintel with a slab cap at
 *     y = 6 / 7. On an avenue that does not run along an axis it is a pair of lantern posts.</li>
 * <li><b>Street rows</b> ({@link #street}): on the kerbs of the inner street and the ring streets, a
 *     lantern post on both rows, a bench on the inner one and a tree on the outer one, every so many
 *     cells of arc; none near an avenue mouth or a node.</li>
 * <li><b>Landmark</b> ({@link #landmark}): on the middle of every crossing of a ring street, a chiselled
 *     brick plinth with a wall post and a lantern (4 high), in the middle of the terracotta rosette.
 *     The plaza at the foot of the main ramp gets two lantern posts on each side.</li>
 * </ul>
 *
 * Nothing stands on a cell of a node or a lot: canopies skip those cells. Light posts and lanterns are
 * decoration (the working light is the light pass's); all spacings are fractions of the road's width.
 */
final class CitadelStreetscape {
	static final BlockState PLINTH = CitadelPaving.CHISELED;
	static final BlockState POST = b(Blocks.STONE_BRICK_WALL);
	static final BlockState LANTERN = b(Blocks.LANTERN);
	static final BlockState HANGING_LANTERN = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
	static final BlockState PIT = b(Blocks.ROOTED_DIRT);
	static final BlockState PIER_FOOT = RIB;

	/** Cells between two trees of a boulevard: a bit over one avenue width (9 unscaled). */
	static final int TREE_EVERY = (int) Math.round(2 * CitadelPaving.AVENUE_HALF * 1.3);
	/** Where the gate stands: this many cells inside the ring (an avenue half width). */
	static final int GATE_IN = (int) Math.round(CitadelPaving.AVENUE_HALF);
	/** The boulevard stops this many cells short of the street (the rosette's room). */
	static final int END_ROOM = 4;
	/** Cells of arc between two trees of a street: this many widths of the street. */
	static final double STREET_EVERY_F = 3.0;
	/** Posts and trees keep this many cells clear of an avenue mouth, beyond its half width. */
	static final double MOUTH_ROOM = 1.5;
	/** Height of a gate's piers and of its tie beam over the floor. */
	static final int PIER_TOP = 5;
	static final int BEAM_Y = 4;
	static final int LINTEL_Y = 6;
	/** A tree of the boulevards: trunk height. */
	static final int TRUNK = 4;

	private CitadelStreetscape() {
	}

	// ------------------------------------------------------------------ pieces

	/** A lantern post: two wall blocks and a lantern. */
	static void lanternPost(CitadelCanvas c, int x, int z) {
		c.set(x, 1, z, POST);
		c.set(x, 2, z, POST);
		c.set(x, 3, z, LANTERN);
	}

	/** A cherry tree in a pit on the ground cell: a trunk and a canopy that skips lots and nodes. */
	static void tree(CitadelCanvas c, int x, int z) {
		c.set(x, 0, z, PIT);
		c.fill(x, 1, TRUNK, z, LOG);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int m = Math.abs(dx) + Math.abs(dz);
				if (m == 0 || m > 2) {
					continue;
				}
				Kind k = c.zoneAt(x + dx, z + dz).kind();
				if (k == Kind.LOT || k == Kind.NODE || k == Kind.NONE) {
					continue;
				}
				c.set(x + dx, TRUNK, z + dz, leaves(Blocks.CHERRY_LEAVES));
				if (m == 1) {
					c.set(x + dx, TRUNK + 1, z + dz, leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
		c.set(x, TRUNK + 1, z, leaves(Blocks.CHERRY_LEAVES));
		c.set(x, TRUNK + 2, z, leaves(Blocks.CHERRY_LEAVES));
	}

	/** A bench of one stair whose back is towards {@code back}. */
	static void bench(CitadelCanvas c, int x, int z, Direction back) {
		c.set(x, 1, z, stairs(Blocks.CHERRY_STAIRS, back));
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

	// ------------------------------------------------------------------ avenues

	/** The boulevard and the gate of one avenue column. */
	static void avenue(CitadelCanvas c, int x, int z, Zone zn) {
		int m = zn.ring();
		int n = RingCampusGeometry.lotsIn(m);
		double d = RingCampusGeometry.radialDistance(x, z, n);
		double ang = CitadelPaving.radialAngle(x, z, n);
		double s = x * Math.sin(ang) - z * Math.cos(ang);
		double l = x * Math.cos(ang) + z * Math.sin(ang);
		double ds = s - Math.rint(s);
		if (Math.abs(ds) > 0.355) {
			return; // one cell per row, also on the diagonals
		}
		long row = Math.round(s);
		long gate = (long) RingCampusGeometry.ringInner(m) + GATE_IN;
		if (row == gate) {
			gate(c, x, z, d, l, Math.abs(Math.sin(2 * ang)) < 1e-6);
			return;
		}
		if (d <= CitadelPaving.AVENUE_HALF - 1.0 || row < gate + 3 || row > RingCampusGeometry.ringOuter(m) - END_ROOM) {
			return;
		}
		int side = l >= 0 ? 1 : -1;
		int phase = (int) Math.floorMod(row - gate - 3, (long) TREE_EVERY);
		if (phase == 0) {
			tree(c, x, z);
		} else if (phase == TREE_EVERY / 2) {
			lanternPost(c, x, z);
		} else if (phase == (side > 0 ? TREE_EVERY / 4 : TREE_EVERY * 3 / 4)) {
			bench(c, x, z, nearest(side * Math.cos(ang), side * Math.sin(ang)));
		}
	}

	/** The torii of an avenue (or the two lantern posts of a diagonal one) on the gate row. */
	static void gate(CitadelCanvas c, int x, int z, double d, double l, boolean cardinal) {
		if (!cardinal) {
			if (d > CitadelPaving.AVENUE_HALF - 1.0) {
				lanternPost(c, x, z);
			}
			return;
		}
		int al = (int) Math.round(Math.abs(l));
		if (al == 3) {
			c.set(x, 1, z, PIER_FOOT);
			c.fill(x, 2, PIER_TOP, z, LOG);
		}
		if (al <= 2) {
			c.set(x, BEAM_Y, z, PLANKS);
		}
		if (al == 0) {
			c.set(x, BEAM_Y - 1, z, HANGING_LANTERN);
		}
		if (al <= 3) {
			c.set(x, LINTEL_Y, z, FOOT);
			c.set(x, LINTEL_Y + 1, z, CitadelPaving.KERB_SLAB);
		}
	}

	// ------------------------------------------------------------------ streets

	/** The furniture of one street column: posts, benches, trees and the crossing landmarks. */
	static void street(CitadelCanvas c, int x, int z, double r, Zone zn) {
		boolean inner = zn.kind() == Kind.INNER_STREET;
		int m = zn.ring();
		double lo = inner ? RingCampusGeometry.RAMP_R : RingCampusGeometry.ringOuter(m);
		double hi = inner ? RingCampusGeometry.ringInner(1) : RingCampusGeometry.streetOuter(m);
		double rc = (lo + hi) / 2;
		double width = hi - lo;
		int nIn = inner ? 8 : RingCampusGeometry.lotsIn(m);
		int nOut = inner ? 8 : m < c.rings ? RingCampusGeometry.lotsIn(m + 1) : 0;
		if (inner) {
			plazaPosts(c, x, z, lo, hi);
		} else {
			landmark(c, x, z, rc, nIn, nOut);
		}
		BlockState ground = c.at(x, 0, z);
		if (ground == null || !CitadelPaving.isKerb(ground)) {
			return;
		}
		double room = CitadelPaving.AVENUE_HALF + MOUTH_ROOM;
		if (RingCampusGeometry.radialDistance(x, z, nIn) <= room
				|| nOut > 0 && RingCampusGeometry.radialDistance(x, z, nOut) <= room) {
			return;
		}
		double arc = RingCampusGeometry.theta(x, z) * rc;
		if (Math.abs(arc - Math.rint(arc)) > 0.45) {
			return;
		}
		int every = Math.max(6, (int) Math.round(STREET_EVERY_F * width));
		int phase = (int) Math.floorMod(Math.round(arc), (long) every);
		boolean outer = r > rc;
		if (phase == 0 && !nearNode(c, x, z, 2)) {
			lanternPost(c, x, z);
		} else if (outer && phase == every / 2 && !nearNode(c, x, z, 3)) {
			tree(c, x, z);
		} else if (!outer && phase == every / 2 && !nearNode(c, x, z, 2)) {
			bench(c, x, z, nearest(x, z).getOpposite());
		}
	}

	/** The landmark on the middle of a crossing of a ring street with an avenue: plinth, post, lantern. */
	private static void landmark(CitadelCanvas c, int x, int z, double rc, int nIn, int nOut) {
		if (isCentre(x, z, rc, nIn) || nOut > 0 && isCentre(x, z, rc, nOut)) {
			c.set(x, 1, z, PLINTH);
			c.set(x, 2, z, POST);
			c.set(x, 3, z, POST);
			c.set(x, 4, z, LANTERN);
		}
	}

	private static boolean isCentre(int x, int z, double rc, int n) {
		double ang = CitadelPaving.radialAngle(x, z, n);
		return x == Math.round(rc * Math.sin(ang)) && z == Math.round(-rc * Math.cos(ang));
	}

	/** Two lantern posts on each side of the plaza at the foot of the main ramp, at the street's edges. */
	private static void plazaPosts(CitadelCanvas c, int x, int z, double lo, double hi) {
		int edge = (int) Math.round(CitadelPaving.PLAZA_FRAC * CitadelPaving.AVENUE_HALF);
		if (z > 0 && Math.abs(x) == edge && (z == Math.round(lo + 1) || z == Math.round(hi - 1))) {
			lanternPost(c, x, z);
		}
	}
}
