package dev.t3craft.build;

import java.util.Arrays;

/**
 * Layer ordering for build plans, on plain ints (no Minecraft classes, so it is unit-tested):
 * bottom layer first ({@code y} ascending), inside a layer row by row ({@code z} ascending) and
 * snaking along {@code x} (ascending on even rows, descending on odd rows) so a builder sweeps
 * instead of jumping back to the start of each row. Stable: entries with the same position keep
 * their original relative order (e.g. "clear with air, then place" at the same spot).
 *
 * <p>fix/demo-reconcile-offthread: sorted on packed primitive {@code long} keys (key fields, then the
 * original index in the low bits, which keeps it stable) instead of a boxed {@code Integer[]} with a
 * chained comparator; falls back to that comparator only when the key ranges do not fit in 63 bits.
 */
public final class Layers {
	private Layers() {
	}

	/** Permutation of {@code 0..n-1}: {@code result[k]} is the original index placed k-th. */
	public static int[] order(int[] x, int[] y, int[] z) {
		int n = x.length;
		if (y.length != n || z.length != n) {
			throw new IllegalArgumentException("coordinate arrays differ in length");
		}
		int[] snake = new int[n];
		for (int i = 0; i < n; i++) {
			snake[i] = (z[i] & 1) == 0 ? x[i] : -x[i]; // same as floorMod(z, 2) == 0, negatives included
		}
		return sortByKeys(y, z, snake);
	}

	/**
	 * Stable permutation of {@code 0..n-1} ordering the entries by {@code keys[0]} ascending, then
	 * {@code keys[1]}, and so on: {@code result[k]} is the original index placed k-th. Every key array
	 * must have the same length.
	 */
	public static int[] sortByKeys(int[]... keys) {
		int n = keys.length == 0 ? 0 : keys[0].length;
		for (int[] k : keys) {
			if (k.length != n) {
				throw new IllegalArgumentException("key arrays differ in length");
			}
		}
		if (n == 0) {
			return new int[0];
		}
		int m = keys.length;
		long[] min = new long[m];
		int[] bits = new int[m];
		int idxBits = bitsFor(n - 1L);
		int total = idxBits;
		for (int j = 0; j < m; j++) {
			int[] k = keys[j];
			long lo = Long.MAX_VALUE;
			long hi = Long.MIN_VALUE;
			for (int v : k) {
				lo = Math.min(lo, v);
				hi = Math.max(hi, v);
			}
			min[j] = lo;
			bits[j] = bitsFor(hi - lo);
			total += bits[j];
		}
		if (total > 63) {
			return sortBoxed(keys, n);
		}
		long[] packed = new long[n];
		for (int i = 0; i < n; i++) {
			long p = 0;
			for (int j = 0; j < m; j++) {
				p = (p << bits[j]) | (keys[j][i] - min[j]);
			}
			packed[i] = (p << idxBits) | i;
		}
		Arrays.sort(packed); // all non-negative (at most 63 bits), so signed order is the key order
		long mask = idxBits == 0 ? 0 : (1L << idxBits) - 1;
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			out[i] = (int) (packed[i] & mask);
		}
		return out;
	}

	/** Bits needed to hold {@code 0..range} ({@code range >= 0}). */
	private static int bitsFor(long range) {
		return range <= 0 ? 0 : 64 - Long.numberOfLeadingZeros(range);
	}

	private static int[] sortBoxed(int[][] keys, int n) {
		Integer[] idx = new Integer[n];
		for (int i = 0; i < n; i++) {
			idx[i] = i;
		}
		Arrays.sort(idx, (a, b) -> {
			for (int[] k : keys) {
				int c = Integer.compare(k[a], k[b]);
				if (c != 0) {
					return c;
				}
			}
			return 0;
		}); // TimSort on objects: stable
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			out[i] = idx[i];
		}
		return out;
	}
}
