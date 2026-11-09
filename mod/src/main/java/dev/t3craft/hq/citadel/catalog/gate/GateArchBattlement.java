package dev.t3craft.hq.citadel.catalog.gate;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * Neighbourhood gate: an arched passage between two towers. Each tower face is framed by pilasters with a
 * recessed panel between them (a cherry-pink panel in the front recesses of wide towers), a pale corbelled cornice
 * and a parapet whose merlons step in height (full corners, half middles, open crenels). The curtain over the
 * arch is lower than the towers, with its own cornice and a parapet whose middle merlon rises above the rest.
 *
 * <ul>
 * <li><b>Walkable centre</b>: the opening is at least 3 high at its edges; the arch shoulders are
 *     upside-down stairs above head height.</li>
 * <li><b>Depth</b>: no tower face is a flat wall: the pilasters stand one block proud of the recessed
 *     panels, the cornice of upside-down stairs overhangs them, slim towers get a window through.</li>
 * <li><b>Gradient</b>: deepslate plinth, stone-brick body (cracked / mossy mix by hash), polished
 *     andesite pilaster caps, pale diorite cornices, a cherry arch ring with a calcite keystone, cherry panels.</li>
 * <li><b>Silhouette</b>: tower parapet (highest) → curtain parapet with a raised middle merlon → arch.</li>
 * </ul>
 *
 * <p><b>Avenue size</b> (13+ wide, 4+ deep, 14+ high: what the avenues of the 200 % city ask for): a real gatehouse, see
 * {@link Large}. Two 4-wide towers with a guard room in one and a walkable spiral stair in the other, a round or pointed
 * arch of marked voussoirs under a battlemented curtain you walk along (door of each tower onto the wall walk), a
 * raised shield with a banner over the keystone, a raised portcullis, lanterns, and a hip roof on each tower. Material
 * (stone brick, tuff, deepslate), arch shape, tower plan and stair side change with the seed. Smaller plots keep the
 * original pilastered design ({@link #buildSmall}).
 * <p>Size: 5–21 × 2–8 × 8+ (the avenue design needs 13+ × 4+ × 14+).
 */
public final class GateArchBattlement implements Variant {
	private static final BlockState CALCITE = b(Blocks.CALCITE);
	private static final BlockState LANTERN = b(Blocks.LANTERN);

	@Override
	public String id() {
		return "gate_arch_battlement";
	}

	@Override
	public Family family() {
		return Family.GATE;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 2;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 21;
	}

	@Override
	public int maxD() {
		return 8;
	}

	/** Stone-brick body with a deterministic mix of cracked and mossy bricks. */
	private static BlockState wall(Plot p, int u, int y, int v) {
		int k = p.hash(u, y, v, 10);
		if (y <= 1) {
			return b(k < 3 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
		}
		return b(k == 0 ? Blocks.CRACKED_STONE_BRICKS : k == 1 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		if (p.w() >= 13 && p.d() >= 4 && p.h() >= 14) {
			new Large(c, p).build();
		} else {
			buildSmall(c, p);
		}
	}

	/** The original design, for plots too small for the avenue gatehouse. */
	private void buildSmall(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int hh = Math.min(p.h(), 10);
		int pl = w >= 9 ? 3 : w >= 7 ? 2 : 1; // tower width
		int ow = w - 2 * pl; // opening width
		int side = hh >= 9 ? 4 : 3; // clear height at the edges of the opening
		int rise = Math.max(0, Math.min((ow - 1) / 2, hh - 4 - side)); // arch shoulders, in stair steps
		int crown = side + rise; // the arch ring course at the top of the opening
		int cornice = crown + 1; // curtain cornice
		int merlon = crown + 2; // curtain parapet
		int tCornice = hh - 2; // tower cornice
		int tTop = hh - 1; // tower parapet
		int a0 = pl;

		// --- towers: deepslate plinth, stone-brick body, recessed panels, pale cornice, stepped parapet
		for (int sd = 0; sd < 2; sd++) {
			int u0 = sd == 0 ? 0 : w - pl;
			int u1 = u0 + pl - 1;
			int outer = sd == 0 ? u0 : u1;
			int inner = sd == 0 ? u1 : u0;
			Direction outDir = sd == 0 ? Direction.WEST : Direction.EAST;
			for (int u = u0; u <= u1; u++) {
				for (int v = 0; v < d; v++) {
					p.set(c, u, 0, v, b(p.hash(u, 0, v, 3) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS));
					for (int y = 1; y < tCornice; y++) {
						p.set(c, u, y, v, wall(p, u, y, v));
					}
					boolean edgeU = u == u0 || u == u1;
					boolean edgeV = v == 0 || v == d - 1;
					// [depth] cornice: upside-down pale stairs facing out round the top, so the parapet corbels out
					if (edgeV || u == outer) {
						Direction out = v == 0 ? Direction.NORTH : v == d - 1 ? Direction.SOUTH : outDir;
						p.set(c, u, tCornice, v, stairs(Blocks.POLISHED_DIORITE_STAIRS, out).setValue(BlockStateProperties.HALF, Half.TOP));
					} else {
						p.set(c, u, tCornice, v, b(Blocks.POLISHED_ANDESITE));
					}
					// parapet: solid merlons on the corners, half-height merlons and open crenels between
					if (edgeU || edgeV) {
						boolean corner = edgeU && edgeV;
						if (corner) {
							p.set(c, u, tTop, v, wall(p, u, tTop, v));
						} else if ((u + v) % 2 == 0) {
							p.set(c, u, tTop, v, slab(Blocks.STONE_BRICK_SLAB));
						}
					}
				}
			}
			// [depth] a string course at the springing of the arch: a pale band round the tower faces, so no face
			// runs more than three courses without a break (the recesses below cut through it where they open)
			for (int u = u0; u <= u1; u++) {
				for (int v = 0; v < d; v++) {
					if (v == 0 || v == d - 1 || u == outer) {
						p.set(c, u, side, v, b(p.hash(u, side, v, 3) == 0 ? Blocks.CHISELED_STONE_BRICKS : Blocks.POLISHED_ANDESITE));
					}
				}
			}
			// pilaster caps: polished andesite where the cornice meets the tower corners, one course down
			for (int v : new int[] {0, d - 1}) {
				p.set(c, outer, tCornice - 1, v, b(Blocks.POLISHED_ANDESITE));
				p.set(c, inner, tCornice - 1, v, b(Blocks.POLISHED_ANDESITE));
			}
			// [depth] recessed panels between the pilasters
			int panelTop = tCornice - 2;
			if (pl >= 3) {
				for (int u = u0 + 1; u < u1; u++) {
					// front: a cherry-pink panel set into the recess, under a chiselled stone
					for (int y = 2; y <= panelTop; y++) {
						p.set(c, u, y, 0, AIR);
						p.set(c, u, y, 1, y == panelTop ? b(Blocks.CHISELED_STONE_BRICKS) : b(Blocks.CHERRY_PLANKS));
					}
					p.set(c, u, 1, 0, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH)); // a cherry sill at the foot of the recess
					p.set(c, u, panelTop, 0, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP)); // and a lintel
					if (d >= 3) {
						for (int y = 2; y <= panelTop; y++) {
							p.set(c, u, y, d - 1, AIR);
						}
					}
				}
			}
			if (pl == 2) {
				// a two-wide tower: a slit recessed in its outer column above the string course, cherry behind it,
				// under a cherry lintel on the face
				for (int y = side + 1; y <= panelTop; y++) {
					p.set(c, outer, y, 0, AIR);
					p.set(c, outer, y, 1, b(Blocks.CHERRY_PLANKS));
				}
				p.set(c, outer, panelTop + 1, 0, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP));
			}
			if (d >= 3) {
				// the outer side: recessed between the front and back pilasters (through the tower when it is one block wide)
				for (int v = 1; v < d - 1; v++) {
					int yTop = pl == 1 ? side - 1 : panelTop;
					for (int y = 2; y <= yTop; y++) {
						p.set(c, outer, y, v, AIR);
					}
				}
			}
			if (d >= 3 && pl >= 2) {
				// the inner side, facing the passage: a niche between the front and back pilasters
				for (int v = 1; v < d - 1; v++) {
					for (int y = 2; y < side; y++) {
						p.set(c, inner, y, v, AIR);
					}
				}
			}
			// polished deepslate pilaster foot on the inner edge, facing the opening
			for (int v = 0; v < d; v++) {
				p.set(c, inner, 1, v, b(Blocks.POLISHED_DEEPSLATE));
			}
		}

		// --- curtain over the arch: shoulders, ring course, cornice, parapet with a raised middle merlon
		for (int i = 0; i < ow; i++) {
			int e = Math.min(i, ow - 1 - i);
			int u = a0 + i;
			boolean left = i < ow - 1 - i;
			int k = Math.abs(2 * i - (ow - 1)); // distance from the middle, doubled
			boolean isMerlon = (k / 2) % 2 == 0;
			boolean middle = k <= 1;
			for (int v = 0; v < d; v++) {
				int from = side + Math.min(e, rise);
				if (e < rise) {
					boolean face = v == 0 || v == d - 1;
					p.set(c, u, side + e, v, stairs(face ? Blocks.CHERRY_STAIRS : Blocks.STONE_BRICK_STAIRS, left ? Direction.WEST : Direction.EAST)
							.setValue(BlockStateProperties.HALF, Half.TOP));
					from = side + e + 1;
				}
				for (int y = from; y <= crown; y++) {
					p.set(c, u, y, v, wall(p, u, y, v));
				}
				boolean edge = v == 0 || v == d - 1;
				if (edge) {
					p.set(c, u, cornice, v, stairs(Blocks.POLISHED_DIORITE_STAIRS, v == 0 ? Direction.NORTH : Direction.SOUTH)
							.setValue(BlockStateProperties.HALF, Half.TOP));
					if (isMerlon) {
						p.set(c, u, merlon, v, wall(p, u, merlon, v));
						if (middle && merlon + 1 <= tTop) {
							p.set(c, u, merlon + 1, v, slab(Blocks.POLISHED_DIORITE_SLAB)); // the middle merlon stands taller
						}
					}
				} else {
					p.set(c, u, cornice, v, b(p.hash(u, cornice, v, 4) == 0 ? Blocks.STRIPPED_CHERRY_WOOD : Blocks.CHERRY_PLANKS));
				}
			}
		}
		// arch ring: the course over the opening is cherry on both faces (the shoulders are cherry stairs), so the
		// arch reads as a pink band framing the passage
		for (int i = 0; i < ow; i++) {
			int e = Math.min(i, ow - 1 - i);
			if (e >= rise) {
				for (int v : new int[] {0, d - 1}) {
					p.set(c, a0 + i, crown, v, b(Blocks.STRIPPED_CHERRY_WOOD));
				}
			}
		}
		// keystone: calcite in the middle of the ring course on both faces
		int cu = a0 + ow / 2;
		for (int v : new int[] {0, d - 1}) {
			p.set(c, cu, crown, v, CALCITE);
			if (ow % 2 == 0) {
				p.set(c, cu - 1, crown, v, CALCITE);
			}
		}

		// --- lanterns, mid depth, never in the passage's 3-high clear profile: standing in the towers' inner
		// niches when there are niches, else hanging at the edges of the opening only if they stay above y 2
		int lv = d / 2;
		if (d >= 3 && pl >= 2) {
			p.set(c, a0 - 1, 2, lv, LANTERN);
			p.set(c, a0 + ow, 2, lv, LANTERN);
		} else if (side - 1 >= 3) {
			p.put(c, a0, side - 1, lv, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			p.put(c, a0 + ow - 1, side - 1, lv, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		}
	}

	// ======================================================================================================== avenue gatehouse

	/** One stone family: body, dark base, light trim. */
	private record Mat(Block wall, Block cracked, Block mossy, Block stairs, Block base, Block baseCracked, Block baseStairs, Block trim,
			Block trimStairs, Block trimSlab, Block key) {
	}

	private static final Mat[] MATS = {
			// stone brick on a deepslate base, polished andesite trim
			new Mat(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICK_STAIRS,
					Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_STAIRS,
					Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_ANDESITE_SLAB, Blocks.CALCITE),
			// pale tuff brick on a deepslate base, polished tuff trim
			new Mat(Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.TUFF_BRICK_STAIRS,
					Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_STAIRS,
					Blocks.POLISHED_TUFF, Blocks.POLISHED_TUFF_STAIRS, Blocks.POLISHED_TUFF_SLAB, Blocks.CALCITE),
			// dark deepslate brick on a blackstone base, stone-brick trim
			new Mat(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICK_STAIRS,
					Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
					Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.CALCITE)};

	/**
	 * The avenue gatehouse. Local frame, front NORTH: {@code u} across the avenue, {@code v} along it (the passage runs
	 * front to back), {@code y} up.
	 * <ul>
	 * <li>two 4-wide towers ({@code u} 0..3 and {@code w-4..w-1}), the opening between them is {@code w-8} wide;</li>
	 * <li>arch: ring course at y 6 in the middle, 3 at the jambs (round or pointed by seed), voussoirs alternating pale
	 *     and body, the keystone one course taller; a raised portcullis of iron bars under the ring;</li>
	 * <li>curtain: deck at y 7 (corbel table of stairs under the face), walk at y 8, parapet with merlons and crenels;</li>
	 * <li>tower A (guard): floor, door, bench, chest, lantern, room y 1..4; tower B (stair): a spiral of cherry stairs
	 *     (first step head-on from an arched doorway) that rises 9 and comes out through a doorway onto the wall walk;</li>
	 * <li>both towers have a lookout chamber at wall-walk level (y 8..10) with arrow slits, and a hip roof (y 11..13).</li>
	 * </ul>
	 */
	private static final class Large {
		private static final int C = 6; // ring course in the middle of the arch
		private static final int DECK = 8; // wall-walk floor block; feet at 9

		private final CitadelCanvas c;
		private final Plot p;
		private final int w;
		private final int d;
		private final Mat m;
		private final Block roofStairs;
		private final Block roofBlock;
		private final DyeColor bannerColor;
		private final boolean stairLeft;
		private final boolean rounded;
		private final boolean pointed;
		/** The curtain's faces stand back this many blocks from the towers' faces (deep plots only). */
		private final int rec;
		/** Row (v) of the stair's first cell: the doorways and the wall walk line up on it. */
		private final int sv;

		Large(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			this.w = p.w();
			this.d = p.d();
			this.rec = p.d() >= 6 ? 1 : 0;
			this.sv = 1 + rec;
			this.m = MATS[p.hash(7, 7, 7, MATS.length)];
			boolean cherryRoof = bit(4);
			this.roofStairs = cherryRoof ? Blocks.CHERRY_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
			this.roofBlock = cherryRoof ? Blocks.CHERRY_PLANKS : Blocks.DEEPSLATE_TILES;
			this.bannerColor = cherryRoof ? DyeColor.PINK : DyeColor.RED;
			this.stairLeft = bit(1);
			this.rounded = bit(2);
			this.pointed = bit(3);
		}

		private boolean bit(int n) {
			return p.hash(n * 31 + 5, n + 3, n * 17 + 1, 2) == 0;
		}

		private void set(int u, int y, int v, BlockState s) {
			p.set(c, u, y, v, s);
		}

		private void set(int u, int y, int v, Block blk) {
			p.set(c, u, y, v, b(blk));
		}

		/** Body stone, mossier near the ground, with cracked bricks. */
		private BlockState wall(int u, int y, int v) {
			int k = p.hash(u, y, v, 10);
			if (y <= 3) {
				return b(k < 2 ? m.mossy : k == 2 ? m.cracked : m.wall);
			}
			return b(k == 0 ? m.cracked : k == 1 ? m.mossy : m.wall);
		}

		private static BlockState top(BlockState s) {
			return s.setValue(BlockStateProperties.HALF, Half.TOP);
		}

		void build() {
			towers();
			curtain();
		}

		// ------------------------------------------------------------------ stairs round a rectangle

		/** Stairs round the border of a rectangle facing inwards (outer-corner shapes at the corners); {@code core} fills the inside. */
		private void ringIn(int u0, int u1, int v0, int v1, int y, Block blk, Block core) {
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					Direction fx = u0 == u1 ? null : u == u0 ? Direction.EAST : u == u1 ? Direction.WEST : null;
					Direction fz = v0 == v1 ? null : v == v0 ? Direction.SOUTH : v == v1 ? Direction.NORTH : null;
					if (fx != null && fz != null) {
						StairsShape shape = fz == fx.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
						set(u, y, v, stairs(blk, fx).setValue(BlockStateProperties.STAIRS_SHAPE, shape));
					} else if (fx != null || fz != null) {
						set(u, y, v, stairs(blk, fx != null ? fx : fz));
					} else if (core != null) {
						set(u, y, v, core);
					}
				}
			}
		}

		// ------------------------------------------------------------------ towers

		private void towers() {
			tower(stairLeft, true);
			tower(!stairLeft, false);
		}

		/** Direction in the tower's own frame (inwards = EAST, towards the passage) mapped to the plot. */
		private static Direction fd(boolean left, Direction frame) {
			return left || frame.getAxis() == Direction.Axis.Z ? frame : frame.getOpposite();
		}

		private void tower(boolean left, boolean stairTower) {
			int u0 = left ? 0 : w - 4;
			int u1 = u0 + 3;
			int outer = left ? u0 : u1;
			int inner = left ? u1 : u0;
			Direction in = fd(left, Direction.EAST);
			Direction out = in.getOpposite();
			// frame column t (0 outer .. 3 inner) -> u
			int[] col = new int[4];
			for (int t = 0; t < 4; t++) {
				col[t] = left ? t : w - 1 - t;
			}
			int top = 11; // last wall course; the roof starts at 12

			// --- body: dark base, talus, stone, string course, corbelled cornice
			for (int u = u0; u <= u1; u++) {
				for (int v = 0; v < d; v++) {
					set(u, 0, v, b(p.hash(u, 0, v, 3) == 0 ? m.baseCracked : m.base));
					set(u, 1, v, m.base);
					for (int y = 2; y <= top; y++) {
						set(u, y, v, wall(u, y, v));
					}
				}
			}
			ringIn(u0, u1, 0, d - 1, 1, m.baseStairs, m.base); // talus: the base slopes into the wall
			for (int u = u0; u <= u1; u++) {
				for (int v = 0; v < d; v++) {
					boolean edgeU = u == u0 || u == u1;
					boolean edgeV = v == 0 || v == d - 1;
					if (!edgeU && !edgeV) {
						continue;
					}
					boolean corner = edgeU && edgeV;
					boolean outerCorner = corner && u == outer;
					// quoins: long-and-short work in the trim stone up the corners
					if (corner && !(rounded && outerCorner)) {
						for (int y = 2; y <= 10; y++) {
							if ((y + (u == u0 ? 0 : 1) + (v == 0 ? 0 : 1)) % 2 == 0 && y != 7) {
								set(u, y, v, m.trim);
							}
						}
					}
					if (!(rounded && outerCorner)) {
						set(u, 7, v, m.trim); // string course at the level of the wall walk
					}
					// corbel table: stairs under the cornice alternate with blocks
					Direction face = u == u0 ? Direction.WEST : u == u1 ? Direction.EAST : v == 0 ? Direction.NORTH : Direction.SOUTH;
					if (!corner && (u + v) % 2 == 0) {
						set(u, top, v, top(stairs(m.trimStairs, face)));
					} else {
						set(u, top, v, m.trim);
					}
				}
			}
			if (rounded) {
				// chamfer the two outer corners: an inner-corner stair leaves a quarter of each corner cell open
				for (int v : new int[] {0, d - 1}) {
					boolean north = v == 0;
					boolean west = outer == 0;
					Direction f = north ? (west ? Direction.SOUTH : Direction.WEST) : (west ? Direction.EAST : Direction.NORTH);
					for (int y = 2; y <= 10; y++) {
						if (y == 7) {
							continue; // the string course stays a block
						}
						set(outer, y, v, stairs(m.stairs, f).setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.INNER_LEFT));
					}
				}
			}

			// --- arrow slits, one block high (a taller one would be a standable pocket): front and back at the second
			// column, the outer face in the middle, each under a corbelled lintel
			int mid = d / 2;
			for (int y : new int[] {4, 10}) {
				set(col[1], y, 0, AIR);
				set(col[1], y, d - 1, AIR);
				set(outer, y, mid, y == 10 && !stairTower ? b(Blocks.LANTERN) : AIR); // the guard tower's lookout glows through its slit
				if (y == 4) {
					set(col[1], 3, 0, m.trim);
					set(col[1], 3, d - 1, m.trim);
					set(col[1], 5, 0, top(stairs(m.trimStairs, Direction.NORTH)));
					set(col[1], 5, d - 1, top(stairs(m.trimStairs, Direction.SOUTH)));
					set(outer, 5, mid, top(stairs(m.trimStairs, out)));
				}
			}

			// --- inside
			if (stairTower) {
				stairTower(left, col, in, out, inner);
			} else {
				guardRoom(left, col, in, out, inner);
			}
			// the doorway onto the wall walk, over a sill at y 8, 3 high
			for (int y = 9; y <= 11; y++) {
				set(inner, y, sv, AIR);
			}
			// lookout chamber: hollow y 9..11, with a lantern from the ceiling
			for (int t = 1; t <= 2; t++) {
				for (int v = 1; v <= d - 2; v++) {
					for (int y = 9; y <= 11; y++) {
						set(col[t], y, v, AIR);
					}
				}
			}
			if (stairTower) {
				p.put(c, col[1], 11, sv, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			}

			// --- roof: hip of stairs, the lowest course a ceiling over the chamber
			int y = 12;
			for (int l = 0; l < 2 && y + l <= 13; l++) {
				int ru0 = u0 + l;
				int ru1 = u1 - l;
				int rv0 = l;
				int rv1 = d - 1 - l;
				if (ru0 > ru1 || rv0 > rv1) {
					break;
				}
				ringIn(ru0, ru1, rv0, rv1, y + l, roofStairs, roofBlock);
			}
		}

		/** Spiral of cherry stairs in the 2x2 well (frame columns 1..2, rows 1..2); first step head-on from the doorway. */
		private void stairTower(boolean left, int[] col, Direction in, Direction out, int inner) {
			// the arched doorway onto the avenue: 3 high, trim lintel
			for (int y = 0; y <= 2; y++) {
				set(inner, y, sv, AIR);
			}
			set(inner, 3, sv, m.trim);
			int[][] cell = {{2, sv}, {1, sv}, {1, sv + 1}, {2, sv + 1}};
			Direction[] face = {out, Direction.SOUTH, in, Direction.NORTH};
			for (int j = 0; j < 4; j++) {
				int u = col[cell[j][0]];
				int v = cell[j][1];
				for (int y = j; y <= 8; y++) {
					boolean step = y == j || y == j + 4 || y == j + 8;
					if (step) {
						Direction f = y == 8 ? in : face[j]; // the last step (y 8, cell 0) runs out through the doorway
						set(u, y, v, stairs(Blocks.CHERRY_STAIRS, f));
					} else {
						set(u, y, v, AIR);
					}
				}
			}
		}

		/** Guard room: cherry floor, door, bench, chest with a lantern, a hanging lantern; y 1..4. */
		private void guardRoom(boolean left, int[] col, Direction in, Direction out, int inner) {
			for (int t = 1; t <= 2; t++) {
				for (int v = 1; v <= d - 2; v++) {
					set(col[t], 0, v, Blocks.CHERRY_PLANKS);
					for (int y = 1; y <= 4; y++) {
						set(col[t], y, v, AIR);
					}
					set(col[t], 5, v, Blocks.CHERRY_PLANKS);
				}
			}
			// door: a threshold stair, a cherry door, an open transom, a trim lintel
			set(inner, 0, sv, stairs(Blocks.CHERRY_STAIRS, out));
			set(inner, 1, sv, door(in, true));
			set(inner, 2, sv, door(in, false));
			set(inner, 3, sv, AIR);
			set(inner, 4, sv, m.trim);
			// bench along the outer wall, the chest at the back with a lantern on it
			int benchTo = Math.min(d - 3, 3);
			for (int v = 1; v <= benchTo; v++) {
				set(col[1], 1, v, stairs(Blocks.CHERRY_STAIRS, out));
			}
			set(col[1], 1, d - 2, b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, in));
			p.put(c, col[2], 4, d / 2, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			if (d >= 7) {
				set(col[1], 1, d - 3, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
			}
		}

		private BlockState door(Direction facing, boolean lower) {
			return Blocks.CHERRY_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
					.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
		}

		// ------------------------------------------------------------------ curtain over the arch

		private void curtain() {
			int a0 = 4;
			int ow = w - 8;
			double half = (ow - 1) / 2.0;
			int rise = Math.min(3, ow / 2);
			int side = C - rise;
			int[] s = new int[ow];
			for (int i = 0; i < ow; i++) {
				double t = half == 0 ? 0 : Math.abs(i - half) / half;
				double dy = pointed ? rise * Math.pow(1 - t, 0.6) : rise * Math.sqrt(1 - t * t);
				s[i] = side + (int) Math.round(dy);
			}
			boolean backPlaque = d - 2 - 2 * rec >= 3; // the walk keeps at least one free row
			for (int i = 0; i < ow; i++) {
				int u = a0 + i;
				double dist = Math.abs(i - half);
				int sn = i < half ? s[i + 1] : i > half ? s[i - 1] : s[i];
				int ringTop = Math.max(s[i], sn - 1);
				boolean keyCol = dist <= 0.5;
				boolean recess = dist <= 0.5;
				boolean cap = dist <= (ow % 2 == 1 ? 1.0 : 1.5);
				boolean merlon = ((int) Math.floor(dist)) % 2 == 0;
				for (int v = rec; v <= d - 1 - rec; v++) {
					boolean face = v == rec || v == d - 1 - rec;
					Direction outward = v == rec ? Direction.NORTH : Direction.SOUTH;
					// arch ring (voussoirs on the faces, body inside) and the spandrel up to the corbel table
					for (int y = s[i]; y < DECK - 1; y++) {
						if (face && y <= ringTop) {
							set(u, y, v, keyCol && y == C ? b(m.key) : (i + y) % 2 == 0 ? b(m.trim) : b(m.wall));
						} else {
							set(u, y, v, wall(u, y, v));
						}
					}
					// corbel table on the faces with the keystone one course taller; the walk's floor above it
					if (face) {
						if (keyCol) {
							set(u, DECK - 1, v, m.key);
						} else if (i % 2 == 0) {
							set(u, DECK - 1, v, top(stairs(m.trimStairs, outward)));
						} else {
							set(u, DECK - 1, v, m.trim);
						}
						set(u, DECK, v, wall(u, DECK, v));
					} else {
						set(u, DECK - 1, v, wall(u, DECK - 1, v));
						set(u, DECK, v, m.trim);
					}
					// parapet: breast wall, merlons, a raised shield with a banner over the keystone
					if (face) {
						boolean plaque = v == rec || backPlaque;
						if (plaque && recess) {
							set(u, 9, v, slab(m.trimSlab)); // a ledge under the shield
							p.put(c, u, 10, v, b(Blocks.WALL_BANNER.pick(bannerColor)).setValue(BlockStateProperties.HORIZONTAL_FACING, outward),
									CitadelCanvas.DECOR);
							int back = v == rec ? rec + 1 : d - 2 - rec;
							set(u, 9, back, wall(u, 9, back));
							set(u, 10, back, wall(u, 10, back));
							set(u, 11, v, slab(m.trimSlab));
						} else if (plaque && cap) {
							set(u, 9, v, wall(u, 9, v));
							set(u, 10, v, wall(u, 10, v));
							set(u, 11, v, slab(m.trimSlab));
						} else {
							set(u, 9, v, wall(u, 9, v));
							if (merlon) {
								set(u, 10, v, wall(u, 10, v));
							}
						}
					}
				}
			}
			// portcullis: raised, a strip of iron bars just inside the face ring, with teeth hanging in the middle
			for (int i = 0; i < ow; i++) {
				if (s[i] < 5) {
					continue;
				}
				boolean e = i + 1 < ow && s[i + 1] >= 5;
				boolean wst = i > 0 && s[i - 1] >= 5;
				set(a0 + i, s[i] - 1, rec + 1, bars(e, wst));
				if (s[i] >= 6 && ((int) Math.floor(Math.abs(i - half))) % 2 == 0) {
					set(a0 + i, s[i] - 2, rec + 1, bars(false, false));
				}
			}
			// lanterns hanging under the arch on both sides, mid depth, where the passage is 4 high
			int il = 0;
			while (il < ow && s[il] < 5) {
				il++;
			}
			if (il < ow / 2) {
				BlockState lan = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
				p.put(c, a0 + il, s[il] - 1, d / 2, lan, CitadelCanvas.HANGING);
				p.put(c, a0 + ow - 1 - il, s[ow - 1 - il] - 1, d / 2, lan, CitadelCanvas.HANGING);
			}
			// lanterns on the parapet at both ends of the walk
			for (int v : new int[] {rec, d - 1 - rec}) {
				for (int i : new int[] {0, ow - 1}) {
					boolean merlon = ((int) Math.floor(Math.abs(i - half))) % 2 == 0;
					set(a0 + i, merlon ? 11 : 10, v, b(Blocks.LANTERN));
				}
			}
		}

		private static BlockState bars(boolean east, boolean west) {
			return b(Blocks.IRON_BARS).setValue(BlockStateProperties.EAST, east).setValue(BlockStateProperties.WEST, west);
		}
	}
}
