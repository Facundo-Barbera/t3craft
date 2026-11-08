package dev.t3craft.build;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Plans how a crew of {@code k} builders puts up a plan, on plain ints (no Minecraft classes,
 * unit-tested). Reimplements ideas of Baritone's builder (placeable = supported and in reach,
 * otherwise walk to the cheapest spot), Structurize's stages (decoration after what holds it) and
 * balanced k-means zoning; see {@code docs/research/builders-ai.md}. No code was copied.
 *
 * <ol>
 *   <li><b>Zones</b>: the plan's columns are split among the builders with a few rounds of
 *   capacity-limited k-means, seeded from their arrival spots around the perimeter.</li>
 *   <li><b>Eligible</b>: an entry can go once the entry before it at the same position is done
 *   (clear, then place) and it is <em>supported</em> (bottom layer of the plan, or a face neighbour
 *   already standing); decoration (not occluding: torches, lanterns, glass, doors...) also waits
 *   for every solid face neighbour of the final plan. Air (clearing) needs no support and is done
 *   by the zone's builder as soon as it may.</li>
 *   <li><b>Local order</b>: each builder takes, among the eligible entries of its zone, the one with
 *   the lowest {@code distance(eyes, block) + 2·(y − lowest eligible y of the zone)}: nearest first,
 *   bottom-up locally, no global layer barrier. In reach ({@link #REACH}) it places without moving;
 *   otherwise it walks to the nearest stand spot (free feet and head cells on something already
 *   built or the ground; a virtual scaffold when there is none) from which the block is in reach.</li>
 *   <li><b>No fights</b>: an entry has one owner; a stand spot never is another builder's cell, and
 *   keeps {@link #SEPARATION} from the others whenever there is such a spot.</li>
 *   <li><b>Rebalance</b>: a builder with nothing eligible in its zone takes the nearest eligible entry
 *   of another zone that is more than {@link #STEAL_CLEARANCE} from that zone's builder (work
 *   stealing, a minimal auction).</li>
 *   <li>What the plan leaves floating is placed last ({@code forced}), so nothing deadlocks.</li>
 * </ol>
 *
 * <p>The result ({@link CrewSchedule}) is deterministic: the same plan always gives the same
 * schedule, which keeps builds resumable.
 */
public final class CrewPlanner {
	/** Survival reach, from the eyes to the block centre. */
	public static final double REACH = 4.5;
	public static final double EYE = 1.62;
	/** Walking pace of a builder, blocks per tick. */
	public static final double WALK_PER_TICK = 0.3;
	/** Simulated time one placement takes (only orders the builders against each other). */
	static final double PLACE_TICKS = 0.25;
	static final double VERTICAL_WEIGHT = 2.0;
	/** Wanted Chebyshev distance between the cells of two builders. */
	public static final int SEPARATION = 2;
	static final double STEAL_CLEARANCE = 3.0;
	static final int STAND_RADIUS = 4;
	private static final int[][] FACES = {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};

	private CrewPlanner() {
	}

	/** Packs a cell (same layout as the NPC pathfinder; y in -2048..2047). */
	public static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	/**
	 * @param x     plan x of each entry, in plan order (y, z likewise)
	 * @param air   entry clears its cell
	 * @param decor entry is decoration (hangs on or leans against its neighbours)
	 * @param k     builders (≥ 1)
	 */
	public static CrewSchedule plan(int[] x, int[] y, int[] z, boolean[] air, boolean[] decor, int k) {
		return new Sim(x, y, z, air, decor, Math.max(1, k)).run();
	}

	/** Octile walking distance between two feet cells (climbing costs 1.5 per block). */
	public static double walkDistance(int ax, int ay, int az, int bx, int by, int bz) {
		int dx = Math.abs(ax - bx);
		int dz = Math.abs(az - bz);
		return Math.max(dx, dz) + (Math.sqrt(2) - 1) * Math.min(dx, dz) + 1.5 * Math.abs(ay - by);
	}

	/** Whether a builder whose feet are in cell {@code (sx, sy, sz)} reaches block {@code (bx, by, bz)}. */
	public static boolean inReach(int sx, int sy, int sz, int bx, int by, int bz) {
		double dx = sx - bx;
		double dy = sy + EYE - (by + 0.5);
		double dz = sz - bz;
		return dx * dx + dy * dy + dz * dz <= REACH * REACH + 1e-9;
	}

	/**
	 * Walking distance of a crew that applies {@code lanes} in order (same yardstick for any order, so
	 * orders can be compared): each builder starts at {@code starts[b]} and, for every non-air entry
	 * out of reach, walks straight to the nearest spot (feet one below the block, on the line towards
	 * where it stands) from which it is in reach.
	 */
	public static double travel(int[] x, int[] y, int[] z, boolean[] air, int[][] lanes, int[][] starts) {
		int minY = Integer.MAX_VALUE;
		for (int v : y) {
			minY = Math.min(minY, v);
		}
		double total = 0;
		for (int b = 0; b < lanes.length; b++) {
			double px = starts[b][0];
			double py = starts[b][1];
			double pz = starts[b][2];
			for (int i : lanes[b]) {
				if (air[i]) {
					continue;
				}
				double ex = px - x[i];
				double ey = py + EYE - (y[i] + 0.5);
				double ez = pz - z[i];
				if (ex * ex + ey * ey + ez * ez <= REACH * REACH) {
					continue;
				}
				double ny = Math.max(minY, y[i] - 1);
				double dy = ny + EYE - (y[i] + 0.5);
				double r = Math.sqrt(Math.max(0, REACH * REACH - dy * dy)) * 0.9;
				double h = Math.sqrt(ex * ex + ez * ez);
				double nx = h < 1e-6 ? x[i] + r : x[i] + ex / h * r;
				double nz = h < 1e-6 ? z[i] : z[i] + ez / h * r;
				double dx = Math.abs(nx - px);
				double dz = Math.abs(nz - pz);
				total += Math.max(dx, dz) + (Math.sqrt(2) - 1) * Math.min(dx, dz) + 1.5 * Math.abs(ny - py);
				px = nx;
				py = ny;
				pz = nz;
			}
		}
		return total;
	}

	/** Ticks to walk {@code dist} blocks. */
	public static int walkTicks(double dist) {
		return dist <= 1e-9 ? 0 : (int) Math.ceil(dist / WALK_PER_TICK);
	}

	private static final class Sim {
		final int n;
		final int k;
		final int[] x;
		final int[] y;
		final int[] z;
		final boolean[] air;
		final boolean[] decor;
		final long[] pos;
		final int[] prevSame;
		final Long2IntOpenHashMap finalAt = new Long2IntOpenHashMap();
		final Long2IntOpenHashMap last = new Long2IntOpenHashMap();
		final Long2IntOpenHashMap firstAt = new Long2IntOpenHashMap();
		final int[] nextSame;
		int minY = Integer.MAX_VALUE;

		final int[] zone;
		final boolean[] applied;
		final boolean[] queued;
		final boolean[] forced;
		final boolean[] relaxed;
		int scanFrom;
		final int[] owner;
		final int[][] deps;
		final List<int[]> pools = new ArrayList<>();
		final int[] poolSize;
		final ArrayDeque<Integer> airQueue = new ArrayDeque<>();

		final int[] bx;
		final int[] by;
		final int[] bz;
		final double[] t;
		final boolean[] idle;
		final int[] startX;
		final int[] startZ;
		final List<List<int[]>> lanes = new ArrayList<>();
		final int[] order;
		int orderLen;
		int remaining;
		double walked;

		Sim(int[] x, int[] y, int[] z, boolean[] air, boolean[] decor, int k) {
			this.n = x.length;
			if (y.length != n || z.length != n || air.length != n || decor.length != n) {
				throw new IllegalArgumentException("arrays differ in length");
			}
			this.k = k;
			this.x = x;
			this.y = y;
			this.z = z;
			this.air = air;
			this.decor = decor;
			pos = new long[n];
			prevSame = new int[n];
			nextSame = new int[n];
			finalAt.defaultReturnValue(-1);
			last.defaultReturnValue(-1);
			firstAt.defaultReturnValue(-1);
			Arrays.fill(nextSame, -1);
			for (int i = 0; i < n; i++) {
				pos[i] = key(x[i], y[i], z[i]);
				int p = finalAt.put(pos[i], i);
				prevSame[i] = p;
				if (p >= 0) {
					nextSame[p] = i;
				} else {
					firstAt.put(pos[i], i);
				}
				minY = Math.min(minY, y[i]);
			}
			zone = new int[n];
			applied = new boolean[n];
			queued = new boolean[n];
			forced = new boolean[n];
			relaxed = new boolean[n];
			owner = new int[n];
			deps = new int[n][];
			poolSize = new int[k];
			for (int b = 0; b < k; b++) {
				pools.add(new int[16]);
				lanes.add(new ArrayList<>());
			}
			bx = new int[k];
			by = new int[k];
			bz = new int[k];
			t = new double[k];
			idle = new boolean[k];
			startX = new int[k];
			startZ = new int[k];
			order = new int[n];
			remaining = n;
		}

		CrewSchedule run() {
			zones();
			for (int i = 0; i < n; i++) {
				if (prevSame[i] < 0) {
					check(i);
				}
			}
			drainAir();
			while (remaining > 0) {
				int b = nextBuilder();
				if (b < 0) {
					force();
					continue;
				}
				int i = pick(b);
				if (i < 0) {
					idle[b] = true;
					continue;
				}
				execute(b, i);
			}
			return build();
		}

		// ---- zones -------------------------------------------------------------------------------

		void zones() {
			Long2IntOpenHashMap colIndex = new Long2IntOpenHashMap();
			colIndex.defaultReturnValue(-1);
			List<int[]> cols = new ArrayList<>(); // {x, z, weight}
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (int i = 0; i < n; i++) {
				minX = Math.min(minX, x[i]);
				maxX = Math.max(maxX, x[i]);
				minZ = Math.min(minZ, z[i]);
				maxZ = Math.max(maxZ, z[i]);
				if (air[i]) {
					continue;
				}
				long c = key(x[i], 0, z[i]);
				int ci = colIndex.get(c);
				if (ci < 0) {
					colIndex.put(c, cols.size());
					cols.add(new int[] {x[i], z[i], 1});
				} else {
					cols.get(ci)[2]++;
				}
			}
			if (n == 0) {
				return;
			}
			double cx = (minX + maxX) / 2.0;
			double cz = (minZ + maxZ) / 2.0;
			double hx = (maxX - minX) / 2.0;
			double hz = (maxZ - minZ) / 2.0;
			double[] mx = new double[k];
			double[] mz = new double[k];
			seeds(cols, mx, mz, cx, minZ);
			int m = cols.size();
			int[] colZone = new int[m];
			if (k > 1 && m > 0) {
				long total = 0;
				for (int[] c : cols) {
					total += c[2];
				}
				long cap = (long) Math.ceil(total / (double) k * 1.15);
				Integer[] pairs = new Integer[m * k];
				double[] d = new double[m * k];
				for (int iter = 0; iter < 6; iter++) {
					for (int c = 0; c < m; c++) {
						for (int b = 0; b < k; b++) {
							double dx = cols.get(c)[0] - mx[b];
							double dz = cols.get(c)[1] - mz[b];
							d[c * k + b] = dx * dx + dz * dz;
							pairs[c * k + b] = c * k + b;
						}
					}
					Arrays.sort(pairs, (p, q) -> {
						int r = Double.compare(d[p], d[q]);
						return r != 0 ? r : Integer.compare(p, q);
					});
					Arrays.fill(colZone, -1);
					long[] load = new long[k];
					for (int pq : pairs) {
						int c = pq / k;
						int b = pq % k;
						if (colZone[c] < 0 && load[b] + cols.get(c)[2] <= cap) {
							colZone[c] = b;
							load[b] += cols.get(c)[2];
						}
					}
					for (int c = 0; c < m; c++) {
						if (colZone[c] < 0) {
							int best = 0;
							for (int b = 1; b < k; b++) {
								if (load[b] < load[best]) {
									best = b;
								}
							}
							colZone[c] = best;
							load[best] += cols.get(c)[2];
						}
					}
					double[] sx = new double[k];
					double[] sz = new double[k];
					double[] sw = new double[k];
					for (int c = 0; c < m; c++) {
						int[] col = cols.get(c);
						sx[colZone[c]] += col[0] * (double) col[2];
						sz[colZone[c]] += col[1] * (double) col[2];
						sw[colZone[c]] += col[2];
					}
					for (int b = 0; b < k; b++) {
						if (sw[b] > 0) {
							mx[b] = sx[b] / sw[b];
							mz[b] = sz[b] / sw[b];
						}
					}
				}
			}
			starts(mx, mz, cx, cz, hx, hz, minZ);
			for (int i = 0; i < n; i++) {
				int ci = colIndex.get(key(x[i], 0, z[i]));
				if (ci >= 0) {
					zone[i] = colZone[ci];
				} else {
					int best = 0;
					double bd = Double.MAX_VALUE;
					for (int b = 0; b < k; b++) {
						double dx = x[i] - mx[b];
						double dz = z[i] - mz[b];
						double dd = dx * dx + dz * dz;
						if (dd < bd) {
							bd = dd;
							best = b;
						}
					}
					zone[i] = best;
				}
			}
		}

		/**
		 * Deterministic farthest-point seeds (k-means++ without randomness): the column nearest the
		 * front ({@code -z}) first, then each time the column farthest from the seeds so far.
		 */
		void seeds(List<int[]> cols, double[] mx, double[] mz, double cx, int minZ) {
			if (cols.isEmpty()) {
				for (int b = 0; b < k; b++) {
					mx[b] = cx;
					mz[b] = minZ;
				}
				return;
			}
			int first = 0;
			for (int c = 1; c < cols.size(); c++) {
				int[] a = cols.get(c);
				int[] f = cols.get(first);
				double da = (a[1] - minZ) * 1000.0 + Math.abs(a[0] - cx);
				double df = (f[1] - minZ) * 1000.0 + Math.abs(f[0] - cx);
				if (da < df) {
					first = c;
				}
			}
			mx[0] = cols.get(first)[0];
			mz[0] = cols.get(first)[1];
			for (int b = 1; b < k; b++) {
				int far = 0;
				double fd = -1;
				for (int c = 0; c < cols.size(); c++) {
					double d = Double.MAX_VALUE;
					for (int o = 0; o < b; o++) {
						double dx = cols.get(c)[0] - mx[o];
						double dz = cols.get(c)[1] - mz[o];
						d = Math.min(d, dx * dx + dz * dz);
					}
					if (d > fd) {
						fd = d;
						far = c;
					}
				}
				mx[b] = cols.get(far)[0];
				mz[b] = cols.get(far)[1];
			}
		}

		/** Each builder arrives just outside the footprint, on the side of its zone. */
		void starts(double[] mx, double[] mz, double cx, double cz, double hx, double hz, int minZ) {
			for (int b = 0; b < k; b++) {
				double dx = mx[b] - cx;
				double dz = mz[b] - cz;
				double sx;
				double sz;
				if (k == 1 || Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) {
					sx = cx;
					sz = minZ - 2;
				} else {
					double tx = Math.abs(dx) < 1e-9 ? Double.MAX_VALUE : (hx + 2) / Math.abs(dx);
					double tz = Math.abs(dz) < 1e-9 ? Double.MAX_VALUE : (hz + 2) / Math.abs(dz);
					double t = Math.min(tx, tz);
					sx = cx + dx * t;
					sz = cz + dz * t;
				}
				int ix = (int) Math.round(sx);
				int iz = (int) Math.round(sz);
				for (int o = 0; o < b; o++) {
					if (Math.max(Math.abs(startX[o] - ix), Math.abs(startZ[o] - iz)) < SEPARATION) {
						ix += SEPARATION; // keep arrivals apart
						o = -1;
					}
				}
				startX[b] = ix;
				startZ[b] = iz;
				bx[b] = ix;
				by[b] = minY;
				bz[b] = iz;
			}
		}

		// ---- eligibility ---------------------------------------------------------------------------

		boolean occupied(long p) {
			int l = last.get(p);
			return l >= 0 && !air[l];
		}

		boolean solidAt(long p) {
			int l = last.get(p);
			return l >= 0 && !air[l] && !decor[l];
		}

		/** Final plan state of a cell is solid (and so decoration next to it waits for it). */
		boolean finalSolid(long p) {
			int f = finalAt.get(p);
			return f >= 0 && !air[f] && !decor[f];
		}

		boolean supported(int i) {
			if (y[i] == minY) {
				return true;
			}
			for (int[] f : FACES) {
				if (occupied(key(x[i] + f[0], y[i] + f[1], z[i] + f[2]))) {
					return true;
				}
			}
			return false;
		}

		boolean neighboursReady(int i) {
			for (int[] f : FACES) {
				long q = key(x[i] + f[0], y[i] + f[1], z[i] + f[2]);
				if (finalSolid(q) && !applied[finalAt.get(q)]) {
					return false;
				}
			}
			return true;
		}

		void check(int i) {
			if (i < 0 || applied[i] || queued[i]) {
				return;
			}
			if (prevSame[i] >= 0 && !applied[prevSame[i]]) {
				return;
			}
			if (air[i]) {
				queued[i] = true;
				airQueue.add(i);
				return;
			}
			if (!supported(i) || decor[i] && !neighboursReady(i)) {
				return;
			}
			queued[i] = true;
			push(zone[i], i);
			for (int b = 0; b < k; b++) {
				if (idle[b]) {
					idle[b] = false;
				}
			}
		}

		void push(int b, int i) {
			int[] p = pools.get(b);
			if (poolSize[b] == p.length) {
				p = Arrays.copyOf(p, p.length * 2);
				pools.set(b, p);
			}
			p[poolSize[b]++] = i;
		}

		// ---- the crew ------------------------------------------------------------------------------

		int nextBuilder() {
			int best = -1;
			for (int b = 0; b < k; b++) {
				if (!idle[b] && (best < 0 || t[b] < t[best])) {
					best = b;
				}
			}
			return best;
		}

		int pick(int b) {
			int i = bestIn(b, b, false);
			if (i >= 0) {
				return i;
			}
			int found = -1;
			double fc = Double.MAX_VALUE;
			for (int o = 0; o < k; o++) {
				if (o == b) {
					continue;
				}
				int j = bestIn(o, b, true);
				if (j >= 0) {
					double c = cost(b, j, 0);
					if (c < fc) {
						fc = c;
						found = j;
					}
				}
			}
			return found;
		}

		/** Best eligible entry of zone {@code zn} for builder {@code b} (compacts the pool). */
		int bestIn(int zn, int b, boolean steal) {
			int[] p = pools.get(zn);
			int w = 0;
			int lowY = Integer.MAX_VALUE;
			for (int r = 0; r < poolSize[zn]; r++) {
				int i = p[r];
				if (applied[i]) {
					continue;
				}
				if (!supported(i)) {
					// A later clear took its support away: out of the pool until a neighbour changes again.
					queued[i] = false;
					continue;
				}
				p[w++] = i;
				lowY = Math.min(lowY, y[i]);
			}
			poolSize[zn] = w;
			int best = -1;
			double bc = Double.MAX_VALUE;
			for (int r = 0; r < w; r++) {
				int i = p[r];
				if (steal && horiz(bx[zn], bz[zn], x[i], z[i]) <= STEAL_CLEARANCE) {
					continue;
				}
				double c = cost(b, i, lowY);
				if (c < bc || c == bc && i < best) {
					bc = c;
					best = i;
				}
			}
			return best;
		}

		double cost(int b, int i, int lowY) {
			double dx = bx[b] - x[i];
			double dy = by[b] + EYE - (y[i] + 0.5);
			double dz = bz[b] - z[i];
			return Math.sqrt(dx * dx + dy * dy + dz * dz) + VERTICAL_WEIGHT * Math.max(0, y[i] - lowY);
		}

		static double horiz(int ax, int az, int bx, int bz) {
			double dx = ax - bx;
			double dz = az - bz;
			return Math.sqrt(dx * dx + dz * dz);
		}

		void execute(int b, int i) {
			int walk = 0;
			if (!inReach(bx[b], by[b], bz[b], x[i], y[i], z[i]) || inBody(bx[b], by[b], bz[b], i)) {
				int[] s = stand(b, i);
				double d = walkDistance(bx[b], by[b], bz[b], s[0], s[1], s[2]);
				walked += d;
				walk = walkTicks(d);
				bx[b] = s[0];
				by[b] = s[1];
				bz[b] = s[2];
			}
			t[b] += walk + PLACE_TICKS;
			apply(b, i, walk);
			drainAir();
		}

		boolean inBody(int sx, int sy, int sz, int i) {
			return x[i] == sx && z[i] == sz && (y[i] == sy || y[i] == sy + 1);
		}

		/** Nearest stand cell from which {@code i} is in reach (see the class doc). */
		int[] stand(int b, int i) {
			int[] best = null;
			double bc = Double.MAX_VALUE;
			for (int pass = 0; pass < 3 && best == null; pass++) {
				for (int sy = Math.max(minY, y[i] - 3); sy <= y[i] + 1; sy++) {
					for (int sx = x[i] - STAND_RADIUS; sx <= x[i] + STAND_RADIUS; sx++) {
						for (int sz = z[i] - STAND_RADIUS; sz <= z[i] + STAND_RADIUS; sz++) {
							if (!inReach(sx, sy, sz, x[i], y[i], z[i]) || inBody(sx, sy, sz, i)) {
								continue;
							}
							if (occupied(key(sx, sy, sz)) || occupied(key(sx, sy + 1, sz))) {
								continue;
							}
							int sep = separation(b, sx, sy, sz);
							if (sep == 0 || pass < 2 && sep < SEPARATION) {
								continue;
							}
							double c = walkDistance(bx[b], by[b], bz[b], sx, sy, sz);
							boolean onSolid = solidAt(key(sx, sy - 1, sz));
							if (!onSolid) {
								if (sy == minY) {
									// on the terrain under the plan; a little worse where the floor goes
									if (finalSolid(key(sx, sy, sz))) {
										c += 1;
									}
								} else if (pass == 0) {
									continue;
								} else {
									c += 6; // virtual scaffold
								}
							}
							if (c < bc) {
								bc = c;
								best = new int[] {sx, sy, sz};
							}
						}
					}
				}
			}
			if (best == null) {
				// Everything around is taken: stand right next to the block (never reached in practice).
				best = new int[] {x[i], y[i] + 1, z[i] - 1};
			}
			return best;
		}

		/** Chebyshev distance (in cells) to the nearest other builder; ≥ 2 vertically counts as clear. */
		int separation(int b, int sx, int sy, int sz) {
			int min = Integer.MAX_VALUE;
			for (int o = 0; o < k; o++) {
				if (o == b) {
					continue;
				}
				if (Math.abs(by[o] - sy) >= 2) {
					continue;
				}
				int d = Math.max(Math.abs(bx[o] - sx), Math.abs(bz[o] - sz));
				min = Math.min(min, d);
			}
			return min;
		}

		void apply(int b, int i, int walk) {
			int[] d = new int[7];
			int nd = 0;
			if (prevSame[i] >= 0) {
				d[nd++] = prevSame[i];
			}
			if (!air[i] && !forced[i]) {
				if (y[i] != minY) {
					for (int[] f : FACES) {
						int l = last.get(key(x[i] + f[0], y[i] + f[1], z[i] + f[2]));
						if (l >= 0 && !air[l]) {
							nd = addDep(d, nd, l);
							break;
						}
					}
				}
				if (decor[i]) {
					for (int[] f : FACES) {
						long q = key(x[i] + f[0], y[i] + f[1], z[i] + f[2]);
						if (finalSolid(q) && applied[finalAt.get(q)]) {
							nd = addDep(d, nd, finalAt.get(q));
						}
					}
				}
			}
			deps[i] = Arrays.copyOf(d, nd);
			applied[i] = true;
			queued[i] = true;
			owner[i] = b;
			last.put(pos[i], i);
			order[orderLen++] = i;
			remaining--;
			lanes.get(b).add(new int[] {i, walk, bx[b], by[b], bz[b]});
			check(nextSame[i]);
			for (int[] f : FACES) {
				check(firstPending(key(x[i] + f[0], y[i] + f[1], z[i] + f[2])));
			}
		}

		private static int addDep(int[] d, int nd, int v) {
			for (int j = 0; j < nd; j++) {
				if (d[j] == v) {
					return nd;
				}
			}
			d[nd] = v;
			return nd + 1;
		}

		/** First entry at a cell not applied yet, or -1. */
		int firstPending(long p) {
			int i = firstAt.get(p);
			while (i >= 0 && applied[i]) {
				i = nextSame[i];
			}
			return i;
		}

		void drainAir() {
			Integer i;
			while ((i = airQueue.poll()) != null) {
				if (!applied[i]) {
					apply(zone[i], i, 0);
				}
			}
		}

		/**
		 * Nobody has anything eligible. First the decoration rule gives way (decoration that holds up a
		 * solid block, e.g. a block on a fence, waits for it and it waits for the fence): the first
		 * supported entry left goes ({@code relaxed}). If nothing is supported, the first entry left
		 * goes anyway ({@code forced}: the plan leaves it floating). "First" = plan order from the
		 * lowest pending entry, i.e. lowest layer for a layered plan.
		 */
		void force() {
			while (scanFrom < n && applied[scanFrom]) {
				scanFrom++;
			}
			int best = -1;
			int any = -1;
			for (int i = scanFrom; i < n; i++) {
				if (applied[i] || prevSame[i] >= 0 && !applied[prevSame[i]]) {
					continue;
				}
				if (any < 0) {
					any = i;
				}
				if (air[i] || supported(i)) {
					best = i;
					break;
				}
			}
			if (best >= 0) {
				relaxed[best] = !air[best];
			} else {
				best = any;
				forced[best] = true;
			}
			int b = zone[best];
			double now = 0;
			for (int o = 0; o < k; o++) {
				now = Math.max(now, t[o]);
			}
			Arrays.fill(idle, false);
			t[b] = now;
			if (air[best]) {
				apply(b, best, 0);
				drainAir();
			} else {
				execute(b, best);
			}
		}

		CrewSchedule build() {
			int[][] lane = new int[k][];
			int[][] walk = new int[k][];
			int[][] stand = new int[k][];
			for (int b = 0; b < k; b++) {
				List<int[]> l = lanes.get(b);
				lane[b] = new int[l.size()];
				walk[b] = new int[l.size()];
				stand[b] = new int[l.size() * 3];
				for (int s = 0; s < l.size(); s++) {
					int[] e = l.get(s);
					lane[b][s] = e[0];
					walk[b][s] = e[1];
					stand[b][s * 3] = e[2];
					stand[b][s * 3 + 1] = e[3];
					stand[b][s * 3 + 2] = e[4];
				}
			}
			int[] sy = new int[k];
			Arrays.fill(sy, n == 0 ? 0 : minY);
			return new CrewSchedule(n, lane, walk, stand, order, deps, forced, relaxed, owner, startX.clone(), sy, startZ.clone(), walked, x, y, z, air);
		}
	}
}
