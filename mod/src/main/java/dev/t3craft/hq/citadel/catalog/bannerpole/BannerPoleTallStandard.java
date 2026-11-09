package dev.t3craft.hq.citadel.catalog.bannerpole;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Concept: a ceremonial standard. A stepped stone base (a chiselled or polished deepslate block in a
 * ring of deepslate-brick stairs climbing to it, tile slabs and a potted flower in the corners), then
 * a polished deepslate wall and, on taller plots, a stone-brick wall as the plinth; a tall cherry
 * fence mast with a waxed-copper collar halfway; at the top a stripped-cherry crosshead, with
 * stripped-cherry arms on wide plots, from which plain wall banners hang to the front and the back
 * (the main colour in the middle, the second colour on the arms); a gold knob and a copper rod crown
 * it. On a 1 × 1 plot there is nowhere to hang a banner, so a standing banner flies on the mast top
 * instead; on a 1-deep plot the banners hang off the crosshead's sides.
 *
 * <p>Palette, dark → light, bottom → top: deepslate bricks, chiselled and polished deepslate (base) →
 * polished deepslate wall → stone-brick wall → cherry mast → stripped cherry crosshead → gold and
 * copper. Banner colours come from the citadel palette (pink, white, light gray, magenta), two per
 * standard, picked by {@code p.hash}.
 *
 * <pre>
 *   front (3 × 3, h 8)         1 × 1
 *        ╿    copper rod          ●  gold
 *        ●    gold knob           ▓  standing banner
 *     ▬▬█▬▬   crosshead, arms     │
 *     ▓ ▓ ▓   wall banners        ◘  copper collar
 *     ▓ │ ▓                       │
 *       ◘     copper collar       ▌  wall
 *       ▌     stone-brick wall    █  base
 *       ▌     deepslate wall
 *    ◢ █ ◣    stepped base
 * </pre>
 *
 * Size: 1–3 × 1–3 × 5+ (the mast grows with the height; the stone-brick plinth appears from height 7).
 */
public final class BannerPoleTallStandard implements Variant {
	private static final DyeColor[] PALETTE = {DyeColor.PINK, DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.MAGENTA};

	@Override
	public String id() {
		return "banner_pole_tall_standard";
	}

	@Override
	public Family family() {
		return Family.BANNER_POLE;
	}

	@Override
	public int minW() {
		return 1;
	}

	@Override
	public int minD() {
		return 1;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 3;
	}

	@Override
	public int maxD() {
		return 3;
	}

	private static BlockState wallBanner(DyeColor col, Direction facing) {
		return b(Blocks.WALL_BANNER.pick(col)).setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int cu = w / 2;
		int cv = d / 2; // d = 2: the mast at the back, the banner in front
		int top = p.h() - 1;
		int iMain = p.hash(0, 0, 0, PALETTE.length);
		DyeColor main = PALETTE[iMain];
		DyeColor second = PALETTE[(iMain + 1 + p.hash(1, 0, 0, PALETTE.length - 1)) % PALETTE.length];
		boolean single = w == 1 && d == 1;

		base(c, p, cu, cv);

		// --- plinth walls: polished deepslate, then stone brick on tall plots
		int y = 1;
		p.set(c, cu, y++, cv, b(Blocks.POLISHED_DEEPSLATE_WALL).setValue(BlockStateProperties.UP, true));
		if (p.h() >= 7) {
			p.set(c, cu, y++, cv, b(Blocks.STONE_BRICK_WALL).setValue(BlockStateProperties.UP, true)
					.setValue(BlockStateProperties.NORTH_WALL, WallSide.NONE).setValue(BlockStateProperties.SOUTH_WALL, WallSide.NONE)
					.setValue(BlockStateProperties.EAST_WALL, WallSide.NONE).setValue(BlockStateProperties.WEST_WALL, WallSide.NONE));
		}

		// --- head: 1 × 1 flies a standing banner at top - 1 (it draws up into the top layer)
		int head = single ? top - 1 : top - 2; // the crosshead row (or the standing banner)
		int mastTop = single ? head - 2 : head - 1; // 1 × 1: a gold knob sits under the banner
		int collar = (y + mastTop) / 2;
		boolean hasCollar = mastTop - y >= 2;
		for (int yy = y; yy <= mastTop; yy++) {
			p.set(c, cu, yy, cv, hasCollar && yy == collar ? b(Blocks.CUT_COPPER.waxed().unaffected()) : b(Blocks.CHERRY_FENCE));
		}

		if (single) {
			p.set(c, cu, head - 1, cv, b(Blocks.GOLD_BLOCK));
			// ROTATION_16: 0 faces south, 8 faces north (local front)
			p.put(c, cu, head, cv, b(Blocks.BANNER.pick(main)).setValue(BlockStateProperties.ROTATION_16, 8), CitadelCanvas.DECOR);
			return;
		}

		// --- crosshead, arms, banners
		p.set(c, cu, head, cv, b(Blocks.STRIPPED_CHERRY_WOOD));
		p.set(c, cu, head + 1, cv, b(Blocks.GOLD_BLOCK));
		p.set(c, cu, top, cv, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		boolean arms = w >= 3 && d >= 2; // one deep: the banners hang off the crosshead's sides instead
		if (arms) {
			BlockState arm = b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X);
			p.set(c, cu - 1, head, cv, arm);
			p.set(c, cu + 1, head, cv, arm);
		}
		if (d >= 2) {
			// [finish] banners hang against the crosshead's front (and back, when there is room)
			for (int du = arms ? -1 : 0; du <= (arms ? 1 : 0); du++) {
				DyeColor col = du == 0 ? main : second;
				p.put(c, cu + du, head, cv - 1, wallBanner(col, Direction.NORTH), CitadelCanvas.DECOR);
				if (cv + 1 < d) {
					p.put(c, cu + du, head, cv + 1, wallBanner(col, Direction.SOUTH), CitadelCanvas.DECOR);
				}
			}
		} else {
			// one deep: the banners hang off the sides of the crosshead (left main, right second)
			if (cu - 1 >= 0) {
				p.put(c, cu - 1, head, cv, wallBanner(main, Direction.WEST), CitadelCanvas.DECOR);
			}
			if (cu + 1 < w) {
				p.put(c, cu + 1, head, cv, wallBanner(second, Direction.EAST), CitadelCanvas.DECOR);
			}
		}
	}

	/** The stepped base: a heavy block, stairs climbing to it on every side that fits, slabs and a pot in the corners. */
	private static void base(CitadelCanvas c, Plot p, int cu, int cv) {
		p.set(c, cu, 0, cv, p.hash(cu, 0, cv, 3) == 0 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE));
		int pot = p.hash(2, 0, 2, 4);
		int i = 0;
		for (int dv = -1; dv <= 1; dv++) {
			for (int du = -1; du <= 1; du++) {
				int u = cu + du;
				int v = cv + dv;
				if ((du == 0 && dv == 0) || !p.has(u, 0, v)) {
					continue;
				}
				if (du == 0 || dv == 0) {
					Direction toMast = du < 0 ? Direction.EAST : du > 0 ? Direction.WEST : dv < 0 ? Direction.SOUTH : Direction.NORTH;
					p.set(c, u, 0, v, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, toMast));
				} else {
					p.set(c, u, 0, v, i == pot ? b(Blocks.POTTED_PINK_TULIP)
							: p.hash(u, 0, v, 3) == 0 ? slab(Blocks.POLISHED_DEEPSLATE_SLAB) : slab(Blocks.DEEPSLATE_TILE_SLAB));
					i++;
				}
			}
		}
	}
}
