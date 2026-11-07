package dev.t3craft.blocks;

import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.Placement;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The HQ adapter (an {@link HqFixtures}): swaps the vanilla fixtures of a generated plan for the
 * module's blocks, cell for cell, as listed by {@link HqBindings}: computers → monitors (facing the
 * chair or the aisle), desk bulbs → status lamps, board bulbs → board lamps. Each gets its binding as
 * block entity data ({@code {"binding": "thread:<id>"}}), which the builders and the preview load with
 * {@code loadCustomOnly}. Positions, anchors and workstations are untouched, so NPCs, workstations
 * and HQ diffs see the same plan shape. Installed by {@link T3Blocks#init} only when the module is on.
 */
public final class HqBlockFixtures implements HqFixtures {
	/** The states to place (the module's blocks; tests pass vanilla stand-ins). */
	public interface States {
		BlockState monitor(Direction facing);

		BlockState lamp();

		BlockState boardLamp();
	}

	private final States states;

	public HqBlockFixtures(States states) {
		this.states = states;
	}

	@Override
	public BuildPlan apply(BuildPlan plan) {
		Map<BlockPos, HqBindings.Fixture> fixtures = new HashMap<>();
		for (HqBindings.Fixture f : HqBindings.of(plan)) {
			fixtures.put(f.rel(), f);
		}
		return plan.replacing(p -> {
			HqBindings.Fixture f = fixtures.get(p.rel());
			if (f == null) {
				return p;
			}
			BlockState s = switch (f.role()) {
				case MONITOR -> states.monitor(f.facing());
				case LAMP -> states.lamp();
				case BOARD_LAMP -> states.boardLamp();
			};
			return new Placement(p.rel(), s, data(f.binding()));
		});
	}

	/** Block entity data for a binding (always a fresh tag; an empty one for unbound blocks). */
	static CompoundTag data(Binding b) {
		CompoundTag t = new CompoundTag();
		if (!b.isNone()) {
			t.putString(BoundBlockEntity.KEY, b.wire());
		}
		return t;
	}
}
