// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.blocks;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Which monitors merge into one large screen. Pure: works on any {@link Lookup} (the server's level,
 * the HQ plan, a map in tests), so the world and the plan always agree.
 *
 * <p>Rule: the monitors that touch in the plane of their screens (above, below, to the viewer's left
 * or right) and have <b>the same facing</b> form a group. The group is <b>one screen</b> when
 * <ul>
 *   <li>it fills its bounding rectangle exactly (no L shapes, no holes),</li>
 *   <li>it is at most {@value #MAX_WIDTH} × {@value #MAX_HEIGHT} blocks, and</li>
 *   <li>its bindings agree: every bound monitor has the same binding (unbound ones join any).</li>
 * </ul>
 * Otherwise every monitor of the group stays a lone 1×1 screen. The binding rule keeps two desks or
 * archive slots that happen to touch (each bound to its own thread) apart, while a wall placed by
 * hand (all unbound) merges and can then be bound as a whole.
 *
 * <p>Adapted from AgentCraft's {@code PanelBlock.connect/origin/extent}, which joins any neighbour
 * with the same facing; here the group is validated as a whole so the block states never describe a
 * shape the renderer cannot draw.
 */
public final class MonitorWall {
	public static final int MAX_WIDTH = 12;
	public static final int MAX_HEIGHT = 8;

	/** What the wall needs to know about a cell that holds a monitor. */
	public record Cell(Direction facing, String binding) {
		public Cell {
			binding = binding == null ? "" : binding;
		}
	}

	/** A world view: the monitor at {@code pos}, or null when there is none (or it is not loaded). */
	@FunctionalInterface
	public interface Lookup {
		@Nullable Cell at(BlockPos pos);
	}

	private MonitorWall() {
	}

	/** The screen the monitor at {@code pos} belongs to; a 1×1 extent if it does not merge (null: no monitor). */
	public static @Nullable ScreenRect extentOf(Lookup world, BlockPos pos) {
		Cell self = world.at(pos);
		if (self == null) {
			return null;
		}
		return screenOf(world, pos.immutable(), self, new HashSet<>()).get(pos.immutable());
	}

	/**
	 * The screen of every monitor in the groups that contain {@code seeds} (positions without a monitor
	 * are skipped). Each group is walked once. This is what a refresh after placing or removing a block
	 * applies: pass the changed cell and its neighbours.
	 */
	public static Map<BlockPos, ScreenRect> resolve(Lookup world, Collection<BlockPos> seeds) {
		Map<BlockPos, ScreenRect> out = new LinkedHashMap<>();
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos s : seeds) {
			BlockPos p = s.immutable();
			if (out.containsKey(p) || seen.contains(p)) {
				continue;
			}
			Cell c = world.at(p);
			if (c != null) {
				out.putAll(screenOf(world, p, c, seen));
			}
		}
		return out;
	}

	/** The in-plane neighbours of a monitor facing {@code facing}: up, down, viewer's left, viewer's right. */
	public static Direction[] planeDirections(Direction facing) {
		return new Direction[] {Direction.UP, Direction.DOWN, facing.getClockWise(), facing.getCounterClockWise()};
	}

	private static Map<BlockPos, ScreenRect> screenOf(Lookup world, BlockPos start, Cell first, Set<BlockPos> seen) {
		Direction facing = first.facing();
		Direction right = facing.getCounterClockWise();
		int limit = MAX_WIDTH * MAX_HEIGHT;
		Set<BlockPos> group = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		group.add(start);
		queue.add(start);
		String binding = "";
		boolean agree = true;
		boolean tooBig = false;
		int minC = 0;
		int maxC = 0;
		int minR = 0;
		int maxR = 0;
		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			Cell c = p.equals(start) ? first : world.at(p);
			if (c == null) {
				continue;
			}
			if (!c.binding().isEmpty()) {
				if (binding.isEmpty()) {
					binding = c.binding();
				} else if (!binding.equals(c.binding())) {
					agree = false;
				}
			}
			int col = (p.getX() - start.getX()) * right.getStepX() + (p.getZ() - start.getZ()) * right.getStepZ();
			int row = start.getY() - p.getY();
			minC = Math.min(minC, col);
			maxC = Math.max(maxC, col);
			minR = Math.min(minR, row);
			maxR = Math.max(maxR, row);
			if (group.size() > limit) {
				tooBig = true;
				break;
			}
			for (Direction d : planeDirections(facing)) {
				BlockPos n = p.relative(d);
				if (group.contains(n)) {
					continue;
				}
				Cell nc = world.at(n);
				if (nc != null && nc.facing() == facing) {
					group.add(n);
					queue.add(n);
				}
			}
		}
		seen.addAll(group);
		int w = maxC - minC + 1;
		int h = maxR - minR + 1;
		Map<BlockPos, ScreenRect> out = new LinkedHashMap<>();
		if (!tooBig && agree && w <= MAX_WIDTH && h <= MAX_HEIGHT && w * h == group.size()) {
			ScreenRect e = new ScreenRect(start.relative(right, minC).above(-minR), facing, w, h);
			for (BlockPos p : e.cells()) {
				out.put(p, e);
			}
		} else {
			for (BlockPos p : group) {
				if (world.at(p) instanceof Cell c && c.facing() == facing) {
					out.put(p, ScreenRect.single(p, facing));
				}
			}
		}
		return out;
	}
}
