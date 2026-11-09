package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * The review tower: a slender stepped tower of three bodies on a 13 × 13 footprint (the node's square),
 * about 39 high with its spire. Each body is narrower than the one below; the setback is a terrace with a
 * stone-brick railing, so the silhouette reads from far: a broad base with buttresses, a middle body, an
 * open belfry and a pyramid roof of stairs with a spire.
 *
 * <p>Rings are {@code r = max(|dx|, |dz|)} from the centre. {@code a} is the cell's coordinate along its face.
 * <ul>
 * <li><b>Base</b> (wall plane r = 5, y 1..11): deepslate base (a cracked one in six), calcite body, 2-wide
 *     glass bays at {@code |a| = 3, 4} recessed behind piers at r = 6 ({@code |a| = 2} beside each door, and
 *     corner buttresses), a lit panel band at y 10, a cherry plank terrace at y 11 with a stone-brick
 *     railing, and a cherry-slab cornice at r = 6. The doors (3 wide, 4 high) are on the four faces; each
 *     has a blackstone lintel at r = 6, y 5. The big screen sits flush in the north wall plane
 *     ({@code z = cz - 5}).</li>
 * <li><b>Middle body</b> (r = 4, y 12..22) on a mezzanine ring at y 11: tall slit windows, a lit band on
 *     top, corner logs; terrace with railing at y 23 (r = 4).</li>
 * <li><b>Belfry</b> (r = 3, y 23..31) on a ring at y 22: open arches 3 wide, a log beam across with a
 *     hanging lantern as the bell, a lit band at the top.</li>
 * <li><b>Roof</b>: stairs (cherry on the two outer rings, blackstone bricks inside), each ring above a
 *     full block so the rings touch; spire of log, a light and a rod.</li>
 * </ul>
 * The interior is a tall hall of 9 × 9 (r ≤ 4) open to the belfry; the furniture is {@link NodeDecor}'s.
 */
public final class TowerRoom {
	/** Radius of the wall plane of each body (base, middle, belfry). */
	private static final int R1 = 5;
	private static final int R2 = 4;
	private static final int R3 = 3;
	/** Cornice height of each body. */
	private static final int TOP1 = 11;
	private static final int TOP2 = 22;
	private static final int TOP3 = 31;

	private TowerRoom() {
	}

	/** Paints the column {@code (x, z)}, at offset {@code (dx, dz)} from the tower's centre. */
	public static void cell(CitadelCanvas c, int x, int z, int dx, int dz) {
		if (grand(c.k)) {
			// the grand tower is painted in real offsets by build(), after the magnification; here only the ground
			c.set(x, 0, z, c.floor());
			return;
		}
		int r = Math.max(Math.abs(dx), Math.abs(dz));
		int a = Math.abs(dx) >= Math.abs(dz) ? dz : dx;
		int aa = Math.abs(a);
		c.set(x, 0, z, r <= 2 ? c.accent() : c.floor());
		if (r == R1 + 1) {
			piers(c, x, z, dx, dz, aa);
		} else if (r == R1) {
			base(c, x, z, dx, dz, aa);
		} else if (r == R2) {
			middle(c, x, z, dx, dz, aa);
		} else if (r == R3) {
			belfry(c, x, z, dx, dz, aa);
		}
		roof(c, x, z, dx, dz, r);
		if (r <= 2 && dz == 0) {
			// the bell beam across the belfry
			c.set(x, TOP3 - 2, z, c.kit.beam(Direction.Axis.X));
		}
	}

	/** Base wall: dark footing, calcite, glass bays, lit band, terrace and railing. */
	private static void base(CitadelCanvas c, int x, int z, int dx, int dz, int aa) {
		boolean corner = Math.abs(dx) == R1 && Math.abs(dz) == R1;
		for (int y = 1; y <= TOP1; y++) {
			BlockState s;
			if (y == TOP1) {
				s = PLANKS;
			} else if (corner) {
				s = LOG;
			} else if (y <= 2) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y == TOP1 - 1) {
				s = c.kit.wallPanel(true);
			} else if (y >= 4 && y <= 9 && (aa == 3 || aa == 4)) {
				s = GLASS;
			} else {
				s = CALCITE;
			}
			c.set(x, y, z, s);
		}
		c.set(x, TOP1 + 1, z, b(Blocks.STONE_BRICK_WALL));
		if (corner) {
			c.put(x, TOP1 + 2, z, Blocks.LANTERN.defaultBlockState(), DECOR);
		}
		if (aa <= 1) {
			c.fill(x, 1, 4, z, AIR);
		}
	}

	/** Piers, buttresses, door lintels and the cornice, one ring outside the wall plane. */
	private static void piers(CitadelCanvas c, int x, int z, int dx, int dz, int aa) {
		boolean buttress = aa >= R1;
		if (buttress) {
			c.fill(x, 1, 2, z, FOOT);
			c.fill(x, 3, TOP1 + 1, z, LOG);
			if (aa == R1 + 1) {
				c.set(x, TOP1 + 2, z, b(Blocks.STONE_BRICK_WALL));
				c.set(x, TOP1 + 3, z, NodeDecor.ROD);
			}
			return;
		}
		if (aa == 2) {
			c.set(x, 1, z, FOOT);
			c.fill(x, 2, TOP1 - 1, z, LOG);
		} else if (aa <= 1) {
			c.set(x, 5, z, RIB);
		} else {
			// a plinth under each bay
			c.set(x, 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		c.set(x, TOP1, z, NodeDecor.topSlab(Blocks.CHERRY_SLAB));
	}

	/** Middle body: slit windows, corner logs, lit band, terrace railing. */
	private static void middle(CitadelCanvas c, int x, int z, int dx, int dz, int aa) {
		c.set(x, TOP1, z, FOOT);
		boolean corner = Math.abs(dx) == R2 && Math.abs(dz) == R2;
		for (int y = TOP1 + 1; y <= TOP2; y++) {
			BlockState s;
			if (corner) {
				s = LOG;
			} else if (y <= TOP1 + 2) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y == TOP2) {
				s = c.kit.wallPanel(true);
			} else if (y >= TOP1 + 4 && (aa & 1) == 1) {
				s = GLASS;
			} else {
				s = CALCITE;
			}
			c.set(x, y, z, s);
		}
		c.set(x, TOP2 + 1, z, b(Blocks.STONE_BRICK_WALL));
		if (corner) {
			c.put(x, TOP2 + 2, z, Blocks.LANTERN.defaultBlockState(), DECOR);
		}
	}

	/** The belfry: open arches, jambs, a lit band. */
	private static void belfry(CitadelCanvas c, int x, int z, int dx, int dz, int aa) {
		c.set(x, TOP2, z, FOOT);
		boolean corner = Math.abs(dx) == R3 && Math.abs(dz) == R3;
		for (int y = TOP2 + 1; y <= TOP3; y++) {
			BlockState s;
			if (corner) {
				s = LOG;
			} else if (y <= TOP2 + 2) {
				s = CALCITE;
			} else if (y == TOP3) {
				s = c.kit.wallPanel(true);
			} else if (y == TOP3 - 1) {
				s = RIB;
			} else if (aa <= 1) {
				s = y == TOP3 - 2 ? RIB : AIR;
			} else {
				s = CALCITE;
			}
			c.set(x, y, z, s);
		}
	}

	/**
	 * The roof: a ring of stairs per level (cherry on the eave and the first ring, blackstone bricks inside),
	 * each inner ring on a full block so the rings touch, then the spire.
	 */
	private static void roof(CitadelCanvas c, int x, int z, int dx, int dz, int r) {
		int eave = TOP3;
		if (r == R2) {
			c.set(x, eave, z, stairs(Blocks.CHERRY_STAIRS, NodeDecor.inward(dx, dz)));
		} else if (r == R3) {
			c.set(x, eave + 1, z, stairs(Blocks.CHERRY_STAIRS, NodeDecor.inward(dx, dz)));
		} else if (r == 2 || r == 1) {
			c.set(x, eave + 1 + (3 - r) - 1, z, RIB);
			c.set(x, eave + 1 + (3 - r), z, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, NodeDecor.inward(dx, dz)));
		} else if (r == 0) {
			c.set(x, eave + 3, z, RIB);
			c.set(x, eave + 4, z, RIB);
			c.set(x, eave + 5, z, LOG);
			c.set(x, eave + 6, z, LOG);
			c.set(x, eave + 7, z, LIGHT);
			c.set(x, eave + 8, z, NodeDecor.ROD);
		}
	}

	// ================================================================== the grand tower (scale >= 2)
	//
	// At the default scale (k = 2) the node is a 25 x 25 square (offsets -12..12). The grand tower is painted in
	// REAL offsets (no magnification), after the node's cells: a wipe of the columns, then the shell, the decks,
	// the stairs and the furniture. All heights are absolute (the ground floor block is y = 0, standing is y = 1).
	//
	//   level      deck block   stand   free      what
	//   L0 hall    0            1       1..11     review hall: the "review" screen (7 x 4, y 5..8) in the north wall, seats, review table
	//   L1 loft    12           13      13..22    reviewers' workroom, terrace on the base roof (doors north and south)
	//   L2 gallery 23           24      24..33    archive gallery
	//   L3 belfry  34           35      35..43    open belfry with the bell, balcony ring, then roof and spire (to y = 56)
	//
	// Stairs: three switchback flights, each 2 wide (L0 -> L1 in the south-west of the hall, L1 -> L2 in the east of the
	// loft, L2 -> L3 in the west of the gallery). The first step faces the room (head-on), the landing is 2 deep, the
	// deck above is cut wherever the headroom would be under 3 blocks, and the cut has a stone-brick rail.

	/** Half side of the grand tower's footprint (the node's half at k = 2). */
	public static final int H = 12;
	private static final int F1 = 12;
	private static final int F2 = 23;
	private static final int F3 = 34;
	private static final int CEIL = 44;
	private static final int Y_TOP = 70;
	/** Bottom row of the review screen (its cells are y = 6..9) and the wall column it sits in (north, r = 11). */
	public static final int SCREEN_Y = 5;
	public static final int SCREEN_Z = 11;

	private static final BlockState LANTERN_UP = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
	private static final BlockState LANTERN_DOWN = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	private static final BlockState RAIL = b(Blocks.STONE_BRICK_WALL);
	/** Rosy stripped cherry log: pilasters and posts (the plain log reads almost black from afar). */
	private static final BlockState STRIP = b(Blocks.STRIPPED_CHERRY_LOG);

	/** Whether the node is big enough (half side 12 or more) for the grand tower; below that the old tower is built. */
	public static boolean grand(double k) {
		return Math.round(6 * k) >= H;
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
	}

	/** Paints the whole grand tower (see the section comment). */
	public static void build(CitadelCanvas c, Node nd) {
		Q q = new Q(c, nd);
		// wipe whatever the design pass and the legacy furniture left above the ground
		for (int dx = -H; dx <= H; dx++) {
			for (int dz = -H; dz <= H; dz++) {
				if (!q.in(dx, dz)) {
					continue;
				}
				for (int y = 1; y <= Y_TOP; y++) {
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
		screenFrame(q);
		// the stairs and their cuts, then the rooms
		flight(q, 0, F1, -6, -4, 3, 1, 6);
		flight(q, F1, F2, 3, 5, 0, -1, 5);
		flight(q, F2, F3, -4, -2, 0, -1, 5);
		hall(q);
		loft(q);
		gallery(q);
		belfry(q);
	}

	/** One column of the shell: ground, base wall, decks, terrace, middle body, balcony, belfry, roof. */
	private static void column(Q q, int dx, int dz) {
		CitadelCanvas c = q.c;
		int adx = Math.abs(dx);
		int adz = Math.abs(dz);
		int r = Math.max(adx, adz);
		int a = adx >= adz ? dz : dx;
		int aa = Math.abs(a);
		boolean ns = adz > adx;
		// ground: the accent cross is the walk from door to door
		if (r >= 11 && aa > 2) {
			q.set(dx, 0, dz, FOOT);
		} else {
			q.set(dx, 0, dz, adx <= 2 || adz <= 2 ? c.accent() : c.floor());
		}
		if (r >= 11) {
			baseWall(q, dx, dz, r, aa);
		}
		// deck F1: the hall's ceiling, the loft's floor and the terrace
		if (r <= 12) {
			q.set(dx, F1, dz, r <= 6 ? c.floor() : PLANKS);
		}
		// terrace railing and the corner pylons that rise out of the base
		if (r == 12 || (r == 11 && aa >= 11)) {
			if (aa >= 11) {
				q.set(dx, F1, dz, RIB);
				q.set(dx, F1 + 1, dz, RIB);
				q.set(dx, F1 + 2, dz, RAIL);
				if (r == 12 && aa == 12) {
					q.put(dx, F1 + 3, dz, LANTERN_UP, DECOR);
				}
			} else {
				q.set(dx, F1 + 1, dz, RAIL);
			}
		}
		if (r == 7 || r == 8) {
			midWall(q, dx, dz, r, aa, ns);
		}
		if (r <= 6) {
			q.set(dx, F2, dz, c.floor());
		}
		// deck F3: the belfry's floor and the balcony ring
		if (r <= 8) {
			q.set(dx, F3, dz, r <= 3 ? c.floor() : PLANKS);
		}
		if (r == 8) {
			q.set(dx, F3 + 1, dz, RAIL);
			if (aa == 8) {
				q.put(dx, F3 + 2, dz, LANTERN_UP, DECOR);
			}
		}
		if (r == 4 || r == 5) {
			belfryWall(q, dx, dz, r, aa);
		}
		if (r <= 5) {
			q.set(dx, CEIL, dz, PLANKS);
		}
		roof(q, dx, dz, r);
	}

	/**
	 * Base wall (r = 11, 12; y 1..11): along a face, {@code aa} is the distance from the face's middle. Door |a| <= 2
	 * (5 wide, 4 high, lintel y = 5); log pilasters at 3..4 and 8..9; glass bays 5..7 recessed behind a dark plinth;
	 * dark pylons from 10 out; a lit band at y = 11.
	 */
	private static void baseWall(Q q, int dx, int dz, int r, int aa) {
		CitadelCanvas c = q.c;
		int x = q.cx + dx;
		int z = q.cz + dz;
		// the north door is a block lower: the review screen sits above it at eye height
		int top = dz < 0 && Math.abs(dz) > Math.abs(dx) ? 3 : 4;
		for (int y = 1; y <= 11; y++) {
			BlockState s;
			if (aa <= 2) {
				if (y <= top) {
					s = AIR;
				} else if (y == top + 1) {
					s = RIB;
				} else if (y == 11 && r == 11) {
					s = c.kit.wallPanel(true);
				} else {
					s = CALCITE;
				}
			} else if (aa >= 10) {
				s = y <= 2 ? NodeDecor.footMix(x, y, z) : (y <= 5 ? RIB : NodeDecor.stoneMix(x, y, z));
			} else if (y <= 2) {
				s = NodeDecor.footMix(x, y, z);
			} else if (aa <= 4 || aa >= 8) {
				s = r == 12 ? (y == 11 ? CALCITE : (y == 3 || y == 10 ? RIB : STRIP)) : (y == 11 ? c.kit.wallPanel(true) : CALCITE);
			} else if (r == 12) {
				s = AIR;
			} else if (y == 11) {
				s = c.kit.wallPanel(true);
			} else if (y >= 4 && y <= 9) {
				s = GLASS;
			} else {
				s = CALCITE;
			}
			q.set(dx, y, dz, s);
		}
		// a hanging lantern at each side of every door, under the lintel
		if (r == 12 && aa == 2) {
			q.put(dx, top, dz, LANTERN_DOWN, HANGING);
		}
	}

	/** The frame of the review screen: a dark sill, jambs and lintel in the north wall's inner layer. */
	private static void screenFrame(Q q) {
		for (int x = -4; x <= 4; x++) {
			q.set(x, SCREEN_Y - 1, -SCREEN_Z, RIB);
			q.set(x, SCREEN_Y + 4, -SCREEN_Z, b(Blocks.GOLD_BLOCK));
		}
		for (int y = SCREEN_Y; y <= SCREEN_Y + 3; y++) {
			q.set(-4, y, -SCREEN_Z, RIB);
			q.set(4, y, -SCREEN_Z, RIB);
		}
	}

	/**
	 * Middle body (r = 7, 8; y 13..33), two storeys with a belt at y = 23: log corner posts, log pilasters at 2..3,
	 * recessed glass bays, a lit band at the top; doors to the terrace on the north and south faces at the first storey.
	 */
	private static void midWall(Q q, int dx, int dz, int r, int aa, boolean ns) {
		CitadelCanvas c = q.c;
		int x = q.cx + dx;
		int z = q.cz + dz;
		for (int y = 13; y <= 33; y++) {
			BlockState s;
			boolean bay = aa <= 1 || aa == 4 || aa == 5;
			boolean door = ns && aa <= 1 && y <= 15;
			boolean win = bay && ((y >= 15 && y <= 20) || (y >= 25 && y <= 30)) && !(ns && aa <= 1 && y <= 16);
			if (door) {
				s = AIR;
			} else if (ns && aa <= 1 && y == 16) {
				s = RIB;
			} else if (aa >= 7) {
				s = y == 33 ? RIB : STRIP;
			} else if (y <= 14) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y == 23) {
				s = r == 8 ? RIB : PLANKS;
			} else if (y == 33) {
				s = r == 7 ? c.kit.wallPanel(true) : RIB;
			} else if (win) {
				s = r == 8 ? AIR : GLASS;
			} else if (aa == 2 || aa == 3) {
				s = r == 8 ? (y == 15 || y == 22 || y == 25 || y == 32 ? RIB : STRIP) : CALCITE;
			} else {
				s = CALCITE;
			}
			q.set(dx, y, dz, s);
		}
	}

	/** Belfry wall (r = 4, 5; y 35..43): dark corner masses, log jambs, open arches 5 wide, a lit band. */
	private static void belfryWall(Q q, int dx, int dz, int r, int aa) {
		CitadelCanvas c = q.c;
		for (int y = 35; y <= 43; y++) {
			BlockState s;
			if (aa >= 4) {
				s = y <= 37 ? RIB : NodeDecor.stoneMix(q.cx + dx, y, q.cz + dz);
			} else if (aa == 3) {
				s = y <= 40 ? STRIP : (y == 41 ? RIB : CALCITE);
			} else if (y <= 40) {
				s = AIR;
			} else if (y == 41) {
				s = RIB;
			} else if (y == 43 && r == 4) {
				s = c.kit.wallPanel(true);
			} else {
				s = CALCITE;
			}
			q.set(dx, y, dz, s);
		}
	}

	/**
	 * The roof: a ring of stairs per level (cherry outside, blackstone bricks inside), one block in and one up each,
	 * every inner ring on solid blocks; then a spire of log, a froglight and a rod.
	 */
	private static void roof(Q q, int dx, int dz, int r) {
		int y0 = CEIL + 1;
		if (r > 6) {
			return;
		}
		if (r >= 1) {
			int ytop = y0 + (6 - r);
			for (int y = y0; y < ytop; y++) {
				q.set(dx, y, dz, RIB);
			}
			Block blk = r >= 4 ? Blocks.CHERRY_STAIRS : Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
			q.set(dx, ytop, dz, stairs(blk, NodeDecor.inward(dx, dz)));
		} else {
			for (int y = y0; y <= y0 + 6; y++) {
				q.set(dx, y, dz, RIB);
			}
			q.set(dx, y0 + 7, dz, LOG);
			q.set(dx, y0 + 8, dz, b(Blocks.GOLD_BLOCK));
			q.set(dx, y0 + 9, dz, LIGHT);
			q.set(dx, y0 + 10, dz, NodeDecor.ROD);
			q.set(dx, y0 + 11, dz, NodeDecor.ROD);
		}
	}

	// ------------------------------------------------------------------ stairs

	/**
	 * A switchback flight between the deck blocks {@code d0} (below) and {@code d1} (above): two lanes 2 wide (A at
	 * {@code xa..xa+1}, B at {@code xb..xb+1}), flight 1 of {@code n1} steps from {@code zStart} towards {@code dirZ},
	 * a 2 x 4 landing, then flight 2 back the other way. The first step faces whoever comes to it (head-on). The deck
	 * above is cut over every step with under 3 blocks of headroom; the cut gets a rail on its open sides.
	 */
	private static void flight(Q q, int d0, int d1, int xa, int xb, int zStart, int dirZ, int n1) {
		Direction up1 = dirZ > 0 ? Direction.SOUTH : Direction.NORTH;
		Direction up2 = up1.getOpposite();
		int n2 = (d1 - d0) - n1;
		for (int i = 0; i < n1; i++) {
			int z = zStart + dirZ * i;
			int y = d0 + 1 + i;
			for (int x = xa; x <= xa + 1; x++) {
				q.fill(x, d0 + 1, y - 1, z, FOOT);
				q.set(x, y, z, stairs(Blocks.CHERRY_STAIRS, up1));
				clearAbove(q, x, y, z, d1);
			}
		}
		// landing: 2 deep, covering both lanes
		int zl = zStart + dirZ * n1;
		int xmin = Math.min(xa, xb);
		int xmax = Math.max(xa, xb) + 1;
		for (int k = 0; k < 2; k++) {
			for (int x = xmin; x <= xmax; x++) {
				q.fill(x, d0 + 1, d0 + n1 - 1, zl + dirZ * k, FOOT);
				q.set(x, d0 + n1, zl + dirZ * k, PLANKS);
				clearAbove(q, x, d0 + n1, zl + dirZ * k, d1);
			}
		}
		// flight 2
		int zb0 = zStart + dirZ * (n1 - 1);
		boolean hole = false;
		int holeFirst = 0;
		int holeLast = 0;
		for (int i = 0; i < n2; i++) {
			int z = zb0 - dirZ * i;
			int y = d0 + n1 + 1 + i;
			for (int x = xb; x <= xb + 1; x++) {
				q.fill(x, d0 + 1, y - 1, z, FOOT);
				q.set(x, y, z, stairs(Blocks.CHERRY_STAIRS, up2));
				if (y >= d1 - 3 && y < d1) {
					q.set(x, d1, z, AIR);
				} else if (y < d1) {
					clearAbove(q, x, y, z, d1);
				}
			}
			if (y >= d1 - 3 && y < d1) {
				if (!hole) {
					holeFirst = z;
					hole = true;
				}
				holeLast = z;
			}
		}
		// rail round the cut, on the upper level: along both sides and across the end away from the exit
		if (hole) {
			int lo = Math.min(holeFirst, holeLast);
			int hi = Math.max(holeFirst, holeLast);
			for (int z = lo; z <= hi; z++) {
				rail(q, xb - 1, d1 + 1, z);
				rail(q, xb + 2, d1 + 1, z);
			}
			int zEnd = holeFirst + dirZ;
			rail(q, xb, d1 + 1, zEnd);
			rail(q, xb + 1, d1 + 1, zEnd);
		}
	}

	/** A rail post, only inside the room (never over a wall). */
	private static void rail(Q q, int x, int y, int z) {
		if (Math.abs(x) <= 6 && Math.abs(z) <= 6) {
			q.set(x, y, z, RAIL);
		}
	}

	/** Frees the cells over a step: up to (and not including) the deck {@code d1}, only the 3 above the step. */
	private static void clearAbove(Q q, int x, int y, int z, int d1) {
		for (int t = 1; t <= 3 && y + t < d1; t++) {
			q.set(x, y + t, z, AIR);
		}
	}

	// ------------------------------------------------------------------ furniture

	private static BlockState lectern(Direction d) {
		return NodeDecor.facing(Blocks.LECTERN, d).setValue(BlockStateProperties.HAS_BOOK, true);
	}

	private static BlockState chest(Direction d) {
		return NodeDecor.facing(Blocks.CHEST, d);
	}

	private static BlockState carpet() {
		return Blocks.CARPET.pick(DyeColor.WHITE).defaultBlockState();
	}

	private static void lantern(Q q, int x, int y, int z) {
		q.put(x, y, z, LANTERN_DOWN, HANGING);
	}

	/** A shelf of 3 against a wall, opening towards {@code d}. */
	private static void shelf(Q q, int x, int y, int z, Direction d) {
		q.set(x, y, z, NodeDecor.facing(Blocks.CHISELED_BOOKSHELF, d));
		q.set(x, y + 1, z, b(Blocks.BOOKSHELF));
		q.set(x, y + 2, z, NodeDecor.facing(Blocks.CHISELED_BOOKSHELF, d));
	}

	/** Whether the hall cell is over the stairs of the south-west flight (no beams or lanterns there). */
	private static boolean overStairs(int x, int z) {
		return x >= -7 && x <= -2 && z >= 2;
	}

	/**
	 * L0, the review hall (interior -10..10): the screen in the north wall and the aisle in front of it, two blocks of
	 * seats facing it at reading distance, terminals and lecterns at its foot, the review table with its terminal in the
	 * south-east, chests along the walls, shelves in the south-west beside the stairs, lantern beams.
	 */
	private static void hall(Q q) {
		CitadelCanvas c = q.c;
		// ceiling beams (not over the stairs) with lanterns under them
		for (int z : new int[] {-6, 0, 6}) {
			for (int x = -10; x <= 10; x++) {
				if (!overStairs(x, z)) {
					q.set(x, F1 - 1, z, c.kit.beam(Direction.Axis.X));
				}
			}
			for (int x : new int[] {-8, -4, 0, 4, 8}) {
				if (z != -6 && !overStairs(x, z)) {
					lantern(q, x, F1 - 2, z);
				}
			}
		}
		// seats: two rows each side of the aisle, facing north (the stair's high back is on the south)
		for (int side : new int[] {-1, 1}) {
			for (int row : new int[] {-7, -4}) {
				for (int x = 4; x <= 9; x++) {
					q.set(side * x, 1, row, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
				}
			}
			// terminals and lecterns at the foot of the screen, either side of the aisle
			q.set(side * 4, 1, -10, c.kit.console(Direction.SOUTH));
			q.set(side * 6, 1, -10, lectern(Direction.SOUTH));
			q.set(side * 3, 1, -7, lectern(Direction.SOUTH));
			q.set(side * 3, 1, -4, lectern(Direction.SOUTH));
			// chests along the side walls, facing the hall
			Direction in = side > 0 ? Direction.WEST : Direction.EAST;
			q.set(side * 10, 1, -9, chest(in));
			q.set(side * 10, 1, -6, chest(in));
		}
		// the review table (south-east): six long, chairs on its south side, lanterns and papers, a terminal at its end
		for (int x = 4; x <= 9; x++) {
			q.set(x, 1, 7, NodeDecor.topSlab(Blocks.CHERRY_SLAB));
		}
		for (int x : new int[] {4, 6, 8}) {
			q.set(x, 1, 6, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH));
			q.set(x, 1, 8, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
		}
		q.put(5, 2, 7, LANTERN_UP, DECOR);
		q.put(8, 2, 7, LANTERN_UP, DECOR);
		q.put(4, 2, 7, carpet(), DECOR);
		q.put(7, 2, 7, carpet(), DECOR);
		q.put(9, 2, 7, carpet(), DECOR);
		q.set(10, 1, 7, c.kit.console(Direction.WEST));
		q.set(10, 1, 5, chest(Direction.WEST));
		q.set(10, 1, 9, chest(Direction.WEST));
		// shelves along the west wall beside the stairs, a planter in each south corner
		for (int z = 4; z <= 9; z++) {
			shelf(q, -10, 1, z, Direction.EAST);
			q.set(-10, 4, z, NodeDecor.topSlab(Blocks.CHERRY_SLAB));
		}
		q.set(10, 1, 10, c.kit.gardenPlanter());
		q.set(-10, 1, 10, c.kit.gardenPlanter());
	}

	/**
	 * L1, the reviewers' workroom (interior -6..6, doors to the terrace north and south at |x| <= 1): a row of terminals on
	 * the west wall, shelves and chests on the north wall, a reading table in the south-east; benches on the terrace.
	 */
	private static void loft(Q q) {
		CitadelCanvas c = q.c;
		int y = F1 + 1;
		for (int z : new int[] {-5, -3, -1}) {
			q.set(-6, y, z, c.kit.console(Direction.EAST));
		}
		for (int z : new int[] {-4, -2}) {
			q.set(-6, y, z, NodeDecor.topSlab(Blocks.CHERRY_SLAB));
			q.put(-6, y + 1, z, LANTERN_UP, DECOR);
		}
		for (int x = -5; x <= -3; x++) {
			shelf(q, x, y, -6, Direction.SOUTH);
		}
		q.set(-2, y, -6, chest(Direction.SOUTH));
		q.set(2, y, -6, chest(Direction.SOUTH));
		// a second desk: two terminals back to back with a lantern between, chairs either side
		for (int x : new int[] {-4, -3}) {
			q.set(x, y, 1, NodeDecor.topSlab(Blocks.CHERRY_SLAB));
			q.set(x, y, 0, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH));
		}
		q.set(-4, y + 1, 1, c.kit.console(Direction.NORTH));
		q.put(-3, y + 1, 1, LANTERN_UP, DECOR);
		// reading table (south-east) with chairs on both sides
		for (int x : new int[] {4, 5}) {
			q.set(x, y, 4, NodeDecor.topSlab(Blocks.CHERRY_SLAB));
			q.set(x, y, 3, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH));
			q.set(x, y, 5, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
		}
		q.put(4, y + 1, 4, LANTERN_UP, DECOR);
		for (int x = 2; x <= 6; x++) {
			shelf(q, x, y, 6, Direction.NORTH);
		}
		// hanging lanterns under the next deck
		for (int x : new int[] {-4, 0, 4}) {
			for (int z : new int[] {-4, 0, 4}) {
				boolean east = x >= 3 && z <= 1;
				boolean hole = x >= -4 && x <= -3 && z >= 3;
				if (!east && !hole) {
					lantern(q, x, F2 - 1, z);
				}
			}
		}
		// terrace east and west: benches facing out and planters
		for (int sd : new int[] {-1, 1}) {
			Direction back = sd > 0 ? Direction.WEST : Direction.EAST;
			for (int z : new int[] {-6, -5, 5, 6}) {
				q.set(sd * 11, y, z, stairs(Blocks.CHERRY_STAIRS, back));
			}
			q.set(sd * 11, y, 0, c.kit.gardenPlanter());
			q.set(sd * 11, y, -3, c.kit.gardenPlanter());
			q.set(sd * 11, y, 3, c.kit.gardenPlanter());
		}
		// terrace: two benches on each side, facing out, on the walkway beside the railing
		for (int x : new int[] {-6, -5, 5, 6}) {
			q.set(x, y, -11, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
			q.set(x, y, 11, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH));
		}
	}

	/**
	 * L2, the archive gallery (interior -6..6): shelves on the south and west walls, a long reading table with chairs,
	 * lecterns and chests on the east wall. The walk from the stairs arriving in the east to the ones leaving in the
	 * west (z = 1..3) stays 3 wide.
	 */
	private static void gallery(Q q) {
		CitadelCanvas c = q.c;
		int y = F2 + 1;
		for (int x = -5; x <= 5; x++) {
			shelf(q, x, y, 6, Direction.NORTH);
		}
		for (int z = -5; z <= 3; z++) {
			shelf(q, -6, y, z, Direction.EAST);
		}
		for (int x = -2; x <= 2; x++) {
			q.set(x, y, 4, NodeDecor.topSlab(Blocks.CHERRY_SLAB));
			q.set(x, y, 5, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
		}
		q.put(-1, y + 1, 4, LANTERN_UP, DECOR);
		q.put(1, y + 1, 4, LANTERN_UP, DECOR);
		q.set(6, y, 4, c.kit.console(Direction.WEST));
		q.set(6, y, 5, chest(Direction.WEST));
		for (int x = 0; x <= 5; x++) {
			shelf(q, x, y, -6, Direction.SOUTH);
		}
		q.set(6, y, -5, lectern(Direction.WEST));
		for (int x = 1; x <= 3; x++) {
			shelf(q, x, y, -2, Direction.SOUTH);
		}
		q.set(0, y, -2, lectern(Direction.EAST));
		for (int x : new int[] {-4, 0, 4}) {
			for (int z : new int[] {-4, 0, 4}) {
				boolean east = x >= 4 && z >= -3 && z <= 1;
				boolean west = x <= 0 && z <= 1 && x >= -4 && z >= -6;
				if (!east && !west) {
					lantern(q, x, F3 - 1, z);
				}
			}
		}
	}

	/** L3, the belfry (interior -3..3): the bell on a plinth, a bench looking north, the beam with its lantern. */
	private static void belfry(Q q) {
		CitadelCanvas c = q.c;
		int y = F3 + 1;
		q.set(2, y, 0, RIB);
		q.set(2, y + 1, 0, b(Blocks.BELL));
		for (int x = -3; x <= 3; x++) {
			q.set(x, CEIL - 1, 0, c.kit.beam(Direction.Axis.X));
		}
		lantern(q, 2, CEIL - 2, 0);
		lantern(q, -2, CEIL - 2, 0);
		for (int x : new int[] {1, 2}) {
			q.set(x, y, -3, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
		}
		q.set(2, y, 3, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH));
		q.set(1, y, 3, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH));
	}
}
