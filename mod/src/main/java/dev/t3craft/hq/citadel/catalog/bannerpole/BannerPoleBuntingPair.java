package dev.t3craft.hq.citadel.catalog.bannerpole;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Concept: festival bunting. Two short posts stand at the ends of the frontage on dark footings
 * (polished or chiselled deepslate, buttressed by deepslate-brick stairs on the sides that fit), each
 * a cherry-fence shaft (a stone-brick wall at its foot on taller plots) up to a stripped-cherry collar
 * crowned with a lantern (iron or copper, by seed). A chain (iron or waxed copper, by seed) runs
 * between the collars and, on 5-wide plots with the height for it, sags a block in the middle. On
 * plots with depth a banner hangs off the front of every link and of each collar, alternating through
 * the citadel colours (pink, white, magenta, light gray), so the line reads as a string of pennants,
 * and a potted flower sits at the foot of each post. On 1-deep plots the flags are stained-glass
 * panes strung under the line instead.
 *
 * <p>Palette, dark → light, bottom → top: polished/chiselled deepslate and deepslate bricks
 * (footings) → stone-brick wall → cherry fence → stripped cherry collar → lantern; the flags and
 * banners are the colour accent.
 *
 * <pre>
 *  front (w 5, h 5)               plan (w 5, d 3)
 *   o       o    lanterns           ✿ · · · ✿     pots
 *   █▬     ▬█    collars, chain     █ ▬ ▬ ▬ █     posts, line
 *   ▓▒▬   ▬▒▓    banners, flags     ◢ · · · ◣     buttresses
 *   │ ▒ ▬ ▒ │    the sag, flags
 *   │   ▒   │
 *   █       █    footings
 * </pre>
 *
 * Size: 3–5 × 1–3 × 4+ (the posts grow up to 7 tall with the height; the sag needs w 5 and h 5).
 */
public final class BannerPoleBuntingPair implements Variant {
	private static final DyeColor[] PALETTE = {DyeColor.PINK, DyeColor.WHITE, DyeColor.MAGENTA, DyeColor.LIGHT_GRAY};

	@Override
	public String id() {
		return "banner_pole_bunting_pair";
	}

	@Override
	public Family family() {
		return Family.BANNER_POLE;
	}

	@Override
	public int minW() {
		return 3;
	}

	@Override
	public int minD() {
		return 1;
	}

	@Override
	public int minH() {
		return 4;
	}

	@Override
	public int maxW() {
		return 5;
	}

	@Override
	public int maxD() {
		return 3;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int pv = d / 2; // the line's row (d = 2: the back row, the front row takes the banners)
		int top = Math.min(p.h() - 1, 6); // lantern layer
		int line = top - 1; // collar and chain layer
		boolean sag = w == 5 && line - 2 >= 1; // the dipped link still has its flag above the ground
		boolean copper = p.hash(0, 0, 0, 2) == 0;
		BlockState chain = (copper ? b(Blocks.COPPER_CHAIN.waxed().unaffected()) : b(Blocks.IRON_CHAIN))
				.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
		BlockState lantern = p.hash(1, 0, 0, 2) == 0 ? b(Blocks.COPPER_LANTERN.waxed().unaffected()) : b(Blocks.LANTERN);
		int iMain = p.hash(2, 0, 0, PALETTE.length);
		DyeColor bannerCol = PALETTE[iMain];

		for (int u : new int[] {0, w - 1}) {
			post(c, p, u, pv, line, top, lantern);
			if (pv - 1 >= 0) {
				// a banner on the collar's front face, hanging over the shaft
				p.put(c, u, line, pv - 1, b(Blocks.WALL_BANNER.pick(bannerCol)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
						CitadelCanvas.DECOR);
			}
		}

		// --- the line and its flags
		int phase = p.hash(3, 0, 0, PALETTE.length);
		for (int u = 1; u <= w - 2; u++) {
			boolean dip = sag && u == w / 2;
			int y = dip ? line - 1 : line;
			p.put(c, u, y, pv, chain, CitadelCanvas.HANGING);
			DyeColor col = PALETTE[(phase + u) % PALETTE.length];
			if (pv - 1 >= 0) {
				// with depth: a banner hangs off every link, on the front of the line, like a row of pennants
				p.put(c, u, y, pv - 1, b(Blocks.WALL_BANNER.pick(col)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
						CitadelCanvas.DECOR);
				continue;
			}
			// depth 1: no room in front, so the flags are glass panes strung under the line
			boolean east = u + 1 <= w - 2 && !(sag && (u + 1 == w / 2 || u == w / 2));
			boolean west = u - 1 >= 1 && !(sag && (u - 1 == w / 2 || u == w / 2));
			p.put(c, u, y - 1, pv, b(Blocks.STAINED_GLASS_PANE.pick(col))
					.setValue(BlockStateProperties.EAST, east).setValue(BlockStateProperties.WEST, west), CitadelCanvas.HANGING);
		}
	}

	/** A post at {@code (u, pv)}: footing with buttresses, shaft, collar at {@code line}, lantern at {@code top}. */
	private static void post(CitadelCanvas c, Plot p, int u, int pv, int line, int top, BlockState lantern) {
		int w = p.w();
		int d = p.d();
		p.set(c, u, 0, pv, p.hash(u, 0, pv, 3) == 0 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
		// [depth] buttresses climbing to the footing, and a pot at the foot towards the front
		if (pv - 1 >= 0) {
			p.set(c, u, 0, pv - 1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH));
		}
		if (pv + 1 < d) {
			p.set(c, u, 0, pv + 1, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH));
		}
		int inner = u == 0 ? 1 : w - 2;
		if (pv - 1 >= 0) {
			p.set(c, inner, 0, pv - 1, p.hash(inner, 0, pv, 2) == 0 ? b(Blocks.POTTED_PINK_TULIP) : b(Blocks.POTTED_FLOWERING_AZALEA));
		}
		for (int y = 1; y < line; y++) {
			if (y == 1 && line >= 4) {
				p.set(c, u, y, pv, b(Blocks.STONE_BRICK_WALL).setValue(BlockStateProperties.UP, true)
						.setValue(BlockStateProperties.NORTH_WALL, WallSide.NONE).setValue(BlockStateProperties.SOUTH_WALL, WallSide.NONE)
						.setValue(BlockStateProperties.EAST_WALL, WallSide.NONE).setValue(BlockStateProperties.WEST_WALL, WallSide.NONE));
			} else {
				p.set(c, u, y, pv, b(Blocks.CHERRY_FENCE));
			}
		}
		// the collar is a fence bracket with its arm reaching towards the line, not a cube
		p.set(c, u, line, pv, b(Blocks.CHERRY_FENCE)
				.setValue(u == 0 ? BlockStateProperties.EAST : BlockStateProperties.WEST, true));
		p.set(c, u, top, pv, lantern);
	}
}
