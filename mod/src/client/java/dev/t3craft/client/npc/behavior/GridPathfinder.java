// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
//
// Changes for T3Craft: works over the Minecraft-free Walkable/Pos types (unit-testable), bounds
// come from the request (box around start and goal) instead of a fixed layout, and an optional
// "avoid" point (the player) blocks the cells around it.
package dev.t3craft.client.npc.behavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.jspecify.annotations.Nullable;

/**
 * A* over a {@link Walkable} grid. Moves: 8 directions (no corner cutting) and 1-block steps
 * up/down (straight only). The raw cell path is shortened by line-of-sight string pulling so NPCs
 * walk straight lines between corners. Deterministic, bounded (box around both ends plus
 * {@link #PAD}, and a node budget) so it never stalls the client tick.
 */
public final class GridPathfinder {
	static final int MAX_NODES = 6_000;
	static final int PAD = 10;
	/** Cells whose centre is this close (horizontally) to the avoid point are blocked. */
	static final double AVOID_RADIUS = 1.1;
	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	private final Walkable ground;
	private final Map<Long, Double> floorCache = new HashMap<>();

	public GridPathfinder(Walkable ground) {
		this.ground = ground;
	}

	private record Cell(int x, int y, int z) {
		long key() {
			return pack(x, y, z);
		}
	}

	private record Node(int x, int y, int z, double g, double f, @Nullable Node parent) {
	}

	static long pack(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	/** Feet height of a walkable cell, or NaN. Cached for the lifetime of this pathfinder. */
	public double floor(int x, int y, int z) {
		long key = pack(x, y, z);
		Double cached = floorCache.get(key);
		if (cached != null) {
			return cached;
		}
		double v = ground.floor(x, y, z);
		floorCache.put(key, v);
		return v;
	}

	/** The walkable cell an NPC at {@code p} stands in (searches 1 block up/down), or null. */
	@Nullable Cell cellAt(Pos p) {
		int x = p.blockX();
		int z = p.blockZ();
		int y = p.blockY();
		for (int dy : new int[] {0, 1, -1}) {
			if (!Double.isNaN(floor(x, y + dy, z))) {
				return new Cell(x, y + dy, z);
			}
		}
		return null;
	}

	/** {@link #find(Pos, Pos, Pos)} without anything to avoid. */
	public @Nullable List<Pos> find(Pos from, Pos to) {
		return find(from, to, null);
	}

	/**
	 * Path from {@code from} to {@code to} as feet positions (first = from, last = to exactly), or
	 * null when there is none within the budget. Cells near {@code avoid} are not entered (except
	 * the start and goal cells).
	 */
	public @Nullable List<Pos> find(Pos from, Pos to, @Nullable Pos avoid) {
		Cell start = cellAt(from);
		Cell goal = cellAt(to);
		if (start == null || goal == null) {
			return null;
		}
		if (start.equals(goal)) {
			return List.of(from, to);
		}
		int minX = Math.min(start.x, goal.x) - PAD;
		int maxX = Math.max(start.x, goal.x) + PAD;
		int minZ = Math.min(start.z, goal.z) - PAD;
		int maxZ = Math.max(start.z, goal.z) + PAD;
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> a.f != b.f ? Double.compare(a.f, b.f) : Double.compare(b.g, a.g));
		Map<Long, Double> best = new HashMap<>();
		open.add(new Node(start.x, start.y, start.z, 0, h(start.x, start.y, start.z, goal), null));
		best.put(start.key(), 0.0);
		int expanded = 0;
		Node found = null;
		while (!open.isEmpty() && expanded < MAX_NODES) {
			Node n = open.poll();
			if (n.g > best.getOrDefault(pack(n.x, n.y, n.z), Double.MAX_VALUE) + 1e-9) {
				continue;
			}
			if (n.x == goal.x && n.y == goal.y && n.z == goal.z) {
				found = n;
				break;
			}
			expanded++;
			for (int[] d : DIRS) {
				boolean diagonal = d[0] != 0 && d[1] != 0;
				for (int dy : new int[] {0, 1, -1}) {
					int nx = n.x + d[0];
					int ny = n.y + dy;
					int nz = n.z + d[1];
					if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
						continue;
					}
					if (Double.isNaN(floor(nx, ny, nz))) {
						continue;
					}
					if (dy != 0 && diagonal) {
						continue; // steps only straight on
					}
					if (dy == 1 && !Double.isNaN(ground.solidTop(n.x, n.y + 2, n.z))) {
						continue; // no headroom to step up
					}
					if (diagonal && (Double.isNaN(floor(n.x + d[0], n.y, n.z)) || Double.isNaN(floor(n.x, n.y, n.z + d[1])))) {
						continue; // never cut corners
					}
					boolean isGoal = nx == goal.x && ny == goal.y && nz == goal.z;
					if (avoid != null && !isGoal && near(avoid, nx, nz)) {
						continue;
					}
					double cost = (diagonal ? 1.41421356 : 1.0) + (dy != 0 ? 0.6 : 0);
					double g = n.g + cost;
					long key = pack(nx, ny, nz);
					if (g < best.getOrDefault(key, Double.MAX_VALUE) - 1e-9) {
						best.put(key, g);
						open.add(new Node(nx, ny, nz, g, g + h(nx, ny, nz, goal), n));
					}
				}
			}
		}
		if (found == null) {
			return null;
		}
		List<Cell> cells = new ArrayList<>();
		for (Node n = found; n != null; n = n.parent) {
			cells.add(new Cell(n.x, n.y, n.z));
		}
		Collections.reverse(cells);
		return smooth(from, to, cells, avoid);
	}

	private static boolean near(Pos avoid, int x, int z) {
		double dx = x + 0.5 - avoid.x();
		double dz = z + 0.5 - avoid.z();
		return dx * dx + dz * dz < AVOID_RADIUS * AVOID_RADIUS;
	}

	private static double h(int x, int y, int z, Cell goal) {
		int dx = Math.abs(x - goal.x);
		int dz = Math.abs(z - goal.z);
		return Math.max(dx, dz) + 0.41421356 * Math.min(dx, dz) + Math.abs(y - goal.y) * 0.6;
	}

	/** String pulling: keep only the corners where the straight line would leave walkable ground. */
	private List<Pos> smooth(Pos from, Pos to, List<Cell> cells, @Nullable Pos avoid) {
		List<Pos> pts = new ArrayList<>(cells.size());
		pts.add(from);
		for (int i = 1; i < cells.size() - 1; i++) {
			Cell c = cells.get(i);
			pts.add(new Pos(c.x + 0.5, floor(c.x, c.y, c.z), c.z + 0.5));
		}
		pts.add(to);
		List<Pos> out = new ArrayList<>();
		out.add(pts.getFirst());
		int i = 0;
		while (i < pts.size() - 1) {
			int j = pts.size() - 1;
			while (j > i + 1 && !clear(pts.get(i), pts.get(j), avoid)) {
				j--;
			}
			out.add(pts.get(j));
			i = j;
		}
		return out;
	}

	/** Can an NPC (0.6 wide) walk the straight segment a→b on one level (and not brush the avoid point)? */
	public boolean clear(Pos a, Pos b, @Nullable Pos avoid) {
		if (Math.abs(a.y() - b.y()) > 0.2) {
			return false;
		}
		double len = a.horizDist(b);
		int steps = Math.max(1, (int) Math.ceil(len / 0.2));
		int y = a.blockY();
		double nx = len == 0 ? 0 : -(b.z() - a.z()) / len * 0.3;
		double nz = len == 0 ? 0 : (b.x() - a.x()) / len * 0.3;
		for (int s = 0; s <= steps; s++) {
			double t = (double) s / steps;
			double px = a.x() + (b.x() - a.x()) * t;
			double pz = a.z() + (b.z() - a.z()) * t;
			if (Double.isNaN(floor((int) Math.floor(px), y, (int) Math.floor(pz)))
					|| Double.isNaN(floor((int) Math.floor(px + nx), y, (int) Math.floor(pz + nz)))
					|| Double.isNaN(floor((int) Math.floor(px - nx), y, (int) Math.floor(pz - nz)))) {
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
