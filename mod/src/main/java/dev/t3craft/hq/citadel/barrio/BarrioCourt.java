package dev.t3craft.hq.citadel.barrio;

import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelGrounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.WATER;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * Courtyards and garden slots of the barrios. Every position derives from the lot ({@code l.court()}, the
 * half side of the courtyard) or from the hall room, so the pieces follow the geometry when it grows.
 *
 * <h2>Courtyard</h2>
 *
 * The courtyard's middle is one of four variants picked by the lot's hash ({@link #variant}): a cherry
 * tree ringed by four benches, a pool with potted planters, a fountain (a lit pillar over the water
 * with four small ponds) or a gazebo over a well. The four quadrants (cells with {@code |a|, |b| >= 2},
 * clear of the cross of paths that the doors use and of the lot's walk anchor at (2, 2)) each take one of
 * three dressings by the lot and the quadrant: a pair of benches facing the path with a hedge behind,
 * a flower bed, or a lantern pillar with hedges. The four corners of the courtyard carry a lantern post.
 *
 * <h2>Garden slots</h2>
 *
 * An empty hall slot is one of three gardens by the slot's position: a tree with benches and flower
 * clumps, a pond with a tree, or two small trees.
 */
public final class BarrioCourt {
	private BarrioCourt() {
	}

	/** Cells from the centre that the middle's decorations (benches, ponds, gazebo posts) occupy. */
	private static final int CENTRE_R = 1;
	/** The quadrant dressings stay this far (cells) from the axis, clear of the paths. */
	private static final int AXIS_CLEAR = 2;
	/** Height of a lantern post's shaft. */
	private static final int POST_H = 2;

	/** The variant of the courtyard's centre of a lot: 0 tree, 1 pool, 2 fountain, 3 gazebo. */
	public static int variant(int lot) {
		return BarrioKit.hash(lot, 17, 5) % 4;
	}

	// ------------------------------------------------------------------ centre

	public static void centre(CitadelCanvas c, int lot, Lot l) {
		switch (variant(lot)) {
			case 0 -> tree(c, l);
			case 1 -> pool(c, l);
			case 2 -> fountain(c, l);
			default -> gazebo(c, l);
		}
		quadrants(c, lot, l);
		corners(c, l);
	}

	private static Direction alongA(Lot l, int s) {
		return s > 0 ? l.out() : l.out().getOpposite();
	}

	private static Direction alongB(Lot l, int s) {
		return s > 0 ? l.across() : l.across().getOpposite();
	}

	/** A cherry tree in the middle, a bench on each diagonal facing it. */
	public static void tree(CitadelCanvas c, Lot l) {
		BlockPos ctr = l.centre();
		CitadelGrounds.tree(c, ctr.getX(), ctr.getZ());
		int r = c.s(CENTRE_R) + (c.k > 1.5 ? 1 : 0);
		for (int sa = -1; sa <= 1; sa += 2) {
			for (int sb = -1; sb <= 1; sb += 2) {
				c.set(l.at(sa * r, sb * r, 1), stairs(Blocks.CHERRY_STAIRS, alongA(l, sa)));
			}
		}
	}

	/** A pool (one cell at scale 1, {@code 2s - 1} wide when scaled) with a planter on each diagonal. */
	public static void pool(CitadelCanvas c, Lot l) {
		int r = c.s(CENTRE_R);
		for (int a = -(r - 1); a <= r - 1; a++) {
			for (int bb = -(r - 1); bb <= r - 1; bb++) {
				BlockPos p = l.at(a, bb, 0);
				c.set(p.getX(), -1, p.getZ(), FOOT);
				c.set(p, WATER);
			}
		}
		for (int sa = -1; sa <= 1; sa += 2) {
			for (int sb = -1; sb <= 1; sb += 2) {
				c.set(l.at(sa * r, sb * r, 1), c.kit.gardenPlanter());
			}
		}
	}

	/** A lit pillar standing in the water, and a small pond on each diagonal. */
	public static void fountain(CitadelCanvas c, Lot l) {
		BlockPos ctr = l.centre();
		c.set(ctr.getX(), -1, ctr.getZ(), FOOT);
		c.set(ctr.getX(), 0, ctr.getZ(), WATER);
		int top = c.sy(2);
		for (int y = 1; y <= top; y++) {
			c.set(ctr.getX(), y, ctr.getZ(), b(Blocks.STONE_BRICK_WALL));
		}
		c.set(ctr.getX(), top + 1, ctr.getZ(), BarrioKit.lantern(false));
		int r = c.s(CENTRE_R);
		for (int a = -(r - 1); a <= r - 1; a++) {
			for (int bb = -(r - 1); bb <= r - 1; bb++) {
				BlockPos p = l.at(a, bb, 0);
				c.set(p.getX(), -1, p.getZ(), FOOT);
				c.set(p, WATER);
			}
		}
		for (int sa = -1; sa <= 1; sa += 2) {
			for (int sb = -1; sb <= 1; sb += 2) {
				BlockPos p = l.at(sa * r, sb * r, 0);
				c.set(p.getX(), -1, p.getZ(), FOOT);
				c.set(p, WATER);
			}
		}
	}

	/** A well under a small pergola: four posts, a ring of slabs, the lantern hanging over the water. */
	public static void gazebo(CitadelCanvas c, Lot l) {
		BlockPos ctr = l.centre();
		int r = c.s(CENTRE_R);
		int roof = c.sy(4);
		for (int a = -r; a <= r; a++) {
			for (int bb = -r; bb <= r; bb++) {
				boolean post = Math.abs(a) == r && Math.abs(bb) == r;
				if (Math.abs(a) < r && Math.abs(bb) < r) {
					BlockPos p = l.at(a, bb, 0);
					c.set(p.getX(), -1, p.getZ(), FOOT);
					c.set(p, WATER);
				}
				if (post) {
					for (int y = 1; y < roof; y++) {
						c.set(l.at(a, bb, y), b(Blocks.CHERRY_FENCE));
					}
				}
				c.set(l.at(a, bb, roof), BarrioKit.slabOf(Blocks.CHERRY_STAIRS));
			}
		}
		c.set(l.at(0, 0, roof - 1), BarrioKit.lantern(true));
	}

	// ------------------------------------------------------------------ quadrants and corners

	private static void quadrants(CitadelCanvas c, int lot, Lot l) {
		int g = l.court();
		for (int sa = -1; sa <= 1; sa += 2) {
			for (int sb = -1; sb <= 1; sb += 2) {
				int q = (sa > 0 ? 2 : 0) + (sb > 0 ? 1 : 0);
				switch (BarrioKit.hash(lot, q, 9) % 3) {
					case 0 -> benches(c, l, g, sa, sb);
					case 1 -> flowerBed(c, l, g, sa, sb);
					default -> lanternPillar(c, l, g, sa, sb);
				}
			}
		}
	}

	/** Two benches along the path edge, facing the path, with a hedge behind them. */
	private static void benches(CitadelCanvas c, Lot l, int g, int sa, int sb) {
		Direction back = alongB(l, sb);
		for (int i = 1; i <= 2; i++) {
			int a = sa * (g - i);
			c.set(l.at(a, sb * AXIS_CLEAR, 1), stairs(Blocks.CHERRY_STAIRS, back));
			c.set(l.at(a, sb * (AXIS_CLEAR + 1), 1), leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		}
	}

	/** A 2x2 bed of moss in the corner of the quadrant: hedges, and a potted sapling. */
	private static void flowerBed(CitadelCanvas c, Lot l, int g, int sa, int sb) {
		for (int i = 1; i <= 2; i++) {
			for (int j = 1; j <= 2; j++) {
				BlockPos p = l.at(sa * (g - i), sb * (g - j), 0);
				c.set(p, MOSS);
				c.set(p.above(), i == 1 && j == 2 ? c.kit.gardenPlanter() : leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			}
		}
	}

	/** A stone pillar with a lantern, hedges on two sides. */
	private static void lanternPillar(CitadelCanvas c, Lot l, int g, int sa, int sb) {
		int p = AXIS_CLEAR + 1;
		c.set(l.at(sa * p, sb * p, 1), b(Blocks.STONE_BRICK_WALL));
		c.set(l.at(sa * p, sb * p, 2), b(Blocks.STONE_BRICK_WALL));
		c.set(l.at(sa * p, sb * p, 3), BarrioKit.lantern(false));
		c.set(l.at(sa * (p + 1), sb * p, 1), leaves(Blocks.AZALEA_LEAVES));
		c.set(l.at(sa * p, sb * (p + 1), 1), leaves(Blocks.FLOWERING_AZALEA_LEAVES));
	}

	/** A lantern post on each corner of the courtyard. */
	private static void corners(CitadelCanvas c, Lot l) {
		int g = l.court();
		for (int sa = -1; sa <= 1; sa += 2) {
			for (int sb = -1; sb <= 1; sb += 2) {
				for (int y = 1; y <= POST_H; y++) {
					c.set(l.at(sa * g, sb * g, y), b(Blocks.CHERRY_FENCE));
				}
				c.set(l.at(sa * g, sb * g, POST_H + 1), c.kit.gardenLantern());
			}
		}
	}

	/** The azalea of the garden belt and the verge: flowering on two cells in three, plain on the third. */
	public static BlockState bush(int x, int z) {
		return leaves(BarrioKit.hash(x, 3, z) % 3 == 0 ? Blocks.AZALEA_LEAVES : Blocks.FLOWERING_AZALEA_LEAVES);
	}

	// ------------------------------------------------------------------ garden slots

	/** An empty hall slot: grass and moss, azalea at the corners, then a garden by the slot's position. */
	public static void slotGarden(CitadelCanvas c, Room r) {
		for (int u = -1; u <= r.d(); u++) {
			for (int v = -1; v <= r.w(); v++) {
				BlockPos p = r.cell(u, v, 0);
				c.set(p, (u + v) % 5 == 0 ? MOSS : GRASS);
				if (r.corner(u, v)) {
					c.set(p.above(), bush(p.getX(), p.getZ()));
				}
			}
		}
		int d = r.d();
		int mid = r.c();
		int variant = BarrioKit.hash(r.origin().getX(), r.origin().getZ(), 5) % 3;
		if (variant == 1 && !pondFree(r)) {
			variant = 0;
		}
		switch (variant) {
			case 0 -> orchard(c, r, d, mid);
			case 1 -> pond(c, r, d, mid);
			default -> twoTrees(c, r, d, mid);
		}
	}

	private static void tree(CitadelCanvas c, BlockPos t) {
		CitadelGrounds.tree(c, t.getX(), t.getZ());
	}

	/** A tree with a bench on each side facing it, and four flower clumps. */
	private static void orchard(CitadelCanvas c, Room r, int d, int mid) {
		tree(c, r.cell(d / 2, mid, 0));
		c.set(r.cell(d / 2, mid - 2, 1), stairs(Blocks.CHERRY_STAIRS, r.side().getOpposite()));
		c.set(r.cell(d / 2, mid + 2, 1), stairs(Blocks.CHERRY_STAIRS, r.side()));
		int[] us = {2, d - 3};
		int[] vs = {1, r.w() - 2};
		for (int u : us) {
			for (int v : vs) {
				BlockPos p = r.cell(u, v, 1);
				c.set(p, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			}
		}
	}

	/** Always true since citadel/light-aesthetic: no lattice lights in the ground any more to keep the pond off. */
	private static boolean pondFree(Room r) {
		return true;
	}

	/** A 3x3 pond with a tree beside it. */
	private static void pond(CitadelCanvas c, Room r, int d, int mid) {
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				BlockPos p = r.cell(d / 2 + du, mid + dv, 0);
				c.set(p.getX(), -1, p.getZ(), FOOT);
				c.set(p, WATER);
			}
		}
		tree(c, r.cell(d / 2, mid + 3, 0));
	}

	/** Two small trees offset along the slot, with a hedge between. */
	private static void twoTrees(CitadelCanvas c, Room r, int d, int mid) {
		tree(c, r.cell(d / 3, mid - 2, 0));
		tree(c, r.cell(2 * d / 3, mid + 2, 0));
		for (int v = mid - 1; v <= mid + 1; v++) {
			BlockPos p = r.cell(d / 2, v, 0);
			c.set(p.above(), bush(p.getX(), p.getZ()));
		}
	}
}
