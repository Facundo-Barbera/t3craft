package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Plot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The organic tree toolbox of the second tree pass (all nine TREE variants draw with it). Package-private.
 *
 * <p>The technique of the custom-tree builders, as tools:
 * <ul>
 * <li>{@link #trunk}: a 2×2 trunk at the foot that thins to three cells and then to a 1×1 leader, which can
 *     lean one block at a time (with a corner filler so it stays face-connected).</li>
 * <li>{@link #roots}: roots that run out over the grass, straight from the faces and <b>diagonal</b> (a plan
 *     staircase) from the corners, with a one-block flare against the trunk and muddy roots at the tips.</li>
 * <li>{@link #branch}: a visible diagonal limb, a 6-connected line of logs whose axis follows each step,
 *     doubled near its base. A limb always ends <i>inside</i> a lobe, so the lobe hangs from it.</li>
 * <li>{@link #lobe}: a rounded clump of leaves (an ellipsoid with a jittered edge: trimmed corners, never a
 *     cube), flatter below than above, in two tones mixed in 2×2×2 clumps.</li>
 * <li>{@link #hang}: a hanging strand of irregular length that can kink sideways once.</li>
 * <li>{@link Vox#prune}: removes every leaf that is not connected (through leaves) to wood, so nothing floats.</li>
 * </ul>
 *
 * <p>Everything is drawn into a {@link Vox} (the size of the plot, ignoring writes outside it so a variant
 * never clips) and flushed through {@link Plot#set} at the end. Wood overwrites leaves; leaves and ground
 * cover only fill empty cells, so the order of the pieces does not matter and limbs show through.
 */
final class TreeKit {
	private TreeKit() {
	}

	static final byte EMPTY = 0;
	static final byte WOOD = 1;
	static final byte LEAF = 2;
	static final byte COVER = 3;
	static final byte FIXED = 4;

	/** A species' wood: {@code log} (axis set per cell), {@code bark} (bark on all sides, joints and the 2×2), roots tips. */
	record Wood(Block logBlock, Block barkBlock, BlockState rootTip) {
		BlockState log(Direction.Axis a) {
			return logBlock.defaultBlockState().setValue(BlockStateProperties.AXIS, a);
		}

		BlockState bark() {
			return barkBlock.defaultBlockState();
		}
	}

	/** Two leaf tones (and an optional accent sprinkled on top, e.g. flowers): {@code a} dominant, {@code b} in clumps. */
	record Leaves(BlockState a, BlockState b, BlockState accent, int clumpOneIn, int accentOneIn) {
		static Leaves of(Block a, Block b) {
			return new Leaves(leaves(a), leaves(b), null, 3, 0);
		}

		Leaves withAccent(BlockState acc, int oneIn) {
			return new Leaves(a, b, acc, clumpOneIn, oneIn);
		}

		Leaves clumps(int oneIn) {
			return new Leaves(a, b, accent, oneIn, accentOneIn);
		}

		Leaves swap() {
			return new Leaves(b, a, accent, clumpOneIn, accentOneIn);
		}
	}

	static final Wood CHERRY = new Wood(Blocks.CHERRY_LOG, Blocks.CHERRY_WOOD, b(Blocks.MUDDY_MANGROVE_ROOTS));
	static final Leaves SAKURA = Leaves.of(Blocks.CHERRY_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES).clumps(4);

	// ------------------------------------------------------------------ voxel buffer

	static final class Vox {
		final Plot p;
		final int w;
		final int d;
		final int h;
		private final BlockState[] cells;
		private final byte[] kind;

		Vox(Plot p) {
			this.p = p;
			this.w = p.w();
			this.d = p.d();
			this.h = p.h();
			this.cells = new BlockState[w * d * h];
			this.kind = new byte[w * d * h];
		}

		boolean in(int u, int y, int v) {
			return u >= 0 && u < w && v >= 0 && v < d && y >= 0 && y < h;
		}

		private int ix(int u, int y, int v) {
			return (y * d + v) * w + u;
		}

		byte kind(int u, int y, int v) {
			return in(u, y, v) ? kind[ix(u, y, v)] : EMPTY;
		}

		BlockState get(int u, int y, int v) {
			return in(u, y, v) ? cells[ix(u, y, v)] : null;
		}

		boolean empty(int u, int y, int v) {
			return in(u, y, v) && kind[ix(u, y, v)] == EMPTY;
		}

		/** Wood: overwrites leaves and cover, never a fixed block (pot, pond stone). */
		void wood(int u, int y, int v, BlockState s) {
			if (in(u, y, v) && kind[ix(u, y, v)] != FIXED) {
				cells[ix(u, y, v)] = s;
				kind[ix(u, y, v)] = WOOD;
			}
		}

		/** Wood only into an empty or leaf cell (never replaces other wood: keeps a log's axis). */
		void woodSoft(int u, int y, int v, BlockState s) {
			byte k = kind(u, y, v);
			if (in(u, y, v) && (k == EMPTY || k == LEAF || k == COVER)) {
				wood(u, y, v, s);
			}
		}

		/** Leaves: only into an empty cell. */
		void leaf(int u, int y, int v, BlockState s) {
			if (empty(u, y, v)) {
				cells[ix(u, y, v)] = s;
				kind[ix(u, y, v)] = LEAF;
			}
		}

		/** Ground cover (flowers, petals, carpets): only into an empty cell with nothing solid assumed below. */
		void cover(int u, int y, int v, BlockState s) {
			if (empty(u, y, v)) {
				cells[ix(u, y, v)] = s;
				kind[ix(u, y, v)] = COVER;
			}
		}

		/** Anything else (stone, water, pot): overwrites whatever is there and is never overwritten by the tree. */
		void fixed(int u, int y, int v, BlockState s) {
			if (in(u, y, v)) {
				cells[ix(u, y, v)] = s;
				kind[ix(u, y, v)] = FIXED;
			}
		}

		void clear(int u, int y, int v) {
			if (in(u, y, v)) {
				cells[ix(u, y, v)] = null;
				kind[ix(u, y, v)] = EMPTY;
			}
		}

		int hash(int u, int y, int v, int n) {
			return p.hash(u, y, v, n);
		}

		/**
		 * Removes every leaf that cannot reach wood through face-connected leaves, and every cover block
		 * standing on a leaf or in the air above y = 0 (so nothing floats).
		 */
		void prune() {
			int n = w * d * h;
			boolean[] seen = new boolean[n];
			ArrayDeque<Integer> q = new ArrayDeque<>();
			for (int i = 0; i < n; i++) {
				if (kind[i] == WOOD) {
					seen[i] = true;
					q.add(i);
				}
			}
			int[] du = {1, -1, 0, 0, 0, 0};
			int[] dy = {0, 0, 1, -1, 0, 0};
			int[] dv = {0, 0, 0, 0, 1, -1};
			while (!q.isEmpty()) {
				int i = q.poll();
				int u = i % w;
				int v = (i / w) % d;
				int y = i / (w * d);
				for (int k = 0; k < 6; k++) {
					int nu = u + du[k];
					int ny = y + dy[k];
					int nv = v + dv[k];
					if (!in(nu, ny, nv)) {
						continue;
					}
					int j = ix(nu, ny, nv);
					if (!seen[j] && kind[j] == LEAF) {
						seen[j] = true;
						q.add(j);
					}
				}
			}
			for (int i = 0; i < n; i++) {
				if (kind[i] == LEAF && !seen[i]) {
					cells[i] = null;
					kind[i] = EMPTY;
				}
			}
			for (int y = 1; y < h; y++) {
				for (int v = 0; v < d; v++) {
					for (int u = 0; u < w; u++) {
						int i = ix(u, y, v);
						if (kind[i] == COVER) {
							byte below = kind[ix(u, y - 1, v)];
							if (below != WOOD && below != FIXED) {
								cells[i] = null;
								kind[i] = EMPTY;
							}
						}
					}
				}
			}
		}

		void flush(CitadelCanvas c) {
			for (int y = 0; y < h; y++) {
				for (int v = 0; v < d; v++) {
					for (int u = 0; u < w; u++) {
						BlockState s = cells[ix(u, y, v)];
						if (s != null) {
							p.set(c, u, y, v, s);
						}
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ trunk

	/**
	 * A trunk: 2×2 with its lower-left cell at {@code (u0, v0)} from y = 0 to {@code y2 - 1}, three cells at
	 * {@code y2} (the corner away from the leader goes), then a 1×1 leader from {@code y2 + 1} to {@code yTop}
	 * that drifts towards {@code (u0 + ou + leanU, v0 + ov + leanV)} (smoothstep, one axis per layer). The
	 * leader starts on cell {@code (ou, ov)} (each 0 or 1) of the 2×2.
	 *
	 * @return the leader's {@code {u[y], v[y]}} for every y up to {@code yTop} (on the 2×2 it is the leader cell)
	 */
	static int[][] trunk(Vox t, Wood wd, int u0, int v0, int y2, int yTop, int ou, int ov, double leanU, double leanV) {
		int[] lu = new int[t.h + 1];
		int[] lv = new int[t.h + 1];
		int cu = u0 + ou;
		int cv = v0 + ov;
		int span = Math.max(1, yTop - y2);
		for (int y = 0; y <= Math.min(yTop, t.h - 1); y++) {
			if (y <= y2) {
				lu[y] = cu;
				lv[y] = cv;
				for (int a = 0; a < 2; a++) {
					for (int c = 0; c < 2; c++) {
						boolean far = a != ou && c != ov;
						if (y == y2 && far) {
							continue;
						}
						t.wood(u0 + a, y, v0 + c, wd.bark()); // bark: no pale log ends on the shoulders
					}
				}
				continue;
			}
			double f = Math.min(1.0, (y - y2) / (double) span);
			double e = f * f * (3 - 2 * f);
			int tu = u0 + ou + (int) Math.round(leanU * e);
			int tv = v0 + ov + (int) Math.round(leanV * e);
			int du = Integer.signum(tu - cu);
			int dv = Integer.signum(tv - cv);
			if (du != 0 && dv != 0) {
				if ((y & 1) == 0) {
					dv = 0;
				} else {
					du = 0;
				}
			}
			if (du != 0 || dv != 0) {
				// the step: a sideways log on the layer below joins the old and the new column
				t.wood(cu + du, y - 1, cv + dv, wd.log(du != 0 ? Direction.Axis.X : Direction.Axis.Z));
			}
			cu += du;
			cv += dv;
			lu[y] = cu;
			lv[y] = cv;
			t.wood(cu, y, cv, wd.log(Direction.Axis.Y));
		}
		if (yTop > y2 && yTop < t.h) {
			t.wood(cu, yTop, cv, wd.bark());
		}
		for (int y = Math.min(yTop, t.h - 1) + 1; y <= t.h; y++) {
			lu[y] = cu;
			lv[y] = cv;
		}
		return new int[][] {lu, lv};
	}

	// ------------------------------------------------------------------ roots

	/** N, NE, E, SE, S, SW, W, NW as {du, dv}. */
	static final int[][] OCT = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

	/**
	 * Roots around a 2×2 base at {@code (u0, v0)}: {@code count} of the eight directions (hashed, or the
	 * {@code bias} octant and its neighbours first), each 1..{@code maxLen} long. Face roots are horizontal
	 * logs that wander a cell sideways; corner roots run diagonally as a plan staircase. Each has a flare (one
	 * block on y = 1) against the trunk and a muddy tip.
	 */
	static void roots(Vox t, Wood wd, int u0, int v0, int count, int maxLen, int bias, int salt) {
		boolean[] used = new boolean[8];
		int made = 0;
		for (int pass = 0; pass < 32 && made < count; pass++) {
			int off = (pass + 1) / 2 * (pass % 2 == 1 ? 1 : -1);
			int k = bias >= 0 && pass < 8 ? Math.floorMod(bias + off, 8) : t.hash(pass, 7, salt, 8);
			if (used[k]) {
				continue;
			}
			used[k] = true;
			made++;
			int du = OCT[k][0];
			int dv = OCT[k][1];
			boolean diag = du != 0 && dv != 0;
			int su = du < 0 ? u0 - 1 : du > 0 ? u0 + 2 : u0 + t.hash(k, 1, salt, 2);
			int sv = dv < 0 ? v0 - 1 : dv > 0 ? v0 + 2 : v0 + t.hash(k, 2, salt, 2);
			int len = k == bias ? maxLen + 1 : 1 + t.hash(k, 3, salt, maxLen);
			Direction.Axis ax = du != 0 && !diag ? Direction.Axis.X : Direction.Axis.Z;
			int u = su;
			int v = sv;
			if (diag) {
				// the corner cell touches the trunk only by an edge: tie it with the face cell next to it
				t.woodSoft(u - du, 0, v, wd.bark());
			}
			for (int i = 0; i < len; i++) {
				boolean tip = i == len - 1 && len > 1;
				BlockState s = tip ? wd.rootTip() : i == 0 ? wd.bark() : diag ? wd.bark() : wd.log(ax);
				t.wood(u, 0, v, s);
				if (i == 0 && len >= 2 && t.hash(u, 1, v, 3) == 0) {
					t.woodSoft(u, 1, v, wd.bark()); // the flare up the trunk
				}
				if (diag) {
					// a plan staircase: one step along u, then along v
					int nu = u + du;
					int nv = v + dv;
					if (i < len - 1) {
						t.woodSoft(t.hash(u, 0, v, 2) == 0 ? nu : u, 0, t.hash(u, 0, v, 2) == 0 ? v : nv,
								i + 1 == len - 1 ? wd.rootTip() : wd.bark());
					}
					u = nu;
					v = nv;
				} else {
					u += du;
					v += dv;
					if (i >= 1 && t.hash(k, i, salt + 3, 3) == 0) { // wander one cell sideways
						int wu = dv != 0 ? (t.hash(k, i, 1, 2) * 2 - 1) : 0;
						int wv = du != 0 ? (t.hash(k, i, 1, 2) * 2 - 1) : 0;
						t.woodSoft(u - du + wu, 0, v - dv + wv, wd.rootTip());
						u += wu;
						v += wv;
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ branches

	/** A 6-connected 3D line (each cell shares a face with the next). */
	static List<int[]> line(int u0, int y0, int v0, int u1, int y1, int v1) {
		int n = Math.max(Math.abs(u1 - u0), Math.max(Math.abs(y1 - y0), Math.abs(v1 - v0)));
		List<int[]> out = new ArrayList<>();
		int pu = u0;
		int py = y0;
		int pv = v0;
		out.add(new int[] {pu, py, pv});
		for (int i = 1; i <= n; i++) {
			double f = i / (double) n;
			int cu = (int) Math.round(u0 + (u1 - u0) * f);
			int cy = (int) Math.round(y0 + (y1 - y0) * f);
			int cv = (int) Math.round(v0 + (v1 - v0) * f);
			while (pu != cu || py != cy || pv != cv) {
				if (py != cy && (i & 1) == 1) {
					py += Integer.signum(cy - py);
				} else if (pu != cu) {
					pu += Integer.signum(cu - pu);
				} else if (pv != cv) {
					pv += Integer.signum(cv - pv);
				} else {
					py += Integer.signum(cy - py);
				}
				out.add(new int[] {pu, py, pv});
			}
		}
		return out;
	}

	/**
	 * A limb from {@code a} to {@code b} ({u, y, v}): logs whose axis follows the step into each cell (the
	 * first cell is bark), and with {@code thick} a second log under the first third of the limb.
	 *
	 * @return the cells of the limb
	 */
	static List<int[]> branch(Vox t, Wood wd, int[] a, int[] b, boolean thick) {
		List<int[]> cells = line(a[0], a[1], a[2], b[0], b[1], b[2]);
		int n = cells.size();
		for (int i = 0; i < n; i++) {
			int[] c = cells.get(i);
			int[] prev = i > 0 ? cells.get(i - 1) : null;
			int[] next = i < n - 1 ? cells.get(i + 1) : null;
			int[] ref = next != null ? next : prev;
			BlockState s;
			if (i == 0 || ref == null) {
				s = wd.bark();
			} else {
				Direction.Axis ax = ref[1] != c[1] ? Direction.Axis.Y : ref[0] != c[0] ? Direction.Axis.X : Direction.Axis.Z;
				// a corner (the step in and the step out on different axes) reads better as bark
				if (prev != null && next != null && axis(prev, c) != axis(c, next)) {
					s = wd.bark();
				} else {
					s = wd.log(ax);
				}
			}
			t.wood(c[0], c[1], c[2], s);
			if (thick && i > 0 && i < n / 3 + 1) {
				t.woodSoft(c[0], c[1] - 1, c[2], wd.bark());
			}
		}
		return cells;
	}

	private static Direction.Axis axis(int[] a, int[] b) {
		return a[1] != b[1] ? Direction.Axis.Y : a[0] != b[0] ? Direction.Axis.X : Direction.Axis.Z;
	}

	// ------------------------------------------------------------------ foliage

	/**
	 * A rounded lobe of leaves centred on {@code (cu, cy, cv)}: semi-axes {@code rx} (u), {@code rz} (v),
	 * {@code up} above the centre and {@code down} below it (flatter below reads as a layered pad). The edge
	 * is jittered by {@code rough} (0..0.5) per cell, so the outline is never a clean ellipse, and corner
	 * cells are always trimmed. Tones: {@code lv.a} dominant, {@code lv.b} in 2×2×2 clumps (more on the
	 * underside), {@code lv.accent} sprinkled on the upper shell.
	 */
	static void lobe(Vox t, double cu, double cy, double cv, double rx, double rz, double up, double down,
			Leaves lv, double rough, int salt) {
		cy = Math.round(cy); // whole layers: a domed profile (wide middle, narrower top, small underside)
		int u1 = (int) Math.floor(cu - rx - 1);
		int u2 = (int) Math.ceil(cu + rx + 1);
		int v1 = (int) Math.floor(cv - rz - 1);
		int v2 = (int) Math.ceil(cv + rz + 1);
		int y1 = (int) Math.floor(cy - down - 1);
		int y2 = (int) Math.ceil(cy + up + 1);
		for (int y = y1; y <= y2; y++) {
			for (int v = v1; v <= v2; v++) {
				for (int u = u1; u <= u2; u++) {
					double du = (u - cu) / rx;
					double dv = (v - cv) / rz;
					double dyr = y - cy;
					double dy = dyr >= 0 ? dyr / Math.max(0.5, up) : dyr / Math.max(0.5, down);
					double q = du * du + dv * dv + dy * dy;
					// a jittered edge: small bites and bumps (kept small: big outward bumps square the outline)
					double jit = (t.hash(u + salt * 13, y + salt * 7, v + salt * 3, 9) - 4.5) / 4.0 * rough * 0.7;
					if (q > 1.0 + jit) {
						continue;
					}
					// trimmed corners: never a cube
					if (Math.abs(du) > 0.62 && Math.abs(dv) > 0.62 && Math.abs(dy) > 0.55) {
						continue;
					}
					t.leaf(u, y, v, tone(t, lv, u, y, v, dy, q, salt));
				}
			}
		}
	}

	/** The leaf for a cell of a lobe: clumps of the second tone (more below), the accent on the upper shell. */
	static BlockState tone(Vox t, Leaves lv, int u, int y, int v, double dy, double q, int salt) {
		boolean clump = t.hash(Math.floorDiv(u + salt, 2), Math.floorDiv(y, 2), Math.floorDiv(v - salt, 2), lv.clumpOneIn()) == 0;
		if (lv.accent() != null && lv.accentOneIn() > 0 && dy > 0.1 && q > 0.35
				&& t.hash(u, y + 31, v + salt, lv.accentOneIn()) == 0) {
			return lv.accent();
		}
		if (clump || (dy < -0.3 && t.hash(u + 5, y, v, 3) == 0)) {
			return lv.b();
		}
		return lv.a();
	}

	/**
	 * A hanging strand from {@code (u, yTop, v)} down {@code len} cells (never below {@code minY}). With
	 * {@code kink} it steps one cell outwards ({@code ku, kv}) a third of the way down, so it is not a
	 * straight curtain. The last cell is {@code tip}.
	 */
	static void hang(Vox t, int u, int yTop, int v, int len, int minY, BlockState body, BlockState tip, int ku, int kv,
			boolean kink) {
		int cu = u;
		int cv = v;
		int kAt = Math.max(1, len / 3);
		for (int k = 0; k < len; k++) {
			int y = yTop - k;
			if (y < minY) {
				break;
			}
			if (kink && k == kAt && (ku != 0 || kv != 0)) {
				t.leaf(cu + ku, y + 1, cv + kv, body); // the step, face-connected
				cu += ku;
				cv += kv;
			}
			if (!t.empty(cu, y, cv) && t.kind(cu, y, cv) != LEAF) {
				break;
			}
			t.leaf(cu, y, cv, k == len - 1 ? tip : body);
		}
	}

	/** One- and two-block drips under the rim of a lobe, so its underside is not a flat plate. */
	static void drips(Vox t, int cu, int cy, int cv, double rx, double rz, int salt) {
		int y = cy - 1;
		for (int v = (int) (cv - rz); v <= cv + rz; v++) {
			for (int u = (int) (cu - rx); u <= cu + rx; u++) {
				double q = Math.pow((u - cu) / rx, 2) + Math.pow((v - cv) / rz, 2);
				if (q < 0.35 || q > 1.0 || t.hash(u, y + salt, v, 4) != 0) {
					continue;
				}
				if (t.kind(u, y + 1, v) != LEAF && t.kind(u, y, v) != LEAF) {
					continue;
				}
				int len = 1 + t.hash(u, 3, v + salt, 2);
				int top = t.kind(u, y, v) == LEAF ? y - 1 : y;
				hang(t, u, top, v, len, 2, SAKURA.a(), SAKURA.b(), 0, 0, false);
			}
		}
	}

	// ------------------------------------------------------------------ ground

	/** Cover on y = 0 within {@code radius} of {@code (cu, cv)}: {@code pick} gets (u, v, k in 0..1 from the centre) and may return null. */
	static void ground(Vox t, double cu, double cv, double radius, CoverPick pick) {
		for (int v = 0; v < t.d; v++) {
			for (int u = 0; u < t.w; u++) {
				double r = Math.hypot(u - cu, v - cv);
				if (r > radius || !t.empty(u, 0, v)) {
					continue;
				}
				BlockState s = pick.at(u, v, r / radius);
				if (s != null) {
					t.cover(u, 0, v, s);
				}
			}
		}
	}

	interface CoverPick {
		BlockState at(int u, int v, double k);
	}

	static BlockState petals(int amount, int facing) {
		return b(Blocks.PINK_PETALS)
				.setValue(BlockStateProperties.FLOWER_AMOUNT, Math.max(1, Math.min(4, amount)))
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(facing & 3));
	}

	/** Fallen petals thick at the trunk and thinning out, with moss in between (the cherries and the azalea). */
	static void petalCarpet(Vox t, double cu, double cv, double radius) {
		ground(t, cu, cv, radius, (u, v, k) -> {
			int hsh = t.hash(u, 0, v, 100);
			int pet = (int) (80 - 45 * k);
			if (hsh < pet) {
				return petals(1 + t.hash(u, 1, v, k < 0.5 ? 4 : 2), t.hash(u, 2, v, 4));
			}
			if (hsh < pet + 10) {
				return b(Blocks.MOSS_CARPET);
			}
			return null;
		});
	}

	/** How far you can go from {@code (u, v)} along {@code (ex, ez)} before leaving the box (minus a margin). */
	static double room(Vox t, double u, double v, double ex, double ez, double margin) {
		double s = 1e9;
		if (ex > 1e-6) {
			s = Math.min(s, (t.w - 1 - margin - u) / ex);
		} else if (ex < -1e-6) {
			s = Math.min(s, (u - margin) / -ex);
		}
		if (ez > 1e-6) {
			s = Math.min(s, (t.d - 1 - margin - v) / ez);
		} else if (ez < -1e-6) {
			s = Math.min(s, (v - margin) / -ez);
		}
		return Math.max(0, s);
	}

	/** The 2×2 base's low index on an axis of {@code n} cells so that its cell {@code o} (the leader) is the middle one. */
	static int base(int n, int o) {
		return clamp((n - 1) / 2 - o, 0, n - 2);
	}

	static int clamp(int x, int lo, int hi) {
		return Math.max(lo, Math.min(hi, x));
	}

	static double clamp(double x, double lo, double hi) {
		return Math.max(lo, Math.min(hi, x));
	}
}
