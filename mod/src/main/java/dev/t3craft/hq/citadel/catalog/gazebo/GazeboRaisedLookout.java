package dev.t3craft.hq.citadel.catalog.gazebo;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Support;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Concept: a real lookout (mirador) you walk up to. A dark-oak pavilion stands on a stone base 2–4 blocks
 * high; a flight of stairs two wide climbs from the front, in a notch cut into the base between two stone
 * cheeks, and the cheeks carry the railing, so the flight has a wall on both sides. A hipped copper roof
 * (a dark deepslate-tile eave, weathered and oxidised copper above, ridge of deepslate tiles, a lightning
 * rod) sits on four stone-footed dark-oak posts with brackets under the eave, and there are always four
 * free blocks of headroom on the deck.
 *
 * <p>On top: a place to sit and look. Benches on the back and the sides, a table with a lectern, a brass
 * telescope (a copper rod on a post, at the front rail), lanterns on the newels and under the beams. The
 * seed changes the height of the platform (2, 3 or 4), the position of the stairs (left, right or centre)
 * and the weathering of the roof. When the platform is 4 high the space under it is a tool shed: a timber
 * and stone room with open bays, barrels, chests and a workbench, all of it reachable on foot.
 *
 * <p>Palette, dark → light, bottom → top: deepslate bricks (foot) → stone bricks, mossy and cracked mixed
 * → dark oak and spruce (frame, deck, benches) → weathered copper (roof) with a dark tile eave.
 *
 * <pre>
 *   front (w 9, platform 4)           plan: deck, notch with the flight at the front
 *        ╿             rod             ┌───────────────┐
 *     ▄▄████▄▄         copper roof     │ ┼ ═ ═ ═ ═ ═ ┼ │  ┼ post  ═ rail
 *   ▄▀▀▀▀▀▀▀▀▀▀▄       tile eave       │ ║ ▬ ▬ ▬ ▬ ▬ ║ │  ▬ bench
 *     ┼ ║ ○ ║ ┼        posts, deck     │ ║  ◢◢   ○   ║ │  ○ table
 *   ═══╧═══╧═══╧═      rail            │ ┼ ║ ◢◢ ║ ═ ┼ │  ◢ stairs
 *   █▒█ ▐▌▐▌▐▌ █▒█     base / shed     └───────────────┘
 * </pre>
 *
 * Size: 5–13 × 5–13 × 8+ (the roof needs {@code platform + 4 + 2} of height; the platform shrinks on low
 * plots, and on a plot that cannot give the hipped roof its rise the slope flattens to 1:2 with slabs).
 */
public final class GazeboRaisedLookout implements Variant {
	private static final int MIN_W = 5;
	private static final int MIN_D = 5;
	private static final int MIN_H = 8;
	private static final int MAX_W = 13;
	private static final int MAX_D = 13;
	private static final boolean DBG_NO_ROOF = false;

	@Override
	public String id() {
		return "gazebo_raised_lookout";
	}

	@Override
	public Family family() {
		return Family.GAZEBO;
	}

	@Override
	public int minW() {
		return MIN_W;
	}

	@Override
	public int minD() {
		return MIN_D;
	}

	@Override
	public int minH() {
		return MIN_H;
	}

	@Override
	public int maxW() {
		return MAX_W;
	}

	@Override
	public int maxD() {
		return MAX_D;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		new Site(c, p).build();
	}

	// ------------------------------------------------------------------ the build

	private static final class Site {
		private final CitadelCanvas c;
		private final Plot p;
		private final int w;
		private final int d;
		/** Platform height: people stand at y = H, the deck layer is H - 1. */
		private final int platform;
		/** The lowest roof ring: four free blocks above the deck. */
		private final int eave;
		/** The flight climbs in the columns nx and nx + 1, at the front (v < platform). */
		private final int nx;
		private final int[][] roofTop;

		Site(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			this.w = p.w();
			this.d = p.d();
			int r = sel(1, 10);
			int h = r < 6 ? 4 : r < 9 ? 3 : 2;
			h = Math.min(h, Math.max(2, p.h() - 8)); // leave the hipped roof at least 3 levels of rise when the plot allows
			h = Math.min(h, d - 2);
			this.platform = Math.max(h, 1);
			this.eave = platform + 4;
			if (w >= 6) {
				int lo = 2;
				int hi = w - 4;
				int side = sel(2, 3);
				this.nx = side == 0 ? lo : side == 1 ? hi : (lo + hi) / 2;
			} else {
				this.nx = 1;
			}
			this.roofTop = new int[w][d];
		}

		private int sel(int salt, int n) {
			return p.hash(salt, 0, 77, n);
		}

		private void s(int u, int y, int v, BlockState st) {
			p.set(c, u, y, v, st);
		}

		void build() {
			for (int v = 0; v < d; v++) {
				for (int u = 0; u < w; u++) {
					if (thin(u, v)) {
						for (int y = 0; y < platform; y++) {
							s(u, y, v, y == platform - 1 ? b(Blocks.POLISHED_DEEPSLATE) : stone(u, v, y));
						}
						if (v == 0) {
							s(u, platform, v, b(Blocks.POLISHED_DEEPSLATE_WALL).setValue(BlockStateProperties.SOUTH_WALL, platform > 1 ? WallSide.LOW : WallSide.NONE));
							s(u, platform + 1, v, b(Blocks.POLISHED_DEEPSLATE_WALL));
							s(u, platform + 2, v, b(Blocks.LANTERN)); // a tall lamp post at the corner: nobody can stand on it
						} else {
							s(u, platform, v, b(Blocks.DARK_OAK_FENCE)
									.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, v < platform - 1));
						}
						continue;
					}
					if (!deck(u, v)) {
						continue;
					}
					if (!open(u, v)) {
						for (int y = 0; y <= platform - 2; y++) {
							s(u, y, v, baseState(u, v, y));
						}
					}
					s(u, platform - 1, v, deckState(u, v));
				}
			}
			flight();
			rails();
			posts();
			furniture();
			shed();
			if (!DBG_NO_ROOF) {
				roof();
			}
		}

		// -------------------------------------------------------------- plan

		/** Notch of the flight: the front columns nx, nx + 1 for v below the platform height. */
		private boolean notch(int u, int v) {
			return u >= nx && u <= nx + 1 && v >= 0 && v < platform;
		}

		/**
		 * A wing of the base that is only two cells wide (cheek + edge) is one solid buttress instead of two
		 * rails side by side: the edge cell is a stone pier capped with a wall and a lantern.
		 */
		private boolean thin(int u, int v) {
			return v >= 0 && v < platform && ((u == 0 && nx == 2) || (u == w - 1 && nx == w - 4)) && !notch(u, v);
		}

		private boolean deck(int u, int v) {
			return u >= 0 && u < w && v >= 0 && v < d && !notch(u, v) && !thin(u, v);
		}

		/** The stone cheeks that wall the flight in, and carry its rail. */
		private boolean cheek(int u, int v) {
			return v >= 0 && v < platform && (u == nx - 1 || u == nx + 2) && deck(u, v);
		}

		private boolean corridor(int u, int v) {
			return u >= nx && u <= nx + 1 && v >= platform && v <= platform + 1;
		}

		private boolean rail(int u, int v) {
			if (!deck(u, v)) {
				return false;
			}
			if (u >= nx && u <= nx + 1 && v == platform) {
				return false; // the top of the flight is open
			}
			return !deck(u - 1, v) || !deck(u + 1, v) || !deck(u, v - 1) || !deck(u, v + 1);
		}

		private boolean postU(int u) {
			return u == 1 || u == w - 2 || (w >= 11 && u == (w - 1) / 2);
		}

		private boolean postV(int v) {
			return v == 1 || v == d - 2 || (d >= 11 && v == (d - 1) / 2);
		}

		private boolean post(int u, int v) {
			if (!deck(u, v)) {
				return false;
			}
			boolean frontBack = (v == 1 || v == d - 2) && postU(u);
			boolean sides = (u == 1 || u == w - 2) && postV(v);
			return frontBack || sides;
		}

		private boolean free(int u, int v) {
			return deck(u, v) && !rail(u, v) && !post(u, v);
		}

		private int ring(int u, int v) {
			return Math.min(Math.min(u, w - 1 - u), Math.min(v, d - 1 - v));
		}

		// -------------------------------------------------------------- base and undercroft

		private int bayU() {
			if (nx + 1 < w / 2) {
				return w - 1;
			}
			if (nx > w / 2 - 1) {
				return 0;
			}
			return sel(5, 2) == 0 ? 0 : w - 1;
		}

		private boolean bay(int u, int v) {
			return d >= platform + 4 && u == bayU() && v >= platform && v <= platform + 1;
		}

		/** Free air under the deck (platform 4: three blocks of headroom), a tool shed with open bays. */
		private boolean open(int u, int v) {
			if (platform < 4 || !deck(u, v) || cheek(u, v) || post(u, v)) {
				return false;
			}
			if (u == 0 || u == w - 1 || v == d - 1) {
				return bay(u, v);
			}
			return true;
		}

		private BlockState stone(int u, int v, int y) {
			if (y == 0) {
				return p.hash(u, 0, v, 5) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
			}
			int h1 = p.hash(u, y, v, 6);
			return h1 == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : h1 == 1 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
		}

		private Direction inward(int u, int v) {
			if (u == 0) {
				return Direction.EAST;
			}
			if (u == w - 1) {
				return Direction.WEST;
			}
			if (v == 0) {
				return Direction.SOUTH;
			}
			return Direction.NORTH;
		}

		private BlockState baseState(int u, int v, int y) {
			boolean perimeter = u == 0 || u == w - 1 || v == 0 || v == d - 1;
			if (platform == 4) {
				if (post(u, v)) {
					return y == 0 ? b(Blocks.DEEPSLATE_BRICKS) : y == 1 ? b(Blocks.STONE_BRICKS) : b(Blocks.CHISELED_STONE_BRICKS);
				}
				if (cheek(u, v) || !perimeter) {
					return stone(u, v, y);
				}
				// a timber-framed wall on a stone foot: dark-oak studs every third cell, spruce between
				boolean side = u == 0 || u == w - 1;
				int coord = side ? v : u;
				int len = side ? d : w;
				if (y == 0) {
					return stone(u, v, 0);
				}
				boolean stud = coord % 3 == 0 || coord == len - 1;
				return stud ? b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Y) : b(Blocks.SPRUCE_PLANKS);
			}
			if (!perimeter) {
				return stone(u, v, y);
			}
			boolean corner = (u == 0 || u == w - 1) && (v == 0 || v == d - 1);
			if (platform == 2) {
				return corner ? b(Blocks.POLISHED_DEEPSLATE) : stone(u, v, 0);
			}
			// platform 3: a corbel course on top, piers and planters below
			if (y == platform - 2) {
				return stairs(Blocks.STONE_BRICK_STAIRS, inward(u, v)).setValue(BlockStateProperties.HALF, Half.TOP);
			}
			int coord = v == 0 || v == d - 1 ? u : v;
			if (corner || coord % 3 == 0) {
				return p.hash(u, 0, v, 3) == 0 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_BRICKS);
			}
			return planter(u, v);
		}

		private BlockState planter(int u, int v) {
			return switch (p.hash(u, 0, v, 6)) {
				case 0 -> b(Blocks.POTTED_PINK_TULIP);
				case 1 -> b(Blocks.POTTED_FLOWERING_AZALEA);
				case 2 -> leaves(Blocks.FLOWERING_AZALEA_LEAVES);
				case 3 -> leaves(Blocks.AZALEA_LEAVES);
				case 4 -> b(Blocks.POTTED_FERN);
				default -> b(Blocks.PINK_PETALS).setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + p.hash(u, 2, v, 4))
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(p.hash(u, 3, v, 4)));
			};
		}

		private BlockState deckState(int u, int v) {
			if (rail(u, v)) {
				return (u + v) % 3 == 0 ? b(Blocks.STRIPPED_DARK_OAK_WOOD) : b(Blocks.DARK_OAK_PLANKS); // not one smooth band
			}
			int r = p.hash(u, platform - 1, v, 7);
			return r == 0 ? b(Blocks.DARK_OAK_PLANKS) : r == 1 ? b(Blocks.BAMBOO_MOSAIC) : b(Blocks.SPRUCE_PLANKS);
		}

		// -------------------------------------------------------------- the flight

		private void flight() {
			for (int i = 0; i < platform; i++) {
				Block mat = i == 0 ? Blocks.DEEPSLATE_BRICK_STAIRS : i == platform - 1 ? Blocks.POLISHED_ANDESITE_STAIRS : Blocks.STONE_BRICK_STAIRS;
				for (int u = nx; u <= nx + 1; u++) {
					for (int y = 0; y < i; y++) {
						s(u, y, i, stone(u, i, y));
					}
					s(u, i, i, stairs(mat, Direction.SOUTH));
				}
			}
		}

		// -------------------------------------------------------------- balustrade

		private void rails() {
			for (int v = 0; v < d; v++) {
				for (int u = 0; u < w; u++) {
					if (!rail(u, v) || post(u, v)) {
						continue;
					}
					boolean n = rail(u, v - 1);
					boolean so = rail(u, v + 1);
					boolean we = rail(u - 1, v);
					boolean e = rail(u + 1, v);
					boolean straight = (n && so && !we && !e) || (we && e && !n && !so);
					if (straight) {
						s(u, platform, v, b(Blocks.DARK_OAK_FENCE)
								.setValue(BlockStateProperties.NORTH, n).setValue(BlockStateProperties.SOUTH, so)
								.setValue(BlockStateProperties.WEST, we).setValue(BlockStateProperties.EAST, e));
					} else {
						// corners and ends: polished deepslate newels with a lantern
						s(u, platform, v, b(Blocks.POLISHED_DEEPSLATE_WALL).setValue(BlockStateProperties.UP, true)
								.setValue(BlockStateProperties.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE)
								.setValue(BlockStateProperties.SOUTH_WALL, so ? WallSide.LOW : WallSide.NONE)
								.setValue(BlockStateProperties.WEST_WALL, we ? WallSide.LOW : WallSide.NONE)
								.setValue(BlockStateProperties.EAST_WALL, e ? WallSide.LOW : WallSide.NONE));
						if ((u == 0 || u == w - 1) && (v == 0 || v == d - 1)) {
							// the four corners of the deck: a tall lamp post (the top is under the ceiling, nobody can stand on it)
							s(u, platform + 1, v, b(Blocks.POLISHED_DEEPSLATE_WALL).setValue(BlockStateProperties.UP, true));
							s(u, platform + 2, v, b(Blocks.LANTERN));
						}
					}
				}
			}
		}

		// -------------------------------------------------------------- posts, beams, brackets

		private void posts() {
			BlockState log = b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
			Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
			for (int v = 0; v < d; v++) {
				for (int u = 0; u < w; u++) {
					if (post(u, v)) {
						s(u, platform, v, b(Blocks.STONE_BRICKS)); // [depth] a stone foot under every post
						for (int y = platform + 1; y < eave; y++) {
							s(u, y, v, log);
						}
						s(u, eave, v, b(Blocks.STRIPPED_DARK_OAK_WOOD));
						for (Direction dir : dirs) {
							int bu = u + dir.getStepX();
							int bv = v + dir.getStepZ();
							if (bu >= 0 && bu < w && bv >= 0 && bv < d && ring(bu, bv) == 0) {
								// [depth] a bracket under the eave tip on each outer face of the post
								s(bu, eave - 1, bv, stairs(Blocks.DARK_OAK_STAIRS, dir.getOpposite()).setValue(BlockStateProperties.HALF, Half.TOP));
							}
						}
					} else if (ring(u, v) == 1) {
						boolean alongX = v == 1 || v == d - 2;
						s(u, eave, v, b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z));
					}
				}
			}
			// lanterns under the beams (never over a bench: people sit there; one over the telescope, so nobody can stand on it) and in the middle of the ceiling
			int tu = telescopeU();
			int[][] spots = {{tu >= 0 ? tu : w / 2, 1}, {nx + sel(6, 2), 1}, {1, d / 2}, {w - 2, d / 2}, {w / 2, d / 2}};
			for (int[] sp : spots) {
				boolean sideBench = d >= 9 && (sp[0] == 1 || sp[0] == w - 2) && sp[1] != 1;
				if (post(sp[0], sp[1]) || sideBench) {
					continue;
				}
				p.put(c, sp[0], eave - 1, sp[1], b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			}
		}

		// -------------------------------------------------------------- what to do up here

		private void furniture() {
			int y = platform;
			// benches along the back, a lectern at the end, pots in the gaps
			for (int u = 2; u <= w - 3; u++) {
				int v = d - 2;
				if (!free(u, v) || corridor(u, v)) {
					continue;
				}
				int idx = u - 2;
				if (u == 2 && w >= 7) {
					s(u, y, v, b(Blocks.CARTOGRAPHY_TABLE)); // a map table: where the district is planned
				} else if (u == w - 3 && w >= 7) {
					s(u, y, v, b(Blocks.LECTERN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.HAS_BOOK, true));
				} else if (w >= 11 && idx % 5 == 4) {
					s(u, y, v, b(Blocks.POTTED_FERN));
				} else {
					s(u, y, v, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
				}
			}
			// benches along the sides
			if (d >= 9) {
				for (int v = platform + 1; v <= d - 3; v++) {
					if ((v - platform) % 4 == 3) {
						continue;
					}
					if (free(1, v) && !corridor(1, v)) {
						s(1, y, v, stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST));
					}
					if (free(w - 2, v) && !corridor(w - 2, v)) {
						s(w - 2, y, v, stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST));
					}
				}
			}
			// the table, against the back bench (not on a one-cell bench: it would shut the seat in)
			int tw = w >= 9 ? 2 : 1;
			int tv = d - 3;
			int c0 = (w - tw) / 2;
			for (int off = 0; w >= 6 && off <= w; off++) {
				int[] tries = {c0 + off, c0 - off};
				boolean placed = false;
				for (int cx : tries) {
					boolean ok = cx >= 2 && cx + tw - 1 <= w - 3;
					for (int k = 0; ok && k < tw; k++) {
						ok = free(cx + k, tv) && !corridor(cx + k, tv);
					}
					if (ok) {
						for (int k = 0; k < tw; k++) {
							s(cx + k, y, tv, b(Blocks.DARK_OAK_FENCE));
							s(cx + k, y + 1, tv, k % 2 == 0 && tw == 2 ? b(Blocks.DARK_OAK_PRESSURE_PLATE)
									: b(Blocks.CANDLE).setValue(BlockStateProperties.CANDLES, 3).setValue(BlockStateProperties.LIT, true));
						}
						placed = true;
						break;
					}
				}
				if (placed) {
					break;
				}
			}
			// the telescope: a copper rod on a post at the front rail
			int tu = telescopeU();
			if (tu >= 0) {
				s(tu, y, 1, b(Blocks.DARK_OAK_FENCE));
				s(tu, y + 1, 1, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
			}
		}

		/** The column of the telescope on the front row, or -1. */
		private int telescopeU() {
			for (int u = w - 3; u >= 2; u--) {
				if (free(u, 1) && !corridor(u, 1)) {
					return u;
				}
			}
			return -1;
		}

		// -------------------------------------------------------------- the tool shed (platform 4)

		private void shed() {
			if (platform < 4) {
				return;
			}
			int v = d - 2;
			for (int u = 2; u <= w - 3; u += 2) {
				if (!open(u, v)) {
					continue;
				}
				switch (p.hash(u, 0, v, 4)) {
					case 0 -> {
						s(u, 0, v, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
						s(u, 1, v, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
						s(u, 2, v, b(Blocks.LANTERN));
					}
					case 1 -> {
						s(u, 0, v, b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
						s(u, 1, v, b(Blocks.LANTERN));
					}
					case 3 -> {
						s(u, 0, v, b(Blocks.COMPOSTER));
						s(u, 1, v, b(Blocks.LANTERN));
					}
					default -> {
						s(u, 0, v, b(Blocks.CRAFTING_TABLE));
						s(u, 1, v, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
					}
				}
			}
		}

		// -------------------------------------------------------------- hipped copper roof

		private Block copperStairs(int t) {
			return t <= 4 ? Blocks.CUT_COPPER_STAIRS.waxed().weathered() : Blocks.CUT_COPPER_STAIRS.waxed().oxidized();
		}

		private Block copperSlab(int t) {
			return t <= 4 ? Blocks.CUT_COPPER_SLAB.waxed().weathered() : Blocks.CUT_COPPER_SLAB.waxed().oxidized();
		}

		private void roofSet(int u, int y, int v, BlockState st) {
			s(u, y, v, st);
			roofTop[u][v] = Math.max(roofTop[u][v], y);
			for (int yy = eave; yy < y; yy++) {
				Support.fillIfFree(c, p, u, yy, v, b(Blocks.DARK_OAK_PLANKS)); // a plank ceiling under the slope
			}
		}

		private void roof() {
			int kmax = (Math.min(w, d) - 1) / 2;
			int rise = p.h() - 1 - eave; // levels available: 0 .. rise - 1
			boolean steep = kmax <= rise - 1;
			for (int v = 0; v < d; v++) {
				for (int u = 0; u < w; u++) {
					int dl = u;
					int dr = w - 1 - u;
					int df = v;
					int db = d - 1 - v;
					int k = Math.min(Math.min(dl, dr), Math.min(df, db));
					boolean slabRing = !steep && k % 2 == 1;
					int lvl = steep ? k : slabRing ? (k + 1) / 2 : k / 2;
					if (lvl > rise - 1) {
						lvl = rise - 1;
						slabRing = true;
					}
					int y = eave + lvl;
					boolean tieU = dl == k && dr == k;
					boolean tieV = df == k && db == k;
					if ((tieU || tieV) && k == kmax) {
						roofSet(u, y, v, slab(Blocks.CUT_COPPER_SLAB.waxed().oxidized())); // the ridge cap
						continue;
					}
					int t = p.hash(u, y, v, 8);
					if (slabRing) {
						roofSet(u, y, v, slab(copperSlab(t)));
						continue;
					}
					Block mat = k == 0 ? Blocks.DEEPSLATE_TILE_STAIRS : copperStairs(t);
					boolean lr = dl == k || dr == k;
					boolean fb = df == k || db == k;
					Direction inU = dl == k ? Direction.EAST : Direction.WEST;
					Direction inV = df == k ? Direction.SOUTH : Direction.NORTH;
					if (lr && fb && !tieU && !tieV) {
						StairsShape shape = inV == inU.getClockWise() ? StairsShape.OUTER_RIGHT : StairsShape.OUTER_LEFT;
						roofSet(u, y, v, stairs(mat, inU).setValue(StairBlock.SHAPE, shape));
					} else if (lr && !tieU) {
						roofSet(u, y, v, stairs(mat, inU));
					} else {
						roofSet(u, y, v, stairs(mat, inV));
					}
				}
			}
			// the finial: a stack of lightning rods
			int cu = w / 2;
			int cv = d / 2;
			int y0 = roofTop[cu][cv] + 1;
			for (int y = y0; y <= Math.min(y0 + 1, p.h() - 1); y++) {
				s(cu, y, cv, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
			}
		}
	}
}
