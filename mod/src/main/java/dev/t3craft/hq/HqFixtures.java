package dev.t3craft.hq;

import org.jspecify.annotations.Nullable;

/**
 * Optional substitution of the HQ's "fixtures" (the blocks that show a thread's state: the desk
 * computers, their status lamps, the board's bulbs and the archived computers) after the plan is
 * generated. {@link HqGenerator} itself always produces the vanilla HQ (black concrete computers,
 * waxed copper bulbs); this hook lets another module swap those cells for its own blocks without
 * touching the generator.
 *
 * <p>The only implementation outside this package lives in {@code dev.t3craft.blocks} (custom
 * monitor and lamp blocks). When that module is disabled nobody calls {@link #use}, {@link #active()}
 * stays {@link #VANILLA} and every consumer ({@link HqPlans}, the preview command) gets exactly the
 * vanilla plan.
 *
 * <p>Contract: {@link #apply} must be pure and deterministic (same plan in → equal plan out), keep
 * every position (only states and block entity data may change, see {@link BuildPlan#replacing}),
 * and depend only on the plan, so idempotence and the per-group diffs of {@link HqPlans} keep
 * working.
 */
@FunctionalInterface
public interface HqFixtures {
	/** No substitution: the vanilla HQ. */
	HqFixtures VANILLA = plan -> plan;

	BuildPlan apply(BuildPlan plan);

	/** The fixtures in use (VANILLA unless a module installed its own). */
	static HqFixtures active() {
		HqFixtures f = Holder.active;
		return f == null ? VANILLA : f;
	}

	/** Installs {@code fixtures} (null = back to vanilla). Called once at startup by the blocks module. */
	static void use(@Nullable HqFixtures fixtures) {
		Holder.active = fixtures;
	}

	/** The single piece of mutable state; volatile because the server thread applies plans too. */
	final class Holder {
		private static volatile @Nullable HqFixtures active;

		private Holder() {
		}
	}
}
