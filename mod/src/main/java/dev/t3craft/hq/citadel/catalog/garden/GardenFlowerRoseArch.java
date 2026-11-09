package dev.t3craft.hq.citadel.catalog.garden;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A flower garden with a rose tunnel: a 3-wide slab walk runs down the middle of the plot under a row of
 * cherry-wood arches (posts with a pale plinth, a plank beam, rails along both sides and a roof of cherry and
 * flowering-azalea leaves, with roses at the foot of every post and vines hanging off the rails), past a small
 * paved square with a bird-bath, and ends in a niche with a bench under the last arch, facing back down the walk.
 *
 * <pre>
 *  v0       entry: lamps flank the walk, first arch right behind them
 *  tunnel   an arch every 4-5 blocks (n grows with the depth), boxwood hedge on a stone kerb along both sides,
 *           a lamp in the middle of every gap; leafy shoulders either side of each post
 *  square   (depth &gt;= 13) 5x5 paving with a bird-bath on a pedestal, a bench each side, lamps in the corners;
 *           deeper plots get one square per ~18 rows
 *  d-2      the bench in the niche, back hedge behind it
 *  wide     (width &gt;= 19) a cross walk with its own arches runs through every square to the plot's sides
 *  beds     the rest is parterres: raised grass beds in concentric colour rings (outer ring one colour, next
 *           ring the second, a core of tall roses / peonies / lilacs / sunflowers), edged in boxwood; big
 *           areas are cut into several parterres of about 9 by 9 that share their boxwood edge
 * </pre>
 *
 * Function: walkable end to end, every step a slab (half-block steps, no jumps), 3.5 blocks of headroom under
 * the arches, benches facing the beds, lamps along the walk. Every flower stands on grass (tall ones included), vines hang from the rails (a log), lanterns stand on fence posts, nothing floats.
 *
 * <p>Size: 9-24 x 9-24 x 5+, and more at scale: the number of arches, squares, cross walks and parterres grows
 * with the plot, the proportions do not stretch. By seed: flower palette (red / pink, white / blue, yellow /
 * orange), which colour takes the outer ring of each parterre, tall species, climbing-leaf mix. [depth] kerbs,
 * plinths, raised beds, rails and eaves, topiary at the hedge crossings. [palette] cherry wood and pale
 * stones, grass and boxwood, one colour family of flowers per ring. [finish] lamps, bird-bath, vines, bench.
 */
public final class GardenFlowerRoseArch implements Variant {
	/** A flower palette: two colour groups of low flowers, tall flowers, climbing leaves, the rose at the posts. */
	private record Pal(Block[] a, Block[] b, Block[] tall, Block[] climb, Block arch) {
	}

	private static final Pal[] PALS = {
			new Pal(new Block[] {Blocks.RED_TULIP, Blocks.POPPY}, new Block[] {Blocks.PINK_TULIP, Blocks.PINK_PETALS},
					new Block[] {Blocks.ROSE_BUSH, Blocks.PEONY, Blocks.ROSE_BUSH},
					new Block[] {Blocks.CHERRY_LEAVES, Blocks.CHERRY_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES}, Blocks.ROSE_BUSH),
			new Pal(new Block[] {Blocks.WHITE_TULIP, Blocks.OXEYE_DAISY}, new Block[] {Blocks.CORNFLOWER, Blocks.BLUE_ORCHID},
					new Block[] {Blocks.LILAC, Blocks.LILAC, Blocks.PEONY},
					new Block[] {Blocks.FLOWERING_AZALEA_LEAVES, Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES}, Blocks.LILAC),
			new Pal(new Block[] {Blocks.DANDELION, Blocks.OXEYE_DAISY}, new Block[] {Blocks.ORANGE_TULIP, Blocks.RED_TULIP},
					new Block[] {Blocks.SUNFLOWER, Blocks.ROSE_BUSH},
					new Block[] {Blocks.FLOWERING_AZALEA_LEAVES, Blocks.CHERRY_LEAVES, Blocks.AZALEA_LEAVES}, Blocks.ROSE_BUSH)
	};

	@Override
	public String id() {
		return "garden_flower_rose_arch";
	}

	@Override
	public Family family() {
		return Family.GARDEN;
	}

	@Override
	public int minW() {
		return 9;
	}

	@Override
	public int minD() {
		return 9;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 24;
	}

	@Override
	public int maxD() {
		return 24;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		new Garden(c, p).run();
	}

	private static final class Garden {
		final CitadelCanvas c;
		final Plot p;
		final int w;
		final int d;
		final Pal pal;
		final int swap;
		/** Seed: one more arch per run, benches on the other side. */
		final int alt;
		/** Left column of the walk (it is 3 wide: pu .. pu + 2). */
		final int pu;
		/** Rows of the centres of the squares (none when the plot is too shallow). */
		int[] vcs;
		boolean cross;
		final List<Integer> mainFrames = new ArrayList<>();
		int[] firstQuad;

		Garden(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			this.w = p.w();
			this.d = p.d();
			this.pal = PALS[p.hash(0, 0, 0, PALS.length)];
			this.swap = p.hash(1, 0, 1, 2);
			this.alt = p.hash(3, 0, 3, 2);
			this.pu = (w - 3) / 2;
		}

		void set(int u, int y, int v, BlockState s) {
			if (p.has(u, y, v)) {
				p.set(c, u, y, v, s);
			}
		}

		void run() {
			int n = d < 13 ? 0 : Math.max(1, d / 18);
			vcs = new int[n];
			for (int i = 0; i < n; i++) {
				vcs[i] = (2 * i + 1) * d / (2 * n);
			}
			cross = w >= 19 && n > 0;
			beds();
			ring();
			paths();
			sides();
			arms();
			furniture();
		}

		// ------------------------------------------------------------------ small helpers

		BlockState kerb(int u, int v) {
			return switch (p.hash(u, 0, v, 4)) {
				case 0 -> b(Blocks.MOSSY_STONE_BRICKS);
				case 1 -> b(Blocks.CRACKED_STONE_BRICKS);
				default -> b(Blocks.STONE_BRICKS);
			};
		}

		/** Boxwood: azalea leaves with the odd flowering one. */
		BlockState box(int u, int y, int v) {
			return p.hash(u, y + 30, v, 7) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES);
		}

		BlockState climb(int u, int y, int v) {
			return leaves(pal.climb[p.hash(u, y + 50, v, pal.climb.length)]);
		}

		BlockState soil(int u, int v) {
			return b(Blocks.GRASS_BLOCK);
		}

		BlockState pathSlab(int u, int v, boolean rim) {
			if (rim) {
				return p.hash(u, 2, v, 4) == 0 ? slab(Blocks.ANDESITE_SLAB) : slab(Blocks.POLISHED_ANDESITE_SLAB);
			}
			return switch (p.hash(u, 1, v, 8)) {
				case 0 -> slab(Blocks.MOSSY_STONE_BRICK_SLAB);
				case 1, 2 -> slab(Blocks.ANDESITE_SLAB);
				case 3 -> slab(Blocks.COBBLESTONE_SLAB);
				default -> slab(Blocks.STONE_BRICK_SLAB);
			};
		}

		void tall(int u, int y, int v, Block plant) {
			set(u, y, v, b(plant).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			set(u, y + 1, v, b(plant).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		}

		BlockState vine(Direction face) {
			BlockState s = Blocks.VINE.defaultBlockState();
			return switch (face) {
				case NORTH -> s.setValue(BlockStateProperties.NORTH, true);
				case SOUTH -> s.setValue(BlockStateProperties.SOUTH, true);
				case EAST -> s.setValue(BlockStateProperties.EAST, true);
				default -> s.setValue(BlockStateProperties.WEST, true);
			};
		}

		boolean plazaRow(int v) {
			for (int vc : vcs) {
				if (Math.abs(v - vc) <= 2) {
					return true;
				}
			}
			return false;
		}

		/** A lamp post: stone footing, two fence posts, a lantern. */
		void lamp(int u, int v) {
			set(u, 0, v, b(Blocks.STONE_BRICKS));
			set(u, 1, v, b(Blocks.CHERRY_FENCE));
			set(u, 2, v, b(Blocks.CHERRY_FENCE));
			set(u, 3, v, b(Blocks.LANTERN));
		}

		// ------------------------------------------------------------------ parterres

		void beds() {
			List<int[]> cols = new ArrayList<>();
			cols.add(new int[] {1, pu - 2});
			cols.add(new int[] {pu + 4, w - 2});
			List<int[]> rows = new ArrayList<>();
			if (cross) {
				int from = 1;
				for (int vc : vcs) {
					rows.add(new int[] {from, vc - 3});
					from = vc + 3;
				}
				rows.add(new int[] {from, d - 2});
			} else {
				rows.add(new int[] {1, d - 2});
			}
			int qi = 0;
			for (int[] r : rows) {
				for (int[] cl : cols) {
					if (cl[1] >= cl[0] && r[1] >= r[0]) {
						if (firstQuad == null) {
							firstQuad = new int[] {cl[0], cl[1], r[0], r[1]};
						}
						quad(cl[0], cl[1], r[0], r[1], qi++);
					}
				}
			}
		}

		static int[] cuts(int len, int n) {
			int[] cu = new int[n + 1];
			cu[0] = -1;
			cu[n] = len;
			for (int i = 1; i < n; i++) {
				cu[i] = (int) Math.round(i * (len - 1) / (double) n);
			}
			return cu;
		}

		static int tile(int[] cuts, int x) {
			int t = 0;
			for (int i = 1; i < cuts.length - 1; i++) {
				if (cuts[i] < x) {
					t = i;
				}
			}
			return t;
		}

		static boolean isCut(int[] cuts, int x) {
			for (int i = 1; i < cuts.length - 1; i++) {
				if (cuts[i] == x) {
					return true;
				}
			}
			return false;
		}

		void quad(int u0, int u1, int v0, int v1, int qi) {
			int bw = u1 - u0 + 1;
			int bh = v1 - v0 + 1;
			int nx = Math.max(1, (int) Math.round(bw / 9.0));
			int ny = Math.max(1, (int) Math.round(bh / 9.0));
			int[] cx = cuts(bw, nx);
			int[] cy = cuts(bh, ny);
			for (int dv = 0; dv < bh; dv++) {
				for (int du = 0; du < bw; du++) {
					int u = u0 + du;
					int v = v0 + dv;
					set(u, 0, v, soil(u, v));
					boolean cutU = isCut(cx, du);
					boolean cutV = isCut(cy, dv);
					if (cutU || cutV) {
						set(u, 1, v, box(u, 1, v));
						if (cutU && cutV) {
							set(u, 2, v, climb(u, 2, v)); // topiary where two hedges cross
						}
						continue;
					}
					int tx = tile(cx, du);
					int ty = tile(cy, dv);
					int lo = cx[tx] + 1;
					int hi = cx[tx + 1] - 1;
					int vlo = cy[ty] + 1;
					int vhi = cy[ty + 1] - 1;
					int tid = tx * 7 + ty * 3 + qi + swap;
					int k = Math.min(Math.min(du - lo, hi - du), Math.min(dv - vlo, vhi - dv));
					int m = Math.min(hi - lo + 1, vhi - vlo + 1);
					boolean tallCell;
					boolean groupA;
					if (m >= 7 && k == 1) { // a flowering boxwood ring between the border flowers and the core
						set(u, 1, v, p.hash(u, 1, v, 2) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
						continue;
					}
					if (m >= 7) {
						tallCell = k >= 3;
						groupA = (k == 0) == (swap == 0); // border flowers one colour, the ring inside the hedge the other
					} else if (m >= 3) {
						int thr = m >= 6 ? 2 : 1;
						tallCell = k >= thr;
						groupA = ((k + tid) & 1) == 0;
					} else {
						tallCell = p.hash(u, 5, v, 6) == 0;
						groupA = (((dv + tid * 2) / 3) & 1) == 0;
					}
					if (tallCell) {
						Block t = pal.tall[(tid + (p.hash(u, 6, v, 4) == 0 ? 1 : 0)) % pal.tall.length];
						tall(u, 1, v, t);
					} else {
						Block[] g = groupA ? pal.a : pal.b;
						set(u, 1, v, b(g[p.hash(u, 3, v, 6) == 0 ? 1 : 0]));
					}
				}
			}
		}

		// ------------------------------------------------------------------ ring hedge, walks, squares

		boolean opening(int u, int v) {
			if (v == 0 && u >= pu && u <= pu + 2) {
				return true;
			}
			if (cross && (u == 0 || u == w - 1)) {
				for (int vc : vcs) {
					if (Math.abs(v - vc) <= 1) {
						return true;
					}
				}
			}
			return false;
		}

		void ring() {
			for (int v = 0; v < d; v++) {
				for (int u = 0; u < w; u++) {
					if (u != 0 && u != w - 1 && v != 0 && v != d - 1) {
						continue;
					}
					if (opening(u, v)) {
						continue;
					}
					set(u, 0, v, kerb(u, v));
					set(u, 1, v, box(u, 1, v));
					boolean corner = (u == 0 || u == w - 1) && (v == 0 || v == d - 1);
					boolean sideRing = u == 0 || u == w - 1;
					int idx = sideRing ? v : u;
					if (!corner && v != d - 1 && idx % 4 == 2 && idx > 1 && idx < (sideRing ? d - 2 : w - 2)
							&& !(v == 0 && Math.abs(u - (pu + 1)) <= 4)) { // a stone pillar in the hedge, a lantern on every other one
						set(u, 0, v, b(Blocks.POLISHED_ANDESITE));
						set(u, 1, v, b(Blocks.STONE_BRICK_WALL));
						set(u, 2, v, b(Blocks.STONE_BRICK_WALL));
						if ((idx / 4) % 2 == 0) {
							set(u, 3, v, b(Blocks.LANTERN));
						}
						continue;
					}
					boolean backWalk = v == d - 1 && u >= pu - 1 && u <= pu + 3;
					if (corner || v == d - 1) {
						set(u, 2, v, box(u, 2, v));
					}
					if (corner || backWalk) {
						set(u, 3, v, climb(u, 3, v));
					}
					if (backWalk) {
						set(u, 4, v, climb(u, 4, v));
					}
				}
			}
		}

		void paths() {
			for (int v = 0; v < d; v++) {
				for (int u = pu; u <= pu + 2; u++) {
					if (!plazaRow(v)) {
						set(u, 0, v, pathSlab(u, v, u == pu || u == pu + 2));
					}
				}
			}
			for (int vc : vcs) {
				for (int v = vc - 2; v <= vc + 2; v++) {
					for (int u = pu - 1; u <= pu + 3; u++) {
						boolean checker = ((u + v) & 1) == 0;
						set(u, 0, v, checker ? slab(Blocks.POLISHED_ANDESITE_SLAB) : slab(Blocks.STONE_BRICK_SLAB));
					}
				}
				if (cross) {
					for (int v = vc - 1; v <= vc + 1; v++) {
						for (int u = 0; u < w; u++) {
							if (u < pu - 1 || u > pu + 3) {
								set(u, 0, v, pathSlab(u, v, v != vc));
							}
						}
					}
				}
			}
		}

		/** The default edge of every walk: a stone kerb with a boxwood hedge on it. */
		void sides() {
			for (int v = 0; v < d; v++) {
				if (plazaRow(v)) {
					continue;
				}
				for (int u : new int[] {pu - 1, pu + 3}) {
					set(u, 0, v, kerb(u, v));
					set(u, 1, v, box(u, 1, v));
				}
			}
			if (cross) {
				for (int vc : vcs) {
					for (int v : new int[] {vc - 2, vc + 2}) {
						for (int u = 1; u < w - 1; u++) {
							if (u < pu - 1 || u > pu + 3) {
								set(u, 0, v, kerb(u, v));
								set(u, 1, v, box(u, 1, v));
							}
						}
					}
				}
			}
		}

		// ------------------------------------------------------------------ arches

		/** Frame positions inside {@code [lo, hi]}: one every ~5, at least 4 apart; a lone one goes at lo, mid or hi. */
		int[] frames(int lo, int hi, int mode) {
			if (hi < lo) {
				return new int[0];
			}
			int n = 1 + (int) Math.round((hi - lo) / 4.5) + (alt == 1 && hi - lo >= 9 ? 1 : 0);
			while (n > 1 && (hi - lo) / (n - 1) < 3) {
				n--;
			}
			int[] f = new int[n];
			if (n == 1) {
				f[0] = mode == 0 ? lo : mode == 2 ? hi : (lo + hi) / 2;
				return f;
			}
			for (int i = 0; i < n; i++) {
				f[i] = lo + (int) Math.round(i * (hi - lo) / (double) (n - 1));
			}
			return f;
		}

		void arms() {
			int n = vcs.length;
			if (n == 0) {
				addMain(d < 11 ? new int[] {d - 3} : frames(1, d - 3, 2));
			} else {
				addMain(frames(1, vcs[0] - 4, 0));
				for (int i = 0; i < n; i++) {
					int hi = i + 1 < n ? vcs[i + 1] - 4 : d - 3;
					addMain(frames(vcs[i] + 4, hi, i + 1 < n ? 1 : 2));
				}
			}
			if (cross) {
				for (int vc : vcs) {
					if (pu - 6 >= 5) { // short arms stay a plain walk: arches round the square would pile up
						arm(false, vc - 1, frames(2, pu - 4, 1));
						arm(false, vc - 1, frames(pu + 6, w - 3, 1));
					}
				}
			}
		}

		void addMain(int[] fr) {
			if (fr.length == 0) {
				return;
			}
			for (int f : fr) {
				mainFrames.add(f);
			}
			arm(true, pu, fr);
		}

		void at(boolean alongV, int s, int t, int y, BlockState st) {
			if (alongV) {
				set(t, y, s, st);
			} else {
				set(s, y, t, st);
			}
		}

		/** One arm of the rose tunnel: arches at {@code fr} along s, the walk's first cell across being {@code tc0}. */
		void arm(boolean alongV, int tc0, int[] fr) {
			if (fr.length == 0) {
				return;
			}
			int tl = tc0 - 1;
			int tr = tc0 + 3;
			int s0 = fr[0] - 1;
			int s1 = fr[fr.length - 1] + 1;
			Direction.Axis railAxis = alongV ? Direction.Axis.Z : Direction.Axis.X;
			BlockState rail = b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, railAxis);
			Direction faceL = alongV ? Direction.EAST : Direction.SOUTH;
			Direction faceR = alongV ? Direction.WEST : Direction.NORTH;
			for (int s = s0; s <= s1; s++) {
				int dist = 99;
				for (int f : fr) {
					dist = Math.min(dist, Math.abs(s - f));
				}
				for (int t : new int[] {tl, tr}) {
					int uu = alongV ? t : s;
					int vv = alongV ? s : t;
					if (dist == 0) {
						at(alongV, s, t, 0, p.hash(uu, 8, vv, 3) == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.POLISHED_ANDESITE));
						for (int y = 1; y <= 3; y++) {
							at(alongV, s, t, y, b(Blocks.CHERRY_FENCE)); // thin posts: the flowers behind them show
						}
					} else if (dist == 1) {
						at(alongV, s, t, 2, climb(uu, 2, vv));
						if (p.hash(uu, 3, vv, 4) != 0) {
							at(alongV, s, t, 3, climb(uu, 3, vv));
						}
					}
					if (dist <= 1) {
						at(alongV, s, t, 4, rail); // none over a window: a hedge top under it would be a sealed floor
					}
				}
				for (int t = tc0; t <= tc0 + 2; t++) {
					int uu = alongV ? t : s;
					int vv = alongV ? s : t;
					if (dist == 0) {
						at(alongV, s, t, 4, b(Blocks.CHERRY_PLANKS));
					} else if (dist == 1) {
						at(alongV, s, t, 4, climb(uu, 4, vv));
						if (p.h() > 5 && p.hash(uu, 5, vv, 3) != 0) {
							at(alongV, s, t, 5, climb(uu, 5, vv));
						}
					} else if (p.hash(uu, 4, vv, 5) < 3) {
						at(alongV, s, t, 4, climb(uu, 4, vv));
					} else {
						at(alongV, s, t, 4, slab(Blocks.CHERRY_SLAB));
						if (p.hash(uu, 11, vv, 4) == 0) {
							at(alongV, s, t, 3, Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.UP, true)); // hangs from the slab
						}
					}
					if (dist == 0 && p.hash(uu, 12, vv, 2) == 0) {
						at(alongV, s, t, 3, Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.UP, true)); // hangs from the beam
					}
				}
			}
			for (int f : fr) {
				for (int side = 0; side < 2; side++) {
					int out = side == 0 ? tl - 1 : tr + 1;
					tallAt(alongV, f, out, pal.arch); // nothing roofed above it: a roofed bed cell would count as a sealed floor
				}
			}
			for (int i = 0; i + 1 < fr.length; i++) {
				int gap = fr[i + 1] - fr[i];
				if (gap < 4) {
					continue;
				}
				int mid = (fr[i] + fr[i + 1]) / 2;
				// a bench in the window of one side (its back to the walk, the seat looking at the parterre), the lamp on the other
				boolean benchLeft = ((i + (alongV ? 0 : 1) + alt) & 1) == 0;
				boolean bench = gap >= 5;
				for (int side = 0; side < 2; side++) {
					int t = side == 0 ? tl : tr;
					if (bench && (side == 0) == benchLeft) {
						for (int s = fr[i] + 2; s <= fr[i + 1] - 2; s++) {
							at(alongV, s, t, 1, b(Blocks.AIR));
							at(alongV, s, t, 0, stairs(Blocks.CHERRY_STAIRS, side == 0 ? faceL : faceR));
						}
						continue;
					}
					at(alongV, mid, t, 1, b(Blocks.CHERRY_FENCE));
					at(alongV, mid, t, 2, b(Blocks.CHERRY_FENCE));
					at(alongV, mid, t, 3, b(Blocks.LANTERN));
				}
			}
		}

		void tallAt(boolean alongV, int s, int t, Block plant) {
			if (alongV) {
				tall(t, 1, s, plant);
			} else {
				tall(s, 1, t, plant);
			}
		}

		// ------------------------------------------------------------------ bench, bird-bath, lamps

		void bath(int u, int v) {
			set(u, 0, v, b(Blocks.POLISHED_ANDESITE));
			set(u, 1, v, b(Blocks.STONE_BRICK_WALL));
			set(u, 2, v, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		}

		/** The centre of a square, by seed: bird-bath, lantern pillar or a topiary rose on a plinth. */
		void centre(int u, int v) {
			switch (p.hash(2, 0, 2, 3)) {
				case 1 -> {
					set(u, 0, v, b(Blocks.CHISELED_STONE_BRICKS));
					set(u, 1, v, b(Blocks.STONE_BRICK_WALL));
					set(u, 2, v, b(Blocks.STONE_BRICK_WALL));
					set(u, 3, v, b(Blocks.LANTERN));
				}
				case 2 -> {
					set(u, 0, v, b(Blocks.CHISELED_STONE_BRICKS));
					set(u, 1, v, b(Blocks.STONE_BRICK_WALL));
					set(u, 2, v, climb(u, 2, v));
					set(u, 3, v, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
				default -> bath(u, v);
			}
		}

		void furniture() {
			// the niche: a bench on the last row of the walk, its back against the hedge
			for (int u = pu; u <= pu + 2; u++) {
				set(u, 0, d - 2, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
			}
			// entry lamps, outside the first arch's shoulders
			lamp(pu - 2, 0);
			lamp(pu + 4, 0);
			for (int vc : vcs) {
				centre(pu + 1, vc);
				for (int v : new int[] {vc - 2, vc + 2}) {
					lamp(pu - 1, v);
					lamp(pu + 3, v);
				}
				if (!cross) {
					for (int v = vc - 1; v <= vc + 1; v++) {
						set(pu - 1, 0, v, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
						set(pu + 3, 0, v, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
					}
				}
			}
			if (vcs.length == 0 && firstQuad != null) {
				int bu = firstQuad[0] + (firstQuad[1] - firstQuad[0]) / 2;
				int mid = (firstQuad[2] + firstQuad[3]) / 2;
				int bv = mid;
				for (int off = 0; off < d; off++) {
					int cand = mid + (off % 2 == 0 ? off / 2 : -(off + 1) / 2);
					boolean free = cand >= firstQuad[2] && cand <= firstQuad[3];
					for (int f : mainFrames) {
						if (Math.abs(cand - f) < 2) {
							free = false;
						}
					}
					if (free) {
						bv = cand;
						break;
					}
				}
				bath(bu, bv);
			}
		}
	}
}
