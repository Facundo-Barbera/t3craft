package dev.t3craft.build;

/**
 * fix/hq-build-parallel: <b>which entry each builder places next</b>, as a pluggable strategy, apart from
 * the per-tick budget and the pause / block / counter handling of {@link FrontCursor} (which drives any
 * order the same way) and from which parts run at once ({@link PartSchedule}).
 *
 * <p>A "front" is one worker (one builder of the crew). The cursor asks {@link #peek} for each front in
 * turn and calls {@link #advance} once that entry was applied (placed, skipped or failed); a blocked
 * entry is simply peeked again later. Contract:
 * <ul>
 *   <li>every plan index {@code 0..total()-1} is handed out by {@code peek} and advanced <b>exactly once</b>
 *   (whatever the pauses, blocks and allowances);</li>
 *   <li>two fronts never get the same index (reservations are the order's business);</li>
 *   <li>{@link #peek} returns {@code -1} when that front has nothing to do right now (waiting for others,
 *   e.g. a layer barrier, or nothing left in its zone); {@link #done()} once everything was advanced;</li>
 *   <li>entries at one position keep their plan order (e.g. "clear with air, then place");</li>
 *   <li>{@link #stage()} grows whenever new work opens up (next layer, newly supported blocks): blocked
 *   fronts retry and survival re-checks materials for {@link #pendingInStage()}.</li>
 * </ul>
 * Single-threaded (server thread). Default: {@link LayeredOrder} (bottom layer first, layer barrier,
 * one band of rows per front). Install another with {@link PlacementOrders#use}.
 */
public interface PlacementOrder {
	/** Builds the order of one job. */
	@FunctionalInterface
	interface Factory {
		/** @param fronts wanted workers (≥ 1); the order may use fewer */
		PlacementOrder create(BuildPlan plan, int fronts);
	}

	/** Workers (lanes) this order hands work to; {@code peek}/{@code advance} take {@code 0..fronts()-1}. */
	int fronts();

	/** Plan entries in all. */
	int total();

	/** Next plan index for {@code front}, or -1 when it has nothing to do now. Repeated calls return the same index until {@link #advance}. */
	int peek(int front);

	/** The index {@link #peek} gave {@code front} was applied. */
	void advance(int front);

	/** Every entry advanced. */
	boolean done();

	/** Grows when new work opens up (blocked fronts retry; materials are re-checked). */
	int stage();

	/** Indices open in the current stage and not advanced yet, sorted ascending (materials check). */
	int[] pendingInStage();

	/** The front that places (or placed) plan index {@code i}, for the builder animation. */
	int frontOf(int i);
}
