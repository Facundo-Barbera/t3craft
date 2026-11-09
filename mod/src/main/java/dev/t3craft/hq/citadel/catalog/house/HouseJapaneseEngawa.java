package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.glassPane;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.log;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.paneU;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.paneV;
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
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * A single-storey Japanese house on a stone footing, wrapped by an engawa (a plank veranda) under
 * deep eaves, with shoji walls of birch trapdoors between dark posts. The roof is an irimoya: a
 * hipped skirt of stairs whose outer corners sweep up (curved eaves) under a raised, gabled core with
 * plaster gable ends. Beside the house, a small garden: a cherry tree, a stone lantern, stepping
 * stones, a pond with lily pads and a bench. The house sits on the left or right of the plot by seed.
 *
 * <pre>
 *  section across (v →)                                 plan (front = v 0, house + garden)
 *  y10-12  core roof 2:1 and ridge cap                  v0   step . . . . . . .  garden (gw 5)
 *  y7-8    clerestory band (plaster, panes)             v1   engawa front (2 deep: v1 v2)
 *  y6      skirt ring 2 ┐                               v3   shoji wall   cherry, lantern,
 *  y5      skirt ring 1 ├ hip, 1:1, curled corners      ..   room         stones, pond, bench
 *  y4      skirt ring 0 ┘ + beam on the posts           d-2  engawa back (1 deep)
 *  y1-3    shoji (birch trapdoors) / posts              d-1  eave
 *  y0      footing + deck boards
 * </pre>
 *
 * Size: 14–18 × 10–13 × 14+ (house is the width minus the 5-wide garden).
 */
public final class HouseJapaneseEngawa implements Variant {
	private static final int GARDEN = 5;
	private static final int BEAM = 4;
	private static final int CORE0 = 9;

	@Override
	public String id() {
		return "house_japanese_engawa";
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
		return 10;
	}

	@Override
	public int minH() {
		return 14;
	}

	@Override
	public int maxW() {
		return 18;
	}

	@Override
	public int maxD() {
		return 13;
	}

	/** [palette] per seed: fascia wood, post wood, tile. */
	private record Scheme(Block fascia, Block post, Block tile, Block tileStair, Block tileSlab) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.CHERRY_STAIRS, Blocks.STRIPPED_CHERRY_LOG, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB),
			new Scheme(Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_LOG, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB),
	};

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x454E_4741_5741L);
		new Builder(c, p, SCHEMES[r.nextInt(SCHEMES.length)], r).run();
	}

	/** One build: house in its own local frame (hu, hv) shifted by {@code off}, garden on the other side. */
	private static final class Builder {
		final CitadelCanvas c;
		final Plot p;
		final Scheme s;
		final Random r;
		final int hw; // house width
		final int d;
		final int off; // u of the house's hu = 0
		final int cu; // door column (hu)
		final boolean gardenLeft;

		Builder(CitadelCanvas c, Plot p, Scheme s, Random r) {
			this.c = c;
			this.p = p;
			this.s = s;
			this.r = r;
			this.hw = p.w() - GARDEN;
			this.d = p.d();
			this.gardenLeft = r.nextBoolean();
			this.off = gardenLeft ? GARDEN : 0;
			this.cu = hw / 2;
		}

		void set(int hu, int y, int hv, BlockState st) {
			p.set(c, off + hu, y, hv, st);
		}

		void put(int hu, int y, int hv, BlockState st, int layer) {
			p.put(c, off + hu, y, hv, st, layer);
		}

		void run() {
			footing();
			posts();
			shoji();
			skirt();
			core();
			frontProps();
			garden();
		}

		// ------------------------------------------------------------------ materials

		BlockState stone(int hu, int y, int hv) {
			return switch (p.hash(off + hu, y, hv, 6)) {
				case 0 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
				case 1 -> b(Blocks.COBBLED_DEEPSLATE);
				default -> b(Blocks.DEEPSLATE_BRICKS);
			};
		}

		/** [palette] deck boards: bamboo mosaic and spruce in a running pattern. */
		BlockState board(int hu, int hv) {
			return ((hu + hv) & 1) == 0 && p.hash(off + hu, 0, hv, 3) != 0 ? b(Blocks.BAMBOO_MOSAIC) : b(Blocks.SPRUCE_PLANKS);
		}

		BlockState plaster(int hu, int y, int hv) {
			return p.hash(off + hu, y, hv, 9) == 0 ? b(Blocks.TUFF_BRICKS) : b(Blocks.CALCITE);
		}

		// ------------------------------------------------------------------ footing and deck (y0)

		void footing() {
			for (int hu = 1; hu <= hw - 2; hu++) {
				for (int hv = 1; hv <= d - 2; hv++) {
					boolean room = hu >= 2 && hu <= hw - 3 && hv >= 3 && hv <= d - 3;
					set(hu, 0, hv, room ? b(Blocks.DARK_OAK_PLANKS) : board(hu, hv));
				}
			}
			// [depth] a stone ledge round the platform at ground level, a step in front of the door
			for (int hu = 0; hu <= hw - 1; hu++) {
				set(hu, 0, 0, slab(Blocks.POLISHED_DEEPSLATE_SLAB));
				set(hu, 0, d - 1, slab(Blocks.POLISHED_DEEPSLATE_SLAB));
			}
			for (int hv = 1; hv <= d - 2; hv++) {
				set(0, 0, hv, slab(Blocks.POLISHED_DEEPSLATE_SLAB));
				set(hw - 1, 0, hv, slab(Blocks.POLISHED_DEEPSLATE_SLAB));
			}
			set(cu, 0, 0, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
			set(cu - 1, 0, 0, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
			// [palette] post feet: dark foundation stones where the posts stand
			for (int[] f : postCells()) {
				set(f[0], 0, f[1], stone(f[0], 0, f[1]));
			}
		}

		/** Post positions on the outer ring of the platform: corners and every third cell. */
		int[][] postCells() {
			java.util.List<int[]> out = new java.util.ArrayList<>();
			for (int hu = 1; hu <= hw - 2; hu++) {
				if (isPostAlong(hu, 1, hw - 2)) {
					// the two door columns stay clear at the veranda edge: a post there stood right in the way from the
					// steps to the door, so it moves one cell aside (the beam spans over the entrance)
					int fu = hu == cu - 1 ? cu - 2 : hu == cu ? cu + 1 : hu;
					out.add(new int[] {fu, 1});
					out.add(new int[] {hu, d - 2});
				}
			}
			for (int hv = 2; hv <= d - 3; hv++) {
				if (isPostAlong(hv, 1, d - 2)) {
					out.add(new int[] {1, hv});
					out.add(new int[] {hw - 2, hv});
				}
			}
			return out.toArray(new int[0][]);
		}

		boolean isPostAlong(int i, int a, int bb) {
			return i == a || i == bb || (i - a) % 3 == 0 && bb - i >= 2;
		}

		void posts() {
			for (int[] f : postCells()) {
				for (int y = 1; y <= 3; y++) {
					set(f[0], y, f[1], log(s.post(), Direction.Axis.Y));
				}
			}
			// [depth] the beam on the posts, round the whole platform
			for (int hu = 1; hu <= hw - 2; hu++) {
				set(hu, BEAM, 1, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
				set(hu, BEAM, d - 2, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			}
			for (int hv = 1; hv <= d - 2; hv++) {
				set(1, BEAM, hv, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
				set(hw - 2, BEAM, hv, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
			}
		}

		// ------------------------------------------------------------------ shoji walls (hu 2..hw-3, hv 3..d-3)

		boolean wallPost(int i, int a, int bb) {
			return i == a || i == bb || (i - a) % 3 == 0 && bb - i >= 2;
		}

		BlockState shojiCell(int y, boolean alongU) {
			return switch (y) {
				case 1 -> b(Blocks.BIRCH_PLANKS); // [palette] a pale wainscot under the paper
				case 2 -> trapdoor(Blocks.BIRCH_TRAPDOOR, alongU ? Direction.NORTH : Direction.WEST, true, Half.BOTTOM);
				default -> alongU ? paneU(glassPane(DyeColor.WHITE)) : paneV(glassPane(DyeColor.WHITE));
			};
		}

		void shoji() {
			int a = 2;
			int bb = hw - 3;
			int va = 3;
			int vb = d - 3;
			for (int y = 1; y <= 3; y++) {
				for (int hu = a; hu <= bb; hu++) {
					for (int hv : new int[] {va, vb}) {
						boolean door = hv == va && (hu == cu || hu == cu - 1) && y <= 2;
						BlockState st;
						if (door) {
							st = b(Blocks.AIR);
						} else if (wallPost(hu, a, bb)) {
							st = log(Blocks.STRIPPED_BIRCH_LOG, Direction.Axis.Y);
						} else {
							st = shojiCell(y, true);
						}
						if (!door) {
							set(hu, y, hv, st);
						}
					}
				}
				for (int hv = va + 1; hv < vb; hv++) {
					for (int hu : new int[] {a, bb}) {
						set(hu, y, hv, wallPost(hv, va, vb) ? log(Blocks.STRIPPED_BIRCH_LOG, Direction.Axis.Y) : shojiCell(y, false));
					}
				}
			}
			// the front door: a pair of cherry doors set back one block
			for (int hu : new int[] {cu - 1, cu}) {
				for (int y = 1; y <= 2; y++) {
					set(hu, y, va + 1, Blocks.CHERRY_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
							.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, y == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
							.setValue(BlockStateProperties.DOOR_HINGE, hu == cu ? net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT
									: net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT));
				}
				set(hu, 3, va, log(Blocks.DARK_OAK_LOG, Direction.Axis.X)); // lintel
			}
			// the wall beam, then plaster above it
			for (int hu = a; hu <= bb; hu++) {
				for (int hv : new int[] {va, vb}) {
					set(hu, BEAM, hv, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
					for (int y = 5; y <= (hv == va ? 6 : 5); y++) {
						set(hu, y, hv, wallPost(hu, a, bb) ? log(s.post(), Direction.Axis.Y) : plaster(hu, y, hv));
					}
				}
			}
			for (int hv = va; hv <= vb; hv++) {
				for (int hu : new int[] {a, bb}) {
					set(hu, BEAM, hv, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
					if (hv != va && hv != vb) {
						set(hu, 5, hv, wallPost(hv, va, vb) ? log(s.post(), Direction.Axis.Y) : plaster(hu, 5, hv));
					}
				}
			}
		}

		// ------------------------------------------------------------------ skirt: hipped eaves, rings 0..2

		static Direction cornerFacing(boolean top, boolean left) {
			if (top) {
				return left ? Direction.SOUTH : Direction.WEST;
			}
			return left ? Direction.EAST : Direction.NORTH;
		}

		void skirt() {
			for (int k = 0; k <= 2; k++) {
				int u0 = k;
				int u1 = hw - 1 - k;
				int v0 = k;
				int v1 = d - 1 - k;
				Block mat0 = k == 0 ? s.fascia() : s.tileStair();
				for (int hu = u0; hu <= u1; hu++) {
					for (int hv = v0; hv <= v1; hv++) {
						boolean top = hv == v0;
						boolean bot = hv == v1;
						boolean left = hu == u0;
						boolean right = hu == u1;
						if (!(top || bot || left || right)) {
							continue;
						}
						int y = 4 + k;
						Block mat = k > 0 && p.hash(off + hu, y, hv, 6) == 0 ? Blocks.DEEPSLATE_BRICK_STAIRS : mat0;
						BlockState st;
						if ((top || bot) && (left || right)) {
							st = stair(mat, cornerFacing(top, left), Half.BOTTOM, StairsShape.OUTER_LEFT);
							if (k == 0) {
								y = 5; // [depth] the eave corner sweeps up
							}
						} else if (top) {
							st = stair(mat, Direction.SOUTH);
						} else if (bot) {
							st = stair(mat, Direction.NORTH);
						} else if (left) {
							st = stair(mat, Direction.EAST);
						} else {
							st = stair(mat, Direction.WEST);
						}
						if (k == 0 && cornerDist(hu, hv) == 1) {
							y = 5; // the two cells beside a corner rise with it
						}
						set(hu, y, hv, st);
						// [depth] under a lifted eave tip, an upside-down stair makes the curve
						if (k == 0 && y == 5) {
							BlockState sup = (top || bot) && (left || right)
									? stair(s.fascia(), cornerFacing(top, left), Half.TOP, StairsShape.OUTER_LEFT)
									: stairTop(s.fascia(), top ? Direction.NORTH : bot ? Direction.SOUTH : left ? Direction.WEST : Direction.EAST);
							set(hu, 4, hv, sup);
						}
					}
				}
			}
			// [finish] a small hanging lantern at each lifted corner
			for (int[] cc : new int[][] {{0, 0}, {hw - 1, 0}, {0, d - 1}, {hw - 1, d - 1}}) {
				put(cc[0], 3, cc[1], lantern(true), CitadelCanvas.HANGING);
			}
		}

		int cornerDist(int hu, int hv) {
			int du = Math.min(hu, hw - 1 - hu);
			int dv = Math.min(hv, d - 1 - hv);
			return du + dv;
		}

		// ------------------------------------------------------------------ core: clerestory and gabled roof

		int coreRowK(int hv) {
			return Math.min(hv - 2, d - 3 - hv);
		}

		int coreY(int hv) {
			return CORE0 + coreRowK(hv) / 2;
		}

		void core() {
			int cu0 = 3;
			int cu1 = hw - 4;
			int cv0 = 3;
			int cv1 = d - 4;
			// clerestory band y7..y8
			for (int y = 7; y <= 8; y++) {
				for (int hu = cu0; hu <= cu1; hu++) {
					for (int hv : new int[] {cv0, cv1}) {
						boolean pane = y == 8 && (hu - cu0) % 2 == 1 && hv == cv0;
						set(hu, y, hv, pane ? paneU(glassPane(DyeColor.WHITE)) : (hu == cu0 || hu == cu1) ? log(s.post(), Direction.Axis.Y) : plaster(hu, y, hv));
					}
				}
				for (int hv = cv0 + 1; hv < cv1; hv++) {
					for (int hu : new int[] {cu0, cu1}) {
						set(hu, y, hv, plaster(hu, y, hv));
					}
				}
			}
			// gable ends: plaster triangles up to the roof, with a king post
			int mid = (cv0 + cv1) / 2;
			for (int hu : new int[] {cu0, cu1}) {
				for (int hv = cv0; hv <= cv1; hv++) {
					for (int y = 9; y < coreY(hv); y++) {
						BlockState st = hv == mid ? log(s.post(), Direction.Axis.Y) : plaster(hu, y, hv);
						if (y == 9 && Math.abs(hv - mid) == 1) {
							st = paneV(glassPane(DyeColor.PINK)); // [palette] a pair of rose lights flanking the king post
						}
						set(hu, y, hv, st);
					}
				}
			}
			// core roof rows: 2:1, stair then block
			int n = d - 4;
			for (int hv = 2; hv <= d - 3; hv++) {
				int k = coreRowK(hv);
				int y = coreY(hv);
				boolean frontHalf = hv - 2 < d - 3 - hv;
				boolean center = n % 2 == 1 && hv - 2 == d - 3 - hv;
				Direction up = frontHalf ? Direction.SOUTH : Direction.NORTH;
				for (int hu = 2; hu <= hw - 3; hu++) {
					BlockState st;
					if (center || k % 2 == 1) {
						st = p.hash(off + hu, y, hv, 4) == 0 ? b(Blocks.POLISHED_DEEPSLATE) : b(s.tile());
					} else {
						st = stair(k == 0 ? s.fascia() : p.hash(off + hu, y, hv, 6) == 0 ? Blocks.DEEPSLATE_BRICK_STAIRS : s.tileStair(), up);
					}
					set(hu, y, hv, st);
				}
			}
			int top = coreY((d - 1) / 2);
			for (int hv = 2; hv <= d - 3; hv++) {
				int k = coreRowK(hv);
				boolean centerRow = k == coreRowK((d - 1) / 2) && (hv == (d - 1) / 2 || hv == d / 2);
				if (centerRow) {
					for (int hu = 2; hu <= hw - 3; hu++) {
						set(hu, coreY(hv) + 1, hv, slab(Blocks.DEEPSLATE_BRICK_SLAB));
					}
				}
			}
			// [finish] end tiles on the ridge
			for (int hu : new int[] {2, hw - 3}) {
				set(hu, top + 2, (d - 1) / 2, wall(Blocks.DEEPSLATE_BRICK_WALL, true, false, false, false, false));
				if (d % 2 == 0) {
					set(hu, top + 2, d / 2, wall(Blocks.DEEPSLATE_BRICK_WALL, true, false, false, false, false));
				}
			}
			// [depth] rafters under the core eaves
			for (int hu = 2; hu <= hw - 3; hu += 2) {
				set(hu, 8, 2, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
				set(hu, 8, d - 3, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
			}
			for (int hv = 4; hv <= d - 5; hv += 2) {
				set(2, 8, hv, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
				set(hw - 3, 8, hv, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			}
		}

		// ------------------------------------------------------------------ front props

		void frontProps() {
			// [finish] lanterns on the veranda beam, either side of the door
			put(cu - 3, 3, 1, lantern(true), CitadelCanvas.HANGING);
			put(cu + 2, 3, 1, lantern(true), CitadelCanvas.HANGING);
			// planters on the deck and a stone basin by the steps
			set(1, 1, 2, b(Blocks.POTTED_FLOWERING_AZALEA));
			set(hw - 2, 1, 2, b(Blocks.POTTED_AZALEA));
			set(hw - 2, 1, d - 3, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		}

		// ------------------------------------------------------------------ garden (the strip beside the house)

		void garden() {
			int g0 = gardenLeft ? 0 : hw;
			// ground: moss, raked gravel and coarse dirt in patches, never one flat colour
			for (int gu = 0; gu < GARDEN; gu++) {
				for (int v = 0; v < d; v++) {
					BlockState st = switch (p.hash(g0 + gu, 0, v, 6)) {
						case 0 -> b(Blocks.MOSS_BLOCK);
						case 1 -> b(Blocks.COARSE_DIRT);
						case 2, 3 -> b(Blocks.GRAVEL);
						default -> b(Blocks.MOSS_BLOCK);
					};
					p.set(c, g0 + gu, 0, v, st);
				}
			}
			// stepping stones from the street towards the tree
			for (int v = 0; v < d - 2; v += 1) {
				int gu = (v % 2 == 0) ? 1 : 2;
				p.set(c, g0 + gu, 0, v, b(Blocks.POLISHED_ANDESITE));
				if (v % 3 == 2) {
					p.set(c, g0 + gu + (v % 2 == 0 ? 1 : -1), 0, v, b(Blocks.STONE_BRICKS));
				}
			}
			// a low hedge and bamboo along the street edge and the outer side
			int outer = gardenLeft ? g0 : g0 + GARDEN - 1;
			for (int v = 1; v < d; v++) {
				p.set(c, outer, 1, v, fence(Blocks.BAMBOO_FENCE, v > 1, false, v < d - 1, false));
				if (p.hash(outer, 1, v, 3) == 0) {
					p.set(c, outer, 2, v, b(Blocks.BAMBOO));
					p.set(c, outer, 3, v, b(Blocks.BAMBOO));
				}
			}
			for (int gu = 0; gu < GARDEN; gu++) {
				if (g0 + gu != outer) {
					p.set(c, g0 + gu, 1, d - 1, p.hash(g0 + gu, 1, d - 1, 2) == 0 ? CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES) : CitadelCanvas.leaves(Blocks.AZALEA_LEAVES));
				}
			}
			// the cherry, off the path
			int tu = gardenLeft ? g0 + 3 : g0 + 1;
			JapaneseKit.sakura(c, p, tu, 1, d / 2 + 1, 0);
			// the stone lantern near the street, a pond by the house, a bench in the shade
			int lu = gardenLeft ? g0 + 4 : g0;
			JapaneseKit.stoneLantern(c, p, lu, 1, 1);
			int pu = gardenLeft ? g0 + 3 : g0 + 1;
			for (int du = 0; du < 2; du++) {
				for (int dv = 0; dv < 2; dv++) {
					p.set(c, pu + du, 0, 2 + dv, b(Blocks.WATER));
				}
			}
			p.set(c, pu, 1, 2, b(Blocks.LILY_PAD));
			p.set(c, pu + 1, 1, 3, b(Blocks.LILY_PAD));
			for (int du = -1; du <= 2; du++) {
				for (int dv = -1; dv <= 2; dv++) {
					boolean edge = du == -1 || du == 2 || dv == -1 || dv == 2;
					if (edge && p.hash(pu + du, 0, 2 + dv, 3) != 0 && !(g0 + 1 <= pu + du && pu + du <= g0 + 2 && dv == -1)) {
						p.set(c, pu + du, 0, 2 + dv, b(Blocks.MOSSY_COBBLESTONE));
					}
				}
			}
			int bu = gardenLeft ? g0 + 1 : g0 + 3;
			p.set(c, bu, 1, d - 3, stair(Blocks.SPRUCE_STAIRS, gardenLeft ? Direction.EAST : Direction.WEST));
			p.set(c, bu, 1, d - 4, b(Blocks.POTTED_CHERRY_SAPLING));
		}
	}
}
