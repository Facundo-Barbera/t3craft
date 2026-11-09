package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.barrel;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.clearPane;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.door;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.facing;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.log;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.slab;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.tintBlock;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.tintPane;
import static dev.t3craft.hq.citadel.catalog.house.HouseWorkshopKit.wall;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A working scriptorium: a stone copying hall where the agents copy, bind and file. A tall nave (copy hall, 10 blocks
 * clear under an open timber roof) with desks and chiselled shelves along both walls, a binding corner (loom,
 * barrels, ink cauldron) under a loft that holds the archive, a cartography table and the copyist's cell, a
 * switchback stair in one back corner, and, when the lot is wide enough, a small cloister next to it: a hedged
 * garth with a bench, ink vats and beds of ink plants (cornflower, allium, berries), reached by a side door from the hall.
 *
 * <p>Concept: Cistercian scriptoria and Sainte-Chapelle glazing in the citadel palette. Deepslate plinth, tuff then
 * stone-brick then calcite piers that stand a block proud as buttresses, tall mullioned windows between them,
 * a steep gable roof with a glazed ridge, trim stairs and gold finials. Materials: deepslate, stone brick, calcite,
 * the wood trim (cherry, dark oak or spruce by seed) and the roof (deepslate tile, stone brick or mud brick).
 *
 * <pre>
 *  section across the nave (u →)              plan (v ↓ = front → back), interior 9 or 11 wide
 *   y21  rod  y12-20 steep roof, ridge lantern   v0   porch, steps, hood
 *   y12  plate + tie beams + collars             v2.. copy hall: desks, shelves (double height)
 *   y8-11 loft (back) / upper windows            vs.. loft over: loom, barrels, cauldron (ground)
 *   y7   loft floor                                    archive, desks, cartography, cell (loft)
 *   y1-6 copy hall / workshop, 6 clear            back corner: switchback stair, 2 flights + landing
 *   y0-2 deepslate plinth
 * </pre>
 *
 * Seed varies: which back corner holds the stair, which side the cloister is on, roof and trim materials, rose window
 * or three lancets in the front gable. Size: 13–23 × 13–23 × 22+ (the body is 13 wide, 15 on lots 20 wide or more,
 * and up to 17 deep; the rest of the lot becomes cloister and back garden).
 */
public final class HouseScriptorium implements Variant {
	private static final int PLATE = 12; // top plate; walls y1..y11, roof from y12
	private static final int LOFT = 7; // the loft floor layer; its walking layer is y8
	private static final DyeColor[] JEWELS = {DyeColor.LIGHT_BLUE, DyeColor.PINK, DyeColor.CYAN, DyeColor.YELLOW, DyeColor.MAGENTA};
	/** Light glass for the gable lights: it must read bright from outside, not as a dark slot. */
	private static final DyeColor[] PALE = {DyeColor.LIGHT_BLUE, DyeColor.PINK, DyeColor.YELLOW, DyeColor.WHITE, DyeColor.LIME};
	private static final BooleanProperty[] SLOTS = {BlockStateProperties.SLOT_0_OCCUPIED, BlockStateProperties.SLOT_1_OCCUPIED,
			BlockStateProperties.SLOT_2_OCCUPIED, BlockStateProperties.SLOT_3_OCCUPIED, BlockStateProperties.SLOT_4_OCCUPIED,
			BlockStateProperties.SLOT_5_OCCUPIED};

	@Override
	public String id() {
		return "house_scriptorium";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 13;
	}

	@Override
	public int minD() {
		return 13;
	}

	@Override
	public int minH() {
		return 22;
	}

	@Override
	public int maxW() {
		return 23;
	}

	@Override
	public int maxD() {
		return 23;
	}

	// ------------------------------------------------------------------ materials and geometry

	/** [palette] roof and wood trim, three sets picked by seed. */
	private record Mat(Block roof, Block roofBlock, Block trimStairs, Block trimSlab, Block trimPlanks, Block strippedLog, Block log, Block door) {
	}

	private static Mat mat(int i) {
		return switch (i) {
			case 0 -> new Mat(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.CHERRY_PLANKS,
					Blocks.STRIPPED_CHERRY_LOG, Blocks.CHERRY_LOG, Blocks.CHERRY_DOOR);
			case 1 -> new Mat(Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS,
					Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_DOOR);
			default -> new Mat(Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS,
					Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_LOG, Blocks.SPRUCE_DOOR);
		};
	}

	/** The measures of one build. {@code kind[v]}: 0 pier, 1 window, 2 mullion, along the long walls. */
	private static final class L {
		final int w;
		final int d;
		final int bw; // body width (odd)
		final int bd; // body depth
		final int s; // spare width beside the body (the cloister)
		final boolean courtLeft;
		final int bu0;
		final int cu;
		final int wl; // left wall column
		final int wr;
		final int iu0; // first interior column
		final int iu1;
		final int iw;
		final int vb; // back wall row
		final int ivb; // last interior row
		final int vs; // first row of the loft and of the stair well
		final int left;
		final boolean wellRight;
		final boolean rose;
		final Mat mat;
		final int[] kind;
		final int doorRow; // the side door to the cloister, or -1

		L(Plot p, Random r) {
			w = p.w();
			d = p.d();
			bw = w >= 20 && p.h() >= 23 ? 15 : 13;
			bd = Math.min(d, 17);
			s = w - bw;
			courtLeft = s > 0 && r.nextBoolean();
			bu0 = courtLeft ? s : 0;
			cu = bu0 + bw / 2;
			wl = bu0 + 1;
			wr = bu0 + bw - 2;
			iu0 = bu0 + 2;
			iu1 = bu0 + bw - 3;
			iw = bw - 4;
			vb = bd - 2;
			ivb = vb - 1;
			vs = ivb - 4;
			left = (bw - 1) / 2;
			wellRight = r.nextBoolean();
			rose = r.nextInt(3) != 0;
			mat = mat(r.nextInt(3));
			kind = rhythm(vb);
			int dr = -1;
			for (int v = 3; v < vs && dr < 0; v++) {
				if (kind[v] == 2) {
					dr = v;
				}
			}
			doorRow = s >= 3 ? dr : -1;
		}

		/** The interior column {@code k} steps in from the stair-side wall. */
		int col(int k) {
			return wellRight ? iu1 - k : iu0 + k;
		}

		/** Direction (local) from the far wall towards the stair wall. */
		Direction toWell() {
			return wellRight ? Direction.EAST : Direction.WEST;
		}

		Direction toFar() {
			return wellRight ? Direction.WEST : Direction.EAST;
		}

		int out0() {
			return bu0;
		}

		int out1() {
			return bu0 + bw - 1;
		}
	}

	/** Piers every 4 along a long wall with 3-5 wide bays between them, a mullion in the middle of odd bays. */
	private static int[] rhythm(int vb) {
		int[] kind = new int[vb + 1];
		List<Integer> piers = new ArrayList<>();
		piers.add(1);
		for (int v = 5; v <= vb - 3; v += 4) {
			piers.add(v);
		}
		piers.add(vb);
		for (int i = 0; i + 1 < piers.size(); i++) {
			int a = piers.get(i);
			int z = piers.get(i + 1);
			for (int v = a + 1; v < z; v++) {
				kind[v] = 1;
			}
			int width = z - a - 1;
			if (width >= 3 && width % 2 == 1) {
				kind[a + 1 + width / 2] = 2;
			}
		}
		for (int v : piers) {
			kind[v] = 0;
		}
		return kind;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x5C21_9701L);
		L g = new L(p, r);
		foundation(c, p, g);
		frontWall(c, p, g);
		sideWalls(c, p, g);
		backWall(c, p, g);
		corners(c, p, g);
		plate(c, p, g);
		roof(c, p, g);
		gables(c, p, g);
		dormers(c, p, g);
		trusses(c, p, g);
		interior(c, p, g);
		sideDoor(c, p, g);
		yard(c, p, g);
	}

	// ------------------------------------------------------------------ palette helpers

	/** [palette] pale piers on the dark plinth: a clear gradient, deepslate, tuff, stone brick, calcite and quartz at the eaves. */
	private static BlockState pier(Plot p, int u, int y, int v) {
		if (y <= 1) {
			return base(p, u, y, v);
		}
		int h = p.hash(u, y, v, 6);
		if (y <= 4) {
			return h == 0 ? b(Blocks.POLISHED_TUFF) : b(Blocks.TUFF_BRICKS);
		}
		if (y <= 8) {
			return switch (h) {
				case 0 -> b(Blocks.CRACKED_STONE_BRICKS);
				case 1 -> b(Blocks.MOSSY_STONE_BRICKS);
				default -> b(Blocks.STONE_BRICKS);
			};
		}
		return h <= 1 ? b(Blocks.SMOOTH_QUARTZ) : b(Blocks.CALCITE);
	}

	/** [palette] a pilaster cell of the gable faces: the pier gradient to y6, a dark-ish imposta at y7, fluted quartz above, capital, cap. */
	private static BlockState pilaster(Plot p, int u, int y, int v, Direction capFacing) {
		if (y == 12) {
			return post(); // a finial post: nobody can stand on it
		}
		if (y == 11) {
			return b(Blocks.CHISELED_QUARTZ_BLOCK);
		}
		if (y == 7) {
			return b(Blocks.CHISELED_STONE_BRICKS);
		}
		if (y >= 8) {
			return log(Blocks.QUARTZ_PILLAR, Direction.Axis.Y);
		}
		return pier(p, u, y, v);
	}

	/** [palette] the heavy plinth: deepslate bricks, cobbled and cracked, never uniform. */
	private static BlockState base(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.COBBLED_DEEPSLATE);
			case 1 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	/** [palette] wall infill: stone bricks with cracked and mossy patches. */
	private static BlockState infill(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.CRACKED_STONE_BRICKS);
			case 1 -> b(Blocks.MOSSY_STONE_BRICKS);
			default -> b(Blocks.STONE_BRICKS);
		};
	}

	/** [palette] the light gable wall: calcite with the odd smooth-stone block. */
	private static BlockState gableWall(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 5) == 0 ? b(Blocks.SMOOTH_STONE) : b(Blocks.CALCITE);
	}

	private static DyeColor jewel(Plot p, int u, int y, int v) {
		return JEWELS[p.hash(u, y, v, JEWELS.length)];
	}

	/** A mullion: a thin stone post (a wall block with no arms). */
	private static BlockState post() {
		return wall(Blocks.STONE_BRICK_WALL, false, false, false, false, true);
	}

	private static BlockState frieze(L g, boolean alongU) {
		return log(g.mat.strippedLog(), alongU ? Direction.Axis.X : Direction.Axis.Z);
	}

	private static BlockState candle(int n) {
		return b(Blocks.CANDLE).setValue(BlockStateProperties.CANDLES, n).setValue(BlockStateProperties.LIT, true);
	}

	private static BlockState lectern(Direction f) {
		return facing(Blocks.LECTERN, f).setValue(BlockStateProperties.HAS_BOOK, true);
	}

	/** A chiselled shelf facing {@code f} with books in a hash-chosen set of its six slots. */
	private static BlockState shelf(Plot p, int u, int y, int v, Direction f) {
		BlockState s = facing(Blocks.CHISELED_BOOKSHELF, f);
		for (int i = 0; i < SLOTS.length; i++) {
			if (p.hash(u, y * 7 + i, v, 4) != 0) {
				s = s.setValue(SLOTS[i], true);
			}
		}
		return s;
	}

	private static BlockState roofStair(L g, int u, boolean trim, boolean towardRight, boolean top) {
		return stair(trim ? g.mat.trimStairs() : g.mat.roof(), towardRight ? Direction.EAST : Direction.WEST, top);
	}

	// ------------------------------------------------------------------ foundation

	/** The floor inside: dark oak boards with a polished stripe down the nave. */
	private static void foundation(CitadelCanvas c, Plot p, L g) {
		for (int u = g.wl; u <= g.wr; u++) {
			for (int v = 1; v <= g.vb; v++) {
				boolean edge = u == g.wl || u == g.wr || v == 1 || v == g.vb;
				BlockState st;
				if (edge) {
					st = base(p, u, 0, v);
				} else if (u == g.cu) {
					st = b(Blocks.POLISHED_DEEPSLATE);
				} else {
					st = b(Blocks.DARK_OAK_PLANKS);
				}
				p.set(c, u, 0, v, st);
			}
		}
	}

	// ------------------------------------------------------------------ front wall (v = 1)

	private static void frontWall(CitadelCanvas c, Plot p, L g) {
		int cu = g.cu;
		for (int u = g.wl; u <= g.wr; u++) {
			boolean pier = u == g.wl || u == g.wr || Math.abs(u - cu) == 2;
			boolean bay = Math.abs(u - cu) <= 1;
			boolean mull = !pier && !bay && g.bw == 15 && (u == g.wl + 2 || u == g.wr - 2);
			for (int y = 1; y <= 11; y++) {
				BlockState st;
				if (pier) {
					st = y == 11 ? b(Blocks.CHISELED_QUARTZ_BLOCK) : pier(p, u, y, 1);
				} else if (bay) {
					if (u == cu && y <= 2) {
						st = door(g.mat.door(), Direction.NORTH, y == 2); // [depth] the door sits at the back of the bay
					} else if (y <= 1) {
						st = base(p, u, y, 1);
					} else if (y == 2) {
						st = clearPane(true); // sidelights
					} else if (y == 3) {
						st = b(Blocks.CHISELED_STONE_BRICKS); // lintel
					} else if (y == 11) {
						st = frieze(g, true);
					} else if (u == cu) {
						st = post(); // mullion of the great transom
					} else {
						st = tintPane(jewel(p, u, y / 2, 1), true);
					}
				} else if (y <= 2) {
					st = base(p, u, y, 1);
				} else if (y == 11) {
					st = frieze(g, true);
				} else if (mull) {
					st = post();
				} else {
					st = y >= 8 ? tintPane(jewel(p, u / 2, y, 1), true) : clearPane(true);
				}
				p.set(c, u, y, 1, st);
			}
			if (pier) {
				// [depth] piers stand a block proud of the wall, capped with a stair
				if (Math.abs(u - cu) == 2) {
					// [depth] the two inner piers rise as pilasters: tuff, stone brick, an imposta course at y7, then fluted
					// quartz to a chiselled capital and a sloped cap (they no longer run on up the gable to the rake)
					for (int y = 1; y <= 12; y++) {
						p.set(c, u, y, 0, pilaster(p, u, y, 0, Direction.SOUTH));
					}
				} else {
					for (int y = 1; y <= 9; y++) {
						p.set(c, u, y, 0, y == 7 ? b(Blocks.CHISELED_STONE_BRICKS) : pier(p, u, y, 0));
					}
					p.set(c, u, 10, 0, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
					p.set(c, u, 12, 0, slab(g.mat.trimSlab(), true)); // a cornice slab: nobody can stand on the cap under the rake
				}
			} else if (!bay) {
				// [finish] jardinières between the piers
				p.set(c, u, 0, 0, b(Blocks.MOSS_BLOCK));
				p.set(c, u, 1, 0, p.hash(u, 0, 0, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
			}
		}
		// [finish] steps and a gabled hood over the door: beam, a row of stairs and two hanging lanterns
		for (int u = cu - 1; u <= cu + 1; u++) {
			p.set(c, u, 0, 0, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
			p.set(c, u, 5, 0, log(g.mat.log(), Direction.Axis.X));
			p.set(c, u, 6, 0, stair(g.mat.trimStairs(), Direction.SOUTH, false));
		}
		p.put(c, cu - 1, 4, 0, lantern(true), CitadelCanvas.HANGING);
		p.put(c, cu + 1, 4, 0, lantern(true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ long walls

	/** One cell of a long wall that is not a pier. {@code loft}: the row is over the loft (windows split by a floor band). */
	private static BlockState wallCell(Plot p, L g, int u, int y, int v, int kind, boolean loft) {
		if (y <= 2) {
			return base(p, u, y, v);
		}
		if (y == 11) {
			return frieze(g, false);
		}
		boolean band = loft && (y == 7 || y == 8);
		if (band) {
			return infill(p, u, y, v);
		}
		if (kind == 2) {
			return post();
		}
		boolean tint = loft ? y >= 9 : y >= 8;
		return tint ? tintPane(jewel(p, 0, y, v / 3), false) : clearPane(false);
	}

	private static void sideWalls(CitadelCanvas c, Plot p, L g) {
		for (int side = 0; side < 2; side++) {
			int u = side == 0 ? g.wl : g.wr;
			int out = side == 0 ? g.out0() : g.out1();
			Direction toWall = side == 0 ? Direction.EAST : Direction.WEST;
			for (int v = 2; v < g.vb; v++) {
				int kind = g.kind[v];
				boolean loft = v >= g.vs;
				for (int y = 1; y <= 11; y++) {
					BlockState st = kind == 0 ? (y == 11 ? b(Blocks.CHISELED_QUARTZ_BLOCK) : pier(p, u, y, v)) : wallCell(p, g, u, y, v, kind, loft);
					p.set(c, u, y, v, st);
				}
				if (kind == 0) {
					// [depth] buttress: stone column, sloped cap
					for (int y = 1; y <= 9; y++) {
						p.set(c, out, y, v, pier(p, out, y, v));
					}
					p.set(c, out, 10, v, stair(Blocks.STONE_BRICK_STAIRS, toWall, false));
				} else {
					// [depth] a plinth skirt with a stone sill under the window
					p.set(c, out, 0, v, base(p, out, 0, v));
					p.set(c, out, 1, v, base(p, out, 1, v));
					p.set(c, out, 2, v, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
				}
			}
		}
	}

	// ------------------------------------------------------------------ back wall (v = vb)

	private static void backWall(CitadelCanvas c, Plot p, L g) {
		int cu = g.cu;
		int vb = g.vb;
		for (int u = g.wl; u <= g.wr; u++) {
			boolean pier = u == g.wl || u == g.wr || Math.abs(u - cu) == 2;
			boolean center = Math.abs(u - cu) <= 1;
			for (int y = 1; y <= 11; y++) {
				BlockState st;
				if (pier) {
					st = y == 11 ? b(Blocks.CHISELED_QUARTZ_BLOCK) : pier(p, u, y, vb);
				} else if (y <= 2) {
					st = base(p, u, y, vb);
				} else if (y == 11) {
					st = frieze(g, true);
				} else if (center && y <= 6) {
					st = u == cu ? post() : clearPane(true);
				} else if (center && y >= 9) {
					st = u == cu ? post() : tintPane(jewel(p, u, y, vb), true);
				} else if (y >= 9) {
					st = gableWall(p, u, y, vb);
				} else {
					st = infill(p, u, y, vb);
				}
				p.set(c, u, y, vb, st);
			}
			if (pier && Math.abs(u - cu) == 2) {
				for (int y = 1; y <= 12; y++) {
					p.set(c, u, y, vb + 1, pilaster(p, u, y, vb + 1, Direction.NORTH));
				}
			} else if (pier) {
				for (int y = 1; y <= 9; y++) {
					p.set(c, u, y, vb + 1, y == 7 ? b(Blocks.CHISELED_STONE_BRICKS) : pier(p, u, y, vb + 1));
				}
				p.set(c, u, 10, vb + 1, stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
				p.set(c, u, 12, vb + 1, slab(g.mat.trimSlab(), true));
			}
		}
	}

	/** [depth] chunky corner buttresses: a column at each outer corner, one cell out in both directions. */
	private static void corners(CitadelCanvas c, Plot p, L g) {
		for (int out : new int[] {g.out0(), g.out1()}) {
			for (int v : new int[] {0, g.vb + 1}) {
				for (int y = 1; y <= 10; y++) {
					p.set(c, out, y, v, pier(p, out, y, v));
				}
				p.set(c, out, 11, v, b(Blocks.CHISELED_QUARTZ_BLOCK));
			}
			boolean leftSide = out == g.out0();
			for (int v : new int[] {1, g.vb}) {
				for (int y = 1; y <= 9; y++) {
					p.set(c, out, y, v, pier(p, out, y, v));
				}
				p.set(c, out, 10, v, stair(Blocks.STONE_BRICK_STAIRS, leftSide ? Direction.EAST : Direction.WEST, false));
			}
			p.set(c, out, 0, 0, base(p, out, 0, 0));
		}
	}

	// ------------------------------------------------------------------ plate (y12)

	private static void plate(CitadelCanvas c, Plot p, L g) {
		for (int u = g.wl; u <= g.wr; u++) {
			p.set(c, u, PLATE, 1, log(g.mat.log(), Direction.Axis.X));
			p.set(c, u, PLATE, g.vb, log(g.mat.log(), Direction.Axis.X));
		}
		for (int v = 1; v <= g.vb; v++) {
			p.set(c, g.wl, PLATE, v, log(g.mat.log(), Direction.Axis.Z));
			p.set(c, g.wr, PLATE, v, log(g.mat.log(), Direction.Axis.Z));
		}
	}

	// ------------------------------------------------------------------ roof

	private static int roofY(L g, int u) {
		int uu = u - g.bu0;
		return PLATE + Math.min(uu, g.bw - 1 - uu);
	}

	/**
	 * [finish] a steep gable roof: trim stairs on the eave and rakes, a lighter stripe, tiles inside, a glazed ridge
	 * lantern, slab cap and gold finials with a lightning rod.
	 */
	private static void roof(CitadelCanvas c, Plot p, L g) {
		Mat m = g.mat;
		int left = g.left;
		int top = PLATE + left;
		int ru = g.bu0 + left;
		for (int v = 0; v < g.bd; v++) {
			for (int uu = 0; uu < g.bw; uu++) {
				int u = g.bu0 + uu;
				int ring = Math.min(uu, g.bw - 1 - uu);
				int y = PLATE + ring;
				boolean leftHalf = uu < left;
				boolean rim = v == 0 || v == g.bd - 1 || uu == 0 || uu == g.bw - 1;
				BlockState st;
				if (uu == left) {
					st = b(m.roofBlock());
				} else {
					boolean band = ring == 4;
					st = roofStair(g, u, rim || band, leftHalf, false);
				}
				p.set(c, u, y, v, st);
				// [depth] under the eaves, an upside-down stair closes the underside above each buttress
				if ((uu == 0 || uu == g.bw - 1) && v >= 1 && v <= g.bd - 2) {
					p.set(c, u, y - 1, v, v <= g.vb + 1 && g.kind.length > v && g.kind[v] == 0 ? b(Blocks.DEEPSLATE_BRICKS) : roofStair(g, u, false, !leftHalf, true));
				}
			}
		}
		// glazed ridge lantern: panes either side of an opening, capped
		int v0 = 3;
		int v1 = g.bd - 4;
		for (int v = v0; v <= v1; v++) {
			p.set(c, ru, top, v, CitadelCanvas.AIR);
			p.set(c, ru - 1, top, v, tintPane(jewel(p, 1, 0, v / 2), false));
			p.set(c, ru + 1, top, v, tintPane(jewel(p, 2, 0, v / 2), false));
			p.set(c, ru - 1, top + 1, v, stair(m.trimStairs(), Direction.EAST, false));
			p.set(c, ru + 1, top + 1, v, stair(m.trimStairs(), Direction.WEST, false));
			p.set(c, ru, top + 1, v, slab(m.trimSlab(), false));
		}
		for (int du = -1; du <= 1; du++) {
			BlockState end = du == 0 ? b(m.trimPlanks()) : b(m.roofBlock());
			p.set(c, ru + du, top, v0 - 1, end);
			p.set(c, ru + du, top, v1 + 1, end);
		}
		for (int v = 0; v < g.bd; v++) {
			if (v < v0 - 1 || v > v1 + 1) {
				p.set(c, ru, top + 1, v, slab(m.trimSlab(), false)); // ridge cap outside the lantern
			}
		}
		// [finish] finials: a chiselled block, a gold-lit lantern and a lightning rod on each gable peak
		for (int v : new int[] {0, g.bd - 1}) {
			p.set(c, ru, top + 1, v, b(Blocks.CHISELED_STONE_BRICKS));
			p.set(c, ru, top + 2, v, lantern(false));
			p.set(c, ru, top + 3, v, b(Blocks.LIGHTNING_ROD.waxed().unaffected()));
		}
	}

	// ------------------------------------------------------------------ gables

	private static void gables(CitadelCanvas c, Plot p, L g) {
		int cu = g.cu;
		for (int u = g.wl + 1; u <= g.wr - 1; u++) {
			for (int y = PLATE + 1; y < roofY(g, u); y++) {
				boolean rake = y == roofY(g, u) - 1;
				for (int v : new int[] {1, g.vb}) {
					p.set(c, u, y, v, rake ? b(Blocks.STONE_BRICKS) : gableWall(p, u, y, v));
				}
			}
		}
		int ry = PLATE + 3;
		if (g.rose) {
			// the rose: a plus of jewel glass in a chiselled frame over the door
			BlockState frame = b(Blocks.CHISELED_STONE_BRICKS);
			for (int du = -1; du <= 1; du++) {
				for (int dy = -1; dy <= 1; dy++) {
					boolean corner = du != 0 && dy != 0;
					BlockState st = corner ? frame : tintBlock(du == 0 && dy == 0 ? DyeColor.YELLOW : (du == 0 ? DyeColor.PINK : DyeColor.LIGHT_BLUE));
					p.set(c, cu + du, ry + dy, 1, st);
				}
			}
			p.set(c, cu, ry + 2, 1, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, true));
			for (int du : new int[] {-2, 2}) {
				p.set(c, cu + du, ry, 1, stair(Blocks.STONE_BRICK_STAIRS, du < 0 ? Direction.WEST : Direction.EAST, false));
			}
		} else {
			// three lancets
			for (int du : new int[] {-2, 0, 2}) {
				int top = du == 0 ? ry + 1 : ry;
				for (int y = ry - 1; y <= top; y++) {
					p.set(c, cu + du, y, 1, tintPane(PALE[p.hash(du + 5, y, 0, PALE.length)], true));
				}
				p.set(c, cu + du, ry - 2, 1, b(Blocks.CHISELED_STONE_BRICKS));
			}
		}
		// [finish] light behind the glass so the rose and the lancets glow from outside: a stripped beam across the
		// gable (hung from the rake where the slope is low) and a lantern under it behind each light
		for (int du = -1; du <= 1; du++) {
			p.set(c, cu + du, ry + 1, 2, log(g.mat.strippedLog(), Direction.Axis.X));
		}
		for (int du : g.rose ? new int[] {-1, 0, 1} : new int[] {-2, 0, 2}) {
			p.put(c, cu + du, ry, 2, lantern(true), CitadelCanvas.HANGING);
		}
		// back gable: a small round light
		for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, 1}, {0, -1}}) {
			p.set(c, cu + o[0], ry + o[1], g.vb, tintBlock(DyeColor.YELLOW));
		}
		p.set(c, cu, ry, g.vb, tintBlock(DyeColor.ORANGE));
	}

	// ------------------------------------------------------------------ dormers

	/**
	 * [finish] small gabled dormers on both slopes, over the middle of the odd window bays: an opening in the roof, a
	 * stained pane, stone cheeks and a trim gable. They light the nave and break up the two big roof planes.
	 */
	private static void dormers(CitadelCanvas c, Plot p, L g) {
		Mat m = g.mat;
		for (int v = 3; v <= g.bd - 5; v++) {
			if (g.kind.length <= v || g.kind[v] != 2) {
				continue;
			}
			for (int side = 0; side < 2; side++) {
				int u2 = side == 0 ? g.bu0 + 2 : g.bu0 + g.bw - 3;
				int u3 = side == 0 ? g.bu0 + 3 : g.bu0 + g.bw - 4;
				p.set(c, u2, 15, v, tintPane(jewel(p, side, 15, v), false));
				p.set(c, u2, 15, v - 1, b(Blocks.STONE_BRICKS));
				p.set(c, u2, 15, v + 1, b(Blocks.STONE_BRICKS));
				p.set(c, u3, 15, v, CitadelCanvas.AIR); // the opening in the roof
				for (int u : new int[] {u2, u3}) {
					p.set(c, u, 16, v - 1, stair(m.roof(), Direction.SOUTH, false));
					p.set(c, u, 16, v + 1, stair(m.roof(), Direction.NORTH, false));
					p.set(c, u, 16, v, b(m.roofBlock()));
				}
			}
		}
	}

	// ------------------------------------------------------------------ side door to the cloister

	/** A door from the hall into the cloister, in the first odd window bay of the hall, with a step outside. */
	private static void sideDoor(CitadelCanvas c, Plot p, L g) {
		if (g.doorRow < 0) {
			return;
		}
		int v = g.doorRow;
		int wallU = g.courtLeft ? g.wl : g.wr;
		int out = g.courtLeft ? g.out0() : g.out1();
		Direction outward = g.courtLeft ? Direction.WEST : Direction.EAST;
		Direction inward = g.courtLeft ? Direction.EAST : Direction.WEST;
		p.set(c, wallU, 1, v, door(g.mat.door(), outward, false));
		p.set(c, wallU, 2, v, door(g.mat.door(), outward, true));
		p.set(c, wallU, 3, v, b(Blocks.CHISELED_STONE_BRICKS));
		p.set(c, out, 0, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, inward, false));
		p.set(c, out, 1, v, CitadelCanvas.AIR);
		p.set(c, out, 2, v, CitadelCanvas.AIR);
	}

	// ------------------------------------------------------------------ trusses (tie beams and collars)

	/** Trusses sit on the piers and on the middle of the odd bays. */
	private static int[] tieRows(L g) {
		List<Integer> rows = new ArrayList<>();
		for (int v = 2; v < g.vb; v++) {
			if (g.kind[v] != 1) {
				rows.add(v);
			}
		}
		int[] out = new int[rows.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = rows.get(i);
		}
		return out;
	}

	private static void trusses(CitadelCanvas c, Plot p, L g) {
		Mat m = g.mat;
		int left = g.left;
		for (int v : tieRows(g)) {
			// tie rail across the nave (a fence: nobody can stand on it), hanging from the plate
			for (int u = g.wl + 1; u <= g.wr - 1; u++) {
				p.set(c, u, PLATE, v, fence(Blocks.DARK_OAK_FENCE, false, true, false, true));
			}
			// [depth] arch ribs of upside-down stairs hugging the underside of both slopes, joined by a log at the ridge
			for (int uu = 2; uu < left; uu++) {
				p.set(c, g.bu0 + uu, PLATE + uu - 1, v, stair(m.trimStairs(), Direction.EAST, true));
				p.set(c, g.bu0 + g.bw - 1 - uu, PLATE + uu - 1, v, stair(m.trimStairs(), Direction.WEST, true));
			}
			p.set(c, g.cu, PLATE + left - 1, v, log(m.log(), Direction.Axis.X));
			// [finish] chandeliers: a chain and a lantern over the hall, a lantern straight off the beam over the loft
			for (int u : new int[] {g.cu - 3, g.cu, g.cu + 3}) {
				if (v < g.vs) {
					p.put(c, u, PLATE - 1, v, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y), CitadelCanvas.HANGING);
					p.put(c, u, PLATE - 2, v, lantern(true), CitadelCanvas.HANGING);
				} else {
					boolean overCell = v > g.vs && v <= g.ivb && Math.abs(u - g.col(g.iw - 2)) <= 1;
					if (!overCell) { // not over the copyist's cell: a chest top there would leave under 2.85 of headroom
						p.put(c, u, PLATE - 1, v, lantern(true), CitadelCanvas.HANGING);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ interior

	private static void shelfStack(CitadelCanvas c, Plot p, int u, int v, Direction f) {
		p.set(c, u, 1, v, b(Blocks.BOOKSHELF));
		p.set(c, u, 2, v, shelf(p, u, 2, v, f));
	}

	private static void interior(CitadelCanvas c, Plot p, L g) {
		Direction toWell = g.toWell();
		Direction toFar = g.toFar();
		int iw = g.iw;
		int vs = g.vs;
		int ivb = g.ivb;
		int c0 = g.col(0);
		int c1 = g.col(1);
		int far = g.col(iw - 1);
		Mat m = g.mat;

		// ---- the switchback stair in a back corner: flight A (c0, rows vs..vs+3) up to a landing at the back, flight B
		// (c1) back to the loft. One stair per cell, first step met head-on, open air above, landings between
		BlockState stoneStair = stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false);
		for (int j = 0; j < 4; j++) {
			int v = vs + j;
			for (int y = 1; y <= j; y++) {
				p.set(c, c0, y, v, infill(p, c0, y, v));
			}
			p.set(c, c0, 1 + j, v, stoneStair);
		}
		for (int y = 1; y <= 4; y++) {
			p.set(c, c0, y, ivb, infill(p, c0, y, ivb));
			p.set(c, c1, y, ivb, infill(p, c1, y, ivb));
		}
		for (int j = 0; j < 3; j++) {
			int v = vs + 3 - j; // stairs at y5, y6, y7 on rows vs+3, vs+2, vs+1
			int sy = 5 + j;
			for (int y = 1; y < sy; y++) {
				p.set(c, c1, y, v, infill(p, c1, y, v));
			}
			p.set(c, c1, sy, v, stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
		}

		// ---- loft floor: the arrival cell and everything from the second column outwards
		p.set(c, c1, LOFT, vs, b(m.trimPlanks()));
		for (int k = 2; k < iw; k++) {
			for (int v = vs; v <= ivb; v++) {
				p.set(c, g.col(k), LOFT, v, v == vs ? b(m.trimPlanks()) : b(Blocks.DARK_OAK_PLANKS));
			}
		}
		// [safety] a continuous rail along the whole open edge of the loft, from the stair well to the far wall. The
		// stair is left by its side: from the top of flight B (row vs+1) one steps straight onto the loft boards.
		int uLo = Math.min(g.col(1), g.col(iw - 1));
		int uHi = Math.max(g.col(1), g.col(iw - 1));
		for (int k = 1; k < iw; k++) {
			int u = g.col(k);
			boolean e = u + 1 <= uHi || (k == iw - 1 && !g.wellRight);
			boolean w2 = u - 1 >= uLo || (k == iw - 1 && g.wellRight);
			p.set(c, u, LOFT + 1, vs, fence(Blocks.DARK_OAK_FENCE, false, e, k == iw - 1, w2));
		}

		// ---- ground floor: shelves along the walls, desks, the binding corner
		int courtCol = g.courtLeft ? g.iu0 : g.iu1;
		int dv = g.doorRow;
		for (int r = 2; r <= vs - 3; r++) {
			if (dv >= 0 && c0 == courtCol && Math.abs(r - dv) <= 1) {
				continue; // the side door
			}
			shelfStack(c, p, c0, r, toFar);
		}
		for (int r = 2; r <= ivb; r++) {
			if (dv >= 0 && far == courtCol && Math.abs(r - dv) <= 1) {
				continue;
			}
			if (r < vs) {
				shelfStack(c, p, far, r, toWell);
			} else if ((r - vs) % 2 == 1) {
				p.set(c, far, 1, r, facing(Blocks.CHEST, toWell)); // archive chests under the loft
				p.set(c, far, 2, r, shelf(p, far, 2, r, toWell));
			} else {
				shelfStack(c, p, far, r, toWell);
			}
		}
		// copy desks hugging the shelves: a lectern with its book, then a table with candles, then a gap
		int fd = g.col(iw - 2);
		int wd = g.col(1);
		for (int r = 3; r <= vs - 2; r++) {
			int idx = (r - 3) % 3;
			boolean fdOpen = fd == g.col(iw - 2) && Math.abs(fd - courtCol) == 1 && r == dv;
			boolean wdOpen = Math.abs(wd - courtCol) == 1 && r == dv;
			if (fdOpen) {
				idx = 2;
			}
			// [function] a stool (a spruce stair, back to the aisle) at every desk, one step from it: the aisle keeps 3 free
			if (idx == 0) {
				p.set(c, fd, 1, r, lectern(toWell));
				p.set(c, g.col(iw - 3), 1, r, stair(Blocks.SPRUCE_STAIRS, toWell, false));
			} else if (idx == 1) {
				p.set(c, fd, 1, r, b(Blocks.SPRUCE_PLANKS));
				p.set(c, fd, 2, r, candle(3));
				p.set(c, g.col(iw - 3), 1, r, stair(Blocks.SPRUCE_STAIRS, toWell, false));
			}
			if (r <= vs - 3 && !wdOpen) {
				int widx = (r - 3) % 3;
				if (widx == 0) {
					p.set(c, wd, 1, r, r == 3 ? b(Blocks.CARTOGRAPHY_TABLE) : lectern(toFar));
					p.set(c, g.col(2), 1, r, stair(Blocks.SPRUCE_STAIRS, toFar, false));
				} else if (widx == 1) {
					p.set(c, wd, 1, r, b(Blocks.SPRUCE_PLANKS));
					p.set(c, wd, 2, r, candle(2));
					p.set(c, g.col(2), 1, r, stair(Blocks.SPRUCE_STAIRS, toFar, false));
				}
			}
		}
		// [finish] lamps under the loft so the binding corner is lit: lanterns hanging from the loft floor
		for (int r : new int[] {vs + 1, ivb - 2}) {
			p.put(c, g.col(iw / 2), LOFT - 1, r, lantern(true), CitadelCanvas.HANGING);
		}
		// binding and ink under the loft: loom, barrels, ink still and quill table along the back wall
		for (int k = 2; k <= iw - 2; k++) {
			int u = g.col(k);
			switch ((k - 2) % 4) {
				case 0 -> p.set(c, u, 1, ivb, facing(Blocks.LOOM, Direction.NORTH));
				case 1 -> {
					p.set(c, u, 1, ivb, barrel());
					p.set(c, u, 2, ivb, barrel());
				}
				case 2 -> p.set(c, u, 1, ivb, b(Blocks.BREWING_STAND)); // the ink still
				default -> p.set(c, u, 1, ivb, b(Blocks.FLETCHING_TABLE)); // quill station
			}
		}

		// ---- the loft: shelves on the far wall, desks and a cartography table on the back row, the copyist's cell
		for (int r = vs + 1; r <= ivb; r++) {
			p.set(c, far, LOFT + 1, r, shelf(p, far, LOFT + 1, r, toWell));
		}
		for (int k = 3; k <= iw - 5; k++) { // the last columns belong to the copyist's cell
			int u = g.col(k);
			switch ((k - 3) % 4) {
				case 0 -> p.set(c, u, LOFT + 1, ivb, lectern(Direction.NORTH));
				case 1 -> {
					p.set(c, u, LOFT + 1, ivb, b(Blocks.SPRUCE_PLANKS));
					p.set(c, u, LOFT + 2, ivb, candle(3));
				}
				case 2 -> p.set(c, u, LOFT + 1, ivb, b(Blocks.CARTOGRAPHY_TABLE));
				default -> p.set(c, u, LOFT + 1, ivb, facing(Blocks.CHEST, Direction.NORTH));
			}
		}
		cell(c, p, g);
	}

	/** A partition cell: two boards high with a fence lattice over them. */
	private static void screen(CitadelCanvas c, Plot p, L g, int u, int v, boolean alongU) {
		p.set(c, u, LOFT + 1, v, b(g.mat.trimPlanks()));
		p.set(c, u, LOFT + 2, v, b(g.mat.trimPlanks()));
		p.set(c, u, LOFT + 3, v, fence(Blocks.DARK_OAK_FENCE, !alongU, alongU, !alongU, alongU)); // open lattice: no one can stand on it
	}

	/**
	 * [function] the copyist's cell, in the loft's far back corner: a plank screen with a door, a bed with its head to the
	 * screen, a chest and candles, a writing lectern, and a hearth (a lit furnace opening onto a free cell) whose chimney goes out
	 * through the roof. Interior 3 wide, 3 deep; the door is in the screen on the aisle side.
	 */
	private static void cell(CitadelCanvas c, Plot p, L g) {
		Mat m = g.mat;
		int iw = g.iw;
		int pc = g.col(iw - 4); // the screen
		int mc = g.col(iw - 3);
		int bc = g.col(iw - 2);
		int ac = g.col(iw - 1); // against the far wall
		int r1 = g.vs + 1;
		int r2 = g.vs + 2;
		int r3 = g.vs + 3;
		int r4 = g.ivb;
		Direction toWell = g.toWell();
		// the screen: along the front (v = r1) and along the aisle side (u = pc), with a stripped-log post at the corner
		for (int u : new int[] {mc, bc, ac}) {
			screen(c, p, g, u, r1, true);
		}
		for (int r : new int[] {r2, r4}) {
			screen(c, p, g, pc, r, false);
		}
		for (int y = LOFT + 1; y <= LOFT + 2; y++) {
			p.set(c, pc, y, r1, log(m.strippedLog(), Direction.Axis.Y));
		}
		p.set(c, pc, LOFT + 3, r1, fence(Blocks.DARK_OAK_FENCE, false, !g.wellRight, true, g.wellRight));
		p.set(c, pc, LOFT + 1, r3, door(m.door(), g.toFar(), false));
		p.set(c, pc, LOFT + 2, r3, door(m.door(), g.toFar(), true));
		p.set(c, pc, LOFT + 3, r3, fence(Blocks.DARK_OAK_FENCE, true, false, true, false));
		// bed: head to the front screen, foot towards the door row
		p.set(c, ac, LOFT + 1, r3, b(Blocks.BED.pick(DyeColor.WHITE)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		p.set(c, ac, LOFT + 1, r2, b(Blocks.BED.pick(DyeColor.WHITE)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		// chest against the front screen, writing lectern along the back
		p.set(c, bc, LOFT + 1, r2, facing(Blocks.CHEST, Direction.SOUTH));
		p.set(c, mc, LOFT + 1, r2, candle(3)); // candles on the boards in the corner by the chest (on top of it they would hit the tie rail's headroom)
		p.set(c, mc, LOFT + 1, r4, lectern(Direction.NORTH)); // (bc, r4) stays free: it is the cell the furnace opens onto
		// hearth in the back corner, chimney up through the roof
		p.set(c, ac, LOFT + 1, r4, facing(Blocks.FURNACE, toWell).setValue(BlockStateProperties.LIT, true));
		int yb = roofY(g, bc); // where the roof slope reaches the second column of the stack
		int top = PLATE + g.left + 2;
		for (int y = LOFT + 2; y <= top; y++) {
			p.set(c, ac, y, r4, y >= top - 1 ? b(Blocks.DEEPSLATE_BRICKS) : infill(p, ac, y, r4));
			if (y >= yb) {
				p.set(c, bc, y, r4, y >= top - 1 ? b(Blocks.DEEPSLATE_BRICKS) : infill(p, bc, y, r4));
			}
		}
		p.set(c, ac, top + 1, r4, slab(Blocks.DEEPSLATE_BRICK_SLAB, false));
		p.set(c, bc, top + 1, r4, slab(Blocks.DEEPSLATE_BRICK_SLAB, false));
	}

	// ------------------------------------------------------------------ yard

	/** [finish] everything on the plot outside the body: the cloister beside it and the garden behind. */
	private static void yard(CitadelCanvas c, Plot p, L g) {
		for (int v = 0; v < g.d; v++) {
			for (int u = 0; u < g.w; u++) {
				boolean inBody = u >= g.bu0 && u < g.bu0 + g.bw;
				if (inBody && v < g.bd) {
					continue;
				}
				if (inBody) {
					backGarden(c, p, g, u, v);
				} else {
					cloister(c, p, g, u, v);
				}
			}
		}
	}

	/** An ink plant on the ground, in stripes by row: cornflower (blue), allium (violet), berries (black), orchid. */
	private static BlockState plant(Plot p, int u, int v) {
		return switch (Math.floorMod(v / 2, 4)) {
			case 0 -> p.hash(u, 0, v, 5) == 0 ? b(Blocks.LILY_OF_THE_VALLEY) : b(Blocks.CORNFLOWER);
			case 1 -> b(Blocks.ALLIUM);
			case 2 -> b(Blocks.SWEET_BERRY_BUSH).setValue(BlockStateProperties.AGE_3, 3);
			default -> b(Blocks.BLUE_ORCHID);
		};
	}

	private static void hedge(CitadelCanvas c, Plot p, int u, int v) {
		BlockState hedge = p.hash(u, 0, v, 3) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA);
		p.set(c, u, 0, v, hedge);
		p.set(c, u, 1, v, hedge);
	}

	private static void cloister(CitadelCanvas c, Plot p, L g, int u, int v) {
		int s = g.s;
		int ca = g.courtLeft ? g.bu0 - 1 - u : u - (g.bu0 + g.bw);
		boolean outer = ca == s - 1;
		boolean front = v == 0;
		boolean back = v == g.d - 1;
		int gc = (s - 1) / 2;
		int half = s >= 7 ? 1 : 0;
		if (s >= 3 && (outer || front || back)) {
			if (front && Math.abs(ca - gc) <= half) {
				p.set(c, u, 0, v, slab(Blocks.STONE_BRICK_SLAB, false)); // the gate
				return;
			}
			if (front && s >= 5 && Math.abs(ca - gc) == half + 1) {
				p.set(c, u, 0, v, wall(Blocks.COBBLED_DEEPSLATE_WALL, false, false, false, false, true));
				p.set(c, u, 1, v, wall(Blocks.COBBLED_DEEPSLATE_WALL, false, false, false, false, true));
				p.set(c, u, 2, v, lantern(false));
				return;
			}
			hedge(c, p, u, v);
			return;
		}
		int vm = g.doorRow >= 0 ? g.doorRow : g.d / 2;
		boolean pathCol = s >= 3 && ca == gc;
		boolean pathRow = s >= 3 && v == vm;
		if (pathCol || pathRow) {
			if (ca == s - 2 && v == vm) {
				p.set(c, u, 0, v, stair(Blocks.SPRUCE_STAIRS, g.courtLeft ? Direction.WEST : Direction.EAST, false)); // the bench
			} else {
				p.set(c, u, 0, v, slab(Blocks.STONE_BRICK_SLAB, false));
			}
			return;
		}
		if (s >= 5 && ca == 1 && v == vm - 2) {
			p.set(c, u, 0, v, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3)); // ink vat
			return;
		}
		if (v % 2 == 1) {
			p.set(c, u, 0, v, plant(p, u, v));
		}
	}

	private static void backGarden(CitadelCanvas c, Plot p, L g, int u, int v) {
		if (v == g.d - 1) {
			if (Math.abs(u - g.cu) > 1) {
				hedge(c, p, u, v);
			}
			return;
		}
		if (v == g.d - 2) {
			// a working corner along the hedge: barrels, a composter, beds elsewhere
			switch (p.hash(u, 0, v, 4)) {
				case 0 -> p.set(c, u, 0, v, barrel());
				case 1 -> p.set(c, u, 0, v, b(Blocks.COMPOSTER));
				default -> {
				}
			}
			return;
		}
		if ((v - g.bd) % 2 == 0) {
			p.set(c, u, 0, v, plant(p, u, v));
		}
	}
}
