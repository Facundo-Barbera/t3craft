package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.door;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.hanging;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.log;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.mix;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.pane;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.trap;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.vine;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.house.TerraceKit.Links;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A three-tier stepped house, a green ziggurat: every storey is set back from the one below, so the
 * roof of each storey is a garden terrace with a cherry railing, and the whole thing climbs towards the
 * back. The ground tier is heavy dark stone with a vine-hung porch under an overhanging deck; the
 * middle tier is white plaster in cherry frames with a covered porch of its own; the top tier is a
 * glazed tea-room under a small gable roof of deepslate tiles with a gold-quiet finial.
 *
 * <pre>
 *  side section (front on the left)
 *                         ____/\____      C  glazed tea-room, gable roof (y9..y16)
 *               ┌──garden──┤  C  ├
 *               │ rail  ___|_____|___     B  white plaster, covered porch (y5..y8)
 *      ┌─garden─┤  B  ├
 *      │ rail ___|_____|____              A  dark stone base, porch under the deck (y0..y4)
 *   hedge|  A   |
 * </pre>
 *
 * Size: 11–15 × 12–15 × 17+. Everything is symmetric about the door column.
 */
public final class HouseTerraceTiers implements Variant {
	/** v of the front wall of each tier; the porch row of tier B is v 4. */
	private static final int VA = 2;
	private static final int VB = 5;
	private static final int VC = 8;
	private static final int DECK_A = 4;
	private static final int DECK_B = 8;
	private static final int PLATE = 11;

	@Override
	public String id() {
		return "house_terrace_tiers";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 11;
	}

	@Override
	public int minD() {
		return 12;
	}

	@Override
	public int minH() {
		return 17;
	}

	@Override
	public int maxW() {
		return 15;
	}

	@Override
	public int maxD() {
		return 15;
	}

	/** Half-widths of the three tiers around the door column, and the shared back wall. */
	private record G(int w, int d, int cu, int ha, int hb, int hc, int ve) {
		int a0() {
			return cu - ha;
		}

		int a1() {
			return cu + ha;
		}

		int b0() {
			return cu - hb;
		}

		int b1() {
			return cu + hb;
		}

		int c0() {
			return cu - hc;
		}

		int c1() {
			return cu + hc;
		}

		int rimL() {
			return cu - ha - 1;
		}

		int rimR() {
			return cu + ha + 1;
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x7E44_ACE5L);
		int w = p.w();
		int d = p.d();
		int cu = (w - 1) / 2;
		int ha = Math.min(cu - 1, w - 2 - cu);
		int hb = ha >= 6 ? ha - 2 : ha - 1;
		G g = new G(w, d, cu, ha, hb, 2, d - 2);
		boolean flip = r.nextBoolean();

		bodyA(c, p, g);
		deckA(c, p, g, r);
		porchA(c, p, g, r);
		bodyB(c, p, g);
		deckB(c, p, g, r, flip);
		bodyC(c, p, g);
		roofC(c, p, g);
		yard(c, p, g, r);
		vines(c, p, g);
	}

	// ------------------------------------------------------------------ materials

	private static BlockState darkStone(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.COBBLED_DEEPSLATE);
	}

	private static BlockState midStone(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS);
	}

	private static BlockState plaster(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.CALCITE, Blocks.CALCITE, Blocks.CALCITE, Blocks.QUARTZ_BRICKS);
	}

	private static BlockState paver(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE);
	}

	private static BlockState post() {
		return b(Blocks.STRIPPED_CHERRY_LOG);
	}

	private static BlockState beamX() {
		return log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X);
	}

	private static BlockState glass(Plot p, int u, int v, boolean alongU, boolean accentAllowed) {
		boolean pink = accentAllowed && p.hash(u, 0, v, 3) == 0;
		return pane(pink ? Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK) : Blocks.GLASS_PANE, alongU);
	}

	// ------------------------------------------------------------------ tier A: dark stone base (y0..y3)

	private static BlockState wallA(Plot p, G g, int u, int y, int v) {
		boolean front = v == VA;
		boolean back = v == g.ve();
		boolean side = u == g.a0() || u == g.a1();
		int du = Math.abs(u - g.cu());
		boolean post;
		boolean window;
		if (side) {
			int k = v - VA;
			post = front || back || k % 4 == 0;
			window = !post && k % 4 == 2;
		} else if (front) {
			post = du == 1 || (du >= 4 && du % 2 == 0 && du < g.ha());
			window = du >= 3 && du % 2 == 1 && du < g.ha();
		} else {
			post = du % 4 == 0;
			window = du % 4 == 2;
		}
		if (y == 0) {
			return darkStone(p, u, y, v);
		}
		if (post) {
			return post();
		}
		if (window && y == 2) {
			return glass(p, u, v, front || back, false);
		}
		return y == 1 ? darkStone(p, u, y, v) : midStone(p, u, y, v);
	}

	private static void bodyA(CitadelCanvas c, Plot p, G g) {
		for (int u = g.a0(); u <= g.a1(); u++) {
			for (int v = VA; v <= g.ve(); v++) {
				boolean ring = u == g.a0() || u == g.a1() || v == VA || v == g.ve();
				if (!ring) {
					p.set(c, u, 0, v, b(Blocks.SPRUCE_PLANKS));
					continue;
				}
				for (int y = 0; y <= 3; y++) {
					p.set(c, u, y, v, wallA(p, g, u, y, v));
				}
			}
		}
		// the door: recessed panel, lintel beam over it
		p.set(c, g.cu(), 1, VA, door(Blocks.CHERRY_DOOR, Direction.NORTH, true));
		p.set(c, g.cu(), 2, VA, door(Blocks.CHERRY_DOOR, Direction.NORTH, false));
		p.set(c, g.cu(), 3, VA, beamX());
	}

	// ------------------------------------------------------------------ deck A and its garden (y4 / y5)

	/** The ring of a rectangle (cells on its border), as a predicate. */
	private static boolean onRing(int u, int v, int u0, int u1, int v0, int v1) {
		return u == u0 || u == u1 || v == v0 || v == v1;
	}

	private static void deckA(CitadelCanvas c, Plot p, G g, Random r) {
		int u0 = g.rimL();
		int u1 = g.rimR();
		int v1 = g.d() - 1;
		boolean[][] free = new boolean[g.w()][g.d()];
		for (int u = u0; u <= u1; u++) {
			for (int v = 1; v <= v1; v++) {
				if (onRing(u, v, u0, u1, 1, v1)) {
					p.set(c, u, DECK_A, v, darkStone(p, u, DECK_A, v));
				} else {
					boolean porchRow = v == VB - 1 && u >= g.b0() && u <= g.b1();
					boolean underB = v >= VB && u >= g.b0() && u <= g.b1();
					if (porchRow) {
						p.set(c, u, DECK_A, v, paver(p, u, DECK_A, v));
					} else if (underB) {
						p.set(c, u, DECK_A, v, darkStone(p, u, DECK_A, v));
					} else {
						free[u][v] = true;
					}
				}
			}
		}
		garden(c, p, g, r, DECK_A, free, VB - 1);
		rail(c, p, g, DECK_A + 1, u0, u1, 1, v1, (u, v) -> false);
	}

	/** The porch under the overhanging deck: columns, a coved ceiling, lanterns, planters, vines, steps. */
	private static void porchA(CitadelCanvas c, Plot p, G g, Random r) {
		int cu = g.cu();
		int u0 = g.rimL();
		int u1 = g.rimR();
		int vb = g.d() - 1;
		int mid = g.d() / 2;
		int[] colV = {1, mid, vb};
		// columns under the deck rim: corners and the middle of each side; two beside the door
		for (int u : new int[] {u0, u1}) {
			for (int v : colV) {
				for (int y = 1; y <= 3; y++) {
					p.set(c, u, y, v, post());
				}
			}
		}
		for (int s : new int[] {-2, 2}) {
			for (int y = 1; y <= 3; y++) {
				p.set(c, cu + s, y, 1, post());
			}
		}
		// [depth] cove of upside-down stairs under the overhang, facing the wall
		for (int u = u0; u <= u1; u++) {
			for (int v = 1; v <= vb; v++) {
				if (!onRing(u, v, u0, u1, 1, vb) || p.at(c, u, 3, v) != null) {
					continue;
				}
				Direction f = v == 1 ? Direction.SOUTH : v == vb ? Direction.NORTH : u == u0 ? Direction.EAST : Direction.WEST;
				p.set(c, u, 3, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, f, true));
			}
		}
		// [finish] lanterns on either side of the door
		for (int s : new int[] {-1, 1}) {
			p.put(c, cu + s, 3, 1, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
		}
		// porch floor: half-height slabs, a stair up to the door, planters under the windows
		for (int u = u0 + 1; u < u1; u++) {
			if (p.at(c, u, 1, 1) != null) {
				continue;
			}
			int du = Math.abs(u - cu);
			if (du == 0) {
				p.set(c, u, 0, 1, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
			} else if (du >= 3 && du % 2 == 1 && du < g.ha()) {
				p.set(c, u, 0, 1, b(Blocks.MOSS_BLOCK));
				p.set(c, u, 1, 1, r.nextInt(3) == 0 ? b(Blocks.AZALEA) : b(Blocks.FLOWERING_AZALEA));
			} else {
				p.set(c, u, 0, 1, slab(Blocks.POLISHED_DEEPSLATE_SLAB));
			}
		}
		// [finish] vines hanging from the deck over the stone between the windows
		for (int u = u0 + 1; u < u1; u++) {
			int du = Math.abs(u - cu);
			boolean plain = du >= 2 && !(du >= 3 && du % 2 == 1 && du < g.ha()) && p.at(c, u, 2, 1) == null;
			if (plain && p.hash(u, 1, 1, 3) == 0) {
				p.put(c, u, 2, 1, vine(Direction.SOUTH), CitadelCanvas.DECOR);
				if (p.hash(u, 2, 1, 2) == 0) {
					p.put(c, u, 1, 1, vine(Direction.SOUTH), CitadelCanvas.DECOR);
				}
			}
		}
	}

	// ------------------------------------------------------------------ tier B: plaster and cherry (y5..y7)

	private static BlockState wallB(Plot p, G g, int u, int y, int v) {
		boolean front = v == VB;
		boolean back = v == g.ve();
		boolean side = u == g.b0() || u == g.b1();
		int du = Math.abs(u - g.cu());
		boolean post;
		boolean window;
		if (side) {
			int k = v - VB;
			post = front || back || k % 4 == 0;
			window = !post && k % 4 == 2;
		} else if (front) {
			post = du == 1;
			window = du >= 2 && du % 2 == 0 && du < g.hb();
		} else {
			post = du % 4 == 0;
			window = du % 4 == 2;
		}
		if (post) {
			return post();
		}
		if (window && y == 6) {
			return glass(p, u, v, front || back, true);
		}
		return y == 5 ? midStone(p, u, y, v) : plaster(p, u, y, v);
	}

	private static void bodyB(CitadelCanvas c, Plot p, G g) {
		for (int u = g.b0(); u <= g.b1(); u++) {
			for (int v = VB; v <= g.ve(); v++) {
				if (!onRing(u, v, g.b0(), g.b1(), VB, g.ve())) {
					continue;
				}
				for (int y = 5; y <= 7; y++) {
					p.set(c, u, y, v, wallB(p, g, u, y, v));
				}
			}
		}
		int cu = g.cu();
		p.set(c, cu, 5, VB, door(Blocks.CHERRY_DOOR, Direction.NORTH, true));
		p.set(c, cu, 6, VB, door(Blocks.CHERRY_DOOR, Direction.NORTH, false));
		p.set(c, cu, 7, VB, beamX());
		// [depth] the deck B overhangs the porch row (v 4): two columns, a coved ceiling, lanterns, sills
		for (int u : new int[] {g.b0(), g.b1()}) {
			for (int y = 5; y <= 7; y++) {
				p.set(c, u, y, VB - 1, post());
			}
		}
		for (int u = g.b0() + 1; u < g.b1(); u++) {
			int du = Math.abs(u - cu);
			if (du == 1) {
				p.put(c, u, 7, VB - 1, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
			} else {
				p.set(c, u, 7, VB - 1, stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH, true));
			}
			if (du >= 2 && du % 2 == 0 && du < g.hb()) {
				p.set(c, u, 5, VB - 1, b(p.hash(u, 5, VB, 2) == 0 ? Blocks.POTTED_FLOWERING_AZALEA : Blocks.POTTED_FERN));
			}
			// [depth] shutters: open trapdoors either side of each window, against the wall
			boolean beside = du % 2 == 1 && du >= 1 && du < g.hb() && (du != 3 || g.hb() > 3);
			if (beside && u != cu) {
				p.set(c, u, 6, VB - 1, trap(Blocks.CHERRY_TRAPDOOR, Direction.NORTH, true, false));
			}
		}
	}

	// ------------------------------------------------------------------ deck B and its garden (y8 / y9)

	private static boolean inC(G g, int u, int v) {
		return u >= g.c0() && u <= g.c1() && v >= VC;
	}

	private static void deckB(CitadelCanvas c, Plot p, G g, Random r, boolean flip) {
		int u0 = g.b0();
		int u1 = g.b1();
		int v0 = VB - 1;
		int v1 = g.ve();
		boolean[][] free = new boolean[g.w()][g.d()];
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				if (onRing(u, v, u0, u1, v0, v1)) {
					p.set(c, u, DECK_B, v, plaster(p, u, DECK_B, v));
				} else if (inC(g, u, v)) {
					p.set(c, u, DECK_B, v, plaster(p, u, DECK_B, v));
				} else {
					free[u][v] = true;
				}
			}
		}
		garden(c, p, g, r, DECK_B, free, VC);
		rail(c, p, g, DECK_B + 1, u0, u1, v0, v1, (u, v) -> inC(g, u, v));
		// a small cherry tree in one front corner of the upper garden
		int tu = g.cu() + (flip ? 1 : -1) * (g.hb() - 1);
		tree(c, p, tu, DECK_B + 1, 6);
	}

	/** [finish] a bonsai cherry: two-block trunk, a layered blossom crown. */
	private static void tree(CitadelCanvas c, Plot p, int u, int y, int v) {
		p.set(c, u, y - 1, v, b(Blocks.COARSE_DIRT));
		p.set(c, u, y, v, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
		p.set(c, u, y + 1, v, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				if (du != 0 || dv != 0) {
					p.set(c, u + du, y + 1, v + dv, leaves(Blocks.CHERRY_LEAVES));
				}
				p.set(c, u + du, y + 2, v + dv, leaves(Blocks.CHERRY_LEAVES));
			}
		}
		p.set(c, u, y + 3, v, leaves(Blocks.CHERRY_LEAVES));
		p.set(c, u + 1, y + 3, v, leaves(Blocks.CHERRY_LEAVES));
		p.set(c, u - 1, y + 3, v, leaves(Blocks.CHERRY_LEAVES));
		p.set(c, u, y + 3, v + 1, leaves(Blocks.CHERRY_LEAVES));
		p.set(c, u, y + 3, v - 1, leaves(Blocks.CHERRY_LEAVES));
	}

	// ------------------------------------------------------------------ tier C: glazed tea-room (y9..y10)

	private static void bodyC(CitadelCanvas c, Plot p, G g) {
		int cu = g.cu();
		for (int u = g.c0(); u <= g.c1(); u++) {
			for (int v = VC; v <= g.ve(); v++) {
				if (!onRing(u, v, g.c0(), g.c1(), VC, g.ve())) {
					continue;
				}
				boolean sideWall = u == g.c0() || u == g.c1();
				boolean corner = sideWall && (v == VC || v == g.ve());
				int k = v - VC;
				boolean pillar = corner || (sideWall && k % 3 == 0);
				for (int y = 9; y <= 10; y++) {
					BlockState st;
					if (pillar) {
						st = log(Blocks.CHERRY_LOG, Direction.Axis.Y);
					} else if (v == g.ve()) {
						st = u == cu ? pane(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK), true) : plaster(p, u, y, v);
					} else {
						st = glass(p, u, v, !sideWall, true);
					}
					p.set(c, u, y, v, st);
				}
			}
		}
		p.set(c, cu, 9, VC, door(Blocks.CHERRY_DOOR, Direction.NORTH, true));
		p.set(c, cu, 10, VC, door(Blocks.CHERRY_DOOR, Direction.NORTH, false));
		// top plate: a cherry beam all round the walls
		for (int u = g.c0(); u <= g.c1(); u++) {
			p.set(c, u, PLATE, VC, log(Blocks.CHERRY_LOG, Direction.Axis.X));
			p.set(c, u, PLATE, g.ve(), log(Blocks.CHERRY_LOG, Direction.Axis.X));
		}
		for (int v = VC; v <= g.ve(); v++) {
			p.set(c, g.c0(), PLATE, v, log(Blocks.CHERRY_LOG, Direction.Axis.Z));
			p.set(c, g.c1(), PLATE, v, log(Blocks.CHERRY_LOG, Direction.Axis.Z));
		}
	}

	/**
	 * [finish] gable roof, ridge along v (the gable end faces the front): dark deepslate tiles, a
	 * cherry eave line, a polished ridge with a slab cap and a lightning-rod finial; the gable ends are
	 * filled with planks and a small pink light; an inverted-stair cove closes the eaves.
	 */
	private static void roofC(CitadelCanvas c, Plot p, G g) {
		int cu = g.cu();
		int ru0 = cu - 3;
		int ru1 = cu + 3;
		int vTop = g.d() - 1;
		for (int v = VC - 1; v <= vTop; v++) {
			for (int u = ru0; u <= ru1; u++) {
				int y = PLATE + Math.min(u - ru0, ru1 - u);
				boolean eave = u == ru0 || u == ru1;
				if (u == cu) {
					p.set(c, u, y, v, b(Blocks.POLISHED_DEEPSLATE));
					p.set(c, u, y + 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
					continue;
				}
				Direction f = u < cu ? Direction.EAST : Direction.WEST;
				p.set(c, u, y, v, stair(eave ? Blocks.CHERRY_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS, f, false));
				if (eave && v >= VC && v <= g.ve()) {
					p.set(c, u, y - 1, v, stair(Blocks.DEEPSLATE_TILE_STAIRS, f, true));
				}
			}
		}
		// gable ends under the roof, in the wall planes
		for (int v : new int[] {VC, g.ve()}) {
			for (int u = g.c0(); u <= g.c1(); u++) {
				int top = PLATE + Math.min(u - ru0, ru1 - u);
				for (int y = PLATE + 1; y < top; y++) {
					p.set(c, u, y, v, u == cu && y == PLATE + 1 ? pane(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK), true) : b(Blocks.CHERRY_PLANKS));
				}
			}
		}
		// the back overhang is closed too: a cherry gable board under the roof's last row, and a soffit over the wall top
		for (int vv : new int[] {vTop, VC - 1}) {
			for (int u = ru0; u <= ru1; u++) {
				int top = PLATE + Math.min(u - ru0, ru1 - u);
				for (int y = PLATE; y < top; y++) {
					p.set(c, u, y, vv, u == cu && y == PLATE + 1 ? pane(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK), true) : b(Blocks.CHERRY_PLANKS));
				}
			}
		}
		for (int u = ru0 + 1; u < ru1; u++) {
			p.set(c, u, PLATE, g.ve(), log(Blocks.CHERRY_LOG, Direction.Axis.X));
		}
		// finial on the ridge
		p.set(c, cu, PLATE + 4, (VC + g.ve()) / 2, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
	}

	// ------------------------------------------------------------------ gardens and railings

	/**
	 * [finish] fills the free cells of a deck with a bed (grass, moss, podzol-like dirt), a paver path
	 * along the door column and plants on top; azalea hedges stand along the walls.
	 */
	private static void garden(CitadelCanvas c, Plot p, G g, Random r, int deckY, boolean[][] free, int stopV) {
		int cu = g.cu();
		for (int u = 0; u < g.w(); u++) {
			for (int v = 0; v < g.d(); v++) {
				if (!free[u][v]) {
					continue;
				}
				boolean path = u == cu && v <= stopV + 1;
				boolean nearWall = v >= 1 && free[u][v] && (v + 1 >= g.d() || !free[u][v + 1]) && u != cu;
				if (path) {
					p.set(c, u, deckY, v, paver(p, u, deckY, v));
					continue;
				}
				p.set(c, u, deckY, v, mix(p, u, deckY, v, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT));
				int k = p.hash(u, deckY, v, 22);
				BlockState plant = switch (k) {
					case 0, 1 -> b(Blocks.AZALEA);
					case 2 -> b(Blocks.FLOWERING_AZALEA);
					case 3 -> b(Blocks.PINK_PETALS);
					case 4 -> b(Blocks.ALLIUM);
					case 5 -> b(Blocks.CORNFLOWER);
					case 6 -> b(Blocks.PINK_TULIP);
					case 7, 8 -> b(Blocks.SHORT_GRASS);
					case 9 -> b(Blocks.FERN);
					case 10 -> b(Blocks.LILY_OF_THE_VALLEY);
					default -> null;
				};
				if (nearWall && k % 2 == 0) {
					plant = leaves(k % 4 == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES);
				}
				if (plant != null) {
					p.set(c, u, deckY + 1, v, plant);
				}
			}
		}
	}

	/**
	 * [depth] a railing round the border of a deck: cherry fences with sides set by hand, linked to
	 * stripped-cherry posts at the corners and every fourth cell, capped with a lantern or a slab.
	 */
	private static void rail(CitadelCanvas c, Plot p, G g, int y, int u0, int u1, int v0, int v1, java.util.function.BiPredicate<Integer, Integer> skip) {
		Links fences = new Links();
		java.util.List<int[]> posts = new java.util.ArrayList<>();
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				if (!onRing(u, v, u0, u1, v0, v1) || skip.test(u, v)) {
					continue;
				}
				boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
				boolean edgeU = v == v0 || v == v1;
				boolean post = corner || (edgeU ? Math.abs(u - g.cu()) % 4 == 0 : (v - v0) % 4 == 0);
				if (post) {
					posts.add(new int[] {u, v, corner ? 1 : 0});
					fences.partner(u, v);
				} else {
					fences.add(u, v);
				}
			}
		}
		fences.emitFences(c, p, y, Blocks.CHERRY_FENCE);
		for (int[] q : posts) {
			p.set(c, q[0], y, q[1], b(Blocks.POLISHED_ANDESITE));
			p.set(c, q[0], y + 1, q[1], q[2] == 1 ? b(Blocks.LANTERN) : slab(Blocks.STONE_BRICK_SLAB));
		}
	}

	// ------------------------------------------------------------------ yard (y0 outside the walls)

	/** [finish] the transition to the ground: a hedge round the plinth, lamp posts, flared steps. */
	private static void yard(CitadelCanvas c, Plot p, G g, Random r) {
		int cu = g.cu();
		int u0 = g.rimL();
		int u1 = g.rimR();
		int vb = g.d() - 1;
		// front row v 0: flared steps at the path, hedge elsewhere
		for (int u = u0; u <= u1; u++) {
			int du = Math.abs(u - cu);
			if (du <= 1) {
				p.set(c, u, 0, 0, slab(du == 0 ? Blocks.DEEPSLATE_BRICK_SLAB : Blocks.POLISHED_DEEPSLATE_SLAB));
			} else if (du == g.ha() + 1) {
				p.set(c, u, 0, 0, b(Blocks.STONE_BRICK_WALL));
				p.set(c, u, 1, 0, b(Blocks.STONE_BRICK_WALL));
				p.set(c, u, 2, 0, b(Blocks.LANTERN));
			} else {
				p.set(c, u, 0, 0, hedge(p, u, 0));
				if (p.hash(u, 1, 0, 3) == 0) {
					p.set(c, u, 1, 0, b(Blocks.FLOWERING_AZALEA));
				}
			}
		}
		// side and back strips: hedge, with a barrel or a woodpile now and then
		for (int v = 1; v <= vb; v++) {
			for (int u : new int[] {u0, u1}) {
				if (p.at(c, u, 1, v) != null) {
					continue;
				}
				int k = p.hash(u, 0, v, 6);
				if (k == 0) {
					p.set(c, u, 0, v, b(Blocks.BARREL));
				} else if (k == 1) {
					p.set(c, u, 0, v, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
				} else {
					p.set(c, u, 0, v, hedge(p, u, v));
				}
			}
		}
		for (int u = u0 + 1; u < u1; u++) {
			if (p.at(c, u, 1, vb) != null) {
				continue;
			}
			p.set(c, u, 0, vb, p.hash(u, 0, vb, 5) == 0 ? b(Blocks.COMPOSTER) : hedge(p, u, vb));
		}
	}

	/** [finish] vines on the dark stone of the sides and the back, hanging down from the cove. */
	private static void vines(CitadelCanvas c, Plot p, G g) {
		int u0 = g.rimL();
		int u1 = g.rimR();
		int vb = g.d() - 1;
		int mid = g.d() / 2;
		for (int v = 2; v < vb; v++) {
			if (v == mid || p.hash(0, 2, v, 2) != 0) {
				continue;
			}
			p.put(c, u0, 2, v, vine(Direction.EAST), CitadelCanvas.DECOR);
			p.put(c, u1, 2, v, vine(Direction.WEST), CitadelCanvas.DECOR);
			if (p.hash(1, 1, v, 2) == 0) {
				p.put(c, u0, 1, v, vine(Direction.EAST), CitadelCanvas.DECOR);
			}
			if (p.hash(2, 1, v, 2) == 0) {
				p.put(c, u1, 1, v, vine(Direction.WEST), CitadelCanvas.DECOR);
			}
		}
		for (int u = u0 + 1; u < u1; u++) {
			if (p.hash(u, 2, vb, 2) == 0) {
				p.put(c, u, 2, vb, vine(Direction.NORTH), CitadelCanvas.DECOR);
				if (p.hash(u, 1, vb, 2) == 0) {
					p.put(c, u, 1, vb, vine(Direction.NORTH), CitadelCanvas.DECOR);
				}
			}
		}
	}

	private static BlockState hedge(Plot p, int u, int v) {
		Block blk = p.hash(u, 0, v, 3) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES;
		return leaves(blk);
	}
}
