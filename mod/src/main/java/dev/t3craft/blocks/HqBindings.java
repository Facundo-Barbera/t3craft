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
 *   <li>each desk: {@code computerPos} → monitor facing the chair, {@code lampPos} → status lamp; both
 *       bound to {@code thread:<threadId>} (the HQ's thread id is the NPC key) or unbound when vacant;</li>
 *   <li>{@code board_lamp_<i>} anchors → board bulb {@code board:<i>};</li>
 *   <li>each module: the lintel over its doorway (the inner wall's beam, one block in from the
 *       {@code module@<i>} anchor, three up) → board bulb {@code module:<threads at its desks>}, the
 *       module's most urgent thread; unbound (off) while the module has nobody;</li>
 *   <li>{@code archive_<threadId>} anchors → monitor facing the aisle, bound to that settled thread.</li>
 * </ul>
 *
 * Used twice with the same result: {@link HqBlockFixtures} writes it into the plan as block entity
 * data (the primary binding, travels with the block), and {@link #world} gives the position fallback
 * for blocks that have no data of their own.
 */
public final class HqBindings {
	public enum Role {
		MONITOR, LAMP, BOARD_LAMP
	}

	/** One fixture cell, relative to the HQ origin. {@code facing}: where the screen looks (monitors). */
	public record Fixture(BlockPos rel, Role role, Binding binding, Direction facing) {
	}

	private HqBindings() {
	}

	/** Every fixture of {@code plan}, one per cell, in a fixed order (desks, board bulbs, module bulbs, archive). */
	public static List<Fixture> of(BuildPlan plan) {
		Map<BlockPos, Fixture> out = new LinkedHashMap<>();
		for (Workstation w : plan.workstations()) {
			Binding b = w.threadId() == null ? Binding.NONE : Binding.thread(w.threadId());
			out.putIfAbsent(w.computerPos(), new Fixture(w.computerPos(), Role.MONITOR, b, w.screenFacing()));
			out.putIfAbsent(w.lampPos(), new Fixture(w.lampPos(), Role.LAMP, b, w.screenFacing()));
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
		String archive = AnchorNames.archive("");
		for (Anchor a : plan.anchors().values()) {
			String n = a.name();
			if (n.startsWith(archive) && n.length() > archive.length()) {
				out.putIfAbsent(a.block(), new Fixture(a.block(), Role.MONITOR, Binding.thread(n.substring(archive.length())), a.facing()));
			}
		}
		return List.copyOf(out.values());
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
