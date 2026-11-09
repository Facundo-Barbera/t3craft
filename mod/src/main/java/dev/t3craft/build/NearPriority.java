package dev.t3craft.build;

import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * feat/build-near-player: which ready part of a multi-part build goes next, nearest the player first
 * ({@code buildNearPlayer}). Plain Java; one instance per build run, used from one thread (the client
 * thread of the demo).
 *
 * <ul>
 *   <li><b>Rarely recomputed, off-thread.</b> The ranking (every part by distance from the player to its
 *   box) is computed on an {@link Offload} worker, timed as {@code build.priority_async}, only when the
 *   player moved more than {@link #MOVE} blocks since the last ranking, and at most every
 *   {@link #INTERVAL_MS}. The game thread only reads the last finished ranking.</li>
 *   <li><b>Hysteresis.</b> Distances are bucketed by {@link #BUCKET} blocks (equal buckets keep the build
 *   order), and the focus only moves after {@link #MOVE} blocks, so the choice does not flip while the
 *   player walks about; a running part is never stopped.</li>
 *   <li><b>Dependencies first.</b> Only parts {@link PartSchedule#next ready} now are candidates (support
 *   from below, the void island before what stands on it).</li>
 *   <li>No player, or the player farther than the radius from every part: the plain build order.</li>
 * </ul>
 */
public final class NearPriority {
	public static final long INTERVAL_MS = 3_000;
	public static final double MOVE = 16;
	public static final double BUCKET = 8;

	private final int[][] boxes;
	private final int radius;
	private double fx = Double.NaN;
	private double fz = Double.NaN;
	private long lastAt = Long.MIN_VALUE;
	private int @Nullable [] rank;
	private @Nullable CompletableFuture<int @Nullable []> pending;

	/**
	 * @param boxes  world boxes of the parts {@code {minX, minY, minZ, maxX, maxY, maxZ}}, in build order
	 * @param radius beyond this (blocks, horizontal) from every part the player does not steer the order
	 */
	public NearPriority(java.util.List<int[]> boxes, int radius) {
		this.boxes = new int[boxes.size()][];
		for (int j = 0; j < this.boxes.length; j++) {
			this.boxes[j] = boxes.get(j).clone();
		}
		this.radius = Math.max(1, radius);
	}

	/** The player's position now (null = no player / feature off). Cheap: at most a future started. */
	public void update(@Nullable Double x, @Nullable Double z, long nowMs) {
		CompletableFuture<int @Nullable []> p = pending;
		if (p != null && p.isDone()) {
			pending = null;
			try {
				rank = p.join();
			} catch (RuntimeException e) {
				rank = null;
			}
		}
		if (x == null || z == null) {
			rank = null;
			fx = Double.NaN;
			return;
		}
		boolean moved = Double.isNaN(fx) || Math.hypot(x - fx, z - fz) > MOVE;
		if (!moved || pending != null || nowMs - lastAt < INTERVAL_MS && !Double.isNaN(fx)) {
			return;
		}
		fx = x;
		fz = z;
		lastAt = nowMs;
		int[][] b = boxes;
		int r = radius;
		double px = x;
		double pz = z;
		pending = Offload.supply(dev.t3craft.debug.PerfStats.STEP_PRIORITY_ASYNC, () -> ranking(b, px, pz, r));
	}

	/** Rank per part (lower goes first), or null when the player is beyond {@code radius} of every part. Pure. */
	static int @Nullable [] ranking(int[][] boxes, double px, double pz, int radius) {
		int n = boxes.length;
		long[] keys = new long[n];
		boolean anyNear = false;
		for (int j = 0; j < n; j++) {
			int[] b = boxes[j];
			double d;
			if (b[0] > b[3] || b[2] > b[5]) {
				d = Double.MAX_VALUE / 4; // empty part
			} else {
				double dx = Math.max(0, Math.max(b[0] - px, px - (b[3] + 1)));
				double dz = Math.max(0, Math.max(b[2] - pz, pz - (b[5] + 1)));
				d = Math.sqrt(dx * dx + dz * dz);
				anyNear |= d <= radius;
			}
			long bucket = (long) Math.min(1_000_000, Math.floor(d / BUCKET));
			keys[j] = (bucket << 32) | j;
		}
		if (!anyNear) {
			return null;
		}
		java.util.Arrays.sort(keys);
		int[] rank = new int[n];
		for (int r = 0; r < n; r++) {
			rank[(int) keys[r]] = r;
		}
		return rank;
	}

	/** The next part to start: the ready one ranked first; the plain order without a ranking. -1 = none ready. */
	public int next(PartSchedule schedule, boolean[] finished, boolean[] running) {
		int[] r = rank;
		return r == null || r.length != schedule.size() ? schedule.next(finished, running) : schedule.next(finished, running, r);
	}
}
