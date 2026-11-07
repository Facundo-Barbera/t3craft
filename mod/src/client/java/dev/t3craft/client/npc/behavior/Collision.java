// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
//
// Changes for T3Craft: the walkability test of GridPathfinder (formerly LevelWalkable), rewritten
// over real collision boxes (Minecraft-free BlockShapes), plus a body sweep for moves between
// cells and collision-resolved movement with auto step.
package dev.t3craft.client.npc.behavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * NPC body against real block collision shapes ({@link BlockShapes}): which cells an NPC can stand
 * in ({@link Walkable}), which moves between neighbouring cells the body can sweep through, and
 * collision-resolved movement ({@link Collider}). A slab desk, a fence, a top trapdoor or a door
 * panel therefore blocks exactly as much as its shape does, not "solid or not".
 *
 * <p>Body: {@link #HALF} = 0.3 (0.6 wide), {@link #HEIGHT} = 1.8, auto step {@link #STEP} = 0.6
 * like vanilla. Box reads are cached per instance; build a new one (or {@link #clear}) each tick.
 */
public final class Collision implements Walkable, Collider {
	static final double EPS = 1e-7;
	public static final double HALF = 0.3;
	public static final double HEIGHT = 1.8;
	static final double STEP = 0.6;
	/**
	 * Extra A* cost of a hop (a whole-block rise with nothing to step on, like a vanilla mob's jump):
	 * high, so routes hop only where there is no other way (the HQ floor stands a block above the
	 * grass, natural terrain), never across desks to save a few steps.
	 */
	static final double HOP_COST = 12;
	/** Floors within this height are "the same level" (carpets, snow layers). */
	static final double LEVEL = 0.07;
	private static final int FREE_RADIUS = 3;

	private final BlockShapes blocks;
	private final Map<Long, List<Box>> boxCache = new HashMap<>();

	public Collision(BlockShapes blocks) {
		this.blocks = blocks;
	}

	public void clear() {
		boxCache.clear();
	}

	private List<Box> boxes(int x, int y, int z) {
		long k = GridPathfinder.pack(x, y, z);
		List<Box> b = boxCache.get(k);
		if (b == null) {
			b = blocks.boxes(x, y, z);
			boxCache.put(k, b);
		}
		return b;
	}

	/** Does the world box overlap any block box? */
	boolean collides(double x0, double y0, double z0, double x1, double y1, double z1) {
		int bx0 = (int) Math.floor(x0 + EPS);
		int bx1 = (int) Math.floor(x1 - EPS);
		int by0 = (int) Math.floor(y0 + EPS) - 1; // fences/walls reach 0.5 into the cell above
		int by1 = (int) Math.floor(y1 - EPS);
		int bz0 = (int) Math.floor(z0 + EPS);
		int bz1 = (int) Math.floor(z1 - EPS);
		for (int bx = bx0; bx <= bx1; bx++) {
			for (int by = by0; by <= by1; by++) {
				for (int bz = bz0; bz <= bz1; bz++) {
					for (Box b : boxes(bx, by, bz)) {
						if (b.overlaps(bx, by, bz, x0, y0, z0, x1, y1, z1)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/** Body with feet at (x,y,z) overlaps a block? */
	boolean bodyCollides(double x, double y, double z) {
		return collides(x - HALF, y, z - HALF, x + HALF, y + HEIGHT, z + HALF);
	}

	private boolean hazard(int x, int y, int z) {
		return blocks.liquid(x, y, z);
	}

	@Override
	public double floor(int x, int y, int z) {
		if (hazard(x, y, z) || hazard(x, y + 1, z)) {
			return Double.NaN;
		}
		double cx = x + 0.5;
		double cz = z + 0.5;
		double f = Double.NaN;
		// Low things in the cell itself (carpet, bottom slab, closed bottom trapdoor): stand on them.
		for (Box b : boxes(x, y, z)) {
			if (b.maxY() <= 0.5 + EPS && underFeet(b, x, z) && (Double.isNaN(f) || y + b.maxY() > f)) {
				f = y + b.maxY();
			}
		}
		if (Double.isNaN(f)) {
			// Ground below: its top must be above half a block (else that cell is the floor) and at most 1.
			for (Box b : boxes(x, y - 1, z)) {
				if (b.maxY() > 0.5 + EPS && b.maxY() <= 1.0 + EPS && underFeet(b, x, z) && (Double.isNaN(f) || y - 1 + b.maxY() > f)) {
					f = y - 1 + b.maxY();
				}
			}
			if (Double.isNaN(f) || hazard(x, y - 1, z) && f < y - 0.5) {
				return Double.NaN;
			}
		}
		return bodyCollides(cx, f, cz) ? Double.NaN : f;
	}

	/** Does the box hold the body up when it stands in the cell centre? */
	private static boolean underFeet(Box b, int x, int z) {
		return b.minX() < 0.5 + HALF && b.maxX() > 0.5 - HALF && b.minZ() < 0.5 + HALF && b.maxZ() > 0.5 - HALF
				&& b.minX() <= 0.5 && b.maxX() >= 0.5 && b.minZ() <= 0.5 && b.maxZ() >= 0.5;
	}

	@Override
	public double solidTop(int x, int y, int z) {
		double top = Double.NaN;
		for (Box b : boxes(x, y, z)) {
			top = Double.isNaN(top) ? b.maxY() : Math.max(top, b.maxY());
		}
		return top;
	}

	/** The body sweeps from one cell centre to the next at the higher of the two feet heights. */
	@Override
	public boolean passage(int ax, int ay, int az, double fa, int bx, int by, int bz, double fb) {
		if (Math.abs(fb - fa) > 1.0 + 1e-6) {
			return false;
		}
		// A whole block up with nothing to step on is a hop (see HOP_COST): the body rises in A first,
		// so A must have headroom at B's height, which the sweep below checks from its first sample.
		double y = Math.max(fa, fb);
		// Samples 0.2 apart (< the 0.6 body width), so the boxes overlap and cover the sweep.
		for (int s = 0; s <= 5; s++) {
			double t = s / 5.0;
			if (bodyCollides(ax + 0.5 + (bx - ax) * t, y, az + 0.5 + (bz - az) * t)) {
				return false;
			}
		}
		return true;
	}

	/** A whole-block rise without a step in between costs {@link #HOP_COST}. */
	@Override
	public double extraCost(int ax, int ay, int az, double fa, int bx, int by, int bz, double fb) {
		return isHop(ax, az, fa, bx, by, bz, fb) ? HOP_COST : 0;
	}

	/** Going up from A to B needs a hop (more than {@link #STEP} with nothing to step on). */
	boolean isHop(int ax, int az, double fa, int bx, int by, int bz, double fb) {
		return fb - fa > STEP + 1e-6 && !landing(fa, bx - ax, bz - az, bx, by, bz, fb);
	}

	/**
	 * Stairs: entering cell B (from direction dx,dz) there is a lower part to step on first. The
	 * lowest support along B's entry strip must be within {@link #STEP} of both feet heights.
	 */
	private boolean landing(double fa, int dx, int dz, int bx, int by, int bz, double fb) {
		double low = Double.MAX_VALUE;
		for (double depth : new double[] {0.1, 0.25}) {
			for (double across : new double[] {0.2, 0.5, 0.8}) {
				double lx = dx > 0 ? depth : dx < 0 ? 1 - depth : across;
				double lz = dz > 0 ? depth : dz < 0 ? 1 - depth : across;
				low = Math.min(low, supportAt(bx, by, bz, lx, lz, fb));
			}
		}
		return low - fa <= STEP + 1e-6 && fb - low <= STEP + 1e-6;
	}

	/** Highest box top (absolute, at most {@code cap}) under local point (lx,lz) of column (x,z), in cells y-1 and y. */
	private double supportAt(int x, int y, int z, double lx, double lz, double cap) {
		double top = Double.NEGATIVE_INFINITY;
		for (int cy = y - 1; cy <= y; cy++) {
			for (Box b : boxes(x, cy, z)) {
				double t = cy + b.maxY();
				if (lx >= b.minX() && lx <= b.maxX() && lz >= b.minZ() && lz <= b.maxZ() && t <= cap + 1e-6 && t > top) {
					top = t;
				}
			}
		}
		return top;
	}

	@Override
	public boolean fits(double x, double y, double z) {
		if (bodyCollides(x, y, z)) {
			return false;
		}
		double f = floor((int) Math.floor(x), (int) Math.floor(y + 0.01), (int) Math.floor(z));
		return !Double.isNaN(f) && Math.abs(f - y) <= LEVEL;
	}

	@Override
	public boolean embedded(Pos p) {
		return bodyCollides(p.x(), p.y(), p.z());
	}

	@Override
	public @Nullable Pos nearestFree(Pos p) {
		int px = p.blockX();
		int py = p.blockY();
		int pz = p.blockZ();
		Pos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dy : new int[] {0, 1, -1}) {
			for (int dx = -FREE_RADIUS; dx <= FREE_RADIUS; dx++) {
				for (int dz = -FREE_RADIUS; dz <= FREE_RADIUS; dz++) {
					double f = floor(px + dx, py + dy, pz + dz);
					if (Double.isNaN(f)) {
						continue;
					}
					Pos c = new Pos(px + dx + 0.5, f, pz + dz + 0.5);
					// Prefer staying on the same level: a step up (onto the chair, the desk) counts double.
					double d = c.horizDist(p) + Math.abs(f - p.y()) * 2;
					if (d < bestD - 1e-9) {
						bestD = d;
						best = c;
					}
				}
			}
		}
		return best;
	}

	/**
	 * Collision-resolved move, vanilla order: Y first, then the larger horizontal axis, then the
	 * other; when a horizontal move is cut short the body tries again stepped up by at most
	 * {@link #STEP}. Boxes the body already overlaps never stop it (so it can get out).
	 */
	@Override
	public Pos move(Pos from, Pos to) {
		double dx = to.x() - from.x();
		double dy = to.y() - from.y();
		double dz = to.z() - from.z();
		if (dx == 0 && dy == 0 && dz == 0) {
			return from;
		}
		double reach = Math.max(Math.abs(dx), Math.abs(dz)) + HALF + 0.1;
		List<double[]> near = gather(from, reach, Math.min(dy, 0) - 0.1, Math.max(dy, 0) + STEP + 0.1);
		double[] a = slide(near, from, dx, dy, dz);
		boolean cut = Math.abs(a[0] - (from.x() + dx)) > 1e-7 || Math.abs(a[2] - (from.z() + dz)) > 1e-7;
		if (cut) {
			double[] body = body(from);
			double lift = clip(near, body, 1, STEP + Math.max(dy, 0));
			if (lift > 1e-6) {
				Pos up = from.add(0, lift, 0);
				double[] b = slide(near, up, dx, 0, dz);
				// then settle down to where the path wants the feet (not below what holds us)
				double down = (from.y() + dy) - b[1];
				if (down < 0) {
					double[] bb = body(new Pos(b[0], b[1], b[2]));
					b[1] += clip(near, bb, 1, down);
				}
				double ga = sq(a[0] - from.x()) + sq(a[2] - from.z());
				double gb = sq(b[0] - from.x()) + sq(b[2] - from.z());
				if (gb > ga + 1e-9) {
					a = b;
				}
			}
		}
		return new Pos(a[0], a[1], a[2]);
	}

	private static double sq(double v) {
		return v * v;
	}

	private static double[] body(Pos p) {
		return new double[] {p.x() - HALF, p.y(), p.z() - HALF, p.x() + HALF, p.y() + HEIGHT, p.z() + HALF};
	}

	/** World boxes around the body, as {minX,minY,minZ,maxX,maxY,maxZ}. */
	private List<double[]> gather(Pos p, double reach, double below, double above) {
		List<double[]> out = new ArrayList<>();
		int x0 = (int) Math.floor(p.x() - reach);
		int x1 = (int) Math.floor(p.x() + reach);
		int z0 = (int) Math.floor(p.z() - reach);
		int z1 = (int) Math.floor(p.z() + reach);
		int y0 = (int) Math.floor(p.y() + below) - 1;
		int y1 = (int) Math.floor(p.y() + HEIGHT + above);
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					for (Box b : boxes(x, y, z)) {
						out.add(new double[] {x + b.minX(), y + b.minY(), z + b.minZ(), x + b.maxX(), y + b.maxY(), z + b.maxZ()});
					}
				}
			}
		}
		return out;
	}

	private static double[] slide(List<double[]> near, Pos from, double dx, double dy, double dz) {
		double[] body = body(from);
		dy = clip(near, body, 1, dy);
		shift(body, 1, dy);
		if (Math.abs(dx) >= Math.abs(dz)) {
			dx = clip(near, body, 0, dx);
			shift(body, 0, dx);
			dz = clip(near, body, 2, dz);
			shift(body, 2, dz);
		} else {
			dz = clip(near, body, 2, dz);
			shift(body, 2, dz);
			dx = clip(near, body, 0, dx);
			shift(body, 0, dx);
		}
		return new double[] {from.x() + dx, from.y() + dy, from.z() + dz};
	}

	private static void shift(double[] body, int axis, double d) {
		body[axis] += d;
		body[axis + 3] += d;
	}

	/** How far (≤ |d|) the body can move along {@code axis} before touching a box. */
	private static double clip(List<double[]> near, double[] body, int axis, double d) {
		if (d == 0) {
			return 0;
		}
		int o1 = (axis + 1) % 3;
		int o2 = (axis + 2) % 3;
		for (double[] b : near) {
			if (b[o1] >= body[o1 + 3] - EPS || b[o1 + 3] <= body[o1] + EPS || b[o2] >= body[o2 + 3] - EPS || b[o2 + 3] <= body[o2] + EPS) {
				continue;
			}
			if (d > 0 && b[axis] >= body[axis + 3] - EPS) {
				d = Math.min(d, b[axis] - body[axis + 3]);
			} else if (d < 0 && b[axis + 3] <= body[axis] + EPS) {
				d = Math.max(d, b[axis + 3] - body[axis]);
			}
		}
		return Math.abs(d) < EPS ? 0 : d;
	}
}
