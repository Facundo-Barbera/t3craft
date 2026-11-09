package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.door;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.hanging;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.log;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.mix;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.pane;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.slabTop;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.trap;
import static dev.t3craft.hq.citadel.catalog.house.TerraceKit.vine;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.house.TerraceKit.Links;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A hillside cottage: a mossy retaining wall holds a raised garden terrace, the cottage stands on it
 * under a long warm-brick roof, and a small waterfall spills from a stone channel in the terrace
 * through a niche between two buttresses into a lily pond at the foot of the wall. A stair climbs
 * along the left edge to the terrace.
 *
 * <pre>
 *  side section, front on the left                       y12  ridge, chimney
 *                                       ____/\____            long gable roof (resin brick)
 *                                      │ cottage  │      y4-6 stone base, plaster, dark-oak frame
 *   lily pond ▒▒ ┌╲ spout   garden  ... ├──────────┤
 *   ▒▒▒▒▒▒▒▒▒▒▒ ┌┘ channel (water y3)   terrace deck y3
 *   ▒pond y0▒▒ ║ retaining wall, buttresses, vines (y0..y3)
 *   stair ▁▂▃▅ ║
 * </pre>
 *
 * Size: 11–15 × 12–15 × 14+. The waterfall stands to the right of the door column.
 */
public final class HouseTerraceCascade implements Variant {
	private static final int VW = 3; // v of the retaining wall's front face
	private static final int DECK = 3; // top layer of the platform
	private static final int PLATE = 8;

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
		return 11;
	}

	@Override
	public int minD() {
		return 12;
	}

	@Override
	public int minH() {
		return 15;
	}

	@Override
	public int maxW() {
		return 15;
	}

	@Override
	public int maxD() {
		return 15;
	}

	private record G(int w, int d, int cu, int ha, int hh, int vh, int ve, int uf) {
		int eL() {
			return cu - ha;
		}

		int eR() {
			return cu + ha;
		}

		int pv1() {
			return d - 2; // back row of the platform
		}

		int h0() {
			return cu - hh;
		}

		int h1() {
			return cu + hh;
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0xCA5C_ADE5L);
		int w = p.w();
		int d = p.d();
		int cu = (w - 1) / 2;
		int ha = Math.min(cu - 1, w - 2 - cu);
		int hh = Math.min(ha - 1, 4);
		int gd = d >= 14 ? 3 : 2;
		G g = new G(w, d, cu, ha, hh, VW + 1 + gd, d - 2, cu + ha - 3);
		platform(c, p, g);
		climb(c, p, g);
		pond(c, p, g, r);
		waterfall(c, p, g);
		terrace(c, p, g, r);
		house(c, p, g);
		roof(c, p, g);
		margins(c, p, g, r);
	}

	// ------------------------------------------------------------------ materials

	private static BlockState foot(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLED_DEEPSLATE);
	}

	private static BlockState wallStone(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS);
	}

	private static BlockState plaster(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.CALCITE, Blocks.CALCITE, Blocks.CALCITE, Blocks.SMOOTH_QUARTZ);
	}

	private static BlockState frame() {
		return log(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
	}

	private static BlockState glass(Plot p, int u, int v, boolean alongU) {
		return pane(p.hash(u, 5, v, 3) == 0 ? Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK) : Blocks.GLASS_PANE, alongU);
	}

	private static BlockState falling() {
		return Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 8);
	}

	// ------------------------------------------------------------------ the retaining wall and platform (y0..y3)

	/**
	 * [palette] cobble and andesite at the foot, mossy brick above, a polished lip on the deck edge.
	 * [depth] buttresses jut from the front face and the sides, capped with slabs.
	 */
	private static void platform(CitadelCanvas c, Plot p, G g) {
		for (int u = g.eL(); u <= g.eR(); u++) {
			for (int v = VW; v <= g.pv1(); v++) {
				p.set(c, u, 0, v, foot(p, u, 0, v));
				p.set(c, u, 1, v, p.hash(u, 1, v, 3) == 0 ? foot(p, u, 1, v) : wallStone(p, u, 1, v));
				p.set(c, u, 2, v, wallStone(p, u, 2, v));
				p.set(c, u, DECK, v, b(Blocks.STONE_BRICKS));
			}
		}
		// front buttresses either side of the waterfall niche and one more to the left
		int[] bu = {g.uf() - 1, g.uf() + 1, g.uf() - 5};
		for (int u : bu) {
			if (u <= g.eL() + 1 || u >= g.eR()) {
				continue;
			}
			for (int y = 0; y <= DECK; y++) {
				p.set(c, u, y, VW - 1, y == DECK ? b(Blocks.POLISHED_ANDESITE) : y == 0 ? foot(p, u, y, VW - 1) : wallStone(p, u, y, VW - 1));
			}
			p.set(c, u, DECK + 1, VW - 1, slab(Blocks.STONE_BRICK_SLAB));
		}
		// side and back buttresses in the margin
		for (int v = VW + 2; v <= g.pv1(); v += 3) {
			for (int u : new int[] {g.eL() - 1, g.eR() + 1}) {
				p.set(c, u, 0, v, foot(p, u, 0, v));
				p.set(c, u, 1, v, wallStone(p, u, 1, v));
				p.set(c, u, 2, v, slabTop(Blocks.STONE_BRICK_SLAB));
			}
		}
		for (int u = g.eL() + 2; u <= g.eR() - 2; u += 3) {
			p.set(c, u, 0, g.pv1() + 1, foot(p, u, 0, g.pv1() + 1));
			p.set(c, u, 1, g.pv1() + 1, slabTop(Blocks.STONE_BRICK_SLAB));
		}
	}

	/** The climbing stair on the left edge: four steps with a sloping cheek, a lantern post on the top. */
	private static void climb(CitadelCanvas c, Plot p, G g) {
		int sl = g.eL();
		for (int k = 0; k <= 3; k++) {
			for (int y = 0; y < k; y++) {
				p.set(c, sl, y, k, wallStone(p, sl, y, k));
			}
			p.set(c, sl, k, k, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
			if (k < 3) {
				for (int y = 0; y <= k; y++) {
					p.set(c, sl + 1, y, k, y == 0 ? foot(p, sl + 1, y, k) : wallStone(p, sl + 1, y, k));
				}
				p.set(c, sl + 1, k + 1, k, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
			}
		}
	}

	// ------------------------------------------------------------------ the pond (y0, v 0..2)

	/** [finish] a shallow lily pond in a stone kerb: rim blocks, water, pads, a few reeds. */
	private static void pond(CitadelCanvas c, Plot p, G g, Random r) {
		int u0 = g.eL() + 2;
		int u1 = g.eR() - 1;
		for (int u = u0 - 1; u <= g.eR(); u++) {
			p.set(c, u, 0, 0, u == u0 - 1 || u == g.eR() ? b(Blocks.POLISHED_ANDESITE) : mix(p, u, 0, 0, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.POLISHED_ANDESITE));
		}
		for (int v = 1; v <= VW - 1; v++) {
			p.set(c, g.eR(), 0, v, mix(p, g.eR(), 0, v, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS));
		}
		for (int u = u0; u <= u1; u++) {
			for (int v = 1; v <= VW - 1; v++) {
				p.set(c, u, 0, v, b(Blocks.WATER));
				if (p.hash(u, 1, v, 3) == 0) {
					p.set(c, u, 1, v, b(Blocks.LILY_PAD));
				}
			}
		}
		// stepping stones across the pond to the right end
		for (int u = u0 + 1; u <= u1; u += 2) {
			p.set(c, u, 0, 1, slab(Blocks.POLISHED_ANDESITE_SLAB));
		}
		// a corner of reeds and a flowering shrub on the kerb
		p.set(c, u1, 1, 1, b(Blocks.SUGAR_CANE));
		p.set(c, u1, 1, 2, b(Blocks.SUGAR_CANE));
		p.set(c, u1, 2, 2, b(Blocks.SUGAR_CANE));
		p.set(c, g.eR(), 1, 0, b(Blocks.FLOWERING_AZALEA));
	}

	/**
	 * [depth] the waterfall: a channel sunk in the deck, a stone spout in the lip of the wall, a fall of
	 * water between two buttresses into the pond, with vines on the stone either side.
	 */
	private static void waterfall(CitadelCanvas c, Plot p, G g) {
		int uf = g.uf();
		p.set(c, uf, DECK, VW, b(Blocks.WATER));
		p.set(c, uf, DECK, VW + 1, b(Blocks.WATER));
		p.set(c, uf, DECK, VW + 2, b(Blocks.CHISELED_STONE_BRICKS));
		p.set(c, uf, DECK + 1, VW + 2, b(Blocks.DECORATED_POT));
		for (int y = 1; y <= DECK; y++) {
			p.set(c, uf, y, VW - 1, y == DECK ? b(Blocks.WATER) : falling());
		}
		// the stone either side of the channel, a ledge of upside-down stairs under the spout
		p.set(c, uf - 1, DECK, VW, b(Blocks.CHISELED_STONE_BRICKS));
		p.set(c, uf + 1, DECK, VW, b(Blocks.CHISELED_STONE_BRICKS));
		// vines on the wall face, beside the fall (the buttresses stand at uf-1 and uf+1)
		for (int u = g.eL() + 2; u <= g.eR() - 1; u++) {
			int du = Math.abs(u - uf);
			if (du >= 2 && p.at(c, u, 2, VW - 1) == null && p.hash(u, 2, VW, 3) == 0) {
				p.put(c, u, 3, VW - 1, vine(Direction.SOUTH), CitadelCanvas.DECOR);
				p.put(c, u, 2, VW - 1, vine(Direction.SOUTH), CitadelCanvas.DECOR);
			}
		}
	}

	// ------------------------------------------------------------------ the terrace garden (y3 / y4)

	private static boolean inHouse(G g, int u, int v) {
		return u >= g.h0() - 1 && u <= g.h1() + 1 && v >= g.vh() - 1;
	}

	/**
	 * [finish] beds of grass and moss with a paver path from the stair to the door, hedges on the
	 * wall, a cherry tree leaning over the pond, and a parapet of mossy brick linked by hand.
	 */
	private static void terrace(CitadelCanvas c, Plot p, G g, Random r) {
		int cu = g.cu();
		for (int u = g.eL() + 1; u < g.eR(); u++) {
			for (int v = VW + 1; v < g.pv1(); v++) {
				if (inHouse(g, u, v) || (u == g.uf() && v <= VW + 2)) {
					continue;
				}
				boolean path = (v == VW + 1 && u <= cu) || (u == cu && v < g.vh());
				if (path) {
					p.set(c, u, DECK, v, mix(p, u, DECK, v, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.ANDESITE, Blocks.POLISHED_ANDESITE));
					continue;
				}
				p.set(c, u, DECK, v, mix(p, u, DECK, v, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.COARSE_DIRT));
				int k = p.hash(u, DECK, v, 12);
				BlockState plant = switch (k) {
					case 0, 1 -> b(Blocks.AZALEA);
					case 2 -> b(Blocks.FLOWERING_AZALEA);
					case 3 -> b(Blocks.PINK_PETALS);
					case 4 -> b(Blocks.ALLIUM);
					case 5 -> b(Blocks.CORNFLOWER);
					case 6 -> b(Blocks.POPPY);
					case 7, 8 -> b(Blocks.SHORT_GRASS);
					case 9 -> b(Blocks.FERN);
					default -> null;
				};
				if (plant != null) {
					p.set(c, u, DECK + 1, v, plant);
				}
			}
		}
		// the tree: a cherry at the front-right corner of the terrace, canopy over the fall
		int tu = g.eR() - 1;
		int tv = VW + 1;
		p.set(c, tu, DECK, tv, b(Blocks.COARSE_DIRT));
		for (int y = DECK + 1; y <= DECK + 3; y++) {
			p.set(c, tu, y, tv, log(Blocks.CHERRY_LOG, Direction.Axis.Y));
		}
		for (int dy = 0; dy <= 2; dy++) {
			int rad = dy == 1 ? 2 : 1;
			for (int du = -rad; du <= rad; du++) {
				for (int dv = -rad; dv <= rad; dv++) {
					if (Math.abs(du) == 2 && Math.abs(dv) == 2) {
						continue;
					}
					p.set(c, tu + du, DECK + 3 + dy, tv + dv, leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
		parapet(c, p, g);
		// a bench by the path, facing the pond
		int bu = Math.max(g.eL() + 3, cu - 3);
		p.set(c, bu, DECK + 1, VW + 1, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
		p.set(c, bu + 1, DECK + 1, VW + 1, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
	}

	/** [depth] a low wall round the platform edge, with an opening at the stair and posts with lanterns. */
	private static void parapet(CitadelCanvas c, Plot p, G g) {
		Links run = new Links();
		List<int[]> posts = new ArrayList<>();
		int u0 = g.eL();
		int u1 = g.eR();
		int v1 = g.pv1();
		for (int u = u0; u <= u1; u++) {
			for (int v = VW; v <= v1; v++) {
				if (!(u == u0 || u == u1 || v == VW || v == v1)) {
					continue;
				}
				if (u == u0 && v <= VW + 1) {
					continue; // the stair arrives here
				}
				if (v == VW && (u == g.uf())) {
					continue; // the channel
				}
				boolean corner = (u == u1 && (v == VW || v == v1)) || (u == u0 && v == v1);
				boolean end = (v == VW && (u == g.uf() - 1 || u == g.uf() + 1 || u == u0 + 1)) || (u == u0 && v == VW + 2);
				if (corner || end) {
					posts.add(new int[] {u, v, corner || (v == VW && u == u0 + 1) ? 1 : 0});
					run.partner(u, v);
				} else {
					run.add(u, v);
				}
			}
		}
		run.emitWalls(c, p, DECK + 1, Blocks.MOSSY_STONE_BRICK_WALL);
		for (int[] q : posts) {
			p.set(c, q[0], DECK + 1, q[1], b(Blocks.POLISHED_ANDESITE));
			p.set(c, q[0], DECK + 2, q[1], q[2] == 1 ? b(Blocks.LANTERN) : slab(Blocks.STONE_BRICK_SLAB));
		}
	}

	// ------------------------------------------------------------------ the cottage (y4..y6)

	private static void house(CitadelCanvas c, Plot p, G g) {
		int cu = g.cu();
		int vh = g.vh();
		int ve = g.ve();
		for (int u = g.h0(); u <= g.h1(); u++) {
			for (int v = vh; v <= ve; v++) {
				if (!(u == g.h0() || u == g.h1() || v == vh || v == ve)) {
					continue;
				}
				boolean sideWall = u == g.h0() || u == g.h1();
				boolean front = v == vh;
				int du = Math.abs(u - cu);
				boolean post;
				boolean window;
				if (sideWall) {
					int k = v - vh;
					post = k == 0 || v == ve || k % 4 == 0;
					window = !post && k % 4 == 2;
				} else if (front) {
					post = du == 1 || du == g.hh();
					window = du == 2 || (du == 4 && du < g.hh());
				} else {
					post = du % 4 == 0;
					window = du % 4 == 2;
				}
				for (int y = 4; y <= 7; y++) {
					BlockState st;
					if (post) {
						st = frame();
					} else if (y == 4) {
						st = foot(p, u, y, v);
					} else if (window && (y == 5 || y == 6)) {
						st = glass(p, u, v, !sideWall);
					} else {
						st = plaster(p, u, y, v);
					}
					p.set(c, u, y, v, st);
				}
			}
		}
		p.set(c, cu, 4, vh, door(Blocks.DARK_OAK_DOOR, Direction.NORTH, true));
		p.set(c, cu, 5, vh, door(Blocks.DARK_OAK_DOOR, Direction.NORTH, false));
		p.set(c, cu, 6, vh, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
		p.set(c, cu, 7, vh, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
		// [finish] shutters beside the windows, a step and lanterns at the door
		for (int s : new int[] {-1, 1}) {
			p.set(c, cu + s * 3, 5, vh - 1, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, false));
			p.set(c, cu + s * 3, 6, vh - 1, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, false));
			p.put(c, cu + s, 5, vh - 1, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
		}
		p.set(c, cu, DECK + 1, vh - 1, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
		// plate beams
		for (int u = g.h0(); u <= g.h1(); u++) {
			p.set(c, u, PLATE, vh, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			p.set(c, u, PLATE, ve, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
		}
		for (int v = vh; v <= ve; v++) {
			p.set(c, g.h0(), PLATE, v, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
			p.set(c, g.h1(), PLATE, v, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
		}
	}

	// ------------------------------------------------------------------ roof (y7+)

	/**
	 * [finish] gable roof, ridge along u: warm resin brick with a cherry eave line, a polished ridge
	 * with a cherry slab cap, side gables with a king post and a pink light, and a chimney.
	 */
	private static void roof(CitadelCanvas c, Plot p, G g) {
		int rv0 = g.vh() - 1;
		int rv1 = g.d() - 1;
		int u0 = g.h0() - 1;
		int u1 = g.h1() + 1;
		int top = PLATE + (rv1 - rv0) / 2;
		boolean oddRows = (rv1 - rv0) % 2 == 0;
		for (int v = rv0; v <= rv1; v++) {
			int y = PLATE + Math.min(v - rv0, rv1 - v);
			boolean frontSlope = v - rv0 < rv1 - v;
			boolean middle = v - rv0 == rv1 - v;
			boolean eave = v == rv0 || v == rv1;
			for (int u = u0; u <= u1; u++) {
				BlockState st;
				if (middle) {
					st = b(Blocks.POLISHED_DEEPSLATE);
				} else {
					st = stair(eave ? Blocks.CHERRY_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS, frontSlope ? Direction.SOUTH : Direction.NORTH, false);
				}
				p.set(c, u, y, v, st);
				if ((u == u0 || u == u1) && !middle && y - 1 >= PLATE) {
					p.set(c, u, y - 1, v, stair(Blocks.DEEPSLATE_TILE_STAIRS, frontSlope ? Direction.NORTH : Direction.SOUTH, true));
				}
			}
		}
		for (int u = u0; u <= u1; u++) {
			if (oddRows) {
				p.set(c, u, top + 1, rv0 + (rv1 - rv0) / 2, slab(Blocks.CHERRY_SLAB));
			} else {
				p.set(c, u, top + 1, rv0 + (rv1 - rv0) / 2, slab(Blocks.CHERRY_SLAB));
				p.set(c, u, top + 1, rv0 + (rv1 - rv0) / 2 + 1, slab(Blocks.CHERRY_SLAB));
			}
		}
		// gables
		int mid = (g.vh() + g.ve()) / 2;
		for (int u : new int[] {g.h0(), g.h1()}) {
			for (int v = g.vh(); v <= g.ve(); v++) {
				for (int y = PLATE + 1; y < PLATE + Math.min(v - rv0, rv1 - v); y++) {
					BlockState st = v == mid ? frame() : plaster(p, u, y, v);
					if (y == PLATE + 2 && Math.abs(v - mid) == 1) {
						st = pane(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK), false);
					}
					p.set(c, u, y, v, st);
				}
			}
		}
		// the chimney, through the back slope at the left gable
		int cuChim = g.h0() + 1;
		int cv = rv0 + (rv1 - rv0) / 2 + 1;
		int ctop = Math.min(top + 2, p.h() - 1);
		for (int y = PLATE; y < ctop; y++) {
			p.set(c, cuChim, y, cv, p.hash(cuChim, y, cv, 3) == 0 ? b(Blocks.COBBLESTONE) : b(Blocks.STONE_BRICKS));
		}
		p.set(c, cuChim, ctop, cv, slab(Blocks.STONE_BRICK_SLAB));
	}

	// ------------------------------------------------------------------ margins (ground level)

	/** [finish] hedges at the foot of the platform, a barrel and a woodpile, a lantern post by the stair. */
	private static void margins(CitadelCanvas c, Plot p, G g, Random r) {
		for (int v = VW + 1; v <= g.pv1() + 1; v++) {
			for (int u : new int[] {g.eL() - 1, g.eR() + 1}) {
				if (p.at(c, u, 1, v) != null) {
					continue;
				}
				int k = p.hash(u, 0, v, 6);
				if (k == 0) {
					p.set(c, u, 0, v, b(Blocks.BARREL));
				} else if (k == 1) {
					p.set(c, u, 0, v, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
				} else {
					p.set(c, u, 0, v, leaves(k == 2 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
				}
			}
		}
		for (int u = g.eL(); u <= g.eR(); u++) {
			if (p.at(c, u, 1, g.pv1() + 1) == null) {
				p.set(c, u, 0, g.pv1() + 1, leaves(p.hash(u, 0, 9, 3) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
			}
		}
		// the left strip beside the stair: a lantern post and a flower bed
		int lu = g.eL() - 1;
		p.set(c, lu, 0, 0, b(Blocks.MOSSY_STONE_BRICK_WALL));
		p.set(c, lu, 1, 0, b(Blocks.MOSSY_STONE_BRICK_WALL));
		p.set(c, lu, 2, 0, b(Blocks.LANTERN));
		for (int v = 1; v <= VW; v++) {
			p.set(c, lu, 0, v, leaves(v == 2 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
		}
		// front row: a hedge in front of the pond rim, open at the stair
		for (int u = g.eL() + 2; u < g.eR(); u++) {
			if (p.hash(u, 0, 0, 3) == 0) {
				p.set(c, u, 1, 0, p.hash(u, 1, 0, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.FERN));
			}
		}
	}
}
