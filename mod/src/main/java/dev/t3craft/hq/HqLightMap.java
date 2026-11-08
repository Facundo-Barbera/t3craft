package dev.t3craft.hq;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

/**
 * Block light of a {@link BuildPlan} at night, computed in memory (no world): a flood fill from every
 * placement that emits light ({@link BlockState#getLightEmission()}), losing
 * {@code max(1, dampening)} per step and stopped by faces that occlude (slabs, stairs), with the
 * same rule the game's {@code BlockLightEngine} uses ({@link LightEngine#getLightDampeningInto}).
 * Cells the plan does not set read as air. Sky light is ignored: this is the HQ at midnight.
 *
 * <p>Used by the lighting validator (no standable cell under {@link #MIN_LEVEL}) and the hq-render
 * light heatmaps.
 */
public final class HqLightMap {
	/** Lowest block light a standable cell of the HQ may have (mobs never spawn, it reads as lit). */
	public static final int MIN_LEVEL = 10;
	/** How far past the plan's footprint light is followed (a level-15 source reaches 14 cells). */
	private static final int MARGIN = 15;

	private final int minX;
	private final int minY;
	private final int minZ;
	private final int sx;
	private final int sy;
	private final int sz;
	private final byte[] level;
	private final BlockState[] states;
	private final IntQueue[] buckets = new IntQueue[16];

	private HqLightMap(Box b, int margin) {
		this.minX = b.minX() - margin;
		this.minY = b.minY() - margin;
		this.minZ = b.minZ() - margin;
		this.sx = b.sizeX() + 2 * margin;
		this.sy = b.sizeY() + 2 * margin;
		this.sz = b.sizeZ() + 2 * margin;
		this.level = new byte[sx * sy * sz];
		this.states = new BlockState[level.length];
		for (int i = 0; i < 16; i++) {
			buckets[i] = new IntQueue();
		}
	}

	/** The light of {@code plan}: every cell of its footprint (plus a margin) gets its block light. */
	public static HqLightMap of(BuildPlan plan) {
		Box fp = plan.footprint();
		for (Placement p : plan.placements()) {
			fp = fp.contains(p.rel()) ? fp : fp.union(new Box(p.rel().getX(), p.rel().getY(), p.rel().getZ(),
					p.rel().getX(), p.rel().getY(), p.rel().getZ()));
		}
		HqLightMap m = new HqLightMap(fp, MARGIN);
		m.compute(plan);
		return m;
	}

	/** What a {@link #ofView} map sees in a cell: a state, or null for air. */
	@FunctionalInterface
	interface View {
		@org.jspecify.annotations.Nullable BlockState at(int x, int y, int z);
	}

	/**
	 * The light of an arbitrary view of the cells in {@code bounds} (plus {@code margin}): every cell is
	 * read from {@code view}, sources are whatever emits there. Cells outside read as dark and empty.
	 */
	static HqLightMap ofView(Box bounds, int margin, View view) {
		HqLightMap m = new HqLightMap(bounds, margin);
		for (int iy = 0; iy < m.sy; iy++) {
			for (int iz = 0; iz < m.sz; iz++) {
				for (int ix = 0; ix < m.sx; ix++) {
					BlockState s = view.at(ix + m.minX, iy + m.minY, iz + m.minZ);
					if (s != null && !s.isAir()) {
						int i = (iy * m.sz + iz) * m.sx + ix;
						m.states[i] = s;
						m.seed(i, s.getLightEmission());
					}
				}
			}
		}
		m.propagate();
		return m;
	}

	private int index(int x, int y, int z) {
		int ix = x - minX;
		int iy = y - minY;
		int iz = z - minZ;
		if (ix < 0 || iy < 0 || iz < 0 || ix >= sx || iy >= sy || iz >= sz) {
			return -1;
		}
		return (iy * sz + iz) * sx + ix;
	}

	private void compute(BuildPlan plan) {
		for (Placement p : plan.placements()) {
			int i = index(p.rel().getX(), p.rel().getY(), p.rel().getZ());
			states[i] = p.state();
			seed(i, p.state().getLightEmission());
		}
		propagate();
	}

	private void seed(int i, int e) {
		if (e > (level[i] & 0xff)) {
			level[i] = (byte) e;
			buckets[e].add(i);
		}
	}

	/**
	 * Puts a light source in a cell that held an opaque block (a lamp let into a floor, wall or
	 * ceiling): light only grows, so the map stays exact without recomputing everything.
	 */
	void addSource(net.minecraft.core.BlockPos p, BlockState lamp) {
		int i = index(p.getX(), p.getY(), p.getZ());
		if (i < 0) {
			throw new IllegalArgumentException("outside the light map: " + p);
		}
		states[i] = lamp;
		seed(i, lamp.getLightEmission());
		propagate();
	}

	/**
	 * What a lamp at {@code k} (swapped for the block there) would light, without placing it: the
	 * cells {@code counts} accepts that would go from under {@link #MIN_LEVEL} to at least it.
	 * Returns -1 when {@code must} would still be under {@link #MIN_LEVEL}.
	 */
	int gain(net.minecraft.core.BlockPos k, BlockState lamp, java.util.function.LongPredicate counts, net.minecraft.core.BlockPos must) {
		int start = index(k.getX(), k.getY(), k.getZ());
		int e = lamp.getLightEmission();
		if (start < 0 || e < MIN_LEVEL) {
			return -1;
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		java.util.HashMap<Integer, Integer> lv = new java.util.HashMap<>();
		java.util.ArrayDeque<Integer> todo = new java.util.ArrayDeque<>();
		lv.put(start, e);
		todo.add(start);
		Direction[] dirs = Direction.values();
		while (!todo.isEmpty()) {
			int i = todo.poll();
			int l = lv.get(i);
			int ix = i % sx;
			int iz = (i / sx) % sz;
			int iy = i / (sx * sz);
			BlockState from = i == start ? lamp : states[i] == null ? air : states[i];
			for (Direction d : dirs) {
				int nx = ix + d.getStepX();
				int ny = iy + d.getStepY();
				int nz = iz + d.getStepZ();
				if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
					continue;
				}
				int n = (ny * sz + nz) * sx + nx;
				BlockState to = states[n] == null ? air : states[n];
				int nl = l - LightEngine.getLightDampeningInto(from, to, d, Math.max(1, to.getLightDampening()));
				if (nl >= MIN_LEVEL && nl > lv.getOrDefault(n, 0)) {
					lv.put(n, nl);
					todo.add(n);
				}
			}
		}
		int mi = index(must.getX(), must.getY(), must.getZ());
		if (mi < 0 || lv.getOrDefault(mi, 0) < MIN_LEVEL) {
			return -1;
		}
		int gain = 0;
		for (java.util.Map.Entry<Integer, Integer> en : lv.entrySet()) {
			int i = en.getKey();
			if ((level[i] & 0xff) < MIN_LEVEL) {
				int x = i % sx + minX;
				int z = (i / sx) % sz + minZ;
				int y = i / (sx * sz) + minY;
				if (counts.test(net.minecraft.core.BlockPos.asLong(x, y, z))) {
					gain++;
				}
			}
		}
		return gain;
	}

	private void propagate() {
		BlockState air = Blocks.AIR.defaultBlockState();
		Direction[] dirs = Direction.values();
		for (int l = 15; l >= 1; l--) {
			IntQueue q = buckets[l];
			while (!q.isEmpty()) {
				int i = q.poll();
				if ((level[i] & 0xff) != l) {
					continue;
				}
				int ix = i % sx;
				int iz = (i / sx) % sz;
				int iy = i / (sx * sz);
				BlockState from = states[i] == null ? air : states[i];
				for (Direction d : dirs) {
					int nx = ix + d.getStepX();
					int ny = iy + d.getStepY();
					int nz = iz + d.getStepZ();
					if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
						continue;
					}
					int n = (ny * sz + nz) * sx + nx;
					BlockState to = states[n] == null ? air : states[n];
					int damp = LightEngine.getLightDampeningInto(from, to, d, Math.max(1, to.getLightDampening()));
					int nl = l - damp;
					if (nl > (level[n] & 0xff)) {
						level[n] = (byte) nl;
						buckets[nl].add(n);
					}
				}
			}
		}
	}

	/** Block light at a cell (0 far outside the plan). */
	public int at(int x, int y, int z) {
		int i = index(x, y, z);
		return i < 0 ? 0 : level[i];
	}

	/** The cell's state as the map sees it (air where the plan sets nothing). */
	BlockState state(int x, int y, int z) {
		int i = index(x, y, z);
		BlockState s = i < 0 ? null : states[i];
		return s == null ? Blocks.AIR.defaultBlockState() : s;
	}

	public int at(net.minecraft.core.BlockPos p) {
		return at(p.getX(), p.getY(), p.getZ());
	}

	/** A growable FIFO of ints (the flood fill touches millions of cells: no boxing). */
	private static final class IntQueue {
		private int[] a = new int[64];
		private int head;
		private int tail;

		void add(int v) {
			if (tail == a.length) {
				if (head > a.length / 2) {
					System.arraycopy(a, head, a, 0, tail - head);
					tail -= head;
					head = 0;
				} else {
					a = java.util.Arrays.copyOf(a, a.length * 2);
				}
			}
			a[tail++] = v;
		}

		boolean isEmpty() {
			return head == tail;
		}

		int poll() {
			int v = a[head++];
			if (head == tail) {
				head = 0;
				tail = 0;
			}
			return v;
		}
	}
}
