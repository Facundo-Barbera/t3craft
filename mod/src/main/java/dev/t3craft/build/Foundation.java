package dev.t3craft.build;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * feat/build-near-player: splits a plan into its <b>foundation</b> (ground, fill and floors: placed in
 * fast layers, no builder animation, {@link FoundationCursor}) and the <b>visible</b> rest the crew
 * builds block by block (facades, roofs, decoration). Pure; runs off-thread (it reads block state
 * properties only, never the world).
 *
 * <p>An entry is foundation when its block is a plain opaque full cube (no block entity, no light, not
 * a falling block), its cell appears once in the plan, it is <b>supported</b> and one of these holds:
 * <ul>
 *   <li><b>terrain</b>: natural ground (grass block, dirt, stone, tuff, deepslate, moss...): the void
 *   island, the plinth, the lawns;</li>
 *   <li><b>bottom layer</b> of the plan (the floor of a part);</li>
 *   <li><b>hidden</b>: all six neighbours are such full cubes of the plan (podium and wall fill);</li>
 *   <li><b>surface</b> of a foundation stack: nothing solid of the plan above it (the podium's top,
 *   a paved ground layer).</li>
 * </ul>
 * Supported = bottom layer, or the cell below is foundation; terrain also spreads sideways inside a
 * layer (an island column whose bottom hangs from its neighbour's). Foundation is placed bottom-up, a
 * whole layer before the next, so nothing ever floats; the visible rest treats it as solid ground
 * ({@link CrewPlanner#plan} with {@code external}). A wall course stacked on the floor is not
 * foundation (it has a block above it and is no terrain), so the builders keep the walls.
 */
public final class Foundation {
	/** Fewer foundation entries than this: not worth a phase, everything goes to the crew. */
	public static final int MIN_ENTRIES = 64;

	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private Foundation() {
	}

	/**
	 * @param foundation plan indices placed fast, bottom-up (by y, then plan order)
	 * @param rest       plan indices the crew builds, in plan order
	 * @param cells      foundation cells, plan-relative, packed with {@link CrewPlanner#key}
	 */
	public record Split(int[] foundation, int[] rest, LongOpenHashSet cells) {
		public boolean any() {
			return foundation.length > 0;
		}
	}

	/** Natural ground blocks (lazily: {@code Blocks} must wait for the vanilla bootstrap). */
	private static final class Terrain {
		static final Set<Block> BLOCKS = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL,
				Blocks.MYCELIUM, Blocks.STONE, Blocks.TUFF, Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.ANDESITE, Blocks.DIORITE,
				Blocks.GRANITE, Blocks.CALCITE, Blocks.MOSS_BLOCK, Blocks.MUD, Blocks.PACKED_MUD, Blocks.CLAY, Blocks.COBBLESTONE,
				Blocks.MOSSY_COBBLESTONE, Blocks.SMOOTH_BASALT);
	}

	static boolean terrain(BlockState s) {
		return Terrain.BLOCKS.contains(s.getBlock());
	}

	/** A plain opaque full cube that is safe to set without neighbour updates or animation. */
	static boolean plain(Placement p) {
		BlockState s = p.state();
		if (s.isAir() || p.hasBlockEntity() || s.hasBlockEntity() || s.getLightEmission() > 0 || s.getBlock() instanceof FallingBlock) {
			return false;
		}
		return s.isSolidRender() && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	public static Split of(BuildPlan plan) {
		int n = plan.size();
		if (n == 0) {
			return new Split(new int[0], new int[0], new LongOpenHashSet());
		}
		int[] x = new int[n];
		int[] y = new int[n];
		int[] z = new int[n];
		Long2IntOpenHashMap at = new Long2IntOpenHashMap(n);
		at.defaultReturnValue(-1);
		LongOpenHashSet dup = new LongOpenHashSet();
		int minY = Integer.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			BlockPos p = plan.get(i).pos();
			x[i] = p.getX();
			y[i] = p.getY();
			z[i] = p.getZ();
			minY = Math.min(minY, y[i]);
			long k = CrewPlanner.key(x[i], y[i], z[i]);
			if (at.put(k, i) >= 0) {
				dup.add(k);
			}
		}
		boolean[] mat = new boolean[n];
		boolean[] ter = new boolean[n];
		boolean[] airAt = new boolean[n];
		for (int i = 0; i < n; i++) {
			Placement p = plan.get(i);
			airAt[i] = p.state().isAir();
			mat[i] = !dup.contains(CrewPlanner.key(x[i], y[i], z[i])) && plain(p);
			ter[i] = mat[i] && terrain(p.state());
		}
		// candidates: terrain, bottom layer, hidden, or surface (support checked below)
		boolean[] cand = new boolean[n];
		for (int i = 0; i < n; i++) {
			if (!mat[i]) {
				continue;
			}
			if (ter[i] || y[i] == minY) {
				cand[i] = true;
				continue;
			}
			int up = at.get(CrewPlanner.key(x[i], y[i] + 1, z[i]));
			boolean surface = up < 0 || airAt[up];
			cand[i] = surface || hidden(i, x, y, z, at, mat);
		}
		// support, layer by layer (bottom-up): below is foundation, or the bottom layer; terrain spreads sideways
		long[] byLayer = new long[n];
		int m = 0;
		for (int i = 0; i < n; i++) {
			if (cand[i]) {
				byLayer[m++] = ((long) (y[i] - minY) << 32) | i;
			}
		}
		long[] sorted = Arrays.copyOf(byLayer, m);
		Arrays.sort(sorted);
		boolean[] found = new boolean[n];
		int[] queue = new int[Math.max(1, m)];
		int from = 0;
		while (from < m) {
			int ly = (int) (sorted[from] >>> 32);
			int to = from;
			while (to < m && (int) (sorted[to] >>> 32) == ly) {
				to++;
			}
			int qh = 0;
			int qt = 0;
			for (int r = from; r < to; r++) {
				int i = (int) sorted[r];
				int below = at.get(CrewPlanner.key(x[i], y[i] - 1, z[i]));
				if (y[i] == minY || below >= 0 && found[below]) {
					found[i] = true;
					if (ter[i]) {
						queue[qt++] = i;
					}
				}
			}
			while (qh < qt) {
				int i = queue[qh++];
				for (int[] d : SIDES) {
					int j = at.get(CrewPlanner.key(x[i] + d[0], y[i], z[i] + d[1]));
					if (j >= 0 && cand[j] && ter[j] && !found[j] && y[j] == y[i]) {
						found[j] = true;
						if (qt < queue.length) {
							queue[qt++] = j;
						}
					}
				}
			}
			from = to;
		}
		LongArrayList order = new LongArrayList();
		for (int r = 0; r < m; r++) {
			int i = (int) sorted[r];
			if (found[i]) {
				order.add(sorted[r]);
			}
		}
		if (order.size() < MIN_ENTRIES) {
			int[] all = new int[n];
			for (int i = 0; i < n; i++) {
				all[i] = i;
			}
			return new Split(new int[0], all, new LongOpenHashSet());
		}
		// already sorted by (layer, plan index): bottom-up, stable
		int[] fnd = new int[order.size()];
		LongOpenHashSet cells = new LongOpenHashSet(fnd.length);
		for (int r = 0; r < fnd.length; r++) {
			int i = (int) order.getLong(r);
			fnd[r] = i;
			cells.add(CrewPlanner.key(x[i], y[i], z[i]));
		}
		int[] rest = new int[n - fnd.length];
		int w = 0;
		for (int i = 0; i < n; i++) {
			if (!found[i]) {
				rest[w++] = i;
			}
		}
		return new Split(fnd, rest, cells);
	}

	private static boolean hidden(int i, int[] x, int[] y, int[] z, Long2IntOpenHashMap at, boolean[] mat) {
		int[][] faces = {{0, -1, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int[] f : faces) {
			int j = at.get(CrewPlanner.key(x[i] + f[0], y[i] + f[1], z[i] + f[2]));
			if (j < 0 || !mat[j]) {
				return false;
			}
		}
		return true;
	}
}
