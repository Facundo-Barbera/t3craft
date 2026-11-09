package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.WATER;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * The archive cloister: a square of 15 × 15 ({@code mx = max(|dx|, |dz|)}, {@code mn = min(...)}): white walls
 * with four gates, corner turrets and a bell gable over the north gate; inside, a gallery of arches and
 * columns round a garden court with a fountain.
 *
 * <ul>
 * <li><b>Court</b> (mx &le; 2): a pool in the middle with a stone-brick rim and a fountain post, grass with an
 *     accent path along each axis. The corners hold cherry trees ({@link NodeDecor}).</li>
 * <li><b>Gallery</b> (mx 3..6): an accent ring and floor, with an accent runner along each axis. On the court side
 *     (mx = 3) stand columns ({@code mn >= 2}: deepslate base, cherry log shaft, chiseled capital) with
 *     arches between them ({@code mn <= 1}: a log beam and chamfered upside-down stairs); the roof climbs
 *     toward the court: brick stairs, a plank ceiling under each ring so the rings touch, and a slab ridge round
 *     the top.</li>
 * <li><b>Walls</b> (mx = 7): white calcite on a deepslate base, log piers beside each gate and at the
 *     corners, clerestory glass at {@code mn 3..5}, a cherry cornice with a brick slab coping. The corner
 *     turrets (2 × 2 of calcite with a slab cap and a rod) and the bell gable over the north gate (stepped,
 *     with a bell window and a hanging lantern, a rod on top) give the silhouette.</li>
 * </ul>
 * The gates are 3 wide and 3 high; the terminal and its stand cell are fixtures of {@code CitadelNodes}.
 */
public final class CloisterRoom {
	/** Ring of the outer wall. */
	private static final int WALL = 7;
	/** Ring of the court columns, and the height of the lowest roof ring (the outermost). */
	private static final int COLUMNS = 3;
	private static final int ROOF_LOW = 5;
	/** Height of the wall's coping, where the turrets and the gable start. */
	private static final int COPING = 7;

	private CloisterRoom() {
	}

	/** Paints the column {@code (x, z)} at offset {@code (dx, dz)}. */
	public static void cell(CitadelCanvas c, int x, int z, int dx, int dz) {
		int mx = Math.max(Math.abs(dx), Math.abs(dz));
		int mn = Math.min(Math.abs(dx), Math.abs(dz));
		floor(c, x, z, mx, mn);
		if (mx == WALL) {
			wall(c, x, z, dx, dz, mn);
		} else if (mx >= COLUMNS) {
			gallery(c, x, z, dx, dz, mx, mn);
		}
	}

	/** The floor: pool, rim, court with paths, gallery rings, footing outside. */
	public static void floor(CitadelCanvas c, int x, int z, int mx, int mn) {
		if (mx == 0) {
			c.set(x, -1, z, FOOT);
			c.set(x, 0, z, WATER);
		} else if (mx == 1) {
			c.set(x, 0, z, FOOT);
			c.set(x, 1, z, slab(Blocks.STONE_BRICK_SLAB));
		} else if (mx == 2) {
			c.set(x, 0, z, mn == 0 ? c.accent() : GRASS);
		} else if (mx == COLUMNS) {
			c.set(x, 0, z, c.accent());
		} else if (mx < WALL) {
			c.set(x, 0, z, mn == 0 ? c.accent() : c.floor());
		} else {
			c.set(x, 0, z, FOOT);
		}
	}

	/** The outer wall, the gates, the turrets and the bell gable. */
	private static void wall(CitadelCanvas c, int x, int z, int dx, int dz, int mn) {
		boolean pier = mn == 2 || mn >= 6;
		for (int y = 1; y <= 5; y++) {
			BlockState s;
			if (pier) {
				s = LOG;
			} else if (y == 1) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y <= 3 && mn >= 3 && mn <= 5) {
				s = NodeDecor.stoneMix(x, y, z);
			} else if (y >= 4 && mn >= 3 && mn <= 5) {
				s = GLASS;
			} else {
				s = CALCITE;
			}
			c.set(x, y, z, s);
		}
		c.set(x, 6, z, PLANKS);
		c.set(x, COPING, z, slab(Blocks.BRICK_SLAB));
		if (mn <= 1) {
			c.fill(x, 1, 3, z, AIR);
		}
		if (mn >= 6) {
			turret(c, x, z, dx, dz);
		}
		if (dz == -WALL && Math.abs(dx) <= 2) {
			gable(c, x, z, dx);
		}
	}

	/**
	 * A corner turret (2 × 2 cells): calcite two high under a small gable roof of stairs (the two columns face each
	 * other, the ridge runs along z), a rod on the outermost cell.
	 */
	private static void turret(CitadelCanvas c, int x, int z, int dx, int dz) {
		c.fill(x, COPING + 1, COPING + 2, z, CALCITE);
		boolean outer = Math.abs(dx) == WALL;
		Direction toRidge = (dx > 0) == outer ? Direction.WEST : Direction.EAST;
		c.set(x, COPING + 3, z, stairs(Blocks.BRICK_STAIRS, toRidge));
		if (outer && Math.abs(dz) == WALL) {
			c.set(x, COPING + 4, z, NodeDecor.ROD);
		}
	}

	/**
	 * The bell gable (espadaña) over the north gate: 5 wide at the foot, stepping up to a single column 8 above
	 * the coping, a window 2 high for the bell with a hanging lantern, brick stairs on the shoulders, a slab and a
	 * rod on top.
	 */
	private static void gable(CitadelCanvas c, int x, int z, int dx) {
		int ax = Math.abs(dx);
		Direction toCentre = dx > 0 ? Direction.WEST : Direction.EAST;
		if (ax == 2) {
			c.fill(x, COPING + 1, COPING + 3, z, CALCITE);
			c.set(x, COPING + 4, z, stairs(Blocks.BRICK_STAIRS, toCentre));
		} else if (ax == 1) {
			c.fill(x, COPING + 1, COPING + 5, z, CALCITE);
			c.set(x, COPING + 6, z, stairs(Blocks.BRICK_STAIRS, toCentre));
		} else {
			c.fill(x, COPING + 1, COPING + 2, z, CALCITE);
			c.put(x, COPING + 4, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), HANGING);
			c.fill(x, COPING + 5, COPING + 6, z, CALCITE);
			c.set(x, COPING + 7, z, slab(Blocks.BRICK_SLAB));
			c.set(x, COPING + 8, z, NodeDecor.ROD);
		}
	}

	/** The gallery: columns, arches, the climbing roof. */
	private static void gallery(CitadelCanvas c, int x, int z, int dx, int dz, int mx, int mn) {
		int roofY = ROOF_LOW + (WALL - 1 - mx);
		if (mx == COLUMNS) {
			if (mn >= 2) {
				c.set(x, 1, z, FOOT);
				c.fill(x, 2, roofY - 2, z, LOG);
				c.set(x, roofY - 1, z, b(Blocks.CHISELED_STONE_BRICKS));
			} else {
				boolean eastWest = Math.abs(dx) == COLUMNS;
				c.set(x, 5, z, c.kit.beam(eastWest ? Direction.Axis.Z : Direction.Axis.X));
				c.set(x, roofY - 1, z, PLANKS);
				int o = eastWest ? dz : dx;
				if (Math.abs(o) == 1) {
					Direction toPost = eastWest ? (o > 0 ? Direction.SOUTH : Direction.NORTH) : (o > 0 ? Direction.EAST : Direction.WEST);
					c.set(x, 4, z, NodeDecor.invStairs(Blocks.CHERRY_STAIRS, toPost));
				}
			}
			c.set(x, roofY + 1, z, slab(Blocks.BRICK_SLAB));
		} else if (mx < WALL - 1) {
			c.set(x, roofY - 1, z, PLANKS);
		}
		// tile bands: cherry on the odd rings, brick on the even ones
		c.set(x, roofY, z, stairs(mx % 2 == 1 ? Blocks.CHERRY_STAIRS : Blocks.BRICK_STAIRS, NodeDecor.inward(dx, dz)));
		if (mx == WALL - 1 && mn == WALL - 1) {
			// the inner cell of a corner turret
			c.fill(x, ROOF_LOW + 1, COPING + 2, z, CALCITE);
			turretCap(c, x, z, dx);
		}
	}

	/** The roof of the inner cell of a turret: it belongs to the 2 × 2 gable of {@link #turret}. */
	private static void turretCap(CitadelCanvas c, int x, int z, int dx) {
		c.set(x, COPING + 3, z, stairs(Blocks.BRICK_STAIRS, dx > 0 ? Direction.EAST : Direction.WEST));
	}
}
