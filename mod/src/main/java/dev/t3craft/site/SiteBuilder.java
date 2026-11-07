package dev.t3craft.site;

import java.util.List;
import java.util.function.Consumer;

/**
 * Minimal build hand-off: {@code submit(plan, origin)}. The site has already validated the box,
 * saved a durable snapshot and the plan; the builder only places blocks.
 *
 * <p>Contract for implementations (the instant one here, the animated NPC builders of
 * {@code feat/builders} later):
 * <ul>
 *   <li>write only cells of {@code plan.box()} whose plan value is non-null;</li>
 *   <li>right before writing a cell, ask {@code guard.mayWrite(x, y, z)}; when false the player
 *   changed it after the snapshot: skip it (count it in {@link Report#skipped}), never overwrite;</li>
 *   <li>be idempotent: a cell that already holds its plan value is not rewritten (so a build can be
 *   re-submitted after a crash);</li>
 *   <li>call {@code done} exactly once, on the server thread, when finished or cancelled.</li>
 * </ul>
 */
public interface SiteBuilder {
	/** Asked per cell before writing it. */
	@FunctionalInterface
	interface CellGuard {
		boolean mayWrite(int x, int y, int z);
	}

	record Report(int placed, int skipped, int alreadyThere, boolean cancelled, List<String> skippedSample) {
	}

	/** Running build. */
	interface Handle {
		boolean finished();

		/** Stops after the current cell; {@code done} is called with {@code cancelled = true}. */
		void cancel();
	}

	Handle submit(CellPlan plan, CellPlan.Origin origin, CellGuard guard, Consumer<Report> done);
}
