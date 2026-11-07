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
		effectiveMax = TickGovernor.apply(t.maxBlocksPerTick(), factor);
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
