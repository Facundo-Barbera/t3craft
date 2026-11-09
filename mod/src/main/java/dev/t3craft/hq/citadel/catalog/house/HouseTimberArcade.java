package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.door;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.gable;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.gableY;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.gapWin;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.groundPlant;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.log;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.mix;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.pane;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.slab;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.stair;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.house.TimberKit.Cell;
import java.util.Random;
import java.util.function.IntPredicate;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A long, low merchant's house. The whole front is an <b>arcade</b> (a covered walk) of cherry piers
 * and timber arches; the plastered upper floor sits flush on the piers above it. A cross gable with a
 * lit window and a cantilevered balcony marks the entrance bay, and the long roof of golden bamboo
 * mosaic has a dark tile eave, a marked ridge and gold finials at both ends.
 *
 * <pre>
 *  section through the entrance bay (front on the left)
 *   y9+   roof ───────╲╲╲╲╲╲        upper floor y6–8 on the piers, plaster
 *   y5    beam  ━━━━━━━━━━━━        arcade ceiling, hanging lanterns
 *   y1-4  ▐ arch ▌ ▐ door ▌         portico v1–3, house core v4+
 *   y0    steps ▂ portico floor
 *     v0   v1     v4        vb
 * </pre>
 *
 * Silhouette: long and low with a hip-like repeated rhythm of arches and one raised centre, against
 * the tall narrow corner house. Palette: stripped cherry, warm white plaster, tuff and deepslate,
 * bamboo roof, gold. Size: 15–24 × 9–11 × 17+.
 */
public final class HouseTimberArcade implements Variant {
	private static final int PLATE = 9;

	@Override
	public String id() {
		return "house_timber_arcade";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 15;
	}

	@Override
	public int minD() {
		return 9;
	}

	@Override
	public int minH() {
		return 17;
	}

	@Override
	public int maxW() {
		return 24;
	}

	@Override
	public int maxD() {
		return 11;
	}

	/** [palette] the golden roof always, but a dark slate or a dark oak line along the eaves and the shutters to match. */
	private record Scheme(Block eave, Block shutter) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.BAMBOO_TRAPDOOR),
			new Scheme(Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_TRAPDOOR),
	};

	/** Pier columns along the front, the door column {@code cd} and the inner depth {@code vb}. */
	private record Geo(int w, int d, int[] pier, int cd) {
		int vb() {
			return d - 2;
		}

		boolean isPier(int u) {
			for (int k : pier) {
				if (k == u) {
					return true;
				}
			}
			return false;
		}

		static Geo of(int w, int d) {
			int bays = Math.max(2, (w - 3) / 4);
			int[] pier = new int[bays + 1];
			for (int k = 0; k <= bays; k++) {
				pier[k] = 1 + (k * (w - 3) + bays / 2) / bays;
			}
			int cc = (w - 1) / 2;
			int bay = 0;
			for (int k = 0; k < bays; k++) {
				if (pier[k] <= cc) {
					bay = k;
				}
			}
			return new Geo(w, d, pier, (pier[bay] + pier[bay + 1]) / 2);
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0xA4CADE5L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		Geo g = Geo.of(p.w(), p.d());
		footing(c, p, g);
		floors(c, p, g);
		arcade(c, p, g);
		for (int fl = 0; fl < 2; fl++) {
			walls(c, p, g, s, fl);
		}
		roof(c, p, g, s);
		gables(c, p, g);
		crossGable(c, p, g, s);
		balcony(c, p, g);
		entrance(c, p, g);
		chimney(c, p, g);
		porticoFurniture(c, p, g);
		strips(c, p, g);
	}

	// ------------------------------------------------------------------ materials

	/** [palette] warm tuff over a dark deepslate foot; polished tuff and cracked bricks break the pattern. */
	private static BlockState stone(Plot p, int u, int y, int v) {
		if (y <= 0) {
			return mix(p, u, y, v, b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_TILES), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.POLISHED_DEEPSLATE));
		}
		return mix(p, u, y, v, b(Blocks.TUFF_BRICKS), b(Blocks.TUFF_BRICKS), b(Blocks.POLISHED_TUFF), b(Blocks.TUFF_BRICKS), b(Blocks.DEEPSLATE_BRICKS));
	}

	/** [palette] a creamy terracotta plaster broken up with calcite. */
	private static BlockState plaster(Plot p, int u, int y, int v) {
		return mix(p, u, y, v, CitadelCanvas.terracotta(DyeColor.WHITE), CitadelCanvas.terracotta(DyeColor.WHITE), b(Blocks.CALCITE), CitadelCanvas.terracotta(DyeColor.WHITE));
	}

	/** [palette] light stripped cherry posts against the dark cherry beams. */
	private static BlockState post() {
		return log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y);
	}

	private static BlockState beam(Direction.Axis axis) {
		return log(Blocks.CHERRY_LOG, axis);
	}

	// ------------------------------------------------------------------ footing and floors

	private static void footing(CitadelCanvas c, Plot p, Geo g) {
		int w = g.w();
		int vb = g.vb();
		for (int u = 1; u <= w - 2; u++) {
			for (int v = 1; v <= vb; v++) {
				boolean edge = u == 1 || u == w - 2 || v == 1 || v == vb;
				if (v <= 3 || edge) {
					p.set(c, u, 0, v, stone(p, u, 0, v)); // the portico floor is flagstone
				} else {
					p.set(c, u, 0, v, b(Blocks.SPRUCE_PLANKS));
				}
			}
		}
		// [depth] the footing step round the sides and the back
		for (int v = 1; v <= vb; v++) {
			p.set(c, 0, 0, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.EAST, false));
			p.set(c, w - 1, 0, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.WEST, false));
		}
		for (int u = 1; u <= w - 2; u++) {
			p.set(c, u, 0, g.d() - 1, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH, false));
		}
	}

	private static void floors(CitadelCanvas c, Plot p, Geo g) {
		int w = g.w();
		int vb = g.vb();
		for (int u = 1; u <= w - 2; u++) {
			for (int v = 1; v <= vb; v++) {
				boolean edgeV = v == 1 || v == vb;
				boolean edge = edgeV || u == 1 || u == w - 2;
				Direction.Axis ax = edgeV ? Direction.Axis.X : Direction.Axis.Z;
				if (edge) {
					p.set(c, u, 5, v, beam(ax));
					p.set(c, u, PLATE, v, beam(ax));
				} else if (v <= 3 && g.isPier(u)) {
					p.set(c, u, 5, v, beam(Direction.Axis.Z)); // [depth] cross beams in the portico ceiling
				} else {
					p.set(c, u, 5, v, b(Blocks.SPRUCE_PLANKS));
				}
			}
		}
	}

	// ------------------------------------------------------------------ the arcade

	/**
	 * [depth] The arcade: stripped cherry piers on a dark foot, and in every bay a timber arch made of
	 * upside-down stair corbels under a plank spandrel with a slab crown. Lanterns hang in the middle
	 * of each bay; a flight of steps leads in.
	 */
	private static void arcade(CitadelCanvas c, Plot p, Geo g) {
		int[] pier = g.pier();
		for (int k = 0; k < pier.length; k++) {
			int u = pier[k];
			p.set(c, u, 1, 1, b(Blocks.POLISHED_DEEPSLATE));
			for (int y = 2; y <= 4; y++) {
				p.set(c, u, y, 1, log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y));
			}
			// the pier's foot outside: a pot on a stone block
			p.set(c, u, 0, 0, b(Blocks.POLISHED_DEEPSLATE));
			p.set(c, u, 1, 0, (k & 1) == 0 ? Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState() : Blocks.POTTED_CHERRY_SAPLING.defaultBlockState());
			if (k + 1 >= pier.length) {
				continue;
			}
			int a = u + 1;
			int bb = pier[k + 1] - 1;
			int m = bb - a + 1;
			for (int x = a; x <= bb; x++) {
				boolean crown = m >= 3 && x != a && x != bb;
				p.set(c, x, 4, 1, crown ? slab(Blocks.CHERRY_SLAB, true) : b(Blocks.CHERRY_PLANKS));
				p.set(c, x, 0, 0, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
				if (m >= 3 && (x == a || x == bb)) {
					p.set(c, x, 3, 1, stair(Blocks.CHERRY_STAIRS, x == a ? Direction.WEST : Direction.EAST, true));
				}
			}
			p.put(c, (a + bb) / 2, 4, 2, lantern(true), CitadelCanvas.HANGING);
		}
	}

	// ------------------------------------------------------------------ walls

	private void walls(CitadelCanvas c, Plot p, Geo g, Scheme s, int fl) {
		int w = g.w();
		int vb = g.vb();
		int cd = g.cd();
		IntPredicate postP = g::isPier;
		IntPredicate winP = i -> Math.abs(i - cd) > 1 && gapWin(postP, i, 1, w - 2);
		int front = fl == 0 ? 4 : 1;
		seg(c, p, s, fl, true, front, -1, 1, w - 2, postP, winP);
		seg(c, p, s, fl, true, vb, 1, 1, w - 2, postP, i -> gapWin(postP, i, 1, w - 2));
		IntPredicate postS = fl == 0 ? i -> i == 1 || i == 4 || i == vb : i -> i == 1 || i == vb || ((i - 1) % 4 == 0 && vb - i >= 2);
		int sa = fl == 0 ? 4 : 1;
		IntPredicate winS = i -> i > sa && gapWin(postS, i, sa, vb);
		seg(c, p, s, fl, false, 1, -1, 1, vb, postS, winS);
		seg(c, p, s, fl, false, w - 2, 1, 1, vb, postS, winS);
	}

	private void seg(CitadelCanvas c, Plot p, Scheme s, int fl, boolean alongU, int fixed, int outward, int a, int b,
			IntPredicate post, IntPredicate win) {
		int y1 = fl == 0 ? 1 : 6;
		int y2 = fl == 0 ? 4 : 8;
		int w1 = fl == 0 ? 2 : 7;
		int w2 = fl == 0 ? 3 : 8;
		int in = fixed - outward;
		Cell rail = null;
		if (fl == 0) {
			rail = (i, y) -> y == 1 ? stone(p, alongU ? i : fixed, 1, alongU ? fixed : i) : null;
		}
		Cell fill = (i, y) -> plaster(p, alongU ? i : in, y, alongU ? in : i);
		TimberKit.wall(c, p, alongU, fixed, outward, a, b, y1, y2, post, win, w1, w2, null, (i, y) -> post(), rail, fill);
		TimberKit.dress(c, p, alongU, fixed, outward, a, b, post, win, w1, w2, Blocks.CHERRY_STAIRS, s.shutter(), false);
	}

	/** The door of the house core, recessed behind the arcade. */
	private static void entrance(CitadelCanvas c, Plot p, Geo g) {
		int cd = g.cd();
		p.set(c, cd, 1, 4, CitadelCanvas.AIR);
		p.set(c, cd, 1, 5, door(Blocks.CHERRY_DOOR, Direction.NORTH, false));
		p.set(c, cd, 2, 5, door(Blocks.CHERRY_DOOR, Direction.NORTH, true));
		p.set(c, cd, 3, 5, pane(true, DyeColor.YELLOW));
	}

	// ------------------------------------------------------------------ roof

	private static void roof(CitadelCanvas c, Plot p, Geo g, Scheme s) {
		gable(c, p, true, 0, g.w() - 1, 0, g.d() - 1, PLATE, Blocks.DEEPSLATE_TILE_STAIRS, s.eave(), Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB, true, true);
		// [finish] gold finials on both ridge ends
		int mid = (g.d() - 1) / 2;
		int top = PLATE + (g.d() - 1) / 2 + 2;
		for (int u : new int[] {0, g.w() - 1}) {
			p.set(c, u, top, mid, b(Blocks.DEEPSLATE_TILE_WALL));
			p.set(c, u, top + 1, mid, Blocks.LIGHTNING_ROD.waxed().exposed().defaultBlockState());
		}
	}

	/** [palette] the gable ends: plaster in the triangle, a king post and a pair of pink lights. */
	private static void gables(CitadelCanvas c, Plot p, Geo g) {
		int d = g.d();
		int vb = g.vb();
		int m0 = (d - 1) / 2;
		int m1 = d / 2;
		for (int u : new int[] {1, g.w() - 2}) {
			for (int v = 1; v <= vb; v++) {
				int top = gableY(v, 0, d - 1, PLATE);
				for (int y = PLATE + 1; y < top; y++) {
					BlockState st;
					if (v == m0 || v == m1) {
						st = post();
					} else if (y == PLATE + 1 && (v == m0 - 1 || v == m1 + 1)) {
						st = pane(false, DyeColor.PINK);
					} else {
						st = plaster(p, u, y, v);
					}
					p.set(c, u, y, v, st);
				}
			}
		}
	}

	/**
	 * [finish] A cross gable over the entrance bay: a second roof, seven wide, with its ridge along v.
	 * It is only drawn where it rises above the main roof, so the valleys form by themselves. The
	 * gable face stands on the upper wall line with a lit window between two posts.
	 */
	private static void crossGable(CitadelCanvas c, Plot p, Geo g, Scheme s) {
		int cd = g.cd();
		int d = g.d();
		for (int u = cd - 3; u <= cd + 3; u++) {
			int dist = Math.abs(u - cd);
			int yc = PLATE + 4 - dist;
			for (int v = 0; v <= (d - 1) / 2; v++) { // front half only: the back slope stays plain
				int ym = gableY(v, 0, d - 1, PLATE);
				if (yc <= ym) {
					continue;
				}
				if (dist == 0) {
					p.set(c, u, yc, v, b(Blocks.DEEPSLATE_TILES));
					p.set(c, u, yc + 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB, false));
				} else {
					Direction f = u < cd ? Direction.EAST : Direction.WEST;
					p.set(c, u, yc, v, stair(dist == 3 ? s.eave() : Blocks.DEEPSLATE_TILE_STAIRS, f, false));
					if (v == 0) {
						p.set(c, u, yc - 1, v, stair(Blocks.DEEPSLATE_TILE_STAIRS, f.getOpposite(), true));
					}
				}
			}
			// the face of the gable on the wall line (v 1)
			for (int y = PLATE + 1; y < yc; y++) {
				BlockState st;
				if (dist == 0) {
					st = y == PLATE + 2 ? pane(true, DyeColor.PINK) : post();
				} else {
					st = plaster(p, u, y, 1);
				}
				p.set(c, u, y, 1, st);
			}
		}
		// the king post sticks out as a little beam end under the ridge
		p.set(c, cd, PLATE + 1, 0, log(Blocks.CHERRY_LOG, Direction.Axis.Z));
	}

	// ------------------------------------------------------------------ balcony

	/**
	 * [depth] A cantilevered balcony in front of the upper door, one deep and three wide, on a row of
	 * upside-down stair corbels: plank deck, cherry fence rail, lantern posts and pots.
	 */
	private static void balcony(CitadelCanvas c, Plot p, Geo g) {
		int cd = g.cd();
		for (int u = cd - 1; u <= cd + 1; u++) {
			p.set(c, u, 5, 0, b(Blocks.CHERRY_PLANKS));
			p.set(c, u, 4, 0, stair(Blocks.CHERRY_STAIRS, Direction.SOUTH, true));
			p.set(c, u, 6, 0, fence(Blocks.CHERRY_FENCE, false, u < cd + 1, false, u > cd - 1));
		}
		p.set(c, cd - 1, 6, 0, fence(Blocks.CHERRY_FENCE, false, true, false, false));
		p.set(c, cd + 1, 6, 0, fence(Blocks.CHERRY_FENCE, false, false, false, true));
		p.set(c, cd - 1, 7, 0, lantern(false));
		p.set(c, cd + 1, 7, 0, lantern(false));
		p.set(c, cd, 6, 0, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		// glazed door onto the balcony
		p.set(c, cd, 6, 2, door(Blocks.CHERRY_DOOR, Direction.NORTH, false));
		p.set(c, cd, 7, 2, door(Blocks.CHERRY_DOOR, Direction.NORTH, true));
		p.set(c, cd, 8, 2, pane(true, DyeColor.PINK));
		p.set(c, cd, 6, 1, CitadelCanvas.AIR);
	}

	// ------------------------------------------------------------------ chimney and yard

	private static void chimney(CitadelCanvas c, Plot p, Geo g) {
		int u = g.w() - 4;
		int v = g.d() - 3;
		int top = Math.min(PLATE + (g.d() - 1) / 2 + 2, p.h() - 2);
		for (int y = PLATE + 1; y < top; y++) {
			p.set(c, u, y, v, mix(p, u, y, v, b(Blocks.BRICKS), b(Blocks.BRICKS), b(Blocks.DEEPSLATE_BRICKS)));
		}
		p.set(c, u, top, v, slab(Blocks.DEEPSLATE_BRICK_SLAB, false));
	}

	/** [finish] benches between the piers, backs to the wall, and a barrel at the end of the walk. */
	private static void porticoFurniture(CitadelCanvas c, Plot p, Geo g) {
		int[] pier = g.pier();
		for (int k = 0; k + 1 < pier.length; k++) {
			int a = pier[k] + 1;
			int bb = pier[k + 1] - 1;
			if (Math.abs((a + bb) / 2 - g.cd()) <= 1) {
				continue;
			}
			for (int x = a + 1; x < bb; x++) {
				p.set(c, x, 1, 3, stair(Blocks.CHERRY_STAIRS, Direction.SOUTH, false));
			}
			if (bb - a >= 3) {
				p.set(c, a, 1, 3, Blocks.POTTED_FERN.defaultBlockState());
			}
		}
		p.set(c, 2, 1, 2, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		p.set(c, g.w() - 3, 1, 2, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
	}

	/** Planting round the house: azaleas under the back windows, a woodpile and barrels at the ends. */
	private static void strips(CitadelCanvas c, Plot p, Geo g) {
		int w = g.w();
		int back = g.d() - 1;
		for (int u = 1; u <= w - 2; u++) {
			BlockState plant = groundPlant(p, u, 1, back);
			if (plant != null) {
				p.set(c, u, 1, back, plant);
			}
		}
		for (int v = 5; v <= g.vb(); v++) {
			BlockState l = switch (p.hash(0, 1, v, 4)) {
				case 0 -> log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Z);
				case 1 -> CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES);
				case 2 -> b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
				default -> null;
			};
			if (l != null) {
				p.set(c, 0, 1, v, l);
			}
			BlockState rr = groundPlant(p, w - 1, 1, v);
			if (rr != null) {
				p.set(c, w - 1, 1, v, rr);
			}
		}
		p.set(c, 0, 1, 2, CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		p.set(c, w - 1, 1, 2, CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		p.set(c, 0, 2, 2, CitadelCanvas.leaves(Blocks.AZALEA_LEAVES));
		p.set(c, w - 1, 2, 2, CitadelCanvas.leaves(Blocks.AZALEA_LEAVES));
	}
}
