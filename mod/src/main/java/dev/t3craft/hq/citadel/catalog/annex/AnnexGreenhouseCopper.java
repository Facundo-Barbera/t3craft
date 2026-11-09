package dev.t3craft.hq.citadel.catalog.annex;

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
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Glasshouse you can walk through: a deepslate plinth, three-high walls of glass panes between oxidized
 * copper posts, and a glass roof on copper ribs (gable or hip, with or without a raised lantern monitor),
 * ridge in bare copper. Inside, a 2 to 4 wide mud-brick aisle runs from the double door to a potting
 * bench; raised dark-oak beds of crops flank it (or, on wide houses, an island bed between two aisles),
 * watered by waterlogged-slab troughs set inside the bed; a nursery row of pots by the door.
 *
 * <pre>
 *  front (w = 10)                          plan (w = 10, d = 9, gable)
 *  y9          ▲ rod                       v0  ◙ ▬ ▬ ▼ ▼ ▬ ▬ ▬ ▬ ▤   apron: cauldron, steps, barrel
 *  y6-8    ▄▄▄██▄▄▄   ribs, glass roof     v1  ▣ ░ ░ ▯ ▯ ░ ░ ▣     front wall, double door
 *  y4     ▀▀▀▀▀▀▀▀▀▀  eave gutter          v2  ▓ ✿ ✿ ▒ ▒ ✿ ✿ ▓     nursery pots
 *  y1-3   ▣░░▣░░▯▯░░▣ posts, panes, door   v3+ ▓ ⁂ ≈ ▒ ▒ ≈ ⁂ ▓     beds ⁂ with trough ≈, aisle ▒
 *  y0    ▓▓▓▓▓▓▓▓▓▓    deepslate plinth    v7  ▓ ▤ ▤ ◙ ▦ ⚙ ▤ ▓     potting bench
 * </pre>
 *
 * Size: 5–11 × 5–10 × 10+ (aisle of 2+ and a double door from 6 wide; at 5 wide the aisle is 1).
 */
public final class AnnexGreenhouseCopper implements Variant {
	/** Eave level: the walls are y1..y3 below it. */
	private static final int PLATE = 4;

	private static final BlockState POST_W = Blocks.CUT_COPPER.waxed().weathered().defaultBlockState();
	private static final BlockState POST_E = Blocks.CUT_COPPER.waxed().exposed().defaultBlockState();
	private static final Block RIB_W = Blocks.CUT_COPPER_STAIRS.waxed().oxidized();
	private static final Block RIB_O = Blocks.CUT_COPPER_STAIRS.waxed().oxidized();
	private static final Block SLAB_O = Blocks.CUT_COPPER_SLAB.waxed().oxidized();
	private static final Block RIDGE_SL = Blocks.CUT_COPPER_SLAB.waxed().unaffected();
	private static final BlockState RIDGE = Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState();
	private static final Block RIDGE_ST = Blocks.CUT_COPPER_STAIRS.waxed().unaffected();
	private static final BlockState EMBLEM = Blocks.CHISELED_COPPER.waxed().exposed().defaultBlockState();

	private static final int AISLE = 1;
	private static final int BED = 2;
	private static final int STRIP = 3;

	@Override
	public String id() {
		return "annex_greenhouse_copper";
	}

	@Override
	public Family family() {
		return Family.ANNEX;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 5;
	}

	@Override
	public int minH() {
		return 10;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 10;
	}

	// ------------------------------------------------------------------ geometry

	/** Everything the seed and the size decide, computed once. */
	private static final class G {
		final int w;
		final int d;
		final int vf;
		final int vb;
		final int cu;
		final int dw;
		final int dl;
		final boolean hip;
		final boolean lant;
		final int r;
		final int rh;
		final int mu0;
		final int mu1;
		final int mv0;
		final int mv1;
		final boolean[] ribU;
		final boolean[] ribV;
		final int[] kinds;
		final int[] widths;
		final int[] u0;

		G(Plot p) {
			w = p.w();
			d = p.d();
			vf = d >= 6 ? 1 : 0;
			vb = d - 1;
			cu = w / 2;
			dw = w % 2 == 0 ? 2 : 1;
			dl = w % 2 == 0 ? cu - 1 : cu;
			int span = vb - vf;
			int half = (w - 1) / 2;
			boolean hipWant = w >= 9 && span >= 6 && p.hash(0, 0, 0, 3) == 0;
			boolean lantWant = w >= 9 && span >= (hipWant ? 4 : 6) && p.hash(1, 0, 0, 2) == 0 && p.h() - 1 - PLATE - 3 >= 1;
			hip = hipWant;
			lant = lantWant;
			int want = p.hash(2, 0, 0, 2) == 0 ? half : Math.max(1, half - 1);
			int byW = lant ? (w - 3) / 2 : half;
			int byD = hip ? (lant ? (span - 2) / 2 : span / 2) : 99;
			int byH = p.h() - 1 - PLATE - (lant ? 3 : 2);
			r = Math.max(1, Math.min(Math.min(want, byW), Math.min(byD, byH)));
			rh = PLATE + r;
			mu0 = lant ? (w % 2 == 1 ? cu - 1 : cu - 2) : r;
			mu1 = lant ? cu + 1 : w - 1 - r;
			mv0 = hip ? vf + r : vf + 2;
			mv1 = hip ? vb - r : vb - 2;
			ribU = ribs(w);
			ribV = ribs(span + 1);

			// layout of the interior columns u = 1 .. w-2
			int iw = w - 2;
			int[] wd;
			int[] kd;
			if (w >= 11 && p.hash(3, 0, 0, 2) == 0) {
				int island = Math.min(5, iw - 6);
				int rem = iw - 2 - island;
				wd = new int[] {1, rem / 2, island, rem - rem / 2, 1};
				kd = new int[] {STRIP, AISLE, BED, AISLE, STRIP};
			} else if (iw == 3) {
				wd = new int[] {1, 2};
				kd = new int[] {BED, AISLE};
			} else if (iw == 5) {
				wd = new int[] {2, 2, 1};
				kd = new int[] {BED, AISLE, BED};
			} else {
				int bw = Math.max(1, Math.min(4, (iw - 2) / 2));
				wd = new int[] {bw, iw - 2 * bw, bw};
				kd = new int[] {BED, AISLE, BED};
			}
			if (p.hash(4, 0, 0, 2) == 0) {
				for (int i = 0; i < wd.length / 2; i++) {
					int j = wd.length - 1 - i;
					int t = wd[i];
					wd[i] = wd[j];
					wd[j] = t;
					t = kd[i];
					kd[i] = kd[j];
					kd[j] = t;
				}
			}
			widths = wd;
			kinds = kd;
			u0 = new int[wd.length];
			int u = 1;
			for (int i = 0; i < wd.length; i++) {
				u0[i] = u;
				u += wd[i];
			}
		}

		/** Roof height over (u, v). */
		int roofY(int u, int v) {
			int du = Math.min(u, w - 1 - u);
			int dv = hip ? Math.min(v - vf, vb - v) : 99;
			return PLATE + Math.min(r, Math.min(du, dv));
		}

		boolean ridgeCol(int u) {
			return w % 2 == 1 ? u == cu : u == cu - 1 || u == cu;
		}

		boolean inMonitor(int u, int v) {
			return lant && u >= mu0 && u <= mu1 && v >= mv0 && v <= mv1;
		}

		boolean doorCol(int u) {
			return u >= dl && u < dl + dw;
		}
	}

	/** Rib marks over n cells: both ends and a rib about every fifth cell. */
	private static boolean[] ribs(int n) {
		boolean[] out = new boolean[n];
		int count = Math.max(1, (int) Math.round((n - 1) / 5.0));
		for (int i = 0; i <= count; i++) {
			out[(int) Math.round(i * (n - 1) / (double) count)] = true;
		}
		return out;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		G g = new G(p);
		plinth(c, p, g);
		walls(c, p, g);
		roof(c, p, g);
		inside(c, p, g);
		lanterns(c, p, g);
		if (g.vf == 1) {
			apron(c, p, g);
		}
	}

	private static BlockState pane(BlockState glass, boolean alongU, boolean sideA, boolean sideB) {
		return alongU ? glass.setValue(BlockStateProperties.WEST, sideA).setValue(BlockStateProperties.EAST, sideB)
				: glass.setValue(BlockStateProperties.NORTH, sideA).setValue(BlockStateProperties.SOUTH, sideB);
	}

	private static BlockState post(Plot p, int u, int v, boolean corner) {
		return corner || p.hash(u, 0, v, 3) == 0 ? POST_W : POST_E;
	}

	private static BlockState door(boolean lower, DoorHingeSide hinge) {
		return b(Blocks.WARPED_DOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
				.setValue(BlockStateProperties.DOOR_HINGE, hinge);
	}

	// ------------------------------------------------------------------ plinth (y0)

	/** [palette] the heavy base: deepslate bricks, cracked by hash, chiseled at the corners, a polished threshold. */
	private static void plinth(CitadelCanvas c, Plot p, G g) {
		for (int u = 0; u < g.w; u++) {
			for (int v = g.vf; v <= g.vb; v++) {
				boolean side = u == 0 || u == g.w - 1;
				boolean end = v == g.vf || v == g.vb;
				if (!side && !end) {
					continue;
				}
				if (g.vf == 0 && v == 0 && g.doorCol(u)) {
					continue; // ground door: the door stands where the plinth would be
				}
				BlockState st;
				if (side && end) {
					st = b(Blocks.CHISELED_DEEPSLATE);
				} else if (v == g.vf && g.doorCol(u)) {
					st = b(Blocks.POLISHED_DEEPSLATE);
				} else {
					st = p.hash(u, 0, v, 4) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
				}
				p.set(c, u, 0, v, st);
			}
		}
	}

	// ------------------------------------------------------------------ walls (y1..y3)

	private static void walls(CitadelCanvas c, Plot p, G g) {
		int w = g.w;
		BlockState clear = b(Blocks.GLASS_PANE);
		BlockState pink = Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
		for (int y = 1; y <= 3; y++) {
			// side walls: a post on every rib row, panes between (joined front to back)
			for (int v = g.vf + 1; v < g.vb; v++) {
				for (int u : new int[] {0, w - 1}) {
					p.set(c, u, y, v, g.ribV[v - g.vf] ? post(p, u, v, false) : pane(clear, false, true, true));
				}
			}
			// back wall: corner posts, a post on every rib column
			for (int u = 0; u < w; u++) {
				boolean post = u == 0 || u == w - 1 || g.ribU[u];
				p.set(c, u, y, g.vb, post ? post(p, u, g.vb, u == 0 || u == w - 1) : pane(clear, true, true, true));
			}
		}
		// front wall: door in the middle (double from an even width), jambs, posts on the ribs
		int doorBase = g.vf == 0 ? 0 : 1;
		int top = doorBase + 2;
		for (int u = 0; u < w; u++) {
						for (int y = 1; y <= 3; y++) {
				BlockState st;
				if (g.doorCol(u)) {
					if (y == doorBase || y == doorBase + 1) {
						st = door(y == doorBase, g.dw == 2 && u == g.dl + 1 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
					} else if (y == top) {
						st = CitadelCanvas.AIR; // the opening is 3 high
					} else {
						st = POST_W; // lintel
					}
				} else if (u == 0 || u == w - 1 || g.ribU[u]) {
					st = post(p, u, g.vf, u == 0 || u == w - 1);
				} else {
					st = pane(y == 3 ? pink : clear, true, !g.doorCol(u - 1), !g.doorCol(u + 1));
				}
				if (g.vf == 0 && y < doorBase) {
					continue;
				}
				p.set(c, u, y, g.vf, st);
			}
			if (g.vf == 0 && g.doorCol(u)) {
				p.set(c, u, 0, 0, door(true, g.dw == 2 && u == g.dl + 1 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT));
				p.set(c, u, 1, 0, door(false, g.dw == 2 && u == g.dl + 1 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT));
			}
		}
	}

	// ------------------------------------------------------------------ roof

	/**
	 * Gutter of copper slabs round the eaves (stairs on the ribs); above, each column steps up: copper stairs
	 * on the rib rows, clear glass between, a bare-copper ridge on top. Gable ends are panes between copper
	 * mullions with an emblem. A lantern monitor (raised glass box with a copper cap) opens the ridge.
	 */
	private static void roof(CitadelCanvas c, Plot p, G g) {
		int w = g.w;
		BlockState glass = b(Blocks.GLASS);
		for (int v = g.vf; v <= g.vb; v++) {
			for (int u = 0; u < w; u++) {
				int du = Math.min(u, w - 1 - u);
				int dv = g.hip ? Math.min(v - g.vf, g.vb - v) : 99;
				int y = g.roofY(u, v);
				boolean rib = dv >= du ? g.ribV[v - g.vf] : g.ribU[u];
				BlockState st;
				if (du == 0 || dv == 0) {
					Direction in = du == 0 ? (u == 0 ? Direction.EAST : Direction.WEST) : (v == g.vf ? Direction.SOUTH : Direction.NORTH);
					boolean corner = du == 0 && dv == 0;
					st = corner || rib ? stairs(RIB_O, in) : slab(SLAB_O);
				} else if (du >= g.r && dv >= g.r) {
					if (rib) {
						st = POST_W;
					} else {
						st = glass;
					}
					if (g.inMonitor(u, v) && u > g.mu0 && u < g.mu1 && v > g.mv0 && v < g.mv1) {
						st = CitadelCanvas.AIR;
					}
				} else {
					Direction in = du <= dv ? (u < w - 1 - u ? Direction.EAST : Direction.WEST) : (v - g.vf < g.vb - v ? Direction.SOUTH : Direction.NORTH);
					st = rib ? stairs(RIB_W, in) : glass;
				}
				p.set(c, u, y, v, st);
				if (g.hip) {
					// a hip's rings step up one block each, touching only at the corners: a block on top of every ring
					// below the plateau makes the steps meet face to face, so the roof is one piece from the gutter to
					// the ridge whatever the height (a gable gets that from its end walls). It is the tread of the step:
					// a copper slab (a stair facing uphill on the ribs), so every step has a solid copper nose and the
					// glass is the fill between them
					int m = Math.min(du, dv);
					if (m < g.r) {
						Direction up = du <= dv ? (u < w - 1 - u ? Direction.EAST : Direction.WEST) : (v - g.vf < g.vb - v ? Direction.SOUTH : Direction.NORTH);
						p.set(c, u, PLATE + m + 1, v, rib ? stairs(RIB_W, up) : slab(SLAB_O));
					}
				}
			}
		}
		// [finish] a thin bare-copper ridge line on top of the glass, with a finial at each end
		int vlo = g.hip ? g.mv0 : g.vf;
		int vhi = g.hip ? g.mv1 : g.vb;
		for (int v = vlo; v <= vhi; v++) {
			if (g.lant && v >= g.mv0 && v <= g.mv1) {
				continue;
			}
			for (int u = 0; u < w; u++) {
				if (g.ridgeCol(u)) {
					p.set(c, u, g.rh + 1, v, slab(RIDGE_SL));
				}
			}
		}
		rod(c, p, g.cu, g.rh + 2, vlo);
		rod(c, p, g.cu, g.rh + 2, vhi);
		if (!g.hip) {
			gables(c, p, g);
		}
		if (g.lant) {
			monitor(c, p, g);
		}
	}

	private static void rod(CitadelCanvas c, Plot p, int u, int y, int v) {
		p.set(c, u, y, v, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
	}

	private static void gables(CitadelCanvas c, Plot p, G g) {
		int w = g.w;
		BlockState clear = b(Blocks.GLASS_PANE);
		BlockState pink = Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
		for (int v : new int[] {g.vf, g.vb}) {
			for (int u = 1; u < w - 1; u++) {
				for (int y = PLATE; y < g.roofY(u, v); y++) {
					BlockState st;
					if (v == g.vf && y == PLATE && (g.doorCol(u))) {
						st = POST_W; // lintel over the door
					} else if (y == PLATE + 1 && v == g.vf && g.r >= 2 && g.ridgeCol(u) && g.roofY(u, v) > PLATE + 1) {
						st = EMBLEM;
					} else if (g.ribU[u]) {
						st = POST_W; // mullion continues the wall post
					} else {
						st = pane(y == PLATE ? pink : clear, true, true, true);
					}
					p.set(c, u, y, v, st);
				}
			}
		}
	}

	private static void monitor(CitadelCanvas c, Plot p, G g) {
		for (int v = g.mv0; v <= g.mv1; v++) {
			for (int u = g.mu0; u <= g.mu1; u++) {
				boolean edgeU = u == g.mu0 || u == g.mu1;
				boolean edgeV = v == g.mv0 || v == g.mv1;
				if (edgeU || edgeV) {
					BlockState wall = edgeU && edgeV ? POST_W : b(Blocks.GLASS);
					p.set(c, u, g.rh + 1, v, wall);
					p.set(c, u, g.rh + 2, v, wall);
				}
				BlockState cap;
				if (edgeU || edgeV) {
					Direction in = u == g.mu0 ? Direction.EAST : u == g.mu1 ? Direction.WEST : v == g.mv0 ? Direction.SOUTH : Direction.NORTH;
					cap = stairs(RIDGE_ST, in);
				} else {
					cap = RIDGE;
				}
				p.set(c, u, g.rh + 3, v, cap);
			}
		}
	}

	// ------------------------------------------------------------------ inside

	private static BlockState crop(int k) {
		return switch (k) {
			case 0 -> b(Blocks.WHEAT).setValue(BlockStateProperties.AGE_7, 7);
			case 1 -> b(Blocks.CARROTS).setValue(BlockStateProperties.AGE_7, 7);
			case 2 -> b(Blocks.POTATOES).setValue(BlockStateProperties.AGE_7, 7);
			default -> b(Blocks.BEETROOTS).setValue(BlockStateProperties.AGE_3, 3);
		};
	}

	private static BlockState pot(int k) {
		return switch (k) {
			case 0 -> b(Blocks.POTTED_FLOWERING_AZALEA);
			case 1 -> b(Blocks.POTTED_FERN);
			case 2 -> b(Blocks.POTTED_PINK_TULIP);
			case 3 -> b(Blocks.POTTED_CHERRY_SAPLING);
			default -> b(Blocks.POTTED_AZALEA);
		};
	}

	/**
	 * The aisle (mud brick) from the door to the potting bench; the beds on both sides are raised dark-oak
	 * planters (farmland at y1, crops at y2) with a waterlogged-slab trough set inside each wide bed; a
	 * nursery row of pots by the door on houses 8+ wide and 9+ deep; the potting bench along the back.
	 */
	private static void inside(CitadelCanvas c, Plot p, G g) {
		int vi0 = g.vf + 1;
		int vi1 = g.vb - 1;
		boolean bench = vi1 - vi0 >= 2;
		boolean nursery = g.w >= 8 && vi1 - vi0 >= 5 && g.kinds.length == 3;
		int vs = vi0 + (nursery ? 1 : 0);
		int ve = bench ? vi1 - 1 : vi1;
		int nrows = ve - vs + 1;
		BlockState farmland = b(Blocks.FARMLAND).setValue(BlockStateProperties.MOISTURE, 7);
		BlockState trough = slab(Blocks.DARK_OAK_SLAB).setValue(BlockStateProperties.WATERLOGGED, true);
		int aislesSeen = 0;
		for (int z = 0; z < g.kinds.length; z++) {
			int kind = g.kinds[z];
			int len = g.widths[z];
			boolean left = g.u0[z] + len / 2.0 < g.w / 2.0;
			boolean centre = g.kinds.length == 5 && z == 2;
			int aisleNo = kind == AISLE ? aislesSeen++ : 0;
			for (int j = 0; j < len; j++) {
				int u = g.u0[z] + j;
				int outer = left ? j : len - 1 - j; // 0 at the wall side
				for (int v = vi0; v <= vi1; v++) {
					if (kind == AISLE) {
						boolean step = g.vf == 0 && v == vi0 && g.doorCol(u);
						BlockState floor = p.hash(u, 0, v, 3) == 0 ? b(Blocks.PACKED_MUD) : b(Blocks.MUD_BRICKS);
						p.set(c, u, 0, v, step ? stairs(Blocks.MUD_BRICK_STAIRS, Direction.SOUTH) : floor);
						if (bench && v == vi1) {
							p.set(c, u, 1, v, aisleItem(j, len, aisleNo));
						}
						continue;
					}
					p.set(c, u, 0, v, p.hash(u, 0, v, 5) == 0 ? b(Blocks.STRIPPED_DARK_OAK_LOG) : b(Blocks.DARK_OAK_PLANKS));
					if (bench && v == vi1) {
						p.set(c, u, 1, v, zoneItem(left, centre, outer, len));
					} else if (nursery && v == vi0) {
						p.set(c, u, 1, v, pot(p.hash(u, 1, v, 5)));
					} else {
						int k = v - vs;
						boolean wet = kind == BED && nrows >= 3 && k >= 1 && k <= nrows - 2 && k % 3 == 1 && troughCol(g, z, j, len);
						p.set(c, u, 1, v, wet ? trough : farmland);
						if (!wet) {
							p.set(c, u, 2, v, crop(p.hash(z, 1, k / 2, 4)));
						}
					}
				}
			}
		}
		// invisible light so the crops grow at night too (the lanterns are the visible source)
		int lv = (vi0 + vi1) / 2;
		p.set(c, g.cu, 3, lv, CitadelCanvas.invisibleLight(12));
	}

	/** Beds 3+ wide: the middle column is the trough; 2 wide: the wall-side column; 1 wide: none. */
	private static boolean troughCol(G g, int z, int j, int len) {
		if (len >= 3) {
			return j == (len - 1) / 2;
		}
		if (len == 2) {
			return g.u0[z] == 1 ? j == 0 : j == 1;
		}
		return false;
	}

	/** The potting bench in the aisle: transplant table (crafting table, composter, barrel of soil). */
	private static BlockState aisleItem(int j, int len, int aisleNo) {
		BlockState composter = b(Blocks.COMPOSTER).setValue(BlockStateProperties.LEVEL_COMPOSTER, 5);
		BlockState soil = b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
		BlockState table = b(Blocks.CRAFTING_TABLE);
		if (len == 1) {
			return table;
		}
		boolean first = aisleNo == 0;
		if (j == 0) {
			return first ? composter : table;
		}
		if (j == len - 1) {
			return first ? (len >= 3 ? soil : table) : composter;
		}
		return table;
	}

	/** Bench cells beside the aisle: seed chests by the walls, barrels between. */
	private static BlockState zoneItem(boolean left, boolean centre, int outer, int len) {
		BlockState barrel = b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
		if (centre) {
			return barrel;
		}
		if (outer == 0) {
			return b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
		}
		return barrel;
	}

	// ------------------------------------------------------------------ lanterns

	/** [finish] copper lanterns hanging over the aisle, from the ridge (or from the monitor cap). */
	private static void lanterns(CitadelCanvas c, Plot p, G g) {
		BlockState lantern = Blocks.COPPER_LANTERN.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.HANGING, true);
		int y = g.lant ? g.rh + 2 : g.rh - 1;
		int vmin = g.lant ? g.mv0 + 1 : g.hip ? g.mv0 : g.vf + 1;
		int vmax = g.lant ? g.mv1 - 1 : g.hip ? g.mv1 : g.vb - 1;
		int len = vmax - vmin + 1;
		if (len < 1) {
			return;
		}
		int n = Math.max(1, Math.min(3, len / 3));
		for (int i = 0; i < n; i++) {
			int v = vmin + (2 * i + 1) * len / (2 * n);
			p.put(c, g.cu, y, v, lantern, CitadelCanvas.HANGING);
			p.set(c, g.cu, y - 1, v, CitadelCanvas.invisibleLight(13));
		}
	}

	// ------------------------------------------------------------------ apron (v0)

	/** [finish] the transition to the ground: wide steps to the door, potted plants, a rain barrel and a cauldron. */
	private static void apron(CitadelCanvas c, Plot p, G g) {
		int w = g.w;
		for (int u = 0; u < w; u++) {
			BlockState st;
			boolean porchPost = w >= 7 && (u == g.dl - 2 || u == g.dl + g.dw + 1);
			if (u >= g.dl - 1 && u <= g.dl + g.dw) {
				st = stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH);
			} else if (porchPost) {
				st = b(Blocks.POLISHED_DEEPSLATE);
				for (int y = 1; y <= 3; y++) {
					p.set(c, u, y, 0, post(p, u, 0, false));
				}
			} else if (u == g.dl - 2 || u == g.dl + g.dw + 1) {
				st = pot(p.hash(u, 0, 0, 5));
			} else if (u == 0) {
				st = b(Blocks.WATER_CAULDRON).setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3);
			} else if (u == w - 1) {
				st = b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
			} else {
				st = p.hash(u, 0, 0, 3) == 0 ? b(Blocks.MOSS_CARPET) : slab(Blocks.POLISHED_DEEPSLATE_SLAB);
			}
			p.set(c, u, 0, 0, st);
			if (w >= 7 && u >= g.dl - 2 && u <= g.dl + g.dw + 1) {
				p.set(c, u, PLATE, 0, slab(SLAB_O)); // [depth] a small copper hood over the steps
			}
		}
	}
}
