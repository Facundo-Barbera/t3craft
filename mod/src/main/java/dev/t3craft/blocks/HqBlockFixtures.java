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
 * chair or the aisle), desk bulbs → status lamps, board bulbs → board lamps, the hub lantern's cap
 * → the beacon (a status lamp bound {@code beacon}), the plan's large-screen cells → monitors. Monitors
 * get the connection flags {@link MonitorWall} gives them over the plan's monitor cells (the same rule
 * the world applies, so a built screen is already merged). Each gets its binding as
 * block entity data ({@code {"binding": "thread:<id>"}}), which the builders and the preview load with
 * {@code loadCustomOnly}. Positions, anchors and workstations are untouched, so NPCs, workstations
 * and HQ diffs see the same plan shape. Installed by {@link T3Blocks#init} only when the module is on.
 */
public final class HqBlockFixtures implements HqFixtures {
	/** The states to place (the module's blocks; tests pass vanilla stand-ins). */
	public interface States {
		BlockState monitor(Direction facing);

		/** A monitor that is one cell of a screen ({@code sides}: neighbours in the same screen). Stand-ins may ignore it. */
		default BlockState monitor(Direction facing, ScreenExtent.Sides sides) {
			return monitor(facing);
		}

		BlockState lamp();

		BlockState boardLamp();
	}

	private final States states;

	public HqBlockFixtures(States states) {
		this.states = states;
	}

	/** Bump when the substitution changes (cells, blocks or binding format): demo worlds then update. */
	public static final String ID = "blocks-3";

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
		Map<BlockPos, ScreenExtent> screens = screens(fixtures);
		return plan.replacing(p -> {
			HqBindings.Fixture f = fixtures.get(p.rel());
			if (f == null) {
				return p;
			}
			BlockState s = switch (f.role()) {
				case MONITOR, SCREEN -> {
					ScreenExtent e = screens.get(f.rel());
					yield e == null ? states.monitor(f.facing()) : states.monitor(f.facing(), e.sides(f.rel()));
				}
				case LAMP, BEACON -> states.lamp();
				case BOARD_LAMP -> states.boardLamp();
			};
			return new Placement(p.rel(), s, data(f.binding()));
		});
	}

	/** The screen of every monitor fixture, by the world's rule ({@link MonitorWall}) over the plan's monitor cells. */
	static Map<BlockPos, ScreenExtent> screens(Map<BlockPos, HqBindings.Fixture> fixtures) {
		MonitorWall.Lookup lookup = p -> {
			HqBindings.Fixture f = fixtures.get(p);
			return f != null && (f.role() == HqBindings.Role.MONITOR || f.role() == HqBindings.Role.SCREEN)
					? new MonitorWall.Cell(f.facing(), f.binding().wire()) : null;
		};
		java.util.List<BlockPos> seeds = new java.util.ArrayList<>();
		for (HqBindings.Fixture f : fixtures.values()) {
			if (f.role() == HqBindings.Role.MONITOR || f.role() == HqBindings.Role.SCREEN) {
				seeds.add(f.rel());
			}
		}
		seeds.sort(java.util.Comparator.comparingLong(BlockPos::asLong)); // deterministic walk
		return MonitorWall.resolve(lookup, seeds);
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
