package dev.t3craft.hq.citadel.catalog.lamp;

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
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Japanese stone lantern (tōrō): a dark stepped base, a slim pale post, a white quartz plate, a one-block
 * light box that glows with open air round it, and a cherry-pink hat whose corners curl up, with a candle
 * finial. Contrast is the point: dark foot → grey post → white plate → glowing neck → pink hat.
 *
 * <ul>
 * <li><b>Gradient</b>: deepslate at the foot, stone brick post, quartz and calcite plate, warm froglight,
 *     cherry hat (the only warm colour besides the light).</li>
 * <li><b>Depth</b>: the base steps in, the plate and the hat overhang the single-block light box, the hat
 *     corners are upside-down slabs so the eaves curl up.</li>
 * <li><b>Transition to the ground</b>: moss carpet and pots in the base corners.</li>
 * </ul>
 * Size: 3 × 3 × 4–6. Height 6 gets the full tōrō; lower plots drop the post, then the finial.
 */
public final class LampStreetToro implements Variant {
	@Override
	public String id() {
		return "lamp_street_toro";
	}

	@Override
	public Family family() {
		return Family.LAMP;
	}

	@Override
	public int minW() {
		return 3;
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
		return 3;
	}

	@Override
	public int maxD() {
		return 3;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int cu = 1;
		int cv = 1;
		boolean post = p.h() >= 5;
		boolean finial = p.h() >= 6;
		int y = 0;
		int[][] sides = {{cu, cv - 1, 0}, {cu, cv + 1, 1}, {cu - 1, cv, 2}, {cu + 1, cv, 3}};
		Direction[] toCentre = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};
		int[][] corners = {{0, 0}, {2, 0}, {0, 2}, {2, 2}};

		// --- base: the only dark part, a stepped square of deepslate bricks with mossy corners
		p.set(c, cu, y, cv, b(p.hash(cu, 0, cv, 2) == 0 ? Blocks.CHISELED_DEEPSLATE : Blocks.POLISHED_DEEPSLATE));
		for (int[] s : sides) {
			p.set(c, s[0], y, s[1], stairs(p.hash(s[0], 0, s[1], 3) == 0 ? Blocks.COBBLED_DEEPSLATE_STAIRS : Blocks.DEEPSLATE_BRICK_STAIRS, toCentre[s[2]]));
		}
		int pot = p.hash(0, 0, 0, 4);
		for (int i = 0; i < 4; i++) {
			BlockState s = i == pot ? b(p.hash(1, 0, 3, 2) == 0 ? Blocks.POTTED_AZALEA : Blocks.POTTED_CHERRY_SAPLING)
					: i == (pot + 2) % 4 ? slab(Blocks.MOSSY_COBBLESTONE_SLAB)
							: b(Blocks.MOSS_CARPET);
			p.set(c, corners[i][0], y, corners[i][1], s);
		}
		y++;

		// --- post: a slim pale column (stone brick wall) so the lantern floats clear of the base
		if (post) {
			p.set(c, cu, y++, cv, b(Blocks.STONE_BRICK_WALL));
		}

		// --- plate (nakadai): white quartz, upside-down stairs flare out round a calcite heart
		p.set(c, cu, y, cv, b(Blocks.CALCITE));
		for (int[] s : sides) {
			p.set(c, s[0], y, s[1], stairs(Blocks.SMOOTH_QUARTZ_STAIRS, toCentre[s[2]]).setValue(StairBlock.HALF, Half.TOP));
		}
		y++;

		// --- light box (hibukuro): one glowing block, open air on its four sides
		p.set(c, cu, y, cv, b(Blocks.OCHRE_FROGLIGHT));
		y++;

		// --- hat (kasa): cherry-pink, the one warm colour. Stairs rise to the middle, corners curl up
		for (int[] s : sides) {
			p.set(c, s[0], y, s[1], stairs(Blocks.CHERRY_STAIRS, toCentre[s[2]]));
		}
		BlockState tip = slab(Blocks.CHERRY_SLAB).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
		for (int[] k : corners) {
			p.set(c, k[0], y, k[1], tip);
		}
		p.set(c, cu, y, cv, b(Blocks.CHERRY_PLANKS));
		y++;

		// --- finial (hōju): a pale jewel, a candle on the crown; the hat peak
		if (finial) {
			p.set(c, cu, y, cv, b(Blocks.DYED_CANDLE.pick(DyeColor.WHITE)).setValue(BlockStateProperties.LIT, true));
		} else if (y < p.h()) {
			p.set(c, cu, y, cv, slab(Blocks.SMOOTH_QUARTZ_SLAB));
		}
	}
}
