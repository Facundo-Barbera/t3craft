package dev.t3craft.build;

import java.util.ArrayList;
import java.util.List;

/**
 * fix/hq-build-parallel: lets the parts of a big build (the HQ's groups: buildings, towers, blocks,
 * wings) go up <em>at the same time</em>, each with its own crew, without changing what ends up in the
 * world. Plain ints, unit-tested.
 *
 * <p>Each part has a box (inclusive min/max, x y z). Part {@code j} <b>depends on</b> every earlier
 * part {@code i < j} whose box, grown by {@link #MARGIN} on every side, meets its own. A part may start
 * once all its dependencies finished. So:
 * <ul>
 *   <li><b>Same result as one after another.</b> Two parts that write the same cell always overlap,
 *   so the later one runs after the earlier one, exactly as in sequential order; parts that run at
 *   the same time touch disjoint cells, and their order cannot matter. The margin also keeps them off
 *   each other's neighbours (attached blocks, doors and beds are placed with neighbour updates off
 *   anyway, see {@link ServerLevelSink#BUILD_FLAGS}).</li>
 *   <li><b>Fronts never meet.</b> Running parts are pairwise apart (box + margin); inside a part the
 *   {@link Fronts} lanes are disjoint as before.</li>
 *   <li><b>Deterministic.</b> {@link #next} always hands out the lowest ready index.</li>
 * </ul>
 */
public final class PartSchedule {
	/** Gap kept between parts that build at once (blocks). */
	public static final int MARGIN = 1;

	private final int[][] boxes;
	private final int[][] deps;

	private PartSchedule(int[][] boxes, int[][] deps) {
		this.boxes = boxes;
		this.deps = deps;
	}

	/** @param boxes per part {@code {minX, minY, minZ, maxX, maxY, maxZ}}, in build order */
	public static PartSchedule of(List<int[]> boxes) {
		int n = boxes.size();
		int[][] b = new int[n][];
		int[][] d = new int[n][];
		for (int j = 0; j < n; j++) {
			b[j] = boxes.get(j).clone();
			if (b[j].length != 6) {
				throw new IllegalArgumentException("box " + j + " needs 6 ints");
			}
			List<Integer> on = new ArrayList<>();
			for (int i = 0; i < j; i++) {
				if (near(b[i], b[j])) {
					on.add(i);
				}
			}
			d[j] = on.stream().mapToInt(Integer::intValue).toArray();
		}
		return new PartSchedule(b, d);
	}

	/** Schedule of builder plans (their bounding boxes). Empty plans get an empty box that meets nothing. */
	public static PartSchedule ofPlans(List<BuildPlan> plans) {
		List<int[]> out = new ArrayList<>(plans.size());
		for (BuildPlan p : plans) {
			if (p.isEmpty()) {
				out.add(new int[] {Integer.MAX_VALUE / 2, 0, 0, Integer.MAX_VALUE / 2 - 10, -1, -1});
			} else {
				out.add(new int[] {p.min().getX(), p.min().getY(), p.min().getZ(), p.max().getX(), p.max().getY(), p.max().getZ()});
			}
		}
		return of(out);
	}

	/** Whether {@code a} grown by {@link #MARGIN} meets {@code b}. */
	static boolean near(int[] a, int[] b) {
		for (int k = 0; k < 3; k++) {
			if (a[k] > a[k + 3] || b[k] > b[k + 3]) {
				return false; // empty
			}
			if (a[k] - MARGIN > b[k + 3] || b[k] > a[k + 3] + MARGIN) {
				return false;
			}
		}
		return true;
	}

	public int size() {
		return boxes.length;
	}

	/** Earlier parts that part {@code j} waits for. */
	public int[] dependencies(int j) {
		return deps[j].clone();
	}

	public int[] box(int j) {
		return boxes[j].clone();
	}

	/** Whether parts {@code i} and {@code j} may build at the same time (apart by more than the margin). */
	public boolean independent(int i, int j) {
		return i != j && !near(boxes[i], boxes[j]);
	}

	/**
	 * The lowest part that may start now: not finished, not running, every dependency finished;
	 * -1 when none. A part waiting for a running neighbour is passed over (later, independent parts
	 * may go first), which is what lets far-apart buildings rise together.
	 */
	public int next(boolean[] finished, boolean[] running) {
		return next(finished, running, null);
	}

	/**
	 * feat/build-near-player: like {@link #next(boolean[], boolean[])}, but among the ready parts the one
	 * with the lowest {@code rank} (e.g. nearest the player, {@link NearPriority}); null rank = lowest index.
	 * Dependencies are the same, so the world still ends as with the parts one after another.
	 */
	public int next(boolean[] finished, boolean[] running, int @org.jspecify.annotations.Nullable [] rank) {
		int best = -1;
		for (int j = 0; j < boxes.length; j++) {
			if (finished[j] || running[j]) {
				continue;
			}
			boolean ok = true;
			for (int i : deps[j]) {
				if (!finished[i]) {
					ok = false;
					break;
				}
			}
			if (!ok) {
				continue;
			}
			if (rank == null || j >= rank.length) {
				return j;
			}
			if (best < 0 || rank[j] < rank[best]) {
				best = j;
			}
		}
		return best;
	}
}
