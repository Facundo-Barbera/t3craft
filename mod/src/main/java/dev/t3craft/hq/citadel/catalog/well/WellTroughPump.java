package dev.t3craft.hq.citadel.catalog.well;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Watering trough: a long log trough on a rough cobble footing, filled to the brim, with a stone
 * hand pump on its back rim (chiseled head, lever handle, a copper spout over the water) and a
 * lantern post at the pump end. At the other end barrels and a bucket-cauldron; a curb in front and
 * hay bales behind when the plot is deep enough.
 *
 * <pre>
 *  plan (w = 7, d = 5)                     section through the pump (v →)
 *  v0  ▬ ▬ ~ ▬ ▬ ~ ▬   curb, moss           y3   ▬     pump cap
 *  v1  ╋ ═ ═ ═ ═ ╋ ▤   front rim, barrel    y2  ►▣ ◄   spout, head, (lever on the side)
 *  v2  ║ ≈ ≈ ≈ ≈ ║ ◘   water, cauldron      y1  ═≈╫    rim, water, pump shaft
 *  v3  ╋ P ═ ═ ═ ╋ ▤   back rim with pump   y0  ▓▓▓    cobble footing
 *  v4  ▦ ✿ . ▦ ✿ . .   hay, flowers
 * </pre>
 *
 * Size: 5–8 × 3–5 × 4+ (from height 5 a pump house roof on four posts over the pump end; ridge cap from 6, finial from 7).
 */
public final class WellTroughPump implements Variant {
	private static final BlockState RIM_X = b(Blocks.STRIPPED_SPRUCE_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X);
	private static final BlockState RIM_Z = b(Blocks.STRIPPED_SPRUCE_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
	private static final BlockState CORNER = b(Blocks.STRIPPED_SPRUCE_LOG); // axis Y: end grain on top
	private static final BlockState BARREL_UP = b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);

	@Override
	public String id() {
		return "well_trough_pump";
	}

	@Override
	public Family family() {
		return Family.WELL;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 4;
	}

	@Override
	public int maxW() {
		return 8;
	}

	@Override
	public int maxD() {
		return 5;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int vf = d >= 4 ? 1 : 0; // front rim
		int vm = vf + 1; // water
		int vk = vf + 2; // back rim (pump)
		int ue = w - 2; // right end of the trough; u = w-1 holds the barrels
		int pu = 1; // pump column

		trough(c, p, ue, vf, vm, vk);
		pump(c, p, pu, vm, vk);
		if (p.h() >= 5) {
			canopy(c, p, ue, vf, vm, vk);
		} else {
			lanternPost(c, p, vk);
		}
		barrels(c, p, w - 1, vf, vm, vk);
		if (vf == 1) {
			curb(c, p);
		}
		if (d >= 5) {
			hay(c, p, d - 1);
		}
	}

	// ------------------------------------------------------------------ trough (y0..y1)

	/** [palette] the dark, rough footing: cobblestone, mossy cobblestone and cobbled deepslate by hash. */
	private static BlockState footing(Plot p, int u, int v) {
		return switch (p.hash(u, 0, v, 4)) {
			case 0 -> b(Blocks.MOSSY_COBBLESTONE);
			case 1 -> b(Blocks.COBBLED_DEEPSLATE);
			default -> b(Blocks.COBBLESTONE);
		};
	}

	/**
	 * [depth] a raised trough, not a puddle: water at y1 on a footing at y0, held by log rims that run
	 * along their wall (axis X front/back, Z at the ends) with upright log corners.
	 */
	private static void trough(CitadelCanvas c, Plot p, int ue, int vf, int vm, int vk) {
		for (int u = 0; u <= ue; u++) {
			for (int v = vf; v <= vk; v++) {
				p.set(c, u, 0, v, footing(p, u, v));
				boolean end = u == 0 || u == ue;
				BlockState st;
				if (end && v != vm) {
					st = CORNER;
				} else if (end) {
					st = RIM_Z;
				} else if (v != vm) {
					st = RIM_X;
				} else {
					st = b(Blocks.WATER);
				}
				p.set(c, u, 1, v, st);
			}
		}
		// [finish] a lily pad drifting at the far end of the water (by seed), away from the spout
		int lu = 2 + p.hash(0, 2, vm, Math.max(1, ue - 2));
		if (lu < ue && p.hash(1, 2, vm, 3) != 0) {
			p.set(c, lu, 2, vm, b(Blocks.LILY_PAD));
		}
	}

	// ------------------------------------------------------------------ pump

	/**
	 * [palette] the light top of the gradient: a stone-brick wall shaft, a chiseled head and a slab
	 * cap; [finish] a copper lightning rod as the spout over the water and a lever as the handle.
	 */
	private static void pump(CitadelCanvas c, Plot p, int pu, int vm, int vk) {
		// the shaft stands in the back rim: it joins the logs on both sides (low wall arms)
		p.set(c, pu, 1, vk, b(Blocks.STONE_BRICK_WALL)
				.setValue(BlockStateProperties.WEST_WALL, WallSide.LOW)
				.setValue(BlockStateProperties.EAST_WALL, WallSide.LOW));
		p.set(c, pu, 2, vk, b(Blocks.CHISELED_STONE_BRICKS));
		p.set(c, pu, 3, vk, slab(Blocks.STONE_BRICK_SLAB));
		p.set(c, pu, 2, vm, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
		p.put(c, pu + 1, 2, vk, b(Blocks.LEVER)
				.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), CitadelCanvas.DECOR);
	}

	/** A spruce fence post on the back-left corner log, joined to the pump head, with a lantern on top. */
	private static void lanternPost(CitadelCanvas c, Plot p, int vk) {
		int top = p.h() >= 5 ? 3 : 2;
		for (int y = 2; y <= top; y++) {
			BlockState f = b(Blocks.SPRUCE_FENCE);
			if (y == 2) {
				f = f.setValue(BlockStateProperties.EAST, true); // the pump head is a full block beside it
			}
			p.set(c, 0, y, vk, f);
		}
		p.set(c, 0, top + 1, vk, b(Blocks.LANTERN));
	}

	// ------------------------------------------------------------------ canopy (h >= 5)

	/**
	 * [finish] a little pump house: a gable roof on four spruce posts over the pump end only (u 0..2),
	 * so the rest of the trough stays open and its water shows. A cherry eave line (stepped one lower
	 * over the curb and the hay row when the plot has them), deepslate tiles, a cherry ridge cap from
	 * height 6, closed gables and a lantern hanging beside the spout.
	 */
	private static void canopy(CitadelCanvas c, Plot p, int ue, int vf, int vm, int vk) {
		int ur = Math.min(2, ue - 1); // the canopy's right posts; the trough runs on past them in the open
		for (int u : new int[] {0, ur}) {
			for (int v : new int[] {vf, vk}) {
				p.set(c, u, 2, v, b(Blocks.SPRUCE_FENCE));
				p.set(c, u, 3, v, b(Blocks.SPRUCE_FENCE));
			}
		}
		boolean frontOver = vf > 0;
		boolean backOver = vk + 1 < p.d();
		for (int u = 0; u <= ur; u++) {
			if (frontOver) {
				p.set(c, u, 3, vf - 1, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
			}
			if (backOver) {
				p.set(c, u, 3, vk + 1, stairs(Blocks.CHERRY_STAIRS, Direction.NORTH));
			}
			p.set(c, u, 4, vf, stairs(frontOver ? Blocks.DEEPSLATE_TILE_STAIRS : Blocks.CHERRY_STAIRS, Direction.SOUTH));
			p.set(c, u, 4, vk, stairs(backOver ? Blocks.DEEPSLATE_TILE_STAIRS : Blocks.CHERRY_STAIRS, Direction.NORTH));
			p.set(c, u, 4, vm, b(Blocks.DEEPSLATE_TILES));
			if (p.h() >= 6) {
				p.set(c, u, 5, vm, slab(Blocks.CHERRY_SLAB));
			}
		}
		if (p.h() >= 7) {
			p.set(c, 1, 6, vm, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState()); // [finish] a copper finial
		}
		// [depth] the gable ends: a spruce beam over the water side, upside-down stairs at the corners
		p.set(c, 0, 3, vm, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST).setValue(BlockStateProperties.HALF, Half.TOP));
		p.set(c, ur, 3, vm, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST).setValue(BlockStateProperties.HALF, Half.TOP));
		p.put(c, ur, 2, vm, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ props

	/** [finish] the barrel end: a barrel at the front, a bucket-cauldron, two barrels stacked at the back. */
	private static void barrels(CitadelCanvas c, Plot p, int u, int vf, int vm, int vk) {
		p.set(c, u, 0, vf, p.hash(u, 0, vf, 2) == 0 ? BARREL_UP : b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH));
		p.set(c, u, 0, vm, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 2));
		p.set(c, u, 0, vk, BARREL_UP);
		p.set(c, u, 1, vk, BARREL_UP);
	}

	/** [finish] the curb in front (transition to the path): stone-brick slabs with moss and grass between. */
	private static void curb(CitadelCanvas c, Plot p) {
		for (int u = 0; u < p.w(); u++) {
			BlockState st = switch (p.hash(u, 0, 0, 6)) {
				case 0 -> b(Blocks.MOSS_CARPET);
				case 1 -> b(Blocks.SHORT_GRASS);
				case 2 -> slab(Blocks.MOSSY_STONE_BRICK_SLAB);
				default -> slab(Blocks.STONE_BRICK_SLAB);
			};
			p.set(c, u, 0, 0, st);
		}
	}

	/** Behind the trough: hay bales lying along the row, flowers and ferns between them. */
	private static void hay(CitadelCanvas c, Plot p, int v) {
		for (int u = 0; u < p.w(); u++) {
			BlockState st = switch (p.hash(u, 0, v, 5)) {
				case 0, 1 -> b(Blocks.HAY_BLOCK).setValue(BlockStateProperties.AXIS, Direction.Axis.X);
				case 2 -> b(Blocks.POPPY);
				case 3 -> b(Blocks.FERN);
				default -> null;
			};
			if (st != null) {
				p.set(c, u, 0, v, st);
			}
		}
	}
}
