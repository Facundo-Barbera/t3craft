package dev.t3craft.mapgen.islands.bridge;

import org.jspecify.annotations.Nullable;

/**
 * The height of a deck along its steps, made so a player walks it without jumping. Step {@code 0} meets island
 * {@code a}'s edge (grass at {@code ya}), step {@code n - 1} island {@code b}'s (grass at {@code yb}); both end
 * steps are level with the grass.
 *
 * <ul>
 *   <li><b>Half-block mode</b> (when the rise fits in half-block steps, {@code |2·(yb - ya)| ≤ n - 1}): the
 *       walking surface goes up or down at most half a block per step, with full blocks on whole heights and
 *       bottom slabs on the halves. A suspension deck also sags in the middle ({@code sagHalves}), dropped
 *       when the sag would need a bigger step.</li>
 *   <li><b>Stair mode</b> (steeper): whole blocks, at most one per step, the step where the height changes is a
 *       stair facing uphill.</li>
 * </ul>
 * Pure.
 */
public final class DeckProfile {
	/** What block a deck step is made of. Stairs are named by the way they rise when walking from a to b. */
	public enum Kind {
		FULL, SLAB, STAIR_UP, STAIR_DOWN
	}

	private final int[] floor;
	private final Kind[] kind;
	private final boolean stairs;

	private DeckProfile(int[] floor, Kind[] kind, boolean stairs) {
		this.floor = floor;
		this.kind = kind;
		this.stairs = stairs;
	}

	/** Whether a deck of {@code n} steps between grass at {@code ya} and {@code yb} needs stairs. */
	public static boolean needsStairs(int n, int ya, int yb) {
		return Math.abs(2 * (yb - ya)) > Math.max(0, n - 1);
	}

	/** The profile of a deck of {@code n ≥ 1} steps from grass at {@code ya} to grass at {@code yb}. */
	public static DeckProfile of(int n, int ya, int yb, int sagHalves) {
		if (n <= 1) {
			return new DeckProfile(new int[] {ya}, new Kind[] {Kind.FULL}, false);
		}
		int s0 = 2 * (ya + 1);
		int s1 = 2 * (yb + 1);
		int @Nullable [] s = halves(n, s0, s1, sagHalves);
		if (s == null && sagHalves > 0) {
			s = halves(n, s0, s1, 0);
		}
		if (s != null) {
			int[] floor = new int[n];
			Kind[] kind = new Kind[n];
			for (int i = 0; i < n; i++) {
				boolean whole = Math.floorMod(s[i], 2) == 0;
				floor[i] = whole ? Math.floorDiv(s[i], 2) - 1 : Math.floorDiv(s[i], 2);
				kind[i] = whole ? Kind.FULL : Kind.SLAB;
			}
			return new DeckProfile(floor, kind, false);
		}
		int[] floor = new int[n];
		Kind[] kind = new Kind[n];
		for (int i = 0; i < n; i++) {
			floor[i] = ya + (int) Math.round((double) (yb - ya) * i / (n - 1));
			kind[i] = Kind.FULL;
		}
		for (int i = 1; i < n; i++) {
			if (floor[i] > floor[i - 1]) {
				kind[i] = Kind.STAIR_UP;
			} else if (floor[i] < floor[i - 1]) {
				kind[i - 1] = Kind.STAIR_DOWN;
			}
		}
		return new DeckProfile(floor, kind, true);
	}

	/** Walking surface per step in half blocks, or null when some step would rise or fall more than half a block. */
	private static int @Nullable [] halves(int n, int s0, int s1, int sag) {
		int[] s = new int[n];
		for (int i = 0; i < n; i++) {
			double t = (double) i / (n - 1);
			s[i] = s0 + (int) Math.round((s1 - s0) * t) - (int) Math.round(sag * 4 * t * (1 - t));
			if (i > 0 && Math.abs(s[i] - s[i - 1]) > 1) {
				return null;
			}
		}
		return s;
	}

	public int length() {
		return floor.length;
	}

	/** y of the deck block of step {@code i}. */
	public int floor(int i) {
		return floor[i];
	}

	public Kind kind(int i) {
		return kind[i];
	}

	/** Whether the deck climbs with stairs (whole-block steps) rather than slabs. */
	public boolean stairs() {
		return stairs;
	}
}
