package dev.t3craft.blocks;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.Placement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The HQ adapter (an {@link HqFixtures}): swaps the vanilla fixtures of a generated plan for the
 * module's blocks, cell for cell, as listed by {@link HqBindings}: each desk's 3×2 screen → six
 * monitors facing the agent that merge into one screen (no desk lamps), board bulbs → board lamps, the hub lantern's cap
 * → the beacon (a status lamp bound {@code beacon}), the archive terminal's lectern → the archive
 * terminal, the plan's large-screen cells → monitors, the plan's {@code thread_wall} anchor cell (if
 * any) → the thread wall anchor with its size as data, and wall signs with text (module signs, the
 * board's header) → room plaques ({@link #plaque}). Monitors get the connection flags
 * {@link MonitorWall} gives them over the plan's monitor cells (the same rule the world applies, so a
 * built screen is already merged). Each gets its binding as
 * block entity data ({@code {"binding": "thread:<id>"}}), which the builders and the preview load with
 * {@code loadCustomOnly}. Positions, anchors and workstations are untouched, so NPCs, workstations
 * and HQ diffs see the same plan shape. Installed by {@link T3Blocks#init} only when the module is on.
 */
public final class HqBlockFixtures implements HqFixtures {
	/** The states to place (the module's blocks; tests pass vanilla stand-ins). */
	public interface States {
		BlockState monitor(Direction facing);

		/** A monitor that is one cell of a screen ({@code sides}: neighbours in the same screen). Stand-ins may ignore it. */
		default BlockState monitor(Direction facing, ScreenRect.Sides sides) {
			return monitor(facing);
		}

		BlockState lamp();

		BlockState boardLamp();

		/** The thread wall anchor facing {@code facing}; null = leave the plan's block (stand-ins). */
		default @Nullable BlockState threadBoard(Direction facing) {
			return null;
		}

		/** The archive terminal, screen facing {@code facing} (default: the vanilla lectern stand-in). */
		default BlockState archiveTerminal(Direction facing) {
			return net.minecraft.world.level.block.Blocks.LECTERN.defaultBlockState()
					.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
		}

		/** The console terminal (kit/console-terminal), front towards {@code facing} (default: the vanilla lectern stand-in). */
		default BlockState consoleTerminal(Direction facing) {
			return net.minecraft.world.level.block.Blocks.LECTERN.defaultBlockState()
					.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
		}

		/** Room plaque with its front towards {@code facing} (replaces the HQ's wall signs); null keeps the signs. */
		default @Nullable BlockState plaque(Direction facing) {
			return null;
		}

		/** Plaque block entity data ({@link PlaqueText#data}). */
		default CompoundTag plaqueData(int color, String... lines) {
			return PlaqueText.data(color, lines);
		}
	}

	private final States states;

	public HqBlockFixtures(States states) {
		this.states = states;
	}

	/** Bump when the substitution changes (cells, blocks or binding format): demo worlds then update. */
	public static final String ID = "blocks-5";

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
		Map<BlockPos, ScreenRect> screens = screens(fixtures);
		Set<BlockPos> anchored = new HashSet<>();
		for (Anchor a : plan.anchors().values()) {
			anchored.add(a.block());
		}
		return plan.replacing(p -> {
			if (wallState != null && p.rel().equals(wall.rel())) {
				return new Placement(p.rel(), wallState, wallData(wall));
			}
			HqBindings.Fixture f = fixtures.get(p.rel());
			if (f == null) {
				Placement plaque = anchored.contains(p.rel()) ? null : plaque(p);
				return plaque == null ? p : plaque;
			}
			BlockState s = switch (f.role()) {
				case MONITOR, SCREEN -> {
					ScreenRect e = screens.get(f.rel());
					yield e == null ? states.monitor(f.facing()) : states.monitor(f.facing(), e.sides(f.rel()));
				}
				case BEACON -> states.lamp();
				case BOARD_LAMP -> states.boardLamp();
				case ARCHIVE_TERMINAL -> states.archiveTerminal(f.facing());
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

	/** The screen of every monitor fixture, by the world's rule ({@link MonitorWall}) over the plan's monitor cells. */
	static Map<BlockPos, ScreenRect> screens(Map<BlockPos, HqBindings.Fixture> fixtures) {
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

	/**
	 * A wall sign with text (module signs, the board's "Needs attention") → a room plaque with the same
	 * lines and the sign's dye as accent; null for anything else. Signs that are anchors (the archive
	 * shelf's header and its "older threads" click target) stay vanilla signs.
	 */
	@Nullable Placement plaque(Placement p) {
		if (!(p.state().getBlock() instanceof WallSignBlock) || p.be() == null || states.plaque(Direction.NORTH) == null) {
			return null;
		}
		Tag front = p.be().get("front_text");
		if (front == null) {
			return null;
		}
		SignText text = SignText.CODEC.parse(NbtOps.INSTANCE, front).result().orElse(null);
		if (text == null) {
			return null;
		}
		List<String> lines = new ArrayList<>(SignText.LINES);
		for (Component c : text.getMessages(false)) {
			lines.add(c.getString());
		}
		BlockState plaque = states.plaque(p.state().getValue(WallSignBlock.FACING));
		if (plaque == null) {
			return null;
		}
		return new Placement(p.rel(), plaque, states.plaqueData(text.getColor().getTextColor(), lines.toArray(String[]::new)));
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
