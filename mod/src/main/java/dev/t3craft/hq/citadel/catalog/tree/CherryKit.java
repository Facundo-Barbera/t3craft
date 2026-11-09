package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Plot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Shared toolbox of the custom cherry trees ({@link TreeCherryGrand}, {@link TreeCherryLeaning},
 * {@link TreeCherryWeeping}). Not a variant: package-private helpers.
 *
 * <p>A tree is first drawn into a {@link Vox} (a voxel buffer the size of the plot, ignoring anything
 * outside, so a variant never clips) and flushed through {@link Plot#set} at the end. Wood is written
 * with overwrite, leaves and ground cover only into empty cells, so the order of the pieces does not
 * matter and branches always show through the foliage.
 *
 * <p>The techniques, from the "sakura" builds of the Minecraft tree builders:
 * <ul>
 * <li><b>Trunk 2×2 that thins</b>: four cells at the base, three at the fork, one above; a leaning trunk
 *     moves one block at a time on one axis, and the 1×1 part gets a corner filler so it stays face-connected.</li>
 * <li><b>Roots</b>: a flare one block high against the trunk, wood next to it, muddy mangrove roots farther
 *     out, running over the grass in 5–8 directions.</li>
 * <li><b>Branches</b>: 6-connected 3D lines, thick near the trunk, with pale stripped wood towards the tip.</li>
 * <li><b>Canopy</b>: never a ball. Flat elliptical "cloud pads" turned along their branch, with ragged
 *     edges, sky holes, a lighter top and a darker azalea underside, and dripping fringes.</li>
 * <li><b>Ground</b>: pink petals and moss carpet thinning out from the trunk to the edge of the canopy.</li>
 * </ul>
 */
final class CherryKit {
	static final BlockState WOOD = b(Blocks.CHERRY_WOOD);
	static final BlockState PALE = b(Blocks.STRIPPED_CHERRY_WOOD);
	static final BlockState LEAF = leaves(Blocks.CHERRY_LEAVES);
	static final BlockState LEAF2 = leaves(Blocks.FLOWERING_AZALEA_LEAVES);
	static final BlockState MUDROOT = b(Blocks.MUDDY_MANGROVE_ROOTS);
	static final BlockState MOSS = b(Blocks.MOSS_CARPET);

	private CherryKit() {
	}

	// ------------------------------------------------------------------ voxel buffer

	/** The tree being drawn: one state per cell of the plot, null = untouched. */
	static final class Vox {
		final Plot p;
		final int w;
		final int d;
		final int h;
		private final BlockState[] cells;

		Vox(Plot p) {
			this.p = p;
			this.w = p.w();
			this.d = p.d();
			this.h = p.h();
			this.cells = new BlockState[w * d * h];
		}

		boolean in(int u, int y, int v) {
			return u >= 0 && u < w && v >= 0 && v < d && y >= 0 && y < h;
		}

		BlockState get(int u, int y, int v) {
			return in(u, y, v) ? cells[(y * d + v) * w + u] : null;
		}

		/** Overwrites a cell (wood). */
		void set(int u, int y, int v, BlockState s) {
			if (in(u, y, v)) {
				cells[(y * d + v) * w + u] = s;
			}
		}

		/** Writes only into an empty cell (leaves, ground cover). */
		void add(int u, int y, int v, BlockState s) {
			if (in(u, y, v) && cells[(y * d + v) * w + u] == null) {
				cells[(y * d + v) * w + u] = s;
			}
		}

		boolean empty(int u, int y, int v) {
			return in(u, y, v) && get(u, y, v) == null;
		}

		boolean isWood(int u, int y, int v) {
			BlockState s = get(u, y, v);
			return s == WOOD || s == PALE || s == MUDROOT;
		}

		int hash(int u, int y, int v, int n) {
			return p.hash(u, y, v, n);
		}

		/**
		 * Wood for a cell: muddy roots at the foot of the trunk, bark in the middle, pale (stripped) streaks
		 * two layers tall that get more frequent towards the tips ({@code f} in 0..1 along the tree).
		 */
		BlockState wood(int u, int y, int v, double f) {
			if (y == 0 && hash(u, y, v, 3) == 0) {
				return MUDROOT;
			}
			if (f < 0.4) {
				return WOOD; // the trunk stays dark bark
			}
			return hash(u, y / 2, v, f > 0.7 ? 2 : 4) == 0 ? PALE : WOOD;
		}

		void flush(CitadelCanvas c) {
			for (int y = 0; y < h; y++) {
				for (int v = 0; v < d; v++) {
					for (int u = 0; u < w; u++) {
						BlockState s = cells[(y * d + v) * w + u];
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
	 * A trunk path: {@code (pu[y], pv[y])} is the lower-left cell of the trunk at height y. It follows
	 * {@code target} one block at a time, on one axis per layer, so consecutive layers overlap.
	 */
	static int[][] path(int h, int u0, int v0, double leanU, double leanV, int yEase, double yEnd) {
		int[] pu = new int[h];
		int[] pv = new int[h];
		int cu = u0;
		int cv = v0;
		for (int y = 0; y < h; y++) {
			double t = Math.min(1.0, y / Math.max(1.0, yEnd));
			double e = t * t * (3 - 2 * t); // smoothstep: straight at the foot, bends in the middle
			if (y < yEase) {
				e *= 0.0;
			}
			int tu = u0 + (int) Math.round(leanU * e);
			int tv = v0 + (int) Math.round(leanV * e);
			int du = Integer.signum(tu - cu);
			int dv = Integer.signum(tv - cv);
			if (du != 0 && dv != 0) {
				if ((y & 1) == 0) {
					dv = 0;
				} else {
					du = 0;
				}
			}
			cu += du;
			cv += dv;
			pu[y] = cu;
			pv[y] = cv;
		}
		return new int[][] {pu, pv};
	}

	/**
	 * Paints the trunk along {@code path}: 2×2 below {@code yFork}, 3 cells at the fork, a single cell
	 * (the one at offset {@code (ou, ov)} of the 2×2) up to {@code yEnd}. Returns the 1×1 cells' {@code u,v}
	 * per layer in {@code leader[0]/leader[1]} (the leader's position, also valid below the fork).
	 */
	static int[][] trunk(Vox t, int[][] path, int yFork, int yEnd, int ou, int ov) {
		int[] pu = path[0];
		int[] pv = path[1];
		int[] lu = new int[t.h];
		int[] lv = new int[t.h];
		for (int y = 0; y <= Math.min(yEnd, t.h - 1); y++) {
			double f = y / (double) Math.max(1, yEnd);
			int u = pu[y];
			int v = pv[y];
			lu[y] = u + ou;
			lv[y] = v + ov;
			if (y < yFork) {
				for (int a = 0; a < 2; a++) {
					for (int c = 0; c < 2; c++) {
						t.set(u + a, y, v + c, t.wood(u + a, y, v + c, f * 0.6));
					}
				}
			} else if (y == yFork) {
				int skip = t.hash(0, 0, 7, 4); // the cell away from the leader goes first
				for (int a = 0; a < 2; a++) {
					for (int c = 0; c < 2; c++) {
						if (a * 2 + c == skip && !(a == ou && c == ov)) {
							continue;
						}
						t.set(u + a, y, v + c, t.wood(u + a, y, v + c, f * 0.6));
					}
				}
			} else {
				t.set(lu[y], y, lv[y], t.wood(lu[y], y, lv[y], f));
				// corner filler when the leader steps sideways, to keep it face-connected
				if (y > 0 && y - 1 >= yFork && (lu[y] != lu[y - 1] || lv[y] != lv[y - 1])) {
					t.set(lu[y], y - 1, lv[y], t.wood(lu[y], y - 1, lv[y], f));
				}
			}
		}
		return new int[][] {lu, lv};
	}

	// ------------------------------------------------------------------ roots

	/** The eight compass steps around the trunk: N, NE, E, SE, S, SW, W, NW as {dx (u), dz (v)}. */
	private static final int[][] OCT = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

	/**
	 * Roots that spill over the grass around the 2×2 trunk base at {@code (tu, tv)}: a one-block flare
	 * against the trunk, wood next to it, muddy mangrove roots on the far end. {@code bias} (-1 for none,
	 * else an octant) makes that side longer and heavier (a leaning tree pulls roots on the side opposite
	 * to the lean).
	 */
	static void roots(Vox t, int tu, int tv, int count, int maxLen, int bias) {
		boolean[] used = new boolean[8];
		int made = 0;
		for (int pass = 0; pass < 24 && made < count; pass++) {
			// with a bias: that octant first, then its neighbours alternating left/right; else hashed
			int off = (pass + 1) / 2 * (pass % 2 == 1 ? 1 : -1);
			int k = pass < 8 && bias >= 0 ? Math.floorMod(bias + off, 8) : t.hash(pass, 5, 3, 8);
			if (used[k]) {
				continue;
			}
			used[k] = true;
			made++;
			int dx = OCT[k][0];
			int dz = OCT[k][1];
			boolean diag = dx != 0 && dz != 0;
			int su = dx < 0 ? tu - 1 : dx > 0 ? tu + 2 : tu + t.hash(k, 1, 9, 2);
			int sv = dz < 0 ? tv - 1 : dz > 0 ? tv + 2 : tv + t.hash(k, 2, 9, 2);
			// uneven lengths: some roots are stubs, one is a long wandering root
			int len = k == bias ? maxLen + 1 : 1 + t.hash(k, 3, 9, maxLen + 1) - (diag ? 1 : 0);
			len = Math.max(1, len);
			for (int i = 0; i < len; i++) {
				int u = su + dx * i;
				int v = sv + dz * i;
				if (!diag && i >= 2) { // wander sideways
					int w = t.hash(k, i, 5, 3) - 1;
					if (dx == 0) {
						u += w;
					} else {
						v += w;
					}
				}
				if (diag && i > 0) { // fill the corner so it stays connected
					t.add(u - dx, 0, v, t.hash(u, 0, v, 2) == 0 ? MUDROOT : WOOD);
				}
				if (i == 0) {
					t.set(u, 0, v, WOOD);
					if (len >= 2 && t.hash(u, 1, v, 3) != 0) { // the root climbs the trunk: a flare one block high
						t.add(u, 1, v, WOOD);
					}
				} else if (i == 1 && len > 2) {
					t.set(u, 0, v, t.hash(u, 0, v, 3) == 0 ? MUDROOT : WOOD);
				} else {
					t.set(u, 0, v, MUDROOT);
				}
			}
		}
	}

	// ------------------------------------------------------------------ branches

	/** A 6-connected 3D line (every cell shares a face with the next). */
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
				if (pu != cu) {
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
	 * Paints a branch line: thick (an extra block below, and beside on level 2) near the trunk,
	 * single towards the tip.
	 */
	static void branch(Vox t, List<int[]> cells, int thick, double hx, double hz) {
		int n = cells.size();
		for (int i = 0; i < n; i++) {
			int[] c = cells.get(i);
			double f = n <= 1 ? 1.0 : i / (double) (n - 1);
			t.set(c[0], c[1], c[2], t.wood(c[0], c[1], c[2], f));
			if (thick >= 1 && f < 0.5) {
				t.set(c[0], c[1] - 1, c[2], t.wood(c[0], c[1] - 1, c[2], f));
			}
			if (thick >= 2 && f < 0.3) {
				boolean alongU = Math.abs(hx) > Math.abs(hz);
				int pu = alongU ? 0 : 1; // beside the branch, across its main direction
				int pv = alongU ? 1 : 0;
				t.set(c[0] + pu, c[1], c[2] + pv, t.wood(c[0] + pu, c[1], c[2] + pv, f));
				t.set(c[0] + pu, c[1] - 1, c[2] + pv, t.wood(c[0] + pu, c[1] - 1, c[2] + pv, f));
			}
		}
	}

	/** How far you can walk from {@code (u, v)} along the unit vector {@code (ex, ez)} before leaving the box. */
	static double room(Vox t, double u, double v, double ex, double ez) {
		double s = 1e9;
		if (ex > 1e-6) {
			s = Math.min(s, (t.w - 1 - u) / ex);
		} else if (ex < -1e-6) {
			s = Math.min(s, u / -ex);
		}
		if (ez > 1e-6) {
			s = Math.min(s, (t.d - 1 - v) / ez);
		} else if (ez < -1e-6) {
			s = Math.min(s, v / -ez);
		}
		return s;
	}

	// ------------------------------------------------------------------ canopy

	/** Radius factor of a pad layer by its distance from the middle layer: lower layers a bit wider than upper. */
	private static final double[] PROF_UP = {1.0, 0.84, 0.52};
	private static final double[] PROF_DOWN = {1.0, 0.62, 0.3};

	/**
	 * A flat cloud of leaves centred on {@code (cu, cy, cv)}: an ellipse of semi-axes {@code ra} (along
	 * the direction {@code (ex, ez)}) and {@code rc} (across), {@code below} layers under the middle one
	 * and {@code above} over it. Ragged edge, a few sky holes, azalea leaves underneath and a few on the
	 * rim, and short dripping fringes under the lowest layer (never lower than {@code minY}).
	 */
	static void pad(Vox t, double cu, double cy, double cv, double ra, double rc, double ex, double ez,
			int below, int above, int salt, int minY) {
		double len = Math.max(1e-6, Math.hypot(ex, ez));
		ex /= len;
		ez /= len;
		int iy = (int) Math.round(cy);
		int span = (int) Math.ceil(Math.max(ra, rc)) + 1;
		int iu = (int) Math.round(cu);
		int iv = (int) Math.round(cv);
		for (int ly = -below; ly <= above; ly++) {
			double f = ly >= 0 ? PROF_UP[Math.min(ly, 2)] : PROF_DOWN[Math.min(-ly, 2)];
			int y = iy + ly;
			for (int u = iu - span; u <= iu + span; u++) {
				for (int v = iv - span; v <= iv + span; v++) {
					double du = u - cu;
					double dv = v - cv;
					double al = du * ex + dv * ez;
					double ac = -du * ez + dv * ex;
					double q = (al / ra) * (al / ra) + (ac / rc) * (ac / rc);
					double noise = (t.hash(u + salt * 7, y + salt * 3, v, 9) - 4) * 0.11;
					if (q > f * f * (1.0 + noise)) {
						// stray leaves just beyond the edge: the cloud is not a clean ellipse
						if (q <= f * f * 1.6 && ly <= 0 && t.hash(u + 3, y, v + salt, 7) == 0) {
							t.add(u, y, v, LEAF);
						}
						continue;
					}
					if (ly == 0 && q > 0.3 && t.hash(u, y + salt, v + salt * 5, 11) == 0) {
						continue; // a hole in the cloud: sky and branch show through
					}
					// azalea (the deeper tone) in clumps: under the cloud and on its lower rim, never on top
					boolean clump = t.hash(Math.floorDiv(u, 2) + salt, y, Math.floorDiv(v, 2), 3) == 0;
					BlockState s = LEAF;
					if (ly < 0 && (clump || t.hash(u, y, v + salt, 12) == 0)) {
						s = LEAF2;
					} else if (ly == 0 && q > 0.55 && clump && t.hash(u, y, v, 3) != 0) {
						s = LEAF2;
					}
					t.add(u, y, v, s);
					// fringe: only under the lowest layer, mostly on the rim
					if (ly == -below && q > 0.45 && t.hash(u + 2, y, v + 1, 3) == 0) {
						int drip = 1 + t.hash(u, y + 2, v, 2);
						for (int k = 1; k <= drip && y - k >= minY; k++) {
							t.add(u, y - k, v, k == drip ? LEAF2 : LEAF);
						}
					}
				}
			}
		}
	}

	/**
	 * A cloud: the main {@link #pad} plus a small bump on top at one end and a thin lobe low at the other,
	 * so the outline is bumpy instead of an ellipse.
	 */
	static void cloud(Vox t, double cu, double cy, double cv, double ra, double rc, double ex, double ez,
			int below, int above, int salt, int minY) {
		pad(t, cu, cy, cv, ra, rc, ex, ez, below, above, salt, minY);
		double len = Math.max(1e-6, Math.hypot(ex, ez));
		double dx = ex / len;
		double dz = ez / len;
		double px = -dz;
		double pz = dx;
		int sg = t.hash(salt, 1, 2, 2) == 0 ? 1 : -1;
		pad(t, cu + dx * ra * 0.45 + px * rc * 0.4 * sg, cy + 1, cv + dz * ra * 0.45 + pz * rc * 0.4 * sg,
				ra * 0.5, rc * 0.55, dx, dz, 0, 1, salt + 40, minY);
		pad(t, cu - dx * ra * 0.5 - px * rc * 0.45 * sg, cy - 1, cv - dz * ra * 0.5 - pz * rc * 0.45 * sg,
				ra * 0.55, rc * 0.5, dx, dz, 0, 0, salt + 50, minY);
	}

	/** A hanging strand of leaves (weeping curtains): cherry leaves with an azalea tip, never below {@code minY}. */
	static void strand(Vox t, int u, int yTop, int v, int length, int minY) {
		for (int k = 0; k < length; k++) {
			int y = yTop - k;
			if (y < minY) {
				break;
			}
			t.add(u, y, v, k == length - 1 || (k > 1 && t.hash(u, y, v, 4) == 0) ? LEAF2 : LEAF);
		}
	}

	// ------------------------------------------------------------------ ground

	/**
	 * Fallen petals and moss under the tree: thick near the trunk, thinning out to {@code radius}. Only
	 * empty cells of layer y = 0; the root cells and the trunk are not touched.
	 */
	static void ground(Vox t, double cu, double cv, double radius) {
		for (int v = 0; v < t.d; v++) {
			for (int u = 0; u < t.w; u++) {
				if (!t.empty(u, 0, v)) {
					continue;
				}
				double r = Math.hypot(u - cu, (v - cv) * 1.0);
				if (r > radius) {
					continue;
				}
				double k = r / radius; // 0 at the trunk, 1 at the rim
				int h = t.hash(u, 0, v, 100);
				int petals = (int) (78 - 38 * k);
				int moss = petals + (int) (14 - 6 * k);
				if (h < petals) {
					t.add(u, 0, v, petals(1 + t.hash(u, 1, v, k < 0.55 ? 4 : 3), t.hash(u, 2, v, 4)));
				} else if (h < moss) {
					t.add(u, 0, v, MOSS);
				}
			}
		}
	}

	static BlockState petals(int amount, int facing) {
		return b(Blocks.PINK_PETALS)
				.setValue(BlockStateProperties.FLOWER_AMOUNT, Math.max(1, Math.min(4, amount)))
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(facing));
	}
}
