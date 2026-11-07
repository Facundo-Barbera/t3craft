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
import org.jspecify.annotations.Nullable;

/**
 * The HQ adapter (an {@link HqFixtures}): swaps the vanilla fixtures of a generated plan for the
 * module's blocks, cell for cell, as listed by {@link HqBindings}: computers → monitors (facing the
 * chair or the aisle), desk bulbs → status lamps, board bulbs → board lamps, the hub lantern's cap
 * → the beacon (a status lamp bound {@code beacon}), and the plan's {@code thread_wall} anchor cell
 * (if any) → the thread wall anchor with its size as data. Each gets its binding as
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

		/** The thread wall anchor facing {@code facing}; null = leave the plan's block (stand-ins). */
		default @Nullable BlockState threadBoard(Direction facing) {
			return null;
		}
	}

	private final States states;

	public HqBlockFixtures(States states) {
		this.states = states;
	}

	/** Bump when the substitution changes (cells, blocks or binding format): demo worlds then update. */
	public static final String ID = "blocks-2";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public BuildPlan apply(BuildPlan plan) {
		Map<BlockPos, HqBindings.Fixture> fixtures = new HashMap<>();
		for (HqBindings.Fixture f : HqBindings.of(plan)) {
			fixtures.put(f.rel(), f);
		}
		HqBindings.ThreadWall wall = HqBindings.threadWall(plan);
		BlockState wallState = wall == null ? null : states.threadBoard(wall.facing());
		return plan.replacing(p -> {
			if (wallState != null && p.rel().equals(wall.rel())) {
				return new Placement(p.rel(), wallState, wallData(wall));
			}
			HqBindings.Fixture f = fixtures.get(p.rel());
			if (f == null) {
				return p;
			}
			BlockState s = switch (f.role()) {
				case MONITOR -> states.monitor(f.facing());
				case LAMP, BEACON -> states.lamp();
				case BOARD_LAMP -> states.boardLamp();
			};
			return new Placement(p.rel(), s, data(f.binding()));
		});
	}

	/** Block entity data of the thread wall anchor: its size when the plan gives one (else the config's). */
	static CompoundTag wallData(HqBindings.ThreadWall w) {
		CompoundTag t = new CompoundTag();
		if (w.width() > 0 && w.height() > 0) {
			t.putInt(ThreadBoardBlockEntity.WIDTH, w.width());
			t.putInt(ThreadBoardBlockEntity.HEIGHT, w.height());
		}
		return t;
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
