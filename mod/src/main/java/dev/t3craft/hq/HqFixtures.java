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

	/**
	 * Short stable name of what this substitution places (goes into build hashes and markers, so a
	 * world built with other fixtures is detected and updated). Bump it when the output changes.
	 */
	default String id() {
		return "custom";
	}

	/** {@link #id()} of the active fixtures, {@code "vanilla"} when none is installed. */
	static String fingerprint() {
		return fingerprint(active());
	}

	/** {@link #id()} of {@code f}, {@code "vanilla"} for {@link #VANILLA}. */
	static String fingerprint(HqFixtures f) {
		return f == VANILLA ? "vanilla" : f.id();
	}

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
