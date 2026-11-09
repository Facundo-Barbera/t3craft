package dev.t3craft.hq.plan;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Reference applier used by {@code /t3craft debug hq-preview}: places placements instantly at
 * {@code origin + rel}, loads their block entity data, then resolves connections (panes, walls,
 * stairs corners) like a structure template does. Run on the server thread only.
 *
 * <p>The real world builder (another module) may pace placements over ticks instead; it must keep
 * the same three steps: place in list order without neighbour updates, load {@code be}, then
 * {@link Block#updateFromNeighbourShapes} over the placed cells and their neighbours.
 */
public final class PlanApplier {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

	private PlanApplier() {
	}

	public record Stats(int placed, int changed, int shaped, long millis) {
	}

	public static Stats apply(ServerLevel level, BlockPos origin, List<Placement> placements) {
		long t0 = System.nanoTime();
		int changed = 0;
		Set<BlockPos> touched = new LinkedHashSet<>();
		for (Placement p : placements) {
			BlockPos pos = origin.offset(p.rel());
			if (level.setBlock(pos, p.state(), FLAGS)) {
				changed++;
			}
			touched.add(pos);
			CompoundTag be = p.be();
			if (be != null) {
				BlockEntity e = level.getBlockEntity(pos);
				if (e != null) {
					e.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), be.copy()));
					e.setChanged();
					level.sendBlockUpdated(pos, p.state(), p.state(), Block.UPDATE_CLIENTS);
				}
			}
		}
		Set<BlockPos> shapeCells = new LinkedHashSet<>(touched);
		for (BlockPos pos : touched) {
			for (Direction d : Direction.values()) {
				shapeCells.add(pos.relative(d));
			}
		}
		int shaped = 0;
		for (BlockPos pos : shapeCells) {
			BlockState s = level.getBlockState(pos);
			BlockState u = Block.updateFromNeighbourShapes(s, level, pos);
			if (u != s) {
				level.setBlock(pos, u, FLAGS);
				shaped++;
			}
		}
		return new Stats(placements.size(), changed, shaped, (System.nanoTime() - t0) / 1_000_000);
	}
}
