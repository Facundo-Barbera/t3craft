package dev.t3craft.build;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a plan (its indices in order) into parallel <em>fronts</em> that keep the layer order, on
 * plain ints (unit-tested).
 *
 * <p>A <em>segment</em> is a maximal run of consecutive entries with the same {@code y} — the same
 * notion of "layer" as {@link MaterialMeter}. Segments are a barrier: no front starts segment
 * {@code s + 1} before every front finished segment {@code s}, so nothing is placed above before
 * what is below, and a plan that orders a layer differently (e.g. hanging blocks top-down) keeps
 * exactly its order between layers. Inside a segment the entries are cut into up to {@code fronts}
 * contiguous chunks of the plan order; with {@link Layers} ordering (rows by {@code z}, snaking in
 * {@code x}) each chunk is a band of rows, a compact area for one builder. Entries at the same
 * position (e.g. "clear with air, then place") are never split across fronts, so their relative
 * order holds. Every index lands in exactly one front, once.
 */
public final class Fronts {
	private final int[] segStart;
	private final int[][][] lanes;
	private final int[] frontOf;
	private final int fronts;
	private final int total;

	private Fronts(int[] segStart, int[][][] lanes, int[] frontOf, int fronts, int total) {
		this.segStart = segStart;
		this.lanes = lanes;
		this.frontOf = frontOf;
		this.fronts = fronts;
		this.total = total;
	}

	/**
	 * @param y      layer key of each entry, in plan order
	 * @param posKey position key of each entry (equal keys = same block position); null = all distinct
	 * @param fronts wanted fronts (≥ 1); small segments get fewer
	 */
	public static Fronts split(int[] y, long[] posKey, int fronts) {
		int n = y.length;
		if (posKey != null && posKey.length != n) {
			throw new IllegalArgumentException("y and posKey differ in length");
		}
		int f = Math.max(1, fronts);
		int[] frontOf = new int[n];
		List<Integer> starts = new ArrayList<>();
		List<int[][]> segs = new ArrayList<>();
		int i = 0;
		while (i < n) {
			int end = i + 1;
			while (end < n && y[end] == y[i]) {
				end++;
			}
			starts.add(i);
			segs.add(cut(i, end, f, posKey, frontOf));
			i = end;
		}
		int[] st = new int[starts.size()];
		for (int k = 0; k < st.length; k++) {
			st[k] = starts.get(k);
		}
		return new Fronts(st, segs.toArray(new int[0][][]), frontOf, f, n);
	}

	/** Cuts {@code [from, to)} into up to {@code f} contiguous lanes; empty lanes stay as empty arrays. */
	private static int[][] cut(int from, int to, int f, long[] posKey, int[] frontOf) {
		int len = to - from;
		int[][] out = new int[f][];
		int lo = from;
		for (int k = 0; k < f; k++) {
			int hi = k == f - 1 ? to : from + (int) Math.round((double) len * (k + 1) / f);
			hi = Math.max(hi, lo);
			// Keep entries at one position together (they are adjacent in a layered plan).
			while (posKey != null && hi > lo && hi < to && posKey[hi] == posKey[hi - 1]) {
				hi++;
			}
			int[] lane = new int[hi - lo];
			for (int j = lo; j < hi; j++) {
				lane[j - lo] = j;
				frontOf[j] = k;
			}
			out[k] = lane;
			lo = hi;
		}
		return out;
	}

	/** Fronts built from a {@link BuildPlan}, segments by plan-relative y. */
	public static Fronts of(BuildPlan plan, int fronts) {
		int n = plan.size();
		int[] y = new int[n];
		long[] key = new long[n];
		for (int i = 0; i < n; i++) {
			var p = plan.get(i).pos();
			y[i] = p.getY();
			key[i] = p.asLong();
		}
		return split(y, key, fronts);
	}

	public int segments() {
		return lanes.length;
	}

	/** Lanes of segment {@code s}: {@code lanes(s)[front]} = plan indices in order (maybe empty). */
	public int[][] lanes(int s) {
		return lanes[s];
	}

	/** First plan index of segment {@code s}. */
	public int segmentStart(int s) {
		return segStart[s];
	}

	/** The front that places plan index {@code i}. */
	public int frontOf(int i) {
		return frontOf[i];
	}

	/** Number of fronts asked for (lanes per segment). */
	public int fronts() {
		return fronts;
	}

	public int total() {
		return total;
	}
}
