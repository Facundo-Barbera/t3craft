package dev.t3craft.build;

import dev.t3craft.T3Craft;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * Runs {@link BuildJob}s on the server thread, one {@link #tick} per server tick. Not a singleton
 * and no static state: the owner (here the client entrypoint, for the integrated server) holds one
 * instance and calls {@link #clear()} when the server stops, so nothing survives a world change or
 * a hot reload with a stale type.
 *
 * <p>{@link #submit}, {@link #job}, {@link #jobs} and the job controls are safe from any thread;
 * {@link #tick} and listener callbacks happen on the server thread.
 */
public final class BuildManager {
	private final AtomicInteger nextId = new AtomicInteger(1);
	private final Queue<BuildJob> incoming = new ConcurrentLinkedQueue<>();
	private final List<BuildJob> active = new ArrayList<>();
	private final Map<Integer, BuildJob> byId = new ConcurrentHashMap<>();
	private final List<BuildListener> listeners = new CopyOnWriteArrayList<>();
	private final Function<ServerLevel, BlockSink> sinks;
	private final Map<Integer, UnaryOperator<BlockSink>> wraps = new ConcurrentHashMap<>();
	/** Null = {@link BuildBudget#global()}. */
	private final @Nullable BuildBudget budget;
	static final String RUNNER = "builders";

	public BuildManager() {
		this(ServerLevelSink::new);
	}

	/** @param sinks how to place into a level (default {@link ServerLevelSink}). */
	public BuildManager(Function<ServerLevel, BlockSink> sinks) {
		this(sinks, null);
	}

	/** @param budget the per-tick budget to draw from (tests); null = {@link BuildBudget#global()}. */
	public BuildManager(Function<ServerLevel, BlockSink> sinks, @Nullable BuildBudget budget) {
		this.sinks = sinks;
		this.budget = budget;
	}

	public void addListener(BuildListener l) {
		listeners.add(l);
	}

	public void removeListener(BuildListener l) {
		listeners.remove(l);
	}

	/** Creates and queues a free job; it starts on the next server tick. */
	public BuildJob submit(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick) {
		return submit(label, dimension, origin, rotation, plan, blocksPerTick, (BuildCost) null);
	}

	/** Like {@link #submit(String, ResourceKey, BlockPos, Rotation, BuildPlan, double)}; a non-null {@code cost} charges materials. */
	public BuildJob submit(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick,
			@Nullable BuildCost cost) {
		return submit(label, dimension, origin, rotation, plan, blocksPerTick, cost, null);
	}

	/**
	 * Like {@link #submit(String, ResourceKey, BlockPos, Rotation, BuildPlan, double, BuildCost)}, with every block of this job
	 * placed through {@code wrap.apply(sink)} (the site's "never overwrite the player's changes" guard).
	 */
	public BuildJob submit(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick,
			@Nullable BuildCost cost, @Nullable UnaryOperator<BlockSink> wrap) {
		return submit(label, dimension, origin, rotation, plan, blocksPerTick, cost, wrap, 1);
	}

	/**
	 * Like {@link #submit(String, ResourceKey, BlockPos, Rotation, BuildPlan, double, BuildCost, UnaryOperator)}, split into
	 * {@code fronts} parallel fronts ({@link Fronts}), each at {@code blocksPerTick}, within the global {@link BuildBudget}.
	 */
	public BuildJob submit(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick,
			@Nullable BuildCost cost, @Nullable UnaryOperator<BlockSink> wrap, int fronts) {
		return submit(label, dimension, origin, rotation, plan, blocksPerTick, cost, wrap, fronts, BuildPlanner.LAYERS);
	}

	/** Like {@link #submit(String, ResourceKey, BlockPos, Rotation, BuildPlan, double, BuildCost, UnaryOperator, int)} with a {@link BuildPlanner}. */
	public BuildJob submit(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick,
			@Nullable BuildCost cost, @Nullable UnaryOperator<BlockSink> wrap, int fronts, BuildPlanner planner) {
		return submit(label, dimension, origin, rotation, plan, blocksPerTick, cost, wrap, fronts, planner, null);
	}

	/** feat/build-near-player: like the overload above; a crew starts by {@code focusWorld} (the player), null = anywhere. */
	public BuildJob submit(String label, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, BuildPlan plan, double blocksPerTick,
			@Nullable BuildCost cost, @Nullable UnaryOperator<BlockSink> wrap, int fronts, BuildPlanner planner, @Nullable BlockPos focusWorld) {
		BuildJob job = new BuildJob(nextId.getAndIncrement(), label, dimension, origin, rotation, plan, blocksPerTick, cost, fronts, planner, focusWorld);
		if (wrap != null) {
			wraps.put(job.id(), wrap);
		}
		byId.put(job.id(), job);
		incoming.add(job);
		return job;
	}

	public @Nullable BuildJob job(int id) {
		return byId.get(id);
	}

	/** Jobs not yet finished (pending, running or paused), any thread. */
	public Collection<BuildJob> jobs() {
		return List.copyOf(byId.values());
	}

	/** Server thread. */
	public void tick(MinecraftServer server) {
		long tickStart = System.nanoTime();
		try {
			tickJobs(server);
		} finally {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_MANAGER_TICK, tickStart);
		}
	}

	private void tickJobs(MinecraftServer server) {
		BuildBudget budget = this.budget != null ? this.budget : BuildBudget.global();
		budget.beginTick(server.getTickCount(), server.getCurrentSmoothedTickTime());
		BuildJob polled;
		long t0 = System.nanoTime();
		boolean anyStarted = false;
		while ((polled = incoming.poll()) != null) {
			BuildJob job = polled;
			active.add(job);
			anyStarted = true;
			for (BuildListener l : listeners) {
				safe(() -> l.started(job));
			}
		}
		if (anyStarted) {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_STARTED, t0);
		}
		double[] allowance = allocate(budget);
		Iterator<BuildJob> it = active.iterator();
		int k = -1;
		while (it.hasNext()) {
			BuildJob job = it.next();
			double allow = allowance[++k];
			// Queued behind maxConcurrentJobs: stays PENDING until it gets a slot.
			if (!job.state().isTerminal() && !(allow <= 0 && job.state() == JobState.PENDING)) {
				ServerLevel level = server.getLevel(job.dimension());
				if (level != null) {
					BlockSink base = sinks.apply(level);
					UnaryOperator<BlockSink> wrap = wraps.get(job.id());
					BlockSink sink = wrap == null ? base : wrap.apply(base);
					try {
						if (job.radial()) {
							job.focusAt(focusOf(level, job)); // feat/build-radial: the centre follows the player
						}
						job.openSupply(server);
						job.tick(allow, sink, (jb, i, pos, state) -> {
							for (BuildListener l : listeners) {
								safe(() -> l.placed(jb, i, pos, state));
							}
						}, (jb, front, i, stand, ticks) -> {
							for (BuildListener l : listeners) {
								safe(() -> l.walking(jb, front, i, stand, ticks));
							}
						});
						if (job.pollMaterialsChanged()) {
							var missing = job.missingMaterials();
							for (BuildListener l : listeners) {
								safe(() -> l.materials(job, missing));
							}
						}
					} catch (RuntimeException e) {
						T3Craft.LOGGER.error("build job {} failed, cancelling", job, e);
						job.cancel();
					}
				}
			}
			JobState s = job.state();
			if (s.isTerminal()) {
				it.remove();
				byId.remove(job.id());
				wraps.remove(job.id());
				long f0 = System.nanoTime();
				for (BuildListener l : listeners) {
					safe(() -> l.finished(job, s));
				}
				dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_FINISHED, f0);
			}
		}
	}

	/**
	 * This tick's allowance per active job (same order as {@code active}): the first
	 * {@code maxConcurrentJobs} running jobs share what the global budget grants, each at most its own rate.
	 */
	private double[] allocate(BuildBudget budget) {
		List<JobState> states = new ArrayList<>(active.size());
		double[] rates = new double[active.size()];
		for (int i = 0; i < rates.length; i++) {
			states.add(active.get(i).state());
			rates[i] = active.get(i).rate();
		}
		return budget.allocate(RUNNER, states, rates);
	}

	/**
	 * feat/build-radial: the player a radial job centres on: the nearest non-spectator player of its level
	 * within {@code buildNearPlayer.radius} blocks (horizontally, 32 vertically) of the build's box; null =
	 * nobody in the build (or {@code buildNearPlayer.enabled} false): the job centres on the hub / spawn.
	 * A few multiplications per player and tick. fix/build-walkable: the exact position {@code {x, y, z}}
	 * (not the block), so the radial job can measure the player's velocity for its ground corridor.
	 */
	static double @Nullable [] focusOf(ServerLevel level, BuildJob job) {
		BuildTuning t = BuildBudget.global().tuning();
		if (!t.nearPlayer()) {
			return null;
		}
		net.minecraft.world.level.levelgen.structure.BoundingBox box = job.worldBounds();
		double r2 = (double) t.nearPlayerRadius() * t.nearPlayerRadius();
		double best = Double.MAX_VALUE;
		double[] out = null;
		for (net.minecraft.server.level.ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			double px = p.getX();
			double py = p.getY();
			double pz = p.getZ();
			double dx = Math.max(0, Math.max(box.minX() - px, px - (box.maxX() + 1)));
			double dz = Math.max(0, Math.max(box.minZ() - pz, pz - (box.maxZ() + 1)));
			double dy = Math.max(0, Math.max(box.minY() - py, py - (box.maxY() + 1)));
			double d = dx * dx + dz * dz;
			if (d <= r2 && dy <= 32 && d < best) {
				best = d;
				out = new double[] {px, py, pz};
			}
		}
		return out;
	}

	/** Cancels and forgets every job (server stopping / world change). Listeners are kept. */
	public void clear() {
		List<BuildJob> all = new ArrayList<>(active);
		BuildJob polled;
		while ((polled = incoming.poll()) != null) {
			all.add(polled);
		}
		active.clear();
		byId.clear();
		wraps.clear();
		for (BuildJob job : all) {
			job.cancel();
			for (BuildListener l : listeners) {
				safe(() -> l.finished(job, JobState.CANCELLED));
			}
		}
	}

	private static void safe(Runnable r) {
		try {
			r.run();
		} catch (RuntimeException e) {
			T3Craft.LOGGER.warn("build listener failed", e);
		}
	}
}
