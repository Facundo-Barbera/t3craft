package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.glassPane;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.log;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.paneU;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.stairTop;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.trapdoor;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.wall;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A Kyoto machiya: a narrow, deep town house. A dark wooden lattice (koshi) of open trapdoors hides
 * the plaster ground floor, a split blue noren hangs in the entrance, a two-row canopy (hisashi)
 * shades the street and the upper floor is plaster with slatted windows. The plaster side walls rise
 * above a low tiled roof in stepped fire walls (udatsu); behind the house a small courtyard with a
 * cherry, a stone lantern and a bamboo fence.
 *
 * <pre>
 *  section through the door (v →)                     local layout (front = v 0)
 *  y12  ridge cap ▂▂▂ with end tiles                  v0   bamboo dog-fence (inu-yarai), step, eave lip
 *  y9+  roof, 2:1, lip at both eaves                  v1   lattice / noren / lantern porch, hisashi
 *  y5-7 upper floor, slatted windows                  v2   front wall (door recessed behind the noren)
 *  y4   hisashi (v0 y4, v1 y5) on the beam            v..  house, udatsu at u 1 and w-2
 *  y1-3 koshi lattice over plaster                    d-4  back eave
 *  y0   plinth (deepslate mix)                        d-3… courtyard: gravel, cherry, ishidoro, fence
 * </pre>
 *
 * Size: 9–11 × 12–16 × 14+.
 */
public final class HouseMachiya implements Variant {
	private static final int BEAM1 = 4;
	private static final int PLATE = 8;
	private static final int ROOF0 = 9;

	@Override
	public String id() {
		return "house_machiya";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 9;
	}

	@Override
	public int minD() {
		return 12;
	}

	@Override
	public int minH() {
		return 14;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 16;
	}

	/** [palette] per seed: lattice wood, eave fascia, noren colour. */
	private record Scheme(Block lattice, Block trim, Block fascia, DyeColor noren, Block door) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.SPRUCE_TRAPDOOR, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS, DyeColor.BLUE, Blocks.DARK_OAK_DOOR),
			new Scheme(Blocks.SPRUCE_TRAPDOOR, Blocks.SPRUCE_LOG, Blocks.DARK_OAK_STAIRS, DyeColor.RED, Blocks.CHERRY_DOOR),
			new Scheme(Blocks.SPRUCE_TRAPDOOR, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS, DyeColor.CYAN, Blocks.DARK_OAK_DOOR),
	};

	/** Fixed lines of the plan. */
	private record Geo(int w, int d, int u1, int u2, int vf, int vb, int cu, int rf, int rb) {
		static Geo of(Plot p) {
			int vb = p.d() - 6;
			return new Geo(p.w(), p.d(), 1, p.w() - 2, 2, vb, p.w() / 2, 1, vb + 1);
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x4D41_4348_4959L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		Geo g = Geo.of(p);

		plinth(c, p, g);
		frontGround(c, p, s, g);
		hisashi(c, p, s, g);
		frontUpper(c, p, s, g);
		sidesAndBack(c, p, s, g);
		roof(c, p, s, g);
		udatsu(c, p, g);
		street(c, p, s, g);
		frontPosts(c, p, s, g);
		courtyard(c, p, r, g);
		// walkability audit: a way up to the sealed upper floor
		dev.t3craft.hq.citadel.catalog.InteriorStair.timber(c, p, 1, 5);
	}

	/**
	 * [depth] the hisashi stands on its own posts, one row in front of the lattice, so the facade reads as three
	 * planes: posts and canopy, the lattice fins, the plaster wall. Posts also rise proud of the upper floor.
	 */
	private static void frontPosts(CitadelCanvas c, Plot p, Scheme s, Geo g) {
		int cu = g.cu();
		for (int u : new int[] {g.u1(), g.u2(), cu - 3, cu + 3}) {
			if (u < g.u1() || u > g.u2()) {
				continue;
			}
			p.set(c, u, 0, 0, b(Blocks.POLISHED_DEEPSLATE));
			for (int y = 1; y <= 3; y++) {
				p.set(c, u, y, 0, log(s.trim(), Direction.Axis.Y));
			}
		}
		for (int u = g.u1(); u <= g.u2(); u++) {
			if (isPost(u, g.u1(), g.u2())) {
				for (int y = 6; y <= 7; y++) {
					p.set(c, u, y, 1, log(s.trim(), Direction.Axis.Y));
				}
			}
		}
	}

	// ------------------------------------------------------------------ materials

	/** [palette] the heavy dark band: three deepslate textures mixed by a per-cell hash. */
	private static BlockState stone(Plot p, int u, int y, int v) {
		// [palette] a lighter stone footing than the dark timber and plaster above it, so the planes read apart
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.CRACKED_STONE_BRICKS);
			case 1 -> b(Blocks.POLISHED_ANDESITE);
			default -> b(Blocks.STONE_BRICKS);
		};
	}

	/** [palette] plaster: calcite with the odd tuff-brick patch where the weather got at it. */
	private static BlockState plaster(Plot p, int u, int y, int v) {
		return b(Blocks.CALCITE); // [palette] a clean whitewash: regular light panels between the dark posts
	}

	/** [palette] the lowest plaster course, darker: gradient from deepslate through tuff to calcite. */
	private static BlockState skirting(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 3) == 0 ? b(Blocks.STONE_BRICKS) : b(Blocks.TUFF_BRICKS);
	}

	private static boolean isPost(int i, int a, int bb) {
		return i == a || i == bb || (i - a) % 4 == 0;
	}

	private static boolean isWindow(int i, int a, int bb) {
		if (isPost(i, a, bb)) {
			return false;
		}
		return isPost(i - 1, a, bb) == isPost(i + 1, a, bb);
	}

	// ------------------------------------------------------------------ plinth (y0)

	private static void plinth(CitadelCanvas c, Plot p, Geo g) {
		for (int u = g.u1(); u <= g.u2(); u++) {
			for (int v = 1; v <= g.vb(); v++) {
				boolean edge = u == g.u1() || u == g.u2() || v <= g.vf() || v == g.vb();
				BlockState st = edge ? stone(p, u, 0, v) : b(Blocks.SPRUCE_PLANKS);
				if (v == 1 && !(u == g.u1() || u == g.u2())) {
					st = p.hash(u, 0, v, 2) == 0 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.STONE_BRICKS); // [palette] porch paving
				}
				p.set(c, u, 0, v, st);
			}
		}
	}

	// ------------------------------------------------------------------ ground floor front (y1..y3)

	private static void frontGround(CitadelCanvas c, Plot p, Scheme s, Geo g) {
		int u1 = g.u1();
		int u2 = g.u2();
		int cu = g.cu();
		for (int y = 1; y <= 3; y++) {
			for (int u = u1; u <= u2; u++) {
				BlockState st;
				if (u == cu && y <= 2) {
					st = s.door().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
							.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, y == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
				} else if (u == u1 || u == u2) {
					st = log(s.trim(), Direction.Axis.Y);
				} else if (y == 1) {
					st = skirting(p, u, y, g.vf());
				} else {
					st = plaster(p, u, y, g.vf());
				}
				p.set(c, u, y, g.vf(), st);
			}
		}
		// [depth] the lattice stands one block proud of the wall: pillars at both ends and beside the entrance,
		// open trapdoors as fins between them, a kick plate below and a header band above.
		for (int y = 1; y <= 3; y++) {
			p.set(c, u1, y, 1, log(s.trim(), Direction.Axis.Y));
			p.set(c, u2, y, 1, log(s.trim(), Direction.Axis.Y));
			p.set(c, cu - 2, y, 1, log(s.trim(), Direction.Axis.Y));
			p.set(c, cu + 2, y, 1, log(s.trim(), Direction.Axis.Y));
		}
		for (int u = u1 + 1; u < u2; u++) {
			if (Math.abs(u - cu) <= 1 || Math.abs(u - cu) == 2) {
				continue;
			}
			for (int y = 1; y <= 3; y++) {
				// a fin on one side of the cell; the sides alternate, so the lattice zigzags and has real depth
				p.set(c, u, y, 1, trapdoor(s.lattice(), (u - u1) % 2 == 0 ? Direction.EAST : Direction.WEST, true, Half.BOTTOM));
			}
			p.set(c, u, 1, 1, trapdoor(s.lattice(), Direction.NORTH, false, Half.BOTTOM)); // kick plate
			p.set(c, u, 3, 1, trapdoor(s.lattice(), Direction.NORTH, false, Half.TOP)); // header
			p.set(c, u, 2, 1, trapdoor(s.lattice(), Direction.NORTH, false, Half.TOP)); // [depth] a mid rail at the top of the cell
		}
		// [finish] the split noren: a valance across three cells and two flaps with a slit at the door
		BlockState cloth = glassPane(s.noren());
		for (int u = cu - 1; u <= cu + 1; u++) {
			p.set(c, u, 3, 1, paneU(cloth));
			if (u != cu) {
				p.set(c, u, 2, 1, paneU(cloth));
			}
		}
		// a lintel over the whole entrance, in front of the door
		for (int u = cu - 1; u <= cu + 1; u++) {
			p.set(c, u, 4, 1, log(s.trim(), Direction.Axis.X));
		}
	}

	// ------------------------------------------------------------------ hisashi (y4..y5, v0..v1)

	private static void hisashi(CitadelCanvas c, Plot p, Scheme s, Geo g) {
		int u1 = g.u1();
		int u2 = g.u2();
		for (int u = u1; u <= u2; u++) {
			boolean noren = Math.abs(u - g.cu()) <= 1;
			// [depth] the beam carries a two-row canopy: a low outer row and a higher inner row on the wall
			p.set(c, u, BEAM1, 1, log(s.trim(), Direction.Axis.X));
			p.set(c, u, BEAM1, 0, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH));
			p.set(c, u, 5, 1, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH));
			if (!noren && (u - u1) % 2 == 0) {
				// [depth] rafter brackets under the lip: a beam tail with a stair corbel under it, like the upper eave
				p.set(c, u, 3, 0, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
				p.set(c, u, 2, 0, stairTop(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
			}
		}
		// the canopy ends on the side pillars
		for (int u : new int[] {u1 - 1, u2 + 1}) {
			p.set(c, u, BEAM1, 0, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH));
		}
		// [finish] two hanging lanterns at the entrance and one at each end
		BlockState hanging = lantern(true);
		p.put(c, g.cu() - 2, 3, 0, hanging, CitadelCanvas.HANGING);
		p.put(c, g.cu() + 2, 3, 0, hanging, CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ upper floor front (y5..y7)

	private static void frontUpper(CitadelCanvas c, Plot p, Scheme s, Geo g) {
		int u1 = g.u1();
		int u2 = g.u2();
		for (int y = 5; y <= 7; y++) {
			for (int u = u1; u <= u2; u++) {
				BlockState st;
				if (isPost(u, u1, u2)) {
					st = log(s.trim(), Direction.Axis.Y);
				} else if (y == 6 && isWindow(u, u1, u2)) {
					// [depth] mushiko-mado: a slatted window sunk into the wall (open trapdoor on the back face)
					st = trapdoor(s.lattice(), Direction.NORTH, true, Half.BOTTOM);
				} else {
					st = plaster(p, u, y, 2);
				}
				if (isPost(u, u1, u2)) {
					p.set(c, u, y, g.vf(), st);
				} else {
					// [depth] the plaster and the slatted windows sit one block behind the posts
					p.set(c, u, y, g.vf(), CitadelCanvas.AIR);
					p.set(c, u, y, g.vf() + 1, st);
				}
			}
		}
		// [depth] a sill and a lintel frame every window: upside-down stairs under, a beam over
		for (int u = u1 + 1; u < u2; u++) {
			if (isWindow(u, u1, u2)) {
				p.set(c, u, 5, 1, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH));
			}
		}
		// plate at the top of the upper floor
		for (int u = u1; u <= u2; u++) {
			p.set(c, u, PLATE, g.vf(), log(s.trim(), Direction.Axis.X));
			p.set(c, u, BEAM1, g.vf(), log(s.trim(), Direction.Axis.X));
		}
	}

	// ------------------------------------------------------------------ side and back walls

	private static void sidesAndBack(CitadelCanvas c, Plot p, Scheme s, Geo g) {
		int u1 = g.u1();
		int u2 = g.u2();
		for (int y = 1; y <= 7; y++) {
			if (y == BEAM1) {
				continue;
			}
			for (int v = g.vf() + 1; v <= g.vb(); v++) {
				for (int u : new int[] {u1, u2}) {
					BlockState st;
					boolean post = isPost(v, g.vf(), g.vb());
					if (post) {
						st = log(s.trim(), Direction.Axis.Y);
					} else if ((y == 2 || y == 3 || y == 6) && isWindow(v, g.vf(), g.vb())) {
						st = JapaneseKit.paneV(b(Blocks.GLASS_PANE));
						// [depth] open shutters on the outside face, either side of the window
						boolean west = u == u1;
						int out = west ? 0 : g.w() - 1;
						for (int dv : new int[] {-1, 1}) {
							p.set(c, out, y, v + dv, trapdoor(s.lattice(), west ? Direction.WEST : Direction.EAST, true, Half.BOTTOM));
						}
					} else if (y == 1) {
						st = stone(p, u, y, v);
					} else if (y == 2) {
						st = skirting(p, u, y, v);
					} else {
						st = plaster(p, u, y, v);
					}
					p.set(c, u, y, v, st);
				}
			}
			for (int u = u1 + 1; u < u2; u++) {
				BlockState st;
				if (u == g.cu() && y <= 2) {
					st = s.door().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
							.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, y == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
				} else if (isPost(u, u1, u2)) {
					st = log(s.trim(), Direction.Axis.Y);
				} else if ((y == 2 || y == 6) && isWindow(u, u1, u2)) {
					st = paneU(b(Blocks.GLASS_PANE));
				} else if (y == 1) {
					st = skirting(p, u, y, g.vb());
				} else {
					st = plaster(p, u, y, g.vb());
				}
				p.set(c, u, y, g.vb(), st);
			}
		}
		// beams all round at the floor between storeys and under the roof
		for (int u = u1; u <= u2; u++) {
			p.set(c, u, BEAM1, g.vb(), log(s.trim(), Direction.Axis.X));
			p.set(c, u, PLATE, g.vb(), log(s.trim(), Direction.Axis.X));
		}
		for (int v = g.vf(); v <= g.vb(); v++) {
			for (int u : new int[] {u1, u2}) {
				p.set(c, u, BEAM1, v, log(s.trim(), Direction.Axis.Z));
				p.set(c, u, PLATE, v, log(s.trim(), Direction.Axis.Z));
			}
		}
		// [depth] a drip ledge of tiles along both flanks at the floor line
		for (int v = g.vf(); v <= g.vb(); v++) {
			p.set(c, 0, BEAM1, v, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST));
			p.set(c, g.w() - 1, BEAM1, v, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST));
		}
		// the floor between storeys
		for (int u = u1 + 1; u < u2; u++) {
			for (int v = g.vf() + 1; v < g.vb(); v++) {
				p.set(c, u, BEAM1, v, b(Blocks.SPRUCE_PLANKS));
			}
		}
	}

	// ------------------------------------------------------------------ roof

	private static int rowK(int v, Geo g) {
		return Math.min(v - g.rf(), g.rb() - v);
	}

	private static int roofY(int v, Geo g) {
		return ROOF0 + rowK(v, g) / 2;
	}

	/**
	 * [finish] a low tiled gable, 2:1: an inward stair then a full block, repeated. The two eave rows are
	 * the fascia colour, with a lip that flicks up beyond them; rafter ends show under both eaves, and the
	 * ridge carries a cap with an end tile at each side.
	 */
	private static void roof(CitadelCanvas c, Plot p, Scheme s, Geo g) {
		int n = g.rb() - g.rf() + 1;
		for (int v = g.rf(); v <= g.rb(); v++) {
			int k = rowK(v, g);
			int y = roofY(v, g);
			boolean frontHalf = v - g.rf() < g.rb() - v;
			boolean center = (n % 2 == 1 && v - g.rf() == g.rb() - v);
			Direction up = frontHalf ? Direction.SOUTH : Direction.NORTH;
			for (int u = g.u1(); u <= g.u2(); u++) {
				boolean end = u == g.u1() || u == g.u2();
				if (end && v > g.rf() && v < g.rb()) {
					continue; // the udatsu stand here
				}
				BlockState st;
				if (center || k % 2 == 1) {
					st = b(Blocks.DEEPSLATE_TILES);
				} else {
					st = stair(k == 0 ? s.fascia() : p.hash(u, y, v, 6) == 0 ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS, up);
				}
				p.set(c, u, y, v, st);
			}
			// ridge cap
			if (center || (n % 2 == 0 && k == rowK(g.rf() + n / 2, g))) {
				for (int u = g.u1(); u <= g.u2(); u++) {
					p.set(c, u, y + 1, v, slab(Blocks.DEEPSLATE_BRICK_SLAB));
				}
			}
		}
		// [finish] the eave lip: a stair pointing out, one block beyond each eave, and rafter ends under it
		for (int u = g.u1(); u <= g.u2(); u++) {
			p.set(c, u, ROOF0, g.rf() - 1, stair(s.fascia(), Direction.NORTH));
			p.set(c, u, ROOF0, g.rb() + 1, stair(s.fascia(), Direction.SOUTH));
			if ((u - g.u1()) % 2 == 0) {
				p.set(c, u, PLATE, g.rf(), log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
				p.set(c, u, PLATE, g.rb(), log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
			}
		}
		// [depth] both lips rest on rafter tails that run out from the wall plate, with a stair corbel under each
		for (int u = g.u1(); u <= g.u2(); u += 2) {
			EaveKit.tail(c, p, u, PLATE, g.rf() - 1, Direction.SOUTH, Blocks.STRIPPED_SPRUCE_LOG);
			EaveKit.tail(c, p, u, PLATE, g.rb() + 1, Direction.NORTH, Blocks.STRIPPED_SPRUCE_LOG);
			if (EaveKit.free(c, p, u, PLATE - 1, g.rf())) {
				p.set(c, u, PLATE - 1, g.rf(), stairTop(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
			}
			if (EaveKit.free(c, p, u, PLATE - 1, g.rb())) {
				p.set(c, u, PLATE - 1, g.rb(), stairTop(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
			}
		}
	}

	/** [depth][palette] the side walls climb above the roof in plaster steps capped with tile slabs. */
	private static void udatsu(CitadelCanvas c, Plot p, Geo g) {
		for (int v = g.vf(); v <= g.vb(); v++) {
			int y = roofY(v, g);
			for (int u : new int[] {g.u1(), g.u2()}) {
				for (int yy = PLATE + 1; yy <= y; yy++) {
					p.set(c, u, yy, v, plaster(p, u, yy, v));
				}
				p.set(c, u, y + 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
		}
	}

	// ------------------------------------------------------------------ street (v0, y0..)

	/** [finish] the step to the door, a bamboo dog-fence (inu-yarai) in front of the lattice and planters at its ends. */
	private static void street(CitadelCanvas c, Plot p, Scheme s, Geo g) {
		int cu = g.cu();
		p.set(c, cu, 0, 0, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH));
		p.set(c, cu - 1, 0, 0, slab(Blocks.POLISHED_DEEPSLATE_SLAB));
		p.set(c, cu + 1, 0, 0, slab(Blocks.POLISHED_DEEPSLATE_SLAB));
		for (int u = g.u1(); u <= g.u2(); u++) {
			if (Math.abs(u - cu) <= 1) {
				continue;
			}
			boolean leftNeighbour = u - 1 >= g.u1() && Math.abs(u - 1 - cu) > 1;
			boolean rightNeighbour = u + 1 <= g.u2() && Math.abs(u + 1 - cu) > 1;
			p.set(c, u, 0, 0, fence(Blocks.BAMBOO_FENCE, false, rightNeighbour, false, leftNeighbour));
			if ((u - g.u1()) % 2 == 1) {
				p.set(c, u, 1, 0, b(Blocks.BAMBOO)); // [depth] tall bamboo stalks behind the fence, now and then
			}
		}
		// side strips: a low stone wall along both flanks with bamboo and moss
		for (int v = 1; v <= g.d() - 2; v++) {
			for (int u : new int[] {0, g.w() - 1}) {
				boolean prev = v - 1 >= 1;
				boolean next = v + 1 <= g.d() - 2;
				p.set(c, u, 0, v, wall(Blocks.COBBLED_DEEPSLATE_WALL, true, prev, false, next, false));
				if (v >= 2 && p.hash(u, 1, v, 4) == 0) {
					p.set(c, u, 1, v, b(Blocks.BAMBOO));
					p.set(c, u, 2, v, b(Blocks.BAMBOO));
				}
			}
		}
	}

	// ------------------------------------------------------------------ courtyard (tsuboniwa)

	private static void courtyard(CitadelCanvas c, Plot p, Random r, Geo g) {
		int vc0 = g.rb() + 2; // first courtyard row, past the back eave lip
		int vend = g.d() - 1;
		for (int u = g.u1(); u <= g.u2(); u++) {
			for (int v = vc0; v <= vend; v++) {
				BlockState st = switch (p.hash(u, 0, v, 5)) {
					case 0 -> b(Blocks.MOSS_BLOCK);
					case 1 -> b(Blocks.COARSE_DIRT);
					default -> b(Blocks.GRAVEL);
				};
				p.set(c, u, 0, v, st);
			}
		}
		// stepping stones from the back door
		for (int v = g.vb() + 1; v <= vend; v++) {
			p.set(c, g.cu(), 0, v, b(Blocks.POLISHED_ANDESITE));
			if (v == g.vb() + 1) {
				p.set(c, g.cu(), 0, v, stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
			}
		}
		// a bamboo fence closes the garden, two rails high
		for (int u = g.u1(); u <= g.u2(); u++) {
			boolean l = u > g.u1();
			boolean rr = u < g.u2();
			p.set(c, u, 1, vend, fence(Blocks.BAMBOO_FENCE, false, rr, false, l));
			p.set(c, u, 2, vend, fence(Blocks.BAMBOO_FENCE, false, rr, false, l));
		}
		// the tree and the lantern, on opposite sides by seed
		boolean left = r.nextBoolean();
		int ut = left ? g.u1() + 1 : g.u2() - 1;
		int ul = left ? g.u2() - 1 : g.u1() + 1;
		JapaneseKit.sakura(c, p, ut, 1, g.d() - 3, 0);
		JapaneseKit.stoneLantern(c, p, ul, 1, g.d() - 2);
		// a pair of moss tufts and a pot by the back door
		// on the ground beside the back step (the courtyard paving starts further back, so at y1 they floated)
		p.set(c, g.cu() + 1, 0, g.vb() + 1, b(Blocks.POTTED_FERN));
		p.set(c, g.cu() - 1, 0, g.vb() + 1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
	}
}
