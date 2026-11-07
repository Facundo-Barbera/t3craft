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
 *   <li>{@code archive_<threadId>} anchors → monitor facing the aisle, bound to that settled thread;</li>
 *   <li>the {@code beacon} anchor (the hub lantern's cap) → status lamp bound {@code beacon}.</li>
 * </ul>
 *
 * Used twice with the same result: {@link HqBlockFixtures} writes it into the plan as block entity
 * data (the primary binding, travels with the block), and {@link #world} gives the position fallback
 * for blocks that have no data of their own.
 */
public final class HqBindings {
	public enum Role {
		MONITOR, LAMP, BOARD_LAMP, BEACON
	}

	/** One fixture cell, relative to the HQ origin. {@code facing}: where the screen looks (monitors). */
	public record Fixture(BlockPos rel, Role role, Binding binding, Direction facing) {
	}

	private HqBindings() {
	}

	/** Every fixture of {@code plan}, one per cell, in a fixed order (desks, board bulbs, archive). */
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
		String archive = AnchorNames.archive("");
		for (Anchor a : plan.anchors().values()) {
			String n = a.name();
			if (n.startsWith(archive) && n.length() > archive.length()) {
				out.putIfAbsent(a.block(), new Fixture(a.block(), Role.MONITOR, Binding.thread(n.substring(archive.length())), a.facing()));
			}
		}
		Anchor beacon = plan.anchors().get(AnchorNames.BEACON);
		if (beacon != null) {
			out.putIfAbsent(beacon.block(), new Fixture(beacon.block(), Role.BEACON, Binding.BEACON, beacon.facing()));
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

	private static int parseIndex(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
