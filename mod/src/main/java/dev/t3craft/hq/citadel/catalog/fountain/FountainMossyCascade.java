package dev.t3craft.hq.citadel.catalog.fountain;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Arrays;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A natural mossy cascade. A mound of mixed rock (deepslate and tuff at the foot, andesite and stone in the body,
 * mossy cobble and moss on top) rises at the back with a spring in a notch of its crest. The water drops in 2 or 3
 * falls: each fall lands in a small stone trough, spills over a sill and falls again, down to a low plunge pool at
 * the front. Every trough is a closed basin (bed, walls, sill) so the water stays where it is put; only the notch
 * over each sill is open and that is where the water falls for real.
 *
 * <pre>
 *  side section (front = left)                         top view
 *   plunge pool        trough 1       trough 0 (spring)   lane . . . . . . . . . lane
 *   ~~~~~~~|bridge     ┌─~~~┐         ┌~~~~~~┐             . rim rim rim rim rim .
 *  rim ~~~~~~~~~~~~~ ▓▓│fall│▓▓ ──fall──│spring│▓▓▓       . rim ~ ~ B B ~ ~ rim .   B bridge + bench
 *  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓       . rim ~ ~ ~ ~ ~ rim .
 *                                                       . ▒ ▒ ▓~▓ ▒ ▒ .   trough 1, gate (sill + notch)
 *                                                       . ▒ ▓ ~ ~ ▓ ▒ .   trough 0, spring, crest
 * </pre>
 *
 * Use: a cherry footbridge crosses the pool from lane to lane, climbing in half-block steps (stair, stair, slab
 * deck), with a stone bench on it that looks at the fall and posts with lanterns; stone lanterns stand on the front
 * rim. The lanes all round the plot stay free, so the piece is walked round and across without a jump.
 *
 * Size 9–20 × 10–20 × 8+. Plots grow with the square root of the citadel scale: the crag then rises (about T = 4 +
 * (min(w, d) − 9) / 3 blocks, always 2–3 falls, each one taller) and widens (wider notch, longer troughs, wider
 * pool); from 45 wide it is built in several bays side by side, each with its own cascade and pool, so the plot
 * gets more pools instead of a stretched one. The seed picks the side of the fall, the shape of the mound, the
 * growth (blossom, bamboo or azalea) and the wobble of the pool edge.
 */
public final class FountainMossyCascade implements Variant {
	@Override
	public String id() {
		return "fountain_mossy_cascade";
	}

	@Override
	public Family family() {
		return Family.FOUNTAIN;
	}

	@Override
	public int minW() {
		return 9;
	}

	@Override
	public int minD() {
		return 10;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 20;
	}

	@Override
	public int maxD() {
		return 20;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int bays = Math.max(1, Math.min(6, 1 + w / 45));
		int m = Math.min(w, d);
		int tBase = Math.max(5, Math.min(p.h() - 3, 4 + (m - 9) / 3));
		int[][] top = new int[w][d];
		for (int[] col : top) {
			Arrays.fill(col, -1);
		}
		boolean[][] wet = new boolean[w][d];
		boolean[][] moss = new boolean[w][d];
		for (int j = 0; j < bays; j++) {
			int a = j * w / bays;
			int z = (j + 1) * w / bays;
			int t = Math.max(5, tBase - (bays > 1 ? p.hash(j, 3, 5, 2) : 0));
			new Bay(c, p, top, wet, moss, a, z - a, t, j).build();
		}
	}

	/** One cascade with its pool, in the columns {@code u0 .. u0 + uw - 1} of the plot (the first and last are lanes). */
	private static final class Bay {
		final CitadelCanvas c;
		final Plot p;
		final int[][] top;
		final boolean[][] wet;
		final boolean[][] moss;
		final int u0;
		final int uw;
		final int uLast;
		final int T;
		final int j;
		final int d;
		final int dm;
		final int theme;
		int n;
		int pool;
		int bn;
		int e0;
		int vb;
		int eL;
		int eR;
		int pl;
		int pr;
		int nlo;
		int nhi;
		int fn;
		int[] t;
		int[] g;
		int[] lo;
		int[] hi;
		boolean bridge;
		int bridgeA;
		int bridgeZ;

		Bay(CitadelCanvas c, Plot p, int[][] top, boolean[][] wet, boolean[][] moss, int u0, int uw, int tier, int j) {
			this.c = c;
			this.p = p;
			this.top = top;
			this.wet = wet;
			this.moss = moss;
			this.u0 = u0;
			this.uw = uw;
			this.uLast = u0 + uw - 1;
			this.T = tier;
			this.j = j;
			this.d = p.d();
			this.dm = d - 2;
			this.theme = p.hash(j, 31, 7, 3);
		}

		void build() {
			layout();
			for (int u = u0; u <= uLast; u++) {
				for (int v = 0; v < d; v++) {
					cell(u, v);
				}
			}
			brow();
			bridge();
			toros();
			rimFlora();
			waterDecor();
			flora();
			vines();
		}

		// ------------------------------------------------------------------ layout

		void layout() {
			n = Math.max(2, Math.min(3, (T - 1) / 2));
			t = new int[n + 1];
			t[0] = T;
			t[n] = 1;
			for (int i = 1; i < n; i++) {
				t[i] = 1 + Math.round((T - 1) * (float) (n - i) / n);
			}
			int back = 1 + d / 14;
			pool = Math.max(2, Math.min(Math.round(0.38f * d) - 1, (d - 1) - 2 - n - back - n)); // every trough keeps at least one row
			int rest = Math.max(n, (d - 1) - 2 - pool - n - back);
			bn = 2 + pool;
			int[] len = new int[n];
			int base = rest / n;
			int extra = rest % n;
			for (int i = n - 1; i >= 0; i--) {
				len[i] = base + ((n - 1 - i) < extra ? 1 : 0);
			}
			g = new int[n];
			g[n - 1] = bn;
			for (int i = n - 1; i >= 1; i--) {
				g[i - 1] = g[i] + 1 + len[i];
			}
			e0 = g[0] + len[0];
			vb = 2 + pool / 2;
			eL = u0 + 1;
			eR = u0 + uw - 2;
			pl = eL + 1;
			pr = eR - 1;
			fn = Math.max(1, Math.min(6, Math.round(uw / 8f)));
			int cc = u0 + (uw - 1) / 2;
			int uc = cc + (p.hash(j, 21, 0, 3) - 1) * (uw / 6);
			nlo = uc - (fn - 1) / 2;
			nlo = Math.max(eL + 2, Math.min(eR - 2 - (fn - 1), nlo));
			nhi = nlo + fn - 1;
			int grow = 1 + (uw - 9) / 16;
			lo = new int[n];
			hi = new int[n];
			for (int i = 0; i < n; i++) {
				lo[i] = Math.max(eL + 1, nlo - i * grow);
				hi[i] = Math.min(eR - 1, nhi + i * grow);
			}
		}

		/** First water column of pool row {@code v} (the front corners are cut, the edge wobbles; the bridge row is straight). */
		int wl(int v) {
			if (v == vb) {
				return pl;
			}
			if (v == 2) {
				return pl + 1;
			}
			return pl + (p.hash(u0, 11, v, 5) == 0 ? 1 : 0);
		}

		int wr(int v) {
			if (v == vb) {
				return pr;
			}
			if (v == 2) {
				return pr - 1;
			}
			return pr - (p.hash(u0, 12, v, 5) == 0 ? 1 : 0);
		}

		/** The trough whose rows hold row {@code v} (its sill row first), or -1 over the crest. */
		int band(int v) {
			for (int i = n - 1; i >= 0; i--) {
				int end = i == 0 ? e0 : g[i - 1] - 1;
				if (v >= g[i] && v <= end) {
					return i;
				}
			}
			return -1;
		}

		// ------------------------------------------------------------------ cells

		void cell(int u, int v) {
			if (u == u0 || u == uLast || v == 0 || v == d - 1) {
				if (p.hash(u, 13, v, 4) == 0) {
					p.set(c, u, 0, v, b(Blocks.MOSS_CARPET));
				}
				return;
			}
			if (v == 1) {
				rim(u, v);
			} else if (v < bn) {
				if (u >= wl(v) && u <= wr(v)) {
					boolean edge = u == wl(v) || u == wr(v) || v == 2;
					p.set(c, u, 0, v, PondKit.bed(p, u, v, edge));
					p.set(c, u, 1, v, PondKit.water());
					wet[u][v] = true;
					top[u][v] = 0;
				} else {
					rim(u, v);
				}
			} else {
				mound(u, v);
			}
		}

		void rim(int u, int v) {
			p.set(c, u, 0, v, PondKit.rimBase(p, u, v));
			p.set(c, u, 1, v, PondKit.rimTop(p, u, 1, v));
			top[u][v] = 1;
		}

		void mound(int u, int v) {
			int i = band(v);
			int tp;
			int water = -1;
			boolean gate = false;
			if (i >= 0) {
				boolean in = u >= lo[i] && u <= hi[i];
				boolean notch = u >= nlo && u <= nhi;
				if (v == g[i]) {
					if (notch) {
						gate = true;
						tp = t[i + 1] - 1;
					} else if (in) {
						tp = t[i];
					} else {
						tp = shoulder(i, u, v);
					}
					if (!notch && i + 1 < n && u >= lo[i + 1] && u <= hi[i + 1]) {
						tp = Math.max(tp, t[i + 1]); // the wall behind the lower trough
					}
				} else if (in) {
					tp = t[i] - 1;
					water = t[i];
				} else {
					tp = shoulder(i, u, v);
				}
			} else {
				tp = crown(u, v);
			}
			for (int y = 0; y <= tp; y++) {
				BlockState s = y == tp && (water >= 0 || gate) ? PondKit.bed(p, u, v, false) : rock(u, y, v, tp);
				p.set(c, u, y, v, s);
				if (y == tp && s.is(Blocks.MOSS_BLOCK)) {
					moss[u][v] = true;
				}
			}
			top[u][v] = tp;
			if (water >= 0) {
				p.set(c, u, water, v, PondKit.water());
			}
			if (gate) {
				int ti = t[i];
				int tn = t[i + 1];
				p.set(c, u, tn, v, PondKit.water()); // landing source
				for (int y = tn + 1; y < ti; y++) {
					p.set(c, u, y, v, PondKit.fallingWater());
				}
				p.set(c, u, ti, v, PondKit.water().setValue(LiquidBlock.LEVEL, 1)); // the lip
				if (i == n - 1) {
					wet[u][v] = true;
				}
			}
		}

		/** Rock top on the shoulder of trough {@code i}: a wall just above the water, then falling away to the lane. */
		int shoulder(int i, int u, int v) {
			boolean left = u < lo[i];
			int delta = left ? lo[i] - u : u - hi[i];
			int dmax = left ? lo[i] - eL : eR - hi[i];
			return shoulder(t[i], delta, dmax, u, v);
		}

		int shoulder(int ti, int delta, int dmax, int u, int v) {
			if (delta <= 1) {
				return ti + 1;
			}
			if (delta >= dmax) {
				return 1;
			}
			int hgt = 1 + Math.round(ti * (float) (dmax - delta) / (dmax - 1));
			if (p.hash(u, 6, v, 4) == 0) {
				hgt = Math.max(1, hgt - 1);
			}
			return Math.min(hgt, ti + 1);
		}

		/** The crest behind the spring: highest around it, falling to the sides and to the back. */
		int crown(int u, int v) {
			int dv = v - e0;
			int rows = dm - e0;
			int backTop = rows <= 1 ? T + 1 : 1 + Math.round(T * (float) (rows + 1 - dv) / rows);
			int lat;
			if (u >= lo[0] && u <= hi[0]) {
				lat = T + 1;
			} else {
				lat = shoulder(0, u, v);
			}
			int tp = Math.min(backTop, lat);
			if (dv <= 2 && tp >= T && p.hash(u, 17, v, 3) == 0) {
				tp = Math.min(T + 2, tp + 1);
			}
			return Math.max(1, tp);
		}

		/** Rock in strata (deepslate foot, tuff, andesite, stone, mossy cobble) under a moss cap. */
		BlockState rock(int u, int y, int v, int tp) {
			int below = tp - y;
			boolean odd = p.hash(u, y, v, 4) == 0;
			if (below == 0) {
				int h = p.hash(u, y, v, 10);
				return h < 6 ? b(Blocks.MOSS_BLOCK) : h < 8 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.MOSSY_STONE_BRICKS);
			}
			if (below == 1) {
				return odd ? b(Blocks.ANDESITE) : b(Blocks.MOSSY_COBBLESTONE);
			}
			if (y == 0) {
				return odd ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.COBBLED_DEEPSLATE);
			}
			int band = Math.min(4, y * 5 / (T + 1));
			return switch (band) {
				case 0 -> odd ? b(Blocks.COBBLED_DEEPSLATE) : b(Blocks.TUFF);
				case 1 -> odd ? b(Blocks.TUFF) : b(Blocks.ANDESITE);
				case 2 -> odd ? b(Blocks.ANDESITE) : b(Blocks.STONE);
				case 3 -> odd ? b(Blocks.STONE) : b(Blocks.MOSSY_COBBLESTONE);
				default -> odd ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.ANDESITE);
			};
		}

		/** A mossy brow over the lip of the spring. */
		void brow() {
			if (fn > 4) {
				return;
			}
			BlockState brow = stairs(Blocks.MOSSY_COBBLESTONE_STAIRS, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP);
			for (int u = nlo; u <= nhi; u++) {
				PondKit.put(c, p, u, T + 1, g[0], brow);
			}
		}

		// ------------------------------------------------------------------ the bridge and its bench

		/**
		 * Footbridge across the pool from lane to lane, walkable without a jump: ground, stair on the rim cell
		 * (top 1), stair on a pier (top 2), then bottom slabs (top 2.5). Rails of cherry fence, a post with a lantern at
		 * each end, and one or more stone benches in the front rail that look at the fall.
		 */
		void bridge() {
			int a = wl(vb);
			int z = wr(vb);
			if (z - a < 4) {
				return;
			}
			bridge = true;
			bridgeA = a;
			bridgeZ = z;
			p.set(c, a - 1, 0, vb, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
			p.set(c, a - 1, 1, vb, CitadelCanvas.AIR);
			p.set(c, z + 1, 0, vb, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
			p.set(c, z + 1, 1, vb, CitadelCanvas.AIR);
			p.set(c, a, 1, vb, stairs(Blocks.CHERRY_STAIRS, Direction.EAST));
			p.set(c, z, 1, vb, stairs(Blocks.CHERRY_STAIRS, Direction.WEST));
			wet[a][vb] = false;
			wet[z][vb] = false;
			for (int u = a + 1; u <= z - 1; u++) {
				if ((u - a) % 4 == 0 && u < z - 1) {
					p.set(c, u, 1, vb, PondKit.brick(p, u, 1, vb)); // a pier under the deck
				}
				p.set(c, u, 2, vb, slab(Blocks.CHERRY_SLAB));
				wet[u][vb] = false;
			}
			int deck = z - a - 1;
			int benches = 1 + (deck - 3) / 10;
			boolean[] seat = new boolean[w()];
			for (int q = 0; q < benches; q++) {
				int centre = a + 1 + (deck * (2 * q + 1)) / (2 * benches);
				for (int u = centre - 1; u <= centre + 1; u++) {
					if (u > a && u < z) {
						seat[u] = true;
					}
				}
			}
			for (int sv = vb - 1; sv <= vb + 1; sv += 2) {
				for (int u = a; u <= z; u++) {
					if (top[u][sv] >= 2 || p.at(c, u, 2, sv) != null) {
						continue; // rock there (the toe of the mound)
					}
					boolean end = u == a || u == z;
					if (end) {
						p.set(c, u, 1, sv, PondKit.brick(p, u, 1, sv));
						p.set(c, u, 2, sv, b(Blocks.STRIPPED_CHERRY_LOG));
						PondKit.put(c, p, u, 3, sv, b(Blocks.LANTERN));
						wet[u][sv] = false;
					} else if (sv == vb - 1 && seat[u]) {
						p.set(c, u, 2, sv, stairs(Blocks.MOSSY_STONE_BRICK_STAIRS, Direction.NORTH));
					} else {
						boolean west = u > a + 1 && !(sv == vb - 1 && seat[u - 1]);
						boolean east = u < z - 1 && !(sv == vb - 1 && seat[u + 1]);
						p.set(c, u, 2, sv, PondKit.fence(false, false, west || u == a + 1, east || u == z - 1));
					}
				}
			}
		}

		int w() {
			return p.w();
		}

		// ------------------------------------------------------------------ rim: lanterns and growth

		void toros() {
			int side = p.hash(j, 27, 0, 2);
			int tu = side == 0 ? eL : eR;
			int gu = side == 0 ? eR : eL;
			PondKit.put(c, p, tu, 2, 1, b(Blocks.STONE_BRICK_WALL));
			PondKit.put(c, p, tu, 3, 1, b(Blocks.CHISELED_STONE_BRICKS));
			PondKit.put(c, p, tu, 4, 1, b(Blocks.LANTERN));
			PondKit.put(c, p, tu, 5, 1, slab(Blocks.MOSSY_STONE_BRICK_SLAB));
			p.set(c, gu, 1, 1, b(Blocks.MOSS_BLOCK));
			PondKit.put(c, p, gu, 2, 1, b(Blocks.FLOWERING_AZALEA));
			int nu = side == 0 ? eR - 1 : eL + 1;
			p.set(c, nu, 1, 1, b(Blocks.MOSS_BLOCK));
			bamboo(nu, 2, 1, 3 + T / 8);
		}

		void rimFlora() {
			for (int v = 1; v < bn; v++) {
				for (int u = eL; u <= eR; u++) {
					if (top[u][v] != 1 || p.at(c, u, 2, v) != null || p.at(c, u, 1, v) == null || p.at(c, u, 1, v).isAir()) {
						continue;
					}
					if (bridge && v == vb && (u == bridgeA - 1 || u == bridgeZ + 1)) {
						continue;
					}
					if (p.hash(u, 16, v, 6) != 0) {
						continue;
					}
					p.set(c, u, 1, v, b(Blocks.MOSS_BLOCK));
					switch (p.hash(u, 18, v, 5)) {
						case 0 -> PondKit.put(c, p, u, 2, v, b(Blocks.FERN));
						case 1 -> PondKit.put(c, p, u, 2, v, b(Blocks.FLOWERING_AZALEA));
						case 2 -> PondKit.put(c, p, u, 2, v, b(Blocks.AZALEA));
						case 3 -> PondKit.put(c, p, u, 2, v, b(Blocks.PINK_PETALS));
						default -> PondKit.put(c, p, u, 2, v, b(Blocks.SHORT_GRASS));
					}
				}
			}
		}

		/** Lily pads and a few mossy rocks in the open water (never at the bridge or under the fall). */
		void waterDecor() {
			for (int v = 2; v < bn; v++) {
				for (int u = pl; u <= pr; u++) {
					if (!wet[u][v] || (bridge && Math.abs(v - vb) <= 1) || (v >= bn - 2 && u >= nlo - 1 && u <= nhi + 1)) {
						continue;
					}
					int h = p.hash(u, 8, v, 10);
					if (h == 0) {
						p.set(c, u, 1, v, PondKit.rimTop(p, u, 1, v));
						p.set(c, u, 2, v, p.hash(u, 9, v, 2) == 0 ? slab(Blocks.MOSSY_COBBLESTONE_SLAB) : b(Blocks.MOSS_CARPET));
						wet[u][v] = false;
					} else if (h <= 2) {
						p.set(c, u, 2, v, b(Blocks.LILY_PAD));
					}
				}
			}
		}

		// ------------------------------------------------------------------ growth on the rocks

		void flora() {
			for (int u = eL; u <= eR; u++) {
				for (int v = bn; v <= dm; v++) {
					if (!moss[u][v]) {
						continue;
					}
					int y = top[u][v] + 1;
					if (!p.has(u, y, v) || p.at(c, u, y, v) != null) {
						continue;
					}
					int h = p.hash(u, y, v, 24);
					int kind = switch (theme) {
						case 0 -> h <= 2 ? 1 : h <= 7 ? 2 : h <= 9 ? 3 : h <= 11 ? 4 : h <= 14 ? 5 : h == 15 ? 6 : 0;
						case 1 -> h <= 1 ? 1 : h <= 6 ? 2 : h <= 8 ? 4 : h <= 11 ? 5 : h <= 14 ? 6 : 0;
						default -> h <= 5 ? 1 : h <= 7 ? 4 : h <= 11 ? 2 : h == 12 ? 3 : h == 13 ? 6 : 0;
					};
					switch (kind) {
						case 1 -> p.set(c, u, y, v, b(Blocks.FLOWERING_AZALEA));
						case 2 -> p.set(c, u, y, v, b(Blocks.FERN));
						case 3 -> p.set(c, u, y, v, b(Blocks.PINK_PETALS));
						case 4 -> p.set(c, u, y, v, b(Blocks.AZALEA));
						case 5 -> p.set(c, u, y, v, b(Blocks.SHORT_GRASS));
						case 6 -> bamboo(u, y, v, 3 + p.hash(u, y, v, 3) + T / 8);
						default -> {
						}
					}
				}
			}
		}

		/** A bamboo stalk with leaves on top; sits on a moss block. */
		void bamboo(int u, int y, int v, int height) {
			for (int k = 0; k < height; k++) {
				BlockState s = b(Blocks.BAMBOO);
				if (k == height - 1) {
					s = s.setValue(BlockStateProperties.BAMBOO_LEAVES, BambooLeaves.LARGE);
				} else if (k == height - 2) {
					s = s.setValue(BlockStateProperties.BAMBOO_LEAVES, BambooLeaves.SMALL);
				}
				PondKit.put(c, p, u, y + k, v, s);
			}
		}

		/** Vines over the cliffs that look at the front. */
		void vines() {
			BlockState vine = b(Blocks.VINE).setValue(BlockStateProperties.SOUTH, true);
			for (int u = eL; u <= eR; u++) {
				for (int v = bn; v <= dm; v++) {
					int tp = top[u][v];
					int fr = top[u][v - 1];
					if (tp < 3 || tp - fr < 2 || (u >= nlo - 1 && u <= nhi + 1) || p.hash(u, 14, v, 4) != 0) {
						continue;
					}
					if (bridge && Math.abs(v - 1 - vb) <= 1) {
						continue;
					}
					int len = 1 + p.hash(u, 15, v, 3);
					for (int k = 0; k < len; k++) {
						int y = tp - 1 - k;
						if (y <= fr || y <= 1 || p.at(c, u, y, v - 1) != null) {
							break;
						}
						PondKit.putLayer(c, p, u, y, v - 1, vine, CitadelCanvas.DECOR);
					}
				}
			}
		}
	}
}
