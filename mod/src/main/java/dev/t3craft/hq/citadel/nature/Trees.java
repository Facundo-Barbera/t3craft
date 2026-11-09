package dev.t3craft.hq.citadel.nature;

import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.nature.Flora.hash;

import dev.t3craft.hq.citadel.CitadelCanvas;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;

/**
 * The trees of the citadel: six deterministic species (three cherries, an azalea tree, a birch and a
 * small oak), each with its own trunk and crown and a base with roots (rooted dirt, root logs lying
 * on the ground, moss and podzol patches, and petals, azalea bushes or ferns round the foot).
 *
 * <p>A {@link Tree} is a pure value: a list of cells relative to the foot of the trunk ({@code dx, dz}
 * from the trunk, {@code y = 1} is the first block of the trunk, {@code y = 0} is the ground it
 * stands on). Which species and which shape a tree takes is a function of the seed (by default the
 * hash of its cell), so the same plan is always the same forest. Every crown is built as discs of
 * leaves with a few cells dropped at the rim, then pruned: a leaf that does not touch (through other
 * leaves or logs) a log is never placed, so there are no floating leaves.
 *
 * <ul>
 * <li>{@link #tree(CitadelCanvas, int, int)}: a tree of the mixed forest on a ground cell (barrios
 *     courtyards, empty hall slots).</li>
 * <li>{@link #tree(CitadelCanvas, int, int, Species, int)}: a given species and seed.</li>
 * <li>{@link #cherry(int)}: a cherry species picked by seed (the patio trees).</li>
 * <li>{@link #placeColumn}: one column of a tree, for pieces that run column by column.</li>
 * </ul>
 */
public final class Trees {
	private Trees() {
	}

	public static final int GROUND = 0;
	public static final int BODY = 1;
	public static final int PLANT = 2;

	public enum Species {
		CHERRY_ROUND, CHERRY_WEEPING, CHERRY_SLENDER, AZALEA, BIRCH, OAK
	}

	/** A cell of a tree: {@code layer} {@link #GROUND} (replaces the floor), {@link #BODY} (log or leaf) or {@link #PLANT} (needs soil). */
	public record Cell(int dx, int y, int dz, BlockState state, int layer) {
	}

	public record Tree(Species species, List<Cell> cells) {
	}

	private static final Species[] MIX = {Species.CHERRY_ROUND, Species.CHERRY_WEEPING, Species.AZALEA, Species.CHERRY_SLENDER, Species.BIRCH,
			Species.CHERRY_ROUND, Species.OAK};
	private static final Species[] CHERRIES = {Species.CHERRY_ROUND, Species.CHERRY_WEEPING, Species.CHERRY_SLENDER};
	private static final int[] DX = {1, 0, -1, 0};
	private static final int[] DZ = {0, 1, 0, -1};

	/** A species of the mixed forest picked by {@code seed}. */
	public static Species pick(int seed) {
		return MIX[Math.floorMod(seed, MIX.length)];
	}

	/** A cherry species picked by {@code seed}. */
	public static Species cherry(int seed) {
		return CHERRIES[Math.floorMod(seed, CHERRIES.length)];
	}

	// ------------------------------------------------------------------ placing

	/** A tree of the mixed forest on the ground cell {@code (x, z)}. */
	public static void tree(CitadelCanvas c, int x, int z) {
		int seed = hash(x, z, 7);
		tree(c, x, z, pick(seed >> 4), seed);
	}

	/** A tree of {@code species} and {@code seed} with its foot at {@code (x, z)}. */
	public static void tree(CitadelCanvas c, int x, int z, Species species, int seed) {
		Tree t = build(species, seed);
		c.rigid(x, z);
		for (Cell cell : t.cells()) {
			placeCell(c, x, z, cell);
		}
		c.unrigid();
	}

	/** One column of a tree whose foot is at {@code (tx, tz)}: the cells at offset {@code (x - tx, z - tz)}. */
	public static void placeColumn(CitadelCanvas c, Tree t, int tx, int tz, int x, int z) {
		c.rigid(tx, tz);
		int dx = x - tx;
		int dz = z - tz;
		for (Cell cell : t.cells()) {
			if (cell.dx() == dx && cell.dz() == dz) {
				placeCell(c, tx, tz, cell);
			}
		}
		c.unrigid();
	}

	/** True when the offset {@code (dx, dz)} can hold a cell of this tree. */
	public static boolean reaches(int dx, int dz) {
		return Math.abs(dx) <= Dims.treeReach() && Math.abs(dz) <= Dims.treeReach();
	}

	private static void placeCell(CitadelCanvas c, int x, int z, Cell cell) {
		int px = x + cell.dx();
		int pz = z + cell.dz();
		switch (cell.layer()) {
			case GROUND -> {
				BlockState cur = c.at(px, 0, pz);
				if (cur != null && cur != LIGHT && !cur.is(Blocks.WATER)) {
					c.set(px, 0, pz, cell.state());
				}
			}
			case BODY -> {
				if (Flora.free(c, px, cell.y(), pz)) {
					c.set(px, cell.y(), pz, cell.state());
				}
			}
			default -> Flora.plant(c, px, pz, cell.state());
		}
	}

	// ------------------------------------------------------------------ building a tree

	/** The tree of a species and seed, as cells relative to the foot of its trunk. */
	public static Tree build(Species sp, int seed) {
		Shape s = new Shape(seed);
		switch (sp) {
			case CHERRY_ROUND -> cherryRound(s);
			case CHERRY_WEEPING -> cherryWeeping(s);
			case CHERRY_SLENDER -> cherrySlender(s);
			case AZALEA -> azalea(s);
			case BIRCH -> birch(s);
			case OAK -> oak(s);
		}
		return new Tree(sp, s.finish());
	}

	private static long key(int dx, int y, int dz) {
		return ((long) (dx + 32) << 40) | ((long) (y + 32) << 20) | (dz + 32);
	}

	private static BlockState logState(Block block, Direction.Axis axis) {
		return block.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	/** The cells being gathered while a tree is built. */
	private static final class Shape {
		final int seed;
		final Map<Long, Cell> ground = new LinkedHashMap<>();
		final Map<Long, Cell> body = new LinkedHashMap<>();
		final Map<Long, Cell> plants = new LinkedHashMap<>();
		final Set<Long> logs = new HashSet<>();

		Shape(int seed) {
			this.seed = seed;
		}

		void ground(int dx, int dz, BlockState s) {
			ground.put(key(dx, 0, dz), new Cell(dx, 0, dz, s, GROUND));
		}

		void plant(int dx, int dz, BlockState s) {
			plants.put(key(dx, 1, dz), new Cell(dx, 1, dz, s, PLANT));
		}

		void log(int dx, int y, int dz, BlockState s) {
			body.put(key(dx, y, dz), new Cell(dx, y, dz, s, BODY));
			logs.add(key(dx, y, dz));
		}

		void trunk(Block block, int from, int to) {
			for (int y = from; y <= to; y++) {
				log(0, y, 0, logState(block, Direction.Axis.Y));
			}
		}

		void leaf(int dx, int y, int dz, BlockState s) {
			if (!logs.contains(key(dx, y, dz))) {
				body.put(key(dx, y, dz), new Cell(dx, y, dz, s, BODY));
			}
		}

		int h(int dx, int dz, int salt) {
			return hash(dx, dz, seed * 31 + salt);
		}

		/** A disc of leaves of radius {@code r} at height {@code y}; {@code drop}% of the rim is left out. */
		void disc(int y, double r, int drop, IntFunction<BlockState> pick) {
			r *= Dims.TREE;
			int n = (int) Math.ceil(r);
			double inner = Math.max(0, r - 1);
			for (int dx = -n; dx <= n; dx++) {
				for (int dz = -n; dz <= n; dz++) {
					double d2 = dx * dx + dz * dz;
					if (d2 > r * r) {
						continue;
					}
					if (d2 > inner * inner && drop > 0 && h(dx, dz, y * 7 + 1) % 100 < drop) {
						continue;
					}
					leaf(dx, y, dz, pick.apply(h(dx, dz, y * 13 + 99)));
				}
			}
		}

		/** The cells, ground first, then the body from the bottom up and without any leaf cut off from the trunk, then the plants. */
		List<Cell> finish() {
			Set<Long> seen = new HashSet<>(logs);
			ArrayDeque<Long> todo = new ArrayDeque<>(logs);
			while (!todo.isEmpty()) {
				long k = todo.poll();
				int dx = (int) ((k >> 40) & 0xFFFFF) - 32;
				int y = (int) ((k >> 20) & 0xFFFFF) - 32;
				int dz = (int) (k & 0xFFFFF) - 32;
				int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
				for (int[] d : around) {
					long n = key(dx + d[0], y + d[1], dz + d[2]);
					if (body.containsKey(n) && seen.add(n)) {
						todo.add(n);
					}
				}
			}
			List<Cell> out = new ArrayList<>(ground.values());
			List<Cell> bodyCells = new ArrayList<>();
			for (Map.Entry<Long, Cell> e : body.entrySet()) {
				if (seen.contains(e.getKey())) {
					bodyCells.add(e.getValue());
				}
			}
			bodyCells.sort((p, q) -> Integer.compare(p.y(), q.y()));
			out.addAll(bodyCells);
			out.addAll(plants.values());
			return out;
		}

		/**
		 * The foot of the tree: rooted dirt under the trunk, root logs lying beside it, patches of moss,
		 * podzol or coarse dirt, and a {@code mode} of plants round it (0 petals, 1 azalea bushes, 2 ferns
		 * and grass).
		 */
		void roots(Block rootLog, int mode) {
			ground(0, 0, b(Blocks.ROOTED_DIRT));
			int mask = rootLog == null ? 0 : 1 + h(5, 5, 3) % 14;
			for (int i = 0; i < 4; i++) {
				int dx = DX[i];
				int dz = DZ[i];
				if ((mask >> i & 1) != 0) {
					ground(dx, dz, b(Blocks.ROOTED_DIRT));
					log(dx, 1, dz, logState(rootLog, dx != 0 ? Direction.Axis.X : Direction.Axis.Z));
				} else {
					switch (h(dx, dz, 21) % 4) {
						case 0 -> ground(dx, dz, MOSS);
						case 1 -> ground(dx, dz, b(Blocks.PODZOL));
						case 2 -> ground(dx, dz, b(Blocks.COARSE_DIRT));
						default -> {
						}
					}
				}
			}
			for (int dx = -1; dx <= 1; dx += 2) {
				for (int dz = -1; dz <= 1; dz += 2) {
					if (h(dx, dz, 22) % 3 == 0) {
						ground(dx, dz, MOSS);
					}
				}
			}
			int n = Dims.treeReach();
			for (int dx = -n; dx <= n; dx++) {
				for (int dz = -n; dz <= n; dz++) {
					int d2 = dx * dx + dz * dz;
					if (d2 == 0 || d2 > 10 * Dims.TREE * Dims.TREE || plants.containsKey(key(dx, 1, dz)) || logs.contains(key(dx, 1, dz))) {
						continue;
					}
					int hh = h(dx, dz, 41);
					switch (mode) {
						case 0 -> {
							if (hh % 100 < 30) {
								plant(dx, dz, Flora.petals(hh >> 8));
							}
						}
						case 1 -> {
							if (d2 <= 2 && hh % 100 < 55) {
								plant(dx, dz, b(hh % 2 == 0 ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA));
							} else if (d2 <= 5 && hh % 100 < 22) {
								plant(dx, dz, b(Blocks.FERN));
							}
						}
						default -> {
							if (d2 <= 5 && hh % 100 < 30) {
								plant(dx, dz, b(hh % 3 == 0 ? Blocks.FERN : Blocks.SHORT_GRASS));
							}
						}
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ species

	/** A round cherry: trunk 4 or 5, a crown of four discs over it. */
	private static void cherryRound(Shape s) {
		int top = Dims.sy(4) + (s.seed & 1);
		s.trunk(Blocks.CHERRY_LOG, 1, top);
		IntFunction<BlockState> lf = h -> leaves(Blocks.CHERRY_LEAVES);
		s.disc(top, 2.0, 25, lf);
		s.disc(top + 1, 2.7, 22, lf);
		s.disc(top + 2, 2.1, 20, lf);
		s.disc(top + 3, 1.2, 0, lf);
		s.roots(Blocks.CHERRY_LOG, 0);
	}

	/** A weeping cherry: a forked crown, a wide flat canopy and leaves hanging from its rim. */
	private static void cherryWeeping(Shape s) {
		int top = Dims.sy(5);
		s.trunk(Blocks.CHERRY_LOG, 1, top - 1);
		s.log(0, top, 0, logState(Blocks.CHERRY_LOG, Direction.Axis.Y));
		int axisSwap = s.seed >> 3 & 1;
		for (int sg = -1; sg <= 1; sg += 2) {
			int dx = axisSwap == 0 ? sg : 0;
			int dz = axisSwap == 0 ? 0 : sg;
			s.log(dx, top, dz, logState(Blocks.CHERRY_LOG, axisSwap == 0 ? Direction.Axis.X : Direction.Axis.Z));
			s.log(dx, top + 1, dz, logState(Blocks.CHERRY_LOG, Direction.Axis.Y));
		}
		IntFunction<BlockState> lf = h -> leaves(Blocks.CHERRY_LEAVES);
		s.disc(top + 1, 3.2, 12, lf);
		s.disc(top + 2, 2.4, 18, lf);
		s.disc(top + 3, 1.5, 0, lf);
		int n = Dims.treeReach();
		double rim = 3.2 * Dims.TREE;
		for (int dx = -n; dx <= n; dx++) {
			for (int dz = -n; dz <= n; dz++) {
				double d2 = dx * dx + dz * dz;
				if (d2 < (rim - 1) * (rim - 1) + 0.2 || d2 > rim * rim) {
					continue;
				}
				int h = s.h(dx, dz, 61);
				if (s.body.containsKey(key(dx, top + 1, dz)) && h % 100 < 60) {
					s.leaf(dx, top, dz, leaves(Blocks.CHERRY_LEAVES));
					if (h % 100 < 28) {
						s.leaf(dx, top - 1, dz, leaves(Blocks.CHERRY_LEAVES));
					}
				}
			}
		}
		s.roots(Blocks.CHERRY_LOG, 0);
	}

	/** A tall slim cherry: trunk 6 and an oval crown. */
	private static void cherrySlender(Shape s) {
		int top = Dims.sy(6);
		s.trunk(Blocks.CHERRY_LOG, 1, top);
		IntFunction<BlockState> lf = h -> leaves(Blocks.CHERRY_LEAVES);
		s.disc(top - 1, 1.5, 0, lf);
		s.disc(top, 2.3, 20, lf);
		s.disc(top + 1, 2.5, 20, lf);
		s.disc(top + 2, 2.3, 20, lf);
		s.disc(top + 3, 1.6, 0, lf);
		s.disc(top + 4, 0.9, 0, lf);
		s.roots(Blocks.CHERRY_LOG, 0);
	}

	/** An azalea tree: oak trunk of 3 and a round crown of azalea and flowering azalea leaves over rooted dirt. */
	private static void azalea(Shape s) {
		s.trunk(Blocks.OAK_LOG, 1, Dims.sy(3));
		IntFunction<BlockState> lf = h -> leaves(h % 100 < 40 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES);
		int t = Dims.sy(3);
		s.disc(t, 2.3, 25, lf);
		s.disc(t + 1, 2.8, 20, lf);
		s.disc(t + 2, 2.2, 20, lf);
		s.disc(t + 3, 1.3, 0, lf);
		s.roots(null, 1);
	}

	/** A birch: a pale trunk of 6 and a tall, narrow crown. */
	private static void birch(Shape s) {
		int top = Dims.sy(6) + (s.seed & 1);
		s.trunk(Blocks.BIRCH_LOG, 1, top);
		IntFunction<BlockState> lf = h -> leaves(Blocks.BIRCH_LEAVES);
		s.disc(top - 2, 2.1, 30, lf);
		s.disc(top - 1, 2.3, 25, lf);
		s.disc(top, 2.1, 25, lf);
		s.disc(top + 1, 1.5, 0, lf);
		s.disc(top + 2, 0.9, 0, lf);
		s.ground(0, 0, b(Blocks.PODZOL));
		s.roots(null, 2);
		s.ground(0, 0, b(Blocks.PODZOL));
	}

	/** A small oak: a trunk of 3 and a broad crown. */
	private static void oak(Shape s) {
		s.trunk(Blocks.OAK_LOG, 1, Dims.sy(3));
		IntFunction<BlockState> lf = h -> leaves(Blocks.OAK_LEAVES);
		int t = Dims.sy(3);
		s.disc(t, 2.2, 25, lf);
		s.disc(t + 1, 2.7, 22, lf);
		s.disc(t + 2, 2.2, 20, lf);
		s.disc(t + 3, 1.2, 0, lf);
		s.roots(Blocks.OAK_LOG, 2);
	}
}
