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
 * A white villa on a tall stone podium. A broad stair with sloping cheeks climbs the front to a
 * promenade; behind it a two-storey block of quartz and calcite stands on a colonnade, under a
 * flat roof with an overhanging, copper-trimmed cornice. The roof is a garden: planter beds along the
 * parapet, a bamboo-mosaic floor, and a cherry pergola with a bench, a table and hanging lanterns,
 * next to a small stair hut.
 *
 * <pre>
 *  front section (door column)
 *                 ┌ pergola ┐ ┌hut┐
 *          ───────┴─────────┴─┴───┴─── roof garden + parapet (y10..y11)
 *          │ upper storey, windows y7..y8                    │
 *          ╞═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═ ═╡ architrave y6
 *   ▁▂▃   │ colonnade (v5) + recessed ground floor (v6)     │
 *  ▁▂▃▅▆▇██ podium y0..y2 (deepslate, tuff, polished tuff) ██
 * </pre>
 *
 * Size: 11–15 × 12–15 × 16+. Symmetric about the door column.
 */
public final class HouseTerracePodium implements Variant {
	private static final int TOP = 2; // last podium layer; the promenade is the top of this layer
	private static final int VF = 3; // podium front face
	private static final int VCOL = 5; // colonnade and upper front wall
	private static final int VWALL = 6; // recessed ground-floor front wall
	private static final int ARCH = 6; // architrave layer (floor between the storeys)
	private static final int DECK = 10;

	@Override
	public String id() {
		return "house_terrace_podium";
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
		return 16;
	}

	@Override
	public int maxW() {
		return 15;
	}

	@Override
	public int maxD() {
		return 15;
	}

	private record G(int w, int d, int cu, int ha, int hb, int ve, int sh) {
		int p0() {
			return cu - ha;
		}

		int p1() {
			return cu + ha;
		}

		int b0() {
			return cu - hb;
		}

		int b1() {
			return cu + hb;
		}

		int pv1() {
			return d - 2; // back row of the podium
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x90D1_0A11L);
		int w = p.w();
		int d = p.d();
		int cu = (w - 1) / 2;
		int ha = Math.min(cu - 1, w - 2 - cu);
		G g = new G(w, d, cu, ha, ha - 1, d - 3, ha >= 5 ? 2 : 1);
		podium(c, p, g);
		stairs(c, p, g);
		balustrade(c, p, g);
		ground(c, p, g);
		upper(c, p, g);
		relief(c, p, g);
		deck(c, p, g, r);
		pergola(c, p, g, r);
		hut(c, p, g);
		forecourt(c, p, g, r);
	}

	// ------------------------------------------------------------------ materials

	private static BlockState foot(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE);
	}

	private static BlockState tuff(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.TUFF_BRICKS, Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF);
	}

	private static BlockState pave(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.POLISHED_TUFF, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS, Blocks.SMOOTH_STONE);
	}

	private static BlockState white(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, Blocks.CALCITE, Blocks.CALCITE, Blocks.SMOOTH_QUARTZ, Blocks.CALCITE, Blocks.QUARTZ_BRICKS);
	}

	private static BlockState pillar() {
		return b(Blocks.QUARTZ_PILLAR);
	}

	/** The oxidised copper trim: green against white, the one cool accent of this house. */
	private static Block copperStairs() {
		return Blocks.CUT_COPPER_STAIRS.waxed().oxidized();
	}

	private static Block copperSlab() {
		return Blocks.CUT_COPPER_SLAB.waxed().oxidized();
	}

	private static BlockState glass(Plot p, int u, int v, boolean alongU) {
		boolean pink = p.hash(u, 7, v, 3) == 0;
		return pane(pink ? Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK) : Blocks.GLASS_PANE, alongU);
	}

	/** [depth] a recessed window: the wall cell is left open and the pane stands one block inside. */
	private static void window(CitadelCanvas c, Plot p, int u, int y, int v, int iu, int iv, boolean alongU) {
		p.set(c, u, y, v, CitadelCanvas.AIR);
		p.set(c, iu, y, iv, glass(p, u, v, alongU));
	}

	// ------------------------------------------------------------------ podium (y0..y2)

	/**
	 * [palette] three courses, dark to light: deepslate bricks, tuff bricks, polished tuff on top.
	 * [depth] piers jut out of the front face and the sides; between them the face is recessed with a
	 * lantern niche.
	 */
	private static void podium(CitadelCanvas c, Plot p, G g) {
		for (int u = g.p0(); u <= g.p1(); u++) {
			for (int v = VF; v <= g.pv1(); v++) {
				p.set(c, u, 0, v, foot(p, u, 0, v));
				p.set(c, u, 1, v, tuff(p, u, 1, v));
				p.set(c, u, 2, v, pave(p, u, 2, v));
			}
		}
		// front piers (even offset from the corner) and niches (the rest), beside the stair
		for (int u = g.p0(); u <= g.p1(); u++) {
			int du = Math.abs(u - g.cu());
			if (du <= g.sh() + 1) {
				continue;
			}
			boolean pier = (g.ha() - du) % 2 == 0;
			if (pier) {
				p.set(c, u, 0, VF - 1, foot(p, u, 0, VF - 1));
				p.set(c, u, 1, VF - 1, tuff(p, u, 1, VF - 1));
				p.set(c, u, 2, VF - 1, b(Blocks.CHISELED_TUFF));
			} else {
				p.set(c, u, 1, VF, b(Blocks.LANTERN)); // the niche, a lantern on the dark course
			}
		}
		// side and back buttresses in the margin: a pier every fourth cell, with a slab cap
		for (int v = VF + 1; v <= g.pv1(); v += 3) {
			for (int u : new int[] {g.p0() - 1, g.p1() + 1}) {
				p.set(c, u, 0, v, foot(p, u, 0, v));
				p.set(c, u, 1, v, slabTop(Blocks.TUFF_BRICK_SLAB));
			}
		}
		for (int u = g.p0() + 2; u <= g.p1() - 2; u += 3) {
			p.set(c, u, 0, g.pv1() + 1, foot(p, u, 0, g.pv1() + 1));
			p.set(c, u, 1, g.pv1() + 1, slabTop(Blocks.TUFF_BRICK_SLAB));
		}
	}

	/** The front stair: three steps with sloping cheeks, a lantern pillar at the top of each cheek. */
	private static void stairs(CitadelCanvas c, Plot p, G g) {
		for (int k = 0; k <= TOP; k++) {
			for (int du = -g.sh(); du <= g.sh(); du++) {
				int u = g.cu() + du;
				for (int y = 0; y < k; y++) {
					p.set(c, u, y, k, tuff(p, u, y, k));
				}
				p.set(c, u, k, k, stair(Blocks.TUFF_BRICK_STAIRS, Direction.SOUTH, false));
			}
			for (int s : new int[] {-1, 1}) {
				int u = g.cu() + s * (g.sh() + 1);
				for (int y = 0; y <= k; y++) {
					p.set(c, u, y, k, y == 0 ? foot(p, u, y, k) : tuff(p, u, y, k));
				}
				p.set(c, u, k + 1, k, stair(Blocks.TUFF_BRICK_STAIRS, Direction.SOUTH, false));
			}
		}
		// the cheeks end in a pillar with a lantern
		for (int s : new int[] {-1, 1}) {
			int u = g.cu() + s * (g.sh() + 1);
			p.set(c, u, TOP + 1, VF, b(Blocks.TUFF_BRICK_WALL));
			p.set(c, u, TOP + 2, VF, b(Blocks.TUFF_BRICK_WALL));
			p.set(c, u, TOP + 3, VF, b(Blocks.LANTERN));
		}
	}

	/** [depth] a tuff-brick balustrade round the promenade, linked by hand, with posts at the ends and corners. */
	private static void balustrade(CitadelCanvas c, Plot p, G g) {
		Links run = new Links();
		java.util.List<int[]> posts = new java.util.ArrayList<>();
		int open = g.sh() + 1;
		for (int u = g.p0(); u <= g.p1(); u++) {
			for (int v = VF; v <= g.pv1(); v++) {
				boolean ring = u == g.p0() || u == g.p1() || v == VF || v == g.pv1();
				if (!ring || (v == VF && Math.abs(u - g.cu()) <= open)) {
					continue;
				}
				boolean corner = (u == g.p0() || u == g.p1()) && (v == VF || v == g.pv1());
				boolean end = v == VF && Math.abs(u - g.cu()) == open + 1;
				if (corner || end) {
					posts.add(new int[] {u, v, corner ? 1 : 0});
					run.partner(u, v);
				} else {
					run.add(u, v);
				}
			}
		}
		run.emitWalls(c, p, TOP + 1, Blocks.TUFF_BRICK_WALL);
		for (int[] q : posts) {
			p.set(c, q[0], TOP + 1, q[1], b(Blocks.POLISHED_TUFF));
			p.set(c, q[0], TOP + 2, q[1], q[2] == 1 ? b(Blocks.LANTERN) : slab(Blocks.POLISHED_TUFF_SLAB));
		}
	}

	// ------------------------------------------------------------------ ground floor (y3..y5)

	private static boolean colAt(G g, int u) {
		int du = Math.abs(u - g.cu());
		return du == g.hb() || (du >= 2 && du % 2 == 0);
	}

	private static void ground(CitadelCanvas c, Plot p, G g) {
		int cu = g.cu();
		for (int u = g.b0(); u <= g.b1(); u++) {
			// colonnade at v 5: quartz pillars on a base and under a capital
			if (colAt(g, u)) {
				for (int y = 3; y <= 5; y++) {
					p.set(c, u, y, VCOL, pillar());
				}
			}
			// the recessed front wall at v 6
			for (int y = 3; y <= 5; y++) {
				int du = Math.abs(u - cu);
				BlockState st;
				if (u == cu && y <= 4) {
					st = door(Blocks.CHERRY_DOOR, Direction.NORTH, y == 3);
				} else if (u == cu) {
					st = log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X);
				} else if (du == 1 && y >= 4) {
					st = pane(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK), true); // sidelights
				} else if (y == 3) {
					st = tuff(p, u, y, VWALL);
				} else if (y == 4 && du >= 2 && du < g.hb() && !colAt(g, u)) {
					window(c, p, u, y, VWALL, u, VWALL + 1, true);
					continue;
				} else {
					st = white(p, u, y, VWALL);
				}
				p.set(c, u, y, VWALL, st);
			}
			// back wall
			for (int y = 3; y <= 5; y++) {
				boolean win = y == 4 && Math.abs(u - cu) % 3 == 1;
				if (win) {
					window(c, p, u, y, g.ve(), u, g.ve() - 1, true);
				} else {
					p.set(c, u, y, g.ve(), y == 3 ? tuff(p, u, y, g.ve()) : white(p, u, y, g.ve()));
				}
			}
		}
		// side walls (v 6..ve) with posts every third cell
		for (int v = VWALL + 1; v < g.ve(); v++) {
			for (int u : new int[] {g.b0(), g.b1()}) {
				boolean post = (v - VWALL) % 3 == 0;
				for (int y = 3; y <= 5; y++) {
					if (!post && y == 4) {
						window(c, p, u, y, v, u == g.b0() ? u + 1 : u - 1, v, false);
						continue;
					}
					BlockState st = post ? pillar() : y == 3 ? tuff(p, u, y, v) : white(p, u, y, v);
					p.set(c, u, y, v, st);
				}
			}
		}
		// the corner pillars of the ground floor, and the floor between the storeys
		for (int u : new int[] {g.b0(), g.b1()}) {
			for (int y = 3; y <= 5; y++) {
				p.set(c, u, y, VWALL, pillar());
				p.set(c, u, y, g.ve(), pillar());
			}
		}
		// [depth] architrave over the colonnade: a cherry beam with a copper-trimmed upper edge
		for (int u = g.b0(); u <= g.b1(); u++) {
			p.set(c, u, ARCH, VCOL, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
			for (int v = VCOL + 1; v <= g.ve(); v++) {
				boolean side = u == g.b0() || u == g.b1();
				boolean edge = side || v == g.ve();
				p.set(c, u, ARCH, v, edge ? log(Blocks.STRIPPED_CHERRY_LOG, side ? Direction.Axis.Z : Direction.Axis.X) : b(Blocks.SPRUCE_PLANKS));
			}
		}
		// [finish] lanterns hanging from the architrave in the bays, planters between the pillars
		for (int u = g.b0() + 1; u < g.b1(); u++) {
			if (colAt(g, u)) {
				continue;
			}
			int du = Math.abs(u - cu);
			if (du == 1) {
				p.put(c, u, 5, VCOL, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
			} else if (du >= 2) {
				p.set(c, u, 3, VCOL, b(p.hash(u, 3, VCOL, 2) == 0 ? Blocks.POTTED_FLOWERING_AZALEA : Blocks.POTTED_FERN));
			}
		}
	}

	// ------------------------------------------------------------------ upper storey (y7..y9)

	private static void upper(CitadelCanvas c, Plot p, G g) {
		int cu = g.cu();
		Links fence = new Links();
		fence.partner(g.b0() - 1, VCOL).partner(g.b1() + 1, VCOL);
		for (int u = g.b0(); u <= g.b1(); u++) {
			int du = Math.abs(u - cu);
			boolean col = colAt(g, u);
			boolean centre = du <= 1;
			for (int y = 7; y <= 9; y++) {
				if (col) {
					p.set(c, u, y, VCOL, pillar()); // [depth] the giant order: pillars run both storeys, a block in front of the wall
				}
				BlockState st;
				if (y == 9) {
					st = mix(p, u, y, VWALL, Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_BRICKS, Blocks.CHISELED_QUARTZ_BLOCK);
				} else if (!centre && du >= 3 && !col) {
					window(c, p, u, y, VWALL, u, VWALL + 1, true);
					continue;
				} else if (centre) {
					st = pane(Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK), true);
				} else {
					st = white(p, u, y, VWALL);
				}
				p.set(c, u, y, VWALL, st);
			}
			if (!col) {
				fence.add(u, VCOL);
			} else {
				fence.partner(u, VCOL);
			}
			// back wall
			for (int y = 7; y <= 9; y++) {
				boolean win = y == 8 && du % 3 == 1;
				if (win) {
					window(c, p, u, y, g.ve(), u, g.ve() - 1, true);
				} else {
					p.set(c, u, y, g.ve(), y == 9 ? mix(p, u, y, g.ve(), Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_BRICKS, Blocks.CHISELED_QUARTZ_BLOCK) : white(p, u, y, g.ve()));
					}
				}
			}
		for (int v = VCOL + 1; v < g.ve(); v++) {
			for (int u : new int[] {g.b0(), g.b1()}) {
				boolean post = (v - VCOL) % 3 == 0;
				for (int y = 7; y <= 9; y++) {
					if (!post && y == 8) {
						window(c, p, u, y, v, u == g.b0() ? u + 1 : u - 1, v, false);
						continue;
					}
					BlockState st = post ? pillar() : y == 9 ? mix(p, u, y, v, Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_BRICKS, Blocks.CHISELED_QUARTZ_BLOCK) : white(p, u, y, v);
					p.set(c, u, y, v, st);
				}
			}
		}
		for (int u : new int[] {g.b0(), g.b1()}) {
			for (int y = 7; y <= 9; y++) {
				p.set(c, u, y, g.ve(), pillar());
			}
		}
		// the loggia's balcony: cherry fences between the pillars, linked to them by hand
		fence.emitFences(c, p, 7, Blocks.CHERRY_FENCE);
		// [depth] a peristyle on the sides and the back: pillars on the podium edge, under the cornice
		for (int v = VWALL + 2; v <= g.ve(); v++) {
			if ((v - VWALL) % 3 == 0) {
				for (int y = 3; y <= 9; y++) {
					p.set(c, g.b0() - 1, y, v, pillar());
					p.set(c, g.b1() + 1, y, v, pillar());
				}
			}
		}
		for (int u = g.b0() + 1; u < g.b1(); u++) {
			if (Math.abs(u - cu) % 3 == 0) {
				for (int y = 3; y <= 9; y++) {
					p.set(c, u, y, g.ve() + 1, pillar());
				}
			}
		}
		// pilaster capitals: the pillars of the colonnade end in a chiselled block at y6
		for (int u = g.b0(); u <= g.b1(); u++) {
			if (colAt(g, u)) {
				p.set(c, u, ARCH, VCOL, b(Blocks.CHISELED_QUARTZ_BLOCK));
			}
		}
	}

	/**
	 * [depth] relief for the side and back walls: a stone sill with a flower pot under every recessed window and a
	 * hooded lintel (an upside-down stair) over it, on both storeys, between the peristyle pillars.
	 */
	private static void relief(CitadelCanvas c, Plot p, G g) {
		for (int s = 0; s < 2; s++) {
			int u = s == 0 ? g.b0() - 1 : g.b1() + 1;
			Direction in = s == 0 ? Direction.EAST : Direction.WEST;
			for (int v = VWALL + 1; v < g.ve(); v++) {
				if ((v - VWALL) % 3 == 0) {
					continue; // a pillar stands here
				}
				for (int wy : new int[] {4, 8}) {
					p.set(c, u, wy - 1, v, stair(Blocks.TUFF_BRICK_STAIRS, in, true)); // sill
					p.set(c, u, wy, v, b(p.hash(u, wy, v, 2) == 0 ? Blocks.POTTED_FLOWERING_AZALEA : Blocks.POTTED_FERN));
					p.set(c, u, wy + 1, v, stair(copperStairs(), in, true)); // hooded lintel
				}
			}
		}
		for (int u = g.b0() + 1; u < g.b1(); u++) {
			if (Math.abs(u - g.cu()) % 3 == 0) {
				continue;
			}
			int v = g.ve() + 1;
			for (int wy : new int[] {4, 8}) {
				if (Math.abs(u - g.cu()) % 3 != 1) {
					continue; // only the cells in front of a back window
				}
				p.set(c, u, wy - 1, v, stair(Blocks.TUFF_BRICK_STAIRS, Direction.NORTH, true));
				p.set(c, u, wy, v, b(p.hash(u, wy, v, 2) == 0 ? Blocks.POTTED_FLOWERING_AZALEA : Blocks.POTTED_FERN));
				p.set(c, u, wy + 1, v, stair(copperStairs(), Direction.NORTH, true));
			}
		}
	}

	// ------------------------------------------------------------------ roof deck and garden (y10 / y11)

	private static boolean ringCell(G g, int u, int v) {
		int u0 = g.b0() - 1;
		int u1 = g.b1() + 1;
		return u == u0 || u == u1 || v == VCOL - 1 || v == g.ve() + 1;
	}

	/**
	 * [finish] the cornice: a ring of quartz slab with an oxidised-copper stair on the outer lip and a
	 * cove under it; inside, a parapet of tuff wall linked by hand, planter beds along it, a bamboo
	 * mosaic floor with a quartz path.
	 */
	private static void deck(CitadelCanvas c, Plot p, G g, Random r) {
		int u0 = g.b0() - 1;
		int u1 = g.b1() + 1;
		int v0 = VCOL - 1;
		int v1 = g.ve() + 1;
		Links parapet = new Links();
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				boolean ring = ringCell(g, u, v);
				BlockState st;
				if (ring) {
					st = white(p, u, DECK, v);
					Direction out = v == v0 ? Direction.NORTH : v == v1 ? Direction.SOUTH : u == u0 ? Direction.WEST : Direction.EAST;
					// the cove under the lip, facing the wall
					Direction in = out.getOpposite();
					if (!(u == u0 || u == u1) || !(v == v0 || v == v1)) {
						p.set(c, u, DECK - 1, v, stair(copperStairs(), in, true));
					}
				} else if (u == g.cu() || p.hash(u, DECK, v, 5) == 0) {
					st = b(Blocks.BAMBOO_MOSAIC);
				} else {
					st = mix(p, u, DECK, v, Blocks.BAMBOO_PLANKS, Blocks.BAMBOO_MOSAIC, Blocks.BAMBOO_PLANKS);
				}
				p.set(c, u, DECK, v, st);
				if (ring) {
					parapet.add(u, v);
				}
			}
		}
		// the parapet: tuff wall on the lip, cherry posts at corners and every fourth cell
		java.util.List<int[]> posts = new java.util.ArrayList<>();
		Links walls = new Links();
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				if (!ringCell(g, u, v)) {
					continue;
				}
				boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
				boolean edgeU = v == v0 || v == v1;
				boolean post = corner;
				if (post) {
					posts.add(new int[] {u, v, corner ? 1 : 0});
					walls.partner(u, v);
				} else {
					walls.add(u, v);
				}
			}
		}
		walls.emitWalls(c, p, DECK + 1, Blocks.TUFF_BRICK_WALL);
		for (int[] q : posts) {
			p.set(c, q[0], DECK + 1, q[1], b(Blocks.POLISHED_TUFF));
			p.set(c, q[0], DECK + 2, q[1], q[2] == 1 ? b(Blocks.LANTERN) : slab(Blocks.POLISHED_TUFF_SLAB));
		}
		// planter beds along the inside of the parapet (the pergola and the hut keep their own corners)
		for (int u = u0 + 1; u < u1; u++) {
			for (int v = v0 + 1; v < v1; v++) {
				boolean border = u == u0 + 1 || u == u1 - 1 || v == v0 + 1 || v == v1 - 1;
				if (!border || inPergola(g, u, v) || inHut(g, u, v) || (u == g.cu() && v == v0 + 1)) {
					continue;
				}
				p.set(c, u, DECK, v, mix(p, u, DECK, v, Blocks.MOSS_BLOCK, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.COARSE_DIRT));
				int k = p.hash(u, DECK, v, 12);
				BlockState plant = switch (k) {
					case 0, 1 -> b(Blocks.AZALEA);
					case 2, 3 -> b(Blocks.FLOWERING_AZALEA);
					case 4 -> b(Blocks.PINK_PETALS);
					case 5 -> b(Blocks.ALLIUM);
					case 6 -> b(Blocks.CORNFLOWER);
					case 7 -> b(Blocks.SHORT_GRASS);
					case 8 -> b(Blocks.FERN);
					case 9 -> b(Blocks.PINK_TULIP);
					default -> null;
				};
				if (plant != null) {
					p.set(c, u, DECK + 1, v, plant);
				}
			}
		}
	}

	// ------------------------------------------------------------------ pergola and hut

	private static int pu0(G g) {
		return g.cu() - 1;
	}

	private static int pv0() {
		return VCOL + 1;
	}

	private static boolean inPergola(G g, int u, int v) {
		return u >= pu0(g) - 1 && u <= pu0(g) + 3 && v >= pv0() - 1 && v <= pv0() + 3;
	}

	private static int hu0(G g) {
		return g.b1() - 2;
	}

	private static int hv0(G g) {
		return g.ve() - 2;
	}

	private static boolean inHut(G g, int u, int v) {
		return u >= hu0(g) - 1 && u <= hu0(g) + 2 && v >= hv0(g) - 1 && v <= g.ve() + 1;
	}

	/**
	 * [depth] a 3×3 pergola: cherry posts, two long beams, cross-slats of cherry slab, hanging
	 * lanterns and a few flowering leaves draped on top; under it a table and two benches.
	 */
	private static void pergola(CitadelCanvas c, Plot p, G g, Random r) {
		int u0 = pu0(g);
		int u1 = u0 + 2;
		int v0 = pv0();
		int v1 = v0 + 2;
		int top = DECK + 4;
		for (int u : new int[] {u0, u1}) {
			for (int v : new int[] {v0, v1}) {
				for (int y = DECK + 1; y < top; y++) {
					p.set(c, u, y, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
				}
			}
		}
		for (int u = u0 - 1; u <= u1 + 1; u++) {
			for (int v : new int[] {v0, v1}) {
				p.set(c, u, top, v, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.X));
			}
		}
		for (int u = u0; u <= u1; u++) {
			p.set(c, u, top, v0 + 1, u == u0 + 1 ? slabTop(Blocks.CHERRY_SLAB) : slab(Blocks.CHERRY_SLAB));
		}
		// [finish] a real hipped roof on the pavilion: an oxidised-copper eave ring over the beams, a cherry ring above
		// it, a cherry core, a copper cap and a lantern finial. Corners are slabs so the hips read cleanly.
		int mu0 = u0 - 1;
		int mu1 = u1 + 1;
		int mv0 = v0 - 1;
		int mv1 = v1 + 1;
		for (int u = mu0; u <= mu1; u++) {
			for (int v = mv0; v <= mv1; v++) {
				boolean edgeU = u == mu0 || u == mu1;
				boolean edgeV = v == mv0 || v == mv1;
				if (edgeU && edgeV) {
					p.set(c, u, top + 1, v, slab(Blocks.CUT_COPPER_SLAB.waxed().oxidized()));
				} else if (edgeU || edgeV) {
					Direction in = edgeV ? (v == mv0 ? Direction.SOUTH : Direction.NORTH) : (u == mu0 ? Direction.EAST : Direction.WEST);
					p.set(c, u, top + 1, v, stair(copperStairs(), in, false));
				} else {
					p.set(c, u, top + 1, v, b(Blocks.CHERRY_PLANKS));
				}
			}
		}
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				boolean edgeU = u == u0 || u == u1;
				boolean edgeV = v == v0 || v == v1;
				if (edgeU && edgeV) {
					p.set(c, u, top + 2, v, slab(Blocks.CHERRY_SLAB));
				} else if (edgeU || edgeV) {
					Direction in = edgeV ? (v == v0 ? Direction.SOUTH : Direction.NORTH) : (u == u0 ? Direction.EAST : Direction.WEST);
					p.set(c, u, top + 2, v, stair(Blocks.CHERRY_STAIRS, in, false));
				} else {
					p.set(c, u, top + 2, v, b(Blocks.CHERRY_PLANKS));
				}
			}
		}
		p.set(c, u0 + 1, top + 3, v0 + 1, slab(Blocks.CUT_COPPER_SLAB.waxed().oxidized()));
		p.set(c, u0 + 1, top + 4, v0 + 1, b(Blocks.LANTERN));
		// under the pergola: table, benches facing it, lanterns
		int mu = u0 + 1;
		int mv = v0 + 1;
		p.set(c, mu, DECK + 1, mv, b(Blocks.CHERRY_FENCE));
		p.set(c, mu, DECK + 2, mv, slab(Blocks.CHERRY_SLAB));
		p.set(c, u0, DECK + 1, mv, stair(Blocks.CHERRY_STAIRS, Direction.WEST, false));
		p.set(c, u1, DECK + 1, mv, stair(Blocks.CHERRY_STAIRS, Direction.EAST, false));
		p.put(c, u0 + 1, top - 1, v0, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
		p.put(c, u0 + 1, top - 1, v1, hanging(Blocks.LANTERN), CitadelCanvas.HANGING);
	}

	/** A 2×2 stair hut on the back corner: white walls, a door to the front, a copper lean-to cap. */
	private static void hut(CitadelCanvas c, Plot p, G g) {
		int u0 = hu0(g);
		int v0 = hv0(g);
		int u1 = u0 + 1;
		int v1 = v0 + 1;
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				for (int y = DECK + 1; y <= DECK + 2; y++) {
					p.set(c, u, y, v, white(p, u, y, v));
				}
			}
		}
		p.set(c, u0, DECK + 1, v0, door(Blocks.CHERRY_DOOR, Direction.NORTH, true));
		p.set(c, u0, DECK + 2, v0, door(Blocks.CHERRY_DOOR, Direction.NORTH, false));
		p.set(c, u1, DECK + 2, v0, glass(p, u1, v0, true));
		for (int u = u0 - 1; u <= u1 + 1; u++) {
			p.set(c, u, DECK + 3, v0 - 1, stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH, false));
			p.set(c, u, DECK + 3, v0, stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH, false));
			p.set(c, u, DECK + 4, v1, slab(Blocks.QUARTZ_SLAB));
			p.set(c, u, DECK + 3, v1, stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH, false));
		}
	}

	// ------------------------------------------------------------------ forecourt (v 0..2 beside the stair)

	/** [finish] hedges and slender bamboo against the podium, so it rises out of a garden. */
	private static void forecourt(CitadelCanvas c, Plot p, G g, Random r) {
		for (int u = g.p0() - 1; u <= g.p1() + 1; u++) {
			int du = Math.abs(u - g.cu());
			if (du <= g.sh() + 1) {
				continue; // the stair and its cheeks
			}
			boolean pier = (g.ha() - du) % 2 == 0 && du <= g.ha();
			int v = pier ? 1 : 2;
			int k = p.hash(u, 0, v, 5);
			if (k <= 1) {
				p.set(c, u, 0, v, leaves(Blocks.AZALEA_LEAVES));
				p.set(c, u, 1, v, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			} else if (k == 2) {
				for (int y = 0; y <= 3; y++) {
					p.set(c, u, y, v, b(Blocks.BAMBOO));
				}
			} else if (k == 3) {
				p.set(c, u, 0, v, b(Blocks.DECORATED_POT));
			} else {
				p.set(c, u, 0, v, leaves(Blocks.AZALEA_LEAVES));
			}
		}
	}
}
