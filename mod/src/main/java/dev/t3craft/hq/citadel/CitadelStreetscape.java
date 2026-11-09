package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
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
 *     in rooted-dirt pits, facing each other across the avenue; between two trees a bench (a cherry
 *     stair, back to the lots) and two tall lantern posts (4 high), at a third of the spacing, so that
 *     the lanterns light the whole width. Trunks and posts stand on the kerb, so the walking lane keeps
 *     its width; canopies start at y = 4 and only over road cells.</li>
 * <li><b>Gate</b> ({@link #gate}): where an avenue enters its ring, a torii: two slender piers of
 *     stripped cherry log on chiselled plinths (y = 2..6) on the kerbs, a tie beam of cherry slabs at
 *     y = 4 reaching one cell beyond each pier, a centre strut, two hanging lanterns, a kasagi of
 *     deepslate-tile slabs at y = 7 whose ends curl up (stairs) one cell beyond the piers, and a crest at
 *     y = 8. The pavement has a band of blackstone brick on both sides of it ({@link CitadelPaving#avenue}).
 *     On an avenue that does not run along an axis: a pair of lantern posts.</li>
 * <li><b>Street rows</b> ({@link #street}): on the kerbs of the inner street and the ring streets, a
 *     lantern post every third of the spacing (the two rows offset), a bench on the inner row and a tree on
 *     the outer one; none near an avenue mouth or a node.</li>
 * <li><b>Landmark</b> ({@link #landmark}): on the middle of every crossing of a ring street, a stone
 *     lantern (chiselled plinth, wall post, lantern, 4 high) in the heart of the rosette.</li>
 * <li><b>Plaza</b> ({@link #plaza}): the plaza at the foot of the main ramp gets a stone lantern on each side of the rosette (the axis stays clear: the spawn is there), a bench facing it at each corner of the rosette, a garden planter on each side and two
 *     lantern posts at each end.</li>
 * </ul>
 *
 * Nothing stands on a cell of a node or a lot: canopies skip those cells. Lanterns are decoration that
 * also lights the roads; all spacings are fractions of the road's width.
 */
final class CitadelStreetscape {
	static final BlockState PLINTH = CitadelPaving.CHISELED;
	static final BlockState POST = b(Blocks.STONE_BRICK_WALL);
	static final BlockState LANTERN = b(Blocks.LANTERN);
	static final BlockState HANGING_LANTERN = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
	static final BlockState PIT = b(Blocks.ROOTED_DIRT);
	static final BlockState PIER = b(Blocks.STRIPPED_CHERRY_LOG);
	static final BlockState BEAM = slab(Blocks.CHERRY_SLAB);
	static final BlockState KASAGI = CitadelPaving.KERB_SLAB;

	/** Cells between two trees of a boulevard: a bit over one avenue width (9 unscaled). */
	static int TREE_EVERY() {
		return (int) Math.round(2 * CitadelPaving.AVENUE_HALF() * 1.3);
	}
	/** Where the gate stands: this many cells inside the ring (an avenue half width). */
	static int GATE_IN() {
		return (int) Math.round(CitadelPaving.AVENUE_HALF());
	}
	/** The boulevard stops this many cells short of the street (the rosette's room). */
	static int END_ROOM() {
		return CitadelCanvas.dims().s(6);
	}
	/** Cells of arc between two trees of a street: this many widths of the street. */
	static final double STREET_EVERY_F = 3.0;
	/** Trees and posts keep this many cells clear of an avenue mouth, beyond its half width. */
	static double MOUTH_ROOM() {
		return CitadelCanvas.dims().sd(1.5);
	}
	/** Height of a gate's pier top, tie beam, kasagi and crest over the floor. */
	static int PIER_TOP() {
		return CitadelCanvas.dims().sy(6);
	}
	static int BEAM_Y() {
		return CitadelCanvas.dims().sy(4);
	}
	static int KASAGI_Y() {
		return CitadelCanvas.dims().sy(7);
	}
	/** A tree of the boulevards: trunk height. */
	static int TRUNK() {
		return CitadelCanvas.dims().sy(4);
	}
	/** Height of a lantern post of a boulevard and of a street (wall blocks under the lantern). */
	static final int AVENUE_POST = 3;
	static final int STREET_POST = 2;

	private CitadelStreetscape() {
	}

	// ------------------------------------------------------------------ pieces

	/** A lantern post: {@code walls} wall blocks and a lantern. */
	static void lanternPost(CitadelCanvas c, int x, int z, int walls) {
		c.fill(x, 1, walls, z, POST);
		c.set(x, walls + 1, z, LANTERN);
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

	/** A stone lantern: chiselled plinth, wall post, lantern (4 high). */
	static void stoneLantern(CitadelCanvas c, int x, int z) {
		c.set(x, 1, z, PLINTH);
		c.set(x, 2, z, POST);
		c.set(x, 3, z, POST);
		c.set(x, 4, z, LANTERN);
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
		long gate = (long) RingCampusGeometry.ringInner(m) + GATE_IN();
		int side = l >= 0 ? 1 : -1;
		Direction out = nearest(side * Math.cos(ang), side * Math.sin(ang));
		if (row == gate) {
			gate(c, x, z, d, l, out, Math.abs(Math.sin(2 * ang)) < 1e-6);
			return;
		}
		if (d <= CitadelPaving.AVENUE_HALF() - 1.0 || row < gate + 3 || row > RingCampusGeometry.ringOuter(m) - END_ROOM()) {
			return;
		}
		int phase = (int) Math.floorMod(row - gate - 3, (long) TREE_EVERY());
		int lamp = TREE_EVERY() / 3;
		if (phase == 0) {
			tree(c, x, z);
		} else if (phase == lamp || phase == 2 * lamp) {
			lanternPost(c, x, z, AVENUE_POST);
		} else if (phase == (side > 0 ? 1 : TREE_EVERY() - 1)) {
			bench(c, x, z, out);
		}
	}

	/** The torii of an avenue (or the two lantern posts of a diagonal one) on the gate row. */
	static void gate(CitadelCanvas c, int x, int z, double d, double l, Direction out, boolean cardinal) {
		if (!cardinal) {
			if (d > CitadelPaving.AVENUE_HALF() - 1.0) {
				lanternPost(c, x, z, AVENUE_POST);
			}
			return;
		}
		int al = (int) Math.round(Math.abs(l));
		int ox = out.getStepX();
		int oz = out.getStepZ();
		int pier = c.s(3);
		if (al == pier) {
			c.set(x, 1, z, PLINTH);
			c.fill(x, 2, PIER_TOP(), z, PIER);
			// the tie beam and the kasagi reach one cell beyond the pier
			c.set(x + ox, BEAM_Y(), z + oz, BEAM);
			c.set(x + ox, KASAGI_Y(), z + oz, stairs(Blocks.DEEPSLATE_TILE_STAIRS, out));
		}
		if (al <= pier) {
			c.set(x, KASAGI_Y(), z, KASAGI);
		}
		if (al <= pier - 1) {
			c.set(x, BEAM_Y(), z, BEAM);
		}
		if (al == 0) {
			c.fill(x, BEAM_Y() + 1, KASAGI_Y() - 1, z, PLANKS);
			c.set(x, KASAGI_Y() + 1, z, KASAGI);
		}
		if (al == 1) {
			c.set(x, BEAM_Y() - 1, z, HANGING_LANTERN);
			c.set(x, KASAGI_Y() + 1, z, KASAGI);
		}
	}

	// ------------------------------------------------------------------ streets

	/** The furniture of one street column: posts, benches, trees and the crossing landmarks. */
	static void street(CitadelCanvas c, int x, int z, double r, Zone zn) {
		boolean inner = zn.kind() == Kind.INNER_STREET;
		int m = zn.ring();
		double lo = inner ? CitadelCanvas.dims().rampR : RingCampusGeometry.ringOuter(m);
		double hi = inner ? RingCampusGeometry.ringInner(1) : RingCampusGeometry.streetOuter(m);
		double rc = (lo + hi) / 2;
		double width = hi - lo;
		int nIn = inner ? 8 : RingCampusGeometry.lotsIn(m);
		int nOut = inner ? 8 : m < c.rings ? RingCampusGeometry.lotsIn(m + 1) : 0;
		if (inner) {
			plaza(c, x, z, lo, hi);
		} else {
			landmark(c, x, z, rc, nIn, nOut);
		}
		BlockState ground = c.at(x, 0, z);
		if (ground == null || !CitadelPaving.isKerb(ground)) {
			return;
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
		int every = Math.max(6, (int) Math.round(STREET_EVERY_F * width));
		int third = every / 3;
		int phase = (int) Math.floorMod(Math.round(arc), (long) every);
		boolean outer = r > rc;
		if (outer) {
			if (phase % third == 0 && !nearNode(c, x, z, 2)) {
				lanternPost(c, x, z, STREET_POST);
			} else if (phase == every / 2 && !nearNode(c, x, z, 4)
					&& RingCampusGeometry.radialDistance(x, z, nIn) > room + 2
					&& (nOut == 0 || RingCampusGeometry.radialDistance(x, z, nOut) > room + 2)) {
				tree(c, x, z);
			}
		} else {
			if ((phase + third / 2) % third == 0 && !nearNode(c, x, z, 2)) {
				lanternPost(c, x, z, STREET_POST);
			} else if (phase == 0 && !nearNode(c, x, z, 2)) {
				bench(c, x, z, nearest(x, z).getOpposite());
			}
		}
	}

	/** The landmark on the middle of a crossing of a ring street with an avenue. */
	private static void landmark(CitadelCanvas c, int x, int z, double rc, int nIn, int nOut) {
		if (isCentre(x, z, rc, nIn) || nOut > 0 && isCentre(x, z, rc, nOut)) {
			stoneLantern(c, x, z);
		}
	}

	private static boolean isCentre(int x, int z, double rc, int n) {
		double ang = CitadelPaving.radialAngle(x, z, n);
		return x == Math.round(rc * Math.sin(ang)) && z == Math.round(-rc * Math.cos(ang));
	}

	/**
	 * The plaza at the foot of the main ramp (south, {@code |x| <= PLAZA_FRAC * AVENUE_HALF}): a stone
	 * lantern in the middle of the rosette, a bench facing it on each side, garden planters further out
	 * and two lantern posts at each end.
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
			stoneLantern(c, x, z); // beside the rosette, never on the axis: the spawn stands there
		} else if (ax == edge && (z == Math.round(lo + 1) || z == Math.round(hi - 1))) {
			lanternPost(c, x, z, AVENUE_POST);
		} else if (ax == edge - 2 && (z == mid || z == mid - 1)) {
			c.set(x, 1, z, c.kit.gardenPlanter());
		} else if (ax == (int) Math.round(CitadelPaving.AVENUE_HALF()) + 1 && Math.abs(z - mid) == step) {
			bench(c, x, z, nearest(0, z - mid));
		}
	}
}
