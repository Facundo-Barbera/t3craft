package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.WATER;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
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
 *
 * <p>At the default scale (node half 14 or more, {@link #grand}) none of this is built: {@link #build} paints the grand
 * cloister in real offsets (see the section at the end of this class) and this design is the fallback for small scales.
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
		if (grand(c.k)) {
			// the grand cloister is painted in real offsets by build(), after the magnification; here only the ground
			c.set(x, 0, z, c.floor());
			return;
		}
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

	// ================================================================== the grand cloister (scale >= 2)
	//
	// At the default scale (k = 2) the node is a 29 x 29 square (offsets -14..14). The grand cloister is painted in REAL
	// offsets (no magnification), after the node's cells: a wipe of the columns, then the shell, then the furniture. The
	// ground floor block is y = 0 and standing is y = 1. Rings are r = max(|dx|, |dz|); along a face, a is the offset
	// along it and aa = |a|.
	//
	//   r 13..14   outer wall: calcite 15 high on a deepslate plinth, stripped-cherry pilasters at aa = 3, 7, 11 (they stand
	//              out one block at r = 14), glass bays recessed behind them, a gate 5 wide and 4 high on each face (aa <= 2,
	//              gold keystone), corner towers 3 x 3 with a stair cap, and a bell gable (espadaña) over the north gate
	//   r 7..12    the gallery, a lean-to nave that falls toward the court (high at the wall, low at the arcade): the aisle is
	//              r 7..9 (3 wide, always clear); at the wall, bays of 3 between the pilasters: near the gates shelves 4 high
	//              under a lit ledge (aa 4..6), farther out reading desks in a window (aa 8..10); the terminal bay is the
	//              north one east of the gate (aa 4..6 of the north face, see ArchiveBay)
	//   r 6        the arcade: columns at aa = 2 and 6, arches 3 wide, a beam and corbels
	//   r 0..5     the garth: a fountain, a ring path and the cross paths, four lawns with a cherry tree and two benches each
	//
	// Heights: arcade columns 7, aisle ceiling 9 at the arcade rising to 14 at the wall; roof stairs from y = 9 (arcade) to
	// y = 15; wall coping y = 16; corner towers 21; gable 30.

	/** Half side of the grand cloister's footprint (the node's half at k = 2). */
	public static final int H = 14;
	/** Offsets (from the node's centre) of the archive terminal: the north shelf line, east of the gate. */
	public static final int TERMINAL_X = 5;
	public static final int TERMINAL_Z = -11;
	private static final int Y_TOP = 40;
	/** Rosy stripped cherry log: pilasters and columns. */
	public static final BlockState STRIP = b(Blocks.STRIPPED_CHERRY_LOG);

	/** Whether the node is big enough (half side 14 or more) for the grand cloister; below that the old one is built. */
	public static boolean grand(double k) {
		return Math.round(7 * k) >= H;
	}

	/** A painter in offsets from the node's centre, writing only inside the node. */
	private static final class Q {
		final CitadelCanvas c;
		final Node nd;
		final int cx;
		final int cz;

		Q(CitadelCanvas c, Node nd) {
			this.c = c;
			this.nd = nd;
			this.cx = nd.cx();
			this.cz = nd.cz();
		}

		boolean in(int dx, int dz) {
			Zone zn = c.zoneAt(cx + dx, cz + dz);
			return zn.kind() == Kind.NODE && zn.node() == nd.k();
		}

		void set(int dx, int y, int dz, BlockState s) {
			if (in(dx, dz)) {
				c.set(cx + dx, y, cz + dz, s);
			}
		}

		void put(int dx, int y, int dz, BlockState s, int layer) {
			if (in(dx, dz)) {
				c.put(cx + dx, y, cz + dz, s, layer);
			}
		}

		void fill(int dx, int y1, int y2, int dz, BlockState s) {
			for (int y = y1; y <= y2; y++) {
				set(dx, y, dz, s);
			}
		}

		/** Sets {@code s} only if the cell is still empty (a strip never replaces anything). */
		void putIfFree(int dx, int y, int dz, BlockState s, int layer) {
			BlockState now = c.at(cx + dx, y, cz + dz);
			if (now == null || now.isAir()) {
				put(dx, y, dz, s, layer);
			}
		}
	}

	/** Paints the whole grand cloister (see the section comment). */
	public static void build(CitadelCanvas c, Node nd) {
		Q q = new Q(c, nd);
		// wipe whatever the design pass and the legacy furniture left
		for (int dx = -H; dx <= H; dx++) {
			for (int dz = -H; dz <= H; dz++) {
				if (!q.in(dx, dz)) {
					continue;
				}
				for (int y = -2; y <= Y_TOP; y++) {
					long key = BlockPos.asLong(q.cx + dx, y, q.cz + dz);
					c.cells.remove(key);
					c.layers.remove(key);
					c.data.remove(key);
				}
			}
		}
		for (int dx = -H; dx <= H; dx++) {
			for (int dz = -H; dz <= H; dz++) {
				column(q, dx, dz);
			}
		}
		bays(q);
		garth(q);
	}

	private static boolean pilasterLine(int aa) {
		return aa == 3 || aa == 7 || aa == 11;
	}

	/** One column of the shell: garth floor, arcade, gallery, wall, outer layer, corner towers. */
	private static void column(Q q, int dx, int dz) {
		int adx = Math.abs(dx);
		int adz = Math.abs(dz);
		int r = Math.max(adx, adz);
		if (r <= 5) {
			garthFloor(q, dx, dz, r);
			return;
		}
		if (adx >= 12 && adz >= 12) {
			turret(q, dx, dz);
			return;
		}
		boolean ns = adz > adx;
		int a = ns ? dx : dz;
		int aa = Math.abs(a);
		if (r == 14) {
			outer(q, dx, dz, ns, aa);
		} else if (r == 13) {
			wall(q, dx, dz, ns, aa);
		} else {
			hall(q, dx, dz, r, ns, a, aa);
		}
	}

	// ------------------------------------------------------------------ garth

	/** A deepslate-brick path cell, one in six cracked. */
	private static BlockState pathMix(int x, int z) {
		return Math.floorMod(x * 3 + z * 7, 6) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
	}

	/**
	 * The garth's ground: a moat of water 5 x 5 round a solid 3 x 3 pedestal (the fountain), a rim of half slabs at r = 3
	 * (open on the axes, where the paths cross), the cross paths 3 wide, grass in the four lawns.
	 */
	private static void garthFloor(Q q, int dx, int dz, int r) {
		int mn = Math.min(Math.abs(dx), Math.abs(dz));
		int x = q.cx + dx;
		int z = q.cz + dz;
		if (r <= 2) {
			q.set(dx, -1, dz, FOOT);
			q.set(dx, 0, dz, r == 2 ? WATER : b(Blocks.DEEPSLATE_BRICKS));
		} else if (r == 3) {
			if (mn <= 1) {
				q.set(dx, 0, dz, pathMix(x, z));
			} else {
				q.set(dx, 0, dz, FOOT);
				q.set(dx, 1, dz, slab(Blocks.DEEPSLATE_BRICK_SLAB));
			}
		} else if (mn <= 1) {
			q.set(dx, 0, dz, pathMix(x, z));
		} else {
			q.set(dx, 0, dz, GRASS);
		}
	}

	/** The garth's furniture: the fountain's post, four cherry trees, two benches under each. */
	private static void garth(Q q) {
		// the fountain: a pedestal 3 x 3 (y 1..2) in the moat, a bowl on top (a ring of bricks round a cell of water), a
		// spout post and a lantern
		for (int ax = -1; ax <= 1; ax++) {
			for (int az = -1; az <= 1; az++) {
				q.set(ax, 1, az, b(Blocks.DEEPSLATE_BRICKS));
				q.set(ax, 2, az, ax == 0 && az == 0 ? WATER : b(Blocks.CHISELED_DEEPSLATE));
			}
		}
		q.fill(0, 3, 4, 0, b(Blocks.DEEPSLATE_BRICK_WALL));
		q.put(0, 5, 0, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false), DECOR);
		BlockState pink = leaves(Blocks.CHERRY_LEAVES);
		BlockState azalea = leaves(Blocks.FLOWERING_AZALEA_LEAVES);
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				int tx = 4 * sx;
				int tz = 4 * sz;
				if (sx == sz) {
					// a cherry tree in two opposite lawns
					q.fill(tx, 1, 4, tz, LOG);
					for (int ax = -2; ax <= 2; ax++) {
						for (int az = -2; az <= 2; az++) {
							int m = Math.abs(ax) + Math.abs(az);
							if (m <= 1 && m > 0) {
								q.set(tx + ax, 4, tz + az, pink);
							}
							if (m <= 2 && !(ax == 2 * sx && az == 0 && sz > 0)) {
								q.set(tx + ax, 5, tz + az, pink);
							}
							if (m <= 1) {
								q.set(tx + ax, 6, tz + az, pink);
							}
						}
					}
					q.set(tx, 7, tz, pink);
					q.set(tx - sx, 7, tz, pink);
				} else {
					// a flowering hedge bed in the other two
					for (int ax = 0; ax <= 1; ax++) {
						for (int az = 0; az <= 1; az++) {
							q.set(tx + ax * sx, 1, tz + az * sz, azalea);
						}
					}
				}
				// two benches facing the ring path: one along each axis side of the lawn
				q.set(4 * sx, 1, 2 * sz, stairs(Blocks.CHERRY_STAIRS, sx > 0 ? Direction.EAST : Direction.WEST));
				q.set(4 * sx, 1, 3 * sz, stairs(Blocks.CHERRY_STAIRS, sx > 0 ? Direction.EAST : Direction.WEST));
				q.set(2 * sx, 1, 4 * sz, stairs(Blocks.CHERRY_STAIRS, sz > 0 ? Direction.SOUTH : Direction.NORTH));
				q.set(3 * sx, 1, 4 * sz, stairs(Blocks.CHERRY_STAIRS, sz > 0 ? Direction.SOUTH : Direction.NORTH));
			}
		}
	}

	// ------------------------------------------------------------------ gallery and arcade

	private static boolean isColumn(int a) {
		int n = Math.abs(a);
		return n == 2 || n == 6;
	}

	/**
	 * The gallery (r 6..12) at one cell: floor (a dark ring at the arcade and under the desks, cherry boards in the aisle),
	 * the lean-to ceiling and roof stairs, the rafters over the pilaster lines, the pilasters at the shelf line, and the
	 * arcade at r = 6.
	 */
	private static void hall(Q q, int dx, int dz, int r, boolean ns, int a, int aa) {
		CitadelCanvas c = q.c;
		q.set(dx, 0, dz, r == 6 ? FOOT : (r <= 9 ? c.floor() : (r == 10 ? CALCITE : NodeDecor.footMix(q.cx + dx, 0, q.cz + dz))));
		Direction out = NodeDecor.outward(dx, dz);
		if (r >= 7) {
			q.set(dx, r + 2, dz, PLANKS);
		}
		// roof stairs, high side outward: cherry, with a deepslate-tile edge at the arcade and at the wall
		boolean trim = r == 6 || r == 12;
		q.set(dx, r + 3, dz, stairs(trim ? Blocks.DEEPSLATE_TILE_STAIRS : Blocks.CHERRY_STAIRS, out));
		if (r >= 7 && r <= 11 && pilasterLine(aa)) {
			// a rafter under the ceiling, along the line of each pilaster
			q.set(dx, r + 1, dz, LOG.setValue(BlockStateProperties.AXIS, ns ? Direction.Axis.Z : Direction.Axis.X));
		}
		if (r == 12 && pilasterLine(aa)) {
			q.fill(dx, 1, r + 1, dz, STRIP);
		}
		if (r == 6) {
			arcade(q, dx, dz, ns, a);
		}
	}

	/** The arcade at r = 6: columns at aa = 2 and 6, a beam over the arches, corbels at the foot of each arch. */
	private static void arcade(Q q, int dx, int dz, boolean ns, int a) {
		CitadelCanvas c = q.c;
		if (isColumn(a)) {
			q.set(dx, 1, dz, FOOT);
			q.fill(dx, 2, 6, dz, STRIP);
			q.set(dx, 7, dz, b(Blocks.CHISELED_STONE_BRICKS));
		} else {
			Direction plus = ns ? Direction.EAST : Direction.SOUTH;
			if (isColumn(a + 1)) {
				q.set(dx, 7, dz, NodeDecor.invStairs(Blocks.CHERRY_STAIRS, plus));
			} else if (isColumn(a - 1)) {
				q.set(dx, 7, dz, NodeDecor.invStairs(Blocks.CHERRY_STAIRS, plus.getOpposite()));
			}
		}
		q.set(dx, 8, dz, c.kit.beam(ns ? Direction.Axis.X : Direction.Axis.Z));
	}

	// ------------------------------------------------------------------ outer wall

	/** The wall plane (r = 13): plinth, calcite, pilasters, glass bays (low in the desk bays), gate, cornice and coping. */
	private static void wall(Q q, int dx, int dz, boolean ns, int aa) {
		int x = q.cx + dx;
		int z = q.cz + dz;
		q.set(dx, 0, dz, FOOT);
		boolean desk = aa >= 8 && aa <= 10;
		int winLo = desk ? 4 : 6;
		for (int y = 1; y <= 16; y++) {
			BlockState s;
			if (aa <= 2) {
				s = y <= 4 ? AIR : (y == 5 ? RIB : (y <= 14 ? CALCITE : (y == 15 ? RIB : slab(Blocks.STONE_BRICK_SLAB))));
			} else if (y <= 2) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y == 15) {
				s = RIB;
			} else if (y == 16) {
				s = slab(Blocks.STONE_BRICK_SLAB);
			} else if (pilasterLine(aa)) {
				s = STRIP;
			} else if (y >= winLo && y <= 12) {
				s = GLASS;
			} else {
				s = CALCITE;
			}
			q.set(dx, y, dz, s);
		}
		if (ns && dz < 0 && aa <= 3) {
			gable(q, dx, dz, aa);
		}
	}

	/** The outer layer (r = 14): the portal, the pilasters that stand out, the sills of the recessed bays, the eave. */
	private static void outer(Q q, int dx, int dz, boolean ns, int aa) {
		int x = q.cx + dx;
		int z = q.cz + dz;
		q.set(dx, 0, dz, FOOT);
		if (aa <= 2) {
			q.fill(dx, 1, 4, dz, AIR);
			q.set(dx, 5, dz, RIB);
			q.set(dx, 6, dz, RIB);
		} else {
			q.set(dx, 1, dz, NodeDecor.footMix(x, 1, z));
			q.set(dx, 2, dz, NodeDecor.footMix(x, 2, z));
			if (pilasterLine(aa)) {
				q.fill(dx, 3, 14, dz, STRIP);
			} else {
				// a sill under the recessed window and a cherry hood over it
				q.set(dx, aa >= 8 && aa <= 10 ? 3 : 5, dz, slab(Blocks.STONE_BRICK_SLAB));
				q.set(dx, 13, dz, NodeDecor.invStairs(Blocks.CHERRY_STAIRS, NodeDecor.inward(dx, dz)));
			}
		}
		q.set(dx, 15, dz, NodeDecor.invStairs(Blocks.CHERRY_STAIRS, NodeDecor.inward(dx, dz)));
		if (ns && dz < 0 && aa <= 3) {
			gable(q, dx, dz, aa);
		}
	}

	/** A corner tower, 3 x 3 (|dx|, |dz| in 12..14): calcite on a dark base, a stripped-cherry corner, slits, a stair cap, a gold finial. */
	private static void turret(Q q, int dx, int dz) {
		int sx = dx < 0 ? -1 : 1;
		int sz = dz < 0 ? -1 : 1;
		int du = dx - 13 * sx;
		int dv = dz - 13 * sz;
		int x = q.cx + dx;
		int z = q.cz + dz;
		boolean extreme = Math.abs(dx) == 14 && Math.abs(dz) == 14;
		boolean slit = Math.abs(dx) == 14 && Math.abs(dz) == 13 || Math.abs(dx) == 13 && Math.abs(dz) == 14;
		q.set(dx, 0, dz, FOOT);
		for (int y = 1; y <= 16; y++) {
			BlockState s;
			if (y <= 2) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y == 4 || y == 15) {
				s = RIB;
			} else if (extreme) {
				s = STRIP;
			} else if (slit && y >= 10 && y <= 12) {
				s = GLASS;
			} else {
				s = CALCITE;
			}
			q.set(dx, y, dz, s);
		}
		if (du == 0 && dv == 0) {
			q.set(dx, 17, dz, CALCITE);
			q.set(dx, 18, dz, CALCITE);
			q.set(dx, 19, dz, b(Blocks.GOLD_BLOCK));
			q.set(dx, 20, dz, NodeDecor.ROD);
			q.set(dx, 21, dz, NodeDecor.ROD);
		} else {
			Direction toCentre = du != 0 ? (du > 0 ? Direction.WEST : Direction.EAST) : (dv > 0 ? Direction.NORTH : Direction.SOUTH);
			q.set(dx, 17, dz, stairs(Blocks.CHERRY_STAIRS, toCentre));
		}
	}

	/**
	 * The bell gable over the north gate, 7 wide (aa <= 3) on both layers: it steps up to a single column 14 above the
	 * coping, with a window 3 wide and 3 high for the bell, cherry stairs on the shoulders, a slab and a rod on top.
	 */
	private static void gable(Q q, int dx, int dz, int aa) {
		Direction toCentre = dx > 0 ? Direction.WEST : Direction.EAST;
		int top = switch (aa) {
			case 3 -> 20;
			case 2 -> 26;
			case 1 -> 29;
			default -> 32;
		};
		for (int y = 16; y < top; y++) {
			BlockState s = CALCITE;
			if (aa <= 1 && y >= 21 && y <= 23) {
				s = AIR;
			} else if (aa <= 1 && y == 24) {
				s = RIB;
			}
			q.set(dx, y, dz, s);
		}
		if (aa >= 1) {
			q.set(dx, top, dz, stairs(Blocks.CHERRY_STAIRS, toCentre));
		} else {
			q.set(dx, top, dz, slab(Blocks.STONE_BRICK_SLAB));
			q.set(dx, top + 1, dz, NodeDecor.ROD);
			q.set(dx, top + 2, dz, NodeDecor.ROD);
			if (dz == -13) {
				// the bell stands on the ledge at y = 20, in the window
				q.set(dx, 21, dz, b(Blocks.BELL));
			}
		}
	}

	// ------------------------------------------------------------------ furniture

	/** The offset {@code (dx, dz)} of the cell at {@code a} along a side (0 north, 1 east, 2 south, 3 west), {@code r} out. */
	private static int[] pos(int side, int a, int r) {
		return switch (side) {
			case 0 -> new int[] {a, -r};
			case 1 -> new int[] {r, a};
			case 2 -> new int[] {a, r};
			default -> new int[] {-r, a};
		};
	}

	/** The direction from a side's wall into the room. */
	private static Direction into(int side) {
		return switch (side) {
			case 0 -> Direction.SOUTH;
			case 1 -> Direction.WEST;
			case 2 -> Direction.NORTH;
			default -> Direction.EAST;
		};
	}

	private static BlockState lectern(Direction d) {
		return NodeDecor.facing(Blocks.LECTERN, d).setValue(BlockStateProperties.HAS_BOOK, true);
	}

	private static BlockState chest(Direction d) {
		return NodeDecor.facing(Blocks.CHEST, d);
	}

	/** The kit's hanging paper lantern (a chochin), or the vanilla lantern when the blocks module is off. */
	private static BlockState chochin() {
		Identifier id = Identifier.fromNamespaceAndPath("t3craft", "paper_lantern_hanging");
		if (BuiltInRegistries.BLOCK.containsKey(id)) {
			BlockState s = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
			Property<?> tone = s.getBlock().getStateDefinition().getProperty("tone");
			if (tone != null) {
				return withTone(s, tone, "paper");
			}
			return s;
		}
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	}

	private static <T extends Comparable<T>> BlockState withTone(BlockState s, Property<T> p, String value) {
		return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
	}

	/**
	 * The bays of the wall, on the four sides and both halves of each: near the gate (aa 4..6) a shelf bay (the north one east
	 * of the gate is the terminal's, see {@link ArchiveBay#buildGrand}), farther out (aa 8..10) a reading desk in a window; a
	 * chest in each inner corner; a paper lantern hung in each gate.
	 */
	private static void bays(Q q) {
		for (int side = 0; side < 4; side++) {
			Direction in = into(side);
			for (int half : new int[] {-1, 1}) {
				if (!(side == 0 && half > 0)) {
					shelfBay(q, side, half, in);
					nook(q, side, half, in);
					spur(q, side, half);
				}
				deskBay(q, side, half, in);
			}
			// a chochin hung in the gate, under the lintel
			int[] g = pos(side, 0, 13);
			q.put(g[0], 4, g[1], chochin(), HANGING);
			// the plaque of the archive over the gate, on the outer face of the wall
			int[] pl = pos(side, 0, 14);
			q.put(pl[0], 7, pl[1], q.c.kit.plaque(in.getOpposite()), DECOR);
			long key = BlockPos.asLong(q.cx + pl[0], 7, q.cz + pl[1]);
			q.c.data.put(key, q.c.kit.plaqueData(DyeColor.ORANGE, "", "Archivo", "", ""));
			q.c.fixtures.add(key);
		}
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				q.set(11 * sx, 1, 11 * sz, chest(sx > 0 ? Direction.WEST : Direction.EAST));
			}
		}
	}

	/** Shelves 4 high against the wall (r = 12) with a lit strip and a ledge over them; in front, a consultation table. */
	private static void shelfBay(Q q, int side, int half, Direction in) {
		CitadelCanvas c = q.c;
		for (int i = 0; i < 3; i++) {
			int a = half * (4 + i);
			int[] w = pos(side, a, 12);
			q.set(w[0], 1, w[1], NodeDecor.facing(Blocks.CHISELED_BOOKSHELF, in));
			q.set(w[0], 2, w[1], b(Blocks.BOOKSHELF));
			q.set(w[0], 3, w[1], b(Blocks.BOOKSHELF));
			q.set(w[0], 4, w[1], NodeDecor.facing(Blocks.CHISELED_BOOKSHELF, in));
			q.putIfFree(w[0], 5, w[1], c.kit.glowStrip(in, true, false), DECOR);
			q.set(w[0], 6, w[1], slab(Blocks.CHERRY_SLAB));
			// the consultation table in front: a lectern in the middle, slabs either side, a chair at each end
			int[] f = pos(side, a, 11);
			q.set(f[0], 1, f[1], i == 1 ? lectern(in) : NodeDecor.topSlab(Blocks.CHERRY_SLAB));
			if (i != 1) {
				int[] ch = pos(side, a, 10);
				q.set(ch[0], 1, ch[1], stairs(Blocks.CHERRY_STAIRS, in));
			}
		}
	}

	/** A nook beside a gate (aa = 3, r = 10..11): a chest and a barrel for the keepers of the archive. */
	private static void nook(Q q, int side, int half, Direction in) {
		int[] a = pos(side, 3 * half, 11);
		int[] b2 = pos(side, 3 * half, 10);
		q.set(a[0], 1, a[1], chest(in));
		q.set(b2[0], 1, b2[1], b(Blocks.BARREL));
	}

	/** A shelf spur at the pilaster line between a shelf bay and a desk bay: 2 deep, 3 high, books on all sides. */
	private static void spur(Q q, int side, int half) {
		for (int r = 10; r <= 11; r++) {
			int[] w = pos(side, 7 * half, r);
			q.fill(w[0], 1, 3, w[1], b(Blocks.BOOKSHELF));
			q.set(w[0], 4, w[1], slab(Blocks.CHERRY_SLAB));
		}
	}

	/** A reading desk in a window: a table along the wall with a lectern, a lamp, and a chair on each side. */
	private static void deskBay(Q q, int side, int half, Direction in) {
		CitadelCanvas c = q.c;
		for (int i = 0; i < 3; i++) {
			int a = half * (8 + i);
			int[] w = pos(side, a, 12);
			q.set(w[0], 1, w[1], i == 1 ? lectern(in) : NodeDecor.topSlab(Blocks.CHERRY_SLAB));
			if (i == 2) {
				q.put(w[0], 2, w[1], c.kit.lantern(), DECOR);
			}
			if (i != 1) {
				int[] f = pos(side, a, 11);
				q.set(f[0], 1, f[1], stairs(Blocks.CHERRY_STAIRS, in));
			}
		}
	}
}
