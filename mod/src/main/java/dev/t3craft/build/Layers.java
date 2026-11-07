package dev.t3craft.build;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Layer ordering for build plans, on plain ints (no Minecraft classes, so it is unit-tested):
 * bottom layer first ({@code y} ascending), inside a layer row by row ({@code z} ascending) and
 * snaking along {@code x} (ascending on even rows, descending on odd rows) so a builder sweeps
 * instead of jumping back to the start of each row. Stable: entries with the same position keep
 * their original relative order (e.g. "clear with air, then place" at the same spot).
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
		Integer[] idx = new Integer[n];
		for (int i = 0; i < n; i++) {
			idx[i] = i;
		}
		Comparator<Integer> cmp = Comparator.<Integer>comparingInt(i -> y[i])
				.thenComparingInt(i -> z[i])
				.thenComparingInt(i -> Math.floorMod(z[i], 2) == 0 ? x[i] : -x[i]);
		Arrays.sort(idx, cmp); // TimSort on objects: stable
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			out[i] = idx[i];
		}
		return out;
	}
}
