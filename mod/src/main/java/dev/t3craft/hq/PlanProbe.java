package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Read-only questions about a finished {@link BuildPlan}, for tools (the hq-render report): the
 * package-private helpers of this package ({@link PlanNav}, {@link PlanShapes}, the group index) seen
 * from outside. Nothing here changes a plan or the generators; it only reads.
 */
public final class PlanProbe {
	private PlanProbe() {
	}

	/** A shaped block (fence, wall, pane, bars, stairs) whose state differs from what vanilla gives it among the plan's real neighbours. */
	public record ShapeMismatch(BlockPos pos, BlockState actual, BlockState expected) {
	}

	/** A block that vanilla's own {@code canSurvive} refuses in its place (torch on air, carpet over nothing, a hanging lantern with no ceiling…). */
	public record Unsupported(BlockPos pos, BlockState state) {
	}

	/** Group of every placement a module owns (see {@link BuildPlan#groupAt}), in one pass. */
	public static Map<BlockPos, String> groups(BuildPlan plan) {
		return plan.groupsByPos();
	}

	/**
	 * Feet cells an NPC reaches on foot from {@code start} through the plan ({@link PlanNav}: body size,
	 * collision, steps of one block). Empty when {@code start} is not standable.
	 */
	public static Set<BlockPos> reachable(BuildPlan plan, BlockPos start) {
		return new PlanNav(k -> {
			Placement p = plan.at(BlockPos.of(k));
			return p == null ? null : p.state();
		}).reachable(start);
	}

	/** True when a person can stand with their feet in {@code cell} among the plan's blocks. */
	public static boolean standable(BuildPlan plan, BlockPos cell) {
		return !Double.isNaN(new PlanNav(k -> {
			Placement p = plan.at(BlockPos.of(k));
			return p == null ? null : p.state();
		}).floor(cell.getX(), cell.getY(), cell.getZ()));
	}

	private static Map<Long, BlockState> cells(BuildPlan plan) {
		Map<Long, BlockState> cells = new HashMap<>(plan.size() * 2);
		for (Placement p : plan.placements()) {
			cells.put(p.rel().asLong(), p.state());
		}
		return cells;
	}

	/**
	 * Every shaped block whose connection state does not match its real neighbours: what
	 * {@link PlanConnect#apply} gives it on the plan's final cells against what the plan holds
	 * (fix/plan-connect: explicit rules, nothing caught; the old check ran vanilla's
	 * {@code updateShape} through a proxy and, with block tags unbound headless, wanted fences and walls
	 * disconnected).
	 */
	public static List<ShapeMismatch> shapeMismatches(BuildPlan plan) {
		Map<Long, BlockState> cells = cells(plan);
		Map<Long, BlockState> expected = new HashMap<>(cells);
		PlanConnect.apply(expected);
		List<ShapeMismatch> out = new ArrayList<>();
		for (Map.Entry<Long, BlockState> e : cells.entrySet()) {
			BlockState s = e.getValue();
			BlockState u = expected.get(e.getKey());
			if (PlanConnect.shaped(s) && !u.equals(s)) {
				out.add(new ShapeMismatch(BlockPos.of(e.getKey()), s, u));
			}
		}
		out.sort(java.util.Comparator.comparingLong(m -> m.pos().asLong()));
		return out;
	}

	/**
	 * Blocks vanilla would not let stand where the plan puts them ({@code BlockState#canSurvive} against
	 * the plan; cells outside it read as air). A block whose check needs more than block reads is left
	 * out (never reported).
	 */
	public static List<Unsupported> unsupported(BuildPlan plan) {
		LevelAccessor view = PlanShapes.view(cells(plan));
		List<Unsupported> out = new ArrayList<>();
		for (Placement p : plan.placements()) {
			BlockState s = p.state();
			if (s.isAir()) {
				continue;
			}
			boolean ok;
			try {
				ok = s.canSurvive(view, p.rel());
			} catch (RuntimeException e) {
				continue;
			}
			if (!ok) {
				out.add(new Unsupported(p.rel(), s));
			}
		}
		return out;
	}
}
