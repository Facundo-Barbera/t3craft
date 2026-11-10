package dev.t3craft.hq.citadel.catalog.bridge;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Concept: a big covered timber bridge you walk through, a place and not a tube. A deck of striped
 * planks (3 or more cells of clear walkway, 4 blocks of headroom) rises from the ground by one stair
 * step and a stone landing; dark-oak trusses (a double post and a cantilevered hammer beam at every
 * bay line) carry a half-pitch gable roof with deep pink eaves; between the trusses the side walls
 * hold open windows with a sill, a lintel and a mullion on the long bays, and a bench stands in
 * every window bay. One bay has a notice board; lanterns hang on chains from the ridge. Both ends
 * are stone abutments with low cheek walls, a lantern newel and a timber portal under a gable that
 * overhangs the landing.
 *
 * <ul>
 * <li><b>Palette, dark → light, bottom → top</b>: deepslate bricks and polished deepslate (abutments,
 *     post plinths); dark-oak logs, planks and stripped beams (the frame); spruce and cherry planks
 *     (deck); cherry (benches, sills, valance, eaves, gable trim, ridge); deepslate tiles (roof
 *     field); bamboo mosaic (notice board).</li>
 * <li><b>Depth</b>: outer posts one block proud of the wall plane, windows set one block back with a
 *     sill ledge and a lintel beam, knee braces under the plates, hammer beams, a recessed gable
 *     behind the roof overhang, a hanging valance on the portals.</li>
 * <li><b>Finish</b>: lanterns on the cheek newels, on chains from the ridge down the middle and at the
 *     two porches, cherry gable trim and ridge, tile-wall finials with a rod.</li>
 * </ul>
 *
 * <pre>
 *  section across u (w = 11)                 side (one bay, d = 13)
 *  y9        ▂            ridge cap          ▂▂▂▂▂▂▂▂▂▂▂▂▂
 *  y8      ▄▄█▄▄          ridge              ▀▀▀▀▀▀▀▀▀▀▀▀▀  tiles
 *  y7    ▄▀▀   ▀▀▄        half pitch         ▄▄▄▄▄▄▄▄▄▄▄▄▄  pink eave
 *  y5  ▄▄██═╗   ╔═██▄▄    plates, beams      ╔═╗         ╔═╗
 *  y4   ▌ ▌◥ ♦  ◤▐ ▐     braces, lantern     ▌▌         ▌▌   lintel
 *  y2-3 ▌ ▌ ♦        ▐ ▐  windows            ▌▌         ▌▌   open window
 *  y1   ▌ ▌▙       ▟▐ ▐  benches, sills     ▌▌▙▙▙▙▙▙▙▙▙▌▌
 *  y0  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒     deck               ▓▓▓▒▒▒▒▒▒▒▓▓▓  landing / deck
 * </pre>
 *
 * Size: 11–15 × 11–31 × 11+ (the roof is a half-pitch: the ridge stands at {@code (11 + (w - 1) / 2) / 2}, which is y8 at 11 wide
 * and y9 at 15 wide; from h 11 on the ridge cap fits, and the finial rods one more block up (they are dropped below h 12 at 15 wide)).
 */
public final class BridgeCoveredTimber implements Variant {
	private static final BlockState POST = b(Blocks.DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
	private static final BlockState PIER = b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
	private static final BlockState PLATE = b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
	private static final BlockState BEAM = b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X);
	private static final BlockState STRINGER = b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
	private static final BlockState BATTEN = b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
	private static final BlockState LANTERN_HANGING = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
	private static final BlockState CHAIN = b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
	/** The plate and eave row. */
	private static final int PL = 5;
	/** First truss line (the portals) and the stone landing in front of it. */
	private static final int PORTAL = 3;

	@Override
	public String id() {
		return "bridge_covered_timber";
	}

	@Override
	public Family family() {
		return Family.BRIDGE;
	}

	@Override
	public int minW() {
		return 11;
	}

	@Override
	public int minD() {
		return 11;
	}

	@Override
	public int minH() {
		return 11;
	}

	@Override
	public int maxW() {
		return 15;
	}

	@Override
	public int maxD() {
		return 31;
	}

	/** Mirrors a left-side column {@code k} (0 = outermost) to the right side. */
	private static int mu(boolean left, int k, int w) {
		return left ? k : w - 1 - k;
	}

	/** Roof depth of column {@code u}: 0 at the eave, up to {@code (w - 1) / 2} at the ridge. */
	private static int rc(int u, int w) {
		return Math.min(u, w - 1 - u);
	}

	/** The y of the roof element of a column of roof depth {@code c}: eave row, then half a block per column. */
	private static int roofY(int c) {
		return c == 0 ? PL : (11 + c) / 2;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();

		// truss lines: PORTAL .. d-1-PORTAL-1, bays of about 4.5
		int span = d - 2 * PORTAL - 1;
		int bays = Math.max(1, Math.round(span / 4.5f));
		List<Integer> posts = new ArrayList<>();
		for (int i = 0; i <= bays; i++) {
			posts.add(PORTAL + Math.round(i * span / (float) bays));
		}

		deck(c, p);
		abutments(c, p);
		trusses(c, p, posts);
		walls(c, p, posts);
		roof(c, p, posts);
		lanterns(c, p, posts);
	}

	// ------------------------------------------------------------------ deck (y0)

	private static void deck(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		for (int v = 0; v < d; v++) {
			int e = Math.min(v, d - 1 - v); // distance to the nearest end
			for (int u = 0; u < w; u++) {
				int k = Math.min(u, w - 1 - u);
				BlockState st;
				if (e <= PORTAL) {
					// [palette] the stone abutment and landing: dark bricks, a few cracked and polished
					int h = p.hash(u, 0, v, 6);
					if (e == 0 && k >= 1) {
						Direction inward = v == 0 ? Direction.SOUTH : Direction.NORTH;
						// one gentle step up from the ground
						st = stairs(Blocks.DEEPSLATE_BRICK_STAIRS, inward);
					} else if (k == 0) {
						st = h == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.COBBLED_DEEPSLATE);
					} else if (Math.abs(u - (w - 1) / 2.0) <= 1.5) {
						// [palette] a lighter stone road down the middle of the landing
						st = h == 0 ? b(Blocks.STONE_BRICKS) : h == 1 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
					} else {
						st = h == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : h == 1 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_BRICKS);
					}
				} else if (k == 0) {
					st = STRINGER;
				} else if (k == 1) {
					st = b(Blocks.DARK_OAK_PLANKS);
				} else if (k <= 3) {
					st = b(Blocks.SPRUCE_PLANKS);
				} else {
					// the walkway: cherry planks, a stripped board now and then
					st = p.hash(u, 0, v, 6) == 0 ? b(Blocks.STRIPPED_CHERRY_WOOD) : b(Blocks.CHERRY_PLANKS);
				}
				p.set(c, u, 0, v, st);
			}
		}
	}

	private static BlockState wall(Block block, boolean up, boolean n, boolean s) {
		return b(block).setValue(BlockStateProperties.UP, up)
				.setValue(BlockStateProperties.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.EAST_WALL, WallSide.NONE)
				.setValue(BlockStateProperties.WEST_WALL, WallSide.NONE);
	}

	/** Low cheek walls on both flanks of both landings, with a lantern newel at the outer corners. */
	private static void abutments(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		for (int end = 0; end < 2; end++) {
			for (int i = 0; i <= PORTAL; i++) {
				int v = end == 0 ? i : d - 1 - i;
				// local north = towards the front (v = 0)
				boolean n = end == 0 ? i > 0 : i < PORTAL;
				boolean s = end == 0 ? i < PORTAL : i > 0;
				boolean up = !(n && s);
				for (int u : new int[] {0, w - 1}) {
					p.set(c, u, 1, v, wall(Blocks.DEEPSLATE_BRICK_WALL, up, n, s));
					if (i == 0) {
						// [finish] lantern newel at the foot of the approach
						p.set(c, u, 2, v, b(Blocks.LANTERN));
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ trusses (posts, braces, hammer beams)

	private static void trusses(CitadelCanvas c, Plot p, List<Integer> posts) {
		int w = p.w();
		int d = p.d();
		// plates run the whole length, resting on the posts and out over the landings
		for (int v = PORTAL - 1; v <= d - PORTAL; v++) {
			for (boolean left : new boolean[] {true, false}) {
				p.set(c, mu(left, 1, w), PL, v, PLATE);
				p.set(c, mu(left, 2, w), PL, v, PLATE);
			}
		}
		for (int vi = 0; vi < posts.size(); vi++) {
			int v = posts.get(vi);
			boolean portal = vi == 0 || vi == posts.size() - 1;
			for (boolean left : new boolean[] {true, false}) {
				Direction toWall = left ? Direction.WEST : Direction.EAST;
				// outer post (one block proud of the wall plane) and the pier in the wall plane
				p.set(c, mu(left, 1, w), 1, v, b(Blocks.POLISHED_DEEPSLATE));
				p.set(c, mu(left, 2, w), 1, v, b(Blocks.POLISHED_DEEPSLATE));
				for (int y = 2; y < PL; y++) {
					p.set(c, mu(left, 1, w), y, v, POST);
					p.set(c, mu(left, 2, w), y, v, PIER);
				}
				// [depth] knee brace under the hammer beam, thick against the pier
				p.set(c, mu(left, 3, w), PL - 1, v, stairs(Blocks.DARK_OAK_STAIRS, toWall).setValue(BlockStateProperties.HALF, Half.TOP));
			}
			// hammer beams: they cantilever in from the plates and leave the middle open (the ridge shows)
			for (int u = 1; u < w - 1; u++) {
				if (rc(u, w) <= 4) {
					p.set(c, u, PL, v, BEAM);
				}
			}
			if (portal) {
				// lintel across the whole portal, a cherry valance hanging under it
				for (int u = 1; u < w - 1; u++) {
					p.set(c, u, PL, v, BEAM);
				}
				Direction out = v < d / 2 ? Direction.NORTH : Direction.SOUTH;
				for (int u = 4; u <= w - 5; u++) {
					p.set(c, u, PL - 1, v, stairs(Blocks.CHERRY_STAIRS, out).setValue(BlockStateProperties.HALF, Half.TOP));
				}
				// [depth] gable closed behind the overhang: planks with log battens
				for (int u = 2; u < w - 2; u++) {
					int top = roofY(rc(u, w));
					for (int y = PL + 1; y < top; y++) {
						p.set(c, u, y, v, u % 2 == 0 ? BATTEN : b(Blocks.DARK_OAK_PLANKS));
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ walls: windows, benches, notice board

	private static void walls(CitadelCanvas c, Plot p, List<Integer> posts) {
		int w = p.w();
		int noticeBay = (posts.size() - 1) / 2;
		for (int i = 0; i + 1 < posts.size(); i++) {
			int a = posts.get(i);
			int nw = posts.get(i + 1) - a - 1; // cells of the bay between the two post lines
			for (int k = 0; k < nw; k++) {
				int v = a + 1 + k;
				boolean mullion = nw >= 5 && (k + 1) % 3 == 0 && k < nw - 1;
				for (boolean left : new boolean[] {true, false}) {
					int wall = mu(left, 2, w);
					int ledge = mu(left, 1, w);
					int bench = mu(left, 3, w);
					boolean notice = i == noticeBay && !left;
					// the low wall under the window; the plate over it is the lintel (a window is 3 high: y2..y4)
					p.set(c, wall, 1, v, p.hash(wall, 1, v, 5) == 0 ? b(Blocks.DARK_OAK_WOOD) : b(Blocks.DARK_OAK_PLANKS));
					if (notice) {
						// [function] the notice board: a pale panel with notes pinned on it, read from the bench
						p.set(c, wall, 2, v, b(Blocks.BAMBOO_MOSAIC));
						p.set(c, wall, 3, v, b(Blocks.BAMBOO_MOSAIC));
						p.set(c, wall, 4, v, b(Blocks.DARK_OAK_PLANKS));
						BlockState sign = b(Blocks.CHERRY_WALL_SIGN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
						p.put(c, bench, 3, v, sign, CitadelCanvas.DECOR);
						if (p.hash(wall, 2, v, 3) != 0) {
							p.put(c, bench, 2, v, sign, CitadelCanvas.DECOR);
						}
					} else if (mullion) {
						for (int y = 2; y < PL; y++) {
							p.set(c, wall, y, v, b(Blocks.DARK_OAK_FENCE));
						}
					}
					// [depth] the sill ledge, proud of the wall, in pink
					p.set(c, ledge, 1, v, slab(Blocks.CHERRY_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
					// [function] the bench in the window bay, back to the wall
					if (notice && k == nw - 1) {
						p.set(c, bench, 1, v, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
					} else {
						p.set(c, bench, 1, v, stairs(Blocks.CHERRY_STAIRS, left ? Direction.WEST : Direction.EAST));
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ roof

	private static void roof(CitadelCanvas c, Plot p, List<Integer> posts) {
		int w = p.w();
		int d = p.d();
		int peak = (w - 1) / 2;
		for (int v = PORTAL - 1; v <= d - PORTAL; v++) {
			boolean edge = v == PORTAL - 1 || v == d - PORTAL;
			for (int u = 0; u < w; u++) {
				int rc = rc(u, w);
				int y = roofY(rc);
				Direction in = u < w - 1 - u ? Direction.EAST : Direction.WEST;
				if (rc == 0) {
					// the eave row: cherry, the citadel's pink line
					p.set(c, u, y, v, stairs(Blocks.CHERRY_STAIRS, in));
				} else if (rc == peak) {
					p.set(c, u, y, v, b(Blocks.CHERRY_PLANKS));
					if (edge) {
						// [finish] finial: a tile-wall post and a rod
						if (y + 1 < p.h()) {
							p.set(c, u, y + 1, v, b(Blocks.DEEPSLATE_TILE_WALL).setValue(BlockStateProperties.UP, true)
									.setValue(BlockStateProperties.NORTH_WALL, WallSide.NONE).setValue(BlockStateProperties.SOUTH_WALL, WallSide.NONE)
									.setValue(BlockStateProperties.EAST_WALL, WallSide.NONE).setValue(BlockStateProperties.WEST_WALL, WallSide.NONE));
						}
						if (y + 2 < p.h()) {
							p.set(c, u, y + 2, v, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
						}
					} else if (y + 1 < p.h()) {
						p.set(c, u, y + 1, v, slab(Blocks.CHERRY_SLAB));
					}
				} else if (rc % 2 == 1) {
					// odd columns are slabs, even ones stairs: half a block of rise per column
					// [palette] tiles for the field, lighter polished stone on the gable edges
					Block sb = edge ? Blocks.POLISHED_DEEPSLATE_SLAB : p.hash(u, y, v, 4) == 0 ? Blocks.DEEPSLATE_BRICK_SLAB : Blocks.DEEPSLATE_TILE_SLAB;
					p.set(c, u, y, v, slab(sb));
				} else {
					Block sb = edge ? Blocks.POLISHED_DEEPSLATE_STAIRS : p.hash(u, y, v, 4) == 0 ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
					p.set(c, u, y, v, stairs(sb, in));
				}
			}
		}
	}

	// ------------------------------------------------------------------ lanterns

	/** A lantern at y = PL - 1 on a chain from the roof element of column {@code u}. */
	private static void hang(CitadelCanvas c, Plot p, int u, int v) {
		int top = roofY(rc(u, p.w()));
		for (int y = PL; y < top; y++) {
			p.put(c, u, y, v, CHAIN, CitadelCanvas.HANGING);
		}
		p.put(c, u, PL - 1, v, LANTERN_HANGING, CitadelCanvas.HANGING);
	}

	private static void lanterns(CitadelCanvas c, Plot p, List<Integer> posts) {
		int w = p.w();
		int d = p.d();
		int cu = (w - 1) / 2;
		for (int i = 1; i < posts.size() - 1; i++) {
			hang(c, p, cu, posts.get(i));
		}
		// "a lantern at the middle": only when no truss line is next to the middle
		int mid = (d - 1) / 2;
		boolean near = false;
		for (int v : posts) {
			near |= Math.abs(v - mid) <= 1;
		}
		if (!near) {
			hang(c, p, cu, mid);
		}
		// the two porches: a pair under the overhang, flanking the way in
		for (int v : new int[] {PORTAL - 1, d - PORTAL}) {
			hang(c, p, 3, v);
			hang(c, p, w - 4, v);
		}
	}
}
