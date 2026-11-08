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

	DemoBuildRun(DemoStructureSource source, List<BuildPlan> plans, Path worldRoot, DemoConfig config, int delayTicks, Consumer<String> say,
			Runnable onComplete) {
		this.source = source;
		this.plans = List.copyOf(plans);
		this.schedule = PartSchedule.ofPlans(this.plans);
		this.finished = new boolean[this.plans.size()];
		this.running = new boolean[this.plans.size()];
		this.total = plans.size();
		this.worldRoot = worldRoot;
		this.config = config;
		this.delay = delayTicks;
		this.say = say;
		this.onComplete = onComplete;
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
		return current.isEmpty() ? null : current.values().iterator().next();
	}

	String describe() {
		StringBuilder sb = new StringBuilder();
		for (BuildHandle h : current.values()) {
			sb.append(String.format(Locale.ROOT, " %s %.0f%%", h.phase().name().toLowerCase(Locale.ROOT), h.progress() * 100));
		}
		return String.format(Locale.ROOT, "%s v%d, %d/%d part(s) done, %d crew(s)%s", source.id(), source.version(), completed, total, current.size(), sb);
	}

	void cancel() {
		current.values().forEach(BuildHandle::cancel);
		current.clear();
		finish();
	}

	private void finish() {
		done = true;
		BuildBudget.global().clientFps(-1);
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
		while (current.size() < crews) {
			int j = schedule.next(finished, running);
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
