package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.door;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.hanging;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.log;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.pane;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.trap;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.vine;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.house.TerraceKit.Links;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A hillside house with a real waterfall. A mossy retaining wall holds a raised terrace; a two-storey
 * house stands on it (kitchen and living room below, bedroom and study above, 4 free blocks per floor).
 * The water has a visible origin and a use: a rain chain brings the roof's water down into a stone
 * cistern against the house, a spout lets it fall into a stone channel that crosses the terrace (a slab
 * bridge crosses it), the channel irrigates a bed of crops, and the water falls over the lip of the
 * retaining wall (one jump into a pond, or two: through a washing trough) into a lily pond at the foot.
 * A 2-wide stair climbs along the side to the terrace. The pond and the cascade never leave the plot.
 *
 * <pre>
 *  side section, front on the left                      roof (tile, brick or mud brick), ridge, chimney
 *                                          ____/\____
 *   rain chain ┐  cistern ▒ spout            │ upper  │  bedroom, study (y9..12)
 *   pond ▒▒ ╲ falls    channel ▒▒▒▒▒ crops   ├────────┤
 *   ▒▒▒▒▒▒▒ ║  retaining wall (y0..3)       │ lower  │  kitchen, living room (y4..7)
 *   stair ▁▂▃▅ ║                             └────────┘
 * </pre>
 *
 * Size: 14–24 wide × 14–26 deep × 21+ high. The seed picks the side of the water, one or two jumps and the
 * roof material.
 */
public final class HouseTerraceCascade implements Variant {
	private static final int DECK = 3; // top layer of the platform; the terrace and the ground floor stand on y4
	private static final int G1 = 4; // feet level of the terrace and the ground floor
	private static final int SLAB = 8; // layer of the upper floor (the ceiling of the ground floor)
	private static final int G2 = 9; // feet level of the upper floor
	private static final int PLATE = 13; // plate beams; the roof starts here

	@Override
	public String id() {
		return "house_terrace_cascade";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 14;
	}

	@Override
	public int minD() {
		return 14;
	}

	@Override
	public int minH() {
		return 21;
	}

	@Override
	public int maxW() {
		return 24;
	}

	@Override
	public int maxD() {
		return 26;
	}

	// ------------------------------------------------------------------ the plan

	/** The plan of one build, in local terms with the water on the right (a flip mirrors it on the canvas). */
	private static final class G {
		final int w;
		final int d;
		final int h;
		final int jumps; // 1 or 2
		final int roof; // 0 deepslate tile, 1 brick, 2 mud brick
		final int cu; // the column of the channel and the fall
		final int gd; // v of the retaining wall's front face
		final int bd; // rows of the washing trough (two jumps)
		final int vr; // the row of the trough's front rim (two jumps)
		final int t; // rows of terrace in front of the house
		final int hu0;
		final int hu1;
		final int hv0;
		final int hv1;
		final int wh;
		final int dh;
		final int ud; // the door's column
		final int pa; // pond u range
		final int pb;
		final int iu0;
		final int iu1;
		final int iv0;
		final int iv1;
		final boolean part;
		final int pw; // partition column
		final int cv; // chimney row

		G(Plot p, int jumps, int roof) {
			w = p.w();
			d = p.d();
			h = p.h();
			this.jumps = jumps;
			this.roof = roof;
			gd = d <= 15 ? 4 : d <= 19 ? 5 : 6;
			bd = gd == 4 ? 1 : 2;
			vr = gd - bd - 1;
			int cap = Math.min(Math.min(d - gd - 3, 2 * (h - 17)), 11);
			dh = Math.max(cap, 7);
			t = d - gd - dh;
			hv0 = gd + t;
			hv1 = d - 1;
			wh = Math.min(w - 4, 16);
			hu1 = w - 3;
			hu0 = hu1 - wh + 1;
			ud = hu0 + wh / 2 - 1;
			cu = w - 6;
			pb = cu + 2;
			pa = Math.max(4, cu - 7);
			iu0 = hu0 + 1;
			iu1 = hu1 - 1;
			iv0 = hv0 + 1;
			iv1 = hv1 - 1;
			part = iu1 - iu0 + 1 >= 12;
			pw = iu0 + 4;
			cv = hv0 + 2;
		}

		int roofY(int v) {
			return Math.min(PLATE + (v - (hv0 - 1)), PLATE + 1 + (hv1 - v));
		}

		int top() {
			int m = 0;
			for (int v = hv0 - 1; v <= hv1; v++) {
				m = Math.max(m, roofY(v));
			}
			return m;
		}
	}

	/** Writes through the plot, mirroring u (and the block states) when the water goes on the left. */
	private static final class X {
		final CitadelCanvas c;
		final Plot p;
		final boolean flip;
		final int w;

		X(CitadelCanvas c, Plot p, boolean flip) {
			this.c = c;
			this.p = p;
			this.flip = flip;
			this.w = p.w();
		}

		int mu(int u) {
			return flip ? w - 1 - u : u;
		}

		void put(int u, int y, int v, BlockState s, int layer) {
			p.put(c, mu(u), y, v, flip ? s.mirror(Mirror.FRONT_BACK) : s, layer);
		}

		void set(int u, int y, int v, BlockState s) {
			put(u, y, v, s, CitadelCanvas.SOLID);
		}

		boolean free(int u, int y, int v) {
			BlockState s = p.at(c, mu(u), y, v);
			return s == null || s.isAir();
		}

		int hash(int u, int y, int v, int n) {
			return p.hash(u, y, v, n);
		}

		/** [palette] a deterministic texture mix: repeat an entry to weigh it. */
		BlockState mix(int u, int y, int v, Block... blocks) {
			return b(blocks[hash(u, y, v, blocks.length)]);
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		boolean flip = p.hash(3, 3, 3, 2) == 1;
		int jumps = 1 + p.hash(4, 1, 6, 2);
		int roof = p.hash(1, 1, 7, 3);
		G g = new G(p, jumps, roof);
		X x = new X(c, p, flip);
		platform(x, g);
		climb(x, g);
		pond(x, g);
		terrace(x, g);
		house(x, g);
		spring(x, g);
		floors(x, g);
		partition(x, g);
		stairs(x, g);
		interior(x, g);
		upstairs(x, g);
		roof(x, g);
		chimney(x, g);
		sides(x, g);
		yard(x, g);
		margins(x, g);
	}

	// ------------------------------------------------------------------ materials

	private static BlockState foot(X x, int u, int y, int v) {
		return x.mix(u, y, v, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLED_DEEPSLATE);
	}

	/** Stone brick with moss kept to the lower courses. */
	private static BlockState wallStone(X x, int u, int y, int v) {
		if (y <= 1) {
			return x.mix(u, y, v, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS);
		}
		return x.mix(u, y, v, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS);
	}

	private static BlockState rim(X x, int u, int y, int v) {
		return x.mix(u, y, v, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.COBBLESTONE);
	}

	private static BlockState plaster(X x, int u, int y, int v) {
		return x.mix(u, y, v, Blocks.CALCITE, Blocks.CALCITE, Blocks.CALCITE, Blocks.SMOOTH_QUARTZ);
	}

	private static BlockState frame() {
		return log(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
	}

	private static BlockState glass(X x, int u, int v, boolean alongU) {
		return pane(x.hash(u, 5, v, 6) == 0 ? Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK) : Blocks.GLASS_PANE, alongU);
	}

	private static BlockState falling() {
		return Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 8);
	}

	private static BlockState facing(Block blk, Direction f) {
		return b(blk).setValue(BlockStateProperties.HORIZONTAL_FACING, f);
	}

	private static BlockState barrel() {
		return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
	}

	// ------------------------------------------------------------------ the platform: retaining wall and deck (y0..3)

	/**
	 * [palette] cobble and andesite at the foot, stone brick above with the moss kept to the lower courses.
	 * The deck layer (y3) is laid here and re-laid by the terrace and the house.
	 */
	private static void platform(X x, G g) {
		for (int u = 1; u <= g.w - 2; u++) {
			for (int v = g.gd; v <= g.d - 1; v++) {
				x.set(u, 0, v, foot(x, u, 0, v));
				x.set(u, 1, v, x.hash(u, 1, v, 3) == 0 ? foot(x, u, 1, v) : wallStone(x, u, 1, v));
				x.set(u, 2, v, wallStone(x, u, 2, v));
				x.set(u, DECK, v, x.mix(u, DECK, v, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.ANDESITE));
			}
		}
	}

	/**
	 * The stair to the terrace: 2 wide, 4 steps rising along v at the left, the first step met head-on, nothing
	 * above it, a cheek of stone on the water side, and the terrace itself as the landing.
	 */
	private static void climb(X x, G g) {
		int s0 = g.gd - 4;
		for (int k = 0; k <= 3; k++) {
			int v = s0 + k;
			for (int u = 1; u <= 2; u++) {
				for (int y = 0; y < k; y++) {
					x.set(u, y, v, wallStone(x, u, y, v));
				}
				x.set(u, k, v, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
			}
			for (int y = 0; y <= k; y++) {
				x.set(3, y, v, y == 0 ? foot(x, 3, y, v) : wallStone(x, 3, y, v));
			}
		}
	}

	// ------------------------------------------------------------------ the pond and the fall (y0..4, v < gd)

	/**
	 * [finish] a lily pond in a kerb of stone, held between two pillars where the water arrives. With one jump
	 * the water falls straight from the lip of the wall into it; with two it first fills a washing trough
	 * on the wall (a lavoir) that spills over its front rim.
	 */
	private static void pond(X x, G g) {
		int cu = g.cu;
		int vp = g.jumps == 1 ? g.gd - 1 : g.vr;
		for (int u = g.pa - 1; u <= g.pb + 1; u++) {
			x.set(u, 0, 0, rim(x, u, 0, 0));
		}
		for (int v = 1; v <= g.gd - 1; v++) {
			x.set(g.pa - 1, 0, v, rim(x, g.pa - 1, 0, v));
			x.set(g.pb + 1, 0, v, rim(x, g.pb + 1, 0, v));
		}
		for (int u = g.pa; u <= g.pb; u++) {
			for (int v = 1; v <= vp; v++) {
				x.set(u, 0, v, b(Blocks.WATER));
				if (u != cu && x.hash(u, 1, v, 4) == 0) {
					x.set(u, 1, v, b(Blocks.LILY_PAD));
				}
			}
		}
		// ferns and shrubs on the front kerb, the pond's only soft edge
		for (int u = g.pa; u <= g.pb; u += 2) {
			if (x.hash(u, 1, 0, 3) == 0) {
				x.set(u, 1, 0, b(Blocks.FERN));
			} else if (x.hash(u, 1, 0, 3) == 1) {
				x.set(u, 1, 0, b(Blocks.FLOWERING_AZALEA));
			}
		}
		if (g.jumps == 1) {
			for (int y = 0; y <= 3; y++) {
				x.set(cu - 1, y, g.gd - 1, y == 0 ? foot(x, cu - 1, y, g.gd - 1) : wallStone(x, cu - 1, y, g.gd - 1));
				x.set(cu + 1, y, g.gd - 1, y == 0 ? foot(x, cu + 1, y, g.gd - 1) : wallStone(x, cu + 1, y, g.gd - 1));
			}
			x.set(cu - 1, 4, g.gd - 1, slab(Blocks.STONE_BRICK_SLAB));
			x.set(cu + 1, 4, g.gd - 1, slab(Blocks.STONE_BRICK_SLAB));
			x.set(cu - 1, 5, g.gd - 1, b(Blocks.LANTERN));
			x.set(cu + 1, 5, g.gd - 1, b(Blocks.LANTERN));
			for (int y = 1; y <= 3; y++) {
				x.set(cu, y, g.gd - 1, falling());
			}
			return;
		}
		// two jumps: the trough (y2 water on a stone base), its cheeks, the spill notch, the planters beside it
		for (int v = g.vr + 1; v <= g.gd - 1; v++) {
			for (int u = g.pa; u <= g.pb; u++) {
				int du = Math.abs(u - cu);
				if (du <= 1) {
					x.set(u, 0, v, foot(x, u, 0, v));
					x.set(u, 1, v, wallStone(x, u, 1, v));
					x.set(u, 2, v, b(Blocks.WATER));
				} else if (du == 2) {
					for (int y = 0; y <= 2; y++) {
						x.set(u, y, v, y == 0 ? foot(x, u, y, v) : wallStone(x, u, y, v));
					}
					x.set(u, 3, v, slab(Blocks.STONE_BRICK_SLAB));
				} else {
					x.set(u, 0, v, rim(x, u, 0, v));
					x.set(u, 1, v, x.hash(u, 1, v, 2) == 0 ? b(Blocks.AZALEA) : b(Blocks.FLOWERING_AZALEA));
				}
			}
		}
		x.set(cu - 2, 4, g.gd - 1, b(Blocks.LANTERN));
		x.set(cu + 2, 4, g.gd - 1, b(Blocks.LANTERN));
		for (int s : new int[] {-1, 1}) {
			x.set(cu + s, 0, g.vr, foot(x, cu + s, 0, g.vr));
			x.set(cu + s, 1, g.vr, wallStone(x, cu + s, 1, g.vr));
			x.set(cu + s, 2, g.vr, slab(Blocks.STONE_BRICK_SLAB));
		}
		x.set(cu, 2, g.vr, b(Blocks.WATER));
		x.set(cu, 1, g.vr, falling());
		x.set(cu, 3, g.gd - 1, falling());
	}

	// ------------------------------------------------------------------ the terrace (y3 deck, y4 and up)

	/**
	 * [depth] the terrace: paved path from the stair to the door, lawn and flowers, a slab kerb along the edge
	 * with lantern posts, hedges on the sides, and the water: a channel sunk in the deck from a stone cistern
	 * (fed by a rain chain from the eave) to the lip of the wall, with kerbs, a slab bridge and a bed of crops
	 * it irrigates.
	 */
	private static void terrace(X x, G g) {
		int cu = g.cu;
		int vEnd = g.hv0 - 1;
		for (int u = 1; u <= g.w - 2; u++) {
			for (int v = g.gd; v <= vEnd; v++) {
				boolean path = v == g.gd || u <= 2 || (v == g.gd + 1 && u <= g.ud + 1) || Math.abs(u - g.ud) <= 1;
				if (path) {
					x.set(u, DECK, v, x.mix(u, DECK, v, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE));
					continue;
				}
				x.set(u, DECK, v, x.mix(u, DECK, v, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.COARSE_DIRT));
				int k = x.hash(u, DECK, v, 12);
				BlockState plant = switch (k) {
					case 0 -> b(Blocks.PINK_PETALS);
					case 1 -> b(Blocks.ALLIUM);
					case 2 -> b(Blocks.CORNFLOWER);
					case 3 -> b(Blocks.POPPY);
					case 4, 5 -> b(Blocks.SHORT_GRASS);
					case 6 -> b(Blocks.FERN);
					default -> null;
				};
				if (plant != null) {
					x.set(u, G1, v, plant);
				}
			}
		}
		// the edge of the terrace: a low kerb of slabs (walkable), with posts and lanterns
		int[] posts = {3, cu - 2, cu + 2, g.w - 2};
		for (int u = 3; u <= g.w - 2; u++) {
			boolean post = false;
			for (int pu : posts) {
				post |= pu == u;
			}
			if (post) {
				x.set(u, G1, g.gd, b(Blocks.POLISHED_ANDESITE));
				x.set(u, G1 + 1, g.gd, b(Blocks.LANTERN));
			} else {
				x.set(u, G1, g.gd, slab(Blocks.STONE_BRICK_SLAB));
			}
		}
		// the channel, from the spout down to the lip, kerbed in stone; a slab bridge where the terrace is deep enough
		int vc = g.hv0 - 1;
		int spout = vc;
		boolean bridge = g.t >= 4;
		int vb = g.gd + 1;
		for (int v = g.gd; v <= spout; v++) {
			x.set(cu, DECK, v, b(Blocks.WATER));
			if (v > g.gd) {
				if (bridge && v == vb) {
					x.set(cu, G1, v, slab(Blocks.STONE_BRICK_SLAB));
				} else {
					x.set(cu - 1, G1, v, slab(Blocks.MOSSY_STONE_BRICK_SLAB));
					x.set(cu + 1, G1, v, slab(Blocks.STONE_BRICK_SLAB));
				}
			}
		}
		// the spring: a stone mask on the house wall (see spring()) pours into the channel; a rain chain from the
		// eave feeds it. The water falls two blocks from the mask into the head of the channel.
		x.set(cu, G1 + 1, vc, b(Blocks.WATER));
		x.set(cu, G1, vc, falling());
		for (int y = G1 + 2; y <= PLATE - 1; y++) {
			x.put(cu, y, vc, b(Blocks.IRON_CHAIN), CitadelCanvas.HANGING);
		}
		// a lavoir off the channel: a pocket of water with a sloped washing stone and a barrel beside it
		if (g.t >= 4) {
			int lr = spout - 1;
			x.set(cu - 1, DECK, lr, b(Blocks.WATER));
			x.set(cu - 1, G1, lr, AIR);
			x.set(cu - 2, G1, lr, stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST, false));
			x.set(cu - 2, G1, lr + 1, barrel());
		}
		if (cu - 2 > g.ud + 1) {
			x.set(cu - 2, G1, vc, barrel());
		}
		// the irrigated bed right of the channel
		for (int u = cu + 2; u <= g.w - 3; u++) {
			for (int v = g.gd + 1; v <= vEnd; v++) {
				x.set(u, DECK, v, b(Blocks.FARMLAND).setValue(BlockStateProperties.MOISTURE, 7));
				Block crop = switch (x.hash(u, 2, v, 3)) {
					case 0 -> Blocks.WHEAT;
					case 1 -> Blocks.CARROTS;
					default -> Blocks.POTATOES;
				};
				x.set(u, G1, v, b(crop).setValue(BlockStateProperties.AGE_7, 3 + x.hash(u, 1, v, 5)));
			}
		}
		// lamps either side of the door and a bench against the wall where there is room
		for (int s : new int[] {-2, 2}) {
			int lu = g.ud + s;
			if (Math.abs(lu - cu) <= 1) {
				continue;
			}
			x.set(lu, G1, vc, b(Blocks.MOSSY_STONE_BRICK_WALL));
			x.set(lu, G1 + 1, vc, b(Blocks.MOSSY_STONE_BRICK_WALL));
			x.set(lu, G1 + 2, vc, b(Blocks.LANTERN));
		}
		// a hedge on the left edge of the terrace, flowers on the right (a hedge there would be a ledge over the crops)
		for (int v = g.gd + 1; v <= vEnd; v++) {
			x.set(1, G1, v, leaves(x.hash(1, G1, v, 3) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
			x.set(g.w - 2, DECK, v, b(Blocks.MOSS_BLOCK));
			x.set(g.w - 2, G1, v, x.hash(g.w - 2, G1, v, 2) == 0 ? b(Blocks.PINK_PETALS) : b(Blocks.CORNFLOWER));
		}
		// on a deep terrace: a cherry on the left lawn, and room to sit under it
		if (g.t >= 6 && g.ud >= 9) {
			cherry(x, 5, g.gd + 3);
			x.set(7, G1, g.gd + 3, stair(Blocks.DARK_OAK_STAIRS, Direction.WEST, false));
		}
	}

	/** A cherry tree: a 5-high trunk, the crown starting 4 blocks over the feet so the cells beneath stay roomy. */
	private static void cherry(X x, int tu, int tv) {
		for (int y = G1; y <= G1 + 4; y++) {
			x.set(tu, y, tv, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
		}
		for (int dy = 0; dy <= 3; dy++) {
			int rad = dy == 0 || dy == 3 ? 1 : 2;
			for (int du = -rad; du <= rad; du++) {
				for (int dv = -rad; dv <= rad; dv++) {
					if (rad == 2 && Math.abs(du) == 2 && Math.abs(dv) == 2) {
						continue;
					}
					if (du == 0 && dv == 0 && dy <= 1) {
						continue;
					}
					x.set(tu + du, G1 + 4 + dy, tv + dv, leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
	}

	// ------------------------------------------------------------------ the house (y4..13)

	/** 0 plain, 1 post, 2 window (u or v is the position along the wall; kc the centre). */
	private static int bay(int k, int kc, int len, boolean front) {
		if (k == 0 || k == len - 1) {
			return 1;
		}
		int dk = Math.abs(k - kc);
		if (front) {
			if (dk % 4 == 1) {
				return 1;
			}
			return dk % 4 == 3 ? 2 : 0;
		}
		if (dk % 4 == 0) {
			return 1;
		}
		return dk % 4 == 2 ? 2 : 0;
	}

	/**
	 * [depth] timber frame on a stone plinth: dark oak posts, calcite panels, a dark oak band at the floor
	 * and plate beams, 2-high windows with shutters, corbels under the eave, a recessed door with a transom.
	 */
	private static void house(X x, G g) {
		int kd = g.ud - g.hu0;
		for (int u = g.hu0; u <= g.hu1; u++) {
			for (int v = g.hv0; v <= g.hv1; v++) {
				if (!(u == g.hu0 || u == g.hu1 || v == g.hv0 || v == g.hv1)) {
					continue;
				}
				boolean side = u == g.hu0 || u == g.hu1;
				boolean front = v == g.hv0;
				boolean isDoor = front && u == g.ud;
				int kind;
				if (side) {
					kind = bay(v - g.hv0, g.dh / 2, g.dh, false);
				} else if (front) {
					kind = bay(u - g.hu0, kd, g.wh, true);
				} else {
					kind = bay(u - g.hu0, g.wh / 2, g.wh, false);
				}
				boolean corner = side && (v == g.hv0 || v == g.hv1);
				if (corner) {
					kind = 1;
				}
				if (isDoor) {
					kind = 0;
				}
				if (kind == 2 && front && Math.abs(u - g.cu) <= 1) {
					kind = 0;
				}
				if (kind == 2 && u == g.hu0 && v >= g.cv - 1 && v <= g.cv + 2) {
					kind = 0;
				}
				boolean post = kind == 1;
				boolean win = kind == 2;
				for (int y = G1; y <= PLATE; y++) {
					BlockState st;
					if (y == PLATE || y == SLAB) {
						st = log(Blocks.DARK_OAK_LOG, side ? Direction.Axis.Z : Direction.Axis.X);
						if (post && y == SLAB) {
							st = frame();
						}
					} else if (post) {
						st = frame();
					} else if (y == G1) {
						if (isDoor) {
							continue;
						}
						st = foot(x, u, y, v);
					} else if (isDoor && y <= G1 + 3) {
						if (y == G1 + 1) {
							continue;
						}
						st = y == G1 + 2 ? glass(x, u, v, true) : log(Blocks.DARK_OAK_LOG, Direction.Axis.X);
					} else if (win && (y == G1 + 1 || y == G1 + 2 || y == G2 + 1 || y == G2 + 2)) {
						st = glass(x, u, v, !side);
					} else if (isDoor && (y == G2 + 1 || y == G2 + 2)) {
						st = glass(x, u, v, true);
					} else {
						st = plaster(x, u, y, v);
					}
					x.set(u, y, v, st);
				}
				if (front) {
					if (isDoor) {
						x.set(u, G1, v, door(Blocks.DARK_OAK_DOOR, Direction.NORTH, true));
						x.set(u, G1 + 1, v, door(Blocks.DARK_OAK_DOOR, Direction.NORTH, false));
						continue;
					}
					// shutters beside the windows and a corbel under the eave at every post
					if (win) {
						for (int s : new int[] {-1, 1}) {
							int su = u + s;
							if (Math.abs(su - g.cu) <= 2 || (Math.abs(su - g.ud) <= 1)) {
								continue;
							}
							x.set(su, G1 + 1, v - 1, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, false));
							x.set(su, G1 + 2, v - 1, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, false));
							x.set(su, G2 + 1, v - 1, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, false));
							x.set(su, G2 + 2, v - 1, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, false));
						}
					}
					if (post && u != g.cu) {
						x.set(u, PLATE - 1, v - 1, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, true));
						if (Math.abs(u - g.ud) == 1 && Math.abs(u - g.cu) > 1) {
							x.put(u, PLATE - 2, v - 1, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
						}
					}
				}
			}
		}
	}

	/** [depth] the spring: a carved mask in the wall, a keystone over it; the water leaves its mouth into the channel. */
	private static void spring(X x, G g) {
		int u = g.cu;
		x.set(u, G1 + 1, g.hv0, b(Blocks.CHISELED_STONE_BRICKS));
		x.set(u, G1 + 2, g.hv0, b(Blocks.POLISHED_ANDESITE));
		x.set(u - 1, G1 + 1, g.hv0, wallStone(x, u - 1, G1 + 1, g.hv0));
		x.set(u + 1, G1 + 1, g.hv0, wallStone(x, u + 1, G1 + 1, g.hv0));
		x.set(u, G1, g.hv0, b(Blocks.POLISHED_ANDESITE));
	}

	/** The floors: planks on the deck, and the slab that is the ceiling below and the floor above. */
	private static void floors(X x, G g) {
		for (int u = g.iu0; u <= g.iu1; u++) {
			for (int v = g.iv0; v <= g.iv1; v++) {
				x.set(u, DECK, v, x.mix(u, DECK, v, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS));
				x.set(u, SLAB, v, x.mix(u, SLAB, v, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS));
			}
		}
	}

	/** A partition in the wider houses: kitchen (left) from living room, bedroom from study, a 3-wide opening at the front. */
	private static void partition(X x, G g) {
		if (!g.part) {
			return;
		}
		for (int v = g.iv0; v <= g.iv1; v++) {
			boolean open = v <= g.iv0 + 2;
			for (int y : new int[] {G1, G1 + 1, G1 + 2, G1 + 3, G2, G2 + 1, G2 + 2, G2 + 3}) {
				boolean lintel = y == G1 + 3 || y == G2 + 3;
				if (open && !lintel) {
					continue;
				}
				x.set(g.pw, y, v, lintel && open ? log(Blocks.DARK_OAK_LOG, Direction.Axis.Z) : y == G1 ? foot(x, g.pw, y, v) : plaster(x, g.pw, y, v));
			}
		}
		x.set(g.pw, G1, g.iv0 + 3, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
		x.set(g.pw, G2, g.iv0 + 3, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
		// closed up to the roof, so there is no ledge on top of it
		for (int v = g.iv0; v <= g.iv1; v++) {
			for (int y = PLATE; y < g.roofY(v); y++) {
				x.set(g.pw, y, v, plaster(x, g.pw, y, v));
			}
		}
	}

	/**
	 * The stair to the upper floor: one flight of 5 along the back wall, rising towards the left, a cut in the
	 * floor over it with a rail, approached head-on from the room and stepping off onto the upper floor.
	 */
	private static void stairs(X x, G g) {
		int su = g.iu1 - 1;
		int v = g.iv1;
		for (int j = 0; j <= 4; j++) {
			int u = su - j;
			for (int y = G1; y < G1 + j; y++) {
				x.set(u, y, v, b(Blocks.SPRUCE_PLANKS));
			}
			x.set(u, G1 + j, v, stair(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
			if (j < 4) {
				x.set(u, SLAB, v, AIR);
			}
		}
		Links rail = new Links();
		for (int u = su - 3; u <= su; u++) {
			rail.add(u, v - 1);
		}
		rail.add(g.iu1, v);
		rail.partner(g.iu1, v - 1);
		rail.partner(g.iu1 + 1, v);
		for (int u = su - 3; u <= su; u++) {
			x.set(u, G2, v - 1, rail.fence(Blocks.DARK_OAK_FENCE, u, v - 1));
		}
		x.set(g.iu1, G2, v, rail.fence(Blocks.DARK_OAK_FENCE, g.iu1, v));
	}

	/** Ground floor: kitchen along the left wall (counter, ranges, sink), living room with a table, a runner, lanterns. */
	private static void interior(X x, G g) {
		int lw = g.iu0;
		for (int v = g.iv0; v <= g.iv1; v++) {
			int k = v - g.iv0;
			BlockState st = switch (k % 5) {
				case 0 -> b(Blocks.CRAFTING_TABLE);
				case 1 -> facing(Blocks.FURNACE, Direction.EAST).setValue(BlockStateProperties.LIT, true);
				case 2 -> facing(Blocks.SMOKER, Direction.EAST).setValue(BlockStateProperties.LIT, true);
				case 3 -> b(Blocks.WATER_CAULDRON);
				default -> barrel();
			};
			x.set(lw, G1, v, st);
		}
		x.set(lw + 1, G1, g.iv1, facing(Blocks.CHEST, Direction.NORTH));
		// the living room's library: shelves from floor to ceiling along the right wall
		for (int v = g.iv0; v <= g.iv1 - 2; v++) {
			for (int y = G1; y <= G1 + 3; y++) {
				x.set(g.iu1, y, v, b(Blocks.BOOKSHELF));
			}
		}
		int su0 = g.part ? g.pw + 1 : g.iu0 + 1;
		int tu = su0 + 2;
		int tv = g.iv0 + 2;
		x.set(tu, G1, tv, slab(Blocks.SPRUCE_SLAB));
		x.set(tu + 1, G1, tv, slab(Blocks.SPRUCE_SLAB));
		x.set(tu, G1, tv - 1, stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
		x.set(tu + 1, G1, tv - 1, stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
		x.set(tu, G1, tv + 1, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
		x.set(tu + 1, G1, tv + 1, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
		// a runner from the door, lanterns from the ceiling
		x.set(g.ud, G1, g.iv0, b(Blocks.CARPET.pick(DyeColor.RED)));
		int ku = g.iu0 + 1;
		x.put(ku, G1 + 3, g.iv0 + 2, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
		x.put(tu - 1, G1 + 3, tv, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
	}

	/** Upper floor: bedroom (bed, chest, wardrobe) on the left, study (shelves, lectern, desk) on the right. */
	private static void upstairs(X x, G g) {
		int u0 = g.iu0;
		BlockState bed = b(Blocks.BED.pick(DyeColor.RED)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
		x.set(u0, G2, g.iv1 - 1, bed.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		x.set(u0, G2, g.iv1, bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		x.set(u0, G2, g.iv0, facing(Blocks.CHEST, Direction.EAST));
		x.set(u0, G2, g.iv0 + 1, barrel());
		x.set(u0 + 1, G2, g.iv1 - 1, slab(Blocks.SPRUCE_SLAB));
		x.set(u0 + 1, G2 + 1, g.iv1 - 1, b(Blocks.LANTERN));
		if (g.part) {
			BlockState bed2 = b(Blocks.BED.pick(DyeColor.BLUE)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
			x.set(u0 + 3, G2, g.iv1 - 1, bed2.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
			x.set(u0 + 3, G2, g.iv1, bed2.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		}
		// the study on the right, in furniture no taller than a step: a bookcase and a desk with a lectern and a map table
		int ru = g.iu1;
		for (int v = g.iv0; v <= g.iv1 - 1; v++) {
			x.set(ru, G2, v, v % 2 == 0 ? b(Blocks.BOOKSHELF) : facing(Blocks.CHISELED_BOOKSHELF, Direction.WEST));
		}
		x.set(ru, G2 + 1, g.iv0 + 1, b(Blocks.LANTERN));
		x.set(ru - 1, G2, g.iv0, facing(Blocks.LECTERN, Direction.SOUTH).setValue(BlockStateProperties.HAS_BOOK, true));
		x.set(ru - 2, G2, g.iv0, b(Blocks.CARTOGRAPHY_TABLE));
		x.set(ru - 3, G2, g.iv0, slab(Blocks.SPRUCE_SLAB));
		x.set(ru - 3, G2 + 1, g.iv0, b(Blocks.LANTERN));
		x.set(ru - 2, G2, g.iv0 + 1, stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
		x.set(ru - 1, G2, g.iv0 + 1, b(Blocks.CARPET.pick(DyeColor.RED)));
	}

	// ------------------------------------------------------------------ the roof (y13+)

	/**
	 * [finish] gable roof, ridge along u, in tile, brick or mud brick with a contrasting eave line, a capped
	 * ridge, a 1-block overhang at the front and the gables, and plaster gables with a king post and light.
	 */
	private static void roof(X x, G g) {
		Block main;
		Block eaveBlk;
		BlockState mid;
		Block cap;
		switch (g.roof) {
			case 1 -> {
				main = Blocks.BRICK_STAIRS;
				eaveBlk = Blocks.STONE_BRICK_STAIRS;
				mid = b(Blocks.BRICKS);
				cap = Blocks.STONE_BRICK_SLAB;
			}
			case 2 -> {
				main = Blocks.MUD_BRICK_STAIRS;
				eaveBlk = Blocks.DARK_OAK_STAIRS;
				mid = b(Blocks.MUD_BRICKS);
				cap = Blocks.DARK_OAK_SLAB;
			}
			default -> {
				main = Blocks.DEEPSLATE_TILE_STAIRS;
				eaveBlk = Blocks.CHERRY_STAIRS;
				mid = b(Blocks.POLISHED_DEEPSLATE);
				cap = Blocks.CHERRY_SLAB;
			}
		}
		int rv0 = g.hv0 - 1;
		int rv1 = g.hv1;
		int u0 = g.hu0 - 1;
		int u1 = g.hu1 + 1;
		int top = g.top();
		for (int v = rv0; v <= rv1; v++) {
			int a = PLATE + (v - rv0);
			int bb = PLATE + 1 + (rv1 - v);
			int y = Math.min(a, bb);
			boolean frontSlope = a < bb;
			boolean middle = a == bb;
			boolean eaveRow = v == rv0 || v == rv1;
			for (int u = u0; u <= u1; u++) {
				BlockState st = middle ? mid : stair(eaveRow ? eaveBlk : main, frontSlope ? Direction.SOUTH : Direction.NORTH, false);
				x.set(u, y, v, st);
				if ((u == u0 || u == u1) && !middle && y - 1 >= PLATE) {
					x.set(u, y - 1, v, stair(main, frontSlope ? Direction.NORTH : Direction.SOUTH, true));
				}
				if (y == top) {
					x.set(u, top + 1, v, slab(cap));
				}
			}
		}
		// gables: plaster under the slope, a king post in the middle, a pink light either side of it
		int midV = (g.hv0 + g.hv1) / 2;
		for (int u : new int[] {g.hu0, g.hu1}) {
			for (int v = g.hv0; v <= g.hv1; v++) {
				int ry = g.roofY(v);
				for (int y = PLATE + 1; y < ry; y++) {
					BlockState st = v == midV ? frame() : plaster(x, u, y, v);
					if (y == PLATE + 2 && Math.abs(v - midV) == 2) {
						st = pane(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK), false);
					}
					x.set(u, y, v, st);
				}
			}
		}
	}

	/** [depth] an outside chimney stack against the left gable, behind the kitchen range: a broad base, a drip course, a slim flue up past the ridge. */
	private static void chimney(X x, G g) {
		int u = g.hu0 - 1;
		int ctop = Math.min(g.top() + 1, g.h - 2);
		for (int y = G1; y <= ctop; y++) {
			for (int v = g.cv; v <= g.cv + 1; v++) {
				x.set(u, y, v, x.hash(u, y, v, 3) == 0 ? b(Blocks.COBBLESTONE) : b(Blocks.STONE_BRICKS));
			}
		}
		x.set(u, ctop + 1, g.cv, slab(Blocks.STONE_BRICK_SLAB));
		x.set(u, ctop + 1, g.cv + 1, slab(Blocks.STONE_BRICK_SLAB));
	}

	// ------------------------------------------------------------------ sides and margins

	/** The deck beside the house: lawn on the wide plots, hedges at the back and on the platform edge. */
	private static void sides(X x, G g) {
		for (int u = 1; u < g.hu0; u++) {
			for (int v = g.hv0; v <= g.hv1; v++) {
				boolean chim = u == g.hu0 - 1 && v >= g.cv - 1 && v <= g.cv + 2;
				if (chim) {
					continue;
				}
				x.set(u, DECK, v, x.mix(u, DECK, v, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK));
				int k = x.hash(u, DECK, v, 8);
				if (k == 0) {
					x.set(u, G1, v, b(Blocks.PINK_PETALS));
				} else if (k == 1) {
					x.set(u, G1, v, b(Blocks.SHORT_GRASS));
				} else if (k == 2) {
					x.set(u, G1, v, b(Blocks.CORNFLOWER));
				}
			}
		}
		for (int v = g.hv0; v <= g.hv1; v++) {
			x.set(g.w - 2, DECK, v, b(Blocks.MOSS_BLOCK));
			if (x.hash(g.w - 2, G1, v, 3) == 0) {
				x.set(g.w - 2, G1, v, b(Blocks.FERN));
			}
		}
	}

	/**
	 * [finish] the ground in front of the wall: flowers on the left of the pond with a bench facing it, and a
	 * few buttress piers with lanterns along the foot of the wall where there is open ground.
	 */
	private static void yard(X x, G g) {
		for (int u = 4; u <= g.pa - 2; u++) {
			for (int v = 0; v <= g.gd - 1; v++) {
				int k = x.hash(u, 0, v, 6);
				BlockState plant = switch (k) {
					case 0 -> b(Blocks.PINK_PETALS);
					case 1 -> b(Blocks.CORNFLOWER);
					case 2 -> b(Blocks.FERN);
					case 3 -> b(Blocks.ALLIUM);
					default -> null;
				};
				if (plant != null) {
					x.set(u, 0, v, plant);
				}
			}
		}
		if (g.pa - 2 >= 5) {
			x.set(g.pa - 2, 0, 1, stair(Blocks.DARK_OAK_STAIRS, Direction.WEST, false));
			x.set(g.pa - 2, 0, 2, stair(Blocks.DARK_OAK_STAIRS, Direction.WEST, false));
			x.set(g.pa - 2, 0, 0, b(Blocks.MOSSY_STONE_BRICK_WALL));
			x.set(g.pa - 2, 1, 0, b(Blocks.LANTERN));
		}
		for (int u = 5; u <= g.pa - 3; u += 4) {
			pier(x, g, u);
		}
		if (g.pb + 3 <= g.w - 2) {
			pier(x, g, g.pb + 3);
		}
	}

	private static void pier(X x, G g, int u) {
		for (int y = 0; y <= 3; y++) {
			x.set(u, y, g.gd - 1, y == 0 ? foot(x, u, y, g.gd - 1) : wallStone(x, u, y, g.gd - 1));
		}
		x.set(u, 4, g.gd - 1, b(Blocks.LANTERN));
	}

	/** [finish] the two outer columns at the foot of the platform: a path of flowers and ferns, vines up the wall, a lamp post each side. */
	private static void margins(X x, G g) {
		for (int v = 0; v < g.d; v++) {
			for (int u : new int[] {0, g.w - 1}) {
				boolean right = u != 0;
				if (v >= g.gd) {
					int k = x.hash(u, 0, v, 7);
					BlockState plant = switch (k) {
						case 0 -> b(Blocks.FERN);
						case 1 -> b(Blocks.CORNFLOWER);
						case 2 -> b(Blocks.PINK_PETALS);
						case 3 -> b(Blocks.SHORT_GRASS);
						default -> null;
					};
					if (plant != null) {
						x.set(u, 0, v, plant);
					}
					if (x.hash(u, 3, v, 3) == 0) {
						Direction side = right ? Direction.WEST : Direction.EAST;
						x.put(u, 3, v, vine(side), CitadelCanvas.DECOR);
						x.put(u, 2, v, vine(side), CitadelCanvas.DECOR);
					}
				}
			}
		}
		int lv = g.gd + (g.hv0 - g.gd) / 2;
		for (int u : new int[] {0, g.w - 1}) {
			x.set(u, 0, lv, b(Blocks.MOSSY_STONE_BRICK_WALL));
			x.set(u, 1, lv, b(Blocks.MOSSY_STONE_BRICK_WALL));
			x.set(u, 2, lv, b(Blocks.LANTERN));
		}
	}
}
