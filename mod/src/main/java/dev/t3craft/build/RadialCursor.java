package dev.t3craft.build;

import it.unimi.dsi.fastutil.ints.IntArrayList;
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
 * When the centre moves the order is recomputed off-thread ({@link Offload}, {@code build.radial_order_async}),
 * exactly around the player, and what was pending elsewhere waits for later.
 *
 * <p><b>The round radius</b> (fix/builder-round-radius) is what holds everything to the player. The order is
 * sorted by distance, so the columns within a radius are a prefix of it ({@link #distAt}). The work radius
 * {@link #reachQ} (quarter blocks) starts at {@code build.radiusMin} (5 blocks) and:
 * <ol>
 *   <li>stays there while any column of the disc still has ground or above work (floor first: the ground goes
 *   before the entries over it in every column, and the ground stream runs {@code build.foundationRingsAhead}
 *   rings past the radius);</li>
 *   <li>when the disc is complete, jumps to the nearest column with work left plus {@code build.radiusStep} blocks
 *   (a few blocks at a time, never the whole plan), until nothing is left;</li>
 *   <li>starts again at the minimum around the new position when the order is recomputed because the player
 *   moved ({@link #installOrder}), so the builders come back with the player.</li>
 * </ol>
 * Columns that cannot be worked now (chunk not loaded: {@link #PARKED}; head without support: stalled) do not
 * hold the radius. The number of builders that take new spans ({@link #active}) grows with the work left near the
 * player ({@link #updateCrew}).
 *
 * <p><b>Each column has three parts</b> (fix/build-radial-tune), bottom-up in the plan:
 * <ol>
 *   <li><b>fill</b>: the hidden terrain of the column's bottom stack (the void island's deep layers, podium
 *   fill), every {@link Foundation} terrain entry of the stack but the top two;</li>
 *   <li><b>ground</b>: the top of that stack, the walkable surface, plus the one layer right under it so it
 *   does not look like a sheet of paper;</li>
 *   <li><b>above</b>: everything over the ground (walls, roofs, decoration, upper floors).</li>
 * </ol>
 * Ground goes before its fill (the one exception to "nothing floats", and only for terrain of the plan:
 * no temporary floors are ever placed). Above goes bottom-up and only once supported:
 * <ul>
 *   <li>the cell below holds a placed solid entry of the plan, or, where the column has a gap (a roof over
 *   a hall, a bridge, a balcony), one of the four side cells (and for decoration the cell above too:
 *   hanging lanterns) does;</li>
 *   <li>falling blocks (sand, gravel, concrete powder) need the cell below;</li>
 *   <li>air entries (clearing) need nothing and support nothing.</li>
 * </ul>
 * A column whose head is not supported <b>stalls</b> and wakes when a neighbour cell of its head is placed.
 * Stalled columns never hold the expansion back. If every column left is stalled (a plan with truly
 * floating pieces) the nearest one is forced, as the crew planner does.
 *
 * <p><b>The streams and the builders</b> are held to the radius ({@link #reachQ}):
 * <ol>
 *   <li><b>Ground stream</b> (no builder, {@link #frontOf} -1): ground and air entries, and above entries
 *   already right in the world, at most {@code build.foundationRingsAhead} rings beyond the radius and
 *   {@code build.groundBlocksPerTick} per tick (within {@link BuildBudget#foundationBlocks}). So the walkable
 *   floor grows from the player's feet, a little ahead of the builders, never across the whole island.</li>
 *   <li><b>Builders</b>: claim a <b>span</b> (a run of up to {@link #SPAN_MAX} neighbouring tall columns: a
 *   piece of wall or tower; short columns are spans of one) inside the radius (plus
 *   {@code build.builderRingsAhead} rings, 0 by default). When their ring has no free span they help on the tallest
 *   span of it (up to {@link #SPAN_CREW} builders) instead of going further. A builder raises its span in
 *   courses (the lowest head of its columns first, so side support within the span counts in the same
 *   pass) and climbs in place as it rises; it walks only to a new span or a column out of reach. Pace:
 *   {@code build.climbBlocksPerTick} on tall spans, {@code perBuilder} otherwise, out of the crews' budget
 *   ({@link BuildBudget#claim}).</li>
 *   <li><b>Fill stream</b>: the hidden fill, {@code build.fillBlocksPerTick} per tick out of what the ground
 *   left of the foundation budget, only {@code build.fillRingsBehind} rings behind the radius (or
 *   anywhere once nothing above or ground is left).</li>
 * </ol>
 * <p><b>The corridor</b> (fix/build-walkable) runs before all of them: while a player is in the build, the
 * ground (surface + support) of the columns under its feet and of a corridor {@code build.corridorWidth}
 * wide and {@code build.corridorLength} long ahead of it, along the way it moves (velocity smoothed from
 * its position every tick, so turning re-aims it within a few ticks), goes first, with no ring limit, at
 * {@code build.corridorBlocksPerTick} (and {@code build.corridorMsPerTick}, not lowered by the throttle).
 * The floor always runs ahead of a walking or running player; outside the corridor the rings rule as before.
 * With {@code build.fastFoundation} (off by default) the old fast base comes back: every foundation entry
 * goes in the streams, with no ring limit, at the whole foundation budget.
 *
 * <p><b>The player's level</b> (fix/build-walkable-level) decides what goes next near it. Off-thread, every
 * column gets its walkable candidates ({@link WalkLevels}: floors of the plan with two passable cells over
 * them, plus its terrain top). Within {@link #levelRings} of the centre, a column's surface is the candidate
 * nearest the player's feet (tie: the higher one). When that surface is not the terrain (a hall's roof under
 * the hub, a terrace, a bridge deck) it is <b>lifted</b>: the column gets a second pointer ({@link #up}) that
 * starts at that floor (and one support block under it, on the floor's edges or under a thin floor), which is
 * then placed by the corridor and the ground stream like ground, unsupported; the builders go on upwards from
 * it. What lies under it (the hidden room, its floor, walls and interior) is <b>under work</b>: it no longer
 * holds the front ring and is laid by the fill stream ({@link #underHead}), rings behind, with the support
 * rule. Builders take work in the vertical band of the player ({@link WalkLevels#inBand}) first (the radius still
 * waits for the rest of the disc, high roofs included): walls at head
 * height, railings, stairs a flight up or down; high roofs and anything far under it after. The level follows
 * the player with hysteresis ({@link WalkLevels#LEVEL_MOVE}, or 1 block held on the ground for
 * {@link #MIN_GAP_MS}); each change, and each re-order, makes the columns choose again, lazily, one int
 * compare per column look when nothing changed.
 *
 * <p>Game-thread cost per tick: bounded scans ({@link #SCAN_MAX} columns per stream, {@link #PICK_SCAN} per
 * pick, {@link #SPAN_MAX} per builder placement) over flat arrays, a handful of hash lookups per placed
 * entry (support and wake-ups), and nothing else; the plan analysis ({@link #plan}) runs off-thread in the
 * job's {@link DeferredCursor}. Plain Java, no world access (the world is read only through the
 * {@code already} probe and the placements).
 */
public final class RadialCursor implements JobCursor {
	/** Columns a stream looks at per tick (besides the ones it works on). */
	static final int SCAN_MAX = 8192;
	/** World reads ({@code already} probe) the ground stream makes per tick for builder heads. */
	static final int PROBES_MAX = 1024;
	/** Columns a builder looks at when it picks its next span. */
	static final int PICK_SCAN = 1024;
	/** The centre moved this far (blocks) since the last order: recompute it. */
	public static final double MOVE = 4.0;
	/** ...or this long passed and it moved at least {@link #DRIFT} blocks. */
	public static final long INTERVAL_MS = 3_000;
	public static final double DRIFT = 1.0;
	/** Never two orders closer than this. */
	public static final long MIN_GAP_MS = 750;
	/** A builder climbs once the head it places is this far above its feet. */
	static final int RAISE_ABOVE = 3;
	/** fix/build-radial-tune: a column with at least this many solid entries above its ground is tall (wall, tower). */
	static final int TALL = 4;
	/** Most columns in one span. */
	static final int SPAN_MAX = 8;
	/** Most builders on one span (its owner and helpers). */
	static final int SPAN_CREW = 3;
	/** A helper joins a span only when it has this many entries left. */
	static final int HELP_MIN = 6;
	/** A builder places up to this far (horizontally, each axis) from where it stands without walking. */
	static final int REACH = 4;
	/** Fill entries already right in the world (skips) per tick at most. */
	static final int FILL_SKIPS = 1024;
	/** fix/builder-round-radius: no radius limit (nothing above or ground left that can be worked now). */
	static final int NO_REACH = Integer.MAX_VALUE / 4;
	/** {@link #stateOf} of a column whose chunk is not loaded: it waits {@link #PARK_TICKS}, never holds the radius or a stream. */
	static final int PARKED = 4;
	/** A column waits this many ticks after a placement answered BLOCKED (chunk not loaded) before it is tried again. */
	static final int PARK_TICKS = 20;
	/** The parked columns are looked at again every this many ticks. */
	static final int UNPARK_EVERY = 10;
	/** The builders in use are worked out again every this many ticks. */
	static final int CREW_EVERY = 10;
	/** The work left that sets the number of builders is counted within this many quarter blocks of the centre (24 blocks). */
	static final int CROWD_Q = 24 * 4;
	/** fix/build-walkable: the corridor always covers this radius around the player's feet (blocks). */
	static final double FEET_RADIUS = 2.0;
	/** Sample step of the corridor scan (blocks): half a block, so a diagonal corridor has no holes. */
	static final double SAMPLE = 0.5;
	/** The player counts as moving above this smoothed speed (blocks/s): the corridor reaches ahead. */
	static final double MOVING_SPEED = 1.0;
	/** Time constant of the velocity smoothing (ms): ~3 ticks, so a turn re-aims the corridor quickly. */
	static final double VELOCITY_TAU_MS = 150.0;
	/** A jump faster than this (blocks/s) is a teleport: the velocity starts again from zero. */
	static final double TELEPORT_SPEED = 30.0;

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
	/** Placed by a stream, never by a builder: air, ground, fill (fastFoundation: every foundation entry). */
	private final boolean[] fast;
	/** Ground entries (surface + one support layer): supported by definition. */
	private final boolean[] ground;
	private final boolean fastMode;
	private final Long2IntOpenHashMap cellAt;
	private final Long2IntOpenHashMap colAt;
	private final int cols;
	private final int[] colX;
	private final int[] colZ;
	private final int[] colStart;
	private final int[] colEntries;
	private final int[] colOf;
	/** fix/builder-round-radius: entries of each column a builder places (not ground, fill or air) and not applied yet. */
	private final int[] buildLeft;
	/** Fill entries of each column: the first {@code colFill[c]} of its bottom-up entries. */
	private final int[] colFill;
	private final int ring;
	private final int[] fallback;
	private final int[] spanOf;
	private final int[] spanStart;
	private final int[] spanCols;
	private final boolean[] tallSpan;
	/** fix/build-walkable-level: walkable candidates of each column: {@code candK[candStart[c] … candStart[c + 1])} (column indices, bottom-up). */
	private final int[] candStart;
	private final int[] candK;
	/** The candidate gets the support block right under it when lifted (floor edge or thin floor). */
	private final boolean[] candSup;

	// ---- dynamic state (server thread) --------------------------------------------------------
	/** Next ground/above entry of each column (index into its bottom-up entries, starts at {@link #colFill}). */
	private final int[] colNext;
	/** Next fill entry of each column (0 … {@link #colFill}). */
	private final int[] fillNext;
	private final int[] colLeft;
	private final boolean[] stalled;
	/** Head entry of each column already found supported (support never goes away); -1 = not checked. */
	private final int[] okHead;
	private final int[] colTop;
	private final boolean[] applied;
	private final boolean[] forced;
	private final int[] by;
	private final int[] spanCrew;
	private final int[] spanLead;
	/** fix/build-walkable-level: upper pointer of a lifted column (column index, -1 = none: one pointer, bottom-up). */
	private final int[] up;
	/** The lifted floor entry of a column and its support entry (-1 = none): placed like ground. */
	private final int[] liftEntry;
	private final int[] liftSupport;
	/** {@link #levelVersion} a column last chose its surface for. */
	private final int[] levelSeen;
	/** The under-work head of a column waits for support (wakes like {@link #stalled}). */
	private final boolean[] underStalled;
	/** Builder took its span for work in the player's band (only then it leaves the span when the band is done). */
	private final boolean[] tband;
	private int colsDone;
	private int[] order;
	private int[] posOf;
	private int[] ringAt;
	/** Distance to the centre, quarter blocks, of {@code order[pos]} (non-decreasing: the order is sorted by distance). */
	private int[] distAt;
	/** The work radius, quarter blocks ({@link #NO_REACH} = no limit), see the class doc. */
	private int reachQ;
	/** Ticks of this cursor, and when each column may be tried again after BLOCKED (chunk not loaded). */
	private int tickNo;
	private final int[] parkUntil;
	private final IntArrayList parked = new IntArrayList();
	/** Builders that take new spans (the rest only finish the span they hold): grows with the work left near the player. */
	private int active;
	private int crewAt;
	private int gPtr;
	private int bPtr;
	private int lPtr;
	private int vPtr;
	private int gWake = Integer.MAX_VALUE;
	private int bWake = Integer.MAX_VALUE;
	private int vWake = Integer.MAX_VALUE;
	private int lWake = Integer.MAX_VALUE;
	/** fix/build-walkable-level: the player's level (plan-relative feet cell), {@link WalkLevels#NO_LEVEL} = nobody. */
	private int level = WalkLevels.NO_LEVEL;
	private int levelVersion;
	private long levelAt = Long.MIN_VALUE / 2;
	private int candFeet = WalkLevels.NO_LEVEL;
	private long candAt;
	/** Columns within this many rings of the centre take the player's level (the rest keep their terrain surface). */
	private int levelRings = 6;
	/** A stream (corridor, ground, fill) applies right now: no builder animates it. */
	private boolean streaming;
	/** Under work applied this tick (the fill stream). */
	private int underDone;

	private final int builders;
	private final double perBuilder;
	private final int[] tspan;
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
	/** fix/build-walkable: a player is in the build (the centre is a player, not the fallback). Server thread. */
	private boolean hasPlayer;
	/** Smoothed player velocity, plan-relative blocks/s, and the last sample it was measured from. */
	private double vx;
	private double vz;
	private double trackX;
	private double trackZ;
	private long trackAt;
	private boolean trackValid;

	private volatile int done;
	private volatile int placed;
	private volatile int skipped;
	private volatile int failed;
	private volatile JobState state = JobState.PENDING;

	private RadialCursor(BuildPlan plan, Foundation.Split split, boolean fastMode, int builders, double perBuilder, int ring, int @Nullable [] focus) {
		this.n = plan.size();
		this.fastMode = fastMode;
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
		ground = new boolean[n];
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
		WalkLevels.Shapes shapes = new WalkLevels.Shapes();
		boolean[] stand = new boolean[n];
		boolean[] pass = new boolean[n];
		boolean[] full = new boolean[n];
		for (int i = 0; i < n; i++) {
			Placement p = plan.get(i);
			x[i] = p.pos().getX();
			y[i] = p.pos().getY();
			z[i] = p.pos().getZ();
			air[i] = p.state().isAir();
			decor[i] = !air[i] && !p.state().canOcclude();
			falling[i] = p.state().getBlock() instanceof FallingBlock;
			stand[i] = !air[i] && shapes.standable(p.state());
			pass[i] = shapes.passable(p.state());
			full[i] = stand[i] && shapes.full(p.state());
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
		boolean[] terrain = new boolean[n];
		for (int i : split.foundation()) {
			terrain[i] = !air[i];
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
		// fix/build-radial-tune: per column, the bottom terrain stack splits into fill (hidden) and ground
		// (its top two entries: the walkable surface and the layer under it); the rest is above
		colFill = new int[nc];
		int[] colStack = new int[nc];
		boolean[] tallCol = new boolean[nc];
		for (int c = 0; c < nc; c++) {
			int a = colStart[c];
			int b = colStart[c + 1];
			int t = 0;
			while (a + t < b && terrain[colEntries[a + t]]) {
				t++;
			}
			colFill[c] = Math.max(0, t - 2);
			colStack[c] = t;
			for (int k = 0; k < t; k++) {
				int i = colEntries[a + k];
				if (k < colFill[c]) {
					fast[i] = true; // fill: the fill stream
				} else {
					ground[i] = true;
				}
			}
			int solidAbove = 0;
			for (int k = a + t; k < b; k++) {
				if (!air[colEntries[k]]) {
					solidAbove++;
				}
			}
			tallCol[c] = solidAbove >= TALL;
		}
		for (int i = 0; i < n; i++) {
			fast[i] |= air[i] || ground[i] || fastMode && terrain[i];
		}
		buildLeft = new int[nc];
		for (int i = 0; i < n; i++) {
			if (!fast[i]) {
				buildLeft[colOf[i]]++;
			}
		}
		parkUntil = new int[nc];
		// fix/build-walkable-level: walkable candidates per column (floors with two passable cells over them,
		// and the terrain top when it is walkable)
		boolean[] walk = new boolean[n];
		for (int i = 0; i < n; i++) {
			walk[i] = stand[i] && cellAt.get(CrewPlanner.key(x[i], y[i], z[i])) == i && passAt(pass, x[i], y[i] + 1, z[i])
					&& passAt(pass, x[i], y[i] + 2, z[i]);
		}
		candStart = new int[nc + 1];
		IntArrayList ck = new IntArrayList();
		it.unimi.dsi.fastutil.booleans.BooleanArrayList csup = new it.unimi.dsi.fastutil.booleans.BooleanArrayList();
		for (int c = 0; c < nc; c++) {
			candStart[c] = ck.size();
			int a = colStart[c];
			int len = colStart[c + 1] - a;
			int t = colStack[c];
			for (int kk = colFill[c]; kk < len; kk++) {
				int i = colEntries[a + kk];
				if (!walk[i]) {
					continue;
				}
				boolean groundTop = kk == t - 1;
				if (!groundTop && (kk < t || !floor(walk, i))) {
					continue; // the ground's support layer, or a lone walkable cell (a wall top, a fence line)
				}
				boolean sup = false;
				if (!groundTop && kk >= 1) {
					int e = colEntries[a + kk - 1];
					sup = y[e] == y[i] - 1 && stand[e] && (!full[i] || edge(stand, i));
				}
				ck.add(kk);
				csup.add(sup);
			}
		}
		candStart[nc] = ck.size();
		candK = ck.toIntArray();
		candSup = csup.toBooleanArray();
		// spans: neighbouring tall columns (4-connected) in runs of up to SPAN_MAX; every other column alone
		spanOf = new int[nc];
		Arrays.fill(spanOf, -1);
		int[] sStart = new int[nc + 1];
		int[] sCols = new int[nc];
		boolean[] sTall = new boolean[nc];
		int ns = 0;
		int k = 0;
		for (int c = 0; c < nc; c++) {
			if (spanOf[c] >= 0) {
				continue;
			}
			int first = k;
			sStart[ns] = first;
			spanOf[c] = ns;
			sCols[k++] = c;
			if (tallCol[c]) {
				sTall[ns] = true;
				int qh = first;
				while (qh < k && k - first < SPAN_MAX) {
					int q = sCols[qh++];
					for (int d = 0; d < 4 && k - first < SPAN_MAX; d++) {
						int nb = colAt.get(CrewPlanner.key(colX[q] + (d == 0 ? 1 : d == 1 ? -1 : 0), 0, colZ[q] + (d == 2 ? 1 : d == 3 ? -1 : 0)));
						if (nb >= 0 && spanOf[nb] < 0 && tallCol[nb]) {
							spanOf[nb] = ns;
							sCols[k++] = nb;
						}
					}
				}
			}
			ns++;
		}
		sStart[ns] = k;
		spanStart = Arrays.copyOf(sStart, ns + 1);
		spanCols = sCols;
		tallSpan = Arrays.copyOf(sTall, ns);
		spanCrew = new int[ns];
		spanLead = new int[ns];
		Arrays.fill(spanLead, -1);
		// where the build centres while no player is in it: the given focus, else the origin when it is
		// inside the footprint (the demo's hub), else the middle of the footprint
		if (focus != null) {
			fallback = new int[] {focus[0], focus[1]};
		} else if (n > 0 && minX <= 0 && 0 <= maxX && minZ <= 0 && 0 <= maxZ) {
			fallback = new int[] {0, 0};
		} else {
			fallback = new int[] {n == 0 ? 0 : (minX + maxX) / 2, n == 0 ? 0 : (minZ + maxZ) / 2};
		}
		colNext = Arrays.copyOf(colFill, nc);
		up = new int[nc];
		Arrays.fill(up, -1);
		liftEntry = new int[nc];
		Arrays.fill(liftEntry, -1);
		liftSupport = new int[nc];
		Arrays.fill(liftSupport, -1);
		levelSeen = new int[nc];
		Arrays.fill(levelSeen, -1);
		underStalled = new boolean[nc];
		fillNext = new int[nc];
		colLeft = new int[nc];
		for (int c = 0; c < nc; c++) {
			colLeft[c] = colStart[c + 1] - colStart[c];
		}
		stalled = new boolean[nc];
		okHead = new int[nc];
		Arrays.fill(okHead, -1);
		colTop = new int[nc];
		Arrays.fill(colTop, Integer.MIN_VALUE);
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
		distAt = o[2];
		posOf = positions(order);
		BuildTuning tune = BuildBudget.global().tuning();
		reachQ = minReachQ(tune);
		active = Math.min(this.builders, tune.radialMinBuilders());
		tspan = new int[this.builders];
		Arrays.fill(tspan, -1);
		tband = new boolean[this.builders];
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

	/** Constructor only: the final plan cell at {@code (bx, by, bz)} lets a body through (absent, air, no collision). */
	private boolean passAt(boolean[] pass, int bx, int by, int bz) {
		int j = cellAt.get(CrewPlanner.key(bx, by, bz));
		return j < 0 || pass[j];
	}

	private boolean walkAt(boolean[] walk, int bx, int by, int bz) {
		int j = cellAt.get(CrewPlanner.key(bx, by, bz));
		return j >= 0 && walk[j];
	}

	/** Constructor only: walkable cell {@code i} lies in a 2 × 2 square of walkable cells at its height (a floor, not a wall top). */
	private boolean floor(boolean[] walk, int i) {
		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dz = -1; dz <= 1; dz += 2) {
				if (walkAt(walk, x[i] + dx, y[i], z[i]) && walkAt(walk, x[i], y[i], z[i] + dz) && walkAt(walk, x[i] + dx, y[i], z[i] + dz)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Constructor only: a side cell of {@code i} at its height holds nothing to stand on (the floor's edge). */
	private boolean edge(boolean[] stand, int i) {
		for (int d = 0; d < 4; d++) {
			int j = cellAt.get(CrewPlanner.key(x[i] + (d == 0 ? 1 : d == 1 ? -1 : 0), y[i], z[i] + (d == 2 ? 1 : d == 3 ? -1 : 0)));
			if (j < 0 || !stand[j]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Analyses {@code plan} (off-thread: {@link DeferredCursor}): the foundation split (always: it tells
	 * terrain, ground and fill apart), columns, bottom-up order, spans and the first ring order around
	 * {@code focus} (plan-relative {@code {x, z}}, null = the hub).
	 *
	 * @param builders   builders (fronts) of the crew
	 * @param perBuilder most blocks one builder places per tick (tall spans: {@code build.climbBlocksPerTick})
	 * @param ring       ring width, blocks ({@code build.radialRing})
	 * @param fastFoundation the old fast base ({@code build.fastFoundation}, off by default): every foundation
	 *                   entry in the streams, no ring limit, the whole foundation budget
	 */
	static RadialCursor plan(BuildPlan plan, int builders, double perBuilder, int ring, int @Nullable [] focus, boolean fastFoundation) {
		long t0 = System.nanoTime();
		try {
			return new RadialCursor(plan, Foundation.of(plan), fastFoundation, builders, perBuilder, ring, focus);
		} finally {
			dev.t3craft.debug.PerfStats.stepAsync(dev.t3craft.debug.PerfStats.STEP_RADIAL_PLAN, t0);
		}
	}

	/**
	 * Columns sorted by ring (horizontal distance to {@code (fx, fz)} over {@code ring}, floored), then
	 * distance, then index; returns {@code {order, ringAt, distAt}} ({@code ringAt[pos]} = ring of {@code order[pos]},
	 * {@code distAt[pos]} = its distance in quarter blocks, never decreasing along the order). Pure.
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
		int[] distAt = new int[nc];
		for (int p = 0; p < nc; p++) {
			order[p] = (int) keys[p];
			long r = keys[p] >>> 52;
			ringAt[p] = (int) r;
			distAt[p] = (int) (r * ring * 4L + ((keys[p] >>> 32) & 0xFFFFF));
		}
		return new int[][] {order, ringAt, distAt};
	}

	/** The radius a build starts at and falls back to when the player moves, quarter blocks ({@code build.radiusMin}). */
	static int minReachQ(BuildTuning t) {
		return Math.max(4, t.radiusMin() * 4);
	}

	private static int[] positions(int[] order) {
		int[] pos = new int[order.length];
		for (int p = 0; p < order.length; p++) {
			pos[order[p]] = p;
		}
		return pos;
	}

	// ---- wiring (server thread) ---------------------------------------------------------------

	/** Entries already right in the world (skipped by the ground stream, no walk); null = none known. */
	void probe(@Nullable IntPredicate already) {
		this.already = already;
	}

	/** Who hears a builder start walking (or climb). */
	void walker(@Nullable Walker w) {
		this.walker = w;
	}

	/**
	 * The centre now, plan-relative; null = nobody in the build (the hub / spawn fallback). Cheap: at most
	 * starts an off-thread re-order (moved more than {@link #MOVE}, or {@link #INTERVAL_MS} passed and it
	 * drifted {@link #DRIFT}; never closer than {@link #MIN_GAP_MS}; one at a time). The new order centres on the
	 * player (fix/builder-round-radius: no lead any more, the radius is round around the player; the ground
	 * corridor already runs ahead of a walking player) and, once installed, the radius starts again at its
	 * minimum ({@link #installOrder}). Server thread.
	 */
	void focus(double @Nullable [] rel, long nowMs) {
		track(rel, nowMs);
		trackLevel(rel, nowMs);
		double fx = rel == null ? fallback[0] : rel[0];
		double fz = rel == null ? fallback[1] : rel[1];
		cx = fx;
		cz = fz;
		if (pendingOrder != null || nowMs - sortedAt < MIN_GAP_MS) {
			return;
		}
		double mx = fx - sortedX;
		double mz = fz - sortedZ;
		double moved = Math.hypot(mx, mz);
		if (moved <= MOVE && !(moved >= DRIFT && nowMs - sortedAt >= INTERVAL_MS)) {
			return;
		}
		double ox = fx;
		double oz = fz;
		sortedX = fx;
		sortedZ = fz;
		sortedAt = nowMs;
		int[] xs = colX;
		int[] zs = colZ;
		int r = ring;
		pendingOrder = Offload.supply(dev.t3craft.debug.PerfStats.STEP_RADIAL_ORDER, () -> order(xs, zs, ox, oz, r));
	}

	/**
	 * fix/build-walkable: the player's velocity (plan-relative, blocks/s), smoothed over ~{@link #VELOCITY_TAU_MS}
	 * from its position every tick; reset by a teleport, a gap of more than a second, or nobody in the build.
	 */
	private void track(double @Nullable [] rel, long nowMs) {
		if (rel == null) {
			hasPlayer = false;
			trackValid = false;
			vx = 0;
			vz = 0;
			return;
		}
		hasPlayer = true;
		if (trackValid) {
			long dt = nowMs - trackAt;
			if (dt <= 0) {
				return; // same millisecond: wait for a measurable step
			}
			double ix = (rel[0] - trackX) * 1000.0 / dt;
			double iz = (rel[1] - trackZ) * 1000.0 / dt;
			if (dt > 1000 || ix * ix + iz * iz > TELEPORT_SPEED * TELEPORT_SPEED) {
				vx = 0;
				vz = 0;
			} else {
				double a = Math.min(1.0, dt / VELOCITY_TAU_MS);
				vx += (ix - vx) * a;
				vz += (iz - vz) * a;
			}
		}
		trackX = rel[0];
		trackZ = rel[1];
		trackAt = nowMs;
		trackValid = true;
	}

	/**
	 * fix/build-walkable-level: the player's level from {@code rel[2]} (plan-relative y; {@code rel[3]} != 0 =
	 * on the ground). It changes when the feet moved {@link WalkLevels#LEVEL_MOVE} blocks (falling into a room,
	 * a flight of stairs), or 1 block held on the ground for {@link #MIN_GAP_MS} (a step, the top of a ramp;
	 * a jump never counts); never twice within {@link #MIN_GAP_MS}. The columns choose again lazily.
	 */
	private void trackLevel(double @Nullable [] rel, long nowMs) {
		if (rel == null || rel.length < 3) {
			if (level != WalkLevels.NO_LEVEL) {
				level = WalkLevels.NO_LEVEL;
				candFeet = WalkLevels.NO_LEVEL;
				levelVersion++;
			}
			return;
		}
		int feet = WalkLevels.feet(rel[2]);
		boolean onGround = rel.length < 4 || rel[3] != 0;
		if (feet != candFeet) {
			candFeet = feet;
			candAt = nowMs;
		}
		if (level == WalkLevels.NO_LEVEL) {
			level = feet;
			levelAt = nowMs;
			levelVersion++;
			return;
		}
		int d = Math.abs(feet - level);
		if (d == 0 || nowMs - levelAt < MIN_GAP_MS) {
			return;
		}
		if (d >= WalkLevels.LEVEL_MOVE || onGround && nowMs - candAt >= MIN_GAP_MS) {
			level = feet;
			levelAt = nowMs;
			levelVersion++;
		}
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
		distAt = o[2];
		posOf = positions(order);
		// fix/builder-round-radius: the player moved: the radius starts again at its minimum around the new position
		reachQ = minReachQ(BuildBudget.global().tuning());
		crewAt = 0;
		gPtr = 0;
		bPtr = 0;
		lPtr = 0;
		vPtr = 0;
		gWake = Integer.MAX_VALUE;
		bWake = Integer.MAX_VALUE;
		vWake = Integer.MAX_VALUE;
		levelVersion++; // the near rings moved: the columns choose their surface again
	}

	// ---- columns ------------------------------------------------------------------------------

	private static final int DONE = 0, STALLED = 1, FAST = 2, BUILD = 3;

	/**
	 * Head (next ground / above entry) of column {@code c}, or -1 when its ground and above are done. A lifted
	 * column ({@link #up} set) works from its lifted floor upwards; what is under it is {@link #underHead}.
	 * Pointers skip entries already applied (a lifted floor, its support), so out-of-order placement is free.
	 */
	private int head(int c) {
		int a = colStart[c];
		int len = colStart[c + 1] - a;
		if (up[c] >= 0) {
			while (up[c] < len && applied[colEntries[a + up[c]]]) {
				up[c]++;
			}
			return up[c] < len ? colEntries[a + up[c]] : -1;
		}
		int k = lowK(c);
		return k < len ? colEntries[a + k] : -1;
	}

	/** The lower pointer of column {@code c}, past what is already applied. */
	private int lowK(int c) {
		int a = colStart[c];
		int len = colStart[c + 1] - a;
		while (colNext[c] < len && applied[colEntries[a + colNext[c]]]) {
			colNext[c]++;
		}
		return colNext[c];
	}

	/** fix/build-walkable-level: next entry under the lifted floor of column {@code c} (the fill stream lays it), or -1. */
	private int underHead(int c) {
		if (up[c] < 0) {
			return -1;
		}
		int k = lowK(c);
		return k < up[c] ? colEntries[colStart[c] + k] : -1;
	}

	/** {@code i} is the lifted floor of column {@code c} or its support: placed like ground. */
	private boolean lifted(int c, int i) {
		return i >= 0 && (i == liftEntry[c] || i == liftSupport[c]);
	}

	/**
	 * fix/build-walkable-level: column {@code c} chooses its surface for the current level (once per
	 * {@link #levelVersion}). Within {@link #levelRings} of the centre: the candidate nearest the player's feet
	 * (tie: higher); a non-terrain one is lifted (the upper pointer starts at it, or at its support). Elsewhere,
	 * with nobody in the build, or when the terrain top wins: one pointer, bottom-up, as before.
	 */
	private void ensure(int c) {
		if (levelSeen[c] == levelVersion) {
			return;
		}
		levelSeen[c] = levelVersion;
		int a = colStart[c];
		int best = -1;
		int bestY = WalkLevels.NO_LEVEL;
		if (level != WalkLevels.NO_LEVEL && candStart[c] < candStart[c + 1] && ringAt[posOf[c]] <= levelRings) {
			for (int q = candStart[c]; q < candStart[c + 1]; q++) {
				int sy = y[colEntries[a + candK[q]]];
				if (WalkLevels.nearer(sy, bestY, level)) {
					best = q;
					bestY = sy;
				}
			}
		}
		int oldUp = up[c];
		int oldLift = liftEntry[c];
		int s = best < 0 ? -1 : colEntries[a + candK[best]];
		if (s < 0 || ground[s]) {
			up[c] = -1;
			liftEntry[c] = -1;
			liftSupport[c] = -1;
		} else {
			int ks = candK[best];
			int sup = candSup[best] ? colEntries[a + ks - 1] : -1;
			int kStart = sup >= 0 && !applied[sup] ? ks - 1 : ks;
			liftEntry[c] = s;
			liftSupport[c] = sup;
			up[c] = lowK(c) < kStart ? kStart : -1;
		}
		if ((up[c] >= 0) != (oldUp >= 0) || liftEntry[c] != oldLift) { // (a rewound up pointer only re-skips applied entries)
			stalled[c] = false;
			underStalled[c] = false;
			okHead[c] = -1;
			touched(c);
		}
	}

	/** Column {@code c} waits for its chunk (a placement answered BLOCKED less than {@link #PARK_TICKS} ago). */
	private boolean parked(int c) {
		return parkUntil[c] > tickNo;
	}

	/**
	 * fix/builder-round-radius: a placement in column {@code c} answered BLOCKED (its chunk is not loaded). The
	 * column steps aside for {@link #PARK_TICKS}: before, every stream stopped at it (and the builder that took
	 * it never let go), so one unloaded chunk held the whole build back, even the loaded columns behind it.
	 */
	private void park(int c) {
		if (parked(c)) {
			return;
		}
		parkUntil[c] = tickNo + PARK_TICKS;
		parked.add(c);
	}

	/** Every {@link #UNPARK_EVERY} ticks the columns that waited long enough are looked at again (nothing was lost: they were never applied). */
	private void unpark() {
		if (parked.isEmpty() || tickNo % UNPARK_EVERY != 0) {
			return;
		}
		for (int k = parked.size() - 1; k >= 0; k--) {
			int c = parked.getInt(k);
			if (parkUntil[c] <= tickNo) {
				parked.set(k, parked.getInt(parked.size() - 1));
				parked.removeInt(parked.size() - 1);
				touched(c);
			}
		}
	}

	private int stateOf(int c) {
		if (parked(c)) {
			return PARKED;
		}
		ensure(c);
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
		return fast[i] || lifted(c, i) ? FAST : BUILD;
	}

	/** Whether head {@code i} of column {@code c} may go now (see the class doc). */
	private boolean supported(int i, int c) {
		if (air[i] || forced[i] || ground[i] || lifted(c, i) || colEntries[colStart[c]] == i) {
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

	/**
	 * Records that {@code i} was applied (any outcome but BLOCKED); wakes the neighbours it supports. The
	 * column pointers move lazily past applied entries ({@link #head}, {@link #lowK}, the fill loop).
	 */
	private void applied(int i, BuildCursor.Outcome o) {
		applied[i] = true;
		int c = colOf[i];
		if (!fast[i]) {
			buildLeft[c]--;
		}
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
		if (--colLeft[c] == 0) {
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
		if (underStalled[c] && underHead(c) == j) {
			underStalled[c] = false;
			lWake = Math.min(lWake, posOf[c]);
		}
	}

	/** Column {@code c} may have work again: let the streams and the front look back at it. */
	private void touched(int c) {
		int p = posOf[c];
		gWake = Math.min(gWake, p);
		bWake = Math.min(bWake, p);
		vWake = Math.min(vWake, p);
		lWake = Math.min(lWake, p);
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
			tickNo++;
			installOrder();
			unpark();
			gPtr = Math.min(gPtr, gWake);
			bPtr = Math.min(bPtr, bWake);
			vPtr = Math.min(vPtr, vWake);
			lPtr = Math.min(lPtr, lWake);
			gWake = Integer.MAX_VALUE;
			bWake = Integer.MAX_VALUE;
			vWake = Integer.MAX_VALUE;
			lWake = Integer.MAX_VALUE;
			BuildTuning t = BuildBudget.global().tuning();
			// fix/build-walkable-level: the player's level reaches as far as the corridor can while the centre stays put
			int lr = (int) Math.ceil((t.corridorLength() + MOVE + ring) / ring);
			if (lr != levelRings) {
				levelRings = lr;
				levelVersion++;
			}
			int corridor = tickCorridor(step, t); // fix/build-walkable: the floor ahead of the player first
			updateReach(t);
			updateCrew(t);
			int groundDone = tickGround(step, t) + corridor;
			int built = tickBuilders(allowance, step, t);
			underDone = 0;
			int filled = tickFill(step, t);
			// the hidden fill never holds a stuck piece back: only ground, builders and under work count here
			// (columns waiting for a chunk are not stuck: they come back by themselves, so the scan for a stalled one runs now and then)
			if (groundDone == 0 && built == 0 && underDone == 0 && colsDone < cols && idle() && (parked.isEmpty() || tickNo % UNPARK_EVERY == 0)) {
				forceOne(filled == 0);
			}
		} finally {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_TICK, t0);
		}
		if (colsDone >= cols) {
			finish();
		}
		return done - n0;
	}

	/**
	 * fix/builder-round-radius: the work radius. {@link #vPtr} walks to the nearest column with ground or above
	 * work left that can be worked now (not done, stalled or {@link #PARKED}). Inside the radius: the disc is not
	 * complete, the radius stays. Beyond it: the disc is complete, the radius grows to that column plus
	 * {@code build.radiusStep} blocks. No such column left: no limit ({@link #NO_REACH}), so the hidden fill and
	 * whatever woke up behind finish. A tick that scans {@link #SCAN_MAX} done columns without reaching work
	 * changes nothing and goes on from there next tick.
	 */
	private void updateReach(BuildTuning t) {
		int scanned = 0;
		boolean found = false;
		while (vPtr < cols && scanned < SCAN_MAX) {
			int st = stateOf(order[vPtr]);
			if (st != DONE && st != STALLED && st != PARKED) {
				found = true;
				break;
			}
			vPtr++;
			scanned++;
		}
		if (vPtr >= cols) {
			reachQ = NO_REACH;
		} else if (found) {
			int dq = distAt[vPtr];
			if (reachQ >= NO_REACH || dq > reachQ) {
				reachQ = Math.max(minReachQ(t), dq + Math.max(4, t.radiusStep() * 4));
			}
		}
	}

	/** {@code reachQ} plus {@code rings} rings, quarter blocks ({@link #NO_REACH} stays). */
	private int reachPlus(int rings) {
		return reachQ >= NO_REACH ? NO_REACH : reachQ + rings * ring * 4;
	}

	/**
	 * fix/builder-round-radius: how many builders take new spans. At least {@code build.radialMinBuilders}; one
	 * more per {@code build.entriesPerBuilder} entries of builder work left within {@link #CROWD_Q} of the
	 * centre (a wall of the HQ's big centre is thousands of entries, a hut a few hundred); never more than the crew.
	 * It grows at once and shrinks one builder per look, so a builder is not taken off a wall between two spans.
	 * A builder that is not in use still finishes the span it holds. Looks every {@link #CREW_EVERY} ticks
	 * (and after the centre moved): one int add per column within 24 blocks.
	 */
	private void updateCrew(BuildTuning t) {
		int min = Math.min(builders, t.radialMinBuilders());
		if (builders <= min) {
			active = builders;
			return;
		}
		if (tickNo < crewAt) {
			return;
		}
		crewAt = tickNo + CREW_EVERY;
		int pending = 0;
		for (int p = 0; p < cols && distAt[p] <= CROWD_Q; p++) {
			pending += buildLeft[order[p]];
		}
		int want = Math.max(min, Math.min(builders, min + pending / t.entriesPerBuilder()));
		active = want >= active ? want : Math.max(want, active - 1);
	}

	/**
	 * fix/build-walkable: the corridor stream. The ground (surface + its support layer) of every column under
	 * the player's feet ({@link #FEET_RADIUS}) and, while it moves, of a corridor {@code build.corridorWidth}
	 * wide reaching {@code build.corridorLength} blocks ahead along its smoothed velocity, nearest first
	 * (along the way, then outwards from the middle line). No ring limit: it runs before every other stream,
	 * at {@code build.corridorBlocksPerTick} and {@code build.corridorMsPerTick} (not lowered by the MSPT / FPS
	 * throttle, it is the one thing that must keep up), and what it places is taken from the foundation budget
	 * the ground and fill streams share. Cost: about {@code (length + 2) × 2 × (width × 2 + 1)} hash lookups per
	 * tick (≈ 650 with the defaults) plus the placements; no world reads (the sink skips cells already right).
	 */
	private int tickCorridor(FrontCursor.Step step, BuildTuning t) {
		int max = t.corridorBlocksPerTick();
		if (!hasPlayer || max <= 0 || cols == 0) {
			return 0;
		}
		long nanos = (long) (t.corridorMsPerTick() * 1_000_000.0);
		long t0 = System.nanoTime();
		int count = 0;
		int skips = 0;
		streaming = true;
		try {
			double speed = Math.hypot(vx, vz);
			boolean moving = speed >= MOVING_SPEED;
			double hx = moving ? vx / speed : 1.0;
			double hz = moving ? vz / speed : 0.0;
			double len = moving ? Math.max(FEET_RADIUS, t.corridorLength()) : FEET_RADIUS;
			double half = moving ? t.corridorWidth() / 2.0 : FEET_RADIUS + SAMPLE;
			double px0 = cx;
			double pz0 = cz;
			int last = -1;
			outer:
			for (double s = -FEET_RADIUS; s <= len; s += SAMPLE) {
				double w = s <= FEET_RADIUS ? Math.max(half, FEET_RADIUS + SAMPLE) : half;
				for (int k = 0; ; k++) {
					double o = ((k + 1) / 2) * SAMPLE * (k % 2 == 0 ? 1 : -1); // 0, -0.5, +0.5, -1, +1, ...
					if (Math.abs(o) > w) {
						break;
					}
					int bx = (int) Math.floor(px0 + hx * s - hz * o);
					int bz = (int) Math.floor(pz0 + hz * s + hx * o);
					int c = colAt.get(CrewPlanner.key(bx, 0, bz));
					if (c < 0 || c == last || parked(c)) {
						continue;
					}
					last = c;
					ensure(c); // fix/build-walkable-level: the surface at the player's level (a lifted floor) or the terrain
					boolean worked = false;
					while (true) {
						int i = head(c);
						if (i < 0 || !(ground[i] || lifted(c, i))) {
							break;
						}
						BuildCursor.Outcome out = step.apply(-1, i);
						if (out == BuildCursor.Outcome.BLOCKED) {
							park(c); // its chunk is not loaded: this column waits, the others of the corridor go on
							break;
						}
						applied(i, out);
						worked = true;
						if (out == BuildCursor.Outcome.SKIPPED) {
							skips++;
						} else {
							count++;
						}
						if (count >= max || skips >= BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK) {
							touched(c);
							break outer;
						}
					}
					if (worked) {
						touched(c); // its head is a builder's (or nothing) now: let the other scans look back at it
						if (System.nanoTime() - t0 > nanos) {
							break outer;
						}
					}
				}
			}
		} finally {
			streaming = false;
			BuildBudget.global().spendFoundation(count, System.nanoTime() - t0);
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_CORRIDOR, t0);
		}
		return count + skips;
	}

	/** Ground stream: ground and air entries (and entries already right) of the columns in ring order, ring-limited. */
	private int tickGround(FrontCursor.Step step, BuildTuning t) {
		BuildBudget b = BuildBudget.global();
		int max = fastMode ? b.foundationBlocks() : Math.min(b.foundationBlocks(), t.groundBlocksPerTick());
		long nanos = b.foundationNanos();
		if (max <= 0 || nanos <= 0) {
			return 0;
		}
		int limit = fastMode ? NO_REACH : reachPlus(t.foundationRingsAhead());
		long t0 = System.nanoTime();
		int count = 0;
		int skips = 0;
		int scanned = 0;
		int probes = 0;
		IntPredicate al = already;
		streaming = true;
		try {
			outer:
			while (gPtr < cols && count < max && scanned < SCAN_MAX && state == JobState.RUNNING) {
				int p = gPtr;
				if (distAt[p] > limit) {
					break; // the floor waits for the radius
				}
				int c = order[p];
				scanned++;
				while (true) {
					int st = stateOf(c);
					int i = head(c);
					boolean right = false;
					if (st == BUILD && spanCrew[spanOf[c]] == 0 && al != null && skips < BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK) {
						if (probes >= PROBES_MAX) {
							break outer; // look again next tick, from this column
						}
						probes++;
						right = al.test(i);
					}
					if (st == FAST || right) {
						BuildCursor.Outcome o = step.apply(-1, i);
						if (o == BuildCursor.Outcome.BLOCKED) {
							park(c); // chunk not loaded: this column waits (kept, tried again later), the stream goes on with the next
							break;
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
				gPtr = p + 1;
				if ((scanned & 255) == 0 && System.nanoTime() - t0 > nanos) {
					break;
				}
			}
		} finally {
			streaming = false;
			b.spendFoundation(count, System.nanoTime() - t0);
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_GROUND, t0);
		}
		return count + skips;
	}

	/**
	 * Fill stream: the hidden terrain under the ground, rings behind the radius, out of the foundation budget
	 * left. fix/build-walkable-level: then the column's under work (what lies under a lifted floor: the hidden
	 * room, its floor, walls and interior), bottom-up with the support rule; a head without support waits
	 * ({@link #underStalled}) and wakes when a neighbour is placed.
	 */
	private int tickFill(FrontCursor.Step step, BuildTuning t) {
		BuildBudget b = BuildBudget.global();
		int max = fastMode ? b.foundationBlocks() : Math.min(b.foundationBlocks(), t.fillBlocksPerTick());
		long nanos = b.foundationNanos();
		if (max <= 0 || nanos <= 0) {
			return 0;
		}
		int limit = fastMode || reachQ >= NO_REACH ? NO_REACH : reachQ - t.fillRingsBehind() * ring * 4;
		if (limit < 0) {
			return 0;
		}
		long t0 = System.nanoTime();
		int count = 0;
		int skips = 0;
		int scanned = 0;
		streaming = true;
		try {
			outer:
			while (lPtr < cols && count < max && scanned < SCAN_MAX && state == JobState.RUNNING) {
				int p = lPtr;
				if (distAt[p] > limit) {
					break;
				}
				int c = order[p];
				scanned++;
				if (parked(c)) {
					lPtr = p + 1; // waits for its chunk; unpark() wakes this stream back at it
					continue;
				}
				ensure(c);
				while (fillNext[c] < colFill[c]) {
					int i = colEntries[colStart[c] + fillNext[c]];
					if (applied[i]) {
						fillNext[c]++;
						continue;
					}
					BuildCursor.Outcome o = step.apply(-1, i);
					if (o == BuildCursor.Outcome.BLOCKED) {
						park(c);
						break;
					}
					applied(i, o);
					fillNext[c]++;
					if (o == BuildCursor.Outcome.SKIPPED) {
						if (++skips >= FILL_SKIPS) {
							break outer;
						}
					} else {
						count++;
					}
					if (count >= max || ((count + skips) & 63) == 0 && System.nanoTime() - t0 > nanos) {
						break outer;
					}
				}
				while (!underStalled[c] && !parked(c)) {
					int i = underHead(c);
					if (i < 0) {
						break;
					}
					if (!supported(i, c)) {
						underStalled[c] = true;
						break;
					}
					BuildCursor.Outcome o = step.apply(-1, i);
					if (o == BuildCursor.Outcome.BLOCKED) {
						park(c);
						break;
					}
					applied(i, o);
					underDone++;
					if (o == BuildCursor.Outcome.SKIPPED) {
						if (++skips >= FILL_SKIPS) {
							break outer;
						}
					} else {
						count++;
					}
					if (count >= max || ((count + skips) & 63) == 0 && System.nanoTime() - t0 > nanos) {
						break outer;
					}
				}
				lPtr = p + 1;
				if ((scanned & 255) == 0 && System.nanoTime() - t0 > nanos) {
					break;
				}
			}
		} finally {
			streaming = false;
			b.spendFoundation(count, System.nanoTime() - t0);
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_FILL, t0);
		}
		return count + skips;
	}

	/** Builders: each raises its span in courses, climbing in place, then picks the next span. */
	private int tickBuilders(double allowance, FrontCursor.Step step, BuildTuning t) {
		long t0 = System.nanoTime();
		try {
			double climb = Math.max(perBuilder, t.climbBlocksPerTick());
			int limit = reachPlus(t.builderRingsAhead());
			for (int f = 0; f < builders; f++) {
				if (tspan[f] >= 0 && walkLeft[f] > 0) {
					walkLeft[f]--;
				}
				if (tspan[f] < 0 && f < active) {
					pick(f, limit);
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
					int s = tspan[f];
					if (s < 0 || walkLeft[f] != 0 || used[f] >= (tallSpan[s] ? climb : perBuilder)) {
						continue;
					}
					int c = next(f, limit);
					if (c < 0) {
						release(f);
						if (f < active) {
							pick(f, limit);
						}
						continue;
					}
					int i = head(c);
					if (Math.abs(colX[c] - px[f]) > REACH || Math.abs(colZ[c] - pz[f]) > REACH) {
						standFor(f, c, i); // out of reach: a short walk along the span
						if (walkLeft[f] != 0) {
							continue;
						}
					} else if (y[i] > py[f] + RAISE_ABOVE || y[i] < py[f] - 2) {
						climbTo(f, i); // up (or down) the scaffold where it stands: no walk
					}
					BuildCursor.Outcome o = step.apply(f, i);
					if (o == BuildCursor.Outcome.BLOCKED) {
						park(c); // chunk not loaded: the builder looks at the other columns of its span (or lets go) instead of waiting here
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
		} finally {
			dev.t3craft.debug.PerfStats.step(dev.t3craft.debug.PerfStats.STEP_RADIAL_BUILDERS, t0);
		}
	}

	/**
	 * The column of builder {@code f}'s span to place next: the lowest head among its columns a builder
	 * may work (within {@code limit} rings), nearest to the builder on a tie (courses: the span rises as
	 * one, so side support inside it counts in the same pass); -1 when none is left.
	 */
	private int next(int f, int limit) {
		int s = tspan[f];
		boolean band = tband[f] && level != WalkLevels.NO_LEVEL;
		int best = -1;
		int bestY = Integer.MAX_VALUE;
		int bestD = Integer.MAX_VALUE;
		for (int k = spanStart[s]; k < spanStart[s + 1]; k++) {
			int c = spanCols[k];
			int st = stateOf(c);
			if (st == FAST) {
				gWake = Math.min(gWake, posOf[c]);
				continue;
			}
			if (st != BUILD || distAt[posOf[c]] > limit) {
				continue;
			}
			int hy = y[head(c)];
			if (band && !WalkLevels.inBand(hy, level)) {
				continue; // fix/build-walkable-level: taken for work at the player's height; the rest comes later
			}
			int d = Math.abs(colX[c] - px[f]) + Math.abs(colZ[c] - pz[f]);
			if (hy < bestY || hy == bestY && d < bestD) {
				best = c;
				bestY = hy;
				bestD = d;
			}
		}
		return best;
	}

	/** Builder {@code f} lets go of its span (nothing it may work is left there now). */
	private void release(int f) {
		int s = tspan[f];
		if (s >= 0) {
			spanCrew[s] = Math.max(0, spanCrew[s] - 1);
			if (spanLead[s] == f) {
				spanLead[s] = -1;
				for (int g = 0; g < builders; g++) {
					if (g != f && tspan[g] == s) {
						spanLead[s] = g;
						break;
					}
				}
			}
			for (int k = spanStart[s]; k < spanStart[s + 1]; k++) {
				int c = spanCols[k];
				int st = stateOf(c);
				if (st == FAST) {
					gWake = Math.min(gWake, posOf[c]);
				} else if (st == BUILD) {
					bWake = Math.min(bWake, posOf[c]);
				}
			}
		}
		tspan[f] = -1;
		tband[f] = false;
		walkLeft[f] = -1;
	}

	/** Entries left (ground and above) in span {@code s}. */
	private int spanLeft(int s) {
		int left = 0;
		for (int k = spanStart[s]; k < spanStart[s + 1]; k++) {
			int c = spanCols[k];
			left += colLeft[c] - (colFill[c] - Math.min(colFill[c], fillNext[c]));
		}
		return left;
	}

	/**
	 * Free builder {@code f} takes work in the nearest ring with builder work (within {@code limit} quarter blocks of the centre):
	 * the free span closest to it there; else it helps on that ring's tallest span (fewer than
	 * {@link #SPAN_CREW} builders on it); only when that ring has neither does it look at the next ring.
	 * fix/build-walkable-level: only heads in the player's band ({@link WalkLevels#inBand}) count first; when
	 * no ring within {@code limit} has any, it takes the first ring's work out of the band (high roofs).
	 */
	private void pick(int f, int limit) {
		int[] in = new int[] {-1, Integer.MAX_VALUE, -1, 0, Integer.MAX_VALUE, -1}; // free, freeD, help, helpLeft, helpD, ring
		int[] out = new int[] {-1, Integer.MAX_VALUE, -1, 0, Integer.MAX_VALUE, -1};
		boolean leading = true;
		int seen = 0;
		boolean levelled = level != WalkLevels.NO_LEVEL;
		for (int p = bPtr; p < cols && seen < PICK_SCAN; p++) {
			if (distAt[p] > limit) {
				break;
			}
			int r = ringAt[p];
			int c = order[p];
			seen++;
			int st = stateOf(c);
			if (st != BUILD) {
				if (leading) {
					bPtr = p + 1;
				}
				if (st == FAST) {
					gWake = Math.min(gWake, p);
				}
				continue;
			}
			leading = false;
			boolean inBand = !levelled || WalkLevels.inBand(y[head(c)], level);
			int[] b = inBand ? in : out;
			if (r != b[5]) {
				if (b[0] >= 0 || b[2] >= 0) {
					if (inBand) {
						break; // the nearest ring with work at the player's height
					}
					continue; // out-of-band work: keep the first ring's best as the fallback
				}
				b[5] = r;
			}
			int s = spanOf[c];
			int d = Math.abs(colX[c] - px[f]) + Math.abs(colZ[c] - pz[f]);
			if (spanCrew[s] == 0) {
				if (d < b[1]) {
					b[0] = c;
					b[1] = d;
				}
			} else if (b[0] < 0 && tallSpan[s] && spanCrew[s] < SPAN_CREW) {
				int left = spanLeft(s);
				if (left >= HELP_MIN && (left > b[3] || left == b[3] && d < b[4])) {
					b[2] = c;
					b[3] = left;
					b[4] = d;
				}
			}
		}
		boolean banded = in[0] >= 0 || in[2] >= 0;
		int[] b = banded ? in : out;
		int free = b[0];
		int c = free >= 0 ? free : b[2];
		if (c < 0) {
			return;
		}
		int s = spanOf[c];
		int anchor = c;
		if (free >= 0 && spanStart[s + 1] - spanStart[s] > 1) {
			anchor = middle(s); // the owner stands by the middle of its span: every column within reach
		}
		tspan[f] = s;
		tband[f] = banded && levelled;
		spanCrew[s]++;
		if (spanLead[s] < 0) {
			spanLead[s] = f;
		}
		int target = next(f, limit);
		standFor(f, anchor, head(target >= 0 ? target : c));
	}

	/** The column of span {@code s} nearest to the span's centroid. */
	private int middle(int s) {
		double mx = 0;
		double mz = 0;
		int a = spanStart[s];
		int b = spanStart[s + 1];
		for (int k = a; k < b; k++) {
			mx += colX[spanCols[k]];
			mz += colZ[spanCols[k]];
		}
		mx /= b - a;
		mz /= b - a;
		int best = spanCols[a];
		double bestD = Double.MAX_VALUE;
		for (int k = a; k < b; k++) {
			int c = spanCols[k];
			double d = (colX[c] - mx) * (colX[c] - mx) + (colZ[c] - mz) * (colZ[c] - mz);
			if (d < bestD) {
				bestD = d;
				best = c;
			}
		}
		return best;
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

	/** Builder {@code f} climbs (or steps down) the scaffold where it stands to place {@code i}: no walk, it places this turn. */
	private void climbTo(int f, int i) {
		py[f] = y[i] - 1;
		Walker w = walker;
		if (w != null) {
			w.walking(f, i, new int[] {px[f], py[f], pz[f]}, 1);
		}
	}

	/** No builder has a span and every column with ground or above work left is stalled. */
	private boolean idle() {
		for (int f = 0; f < builders; f++) {
			if (tspan[f] >= 0) {
				return false;
			}
		}
		return vPtr >= cols && vWake == Integer.MAX_VALUE;
	}

	/**
	 * Every column left is stalled: the nearest one goes on regardless (a floating piece of the plan).
	 * fix/build-walkable-level: with {@code under} (the fill stream did nothing either), a stalled under-work
	 * head counts too.
	 */
	private void forceOne(boolean under) {
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
		if (!under) {
			return;
		}
		for (int p = 0; p < cols; p++) {
			int c = order[p];
			if (underStalled[c]) {
				int i = underHead(c);
				underStalled[c] = false;
				if (i >= 0) {
					forced[i] = true;
					lWake = Math.min(lWake, p);
					return;
				}
			}
		}
	}

	private synchronized void finish() {
		if (state == JobState.RUNNING) {
			state = JobState.COMPLETED;
		}
	}

	/**
	 * Blocks per tick this job wants from the crews' budget (server thread): each builder on a tall span
	 * {@code build.climbBlocksPerTick}, the others {@code perBuilder}; 1 while no builder has work (the
	 * ground and fill have their own budget).
	 */
	public double rate() {
		if (state.isTerminal()) {
			return 0.0;
		}
		double climb = Math.max(perBuilder, BuildBudget.global().tuning().climbBlocksPerTick());
		double r = 0;
		boolean any = false;
		for (int f = 0; f < builders; f++) {
			int s = tspan[f];
			if (s >= 0) {
				any = true;
				r += tallSpan[s] ? climb : perBuilder;
			} else if (f < active) {
				r += perBuilder;
			}
		}
		return any ? r : Math.min(1.0, perBuilder);
	}

	// ---- JobCursor ----------------------------------------------------------------------------

	/** Laying ground or fill with no builder at work (the job asks almost nothing of the crews' budget). */
	@Override
	public boolean fastPhase() {
		if (state.isTerminal() || colsDone >= cols) {
			return false;
		}
		for (int f = 0; f < builders; f++) {
			if (tspan[f] >= 0) {
				return false;
			}
		}
		return true;
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

	/** The builder that placed (or works on) {@code index}; -1 for the streams (ground, fill, air: no animation). */
	@Override
	public int frontOf(int index) {
		if (index < 0 || index >= n) {
			return -1;
		}
		int b = by[index];
		if (b >= 0) {
			return b;
		}
		if (streaming) {
			return -1; // fix/build-walkable-level: lifted floors and under work laid by a stream, no builder
		}
		return fast[index] ? -1 : spanLead[spanOf[colOf[index]]];
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

	/** Columns / columns done / current centre / radius and builders in use (status, debug). */
	public String describe() {
		return "radial cols " + colsDone + "/" + cols + " centre " + Math.round(cx) + "," + Math.round(cz) + " radius "
				+ (reachQ >= NO_REACH ? "all" : String.valueOf(reachQ / 4)) + " builders " + active + "/" + builders + (parked.isEmpty() ? "" : " waiting-chunks " + parked.size()) + (level == WalkLevels.NO_LEVEL ? "" : " level " + level) + (hasPlayer ? String.format(java.util.Locale.ROOT, " speed %.1f", Math.hypot(vx, vz)) : "");
	}
}
