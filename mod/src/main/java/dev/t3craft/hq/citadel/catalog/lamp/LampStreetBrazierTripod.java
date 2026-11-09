package dev.t3craft.hq.citadel.catalog.lamp;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

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
 * Fire brazier on a stone tripod: three thin dark legs (two at the front, one at the back) hold a wide
 * dish of upside-down stairs with copper corners, a coal bed and a campfire (a soul campfire on some
 * seeds) burning in the open. The dish is the widest part, so it overhangs the legs like a bowl on a stand.
 *
 * <ul>
 * <li><b>Gradient dark → light, bottom → top</b>: cobbled deepslate feet, polished deepslate legs, blackstone
 *     brick dish, waxed copper corners, and the bright fire on top.</li>
 * <li><b>Depth</b>: the legs end under the dish, which overhangs by one block all round; the feet splay
 *     out on stairs; nothing stands around the flame, so it is seen from every side.</li>
 * <li><b>Safe fire</b>: a campfire only burns the block it is in (it never spreads) and nothing flammable is
 *     next to it.</li>
 * </ul>
 * Size: 3 × 3 × 4–6. Legs are two or three blocks; taller plots keep the extra height empty.
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
		int dish = Math.min(p.h() - 2, 3); // y of the dish; the fire sits on it
		int fire = dish + 1;
		int[][] legs = {{0, 0}, {2, 0}, {1, 2}};
		boolean soul = p.hash(0, 0, 0, 3) == 0;

		// --- legs: dark foot, polished shaft; they end under the dish
		for (int[] l : legs) {
			int u = l[0];
			int v = l[1];
			p.set(c, u, 0, v, b(p.hash(u, 0, v, 2) == 0 ? Blocks.COBBLED_DEEPSLATE_WALL : Blocks.DEEPSLATE_BRICK_WALL));
			for (int y = 1; y < dish; y++) {
				p.set(c, u, y, v, b(Blocks.POLISHED_DEEPSLATE_WALL));
			}
		}
		// feet splay outward: a stair at the ground beside each front foot, climbing towards it
		p.set(c, 0, 0, 1, stairs(Blocks.COBBLED_DEEPSLATE_STAIRS, Direction.NORTH));
		p.set(c, 2, 0, 1, stairs(Blocks.COBBLED_DEEPSLATE_STAIRS, Direction.NORTH));
		p.set(c, 1, 0, 1, slab(Blocks.BLACKSTONE_SLAB)); // ash patch under the dish

		// --- dish: a full ring of upside-down stairs facing out, copper slabs on the corners, coal bed
		p.set(c, 1, dish, 1, b(Blocks.COAL_BLOCK));
		BlockState half = stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.NORTH).setValue(StairBlock.HALF, Half.TOP);
		p.set(c, 1, dish, 0, half.setValue(StairBlock.FACING, Direction.NORTH));
		p.set(c, 1, dish, 2, half.setValue(StairBlock.FACING, Direction.SOUTH));
		p.set(c, 0, dish, 1, half.setValue(StairBlock.FACING, Direction.WEST));
		p.set(c, 2, dish, 1, half.setValue(StairBlock.FACING, Direction.EAST));
		BlockState copper = slab(Blocks.CUT_COPPER_SLAB.waxed().unaffected()).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
		for (int[] k : new int[][] {{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
			p.set(c, k[0], dish, k[1], copper);
			// rim horns: a copper spike rising from each corner (a second slab would read as a cube),
			// the middle of every side stays open for the fire
			p.set(c, k[0], fire, k[1], Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		}

		// --- fire, in the open
		p.set(c, 1, fire, 1, soul ? b(Blocks.SOUL_CAMPFIRE) : b(Blocks.CAMPFIRE));
	}
}
