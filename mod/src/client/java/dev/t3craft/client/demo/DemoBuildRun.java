package dev.t3craft.client.demo;

import dev.t3craft.build.BuildBudget;
import dev.t3craft.build.BuildHandle;
import dev.t3craft.build.BuildJob;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.BuildService;
import dev.t3craft.build.JobState;
import dev.t3craft.build.BuildTuning;
import dev.t3craft.build.PartSchedule;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * One demo build: the source's plans at the demo origin, each with its own animated crew.
 * fix/hq-build-parallel: parts that are far enough apart go up <b>at the same time</b> (up to
 * {@link #crews} crews, each part a {@link PartSchedule} slot): far-apart buildings, towers and blocks
 * rise together, while parts that share or touch cells keep their order, so the world ends exactly as
 * with one part after another. All crews draw from the global per-tick budget
 * ({@code BuildBudget}: blocks per tick, a time cap per tick and MSPT / FPS auto-throttle).
 *
 * <p>feat/build-radial ({@code build.order: "radial"}, the default): the parts are merged into one plan
 * (off-thread, later parts win a shared cell as they would one after another) and built by one crew
 * around the player, ring by ring ({@code RadialCursor}); {@code "parts"} keeps the part-by-part order
 * above.
 *
 * <p>The world marker is written only when every plan completed, so leaving mid-build (or
 * cancelling) means the next visit builds again; already placed blocks are skipped then (resume).
 * Client thread only.
 */
final class DemoBuildRun {
	/** Most crews at once (each one also a budget slot while it places). */
	static final int MAX_CREWS = 4;

	private final DemoStructureSource source;
	private final List<BuildPlan> plans;
	private final PartSchedule schedule;
	/** feat/build-near-player: ready parts nearest the player first (ranked off-thread, with hysteresis). */
	private final dev.t3craft.build.NearPriority near;
	private final boolean[] finished;
	private final boolean[] running;
	private final Map<Integer, BuildHandle> current = new LinkedHashMap<>();
	private final int total;
	private final Path worldRoot;
	private final DemoConfig config;
	private final Consumer<String> say;
	private final Runnable onComplete;
	private int delay;
	private int completed;
	private boolean done;
	/** feat/build-radial: chosen when the run starts ({@code build.order}); the merged plan, built off-thread. */
	private final boolean radial;
	private java.util.concurrent.@Nullable CompletableFuture<BuildPlan> merged;
	private @Nullable BuildHandle radialHandle;

	DemoBuildRun(DemoStructureSource source, List<BuildPlan> plans, Path worldRoot, DemoConfig config, int delayTicks, Consumer<String> say,
			Runnable onComplete) {
		this.source = source;
		this.plans = List.copyOf(plans);
		this.schedule = PartSchedule.ofPlans(this.plans);
		DemoWorldSpec sp = config.spec();
		java.util.List<int[]> boxes = new java.util.ArrayList<>(this.plans.size());
		for (BuildPlan p : this.plans) {
			boxes.add(p.isEmpty() ? new int[] {1, 0, 1, 0, -1, 0}
					: new int[] {sp.originX() + p.min().getX(), sp.originY() + p.min().getY(), sp.originZ() + p.min().getZ(),
							sp.originX() + p.max().getX(), sp.originY() + p.max().getY(), sp.originZ() + p.max().getZ()});
		}
		this.near = new dev.t3craft.build.NearPriority(boxes, BuildBudget.global().tuning().nearPlayerRadius());
		this.finished = new boolean[this.plans.size()];
		this.running = new boolean[this.plans.size()];
		this.total = plans.size();
		this.worldRoot = worldRoot;
		this.config = config;
		this.delay = delayTicks;
		this.say = say;
		this.onComplete = onComplete;
		this.radial = BuildBudget.global().tuning().radial() && this.plans.size() > 1;
	}

	/**
	 * feat/build-radial: one plan out of the parts, in build order; a cell written by several parts keeps
	 * the last part's state, so the world ends as with the parts one after another. Pure; off-thread (timed as {@code build.radial_merge_async}).
	 * fix/25-builder-unfinished-columns: each entry keeps the index of the part it came from as its work (obra), so the
	 * radial build can finish one building before it starts the next ({@code RadialCursor}, {@code WorkRank}).
	 */
	static BuildPlan merge(List<BuildPlan> plans) {
		BuildPlan.Builder b = BuildPlan.builder();
		for (int j = 0; j < plans.size(); j++) {
			for (dev.t3craft.build.Placement e : plans.get(j).placements()) {
				b.set(e.pos(), e.state(), e.blockEntity(), j);
			}
		}
		return b.build();
	}

	/** Crews that build at once: one more than the budget's job slots (it walks in while the others place), at most {@link #MAX_CREWS}. */
	static int crews(BuildTuning t) {
		return Math.max(1, Math.min(MAX_CREWS, t.maxConcurrentJobs() + 1));
	}

	/**
	 * Pace of one front: the configured {@code demo.blocksPerTick} is the floor; asking for the whole
	 * budget split over the fronts lets a crew that builds alone use all of it, while crews that build
	 * together share it fairly (max-min, {@code BuildBudget.share}).
	 */
	static double frontRate(DemoConfig c, BuildTuning t) {
		return Math.max(c.blocksPerTick(), (double) t.maxBlocksPerTick() / t.frontsFor(c.crew()));
	}

	boolean done() {
		return done;
	}

	/** One of the crews at work (status), or null. */
	@Nullable BuildHandle current() {
		if (radialHandle != null) {
			return radialHandle;
		}
		return current.isEmpty() ? null : current.values().iterator().next();
	}

	String describe() {
		if (radial) {
			BuildHandle h = radialHandle;
			BuildJob j = h == null ? null : h.job();
			String extra = j == null ? (merged != null ? " merging parts" : "") : " " + j.radialStatus();
			return String.format(Locale.ROOT, "%s v%d, radial, %d part(s), %s%s", source.id(), source.version(), total,
					h == null ? "starting" : String.format(Locale.ROOT, "%s %.0f%%", h.phase().name().toLowerCase(Locale.ROOT), h.progress() * 100), extra);
		}
		StringBuilder sb = new StringBuilder();
		for (BuildHandle h : current.values()) {
			sb.append(String.format(Locale.ROOT, " %s %.0f%%", h.phase().name().toLowerCase(Locale.ROOT), h.progress() * 100));
		}
		return String.format(Locale.ROOT, "%s v%d, %d/%d part(s) done, %d crew(s)%s", source.id(), source.version(), completed, total, current.size(), sb);
	}

	void cancel() {
		current.values().forEach(BuildHandle::cancel);
		current.clear();
		if (radialHandle != null) {
			radialHandle.cancel();
			radialHandle = null;
		}
		if (merged != null) {
			merged.cancel(false);
			merged = null;
		}
		finish();
	}

	private void finish() {
		done = true;
		BuildBudget.global().clientFps(-1);
	}

	/** feat/build-radial: merge the parts (off-thread), then one crew builds them around the player. */
	private void tickRadial(Minecraft mc, @Nullable BuildService service) {
		BuildHandle h = radialHandle;
		if (h != null) {
			if (h.phase() == BuildHandle.Phase.ARRIVING || h.phase() == BuildHandle.Phase.BUILDING) {
				return;
			}
			BuildJob job = h.job();
			radialHandle = null;
			if (job == null || job.state() != JobState.COMPLETED) {
				say.accept("HQ build stopped before finishing; it resumes next time you enter (or /t3craft demo rebuild)");
				cancel();
				return;
			}
			java.util.Arrays.fill(finished, true);
			completed = total;
			DemoMarkerFile.update(worldRoot, m -> m.withBuilt(source));
			say.accept("HQ built (" + source.id() + " v" + source.version() + ")");
			finish();
			onComplete.run();
			return;
		}
		if (merged == null) {
			List<BuildPlan> ps = plans;
			merged = dev.t3craft.build.Offload.supply(dev.t3craft.debug.PerfStats.STEP_RADIAL_MERGE, () -> merge(ps));
			return;
		}
		if (!merged.isDone()) {
			return;
		}
		if (service == null || !service.available() || mc.level == null || !Level.OVERWORLD.equals(mc.level.dimension())) {
			return; // wait: no builders yet, or the player is in another dimension
		}
		BuildPlan plan;
		try {
			plan = merged.join();
		} catch (RuntimeException e) {
			say.accept("HQ build could not start: " + e.getMessage());
			cancel();
			return;
		}
		DemoWorldSpec spec = config.spec();
		try {
			radialHandle = service.build("demo-" + source.id(), Level.OVERWORLD, new BlockPos(spec.originX(), spec.originY(), spec.originZ()), Rotation.NONE,
					plan, config.blocksPerTick(), config.crew(), BuildService.Billing.FREE);
		} catch (IllegalStateException e) {
			say.accept("HQ build could not start: " + e.getMessage());
			cancel();
		}
	}

	void tick(Minecraft mc, @Nullable BuildService service) {
		if (done) {
			return;
		}
		if (delay > 0) {
			delay--;
			return;
		}
		BuildBudget.global().clientFps(mc.getFps());
		if (radial) {
			tickRadial(mc, service);
			return;
		}
		for (var it = current.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			BuildHandle h = e.getValue();
			if (h.phase() == BuildHandle.Phase.ARRIVING || h.phase() == BuildHandle.Phase.BUILDING) {
				continue;
			}
			BuildJob job = h.job();
			if (job == null || job.state() != JobState.COMPLETED) {
				say.accept("HQ build stopped before finishing; it resumes next time you enter (or /t3craft demo rebuild)");
				it.remove();
				cancel();
				return;
			}
			it.remove();
			running[e.getKey()] = false;
			finished[e.getKey()] = true;
			completed++;
		}
		if (completed == total) {
			DemoMarkerFile.update(worldRoot, m -> m.withBuilt(source));
			say.accept("HQ built (" + source.id() + " v" + source.version() + ")");
			finish();
			onComplete.run();
			return;
		}
		if (service == null || !service.available() || mc.level == null || !Level.OVERWORLD.equals(mc.level.dimension())) {
			return; // wait: no builders yet, or the player is in another dimension
		}
		BuildTuning tuning = BuildBudget.global().tuning();
		int crews = crews(tuning);
		DemoWorldSpec spec = config.spec();
		// feat/build-near-player: cheap here (a future at most every few seconds); the ranking runs off-thread
		boolean steer = tuning.nearPlayer() && mc.player != null;
		near.update(steer ? mc.player.getX() : null, steer ? mc.player.getZ() : null, System.currentTimeMillis());
		while (current.size() < crews) {
			int j = near.next(schedule, finished, running);
			if (j < 0) {
				return;
			}
			try {
				BuildHandle h = service.build("demo-" + source.id() + "-" + (j + 1), Level.OVERWORLD,
						new BlockPos(spec.originX(), spec.originY(), spec.originZ()), Rotation.NONE, plans.get(j),
						frontRate(config, tuning), config.crew(), BuildService.Billing.FREE);
				current.put(j, h);
				running[j] = true;
			} catch (IllegalStateException e) {
				say.accept("HQ build could not start: " + e.getMessage());
				cancel();
				return;
			}
		}
	}
}
