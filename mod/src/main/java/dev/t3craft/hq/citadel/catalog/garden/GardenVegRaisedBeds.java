package dev.t3craft.hq.citadel.catalog.garden;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A kitchen garden: two long raised beds framed in dark oak and spruce with a gravel path between
 * them, each bed striped with different crops, a scarecrow in one bed, a lean-to compost shed with
 * composters, a potting corner with rain barrel and hay, a hedge of azalea with sunflowers at the back
 * and a pumpkin pile at the front.
 *
 * <pre>
 *  v0     kerb slabs, pumpkins, lamp posts, gravel entry
 *  v1..   ▒▒▒▒ bed ▒▒▒▒  path  ▒▒▒▒ bed ▒▒▒▒   (frame 1.5 high, soil one block below the crops' feet)
 *  d-3/-2 compost shed | path | potting corner
 *  d-1    hedge + sunflowers
 * </pre>
 *
 * Size: 10–12 × 9–12 × 5+. [depth] beds are framed boxes with taller corner posts; scarecrow arms
 * and the shed's lean-to roof break the silhouette. [palette] dark oak → spruce → hay, with gravel and
 * coarse dirt in the paths and the greens/yellows/oranges of the crops. [finish] lanterns, pumpkins,
 * pots, mushrooms, mixed crop ages.
 */
public final class GardenVegRaisedBeds implements Variant {
	private static final Direction[] LOCAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	@Override
	public String id() {
		return "garden_veg_raised_beds";
	}

	@Override
	public Family family() {
		return Family.GARDEN;
	}

	@Override
	public int minW() {
		return 10;
	}

	@Override
	public int minD() {
		return 9;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 12;
	}

	@Override
	public int maxD() {
		return 12;
	}

	private enum Crop {
		WHEAT, CARROTS, POTATOES, BEETROOTS, BERRIES, PUMPKINS
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int pw = w % 2 == 0 ? 2 : 3; // path width between the beds
		int bw = (w - pw) / 2; // bed width, frame included
		int rightU = w - bw; // first column of the right bed
		int bv0 = 1;
		int bv1 = d - 4; // last row of the beds (frame)
		int pathL = bw;
		int pathR = w - bw - 1;

		yard(c, p, pathL, pathR, bw, bv1);
		Crop[][] plan = cropPlan(p);
		bed(c, p, 0, bv0, bw, bv1, plan[0]);
		bed(c, p, rightU, bv0, bw, bv1, plan[1]);
		scarecrow(c, p, rightU + bw / 2, (bv0 + bv1) / 2);
		shed(c, p, d);
		pottingCorner(c, p, w, d);
		hedge(c, p, w, d);
		frontRow(c, p, w, pathL, pathR, bw);
	}

	/** Which crop each interior column grows, by seed (front half and back half differ). */
	private static Crop[][] cropPlan(Plot p) {
		Crop[][][] opts = {
				{{Crop.WHEAT, Crop.CARROTS, Crop.POTATOES}, {Crop.BEETROOTS, Crop.WHEAT, Crop.BERRIES}},
				{{Crop.CARROTS, Crop.WHEAT, Crop.BEETROOTS}, {Crop.POTATOES, Crop.BERRIES, Crop.WHEAT}},
		};
		return opts[p.hash(1, 1, 1, opts.length)];
	}

	// ------------------------------------------------------------------ floor

	/** Gravel, coarse dirt and a few cobbles: the working floor of paths and strips. */
	private static BlockState ground(Plot p, int u, int v) {
		return switch (p.hash(u, 0, v, 8)) {
			case 0, 1 -> b(Blocks.COARSE_DIRT);
			case 2 -> b(Blocks.COBBLESTONE);
			case 3 -> b(Blocks.PACKED_MUD);
			default -> b(Blocks.GRAVEL);
		};
	}

	private static void yard(CitadelCanvas c, Plot p, int pathL, int pathR, int bw, int bv1) {
		for (int u = 0; u < p.w(); u++) {
			for (int v = 0; v < p.d(); v++) {
				boolean bed = (u < bw || u > p.w() - 1 - bw) && v >= 1 && v <= bv1;
				if (!bed && v < p.d() - 1) {
					p.set(c, u, 0, v, ground(p, u, v));
				}
			}
		}
	}

	// ------------------------------------------------------------------ beds

	private static BlockState frame(Plot p, int u, int v) {
		return switch (p.hash(u, 0, v, 5)) {
			case 0, 1, 2 -> b(Blocks.OAK_PLANKS);
			case 3 -> b(Blocks.STRIPPED_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X);
			default -> b(Blocks.SPRUCE_PLANKS);
		};
	}

	private static BlockState crop(Plot p, Crop type, int u, int v) {
		int age = 4 + p.hash(u, 2, v, 4); // 4..7: a mix of ripeness
		return switch (type) {
			case WHEAT -> b(Blocks.WHEAT).setValue(BlockStateProperties.AGE_7, Math.min(7, age + 1));
			case CARROTS -> b(Blocks.CARROTS).setValue(BlockStateProperties.AGE_7, Math.min(7, age + 1));
			case POTATOES -> b(Blocks.POTATOES).setValue(BlockStateProperties.AGE_7, Math.min(7, age + 1));
			case BEETROOTS -> b(Blocks.BEETROOTS).setValue(BlockStateProperties.AGE_3, Math.min(3, age - 2));
			case BERRIES -> b(Blocks.SWEET_BERRY_BUSH).setValue(BlockStateProperties.AGE_3, Math.min(3, age - 2));
			case PUMPKINS -> p.hash(u, 3, v, 3) == 0 ? b(Blocks.PUMPKIN) : b(Blocks.MELON);
		};
	}

	/** A framed bed: frame ring 1.5 high with 2-high corner posts, soil inside with crops in columns. */
	private static void bed(CitadelCanvas c, Plot p, int u0, int v0, int bw, int bv1, Crop[] cols) {
		int u1 = u0 + bw - 1;
		int mid = (v0 + bv1) / 2;
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= bv1; v++) {
				boolean ring = u == u0 || u == u1 || v == v0 || v == bv1;
				boolean corner = (u == u0 || u == u1) && (v == v0 || v == bv1);
				if (corner) {
					p.set(c, u, 0, v, b(Blocks.DARK_OAK_LOG));
					p.set(c, u, 1, v, b(Blocks.DARK_OAK_LOG));
					p.set(c, u, 2, v, slab(Blocks.OAK_SLAB));
				} else if (ring) {
					p.set(c, u, 0, v, frame(p, u, v));
					p.set(c, u, 1, v, slab(Blocks.OAK_SLAB));
				} else {
					int col = u - u0 - 1;
					Crop type = cols[Math.min(col, cols.length - 1)];
					boolean back = v > mid;
					if (back && type == Crop.WHEAT) {
						type = Crop.POTATOES; // second half of the bed: another crop in the same column
					} else if (back && type == Crop.CARROTS) {
						type = Crop.PUMPKINS;
					}
					p.set(c, u, 0, v, b(Blocks.FARMLAND).setValue(BlockStateProperties.MOISTURE, 7));
					p.set(c, u, 1, v, crop(p, type, u, v));
				}
			}
		}
		// lamp posts on the two corners nearest the path
		int lampU = u0 == 0 ? u1 : u0;
		p.set(c, lampU, 2, v0, b(Blocks.OAK_FENCE));
		p.set(c, lampU, 3, v0, b(Blocks.LANTERN));
	}

	// ------------------------------------------------------------------ scarecrow

	private static void scarecrow(CitadelCanvas c, Plot p, int u, int v) {
		p.set(c, u, 0, v, b(Blocks.COARSE_DIRT));
		p.set(c, u, 1, v, b(Blocks.OAK_FENCE));
		p.set(c, u, 2, v, b(Blocks.HAY_BLOCK));
		p.set(c, u - 1, 2, v, b(Blocks.OAK_FENCE).setValue(BlockStateProperties.EAST, true));
		p.set(c, u + 1, 2, v, b(Blocks.OAK_FENCE).setValue(BlockStateProperties.WEST, true));
		p.set(c, u, 3, v, b(Blocks.CARVED_PUMPKIN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		p.set(c, u, 4, v, slab(Blocks.DARK_OAK_SLAB));
		// straw hands and a few hay tufts at the feet
		p.set(c, u - 1, 1, v, b(Blocks.HAY_BLOCK));
	}

	// ------------------------------------------------------------------ compost shed

	/** Lean-to: four posts, two composters and a mushroom heap under a roof that rises towards the back. */
	private static void shed(CitadelCanvas c, Plot p, int d) {
		int vf = d - 3;
		int vb = d - 2;
		for (int[] uv : new int[][] {{0, vf}, {3, vf}}) {
			p.set(c, uv[0], 1, uv[1], b(Blocks.SPRUCE_LOG));
			p.set(c, uv[0], 2, uv[1], b(Blocks.SPRUCE_LOG));
		}
		for (int[] uv : new int[][] {{0, vb}, {3, vb}}) {
			for (int y = 1; y <= 2; y++) {
				p.set(c, uv[0], y, uv[1], b(Blocks.SPRUCE_LOG));
			}
		}
		// a mini gable: both rows of stairs rise to the middle, ridge between them
		for (int u = 0; u <= 3; u++) {
			p.set(c, u, 3, vf, stairs(Blocks.OAK_STAIRS, Direction.SOUTH));
			p.set(c, u, 3, vb, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
		}
		p.set(c, 1, 1, vb, b(Blocks.COMPOSTER).setValue(BlockStateProperties.LEVEL_COMPOSTER, 8));
		p.set(c, 2, 1, vb, b(Blocks.COMPOSTER).setValue(BlockStateProperties.LEVEL_COMPOSTER, 5));
		p.set(c, 1, 0, vf, b(Blocks.PODZOL));
		p.set(c, 1, 1, vf, b(Blocks.BROWN_MUSHROOM));
		p.set(c, 2, 1, vf, b(Blocks.HAY_BLOCK));
		p.set(c, 2, 2, vf, b(Blocks.HAY_BLOCK));
		p.set(c, 0, 1, vb - 0, b(Blocks.SPRUCE_LOG));
		p.put(c, 1, 2, vf, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ potting corner

	private static void pottingCorner(CitadelCanvas c, Plot p, int w, int d) {
		int u0 = w - 4;
		int vf = d - 3;
		int vb = d - 2;
		p.set(c, u0, 1, vb, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		p.set(c, u0 + 1, 1, vb, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		p.set(c, u0 + 2, 1, vb, b(Blocks.HAY_BLOCK));
		p.set(c, u0 + 2, 2, vb, b(Blocks.HAY_BLOCK));
		p.set(c, u0 + 3, 1, vb, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		p.set(c, u0 + 3, 2, vb, b(Blocks.POTTED_FLOWERING_AZALEA));
		p.set(c, u0, 2, vb, b(Blocks.POTTED_FERN));
		p.set(c, u0 + 1, 1, vf, b(Blocks.POTTED_RED_TULIP));
		p.set(c, u0 + 3, 1, vf, b(Blocks.PUMPKIN));
		p.set(c, u0 + 2, 1, vf, b(Blocks.MELON));
		p.set(c, u0 + 3, 2, vf, b(Blocks.CARVED_PUMPKIN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
	}

	// ------------------------------------------------------------------ hedge and front

	private static void hedge(CitadelCanvas c, Plot p, int w, int d) {
		int v = d - 1;
		for (int u = 0; u < w; u++) {
			int k = p.hash(u, 5, v, 5);
			if (k == 0) {
				p.set(c, u, 0, v, b(Blocks.SUNFLOWER).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
				p.set(c, u, 1, v, b(Blocks.SUNFLOWER).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
			} else {
				p.set(c, u, 0, v, k == 1 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
				p.set(c, u, 1, v, k == 2 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
				if (k >= 3 && p.hash(u, 6, v, 2) == 0) {
					p.set(c, u, 2, v, leaves(Blocks.AZALEA_LEAVES));
				}
			}
		}
	}

	private static void frontRow(CitadelCanvas c, Plot p, int w, int pathL, int pathR, int bw) {
		for (int u = 0; u < w; u++) {
			if (u >= pathL && u <= pathR) {
				p.set(c, u, 0, 0, slab(Blocks.STONE_BRICK_SLAB));
			} else {
				p.set(c, u, 0, 0, switch (p.hash(u, 0, 0, 3)) {
					case 0 -> b(Blocks.MOSSY_COBBLESTONE);
					case 1 -> b(Blocks.COBBLESTONE);
					default -> b(Blocks.STONE_BRICKS);
				});
			}
		}
		// pumpkin pile on the left, planter on the right
		p.set(c, 0, 1, 0, b(Blocks.HAY_BLOCK));
		p.set(c, 0, 2, 0, b(Blocks.PUMPKIN));
		p.set(c, 1, 1, 0, b(Blocks.CARVED_PUMPKIN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		p.set(c, w - 1, 1, 0, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		p.set(c, w - 1, 2, 0, b(Blocks.FLOWERING_AZALEA));
		p.set(c, w - 2, 1, 0, b(Blocks.POTTED_OXEYE_DAISY));
	}
}
