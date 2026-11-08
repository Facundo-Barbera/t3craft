// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
//
// Changes for T3Craft: works over the Minecraft-free Walkable/Pos types (unit-testable), bounds
// come from the request (box around start and goal) instead of a fixed layout, an optional
// "avoid" point (the player) blocks the cells around it, moves between cells are checked against
// the body sweep (Walkable.passage, real collision shapes), start/goal snap to the nearest
// walkable cell, and the search is resumable so it can be spread over ticks with a node budget.
package dev.t3craft.client.npc.behavior;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.LongPredicate;
import org.jspecify.annotations.Nullable;

/**
 * A* over a {@link Walkable} grid. Moves: 8 directions (no corner cutting, diagonals only on one
 * level) and steps of up to one block (straight only), each checked with
 * {@link Walkable#passage} and priced with {@link Walkable#extraCost} (a hop up a whole block is dear). The raw cell path is shortened by line-of-sight string pulling
 * ({@link #clear}) so NPCs walk straight lines between corners. Deterministic and bounded (box
 * around both ends plus {@link #PAD}, {@link #MAX_NODES} per search); a {@link Search} can run in
 * slices ({@link Search#run}) so several NPCs share a per-tick node budget ({@link Navigation}).
 */
public final class GridPathfinder {
	static final int MAX_NODES = 48_000;
	static final int PAD = 16;
	/**
	 * fix/hq-npc-spawn: the widest the search box may grow to (blocks around both ends). When the
	 * open set runs dry and some move was cut off by the box, the box grows (×3, up to this) and the
	 * cut-off cells are searched on: the only way to an upper-floor desk often goes past it to the
	 * stairs at the far end of the building, or round a whole block, further than {@link #PAD}.
	 */
	static final int MAX_PAD = 144;
	/** Cells whose centre is this close (horizontally) to the avoid point are blocked. */
	static final double AVOID_RADIUS = 1.1;
	/** How far start/goal may be moved to the nearest walkable cell (blocks, horizontally). */
	static final int SNAP_RADIUS = 2;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
	private static final int[] DYS = {0, 1, -1};

	private final Walkable ground;
	private final LongPredicate blocked;
	private final Long2DoubleOpenHashMap floorCache = new Long2DoubleOpenHashMap();

	public GridPathfinder(Walkable ground) {
		this(ground, k -> false);
	}

	/** {@code blocked}: packed cells ({@link #pack}) not to enter (besides start and goal). */
	public GridPathfinder(Walkable ground, LongPredicate blocked) {
		this.ground = ground;
		this.blocked = blocked;
	}

	record Cell(int x, int y, int z) {
		long key() {
			return pack(x, y, z);
		}
	}

	private record Node(int x, int y, int z, double g, double f, @Nullable Node parent) {
	}

	public enum Status {
		RUNNING, FOUND, NONE
	}

	static long pack(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	/** Forget cached floors (the blocks may have changed). */
	public void clearCache() {
		floorCache.clear();
	}

	/** Feet height of a walkable cell, or NaN. Cached until {@link #clearCache}. */
	public double floor(int x, int y, int z) {
		long key = pack(x, y, z);
		if (floorCache.containsKey(key)) {
			return floorCache.get(key);
		}
		double v = ground.floor(x, y, z);
		floorCache.put(key, v);
		return v;
	}

	double floor(Cell c) {
		return floor(c.x, c.y, c.z);
	}

	/**
	 * The walkable cell for an NPC (or a target) at {@code p}: its own cell, else the nearest
	 * walkable cell within {@link #SNAP_RADIUS} (1 block up/down, preferring the same level); null if none.
	 */
	@Nullable Cell cellAt(Pos p) {
		int x = p.blockX();
		int z = p.blockZ();
		int y = p.blockY();
		if (!Double.isNaN(floor(x, y, z))) {
			return new Cell(x, y, z);
		}
		// Nearest walkable cell; changing level costs double, so a target inside a desk or chair
		// resolves to the floor next to it rather than the top of it.
		Cell best = null;
		double bestD = Double.MAX_VALUE;
		for (int dy : DYS) {
			for (int dx = -SNAP_RADIUS; dx <= SNAP_RADIUS; dx++) {
				for (int dz = -SNAP_RADIUS; dz <= SNAP_RADIUS; dz++) {
					double f = floor(x + dx, y + dy, z + dz);
					if (Double.isNaN(f)) {
						continue;
					}
					double ddx = x + dx + 0.5 - p.x();
					double ddz = z + dz + 0.5 - p.z();
					double d = Math.sqrt(ddx * ddx + ddz * ddz) + Math.abs(f - p.y()) * 2;
					if (d < bestD - 1e-9) {
						bestD = d;
						best = new Cell(x + dx, y + dy, z + dz);
					}
				}
			}
		}
		return best;
	}

	/** Where an NPC heading for {@code to} actually stops: {@code to} if the body fits there, else the centre of its walkable cell. */
	Pos standPoint(Pos to, Cell goal) {
		double f = floor(goal);
		if (goal.x == to.blockX() && goal.z == to.blockZ() && Math.abs(f - to.y()) <= 0.07 && ground.fits(to.x(), to.y(), to.z())) {
			return to;
		}
		return new Pos(goal.x + 0.5, f, goal.z + 0.5);
	}

	/** {@link #find(Pos, Pos, Pos)} without anything to avoid. */
	public @Nullable List<Pos> find(Pos from, Pos to) {
		return find(from, to, null);
	}

	/**
	 * Path from {@code from} to {@code to} as feet positions (first = from, last = to, or the
	 * nearest spot where the body fits when {@code to} is inside a block), or null when there is
	 * none within {@link #MAX_NODES}. Cells near {@code avoid} are not entered (except the start
	 * and goal cells).
	 */
	public @Nullable List<Pos> find(Pos from, Pos to, @Nullable Pos avoid) {
		Search s = search(from, to, avoid);
		s.run(MAX_NODES);
		return s.status() == Status.FOUND ? s.path() : null;
	}

	/** A search to run in slices; {@link Search#status} is {@link Status#NONE} right away when an end has no walkable cell. */
	public Search search(Pos from, Pos to, @Nullable Pos avoid) {
		return new Search(from, to, avoid);
	}

	public final class Search {
		private final Pos from;
		private final Pos to;
		private final @Nullable Pos avoid;
		private final @Nullable Cell start;
		private final @Nullable Cell goal;
		private final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> a.f != b.f ? Double.compare(a.f, b.f) : Double.compare(b.g, a.g));
		private final Long2DoubleOpenHashMap best = new Long2DoubleOpenHashMap();
		private int minX;
		private int maxX;
		private int minZ;
		private int maxZ;
		private int pad = PAD;
		/** Expanded nodes with a neighbour outside the box: searched on when the box grows. */
		private final List<Node> edge = new ArrayList<>();
		private int expanded;
		private Status status = Status.RUNNING;
		private String reason = "";
		private @Nullable List<Cell> cells;

		private Search(Pos from, Pos to, @Nullable Pos avoid) {
			this.from = from;
			this.to = to;
			this.avoid = avoid;
			this.start = cellAt(from);
			this.goal = cellAt(to);
			if (start == null || goal == null) {
				status = Status.NONE;
				reason = start == null ? "no walkable cell near the NPC " + from : "no walkable cell near the target " + to;
				minX = maxX = minZ = maxZ = 0;
				return;
			}
			minX = Math.min(start.x, goal.x) - PAD;
			maxX = Math.max(start.x, goal.x) + PAD;
			minZ = Math.min(start.z, goal.z) - PAD;
			maxZ = Math.max(start.z, goal.z) + PAD;
			if (start.equals(goal)) {
				status = Status.FOUND;
				cells = List.of(start);
				return;
			}
			open.add(new Node(start.x, start.y, start.z, 0, h(start.x, start.y, start.z, goal), null));
			best.put(start.key(), 0.0);
		}

		public Status status() {
			return status;
		}

		/** Why there is no route ({@link Status#NONE}), for the debug log; empty otherwise. */
		public String reason() {
			return reason;
		}

		/** Nodes expanded so far. */
		public int expanded() {
			return expanded;
		}

		@Nullable Cell startCell() {
			return start;
		}

		@Nullable Cell goalCell() {
			return goal;
		}

		/** The cells of the found route (start first). */
		@Nullable List<Cell> cells() {
			return cells;
		}

		/** Expand at most {@code budget} nodes; returns how many were expanded. */
		public int run(int budget) {
			int used = 0;
			Cell g = goal;
			while (status == Status.RUNNING && used < budget) {
				if (open.isEmpty() && !edge.isEmpty() && pad < MAX_PAD && expanded < MAX_NODES) {
					grow();
				}
				if (open.isEmpty() || expanded >= MAX_NODES) {
					status = Status.NONE;
					reason = open.isEmpty()
							? "unreachable within x " + minX + ".." + maxX + ", z " + minZ + ".." + maxZ + " (" + expanded + " cells searched)"
							: "gave up after " + MAX_NODES + " cells";
					break;
				}
				Node n = open.poll();
				if (n.g > best.getOrDefault(pack(n.x, n.y, n.z), Double.MAX_VALUE) + 1e-9) {
					continue;
				}
				if (n.x == g.x && n.y == g.y && n.z == g.z) {
					List<Cell> out = new ArrayList<>();
					for (Node k = n; k != null; k = k.parent) {
						out.add(new Cell(k.x, k.y, k.z));
					}
					Collections.reverse(out);
					cells = out;
					status = Status.FOUND;
					break;
				}
				expanded++;
				used++;
				expand(n, g);
			}
			return used;
		}

		/** Widens the box around both ends and searches on from the nodes it had cut off. */
		private void grow() {
			Cell s = Objects.requireNonNull(start);
			Cell g = Objects.requireNonNull(goal);
			pad = Math.min(MAX_PAD, pad * 3);
			minX = Math.min(s.x, g.x) - pad;
			maxX = Math.max(s.x, g.x) + pad;
			minZ = Math.min(s.z, g.z) - pad;
			maxZ = Math.max(s.z, g.z) + pad;
			open.addAll(edge);
			edge.clear();
		}

		private void expand(Node n, Cell g) {
			double fa = floor(n.x, n.y, n.z);
			boolean cut = false;
			for (int[] d : DIRS) {
				boolean diagonal = d[0] != 0 && d[1] != 0;
				for (int dy : DYS) {
					if (dy != 0 && diagonal) {
						continue; // steps only straight on
					}
					int nx = n.x + d[0];
					int ny = n.y + dy;
					int nz = n.z + d[1];
					if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
						if (!cut) {
							cut = true;
							edge.add(n);
						}
						continue;
					}
					double fb = floor(nx, ny, nz);
					if (Double.isNaN(fb)) {
						continue;
					}
					boolean isGoal = nx == g.x && ny == g.y && nz == g.z;
					if (!isGoal && blocked.test(pack(nx, ny, nz))) {
						continue;
					}
					if (avoid != null && !isGoal && near(avoid, nx, nz)) {
						continue;
					}
					if (diagonal && !diagonalOk(n.x, n.y, n.z, fa, d[0], d[1], fb)) {
						continue;
					}
					if (!ground.passage(n.x, n.y, n.z, fa, nx, ny, nz, fb)) {
						continue;
					}
					double rise = Math.abs(fb - fa);
					double cost = (diagonal ? 1.41421356 : 1.0) + (rise > 0.07 ? 0.3 + 0.5 * rise : 0)
							+ ground.extraCost(n.x, n.y, n.z, fa, nx, ny, nz, fb);
					double gg = n.g + cost;
					long key = pack(nx, ny, nz);
					if (gg < best.getOrDefault(key, Double.MAX_VALUE) - 1e-9) {
						best.put(key, gg);
						open.add(new Node(nx, ny, nz, gg, gg + h(nx, ny, nz, g), n));
					}
				}
			}
		}

		/** Diagonals only between four cells on one level, never cutting a corner. */
		private boolean diagonalOk(int x, int y, int z, double fa, int dx, int dz, double fb) {
			if (Math.abs(fb - fa) > 0.07) {
				return false;
			}
			double f1 = floor(x + dx, y, z);
			double f2 = floor(x, y, z + dz);
			return !Double.isNaN(f1) && !Double.isNaN(f2) && Math.abs(f1 - fa) <= 0.07 && Math.abs(f2 - fa) <= 0.07
					&& !blocked.test(pack(x + dx, y, z)) && !blocked.test(pack(x, y, z + dz));
		}

		/** The smoothed route (first = from, last = the stand point near {@code to}); null unless found. */
		public @Nullable List<Pos> path() {
			return cells == null ? null : smooth(from, to, cells, avoid);
		}
	}

	private static boolean near(Pos avoid, int x, int z) {
		double dx = x + 0.5 - avoid.x();
		double dz = z + 0.5 - avoid.z();
		return dx * dx + dz * dz < AVOID_RADIUS * AVOID_RADIUS;
	}

	private static double h(int x, int y, int z, Cell goal) {
		int dx = Math.abs(x - goal.x);
		int dz = Math.abs(z - goal.z);
		return Math.max(dx, dz) + 0.41421356 * Math.min(dx, dz) + Math.abs(y - goal.y) * 0.3;
	}

	/** Are all cells of a (cached) route still walkable and not blocked? */
	boolean stillWalkable(List<Cell> cells) {
		for (int i = 0; i < cells.size(); i++) {
			Cell c = cells.get(i);
			if (Double.isNaN(floor(c)) || i > 0 && i < cells.size() - 1 && blocked.test(c.key())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * String pulling over the cell centres: keep only the corners where the straight line would
	 * not fit the body. The start cell's centre is a candidate too, so an NPC standing off-centre
	 * (next to a wall, pushed out of a block) first re-centres when it cannot go straight on.
	 */
	List<Pos> smooth(Pos from, Pos to, List<Cell> cells, @Nullable Pos avoid) {
		List<Pos> pts = new ArrayList<>(cells.size() + 1);
		pts.add(from);
		for (int i = 0; i < cells.size() - 1; i++) {
			Cell c = cells.get(i);
			pts.add(new Pos(c.x + 0.5, floor(c), c.z + 0.5));
		}
		Pos end = standPoint(to, cells.getLast());
		pts.add(end);
		List<Pos> out = new ArrayList<>();
		out.add(from);
		int i = 0;
		while (i < pts.size() - 1) {
			// Greedy: go as far ahead as the straight line stays clear.
			int j = i + 1;
			while (j + 1 < pts.size() && clear(pts.get(i), pts.get(j + 1), avoid)) {
				j++;
			}
			Pos p = pts.get(j);
			if (!out.getLast().equals(p)) {
				out.add(p);
			}
			i = j;
		}
		if (out.size() == 1) {
			out.add(end);
		}
		return out;
	}

	/** Can an NPC (0.6 wide) walk the straight segment a→b on one level (and not brush the avoid point)? */
	public boolean clear(Pos a, Pos b, @Nullable Pos avoid) {
		if (Math.abs(a.y() - b.y()) > 0.07) {
			return false;
		}
		double len = a.horizDist(b);
		int steps = Math.max(1, (int) Math.ceil(len / 0.25));
		for (int s = 0; s <= steps; s++) {
			double t = (double) s / steps;
			double px = a.x() + (b.x() - a.x()) * t;
			double py = a.y() + (b.y() - a.y()) * t;
			double pz = a.z() + (b.z() - a.z()) * t;
			if (!ground.fits(px, py, pz)) {
				return false;
			}
			if (avoid != null && s > 0 && s < steps) {
				double dx = px - avoid.x();
				double dz = pz - avoid.z();
				if (dx * dx + dz * dz < AVOID_RADIUS * AVOID_RADIUS) {
					return false;
				}
			}
		}
		return true;
	}
}
