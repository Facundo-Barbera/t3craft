package dev.t3craft.hq.citadel.catalog.lamp;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Fire brazier on a stone tripod: three slim pale legs on dark feet (two at the front, one at the back)
 * hold a copper bowl, a plus of upside-down stairs with open corners, and a campfire (a soul campfire on
 * some seeds) burns in it. Two copper rods rise from the front corners as horns. The legs, the air between
 * them and the bowl overhanging the plus make the silhouette; the copper is the one warm colour next to the
 * grey stone, so the bowl reads at once.
 *
 * <ul>
 * <li><b>Gradient dark → light, bottom → top</b>: cobbled deepslate feet, stone-brick and andesite legs,
 *     bright copper bowl, fire on top.</li>
 * <li><b>Depth</b>: the legs stand apart with open air between them; the bowl overhangs; the corners of the
 *     bowl are open, so the flame is seen from every side.</li>
 * <li><b>Safe fire</b>: a campfire only burns the block it is in (it never spreads) and nothing flammable is
 *     next to it.</li>
 * </ul>
 * Size: 3 × 3 × 4–6. Legs are two to four blocks.
 */
public final class LampStreetBrazierTripod implements Variant {
	@Override
	public String id() {
		return "lamp_street_brazier_tripod";
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
		int dish = Math.min(p.h() - 2, 4); // y of the dish; the fire sits on it
		int fire = dish + 1;
		int[][] legs = {{0, 0}, {2, 0}, {1, 2}};
		boolean soul = p.hash(0, 0, 0, 3) == 0;

		// --- legs: dark foot, then pale slim walls; the gaps between them are the point of the silhouette
		for (int[] l : legs) {
			int u = l[0];
			int v = l[1];
			p.set(c, u, 0, v, b(p.hash(u, 0, v, 2) == 0 ? Blocks.COBBLED_DEEPSLATE_WALL : Blocks.DEEPSLATE_BRICK_WALL));
			for (int y = 1; y < dish; y++) {
				p.set(c, u, y, v, b(y == 1 ? Blocks.STONE_BRICK_WALL : Blocks.ANDESITE_WALL));
			}
		}
		// ash patch under the bowl: the only thing on the ground between the legs
		p.set(c, 1, 0, 1, slab(Blocks.BLACKSTONE_SLAB));

		// --- bowl: a plus of upside-down verdigris stairs (oxidized copper) on a bright copper heart, the corners left open
		// so light and sky show between the horns (the legs' corners carry a slab only where a leg arrives)
		BlockState cu0 = Blocks.CUT_COPPER_STAIRS.waxed().oxidized().defaultBlockState().setValue(StairBlock.HALF, Half.TOP);
		p.set(c, 1, dish, 1, b(Blocks.CUT_COPPER.waxed().unaffected()));
		p.set(c, 1, dish, 0, cu0.setValue(StairBlock.FACING, Direction.NORTH));
		p.set(c, 1, dish, 2, cu0.setValue(StairBlock.FACING, Direction.SOUTH));
		p.set(c, 0, dish, 1, cu0.setValue(StairBlock.FACING, Direction.WEST));
		p.set(c, 2, dish, 1, cu0.setValue(StairBlock.FACING, Direction.EAST));
		BlockState copper = slab(Blocks.CUT_COPPER_SLAB.waxed().unaffected()).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
		p.set(c, 0, dish, 0, copper);
		p.set(c, 2, dish, 0, copper);
		// tall horns on the two front legs only: an asymmetric crown, not a cage
		p.set(c, 0, fire, 0, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		p.set(c, 2, fire, 0, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());

		// --- fire, in the open
		p.set(c, 1, fire, 1, soul ? b(Blocks.SOUL_CAMPFIRE) : b(Blocks.CAMPFIRE));
	}
}
