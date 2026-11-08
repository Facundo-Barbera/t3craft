package dev.t3craft.blocks;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.Workstation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Thread ↔ block binding from the HQ plan: which cell of the plan is which fixture, and what it
 * shows. Pure function of the plan, so it is deterministic and idempotent with hq-design's layout: a
 * desk's cells depend only on its (module, slot), the thread sitting there comes from the stable
 * {@code HqLayout}, and the same roster in any order gives the same list.
 *
 * <ul>
 *   <li>each desk: the six cells of its 3×2 screen ({@link Workstation#screenCells}) → monitors facing
 *       the agent, all bound to {@code thread:<threadId>} (the HQ's thread id is the NPC key), so they
 *       merge into one screen whose origin carries the thread ({@link MonitorWall}); unbound when
 *       vacant. Desks have no status lamp (fix/desk-screens-3x2);</li>
 *   <li>{@code board_lamp_<i>} anchors → board bulb {@code board:<i>};</li>
 *   <li>each module: the lintel over its doorway (the inner wall's beam, one block in from the
 *       {@code module@<i>} anchor, three up) → board bulb {@code module:<threads at its desks>}, the
 *       module's most urgent thread; unbound (off) while the module has nobody;</li>
 *   <li>the {@code archiveTerminal} anchor → the archive terminal (unbound). Closed threads live only
 *       there: the "Archived" shelf's {@code archive_<threadId>} cells are <b>not</b> fixtures (they stay
 *       the plan's plain blocks, no thread shown on them);</li>
 *   <li>the {@code beacon} anchor (the hub lantern's cap) → status lamp bound {@code beacon};</li>
 *   <li>each {@link AnchorNames#bigScreen bigscreen_<key>@<w>x<h>=<binding>} anchor → a large screen:
 *       every cell of the {@code w × h} rectangle (top-left = the anchor's block, columns to the
 *       viewer's right) becomes a monitor facing the anchor's facing, all bound to {@code binding}, so
 *       they merge into one screen ({@link MonitorWall}). Cells the plan leaves empty, or already taken
 *       by another fixture, are skipped.</li>
 * </ul>
 *
 * <p>Reserving a large screen from the HQ generator (e.g. one per module, or one in the hub):
 * <pre>{@code
 * b.anchor(Anchor.face(AnchorNames.bigScreen("module@" + i, 3, 2, Binding.project(projectKey).wire()), topLeft, facing));
 * }</pre>
 * and place a solid block in each of its cells. {@link #screens} lists them.
 *
 * Used twice with the same result: {@link HqBlockFixtures} writes it into the plan as block entity
 * data (the primary binding, travels with the block), and {@link #world} gives the position fallback
 * for blocks that have no data of their own.
 */
public final class HqBindings {
	public enum Role {
		/** One cell of a desk's 3×2 screen. */
		MONITOR,
		BOARD_LAMP, BEACON,
		/** The archive terminal ({@code archiveTerminal} anchor): unbound, it always shows the whole archive. */
		ARCHIVE_TERMINAL,
		/** One cell of a large screen reserved by the plan ({@link AnchorNames#bigScreen}). */
		SCREEN
	}

	/**
	 * The plan's thread wall: anchor cell (bottom-left as seen from the front, relative to the HQ
	 * origin), the face's outward normal and the size in blocks ({@code 0} = the config's).
	 */
	public record ThreadWall(BlockPos rel, Direction facing, int width, int height) {
	}

	/** One fixture cell, relative to the HQ origin. {@code facing}: where the screen looks (monitors). */
	public record Fixture(BlockPos rel, Role role, Binding binding, Direction facing) {
	}

	private HqBindings() {
	}

	/** The 3×2 screen of a desk as a {@link ScreenRect} (plan-relative; origin = top-left cell). */
	public static ScreenRect deskScreen(Workstation w) {
		return new ScreenRect(w.screenOrigin(), w.screenFacing(), Workstation.SCREEN_WIDTH, Workstation.SCREEN_HEIGHT);
	}

	/** A large screen reserved by the plan: its plan-relative extent and what it shows. */
	public record Screen(String key, ScreenRect extent, Binding binding) {
	}

	/** Every fixture of {@code plan}, one per cell, in a fixed order (desks, board bulbs, module bulbs, archive terminal, beacon, large screens). */
	public static List<Fixture> of(BuildPlan plan) {
		Map<BlockPos, Fixture> out = new LinkedHashMap<>();
		for (Workstation w : plan.workstations()) {
			Binding b = w.threadId() == null ? Binding.NONE : Binding.thread(w.threadId());
			for (BlockPos cell : w.screenCells()) {
				out.putIfAbsent(cell, new Fixture(cell, Role.MONITOR, b, w.screenFacing()));
			}
		}
		for (Anchor a : plan.anchors().values()) {
			String n = a.name();
			if (n.startsWith(AnchorNames.BOARD_LAMP)) {
				int i = parseIndex(n.substring(AnchorNames.BOARD_LAMP.length()));
				if (i >= 0) {
					out.putIfAbsent(a.block(), new Fixture(a.block(), Role.BOARD_LAMP, Binding.board(i), a.facing()));
				}
			}
		}
		for (Anchor a : plan.anchors().values()) {
			int i = moduleIndex(a.name());
			if (i < 0) {
				continue;
			}
			BlockPos lintel = moduleLamp(a);
			if (plan.at(lintel) != null) {
				out.putIfAbsent(lintel, new Fixture(lintel, Role.BOARD_LAMP, Binding.module(moduleThreads(plan, i)), a.facing()));
			}
		}
		Anchor terminal = plan.anchors().get(AnchorNames.ARCHIVE_TERMINAL);
		if (terminal != null) {
			out.putIfAbsent(terminal.block(), new Fixture(terminal.block(), Role.ARCHIVE_TERMINAL, Binding.NONE, terminal.facing()));
		}
		Anchor beacon = plan.anchors().get(AnchorNames.BEACON);
		if (beacon != null) {
			out.putIfAbsent(beacon.block(), new Fixture(beacon.block(), Role.BEACON, Binding.BEACON, beacon.facing()));
		}
		for (Screen sc : screens(plan)) {
			for (BlockPos cell : sc.extent().cells()) {
				if (plan.at(cell) != null) {
					out.putIfAbsent(cell, new Fixture(cell, Role.SCREEN, sc.binding(), sc.extent().facing()));
				}
			}
		}
		return List.copyOf(out.values());
	}

	/** The large screens the plan reserves ({@link AnchorNames#bigScreen} anchors), in anchor-name order. */
	public static List<Screen> screens(BuildPlan plan) {
		List<Screen> out = new ArrayList<>();
		for (Anchor a : plan.anchors().values()) {
			AnchorNames.BigScreen bs = AnchorNames.parseBigScreen(a.name());
			if (bs != null) {
				int w = Math.min(bs.width(), MonitorWall.MAX_WIDTH);
				int h = Math.min(bs.height(), MonitorWall.MAX_HEIGHT);
				out.add(new Screen(bs.key(), new ScreenRect(a.block(), a.facing(), w, h), Binding.parse(bs.binding())));
			}
		}
		return out;
	}

	/**
	 * The thread wall the plan reserves ({@link AnchorNames#THREAD_WALL}, sized by
	 * {@link AnchorNames#THREAD_WALL_END}), or null when the plan has none. Pure.
	 */
	public static @Nullable ThreadWall threadWall(BuildPlan plan) {
		Anchor a = plan.anchors().get(AnchorNames.THREAD_WALL);
		if (a == null) {
			return null;
		}
		Anchor end = plan.anchors().get(AnchorNames.THREAD_WALL_END);
		int[] size = end == null ? new int[] {0, 0} : wallSize(a.block(), end.block(), a.facing());
		return new ThreadWall(a.block(), a.facing(), size[0], size[1]);
	}

	/**
	 * {width, height} of a wall from its bottom-left cell {@code from} to its top-right cell {@code to}
	 * (as seen from the front, {@code facing} = outward normal); {0, 0} when they do not span a
	 * rectangle on one plane facing the viewer. Clamped to the renderer's maximum.
	 */
	static int[] wallSize(BlockPos from, BlockPos to, Direction facing) {
		Direction right = facing.getCounterClockWise();
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		int along = dx * right.getStepX() + dz * right.getStepZ();
		int depth = dx * facing.getStepX() + dz * facing.getStepZ();
		int up = to.getY() - from.getY();
		if (depth != 0 || along < 0 || up < 0) {
			return new int[] {0, 0};
		}
		return new int[] {
				Math.min(along + 1, ThreadBoardBlockEntity.MAX_WIDTH),
				Math.min(up + 1, ThreadBoardBlockEntity.MAX_HEIGHT)};
	}

	/**
	 * World cell ({@link BlockPos#asLong}) → binding, for the HQ placed at {@code site}. Unbound
	 * (vacant) cells are included as {@link Binding#NONE} so they stay unbound.
	 */
	public static Map<Long, Binding> world(BuildPlan plan, HqSite site) {
		Map<Long, Binding> out = new LinkedHashMap<>();
		for (Fixture f : of(plan)) {
			out.put(site.cell(f.rel()).asLong(), f.binding());
		}
		return Collections.unmodifiableMap(out);
	}

	/** Bindings by role (tests, debug). */
	public static List<Fixture> ofRole(BuildPlan plan, Role role) {
		List<Fixture> out = new ArrayList<>();
		for (Fixture f : of(plan)) {
			if (f.role() == role) {
				out.add(f);
			}
		}
		return out;
	}

	/**
	 * The bindings that change from {@code before} to {@code after} (both {@link #world} maps, or any
	 * cell → binding maps): cells whose binding differs or that are new, and cells that are gone,
	 * mapped to {@link Binding#NONE}. Unchanged cells are left out, so applying the result touches
	 * only the blocks that must change. {@code before == null}: everything in {@code after}.
	 */
	public static Map<Long, Binding> changes(@Nullable Map<Long, Binding> before, Map<Long, Binding> after) {
		Map<Long, Binding> out = new LinkedHashMap<>();
		for (Map.Entry<Long, Binding> e : after.entrySet()) {
			if (before == null || !e.getValue().equals(before.get(e.getKey()))) {
				out.put(e.getKey(), e.getValue());
			}
		}
		if (before != null) {
			for (Map.Entry<Long, Binding> e : before.entrySet()) {
				if (!after.containsKey(e.getKey()) && !e.getValue().isNone()) {
					out.put(e.getKey(), Binding.NONE);
				}
			}
		}
		return Collections.unmodifiableMap(out);
	}

	/** The cell of a module's bulb: the lintel of its doorway, seen from the {@code module@<i>} anchor. */
	static BlockPos moduleLamp(Anchor door) {
		return door.block().relative(door.facing().getOpposite()).above(3);
	}

	private static List<String> moduleThreads(BuildPlan plan, int module) {
		List<String> out = new ArrayList<>();
		for (Workstation w : plan.workstations()) {
			if (w.module() == module && w.threadId() != null) {
				out.add(w.threadId());
			}
		}
		return out;
	}

	private static int moduleIndex(String anchor) {
		String prefix = AnchorNames.module(0);
		prefix = prefix.substring(0, prefix.length() - 1);
		return anchor.startsWith(prefix) ? parseIndex(anchor.substring(prefix.length())) : -1;
	}

	private static int parseIndex(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
