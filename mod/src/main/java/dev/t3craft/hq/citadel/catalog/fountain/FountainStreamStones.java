package dev.t3craft.hq.citadel.catalog.fountain;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Meandering brook: a two-wide water channel that is born in a spring (a source tucked into a stratified
 * rock head that spills out of a stone spout through a little waterfall), winds across the plot and rests in a
 * still pool at the far end. The stream runs along the longer side of the plot; the seed picks the side of the
 * spring, the mirror, the curve, where it is crossed and how: a spruce footbridge or a ford of flat stepping
 * stones, both flush with the banks and reached by stairs, so nobody has to jump.
 *
 * <p>Use: a water cauldron at the foot of the spring to fill buckets, a spruce stair bench by the pool with a
 * lantern post at each end, sugar cane on sand banks, lily pads, ferns and flowers. Palette: cobblestone, mossy
 * cobblestone, andesite and tuff for the banks and the head, moss for the soft parts, spruce for what people use,
 * sand for the cane.
 *
 * <pre>
 *  top view (stream runs left to right; the spring may be on either side)
 *   ▓▓▓▓ . . . . . . . . . .       ▓ rock head (3-5 high, strata)   ~ water (y0, banks 1 high all round)
 *   ▓▓▓▓ ~ ~ . . = = . . . .       = bridge / stepping stones, with a stair up on each side
 *   ▓SP▓ ~ ~ ~ ~ = = ~ ~ ◯ ◯       SP spout (source + lip + fall)   ◯ pool, bench B and lantern post beside it
 * </pre>
 *
 * Size 7–13 × 6–13 × 4+ (the stream follows the longer side; every water cell is walled by a full block or a
 * slab, nothing leans out of the plot, and each block is joined to the ground by faces). Water is at y0 on the plot
 * ground, banks are one block high, so the whole walkway is flat or stairs.
 */
public final class FountainStreamStones implements Variant {
	@Override
	public String id() {
		return "fountain_stream_stones";
	}

	@Override
	public Family family() {
		return Family.FOUNTAIN;
	}

	@Override
	public int minW() {
		return 7;
	}

	@Override
	public int minD() {
		return 6;
	}

	@Override
	public int minH() {
		return 4;
	}

	@Override
	public int maxW() {
		return 13;
	}

	@Override
	public int maxD() {
		return 13;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		new Build(c, p).run();
	}

	/** One build. Works in stream coordinates: {@code a} runs along the stream (spring at a = 0), {@code s} across it. */
	private static final class Build {
		static final int OUT = 0;
		static final int WATER = 1;
		static final int RIM = 2;
		static final int ROCK = 3;
		static final int DECK = 4;
		static final int STAIR = 5;

		final CitadelCanvas c;
		final Plot p;
		final int h;
		final boolean alongU;
		final int n; // length of the stream axis
		final int m; // width of the plot across the stream
		final boolean flipA;
		final boolean flipS;
		final int[][] k;
		final int[][] rockH;
		final boolean[][] moss;
		final boolean[][] res;
		final int[][] deckKind; // 0 none, 1 end beam, 2 plank, 3 stone, 4 slab stone
		int hd; // depth of the head in a
		int pl; // pool length
		int pw; // pool width
		int pa0;
		int pa1;
		int ps0;
		int ht; // height of the head's centre
		int ys; // y of the spring source
		int s0; // the cell under the spout
		int[] sc;
		boolean bridge;
		int ac;
		int wc;

		Build(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			this.h = p.h();
			this.alongU = p.w() > p.d() || (p.w() == p.d() && p.hash(0, 0, 0, 2) == 0);
			this.n = alongU ? p.w() : p.d();
			this.m = alongU ? p.d() : p.w();
			this.flipA = p.hash(1, 0, 0, 2) == 0;
			this.flipS = p.hash(2, 0, 0, 2) == 0;
			this.k = new int[n][m];
			this.rockH = new int[n][m];
			this.moss = new boolean[n][m];
			this.res = new boolean[n][m];
			this.deckKind = new int[n][m];
		}

		// ------------------------------------------------------------------ coordinates

		int u(int a, int s) {
			int aa = flipA ? n - 1 - a : a;
			int ss = flipS ? m - 1 - s : s;
			return alongU ? aa : ss;
		}

		int v(int a, int s) {
			int aa = flipA ? n - 1 - a : a;
			int ss = flipS ? m - 1 - s : s;
			return alongU ? ss : aa;
		}

		void set(int a, int s, int y, BlockState st) {
			PondKit.put(c, p, u(a, s), y, v(a, s), st);
		}

		int hs(int a, int y, int s, int bound) {
			return p.hash(u(a, s), y, v(a, s), bound);
		}

		/** The local direction of a step {@code (da, ds)} in stream coordinates. */
		Direction dir(int da, int ds) {
			int du = u(da, ds) - u(0, 0);
			int dv = v(da, ds) - v(0, 0);
			if (du != 0) {
				return du > 0 ? Direction.EAST : Direction.WEST;
			}
			return dv > 0 ? Direction.SOUTH : Direction.NORTH;
		}

		boolean in(int a, int s) {
			return a >= 0 && a < n && s >= 0 && s < m;
		}

		boolean free(int a, int s) {
			return in(a, s) && k[a][s] == OUT && !res[a][s];
		}

		// ------------------------------------------------------------------ plan

		void run() {
			plan();
			crossing();
			drawWater();
			drawHead();
			drawRim();
			drawCrossing();
			drawUse();
			drawGround();
		}

		void plan() {
			hd = n >= 13 ? 2 : 1;
			pl = n >= 12 ? 3 : 2;
			pa1 = n - 3;
			pa0 = pa1 - pl + 1;
			ht = Math.max(3, Math.min(5, p.h() - 3));
			ys = ht - 2;
			int lo = 2;
			int hi = m - 4;
			sc = new int[n];
			int a0 = hd + 1; // first column of channel
			int aLast = pa0 - 2; // last top-left of a 2x2 brush that still ends before the pool
			boolean lateral = hi - lo >= 3 && aLast - a0 >= 2;
			if (lateral) {
				// an S across the plot: forward, one long run sideways to the far bank, forward again
				int f = Math.max(1, (aLast - a0) / 2);
				int sA = p.hash(13, 0, 0, 2) == 0 ? lo : hi;
				int sB = lo + hi - sA;
				for (int a = a0; a <= a0 + f; a++) {
					brush(a, sA);
				}
				for (int s = Math.min(sA, sB); s <= Math.max(sA, sB); s++) {
					brush(a0 + f, s);
				}
				for (int a = a0 + f; a <= aLast; a++) {
					brush(a, sB);
				}
				for (int a = a0; a <= pa0 - 1; a++) {
					for (int s = 0; s < m; s++) {
						if (k[a][s] == WATER) {
							sc[a] = s;
							break;
						}
					}
				}
			} else {
				// a ribbon that swings across the plot one cell a column
				int len = pa0 - 1 - hd;
				double phase = p.hash(3, 0, 0, 628) / 100.0;
				double freq = (p.hash(7, 0, 0, 2) == 0 ? 1.15 : 0.8) * Math.PI / Math.max(1, len - 1);
				int prev = -1;
				for (int a = a0; a <= pa0 - 1; a++) {
					double t = (lo + hi) / 2.0 + (hi - lo) / 2.0 * 1.15 * Math.sin(phase + (a - hd) * freq);
					int val = (int) Math.round(t);
					if (prev >= 0) {
						val = Math.max(prev - 1, Math.min(prev + 1, val));
					}
					val = Math.max(lo, Math.min(hi, val));
					sc[a] = val;
					prev = val;
					k[a][val] = WATER;
					k[a][val + 1] = WATER;
				}
			}
			s0 = sc[hd + 1];
			pw = Math.max(2, Math.min(m >= 11 ? 4 : 3, m - 4));
			int scL = sc[pa0 - 1];
			ps0 = Math.max(2, Math.min(m - 2 - pw, scL - (pw - 2) / 2));
			while (ps0 + pw - 1 < scL) {
				ps0++;
			}
			while (ps0 > scL + 1) {
				ps0--;
			}
			k[hd][s0] = WATER;
			for (int a = pa0; a <= pa1; a++) {
				for (int s = ps0; s < ps0 + pw; s++) {
					boolean cut = pl >= 3 && pw >= 4 && (a == pa0 || a == pa1) && (s == ps0 || s == ps0 + pw - 1);
					if (!cut) {
						k[a][s] = WATER;
					}
				}
			}
			// the rock head and the two piers that flank the spout
			int hw = m >= 10 ? 2 : 1;
			for (int a = 0; a < hd; a++) {
				for (int s = s0 - 1 - hw; s <= s0 + 1 + hw; s++) {
					if (s < 0 || s >= m) {
						continue;
					}
					k[a][s] = ROCK;
					rockH[a][s] = Math.max(2, ht - Math.max(0, Math.abs(s - s0) - 1));
				}
			}
			for (int ds = -1; ds <= 1; ds += 2) {
				k[hd][s0 + ds] = ROCK;
				rockH[hd][s0 + ds] = ys + 1;
			}
			// banks: every free cell next to water
			for (int a = 0; a < n; a++) {
				for (int s = 0; s < m; s++) {
					if (k[a][s] == OUT && touchesWater(a, s)) {
						k[a][s] = RIM;
					}
				}
			}
		}

		/** Paints a 2 x 2 square of water with its low corner at {@code (a, s)}: a brush that keeps the channel two wide. */
		void brush(int a, int s) {
			for (int da = 0; da <= 1; da++) {
				for (int ds = 0; ds <= 1; ds++) {
					k[a + da][s + ds] = WATER;
				}
			}
		}

		boolean touchesWater(int a, int s) {
			return (in(a - 1, s) && k[a - 1][s] == WATER) || (in(a + 1, s) && k[a + 1][s] == WATER)
					|| (in(a, s - 1) && k[a][s - 1] == WATER) || (in(a, s + 1) && k[a][s + 1] == WATER);
		}

		/** A column that can carry a crossing: two water cells, rims on both sides, free ground for the stairs. */
		boolean straight(int a) {
			int sw = sc[a];
			return k[a][sw] == WATER && k[a][sw + 1] == WATER && k[a][sw - 1] == RIM && k[a][sw + 2] == RIM
					&& sw - 2 >= 0 && sw + 3 < m && k[a][sw - 2] == OUT && k[a][sw + 3] == OUT
					&& (sw - 3 < 0 || k[a][sw - 3] != WATER) && (sw + 4 >= m || k[a][sw + 4] != WATER);
		}

		/** Picks where and how the stream is crossed; the deck cells become DECK, the stair cells STAIR. */
		void crossing() {
			// candidate columns: exactly two cells of water, both banks and both stair cells free
			wc = Math.min(2, pa0 - 1 - hd);
			java.util.List<Integer> ok = new java.util.ArrayList<>();
			while (ok.isEmpty() && wc >= 1) {
				for (int a = hd + 1; a + wc - 1 <= pa0 - 1; a++) {
					boolean fits = true;
					for (int i = 0; i < wc && fits; i++) {
						fits = straight(a + i);
					}
					if (fits) {
						ok.add(a);
					}
				}
				if (ok.isEmpty()) {
					wc--;
				}
			}
			if (ok.isEmpty()) {
				wc = 0;
				return;
			}
			ac = ok.get(p.hash(5, 0, 0, ok.size()));
			bridge = p.hash(6, 0, 0, 2) == 0;
			if (bridge) {
				int[][] posts = {{ac - 1, sc[ac] - 1}, {ac + wc, sc[ac + wc - 1] + 2}};
				for (int[] q : posts) {
					if (in(q[0], q[1])) {
						res[q[0]][q[1]] = true;
					}
				}
			}
			for (int i = 0; i < wc; i++) {
				int a = ac + i;
				int sl = sc[a] - 1;
				int sr = sc[a] + 2;
				for (int s = sl; s <= sr; s++) {
					boolean end = s == sl || s == sr;
					boolean wet = k[a][s] == WATER;
					k[a][s] = DECK;
					res[a][s] = true;
					if (bridge) {
						deckKind[a][s] = end ? 1 : 2;
					} else if (end) {
						deckKind[a][s] = 0; // bank stays as it is, kept clear
						k[a][s] = RIM;
					} else if (wet) {
						boolean keep = i > 0 && p.hash(a, 7, s, 3) == 0; // a gap of water between the stones
						if (keep) {
							k[a][s] = WATER;
							res[a][s] = true;
						} else {
							deckKind[a][s] = p.hash(a, 8, s, 4) == 0 ? 4 : 3;
						}
					}
				}
				for (int side = -1; side <= 1; side += 2) {
					int s = side < 0 ? sl - 1 : sr + 1;
					if (in(a, s) && k[a][s] == OUT) {
						k[a][s] = STAIR;
						res[a][s] = true;
					}
				}
			}
		}

		// ------------------------------------------------------------------ drawing

		void drawWater() {
			for (int a = 0; a < n; a++) {
				for (int s = 0; s < m; s++) {
					if (k[a][s] == WATER) {
						set(a, s, 0, PondKit.water());
					}
				}
			}
		}

		BlockState rock(int a, int s, int y, int top) {
			int r = hs(a, y, s, 6);
			if (y == top - 1) {
				boolean soft = r < 4;
				moss[a][s] = soft;
				return soft ? (r < 3 ? b(Blocks.MOSS_BLOCK) : b(Blocks.GRASS_BLOCK)) : b(Blocks.MOSSY_COBBLESTONE);
			}
			if (y == 0) {
				return r < 3 ? b(Blocks.COBBLESTONE) : b(Blocks.TUFF);
			}
			if (y == 1) {
				return r < 4 ? b(Blocks.ANDESITE) : b(Blocks.TUFF);
			}
			if (y == 2) {
				return r < 3 ? b(Blocks.MOSSY_COBBLESTONE) : r < 5 ? b(Blocks.TUFF) : b(Blocks.ANDESITE);
			}
			return r < 3 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.ANDESITE);
		}

		void drawHead() {
			for (int a = 0; a < n; a++) {
				for (int s = 0; s < m; s++) {
					if (k[a][s] != ROCK) {
						continue;
					}
					int top = rockH[a][s];
					for (int y = 0; y < top; y++) {
						set(a, s, y, rock(a, s, y, top));
					}
					if (moss[a][s]) {
						BlockState plant = switch (hs(a, 11, s, 7)) {
							case 0, 1 -> b(Blocks.FLOWERING_AZALEA);
							case 2 -> b(Blocks.AZALEA);
							case 3, 4 -> b(Blocks.FERN);
							case 5 -> b(Blocks.MOSS_CARPET);
							default -> b(Blocks.SHORT_GRASS);
						};
						set(a, s, top, plant);
					}
				}
			}
			// spring: a source in the rock, a lip over the first water cell and the fall to it
			set(hd - 1, s0, ys, PondKit.water());
			set(hd, s0, ys, PondKit.water().setValue(LiquidBlock.LEVEL, 1));
			for (int y = 1; y < ys; y++) {
				set(hd, s0, y, PondKit.fallingWater());
			}
		}

		BlockState rimBlock(int a, int s) {
			int r = hs(a, 0, s, 20);
			if (r < 6) {
				return b(Blocks.MOSSY_COBBLESTONE);
			}
			if (r < 10) {
				return b(Blocks.COBBLESTONE);
			}
			if (r < 13) {
				return b(Blocks.ANDESITE);
			}
			if (r < 15) {
				return b(Blocks.TUFF);
			}
			return r < 18 ? b(Blocks.MOSS_BLOCK) : b(Blocks.SAND);
		}

		void drawRim() {
			for (int a = 0; a < n; a++) {
				for (int s = 0; s < m; s++) {
					if (k[a][s] != RIM) {
						continue;
					}
					BlockState top = rimBlock(a, s);
					set(a, s, 0, top);
					if (res[a][s]) {
						continue; // ford end bank: bare
					}
					if (top.is(Blocks.SAND)) {
						int hgt = Math.min(h - 1, 2 + hs(a, 2, s, 2));
						for (int y = 1; y <= hgt; y++) {
							set(a, s, y, b(Blocks.SUGAR_CANE));
						}
					} else if (top.is(Blocks.MOSS_BLOCK)) {
						set(a, s, 1, switch (hs(a, 3, s, 6)) {
							case 0 -> b(Blocks.FERN);
							case 1 -> b(Blocks.AZURE_BLUET);
							case 2 -> b(Blocks.PINK_PETALS);
							case 3 -> b(Blocks.LILY_OF_THE_VALLEY);
							default -> b(Blocks.MOSS_CARPET);
						});
					} else if (hs(a, 8, s, 7) == 0) {
						set(a, s, 1, p.hash(u(a, s), 9, v(a, s), 2) == 0 ? b(Blocks.MOSSY_COBBLESTONE) : slab(Blocks.MOSSY_COBBLESTONE_SLAB));
					}
				}
			}
		}

		/** Whether {@code (a, s)} is one of the two cells reserved for the bridge's lantern posts. */
		boolean postCell(int a, int s) {
			return bridge && wc > 0 && ((a == ac - 1 && s == sc[ac] - 1) || (a == ac + wc && s == sc[ac + wc - 1] + 2));
		}

		void drawCrossing() {
			Direction.Axis axis = alongU ? Direction.Axis.Z : Direction.Axis.X;
			for (int a = 0; a < n; a++) {
				for (int s = 0; s < m; s++) {
					BlockState st = null;
					switch (deckKind[a][s]) {
						case 1 -> st = b(Blocks.STRIPPED_SPRUCE_LOG).setValue(BlockStateProperties.AXIS, axis);
						case 2 -> st = b(Blocks.SPRUCE_PLANKS);
						case 3 -> st = switch (hs(a, 5, s, 4)) {
							case 0 -> b(Blocks.POLISHED_ANDESITE);
							case 1 -> b(Blocks.MOSSY_COBBLESTONE);
							case 2 -> b(Blocks.STONE_BRICKS);
							default -> b(Blocks.ANDESITE);
						};
						case 4 -> st = slab(Blocks.STONE_BRICK_SLAB);
						default -> {
						}
					}
					if (st != null) {
						set(a, s, 0, st);
					}
					if (k[a][s] == STAIR) { // the high side faces the bank
						boolean left = s < sc[a];
						set(a, s, 0, stairs(bridge ? Blocks.SPRUCE_STAIRS : Blocks.MOSSY_COBBLESTONE_STAIRS, dir(0, left ? 1 : -1)));
					}
				}
			}
			if (bridge && wc > 0) { // a lantern post on each bank at the ends of the bridge (reserved in crossing())
				post(ac - 1, sc[ac] - 1);
				post(ac + wc, sc[ac + wc - 1] + 2);
			}
		}

		/** A lantern post on a free or bank cell. */
		void post(int a, int s) {
			if (!in(a, s) || (k[a][s] != OUT && !(k[a][s] == RIM && postCell(a, s))) || (res[a][s] && !postCell(a, s))) {
				return;
			}
			res[a][s] = true;
			set(a, s, 0, b(Blocks.POLISHED_ANDESITE));
			set(a, s, 1, b(Blocks.SPRUCE_FENCE));
			set(a, s, 2, b(Blocks.LANTERN));
		}

		void drawUse() {
			// the pool: a bench of spruce stairs beside it, facing the water, a lantern post at each end
			int sd = p.hash(10, 0, 0, 2) == 0 ? -1 : 1;
			int sSeat = sd < 0 ? ps0 - 2 : ps0 + pw + 1;
			if (!in(0, sSeat) || k[pa0][sSeat] != OUT) {
				sd = -sd;
				sSeat = sd < 0 ? ps0 - 2 : ps0 + pw + 1;
			}
			int seats = pl >= 3 ? 3 : 2;
			int a0 = pa0 + (pl - seats) / 2;
			for (int i = 0; i < seats; i++) {
				int a = a0 + i;
				if (free(a, sSeat)) {
					set(a, sSeat, 0, stairs(Blocks.SPRUCE_STAIRS, dir(0, sd)));
					res[a][sSeat] = true;
				}
			}
			post(a0 - 1, sSeat);
			if (n >= 9) {
				post(a0 + seats, sSeat);
			}
			// the spring's cauldron: water to fill buckets, on the ground beside the head
			int cs = p.hash(11, 0, 0, 2) == 0 ? s0 - 2 : s0 + 2;
			if (free(hd, cs)) {
				set(hd, cs, 0, b(Blocks.WATER_CAULDRON).setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
				res[hd][cs] = true;
			}
			// lily pads
			for (int a = hd + 1; a < n; a++) {
				for (int s = 0; s < m; s++) {
					if (k[a][s] == WATER && !res[a][s] && hs(a, 5, s, a >= pa0 ? 3 : 6) == 0) {
						set(a, s, 1, b(Blocks.LILY_PAD));
					}
				}
			}
		}

		void drawGround() {
			for (int a = 0; a < n; a++) {
				for (int s = 0; s < m; s++) {
					if (!free(a, s)) {
						continue;
					}
					boolean inner = a >= 1 && a <= n - 2 && s >= 1 && s <= m - 2;
					BlockState plant = switch (hs(a, 3, s, 24)) {
						case 0, 1, 2, 3, 4 -> b(Blocks.SHORT_GRASS);
						case 5, 6, 7 -> b(Blocks.FERN);
						case 8 -> b(Blocks.PINK_PETALS);
						case 9 -> b(Blocks.AZURE_BLUET);
						case 10 -> b(Blocks.CORNFLOWER);
						case 11 -> b(Blocks.LILY_OF_THE_VALLEY);
						case 12 -> inner ? b(Blocks.FLOWERING_AZALEA) : null;
						case 13 -> inner ? b(Blocks.AZALEA) : null;
						default -> null;
					};
					if (plant != null) {
						set(a, s, 0, plant);
					}
				}
			}
		}
	}
}
