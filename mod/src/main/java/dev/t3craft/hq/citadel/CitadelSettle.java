package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.SOLID;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * fix/relief-floating: the last net under the citadel. After the relief has lifted the plan, any group of cells that
 * touches nothing that reaches the ground (the same test as {@code PlanReport}'s FLOATING: face-connected cells with none
 * at {@code y <= 0}) is a piece that came out of its support: a roof over posts that ended a block lower, a balcony whose
 * wall was clipped, a pane whose frame is gone. Nobody tolerates a floating block, so each group is settled:
 *
 * <ol>
 * <li><b>It falls</b> up to {@link #MAX_DROP} blocks, as a whole, until a cell touches something grounded (a roof lands on
 *     its posts). It never falls into another cell.</li>
 * <li>Else a <b>small solid group gets a post</b> (a cherry fence or a stone-brick wall) under its lowest cell, down to
 *     the first solid cell or the ground, like the bracket of a balcony.</li>
 * <li>Else a group made only of leaves, panes, lights and plants (things that mean nothing alone) is <b>removed</b>.</li>
 * <li>Anything else stays and shows in the report (FLOATING).</li>
 * </ol>
 *
 * Fixtures (desks, screens, the board) never move. Pure and deterministic: groups are visited in the order of their
 * lowest cell, so the plan is the same on every run.
 */
public final class CitadelSettle {
	private CitadelSettle() {
	}

	/** How far a floating group may fall to find its support. */
	static final int MAX_DROP = 4;
	/** A group up to this many cells gets a post when it cannot fall onto anything. */
	private static final int POST_GROUP = 12;
	/** The longest post (blocks): a floating group higher than this above anything is left to the report. */
	private static final int POST_LEN = 9;

	/** What the last pass did (render diagnostics): groups fallen, propped, removed, left, and the blocks of each. */
	record Result(int fell, int fellBlocks, int propped, int proppedBlocks, int removed, int removedBlocks, int left, int leftBlocks) {
		static final Result NONE = new Result(0, 0, 0, 0, 0, 0, 0, 0);

		@Override
		public String toString() {
			return "floating groups settled: " + fell + " fell onto their support (" + fellBlocks + " blocks), " + propped + " got a post (" + proppedBlocks
					+ "), " + removed + " removed (" + removedBlocks + "), " + left + " left (" + leftBlocks + ")";
		}
	}

	/** Last result of {@link #apply} (render diagnostics). */
	static volatile Result last = Result.NONE;

	/**
	 * fix/settle-sources (render diagnostics): one floating group the last pass handled, where it was (before falling),
	 * who owned the column (the canvas group and zone kind), what it was made of, what happened and how far it fell.
	 */
	public record Source(String group, String zone, String piece, String outcome, int drop, int blocks, int x, int y, int z, String blocksOf) {
	}

	/** The groups of the last pass, in the order they were settled (render diagnostics only). */
	static volatile List<Source> lastSources = List.of();

	/** The groups the last pass handled (render diagnostics). */
	public static List<Source> lastSources() {
		return lastSources;
	}

	/**
	 * The last pass grouped by origin (canvas group + zone kind): {@code [origin] → {groups, fell, blocks, drop sum, propped,
	 * removed, left}}, most groups first (ties by name so the order is stable).
	 */
	public static Map<String, int[]> lastByOrigin() {
		Map<String, int[]> by = new TreeMap<>();
		for (Source s : lastSources) {
			int[] a = by.computeIfAbsent(s.piece().isEmpty() ? s.group() + " / " + s.zone() : s.piece(), k -> new int[7]);
			a[0]++;
			a[2] += s.blocks();
			switch (s.outcome()) {
				case "fell" -> {
					a[1]++;
					a[3] += s.drop();
				}
				case "post" -> a[4]++;
				case "removed" -> a[5]++;
				default -> a[6]++;
			}
		}
		List<Map.Entry<String, int[]>> es = new ArrayList<>(by.entrySet());
		es.sort((p, q) -> p.getValue()[0] != q.getValue()[0] ? Integer.compare(q.getValue()[0], p.getValue()[0]) : p.getKey().compareTo(q.getKey()));
		Map<String, int[]> out = new LinkedHashMap<>();
		for (var e : es) {
			out.put(e.getKey(), e.getValue());
		}
		return out;
	}

	private static Source source(CitadelCanvas c, List<Long> g, String outcome, int drop) {
		long low = g.get(0);
		for (long k : g) {
			if (BlockPos.getY(k) < BlockPos.getY(low)) {
				low = k;
			}
		}
		int x = BlockPos.getX(low);
		int y = BlockPos.getY(low);
		int z = BlockPos.getZ(low);
		String group;
		String zone;
		try {
			group = CitadelFinish.groupOf(c, x, y, z);
			zone = c.zoneAt(x, z).kind().name();
		} catch (RuntimeException e) {
			group = "?";
			zone = "?";
		}
		Map<String, Integer> kinds = new TreeMap<>();
		for (long k : g) {
			BlockState s = c.cells.get(k);
			if (s != null) {
				kinds.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath(), 1, Integer::sum);
			}
		}
		String piece = "";
		for (CitadelCanvas.Piece pc : c.pieces) {
			if (x >= pc.x() && x < pc.x() + pc.sx() && z >= pc.z() && z < pc.z() + pc.sz()) {
				piece = pc.id();
			}
		}
		StringBuilder sb = new StringBuilder();
		kinds.entrySet().stream().sorted((p, q) -> q.getValue() - p.getValue()).limit(3).forEach(e -> sb.append(sb.length() == 0 ? "" : ",").append(e.getKey()));
		return new Source(group, zone, piece, outcome, drop, g.size(), x, y, z, sb.toString());
	}

	/** One line about the last pass (render diagnostics, {@code PlanReport}'s split pieces). */
	public static String lastSummary() {
		return last.toString();
	}

	/** Groups that fell, got a post, were removed or were left, then the blocks of each (render diagnostics). */
	public static int[] lastCounts() {
		Result r = last;
		return new int[] {r.fell(), r.fellBlocks(), r.propped(), r.proppedBlocks(), r.removed(), r.removedBlocks(), r.left(), r.leftBlocks()};
	}

	/**
	 * A cell that is something. The invisible light block ({@code minecraft:light}) is not: like {@code PlanReport}'s
	 * FLOATING test, the settling ignores it. It hangs in the air by design (one cell above the walking cell), so before
	 * this the hub's ~390 lights fell a block each, into the walking cells.
	 */
	private static boolean present(@Nullable BlockState s) {
		return s != null && !s.isAir() && !s.is(Blocks.LIGHT);
	}

	private static boolean solid(@Nullable BlockState s) {
		return present(s) && !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/** Leaves, panes, lights and plants: nothing that stands for a piece on its own. */
	private static boolean trivial(BlockState s) {
		return s.getBlock() instanceof LeavesBlock || s.getBlock() instanceof IronBarsBlock || s.getLightEmission() > 0
				|| s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
	}

	/** Settles every floating group of the canvas (see the class comment). */
	static void apply(CitadelCanvas c) {
		last = Result.NONE;
		lastSources = List.of();
		Map<Long, BlockState> cells = c.cells;
		Set<Long> seen = new HashSet<>(cells.size() * 2);
		List<List<Long>> floating = new ArrayList<>();
		for (long start : cells.keySet()) {
			if (!present(cells.get(start)) || !seen.add(start)) {
				continue;
			}
			List<Long> group = new ArrayList<>();
			ArrayDeque<Long> q = new ArrayDeque<>();
			q.add(start);
			boolean grounded = false;
			while (!q.isEmpty()) {
				long k = q.poll();
				group.add(k);
				grounded |= BlockPos.getY(k) <= 0;
				for (Direction d : Direction.values()) {
					long n = BlockPos.offset(k, d);
					if (present(cells.get(n)) && seen.add(n)) {
						q.add(n);
					}
				}
			}
			if (!grounded) {
				floating.add(group);
			}
		}
		if (floating.isEmpty()) {
			return;
		}
		// deterministic order: by the lowest cell of the group, then its key
		for (List<Long> g : floating) {
			g.sort(null);
		}
		floating.sort((a, z) -> {
			int ya = lowestY(a);
			int yz = lowestY(z);
			return ya != yz ? Integer.compare(ya, yz) : Long.compare(a.get(0), z.get(0));
		});
		Set<Long> loose = new HashSet<>();
		for (List<Long> g : floating) {
			loose.addAll(g);
		}
		int[] n = new int[8];
		List<Source> sources = new ArrayList<>();
		for (List<Long> g : floating) {
			if (touchesFixture(c, g)) {
				sources.add(source(c, g, "left", 0));
				count(n, 6, g.size());
				continue;
			}
			int drop = fall(c, g, loose);
			if (drop > 0) {
				sources.add(source(c, g, "fell", drop));
				move(c, g, drop);
				loose.removeAll(g);
				count(n, 0, g.size());
				continue;
			}
			Source before = source(c, g, "post", 0);
			if (g.size() <= POST_GROUP && prop(c, g, loose)) {
				sources.add(before);
				loose.removeAll(g);
				count(n, 2, g.size());
				continue;
			}
			if (allTrivial(c, g)) {
				sources.add(source(c, g, "removed", 0));
				for (long k : g) {
					remove(c, k);
				}
				loose.removeAll(g);
				count(n, 4, g.size());
				continue;
			}
			sources.add(source(c, g, "left", 0));
			count(n, 6, g.size());
		}
		lastSources = List.copyOf(sources);
		last = new Result(n[0], n[1], n[2], n[3], n[4], n[5], n[6], n[7]);
	}

	private static void count(int[] n, int at, int blocks) {
		n[at]++;
		n[at + 1] += blocks;
	}

	private static int lowestY(List<Long> g) {
		int y = Integer.MAX_VALUE;
		for (long k : g) {
			y = Math.min(y, BlockPos.getY(k));
		}
		return y;
	}

	private static boolean touchesFixture(CitadelCanvas c, List<Long> g) {
		for (long k : g) {
			if (c.fixtures.contains(k)) {
				return true;
			}
		}
		return false;
	}

	private static boolean allTrivial(CitadelCanvas c, List<Long> g) {
		for (long k : g) {
			if (!trivial(c.cells.get(k))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * How many blocks the group falls to rest on something grounded (0 when it finds nothing within {@link #MAX_DROP}
	 * or would have to go through a cell). A group rests when one of its cells ends up face to face with a cell that
	 * is not itself floating.
	 */
	private static int fall(CitadelCanvas c, List<Long> g, Set<Long> loose) {
		Set<Long> mine = new HashSet<>(g);
		for (int d = 1; d <= MAX_DROP; d++) {
			boolean blocked = false;
			boolean rests = false;
			for (long k : g) {
				int y = BlockPos.getY(k) - d;
				if (y < 1) {
					blocked = true;
					break;
				}
				long moved = BlockPos.offset(k, 0, -d, 0);
				if (present(c.cells.get(moved)) && !mine.contains(moved)) {
					blocked = true;
					break;
				}
				for (Direction dir : Direction.values()) {
					long nb = BlockPos.offset(moved, dir);
					if (!mine.contains(nb) && solid(c.cells.get(nb)) && !loose.contains(nb)) {
						rests = true;
					}
				}
			}
			if (blocked) {
				return 0;
			}
			if (rests) {
				return d;
			}
		}
		return 0;
	}

	/** Moves the whole group {@code d} blocks down (cells, layers, block-entity data). */
	private static void move(CitadelCanvas c, List<Long> g, int d) {
		List<BlockState> states = new ArrayList<>(g.size());
		List<Integer> layers = new ArrayList<>(g.size());
		List<@Nullable CompoundTag> data = new ArrayList<>(g.size());
		for (long k : g) {
			states.add(c.cells.remove(k));
			layers.add(c.layers.getOrDefault(k, SOLID));
			data.add(c.data.remove(k));
			c.layers.remove(k);
		}
		for (int i = 0; i < g.size(); i++) {
			long n = BlockPos.offset(g.get(i), 0, -d, 0);
			c.cells.put(n, states.get(i));
			c.layers.put(n, layers.get(i));
			CompoundTag t = data.get(i);
			if (t != null) {
				c.data.put(n, t);
			}
		}
	}

	private static void remove(CitadelCanvas c, long k) {
		c.cells.remove(k);
		c.layers.remove(k);
		c.data.remove(k);
	}

	/**
	 * A post under the lowest solid cell of a small group, down to the first cell that is already something or to
	 * the ground. False when the post would be longer than {@link #POST_LEN} or the group has nothing to stand on a post.
	 */
	private static boolean prop(CitadelCanvas c, List<Long> g, Set<Long> loose) {
		long best = Long.MIN_VALUE;
		int by = Integer.MAX_VALUE;
		for (long k : g) {
			BlockState s = c.cells.get(k);
			if (!solid(s) || trivial(s)) {
				continue;
			}
			int y = BlockPos.getY(k);
			// the lowest; ties go to the smallest key so the choice is the same on every run
			if (y < by || y == by && k < best) {
				by = y;
				best = k;
			}
		}
		if (best == Long.MIN_VALUE) {
			return false;
		}
		int x = BlockPos.getX(best);
		int z = BlockPos.getZ(best);
		int y = BlockPos.getY(best) - 1;
		List<Long> post = new ArrayList<>();
		while (y >= 1) {
			long p = BlockPos.asLong(x, y, z);
			if (present(c.cells.get(p))) {
				if (loose.contains(p)) {
					return false;
				}
				break; // it stands on something grounded
			}
			post.add(p);
			y--;
			if (post.size() > POST_LEN) {
				return false;
			}
		}
		// y >= 1: stopped at a grounded cell; y < 1: reached the ground (y = 0 holds the paving or soil)
		long foot = post.get(post.size() - 1);
		for (long p : post) {
			c.put(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p), p == foot ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.CHERRY_FENCE), SOLID);
		}
		return !post.isEmpty();
	}
}
