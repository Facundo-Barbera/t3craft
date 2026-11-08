package dev.t3craft.build;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The global per-tick block budget, shared by every runner that writes blocks on the server thread
 * (the builders' {@link BuildManager}, the site's paced builder, the HQ's paced writer), plus a cap on
 * placement effects (sound + particles) per tick. Plain Java, unit-tested; the live instance is
 * {@link #global()} (this package's only static besides {@code Builders.service}).
 *
 * <p>Per tick: the first runner calls {@link #beginTick} (idempotent for the same tick number), then
 * each runner {@link #claim}s what its jobs want and splits the grant among them with {@link #share}.
 * A runner gets at most {@code budget / claimantsLastTick}, so one busy runner cannot starve another.
 * {@link #slots} decides which jobs may place at all ({@code maxConcurrentJobs}).
 *
 * <p>Auto-throttle: {@link TickGovernor} lowers the budget while the smoothed MSPT is over the target.
 */
public final class BuildBudget {
	private static volatile BuildBudget global = new BuildBudget(BuildTuning.DEFAULTS);

	private volatile BuildTuning tuning;
	private final TickGovernor governor = new TickGovernor();
	private long tick = Long.MIN_VALUE;
	private double remaining;
	private int effectsLeft;
	private int lastClaimants = 1;
	private final Set<String> claimants = new HashSet<>();
	private volatile int effectiveMax;
	/** fix/hq-build-parallel: frame-rate side of the auto-throttle (client FPS, integrated server). */
	private final TickGovernor fpsGovernor = new TickGovernor();
	private volatile double clientFps = -1;
	/** Placement work measured this tick ({@link #work}) and its running average, ns per placed block. */
	private long workNanos;
	private int workBlocks;
	private double nanosPerBlock;

	/**
	 * Wall-clock budget for block placement per server tick (ms), out of the 50 ms tick: the
	 * integrated server shares the CPU with the client. With the measured cost per block it caps the
	 * blocks per tick (light updates and chunk-section marking are part of the measured setBlock).
	 */
	public static final double MS_PER_TICK = 8.0;
	/** Below this client frame rate the budget drops (×0.7 per second); it recovers above {@link #RECOVER_FPS}. */
	public static final double MIN_FPS = 30;
	public static final double RECOVER_FPS = MIN_FPS / TickGovernor.RECOVER_BELOW;

	public BuildBudget(BuildTuning tuning) {
		this.tuning = tuning;
		this.effectiveMax = tuning.maxBlocksPerTick();
		this.remaining = effectiveMax;
		this.effectsLeft = tuning.maxEffectsPerTick();
	}

	/** The live budget. Never null. */
	public static BuildBudget global() {
		return global;
	}

	/** Replaces the live budget's settings (config reload). Null restores the defaults. */
	public static void configure(BuildTuning t) {
		global.tuning(t == null ? BuildTuning.DEFAULTS : t);
	}

	/** Re-reads the {@code build} section of {@code file} into the live budget; returns the warnings (key names only). */
	public static java.util.List<String> reload(java.nio.file.Path file) {
		BuildTuning.Result r = BuildTuning.load(file);
		configure(r.tuning());
		if (!r.warnings().isEmpty()) {
			dev.t3craft.T3Craft.LOGGER.warn("[build] invalid config keys, using defaults for: {}", r.warnings());
		}
		return r.warnings();
	}

	public BuildTuning tuning() {
		return tuning;
	}

	public synchronized void tuning(BuildTuning t) {
		tuning = t;
		effectiveMax = TickGovernor.apply(t.maxBlocksPerTick(), governor.factor());
		remaining = Math.min(remaining, effectiveMax);
	}

	/**
	 * Starts server tick {@code tickNumber} (no-op if already started) with the smoothed MSPT in ms
	 * (negative = unknown, keeps the current factor).
	 */
	public synchronized void beginTick(long tickNumber, double msptSmoothed) {
		if (tickNumber == tick) {
			return;
		}
		tick = tickNumber;
		BuildTuning t = tuning;
		double factor = msptSmoothed < 0 ? governor.factor() : governor.observe(msptSmoothed, t.targetMspt(), t.autoThrottle());
		// Frame time against 1000 / MIN_FPS; unknown FPS (no build watching it) lets it recover.
		double fps = clientFps;
		double frameMs = fps > 0 ? 1000.0 / fps : 0;
		factor = Math.min(factor, fpsGovernor.observe(frameMs, 1000.0 / MIN_FPS, t.autoThrottle()));
		if (workBlocks > 0) {
			double sample = (double) workNanos / workBlocks;
			nanosPerBlock = nanosPerBlock <= 0 ? sample : nanosPerBlock * 0.8 + sample * 0.2;
		}
		workNanos = 0;
		workBlocks = 0;
		effectiveMax = capByTime(TickGovernor.apply(t.maxBlocksPerTick(), factor), nanosPerBlock, t.autoThrottle());
		remaining = effectiveMax;
		effectsLeft = t.maxEffectsPerTick();
		lastClaimants = Math.max(1, claimants.size());
		claimants.clear();
	}

	/**
	 * Takes up to {@code want} blocks of this tick's budget for {@code runner}. Fractional amounts are
	 * fine (a job at 0.5 blocks/tick). Returns the grant (0 when the tick's budget is spent).
	 */
	public synchronized double claim(String runner, double want) {
		if (!(want > 0)) {
			return 0;
		}
		claimants.add(runner);
		double cap = (double) effectiveMax / lastClaimants;
		double g = Math.max(0, Math.min(want, Math.min(remaining, cap)));
		remaining -= g;
		return g;
	}

	/** Blocks per tick that fit {@link #MS_PER_TICK} at {@code nanosPerBlock} (unknown cost = no cap), at least one. */
	public static int capByTime(int blocks, double nanosPerBlock, boolean enabled) {
		if (!enabled || !(nanosPerBlock > 0)) {
			return blocks;
		}
		long fit = (long) Math.floor(MS_PER_TICK * 1_000_000.0 / nanosPerBlock);
		return (int) Math.max(1, Math.min(blocks, fit));
	}

	/** Reports placement work done this tick (server thread): wall time and blocks actually placed. */
	public synchronized void work(long nanos, int blocks) {
		if (nanos > 0 && blocks > 0) {
			workNanos += nanos;
			workBlocks += blocks;
		}
	}

	/** The client's current frame rate while a build is watched (client thread); ≤ 0 = unknown. */
	public void clientFps(double fps) {
		clientFps = fps;
	}

	/** Frame-rate throttle factor (1 = full budget). */
	public double fpsThrottle() {
		return fpsGovernor.factor();
	}

	/** Measured placement cost, ns per block (0 = not measured yet). */
	public synchronized double nanosPerBlock() {
		return nanosPerBlock;
	}

	/** One placement effect (sound + particles) allowed this tick? Counts it if so. */
	public synchronized boolean effect() {
		if (effectsLeft > 0) {
			effectsLeft--;
			return true;
		}
		return false;
	}

	/** This tick's budget after auto-throttle. */
	public int effectiveMax() {
		return effectiveMax;
	}

	/** Current auto-throttle factor (1 = full budget). */
	public double throttle() {
		return governor.factor();
	}

	/**
	 * Fair split of {@code pool} among wants (max-min fairness / water filling): nobody gets more than
	 * it wants, and what a small want leaves goes to the others in equal parts.
	 */
	public static double[] share(double pool, double[] wants) {
		int n = wants.length;
		double[] out = new double[n];
		boolean[] full = new boolean[n];
		double left = Math.max(0, pool);
		int open = 0;
		for (int i = 0; i < n; i++) {
			if (wants[i] > 0) {
				open++;
			} else {
				full[i] = true;
			}
		}
		while (open > 0 && left > 1e-9) {
			double each = left / open;
			boolean changed = false;
			for (int i = 0; i < n; i++) {
				if (!full[i] && wants[i] - out[i] <= each) {
					left -= wants[i] - out[i];
					out[i] = wants[i];
					full[i] = true;
					open--;
					changed = true;
				}
			}
			if (!changed) {
				for (int i = 0; i < n; i++) {
					if (!full[i]) {
						out[i] += each;
					}
				}
				left = 0;
			}
		}
		return out;
	}

	/**
	 * One runner's tick: the jobs holding a {@link #slots slot} claim their {@code rates} (blocks/tick)
	 * together and split the grant fairly ({@link #share}). Jobs without a slot get 0.
	 */
	public double[] allocate(String runner, List<JobState> states, double[] rates) {
		boolean[] slot = slots(states, tuning.maxConcurrentJobs());
		double[] wants = new double[rates.length];
		double sum = 0;
		for (int i = 0; i < wants.length; i++) {
			if (slot[i]) {
				wants[i] = Math.max(0, rates[i]);
				sum += wants[i];
			}
		}
		return share(claim(runner, sum), wants);
	}

	/**
	 * Which jobs may place this tick: the first {@code max} (submission order) that are
	 * {@link JobState#PENDING} or {@link JobState#RUNNING}. Paused jobs (by the player, or waiting for
	 * materials) do not hold a slot, so they never block the queue.
	 */
	public static boolean[] slots(List<JobState> states, int max) {
		boolean[] out = new boolean[states.size()];
		int used = 0;
		for (int i = 0; i < out.length && used < max; i++) {
			JobState s = states.get(i);
			if (s == JobState.PENDING || s == JobState.RUNNING) {
				out[i] = true;
				used++;
			}
		}
		return out;
	}
}
