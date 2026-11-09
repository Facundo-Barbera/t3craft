package dev.t3craft.hq.citadel.catalog.plazacenter;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Plaza centre: a disc of glazed terracotta laid as an eight-pointed rosette (white cardinal rays,
 * golden diagonal rays, pink field), ringed by a black/white dentil band and a deepslate ramp, with a
 * stepped plinth and a quartz obelisk with a golden pyramidion in the middle. The square corners
 * left over by the disc are raised flower beds with a lantern bollard.
 *
 * <pre>
 *  top view (w = d = 13)                section through the axis
 *    ▓▓▓ . . . . . . ▓▓▓                  y11  ╿ lightning rod
 *    ▓▓  ring: ramp, dentils, rays            y10  ▲ gold
 *     .  ✦ rosette of glazed terracotta   y7-9 │ quartz needle (chiselled bands)
 *     .     ▣ obelisk plinth              y5-6 ▟▙ cornice, step
 *    ▓▓▓ . . . . . . ▓▓▓                  y1-4 ▟█▙ plinth tiers   y0 pavement
 * </pre>
 *
 * Size: 13–15 × 13–15 × 10+ (the needle grows with the height up to 13).
 */
public final class PlazaCenterGlazedRosette implements Variant {
	@Override
	public String id() {
		return "plaza_center_glazed_rosette";
	}

	@Override
	public Family family() {
		return Family.PLAZA_CENTER;
	}

	@Override
	public int minW() {
		return 15;
	}

	@Override
	public int minD() {
		return 15;
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
		return 15;
	}

	private static BlockState gl(DyeColor color, Direction facing) {
		return Blocks.GLAZED_TERRACOTTA.pick(color).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	private static BlockState top(BlockState s) {
		return s.setValue(StairBlock.HALF, Half.TOP);
	}

	private static BlockState slabTop(net.minecraft.world.level.block.Block b) {
		return b.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	/** Outward direction of a cell relative to the centre (dominant axis). */
	private static Direction out(int dx, int dz) {
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int cu = w / 2;
		int cv = d / 2;
		double radius = Math.min(Math.min(cu, w - 1 - cu), Math.min(cv, d - 1 - cv)) + 0.5;
		int top = Math.min(p.h() - 1, 13);

		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				int dx = u - cu;
				int dz = v - cv;
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > radius) {
					corner(c, p, u, v, r - radius, w, d);
				} else {
					disc(c, p, u, v, dx, dz, r, radius);
				}
			}
		}
		obelisk(c, p, cu, cv, top);
		bollards(c, p, w, d, top);
	}

	// ------------------------------------------------------------------ the rosette

	private void disc(CitadelCanvas c, Plot p, int u, int v, int dx, int dz, double r, double radius) {
		double e = radius - r;
		int cheb = Math.max(Math.abs(dx), Math.abs(dz));
		Direction f = out(dx, dz);
		double ang = Math.atan2(dz, dx);
		if (e < 1.0) {
			// [depth] ramp: deepslate tile stairs low side out, so the disc is a raised dais, never a cut edge (facing inwards: the tall half towards the disc, climbed walking in)
			p.set(c, u, 0, v, p.hash(u, 0, v, 4) == 0 ? stairs(Blocks.MOSSY_STONE_BRICK_STAIRS, f.getOpposite()) : stairs(Blocks.STONE_BRICK_STAIRS, f.getOpposite()));
			return;
		}
		if (e < 2.0) {
			// dentil band: black and white wedges
			int k = (int) Math.floor((ang + Math.PI) / (2 * Math.PI) * 32);
			p.set(c, u, 0, v, (k & 1) == 0 ? gl(DyeColor.BLACK, f) : gl(DyeColor.WHITE, f));
			return;
		}
		if (cheb <= 1) {
			// apron under the obelisk: dark stone with a sprinkle of gilded blackstone
			int h = p.hash(u, 0, v, 7);
			p.set(c, u, 0, v, h == 0 ? b(Blocks.GILDED_BLACKSTONE) : h < 3 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_TILES));
			return;
		}
		// field with eight tapering rays
		double sector = Math.PI / 4;
		int ray = (int) Math.round(ang / sector);
		double da = Math.abs(ang - ray * sector);
		double perp = r * Math.sin(da);
		double rmin = 1.6;
		double rmax = radius - 2.0;
		double half = 1.5 - 1.0 * Math.max(0, Math.min(1, (r - rmin) / (rmax - rmin)));
		boolean cardinal = Math.floorMod(ray, 2) == 0;
		if (perp < half) {
			p.set(c, u, 0, v, cardinal ? gl(DyeColor.WHITE, f) : gl(DyeColor.YELLOW, f));
		} else {
			p.set(c, u, 0, v, gl(DyeColor.PINK, f));
		}
	}

	// ------------------------------------------------------------------ corners: paving + flower beds

	private void corner(CitadelCanvas c, Plot p, int u, int v, double beyond, int w, int d) {
		boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
		if (beyond <= 1.0) {
			int h = p.hash(u, 0, v, 4);
			p.set(c, u, 0, v, h == 0 ? slab(Blocks.MOSSY_STONE_BRICK_SLAB) : h == 1 ? slab(Blocks.POLISHED_ANDESITE_SLAB) : slab(Blocks.STONE_BRICK_SLAB));
			// (no full blocks sprinkled in this paving: it is the walk round the disc, slabs only)
			return;
		}
		// raised bed: deepslate retaining blocks on the rim, moss inside, bushes and flowers above
		if (edge || beyond <= 1.3) {
			p.set(c, u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS));
			if (!edge && p.hash(u, 1, v, 3) == 0) {
				p.set(c, u, 1, v, slab(Blocks.DEEPSLATE_BRICK_SLAB));
			}
			return;
		}
		p.set(c, u, 0, v, b(Blocks.MOSS_BLOCK));
		switch (p.hash(u, 1, v, 6)) {
			case 0 -> p.set(c, u, 1, v, CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			case 1 -> p.set(c, u, 1, v, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
			case 2 -> {
				p.set(c, u, 1, v, b(Blocks.PEONY));
				p.set(c, u, 2, v, Blocks.PEONY.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
			}
			case 3 -> p.set(c, u, 1, v, b(Blocks.PINK_PETALS));
			default -> p.set(c, u, 1, v, CitadelCanvas.leaves(Blocks.AZALEA_LEAVES));
		}
	}

	/** A lantern bollard on each plot corner. */
	private void bollards(CitadelCanvas c, Plot p, int w, int d, int top) {
		int[][] spots = {{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}};
		for (int[] s : spots) {
			p.set(c, s[0], 1, s[1], b(Blocks.POLISHED_DEEPSLATE_WALL));
			p.set(c, s[0], 2, s[1], b(Blocks.POLISHED_DEEPSLATE_WALL));
			p.set(c, s[0], 3, s[1], b(Blocks.LANTERN));
		}
	}

	// ------------------------------------------------------------------ obelisk

	private void obelisk(CitadelCanvas c, Plot p, int cu, int cv, int top) {
		// y1-2: 3×3 pedestal, pale at the top: calcite and chiselled quartz over a dark deepslate course at the foot
		for (int y = 1; y <= 2; y++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					int h = p.hash(cu + dx, y, cv + dz, 5);
					boolean face = (dx == 0) != (dz == 0);
					p.set(c, cu + dx, y, cv + dz, y == 1 ? (h == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : h == 1 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.DEEPSLATE_BRICKS))
							: face ? b(Blocks.CHISELED_QUARTZ_BLOCK) : h == 0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.CALCITE));
				}
			}
		}
		// y3: cornice, inverted stairs overhanging the pedestal on every side (5×5 with the corners cut)
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				int cheb = Math.max(Math.abs(dx), Math.abs(dz));
				boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
				if (corner) {
					continue;
				}
				if (cheb == 2) {
					p.set(c, cu + dx, 3, cv + dz, Math.abs(dx) == 2 && Math.abs(dz) == 1 || Math.abs(dz) == 2 && Math.abs(dx) == 1
							? slabTop(Blocks.DEEPSLATE_TILE_SLAB) : top(stairs(Blocks.DEEPSLATE_TILE_STAIRS, out(dx, dz))));
				} else {
					p.set(c, cu + dx, 3, cv + dz, b(Blocks.POLISHED_DEEPSLATE));
				}
			}
		}
		// y4: quartz cross step climbing to the needle
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				boolean centre = dx == 0 && dz == 0;
				boolean corner = dx != 0 && dz != 0;
				if (corner) {
					p.set(c, cu + dx, 4, cv + dz, slab(Blocks.QUARTZ_SLAB));
				} else {
					p.set(c, cu + dx, 4, cv + dz, centre ? b(Blocks.SMOOTH_QUARTZ) : stairs(Blocks.QUARTZ_STAIRS, out(dx, dz)));
				}
			}
		}
		// needle: smooth quartz with chiselled bands and a gold pyramidion
		for (int y = 5; y <= top - 3; y++) {
			p.set(c, cu, y, cv, (y - 5) % 3 == 2 ? b(Blocks.CHISELED_QUARTZ_BLOCK) : (y == 5 ? b(Blocks.QUARTZ_PILLAR) : b(Blocks.SMOOTH_QUARTZ)));
		}
		p.set(c, cu, top - 2, cv, b(Blocks.CHISELED_QUARTZ_BLOCK));
		p.set(c, cu, top - 1, cv, b(Blocks.GOLD_BLOCK));
		p.set(c, cu, top, cv, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		// buttress stairs hugging the needle foot
		for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			p.set(c, cu + dir.getStepX(), 5, cv + dir.getStepZ(), stairs(Blocks.QUARTZ_STAIRS, dir));
		}
	}
}
