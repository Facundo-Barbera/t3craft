package dev.t3craft.mapgen.islands;

import dev.t3craft.build.Placement;
import dev.t3craft.mapgen.Noise;
import dev.t3craft.mapgen.TerrainShape;
import dev.t3craft.mapgen.islands.bridge.BridgeStyle;
import dev.t3craft.mapgen.islands.bridge.DeckProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Plans the bridge between two islands (second cut, #32). Pure and deterministic: the same two islands always
 * give the same bridge.
 *
 * <ol>
 *   <li><b>Where</b>: the straight line between the islands' centres, walked one block per step along its longer
 *       axis. The real edges are found column by column on the field's {@link TerrainShape} (no circle is
 *       assumed): the deck spans from the last ground column of {@code a} to the first of {@code b}. A line
 *       whose deck (or rails) would cross the ground of a third island, or that re-enters an island halfway, has
 *       no bridge.</li>
 *   <li><b>Height</b>: a {@link DeckProfile} from {@code a}'s edge grass to {@code b}'s, walkable without jumping
 *       (half-block slab steps, or stairs when steeper).</li>
 *   <li><b>Look</b>: a {@link BridgeStyle} from length, rise and a hash of the pair; each step gets its supports,
 *       a three-wide deck, a curb and a continuous rail on both sides (an extra rail block where the line shifts
 *       sideways, so the rail never has a diagonal gap), and the style's lights and cables.</li>
 * </ol>
 * Blocks only go into void columns: the steps reaching back over each rim ({@value #OVERLAP}) fill in around a
 * ragged edge without touching the ground.
 */
public final class BridgePlanner {
	/** Half the walkable deck width (3 wide). */
	static final int HALF_WIDTH = 1;
	/** Lateral offset of the curb and rail. */
	static final int RAIL = HALF_WIDTH + 1;
	/** Steps the bridge reaches back over each island's rim (void columns only). */
	static final int OVERLAP = 2;
	/** Height of a suspension tower above the deck. */
	static final int TOWER = 5;

	private static final int VOID = 0;
	private static final int OWN_A = 1;
	private static final int OWN_B = 2;
	private static final int OTHER = 3;

	private BridgePlanner() {
	}

	/** The id of the bridge between two islands, whichever order they are given in. */
	public static String idOf(Island x, Island y) {
		return x.id().compareTo(y.id()) <= 0 ? "b:" + x.id() + "|" + y.id() : "b:" + y.id() + "|" + x.id();
	}

	/**
	 * The bridge between {@code p} and {@code q}, or null when they touch, when the line between them does not
	 * leave one and reach the other through void only, or when the bridge would touch a third island.
	 */
	public static @Nullable Bridge plan(IslandField field, Island p, Island q) {
		Island a = p.id().compareTo(q.id()) <= 0 ? p : q;
		Island b = a == p ? q : p;
		int dx = b.cx() - a.cx();
		int dz = b.cz() - a.cz();
		int n = Math.max(Math.abs(dx), Math.abs(dz));
		if (n == 0) {
			return null;
		}
		boolean alongX = Math.abs(dx) >= Math.abs(dz);
		TerrainShape ground = ground(field, a, b, n);
		int[] xs = new int[n + 1];
		int[] zs = new int[n + 1];
		int[] own = new int[n + 1];
		int firstB = -1;
		for (int i = 0; i <= n; i++) {
			xs[i] = a.cx() + (int) Math.round((double) dx * i / n);
			zs[i] = a.cz() + (int) Math.round((double) dz * i / n);
			own[i] = owner(ground, a, b, xs[i], zs[i]);
			if (firstB < 0 && own[i] == OWN_B) {
				firstB = i;
			}
		}
		if (firstB < 0) {
			return null;
		}
		int lastA = -1;
		for (int i = 0; i < firstB; i++) {
			if (own[i] == OWN_A) {
				lastA = i;
			}
		}
		if (lastA < 0) {
			return null;
		}
		int i0 = lastA + 1;
		int i1 = firstB - 1;
		if (i1 < i0) {
			return null; // the islands touch: nothing to cross
		}
		for (int i = i0; i <= i1; i++) {
			if (own[i] != VOID) {
				return null; // another island (or a bay of one of these) is in the way
			}
			for (int k = -RAIL - 1; k <= RAIL + 1; k++) {
				if (owner(ground, a, b, latX(xs[i], k, alongX), latZ(zs[i], k, alongX)) == OTHER) {
					return null;
				}
			}
		}
		TerrainShape.Span ea = ground.column(xs[lastA], zs[lastA]);
		TerrainShape.Span eb = ground.column(xs[firstB], zs[firstB]);
		if (ea == null || eb == null) {
			return null;
		}
		int len = i1 - i0 + 1;
		boolean steep = DeckProfile.needsStairs(len, ea.top(), eb.top());
		BridgeStyle style = BridgeStyle.pick(a, b, len, eb.top() - ea.top(), steep);
		DeckProfile prof = DeckProfile.of(len, ea.top(), eb.top(), style.sagHalves(len));
		Direction toB = alongX ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		Writer w = new Writer(ground, a.seed(), style, prof, alongX, toB, xs, zs, i0, len);
		int from = Math.max(0, i0 - OVERLAP);
		int to = Math.min(n, i1 + OVERLAP);
		List<Bridge.Step> steps = new ArrayList<>();
		for (int i = from; i <= to; i++) {
			steps.add(w.step(i, from, to));
		}
		BlockPos startA = new BlockPos(xs[i0], prof.floor(0), zs[i0]);
		BlockPos startB = new BlockPos(xs[i1], prof.floor(len - 1), zs[i1]);
		return new Bridge(idOf(a, b), a.id(), b.id(), style, steps, startA, startB, len);
	}

	/** The field's ground around the line from {@code a} to {@code b} (every island touching that window). */
	private static TerrainShape ground(IslandField field, Island a, Island b, int n) {
		int mx = Math.floorDiv(a.cx() + b.cx(), 2);
		int mz = Math.floorDiv(a.cz() + b.cz(), 2);
		return field.generate(new IslandField.Window(mx, mz, n / 2 + RAIL + 4)).shape();
	}

	private static int owner(TerrainShape g, Island a, Island b, int x, int z) {
		if (g.column(x, z) == null) {
			return VOID;
		}
		if (a.contains(x, z, 0)) {
			return OWN_A;
		}
		if (b.contains(x, z, 0)) {
			return OWN_B;
		}
		return OTHER;
	}

	/** x of the cell {@code k} blocks to the side of the line at {@code x}: sideways is z when the line runs along x. */
	private static int latX(int x, int k, boolean alongX) {
		return alongX ? x : x + k;
	}

	private static int latZ(int z, int k, boolean alongX) {
		return alongX ? z + k : z;
	}

	/** Writes the cross-sections of one planned bridge. */
	private static final class Writer {
		private final TerrainShape ground;
		private final long seed;
		private final BridgeStyle style;
		private final DeckProfile prof;
		private final boolean alongX;
		private final Direction toB;
		private final int[] xs;
		private final int[] zs;
		private final int i0;
		private final int len;
		private final Direction.Axis along;
		private final Direction.Axis across;

		Writer(TerrainShape ground, long seed, BridgeStyle style, DeckProfile prof, boolean alongX, Direction toB, int[] xs, int[] zs, int i0,
				int len) {
			this.ground = ground;
			this.seed = seed;
			this.style = style;
			this.prof = prof;
			this.alongX = alongX;
			this.toB = toB;
			this.xs = xs;
			this.zs = zs;
			this.i0 = i0;
			this.len = len;
			this.along = alongX ? Direction.Axis.X : Direction.Axis.Z;
			this.across = alongX ? Direction.Axis.Z : Direction.Axis.X;
		}

		/** The sideways coordinate of the centre line at index {@code i}. */
		private int minor(int i) {
			return alongX ? zs[i] : xs[i];
		}

		/** Cross-section of line index {@code i} ({@code from..to} are the indices the bridge covers). */
		Bridge.Step step(int i, int from, int to) {
			int j = i - i0;
			boolean span = j >= 0 && j < len;
			int jj = Math.max(0, Math.min(len - 1, j));
			int y = prof.floor(jj);
			DeckProfile.Kind kind = span ? prof.kind(jj) : DeckProfile.Kind.FULL;
			double t = len <= 1 ? 0.5 : (double) jj / (len - 1);
			int x = xs[i];
			int z = zs[i];
			List<Placement> out = new ArrayList<>();

			// Rail offsets: both sides, plus one block further out where the line shifts sideways to or from the
			// next step (two rail blocks meeting only at a corner would leave a gap a player can slip through).
			List<Integer> rails = new ArrayList<>(List.of(-RAIL, RAIL));
			if (i < to) {
				int d = minor(i + 1) - minor(i);
				if (d != 0) {
					addOnce(rails, Integer.signum(d) * (RAIL + 1));
				}
			}
			if (i > from) {
				int d = minor(i) - minor(i - 1);
				if (d != 0) {
					addOnce(rails, -Integer.signum(d) * (RAIL + 1));
				}
			}

			// 1. what holds the deck up (placed first: the slice grows from below)
			switch (style) {
				case ARCH -> {
					if (span) {
						int depth = archDepth(t);
						for (int d = depth; d >= 1; d--) {
							for (int k = -RAIL; k <= RAIL; k++) {
								put(out, x, y - d, z, k, stone(latX(x, k, alongX), y - d, latZ(z, k, alongX)));
							}
						}
					}
				}
				case PLANK -> {
					if (span && legs(jj)) {
						for (int k = -HALF_WIDTH; k <= HALF_WIDTH; k++) {
							put(out, x, y - 1, z, k, axis(Blocks.SPRUCE_LOG, across));
						}
						for (int side = -1; side <= 1; side += 2) {
							for (int h = 1; h <= 3; h++) {
								put(out, x, y - h, z, side * RAIL, axis(Blocks.SPRUCE_LOG, Direction.Axis.Y));
							}
							put(out, x, y - 4, z, side * RAIL, Blocks.SPRUCE_FENCE.defaultBlockState());
						}
					}
				}
				case SUSPENSION -> {
					if (span && hangs(jj)) {
						for (int side = -1; side <= 1; side += 2) {
							put(out, x, y - 1, z, side * RAIL, axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
						}
					}
				}
			}

			// 2. the walkable deck
			BlockState deck = deck(kind);
			for (int k = -HALF_WIDTH; k <= HALF_WIDTH; k++) {
				put(out, x, y, z, k, deck);
			}

			// 3. curbs, 4. rails (or a suspension tower at each end)
			for (int k : rails) {
				put(out, x, y, z, k, style.curb().defaultBlockState());
			}
			boolean tower = style == BridgeStyle.SUSPENSION && span && len >= 8 && (jj == 0 || jj == len - 1);
			for (int k : rails) {
				if (tower && Math.abs(k) == RAIL) {
					for (int h = 1; h <= TOWER; h++) {
						put(out, x, y + h, z, k, axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
					}
					put(out, x, y + TOWER + 1, z, k, lantern(false));
				} else {
					put(out, x, y + 1, z, k, style.rail().defaultBlockState());
				}
			}

			// 5. lights and cables (after what they stand on or hang from)
			switch (style) {
				case PLANK -> {
					if (span && legs(jj)) {
						for (int side = -1; side <= 1; side += 2) {
							put(out, x, y + 2, z, side * RAIL, lantern(false));
						}
					}
				}
				case ARCH -> {
					if (span && len >= 6 && jj % 5 == 2 && jj <= len - 3) {
						for (int side = -1; side <= 1; side += 2) {
							put(out, x, y + 2, z, side * RAIL, lantern(false));
						}
					}
					if (span && jj == len / 2) {
						put(out, x, y - archDepth(t) - 1, z, 0, lantern(true));
					}
				}
				case SUSPENSION -> {
					if (span && hangs(jj)) {
						for (int side = -1; side <= 1; side += 2) {
							put(out, x, y - 2, z, side * RAIL, lantern(true));
						}
					}
					if (span && len >= 8 && !tower) {
						cable(out, x, y, z, jj, t);
					}
				}
			}
			return new Bridge.Step(new BlockPos(x, y, z), out);
		}

		/** The chain cable of a suspension bridge: a parabola from tower top to rail and back, hangers every other step. */
		private void cable(List<Placement> out, int x, int y, int z, int jj, double t) {
			int top0 = prof.floor(0) + TOWER;
			int top1 = prof.floor(len - 1) + TOWER;
			int c = (int) Math.round(top0 + (top1 - top0) * t - (TOWER - 1) * 4 * t * (1 - t));
			c = Math.max(c, y + 2);
			for (int side = -1; side <= 1; side += 2) {
				if (jj % 2 == 0) {
					for (int h = y + 2; h < c; h++) {
						put(out, x, h, z, side * RAIL, axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
					}
				}
				put(out, x, c, z, side * RAIL, axis(Blocks.IRON_CHAIN, along));
			}
		}

		/** Depth of the stone below the deck of an arch: deepest at the islands, one block at the crown. */
		private int archDepth(double t) {
			int deepest = Math.max(2, Math.min(7, len / 4));
			double u = 2 * t - 1;
			return 1 + (int) Math.round((deepest - 1) * u * u);
		}

		/** Plank bridges stand on log legs every four steps (none at the very ends). */
		private boolean legs(int jj) {
			return len >= 8 && jj % 4 == 2 && jj <= len - 3;
		}

		/** Suspension bridges have chains with lanterns under the deck every six steps. */
		private boolean hangs(int jj) {
			return len >= 8 && jj % 6 == 3 && jj <= len - 3;
		}

		private BlockState deck(DeckProfile.Kind kind) {
			return switch (kind) {
				case FULL -> style.deck().defaultBlockState();
				case SLAB -> style.slab().defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
				case STAIR_UP -> stairs(toB);
				case STAIR_DOWN -> stairs(toB.getOpposite());
			};
		}

		/** A bottom stair whose high side is towards {@code up}. */
		private BlockState stairs(Direction up) {
			return style.stairs().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, up)
					.setValue(BlockStateProperties.HALF, Half.BOTTOM);
		}

		private BlockState stone(int x, int y, int z) {
			int h = Noise.hash(seed, x, z * 31 + y, 43, 12);
			if (h == 0) {
				return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
			}
			return h == 1 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
		}

		/** Adds the block {@code k} to the side of the line at {@code (x, y, z)} if that column is void. */
		private void put(List<Placement> out, int x, int y, int z, int k, BlockState state) {
			int cx = latX(x, k, alongX);
			int cz = latZ(z, k, alongX);
			if (ground.column(cx, cz) == null) {
				out.add(new Placement(new BlockPos(cx, y, cz), state));
			}
		}
	}

	private static void addOnce(List<Integer> list, int v) {
		if (!list.contains(v)) {
			list.add(v);
		}
	}

	private static BlockState axis(Block block, Direction.Axis axis) {
		return block.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	private static BlockState lantern(boolean hanging) {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging);
	}
}
