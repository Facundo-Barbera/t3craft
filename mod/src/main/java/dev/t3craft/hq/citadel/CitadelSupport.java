package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.SOLID;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import dev.t3craft.hq.plan.PlanProbe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PathBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;

/**
 * fix/unsupported-blocks: the net under the citadel for blocks vanilla would drop as items on the first neighbour update.
 * After the relief and the floating pass, every block that {@link PlanProbe#survives} refuses where it stands is given
 * what it lacks, keeping the design: the same check the hq-render report prints as UNSUPPORTED, so what the report
 * shows is what this pass could not fix.
 *
 * <ul>
 * <li><b>Plants</b> (flowers, ferns, grass, petals, saplings, azaleas, bamboo, bushes) on a block that is no soil: a plain full
 *     block under them becomes moss (a ruin overgrown); azalea bushes on leaves become the matching leaves; on anything
 *     smaller (a wall top, a stair) they become their flower-pot twin when there is one, else go.</li>
 * <li><b>Lanterns</b>: hung from a block that cannot hold them they stand on what is under them (and the other way round);
 *     else a chain goes up to a ceiling within 6 blocks, or the lantern hangs one block lower from a chain under a stair or
 *     slab; on a chest they become an end rod; the last resort is the invisible light block.</li>
 * <li><b>Farmland and dirt paths</b> under a solid block become dirt, what the game does to them anyway. <b>Vines</b> and
 *     <b>wall banners</b> with nothing to hold them go.</li>
 * </ul>
 *
 * Pure and deterministic: candidates are visited bottom-up by position. Fixtures never change.
 */
public final class CitadelSupport {
	private CitadelSupport() {
	}

	private static final int ROUNDS = 8;
	private static final int CHAIN_REACH = 6;

	/** Last result (render diagnostics): blocks given a soil, swapped, hung, replaced and removed. */
	static volatile String last = "no pass yet";

	/** One line about the last pass. */
	public static String lastSummary() {
		return last;
	}

	private static Map<Block, Block> pots;

	private static synchronized Map<Block, Block> pots() {
		if (pots == null) {
			Map<Block, Block> m = new HashMap<>();
			m.put(Blocks.AZALEA, Blocks.POTTED_AZALEA);
			m.put(Blocks.FLOWERING_AZALEA, Blocks.POTTED_FLOWERING_AZALEA);
			m.put(Blocks.FERN, Blocks.POTTED_FERN);
			m.put(Blocks.CHERRY_SAPLING, Blocks.POTTED_CHERRY_SAPLING);
			m.put(Blocks.BAMBOO, Blocks.POTTED_BAMBOO);
			m.put(Blocks.DANDELION, Blocks.POTTED_DANDELION);
			m.put(Blocks.POPPY, Blocks.POTTED_POPPY);
			m.put(Blocks.BLUE_ORCHID, Blocks.POTTED_BLUE_ORCHID);
			m.put(Blocks.ALLIUM, Blocks.POTTED_ALLIUM);
			m.put(Blocks.AZURE_BLUET, Blocks.POTTED_AZURE_BLUET);
			m.put(Blocks.RED_TULIP, Blocks.POTTED_RED_TULIP);
			m.put(Blocks.ORANGE_TULIP, Blocks.POTTED_ORANGE_TULIP);
			m.put(Blocks.WHITE_TULIP, Blocks.POTTED_WHITE_TULIP);
			m.put(Blocks.PINK_TULIP, Blocks.POTTED_PINK_TULIP);
			m.put(Blocks.OXEYE_DAISY, Blocks.POTTED_OXEYE_DAISY);
			m.put(Blocks.CORNFLOWER, Blocks.POTTED_CORNFLOWER);
			m.put(Blocks.LILY_OF_THE_VALLEY, Blocks.POTTED_LILY_OF_THE_VALLEY);
			pots = m;
		}
		return pots;
	}

	private static boolean candidate(BlockState s) {
		Block b = s.getBlock();
		return b instanceof VegetationBlock || b instanceof net.minecraft.world.level.block.BambooStalkBlock
				|| b instanceof net.minecraft.world.level.block.BambooSaplingBlock || b instanceof LanternBlock || b instanceof VineBlock
				|| b instanceof FarmlandBlock || b instanceof PathBlock || b instanceof WallBannerBlock;
	}

	/** Gives every refused block what it lacks (see the class comment). */
	static void apply(CitadelCanvas c) {
		Map<Long, BlockState> cells = c.cells;
		LevelAccessor view = PlanProbe.liveView(cells);
		int[] n = new int[5]; // soil, swapped, hung, replaced, removed
		for (int round = 0; round < ROUNDS; round++) {
			List<Long> keys = new ArrayList<>();
			for (Map.Entry<Long, BlockState> e : cells.entrySet()) {
				if (candidate(e.getValue())) {
					keys.add(e.getKey());
				}
			}
			keys.sort((a, z) -> {
				int ya = BlockPos.getY(a);
				int yz = BlockPos.getY(z);
				return ya != yz ? Integer.compare(ya, yz) : Long.compare(a, z);
			});
			boolean changed = false;
			for (long k : keys) {
				BlockState s = cells.get(k);
				if (s == null || !candidate(s) || c.fixtures.contains(k)) {
					continue;
				}
				BlockPos pos = BlockPos.of(k);
				if (PlanProbe.survives(view, cells, s, pos)) {
					continue;
				}
				changed |= fix(c, view, k, pos, s, n);
			}
			if (!changed) {
				break;
			}
		}
		last = "unsupported blocks fixed: " + n[0] + " given soil, " + n[1] + " swapped (leaves, pot, dirt), " + n[2] + " hung or stood, " + n[3]
				+ " replaced (end rod, light), " + n[4] + " removed";
	}

	private static BlockState at(Map<Long, BlockState> cells, BlockPos p) {
		BlockState s = cells.get(p.asLong());
		return s == null ? Blocks.AIR.defaultBlockState() : s;
	}

	private static void set(CitadelCanvas c, long k, BlockState s) {
		BlockPos p = BlockPos.of(k);
		c.put(p.getX(), p.getY(), p.getZ(), s, c.layers.getOrDefault(k, SOLID));
	}

	private static void remove(CitadelCanvas c, long k) {
		c.cells.remove(k);
		c.layers.remove(k);
		c.data.remove(k);
	}

	private static boolean fullCube(BlockState s) {
		return !s.isAir() && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	private static boolean fix(CitadelCanvas c, LevelAccessor view, long k, BlockPos pos, BlockState s, int[] n) {
		Block blk = s.getBlock();
		if (blk instanceof LanternBlock) {
			return fixLantern(c, view, k, pos, s, n);
		}
		if (blk instanceof FarmlandBlock || blk instanceof PathBlock) {
			set(c, k, b(Blocks.DIRT));
			n[1]++;
			return true;
		}
		if (blk instanceof VineBlock || blk instanceof WallBannerBlock) {
			remove(c, k);
			n[4]++;
			return true;
		}
		return fixPlant(c, k, pos, s, n);
	}

	private static boolean fixPlant(CitadelCanvas c, long k, BlockPos pos, BlockState s, int[] n) {
		Block blk = s.getBlock();
		Map<Long, BlockState> cells = c.cells;
		if (blk instanceof DoublePlantBlock && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
			remove(c, k); // its lower half is gone or is another plant
			n[4]++;
			return true;
		}
		BlockPos belowPos = pos.below();
		long bk = belowPos.asLong();
		BlockState below = at(cells, belowPos);
		if ((blk == Blocks.AZALEA || blk == Blocks.FLOWERING_AZALEA) && below.getBlock() instanceof LeavesBlock) {
			Block leaves = blk == Blocks.AZALEA ? Blocks.AZALEA_LEAVES : Blocks.FLOWERING_AZALEA_LEAVES;
			set(c, k, b(leaves).setValue(BlockStateProperties.PERSISTENT, true));
			n[1]++;
			return true;
		}
		boolean soilable = fullCube(below) && !(below.getBlock() instanceof LeavesBlock) && !c.data.containsKey(bk) && !c.fixtures.contains(bk);
		if (soilable) {
			set(c, bk, b(Blocks.MOSS_BLOCK));
			n[0]++;
			return true;
		}
		// something rests on this plant (a pot, the leaves of a shrub): a moss block keeps it up instead of leaving a hole
		BlockState above = at(cells, pos.above());
		if (!above.isAir() && !(blk instanceof DoublePlantBlock && above.getBlock() == blk) && !c.data.containsKey(k)) {
			set(c, k, b(Blocks.MOSS_BLOCK));
			n[0]++;
			return true;
		}
		Block pot = pots().get(blk);
		if (pot != null && !below.isAir()) {
			set(c, k, b(pot));
			n[1]++;
			return true;
		}
		remove(c, k);
		if (blk instanceof DoublePlantBlock) {
			long up = pos.above().asLong();
			BlockState u = cells.get(up);
			if (u != null && u.getBlock() == blk) {
				remove(c, up);
			}
		}
		n[4]++;
		return true;
	}

	private static boolean fixLantern(CitadelCanvas c, LevelAccessor view, long k, BlockPos pos, BlockState s, int[] n) {
		Map<Long, BlockState> cells = c.cells;
		boolean hanging = s.getValue(LanternBlock.HANGING);
		boolean standOk = Block.canSupportCenter(view, pos.below(), Direction.UP);
		boolean hangOk = Block.canSupportCenter(view, pos.above(), Direction.DOWN);
		if (hanging && standOk) {
			set(c, k, s.setValue(LanternBlock.HANGING, false));
			n[2]++;
			return true;
		}
		if (!hanging && hangOk) {
			set(c, k, s.setValue(LanternBlock.HANGING, true));
			n[2]++;
			return true;
		}
		// a chain up to a ceiling within reach, through air or other chains
		for (int d = 1; d <= CHAIN_REACH; d++) {
			BlockPos q = pos.above(d);
			BlockState qs = at(cells, q);
			if (qs.isAir() || qs.getBlock() instanceof ChainBlock) {
				continue;
			}
			if (d > 1 && Block.canSupportCenter(view, q, Direction.DOWN)) {
				for (int i = 1; i < d; i++) {
					BlockPos cp = pos.above(i);
					if (at(cells, cp).isAir()) {
						set(c, cp.asLong(), b(Blocks.IRON_CHAIN));
					}
				}
				set(c, k, s.setValue(LanternBlock.HANGING, true));
				n[2]++;
				return true;
			}
			break;
		}
		// under a stair, a slab or leaves: the lantern hangs one block lower from a chain
		BlockPos lowPos = pos.below();
		BlockState low = at(cells, lowPos);
		boolean lowWater = low.getFluidState().getType() == Fluids.WATER;
		if (!at(cells, pos.above()).isAir() && (low.isAir() || lowWater && low.getBlock() == Blocks.WATER)) {
			set(c, k, b(Blocks.IRON_CHAIN));
			set(c, lowPos.asLong(), s.setValue(LanternBlock.HANGING, true).setValue(LanternBlock.WATERLOGGED, lowWater));
			n[2]++;
			return true;
		}
		// on a chest (or anything not flat): an end rod gives the same light
		if (!hanging && !at(cells, pos.below()).isAir()) {
			set(c, k, b(Blocks.END_ROD));
			n[3]++;
			return true;
		}
		set(c, k, CitadelCanvas.invisibleLight(15));
		n[3]++;
		return true;
	}
}
