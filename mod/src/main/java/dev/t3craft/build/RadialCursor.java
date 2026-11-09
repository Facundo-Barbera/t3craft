package dev.t3craft.build;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntPredicate;
import net.minecraft.world.level.block.FallingBlock;
import org.jspecify.annotations.Nullable;

/**
 * feat/build-radial: builds a free {@link BuildPlanner#CREW} job <b>around a moving centre</b> (the
 * player) instead of part by part: every column ({@code x, z}) of the plan gets a priority from its
 * horizontal distance to the centre, rounded to rings of {@code build.radialRing} blocks (5 → ring 0 is
 * everything within 5 blocks, ring 1 within 10, …), and the columns of the nearest ring go first.
 * When the centre moves the order is recomputed off-thread ({@link Offload}, {@code build.radial_order_async})
 * and what was pending elsewhere waits for later.
 *
 * <p><b>Inside a column, always bottom-up</b>, and an entry goes only once it is supported, so nothing
 * floats:
 * <ul>
 *   <li>the lowest entry of a column rests on the terrain (or is the bottom of the void island);</li>
 *   <li>above it, the cell below must hold a placed solid entry of the plan, or, where the column has a
 *   gap (a roof over a hall, a bridge, a balcony), one of the four side cells (and for decoration the cell
 *   above too: hanging lanterns) must;</li>
 *   <li>falling blocks (sand, gravel, concrete powder) need the cell below;</li>
 *   <li>air entries (clearing) need nothing and support nothing.</li>
 * </ul>
 * A column whose head is not supported <b>stalls</b> (its entries above wait too) and wakes when a
 * neighbour cell of its head is placed. Stalled columns never hold the expansion back. If every column
 * left is stalled (a plan with truly floating pieces) the nearest one is forced, as the crew planner does.
 *
 * <p><b>Two streams</b> share the column order, each with its own per-tick budget:
 * <ol>
 *   <li><b>Foundation</b> ({@link Foundation}: terrain, the void island, floors and fill, plus air
 *   entries and entries already right in the world): no builder, {@link #frontOf} is -1, so no animation
 *   and no client event. Budget {@link BuildBudget#foundationBlocks} ({@code build.foundationBlocksPerTick},
 *   {@code build.foundationMsPerTick}, MSPT / FPS throttle).</li>
 *   <li><b>Builders</b>: everything else (walls, roofs, decoration). Each builder claims the free column of
 *   the current ring nearest to where it stands (a neighbouring ring when its ring has no free column left),
 *   walks to a spot beside it ({@link Walker}), places the column's entries bottom-up at
 *   {@code perBuilder} blocks per tick at most (raising its spot as the column grows, as if on scaffolding)
 *   and moves on once the column is done, stalled or its head is foundation again. They draw from the
 *   crews' budget ({@link BuildBudget#claim}) as every crew does.</li>
 * </ol>
 *
 * <p>Game-thread cost per tick: bounded scans ({@link #SCAN_MAX} columns per stream, {@link #PICK_SCAN} per
 * pick) over flat arrays, a handful of hash lookups per placed entry (support and wake-ups), and nothing
 * else; the plan analysis ({@link #plan}) runs off-thread in the job's {@link DeferredCursor}.
 * Plain Java, no world access (the world is read only through the {@code already} probe).
 */
public final class RadialCursor implements JobCursor {
	/** Columns a stream looks at per tick (besides the ones it works on). */
	static final int SCAN_MAX = 8192;
	/** World reads ({@code already} probe) the foundation stream makes per tick for builder heads. */
	static final int PROBES_MAX = 1024;
	/** Columns a builder looks at when it picks its next column. */
	static final int PICK_SCAN = 1024;
	/** The centre moved this far (blocks) since the last order: recompute it. */
	public static final double MOVE = 4.0;
	/** ...or this long passed and it moved at least {@link #DRIFT} blocks. */
	public static final long INTERVAL_MS = 3_000;
	public static final double DRIFT = 1.0;
	/** Never two orders closer than this. */
	public static final long MIN_GAP_MS = 750;
	/** A builder raises its spot once the column's head is this far above its feet. */
	static final int RAISE_ABOVE = 3;

	/** Builder {@code front} starts walking to plan-relative feet cell {@code stand} to apply {@code index}, arriving in {@code ticks}. */
	@FunctionalInterface
	public interface Walker {
		void walking(int front, int index, int[] stand, int ticks);
	}

	// ---- static plan data (built off-thread, read-only afterwards) ----------------------------
	private final int n;
	private final int[] x;
	private final int[] y;
	private final int[] z;
	private final boolean[] air;
	private final boolean[] decor;
	private final boolean[] falling;
	/** Placed by the foundation stream (foundation or air). */
	private final boolean[] fast;
	private final Long2IntOpenHashMap cellAt;
	private final Long2IntOpenHashMap colAt;
	private final int cols;
	private final int[] colX;
	private final int[] colZ;
	private final int[] colStart;
	private final int[] colEntries;
	private final int[] colOf;
	private final int ring;
	private final int[] fallback;

	// ---- dynamic state (server thread) --------------------------------------------------------
	private final int[] colNext;
	private final boolean[] stalled;
	/** Head entry of each column already found supported (support never goes away); -1 = not checked. */
	private final int[] okHead;
	private final int[] colTop;
	private final int[] colClaim;
	private final boolean[] applied;
	private final boolean[] forced;
	private final int[] by;
	private int colsDone;
	private int[] order;
	private int[] posOf;
	private int[] ringAt;
	private int fPtr;
	private int bPtr;
	private int fWake = Integer.MAX_VALUE;
	private int bWake = Integer.MAX_VALUE;

	private final int builders;
	private final double perBuilder;
	private final int[] tcol;
	private final int[] walkLeft;
	private final int[] px;
	private final int[] py;
	private final int[] pz;
	private double credit;
	private int rr;

	private @Nullable IntPredicate already;
	private @Nullable Walker walker;

	// ---- centre (focus): written by the server thread, ordered off-thread ---------------------
	private double cx;
	private double cz;
	private double sortedX;
	private double sortedZ;
	private long sortedAt = Long.MIN_VALUE / 2;
	private @Nullable CompletableFuture<int[][]> pendingOrder;

	private volatile int done;
	private volatile int placed;
	private volatile int skipped;
	private volatile int failed;
	private volatile JobState state = JobState.PENDING;

	private RadialCursor(BuildPlan plan, Foundation.@Nullable Split split, int builders, double perBuilder, int ring, int @Nullable [] focus) {
		this.n = plan.size();
		this.ring = Math.max(1, ring);
		this.builders = Math.max(1, builders);
		this.perBuilder = Math.max(1.0, perBuilder);
		x = new int[n];
		y = new int[n];
		z = new int[n];
		air = new boolean[n];
		decor = new boolean[n];
		falling = new boolean[n];
		fast = new boolean[n];
		cellAt = new Long2IntOpenHashMap(Math.max(16, n));
		cellAt.defaultReturnValue(-1);
		colAt = new Long2IntOpenHashMap();
		colAt.defaultReturnValue(-1);
		int[] colCount = new int[Math.max(16, n / 4)];
		int[] cxs = new int[colCount.length];
		int[] czs = new int[colCount.length];
		colOf = new int[n];
		int nc = 0;
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (int i = 0; i < n; i++) {
			Placement p = plan.get(i);
			x[i] = p.pos().getX();
			y[i] = p.pos().getY();
			z[i] = p.pos().getZ();
			air[i] = p.state().isAir();
			decor[i] = !air[i] && !p.state().canOcclude();
			falling[i] = p.state().getBlock() instanceof FallingBlock;
			cellAt.put(CrewPlanner.key(x[i], y[i], z[i]), i); // a later entry at the same cell wins (as in plan order)
			long ck = CrewPlanner.key(x[i], 0, z[i]);
			int c = colAt.get(ck);
			if (c < 0) {
				c = nc++;
				if (c >= colCount.length) {
					int len = colCount.length * 2;
					colCount = Arrays.copyOf(colCount, len);
					cxs = Arrays.copyOf(cxs, len);
					czs = Arrays.copyOf(czs, len);
				}
				colAt.put(ck, c);
				cxs[c] = x[i];
				czs[c] = z[i];
			}
			colOf[i] = c;
			colCount[c]++;
			minX = Math.min(minX, x[i]);
			maxX = Math.max(maxX, x[i]);
			minZ = Math.min(minZ, z[i]);
			maxZ = Math.max(maxZ, z[i]);
		}
		if (split != null) {
			for (int i : split.foundation()) {
				fast[i] = true;
			}
		}
		for (int i = 0; i < n; i++) {
			fast[i] |= air[i];
		}
		cols = nc;
		colX = Arrays.copyOf(cxs, nc);
		colZ = Arrays.copyOf(czs, nc);
		colStart = new int[nc + 1];
		for (int c = 0; c < nc; c++) {
			colStart[c + 1] = colStart[c] + colCount[c];
		}
		colEntries = new int[n];
		int[] fill = Arrays.copyOf(colStart, nc);
		for (int i = 0; i < n; i++) {
			colEntries[fill[colOf[i]]++] = i;
		}
		// bottom-up inside each column (stable: plan order for the same height)
		long[] tmp = new long[0];
		for (int c = 0; c < nc; c++) {
			int a = colStart[c];
			int b = colStart[c + 1];
			if (b - a < 2) {
				continue;
			}
			if (tmp.length < b - a) {
				tmp = new long[Math.max(b - a, tmp.length * 2)];
			}
			for (int k = a; k < b; k++) {
				int i = colEntries[k];
				tmp[k - a] = ((long) (y[i] + (1 << 20)) << 32) | i;
			}
			Arrays.sort(tmp, 0, b - a);
			for (int k = a; k < b; k++) {
				colEntries[k] = (int) tmp[k - a];
			}
		}
		// where the build centres while no player is in it: the given focus, else the origin when it is
		// inside the footprint (the demo's hub), else the middle of the footprint
		if (focus != null) {
			fallback = new int[] {focus[0], focus[1]};
		} else if (n > 0 && minX <= 0 && 0 <= maxX && minZ <= 0 && 0 <= maxZ) {
			fallback = new int[] {0, 0};
		} else {
			fallback = new int[] {n == 0 ? 0 : (minX + maxX) / 2, n == 0 ? 0 : (minZ + maxZ) / 2};
		}
		colNext = new int[nc];
		stalled = new boolean[nc];
		okHead = new int[nc];
		Arrays.fill(okHead, -1);
		colTop = new int[nc];
		Arrays.fill(colTop, Integer.MIN_VALUE);
		colClaim = new int[nc];
		Arrays.fill(colClaim, -1);
		applied = new boolean[n];
		forced = new boolean[n];
		by = new int[n];
		Arrays.fill(by, -1);
		cx = fallback[0];
		cz = fallback[1];
		sortedX = cx;
		sortedZ = cz;
		int[][] o = order(colX, colZ, cx, cz, this.ring);
		order = o[0];
		ringAt = o[1];
		posOf = positions(order);
		tcol = new int[this.builders];
		Arrays.fill(tcol, -1);
		walkLeft = new int[this.builders];
		Arrays.fill(walkLeft, -1);
		px = new int[this.builders];
		py = new int[this.builders];
		pz = new int[this.builders];
		int baseY = n == 0 ? 0 : y[colEntries[0]];
		for (int f = 0; f < this.builders; f++) {
			px[f] = (int) Math.round(cx) + (f % 3) - 1;
			py[f] = baseY;
			pz[f] = (int) Math.round(cz) + (f / 3 % 3) - 1;
		}
	}

	/**
	 * Analyses {@code plan} (off-thread: {@link DeferredCursor}): the foundation split, columns, bottom-up
	 * order and the first ring order around {@code focus} (plan-relative {@code {x, z}}, null = the hub).
	 *
	 * @param builders   builders (fronts) of the crew
	 * @param perBuilder most blocks one builder places per tick
	 * @param ring       ring width, blocks ({@code build.radialRing})
	 * @param fastFoundation lay the foundation without builders ({@code build.fastFoundation}); false = builders do everything but air
	 */
	static RadialCursor plan(BuildPlan plan, int builders, double perBuilder, int ring, int @Nullable [] focus, boolean fastFoundation) {
		long t0 = System.nanoTime();
		try {
			Foundation.Split split = fastFoundation ? Foundation.of(plan) : null;
			return new RadialCursor(plan, split, builders, perBuilder, ring, focus);
		} finally {
			dev.t3craft.debug.PerfStats.stepAsync(dev.t3craft.debug.PerfStats.STEP_RADIAL_PLAN, t0);
		}
	}

	/**
	 * Columns sorted by ring (horizontal distance to {@code (fx, fz)} over {@code ring}, floored), then
	 * distance, then index; returns {@code {order, ringAt}} ({@code ringAt[pos]} = ring of {@code order[pos]}). Pure.
	 */
	static int[][] order(int[] colX, int[] colZ, double fx, double fz, int ring) {
		int nc = colX.length;
		long[] keys = new long[nc];
		for (int c = 0; c < nc; c++) {
			double dx = colX[c] + 0.5 - fx;
			double dz = colZ[c] + 0.5 - fz;
			double d = Math.sqrt(dx * dx + dz * dz);
			long r = (long) Math.min(2047, Math.floor(d / ring));
			long fine = (long) Math.min(4095, Math.floor(d * 4) - r * ring * 4L);
			keys[c] = (r << 52) | (Math.max(0, fine) << 32) | c;
		}
		Arrays.sort(keys);
		int[] order = new int[nc];
		int[] ringAt = new int[nc];
		for (int p = 0; p < nc; p++) {
			order[p] = (int) keys[p];
			ringAt[p] = (int) (keys[p] >>> 52);
		}
		return new int[][] {order, ringAt};
	}

	private static int[] positions(int[] order) {
		int[] pos = new int[order.length];
		for (int p = 0; p < order.length; p++) {
			pos[order[p]] = p;
		}
		return pos;
	}

	// ---- wiring (server thread) ---------------------------------------------------------------

	/** Entries already right in the world (skipped by the foundation stream, no walk); null = none known. */
	void probe(@Nullable IntPredicate already) {
		this.already = already;
	}

	/** Who hears a builder start walking. */
	void walker(@Nullable Walker w) {
		this.walker = w;
	}

	/**
	 * The centre now, plan-relative; null = nobody in the build (the hub / spawn fallback). Cheap: at most
	 * starts an off-thread re-order (moved more than {@link #MOVE}, or {@link #INTERVAL_MS} passed and it
	 * drifted {@link #DRIFT}; never closer than {@link #MIN_GAP_MS}; one at a time). Server thread.
	 */
	void focus(double @Nullable [] rel, long nowMs) {
		double fx = rel == null ? fallback[0] : rel[0];
		double fz = rel == null ? fallback[1] : rel[1];
		cx = fx;
		cz = fz;
		if (pendingOrder != null || nowMs - sortedAt < MIN_GAP_MS) {
			return;
		}
		double moved = Math.hypot(fx - sortedX, fz - sortedZ);
		if (moved <= MOVE && !(moved >= DRIFT && nowMs - sortedAt >= INTERVAL_MS)) {
			return;
		}
		sortedX = fx;
		sortedZ = fz;
		sortedAt = nowMs;
		int[] xs = colX;
		int[] zs = colZ;
		int r = ring;
		pendingOrder = Offload.supply(dev.t3craft.debug.PerfStats.STEP_RADIAL_ORDER, () -> order(xs, zs, fx, fz, r));
	}

	/** Installs a finished re-order (the scans start again from the new nearest ring). */
	private void installOrder() {
		CompletableFuture<int[][]> p = pendingOrder;
		if (p == null || !p.isDone()) {
			return;
		}
		pendingOrder = null;
		int[][] o;
		try {
			o = p.join();
		} catch (RuntimeException e) {
			return; // keep the old order
		}
		order = o[0];
		ringAt = o[1];
		posOf = positions(order);
		fPtr = 0;
		bPtr = 0;
		fWake = Integer.MAX_VALUE;
		bWake = Integer.MAX_VALUE;
	}

	// ---- columns ------------------------------------------------------------------------------

	private static final int DONE = 0, STALLED = 1, FAST = 2, BUILD = 3;

	/** Head entry of column {@code c}, or -1 when the column is done. */
	private int head(int c) {
		int k = colStart[c] + colNext[c];
		return k < colStart[c + 1] ? colEntries[k] : -1;
	}

	private int stateOf(int c) {
		int i = head(c);
		if (i < 0) {
			return DONE;
		}
		if (stalled[c]) {
			return STALLED;
		}
		if (okHead[c] != i) {
			if (!supported(i, c)) {
				stalled[c] = true;
				return STALLED;
			}
			okHead[c] = i;
		}
		return fast[i] ? FAST : BUILD;
	}

	/** Whether head {@code i} of column {@code c} may go now (see the class doc). */
	private boolean supported(int i, int c) {
		if (air[i] || forced[i] || colNext[c] == 0) {
			return true;
		}
		int below = cellAt.get(CrewPlanner.key(x[i], y[i] - 1, z[i]));
		if (below >= 0 && applied[below] && !air[below]) {
			return true;
		}
		if (falling[i]) {
			return false;
		}
		if (solidAt(x[i] + 1, y[i], z[i]) || solidAt(x[i] - 1, y[i], z[i]) || solidAt(x[i], y[i], z[i] + 1) || solidAt(x[i], y[i], z[i] - 1)) {
			return true;
		}
		return decor[i] && solidAt(x[i], y[i] + 1, z[i]);
	}

	private boolean solidAt(int bx, int by, int bz) {
		int j = cellAt.get(CrewPlanner.key(bx, by, bz));
		return j >= 0 && applied[j] && !air[j];
	}

	/** Records that {@code i} was applied (any outcome but BLOCKED); wakes the neighbours it supports. */
	private void applied(int i, BuildCursor.Outcome o) {
		applied[i] = true;
		int c = colOf[i];
		colNext[c]++;
		done++;
		switch (o) {
			case PLACED -> placed++;
			case SKIPPED -> skipped++;
			case FAILED -> failed++;
			default -> {
			}
		}
		if (!air[i]) {
			colTop[c] = Math.max(colTop[c], y[i]);
			wake(x[i] + 1, y[i], z[i]);
			wake(x[i] - 1, y[i], z[i]);
			wake(x[i], y[i], z[i] + 1);
			wake(x[i], y[i], z[i] - 1);
			wake(x[i], y[i] + 1, z[i]);
			wake(x[i], y[i] - 1, z[i]);
		}
		if (head(c) < 0) {
			colsDone++;
		}
	}

	private void wake(int bx, int by, int bz) {
		int j = cellAt.get(CrewPlanner.key(bx, by, bz));
		if (j < 0 || applied[j]) {
			return;
		}
		int c = colOf[j];
		if (stalled[c] && head(c) == j) {
			stalled[c] = false;
			touched(c);
		}
	}

	/** Column {@code c} may have work for a stream again: let the streams look back at it. */
	private void touched(int c) {
		int p = posOf[c];
		fWake = Math.min(fWake, p);
		bWake = Math.min(bWake, p);
	}

	// ---- tick ---------------------------------------------------------------------------------

	@Override
	public int tick(double allowance, FrontCursor.Step step) {
		JobState s;
		synchronized (this) {
			if (state == JobState.PENDING) {
				state = JobState.RUNNING;
			}
			s = state;
		}
		if (s != JobState.RUNNING) {
			return 0;
		}
		long t0 = System.nanoTime();
		int n0 = done;
		try {
			installOrder();
			fPtr = Math.min(fPtr, fWake);
			bPtr = Math.min(bPtr, bWake);
			fWake = Integer.MAX_VALUE;
			bWake = Integer.MAX_VALUE;
			int fastDone = tickFoundation(step);
			int built = tickBuilders(allowance, step);
			if (fastDone == 0 && built == 0 && colsDone < cols && idle()) {
				forceOne();
			}
		} finally {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_TICK, t0);
		}
		if (colsDone >= cols) {
			finish();
		}
		return done - n0;
	}

	/** Foundation stream: fast entries (and entries already right) of the columns in ring order. */
	private int tickFoundation(FrontCursor.Step step) {
		BuildBudget b = BuildBudget.global();
		int max = b.foundationBlocks();
		long nanos = b.foundationNanos();
		if (max <= 0 || nanos <= 0) {
			return 0;
		}
		long t0 = System.nanoTime();
		int count = 0;
		int skips = 0;
		int scanned = 0;
		int probes = 0;
		IntPredicate al = already;
		try {
			outer:
			while (fPtr < cols && count < max && scanned < SCAN_MAX && state == JobState.RUNNING) {
				int p = fPtr;
				int c = order[p];
				scanned++;
				while (true) {
					int st = stateOf(c);
					int i = head(c);
					boolean right = false;
					if (st == BUILD && colClaim[c] < 0 && al != null && skips < BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK) {
						if (probes >= PROBES_MAX) {
							break outer; // look again next tick, from this column
						}
						probes++;
						right = al.test(i);
					}
					if (st == FAST || right) {
						BuildCursor.Outcome o = step.apply(-1, i);
						if (o == BuildCursor.Outcome.BLOCKED) {
							break outer; // free jobs never block; keep it for the next tick
						}
						applied(i, o);
						if (o == BuildCursor.Outcome.SKIPPED) {
							skips++;
						} else {
							count++;
						}
						if (count >= max || ((count + skips) & 63) == 0 && System.nanoTime() - t0 > nanos) {
							break outer;
						}
						continue;
					}
					if (st == BUILD) {
						bWake = Math.min(bWake, p); // a builder's head now
					}
					break;
				}
				// this stream has nothing more for column c until it is touched again (release / wake)
				fPtr = p + 1;
				if ((scanned & 255) == 0 && System.nanoTime() - t0 > nanos) {
					break;
				}
			}
		} finally {
			b.spendFoundation(count, System.nanoTime() - t0);
		}
		return count + skips;
	}

	/** Builder stream: each builder works its claimed column bottom-up, then picks the next one. */
	private int tickBuilders(double allowance, FrontCursor.Step step) {
		for (int f = 0; f < builders; f++) {
			if (tcol[f] >= 0 && walkLeft[f] > 0) {
				walkLeft[f]--;
			}
			if (tcol[f] < 0) {
				pick(f);
			}
		}
		double a = Math.max(0, allowance);
		credit = Math.min(credit + a, Math.max(1.0, a));
		int[] used = new int[builders];
		int applies = 0;
		int skips = 0;
		boolean progress = true;
		while (credit >= 1.0 && progress && state == JobState.RUNNING) {
			progress = false;
			for (int j = 0; j < builders && credit >= 1.0; j++) {
				int f = (rr + j) % builders;
				if (tcol[f] < 0 || walkLeft[f] != 0 || used[f] >= perBuilder) {
					continue;
				}
				int c = tcol[f];
				int st = stateOf(c);
				if (st != BUILD) {
					release(f, st);
					pick(f);
					continue;
				}
				int i = head(c);
				if (y[i] > py[f] + RAISE_ABOVE || y[i] < py[f] - 2) {
					standFor(f, c, i); // up the scaffold (or down): a short walk, the next turn places
					continue;
				}
				BuildCursor.Outcome o = step.apply(f, i);
				if (o == BuildCursor.Outcome.BLOCKED) {
					continue;
				}
				by[i] = f;
				applied(i, o);
				applies++;
				progress = true;
				if (o == BuildCursor.Outcome.SKIPPED) {
					if (++skips >= BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK) {
						credit = Math.min(credit, 0.999);
					}
				} else {
					credit -= 1.0;
					used[f]++;
				}
				rr = (f + 1) % builders;
			}
		}
		return applies;
	}

	/** Builder {@code f} lets go of its column (done, stalled, or foundation's turn). */
	private void release(int f, int st) {
		int c = tcol[f];
		if (c >= 0) {
			colClaim[c] = -1;
			if (st == FAST) {
				fWake = Math.min(fWake, posOf[c]);
			} else if (st == BUILD) {
				bWake = Math.min(bWake, posOf[c]);
			}
		}
		tcol[f] = -1;
		walkLeft[f] = -1;
	}

	/**
	 * Free builder {@code f} claims the free column of the nearest open ring closest to where it stands
	 * (the ring after it when that ring has none free), and walks there.
	 */
	private void pick(int f) {
		int best = -1;
		double bestD = Double.MAX_VALUE;
		int bestRing = -1;
		boolean leading = true;
		int seen = 0;
		for (int p = bPtr; p < cols && seen < PICK_SCAN; p++) {
			int c = order[p];
			seen++;
			int st = stateOf(c);
			if (st != BUILD) {
				if (leading) {
					bPtr = p + 1;
				}
				if (st == FAST) {
					fWake = Math.min(fWake, p);
				}
				continue;
			}
			leading = false;
			if (colClaim[c] >= 0) {
				continue;
			}
			if (bestRing >= 0 && ringAt[p] > bestRing) {
				break;
			}
			bestRing = ringAt[p];
			double dx = colX[c] - px[f];
			double dz = colZ[c] - pz[f];
			double d = dx * dx + dz * dz;
			if (d < bestD) {
				bestD = d;
				best = c;
			}
		}
		if (best < 0) {
			return;
		}
		colClaim[best] = f;
		tcol[f] = best;
		standFor(f, best, head(best));
	}

	/**
	 * Builder {@code f} goes to a spot beside column {@code c} to place {@code i}: the neighbouring cell on
	 * its own side (else the centre's side), feet one below the block, on the scaffold.
	 */
	private void standFor(int f, int c, int i) {
		int sx = Integer.signum(px[f] - colX[c]);
		int sz = Integer.signum(pz[f] - colZ[c]);
		if (sx == 0 && sz == 0) {
			sx = colX[c] >= cx ? 1 : -1;
		}
		if (sx != 0 && sz != 0) {
			if (Math.abs(px[f] - colX[c]) >= Math.abs(pz[f] - colZ[c])) {
				sz = 0;
			} else {
				sx = 0;
			}
		}
		int ax = colX[c] + sx;
		int az = colZ[c] + sz;
		int nc = colAt.get(CrewPlanner.key(ax, 0, az));
		int feet = y[i] - 1; // on the scaffold, the block at chest height
		if (nc >= 0 && colTop[nc] != Integer.MIN_VALUE && colTop[nc] < y[i]) {
			feet = Math.max(y[i] - 2, colTop[nc] + 1); // stands on what is built there
		}
		double d = CrewPlanner.walkDistance(px[f], py[f], pz[f], ax, feet, az);
		int ticks = d <= 1.5 ? 0 : (int) Math.min(CrewCursor.MAX_WALK_TICKS, Math.ceil(d / CrewCursor.RUN_PER_TICK));
		px[f] = ax;
		py[f] = feet;
		pz[f] = az;
		walkLeft[f] = ticks;
		Walker w = walker;
		if (w != null) {
			w.walking(f, i, new int[] {ax, feet, az}, Math.max(1, ticks));
		}
	}

	/** No builder has a column it can work and every column left is stalled or waits for nobody. */
	private boolean idle() {
		for (int f = 0; f < builders; f++) {
			if (tcol[f] >= 0) {
				return false;
			}
		}
		return fPtr >= cols && bPtr >= cols && fWake == Integer.MAX_VALUE && bWake == Integer.MAX_VALUE;
	}

	/** Every column left is stalled: the nearest one goes on regardless (a floating piece of the plan). */
	private void forceOne() {
		for (int p = 0; p < cols; p++) {
			int c = order[p];
			int i = head(c);
			if (i >= 0 && stalled[c]) {
				forced[i] = true;
				stalled[c] = false;
				touched(c);
				return;
			}
		}
	}

	private synchronized void finish() {
		if (state == JobState.RUNNING) {
			state = JobState.COMPLETED;
		}
	}

	// ---- JobCursor ----------------------------------------------------------------------------

	/** Laying foundation with no builder at work yet (the job asks almost nothing of the crews' budget). */
	@Override
	public boolean fastPhase() {
		if (state.isTerminal()) {
			return false;
		}
		for (int f = 0; f < builders; f++) {
			if (tcol[f] >= 0) {
				return false;
			}
		}
		return bPtr >= cols && fPtr < cols;
	}

	@Override
	public synchronized boolean pause() {
		if (state == JobState.RUNNING || state == JobState.PENDING) {
			state = JobState.PAUSED;
			return true;
		}
		return false;
	}

	@Override
	public synchronized boolean resume() {
		if (state == JobState.PAUSED) {
			state = JobState.RUNNING;
			return true;
		}
		return false;
	}

	@Override
	public synchronized boolean cancel() {
		if (state.isTerminal()) {
			return false;
		}
		state = JobState.CANCELLED;
		return true;
	}

	@Override
	public boolean blocked() {
		return false; // free jobs only: nothing waits for materials
	}

	@Override
	public boolean frontBlocked(int f) {
		return false;
	}

	@Override
	public int segment() {
		return 0;
	}

	@Override
	public int[] pendingInSegment() {
		int[] out = new int[n - done];
		int k = 0;
		for (int i = 0; i < n && k < out.length; i++) {
			if (!applied[i]) {
				out[k++] = i;
			}
		}
		return k == out.length ? out : Arrays.copyOf(out, k);
	}

	@Override
	public int frontCount() {
		return builders;
	}

	/** The builder that placed (or works on) {@code index}; -1 for the foundation stream (no animation). */
	@Override
	public int frontOf(int index) {
		if (index < 0 || index >= n) {
			return -1;
		}
		int b = by[index];
		if (b >= 0) {
			return b;
		}
		return fast[index] ? -1 : colClaim[colOf[index]];
	}

	@Override
	public JobState state() {
		return state;
	}

	@Override
	public int total() {
		return n;
	}

	@Override
	public int done() {
		return done;
	}

	@Override
	public int placed() {
		return placed;
	}

	@Override
	public int skipped() {
		return skipped;
	}

	@Override
	public int failed() {
		return failed;
	}

	@Override
	public double progress() {
		return n == 0 ? 1.0 : (double) done / n;
	}

	/** Columns / columns done / current centre (status, debug). */
	public String describe() {
		return "radial cols " + colsDone + "/" + cols + " centre " + Math.round(cx) + "," + Math.round(cz) + " ring " + ring;
	}
}
