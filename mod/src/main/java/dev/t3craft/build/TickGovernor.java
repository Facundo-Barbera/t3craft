package dev.t3craft.build;

/**
 * Auto-throttle of the global build budget from the measured server tick time (MSPT), plain Java.
 * Every {@link #INTERVAL_TICKS} ticks it looks at the smoothed MSPT: above the target the budget
 * factor drops quickly (×{@link #DOWN}); comfortably below it ({@code < target × }{@link #RECOVER_BELOW})
 * it climbs back slowly (+{@link #UP}) up to 1. In between it holds, so it does not oscillate.
 * The factor never goes under {@link #MIN_FACTOR}; the resulting budget is never under one block.
 */
public final class TickGovernor {
	public static final int INTERVAL_TICKS = 20;
	public static final double DOWN = 0.7;
	public static final double UP = 0.1;
	public static final double RECOVER_BELOW = 0.75;
	public static final double MIN_FACTOR = 1.0 / 16;

	private double factor = 1.0;
	private int sinceCheck;

	/**
	 * One server tick with the smoothed MSPT; {@code enabled = false} resets to full budget.
	 * Returns the current factor (0..1].
	 */
	public double observe(double msptSmoothed, double targetMspt, boolean enabled) {
		if (!enabled) {
			factor = 1.0;
			sinceCheck = 0;
			return factor;
		}
		if (++sinceCheck < INTERVAL_TICKS) {
			return factor;
		}
		sinceCheck = 0;
		if (!(msptSmoothed >= 0) || Double.isInfinite(msptSmoothed)) {
			return factor;
		}
		if (msptSmoothed > targetMspt) {
			factor = Math.max(MIN_FACTOR, factor * DOWN);
		} else if (msptSmoothed < targetMspt * RECOVER_BELOW) {
			factor = Math.min(1.0, factor + UP);
		}
		return factor;
	}

	public double factor() {
		return factor;
	}

	/** Budget after throttling: {@code max × factor}, at least one block per tick. */
	public static int apply(int max, double factor) {
		return Math.max(1, (int) Math.floor(max * factor));
	}
}
