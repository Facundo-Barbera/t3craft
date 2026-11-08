package dev.t3craft.client.demo;

import dev.t3craft.build.BuildBudget;
import dev.t3craft.build.BuildCursor;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.BuildTuning;
import dev.t3craft.build.FrontCursor;
import dev.t3craft.build.PlacementOrders;
import dev.t3craft.build.JobState;
import dev.t3craft.build.PartSchedule;
import dev.t3craft.build.Placement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * fix/hq-build-parallel: headless model of the HQ world's animated build, server tick by server tick,
 * with the <b>real</b> scheduling code: {@link PlacementOrders#current()} + {@link FrontCursor} per part (as in
 * {@code BuildJob}), {@link BuildBudget#allocate} for the per-tick budget and job slots (as in
 * {@code BuildManager}), and {@link PartSchedule} + {@link DemoBuildRun#crews}/{@link DemoBuildRun#frontRate}
 * for which parts run (as in {@link DemoBuildRun}). The world is an in-memory superflat: ground below
 * {@code y = 0}, air above; a cell already right is a free skip (bounded per tick), exactly like
 * {@code ServerLevelSink}.
 *
 * <p>Only the walk of the crews is modelled instead of simulated ({@link #arrivalTicks}): builders
 * spawn {@code 14} blocks from the part's centre and walk at {@code 0.22} blocks/tick to its edge
 * ({@code BuildCrew}), capped by its 200-tick arrive timeout; the job is submitted when they are there
 * and the server picks it up one tick later. A finished part frees its crew slot on the next tick (the
 * run polls on the client tick).
 */
final class HqBuildSim {
	/** BuildCrew.SPAWN_DISTANCE, WALK_SPEED, ARRIVE_TIMEOUT_TICKS. */
	static final double SPAWN = 14.0;
	static final double WALK = 0.22;
	static final int ARRIVE_CAP = 200;

	/** How the run picks parts and paces fronts. */
	record Policy(String name, int crews, double frontRate, int fronts) {
		/** main before this branch: one part after another, demo.blocksPerTick per front. */
		static Policy before(DemoConfig c, BuildTuning t) {
			return new Policy("before", 1, c.blocksPerTick(), t.frontsFor(c.crew()));
		}

		/** This branch: {@link DemoBuildRun}'s crews and front rate. */
		static Policy after(DemoConfig c, BuildTuning t) {
			return new Policy("after", DemoBuildRun.crews(t), DemoBuildRun.frontRate(c, t), t.frontsFor(c.crew()));
		}
	}

	record Result(long ticks, long placed, long skipped, int maxPerTick, double meanPerTick, int maxFronts, double meanFronts, int maxParts,
			boolean overBudget, boolean overlap, boolean outOfArea, int maxEffective) {
		double seconds() {
			return ticks / 20.0;
		}
	}

	/** World cells set so far (everything else: ground below y 0, air above). */
	final Map<Long, BlockState> world = new HashMap<>();
	private final BlockState ground = Blocks.GRASS_BLOCK.defaultBlockState();
	private final BlockState air = Blocks.AIR.defaultBlockState();

	BlockState state(BlockPos p) {
		BlockState s = world.get(p.asLong());
		return s != null ? s : p.getY() < 0 ? ground : air;
	}

	static int arrivalTicks(BuildPlan p) {
		double hx = (p.max().getX() - p.min().getX() + 1) / 2.0;
		double hz = (p.max().getZ() - p.min().getZ() + 1) / 2.0;
		double r = (hx + hz) / 2;
		return (int) Math.min(ARRIVE_CAP, Math.max(1, Math.ceil(Math.abs(SPAWN - r) / WALK)));
	}

	private final class Part {
		final int index;
		final BuildPlan plan;
		final FrontCursor cursor;
		final long submitAt;
		final int[] box;

		Part(int index, BuildPlan plan, int fronts, long submitAt, int[] box) {
			this.index = index;
			this.plan = plan;
			this.cursor = new FrontCursor(PlacementOrders.current().create(plan, fronts)); // as BuildJob
			this.submitAt = submitAt;
			this.box = box;
		}
	}

	/**
	 * Runs {@code parts} to the end (or {@code stopAt} ticks, then drops the run as leaving the world
	 * does) and returns the counters. {@code budget} is fresh per run.
	 */
	Result run(List<BuildPlan> parts, Policy policy, BuildBudget budget, long stopAt) {
		PartSchedule schedule = PartSchedule.ofPlans(parts);
		int n = parts.size();
		boolean[] finished = new boolean[n];
		boolean[] running = new boolean[n];
		Map<Integer, Part> crews = new LinkedHashMap<>();
		List<Part> jobs = new ArrayList<>(); // submitted, in submission order (BuildManager.active)
		long tick = 0;
		long placed = 0;
		long skipped = 0;
		long placedTicks = 0;
		long frontSum = 0;
		int maxPerTick = 0;
		int maxFronts = 0;
		int maxParts = 0;
		int maxEffective = 0;
		boolean over = false;
		boolean overlap = false;
		boolean outside = false;
		int done = 0;
		while (done < n && tick < stopAt) {
			tick++;
			// client tick of the run: free finished crews, start ready parts
			for (var it = crews.entrySet().iterator(); it.hasNext(); ) {
				var e = it.next();
				if (e.getValue().cursor.state() == JobState.COMPLETED) {
					it.remove();
					running[e.getKey()] = false;
					finished[e.getKey()] = true;
					done++;
				}
			}
			if (done == n) {
				break;
			}
			while (crews.size() < policy.crews()) {
				int j = schedule.next(finished, running);
				if (j < 0) {
					break;
				}
				BuildPlan p = parts.get(j);
				Part part = new Part(j, p, policy.fronts(), tick + arrivalTicks(p) + 1, schedule.box(j));
				crews.put(j, part);
				running[j] = true;
			}
			maxParts = Math.max(maxParts, crews.size());
			List<Part> live = new ArrayList<>(crews.values());
			for (int a = 0; a < live.size(); a++) {
				for (int b = a + 1; b < live.size(); b++) {
					if (!schedule.independent(live.get(a).index, live.get(b).index)) {
						overlap = true;
					}
				}
			}
			// server tick: BuildManager picks up arrived crews' jobs, allocates and places
			for (Part p : crews.values()) {
				if (p.submitAt == tick && !jobs.contains(p)) {
					jobs.add(p);
				}
			}
			budget.beginTick(tick, -1);
			List<JobState> states = new ArrayList<>(jobs.size());
			double[] rates = new double[jobs.size()];
			for (int i = 0; i < rates.length; i++) {
				states.add(jobs.get(i).cursor.state());
				rates[i] = policy.frontRate() * policy.fronts();
			}
			double[] allow = budget.allocate("builders", states, rates);
			int placedNow = 0;
			java.util.Set<Long> activeFronts = new java.util.HashSet<>();
			for (int i = 0; i < jobs.size(); i++) {
				Part p = jobs.get(i);
				if (p.cursor.state().isTerminal() || (allow[i] <= 0 && p.cursor.state() == JobState.PENDING)) {
					continue;
				}
				int before = p.cursor.placed();
				int skipBefore = p.cursor.skipped();
				p.cursor.tick(allow[i], (front, index) -> {
					Placement pl = p.plan.get(index);
					BlockPos pos = pl.pos();
					activeFronts.add(((long) p.index << 8) | front);
					if (pos.getX() < p.box[0] || pos.getY() < p.box[1] || pos.getZ() < p.box[2] || pos.getX() > p.box[3] || pos.getY() > p.box[4]
							|| pos.getZ() > p.box[5]) {
						throw new AssertionError("out of area");
					}
					if (state(pos) == pl.state()) {
						return BuildCursor.Outcome.SKIPPED;
					}
					world.put(pos.asLong(), pl.state());
					return BuildCursor.Outcome.PLACED;
				});
				placedNow += p.cursor.placed() - before;
				skipped += p.cursor.skipped() - skipBefore;
			}
			jobs.removeIf(p -> p.cursor.state().isTerminal());
			maxEffective = Math.max(maxEffective, budget.effectiveMax());
			if (placedNow > budget.effectiveMax()) {
				over = true;
			}
			placed += placedNow;
			if (placedNow > 0) {
				placedTicks++;
			}
			maxPerTick = Math.max(maxPerTick, placedNow);
			frontSum += activeFronts.size();
			maxFronts = Math.max(maxFronts, activeFronts.size());
		}
		return new Result(tick, placed, skipped, maxPerTick, placedTicks == 0 ? 0 : (double) placed / placedTicks, maxFronts,
				tick == 0 ? 0 : (double) frontSum / tick, maxParts, over, overlap, outside, maxEffective);
	}

	/** What the world must hold once every part is applied in order (the sequential result). */
	static Map<Long, BlockState> expected(List<BuildPlan> parts) {
		Map<Long, BlockState> out = new HashMap<>();
		for (BuildPlan p : parts) {
			for (Placement pl : p.placements()) {
				out.put(pl.pos().asLong(), pl.state());
			}
		}
		return out;
	}

	/** Cells of {@code expected} whose state in this world differs. */
	int mismatches(Map<Long, BlockState> expected) {
		int bad = 0;
		for (var e : expected.entrySet()) {
			if (state(BlockPos.of(e.getKey())) != e.getValue()) {
				bad++;
			}
		}
		return bad;
	}
}
