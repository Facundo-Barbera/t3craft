package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Leaves;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Wood;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Weeping willow beside the water: an oval pool at the front (a mossy stone rim with rounded corners,
 * water, lily pads, submerged stepping stones and a stone lantern on the free corner). Right behind the
 * rim a dark-oak 2×2 trunk on diagonal roots thins to a leader that leans out over the pond; three or four
 * limbs arch out from it to flattish rounded lobes that, with the crown lobe on the leader, make a broad,
 * low, lumpy dome in two greens. From about half the rim of that dome hangs an irregular curtain: stubs,
 * mid strands and a few that nearly reach the water, some stepping outwards, leaves above and vines at the
 * tips. Ferns, moss, a flowering azalea bush and a mossy boulder behind.
 *
 * <p>Size: 7–9 × 7–9 × 9+ (the tree stops at 12 high).
 */
public final class TreeWillowPond implements Variant {
	private static final Wood WOOD = new Wood(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD, b(Blocks.MANGROVE_ROOTS));
	private static final Leaves GREEN = Leaves.of(Blocks.OAK_LEAVES, Blocks.AZALEA_LEAVES).clumps(3);
	private static final Leaves DEEP = Leaves.of(Blocks.AZALEA_LEAVES, Blocks.DARK_OAK_LEAVES).clumps(3);
	private static final Leaves CROWN = GREEN.withAccent(leaves(Blocks.BIRCH_LEAVES), 7);

	@Override
	public String id() {
		return "tree_willow_pond";
	}

	@Override
	public Family family() {
		return Family.TREE;
	}

	@Override
	public int minW() {
		return 7;
	}

	@Override
	public int minD() {
		return 7;
	}

	@Override
	public int minH() {
		return 9;
	}

	@Override
	public int maxW() {
		return 9;
	}

	@Override
	public int maxD() {
		return 9;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0x5A11);
		int w = p.w();
		int d = p.d();
		int H = Math.min(p.h(), 12);
		double sc = Math.min(w, d) / 7.0;
		int pd = d >= 9 ? 4 : 3; // the pool covers v 0..pd
		int bu = (w - 2) / 2;
		int bv = pd + 1; // the 2×2 stands right behind the back rim

		// the fixed pieces first: the tree never overwrites them
		pond(t, w, pd, bu);
		garden(t, w, d);

		// trunk: 2×2 on roots (mostly to the back and sides), leaning out over the water
		int yTop = H - 3;
		int[][] lead = TreeKit.trunk(t, WOOD, bu, bv, 2, yTop, r.nextInt(2), 0, r.nextInt(3) - 1, -(pd - 1));
		TreeKit.roots(t, WOOD, bu, bv, 6, 2, 4, 21);

		// the crown lobe on the leader, over the pond
		int lu = lead[0][yTop];
		int lv = lead[1][yTop];
		TreeKit.lobe(t, lu, yTop, lv, 2.2 * sc, 1.9 * sc, 1.6, 1.1, CROWN, 0.4, 0);

		// limbs arching out to the sides and the back, each to a flattish lobe at its own height
		double[] dirs = w >= 8 || d >= 8
				? new double[] {Math.PI + 0.35, -0.35, Math.PI / 2 + 0.75, Math.PI / 2 - 0.75}
				: new double[] {Math.PI + 0.3, -0.3, Math.PI / 2 + (r.nextBoolean() ? 0.45 : -0.45)};
		int[] drop = {1, 2, 1, 3};
		int shift = r.nextInt(4);
		for (int i = 0; i < dirs.length; i++) {
			double ang = dirs[i] + (r.nextDouble() - 0.5) * 0.4;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			int ty = Math.max(4, yTop - drop[(i + shift) % 4]);
			int sy = TreeKit.clamp(ty - 2 - (i & 1), 3, yTop - 1);
			int su = lead[0][sy];
			int sv = lead[1][sy];
			double rx = (1.7 + r.nextDouble() * 0.6) * sc;
			double rz = rx * (0.8 + r.nextDouble() * 0.2);
			double reach = TreeKit.clamp(TreeKit.room(t, su, sv, ex, ez, 0) - Math.max(rx, rz) * 0.7, 1.2, 3.4 * sc);
			int tu = (int) Math.round(su + ex * reach);
			int tv = (int) Math.round(sv + ez * reach);
			TreeKit.branch(t, WOOD, new int[] {su, sy, sv}, new int[] {tu, ty, tv}, i < 2);
			TreeKit.lobe(t, tu, ty, tv, rx, rz, 1.5, 1.1, i % 2 == 0 ? GREEN : DEEP, 0.45, i + 1);
		}

		// the curtain: from the rim of the dome (air below, away from the trunk)
		double mu = 0;
		double mv = 0;
		int nl = 0;
		for (int y = 0; y < t.h; y++) {
			for (int v = 0; v < t.d; v++) {
				for (int u = 0; u < t.w; u++) {
					if (t.kind(u, y, v) == TreeKit.LEAF) {
						mu += u;
						mv += v;
						nl++;
					}
				}
			}
		}
		if (nl > 0) {
			mu /= nl;
			mv /= nl;
		}
		double tcu = bu + 0.5;
		double tcv = bv + 0.5;
		List<int[]> seeds = new ArrayList<>();
		for (int y = 2; y < t.h; y++) {
			for (int v = 0; v < t.d; v++) {
				for (int u = 0; u < t.w; u++) {
					if (t.kind(u, y, v) != TreeKit.LEAF || !t.empty(u, y - 1, v)) {
						continue;
					}
					if (Math.hypot(u - lead[0][y], v - lead[1][y]) < 1.5 || Math.hypot(u - tcu, v - tcv) < 1.8) {
						continue;
					}
					double ou = u - mu;
					double ov = v - mv;
					int su = Math.abs(ou) >= Math.abs(ov) ? (int) Math.signum(ou) : 0;
					int sv = Math.abs(ov) > Math.abs(ou) ? (int) Math.signum(ov) : 0;
					boolean rim = (su != 0 || sv != 0) && t.kind(u + su, y, v + sv) != TreeKit.LEAF;
					seeds.add(new int[] {u, y, v, rim ? 1 : 0});
				}
			}
		}
		boolean[] longCol = new boolean[t.w * t.d]; // long strands never side by side: a fringe, not a wall
		for (int[] s : seeds) {
			int u = s[0];
			int y = s[1];
			int v = s[2];
			boolean rim = s[3] == 1;
			int pick = t.hash(u, y, v, 20);
			if (rim ? pick >= 11 : pick >= 3) {
				continue; // gaps: about half the rim hangs, a few inner strands
			}
			int room = y - 1; // the lowest strand cell is y = 1, just above the water
			if (room < 1) {
				continue;
			}
			int len;
			if (!rim || pick < 4) {
				len = 1 + t.hash(u, 1, v, 2); // stubs
			} else if (pick < 8) {
				len = 2 + t.hash(u, 2, v, 3); // mid
			} else {
				len = room - t.hash(u, 3, v, 2); // nearly to the water
			}
			if (v == 0) {
				len = Math.min(len, 3); // the front row stays short: the pool shows
			}
			len = TreeKit.clamp(len, 1, room);
			if (len >= 3) {
				boolean near = false;
				for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 0}}) {
					int a2 = u + o[0];
					int b2 = v + o[1];
					near |= a2 >= 0 && a2 < t.w && b2 >= 0 && b2 < t.d && longCol[b2 * t.w + a2];
				}
				if (near) {
					len = Math.min(len, 1 + t.hash(u, 8, v, 2));
				} else {
					longCol[v * t.w + u] = true;
				}
			}
			int ku = Integer.signum((int) Math.round(u - mu));
			int kv = Integer.signum((int) Math.round(v - mv));
			boolean kink = len >= 3 && t.hash(u, 4, v, 3) == 0;
			if (kink && ku != 0 && kv != 0) {
				if (t.hash(u, 5, v, 2) == 0) {
					ku = 0;
				} else {
					kv = 0;
				}
			}
			int vines = len >= 3 ? 1 + t.hash(u, 6, v, 2) : 0;
			boolean ew = Math.abs(u - mu) > Math.abs(v - mv);
			boolean deep = t.hash(u, 7, v, 3) == 0;
			BlockState body = deep ? DEEP.a() : GREEN.a();
			BlockState tip = deep ? GREEN.a() : DEEP.a();
			strand(t, u, y - 1, v, len, vines, body, tip, ku, kv, kink, ew);
		}

		// ferns, grass, moss and a few flowering azaleas on the free ground
		TreeKit.ground(t, (w - 1) / 2.0, (d - 1) / 2.0, Math.max(w, d), (u, v, k) -> {
			int hsh = t.hash(u, 0, v, 100);
			if (hsh < 16) {
				return b(Blocks.FERN);
			}
			if (hsh < 28) {
				return b(Blocks.SHORT_GRASS);
			}
			if (hsh < 38) {
				return b(Blocks.MOSS_CARPET);
			}
			if (hsh < 41 && v > pd) {
				return b(Blocks.FLOWERING_AZALEA);
			}
			return null;
		});
		t.prune();
		t.flush(c);
	}

	/**
	 * A hanging strand from {@code (u, yTop, v)} down {@code len} cells (never below y = 1): leaves, with the
	 * last {@code vines} cells as vines (or the {@code tip} leaf when there are none), stepping one cell
	 * outwards a third of the way down with {@code kink}.
	 */
	private static void strand(Vox t, int u, int yTop, int v, int len, int vines, BlockState body, BlockState tip,
			int ku, int kv, boolean kink, boolean ew) {
		int cu = u;
		int cv = v;
		int kAt = Math.max(1, len / 3);
		for (int k = 0; k < len; k++) {
			int y = yTop - k;
			if (y < 1) {
				break;
			}
			if (kink && k == kAt && (ku != 0 || kv != 0)) {
				t.leaf(cu + ku, y + 1, cv + kv, body); // the step, face-connected
				cu += ku;
				cv += kv;
			}
			if (!t.empty(cu, y, cv) && t.kind(cu, y, cv) != TreeKit.LEAF) {
				break;
			}
			boolean vine = k >= len - vines;
			BlockState s = vine ? vine(ew, k == len - vines) : k == len - 1 ? tip : body;
			t.leaf(cu, y, cv, s);
		}
	}

	/** A vine hanging in the middle of its cell: two opposite faces, and the top one also clings upwards. */
	private static BlockState vine(boolean ew, boolean top) {
		BlockState s = b(Blocks.VINE)
				.setValue(ew ? BlockStateProperties.EAST : BlockStateProperties.NORTH, true)
				.setValue(ew ? BlockStateProperties.WEST : BlockStateProperties.SOUTH, true);
		return top ? s.setValue(BlockStateProperties.UP, true) : s;
	}

	/**
	 * The pool: an oval of water inside the box v 0..pd, ringed (4-adjacent, so the water is sealed) by a
	 * mixed mossy rim; the box corners outside the ring stay ground, which rounds the outline. Submerged
	 * stepping stones zig-zag from the front to the trunk, lily pads float, and a stone lantern stands on the
	 * front right corner.
	 */
	private static void pond(Vox t, int w, int pd, int bu) {
		double cu = (w - 1) / 2.0;
		double au = cu - 0.5;
		double cv = pd / 2.0;
		double av = cv - 0.3;
		boolean[][] wet = new boolean[w][pd + 1];
		for (int u = 1; u <= w - 2; u++) {
			for (int v = 1; v <= pd - 1; v++) {
				double du = (u - cu) / au;
				double dv = (v - cv) / av;
				wet[u][v] = du * du + dv * dv <= 1.0;
			}
		}
		int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int u = 0; u < w; u++) {
			for (int v = 0; v <= pd; v++) {
				if (wet[u][v]) {
					boolean stone = u == bu - 1 + (v & 1) && v >= 1;
					if (stone) {
						t.fixed(u, 0, v, slab(Blocks.MOSSY_COBBLESTONE_SLAB).setValue(BlockStateProperties.WATERLOGGED, true));
					} else {
						t.fixed(u, 0, v, b(Blocks.WATER));
						if (t.hash(u, 5, v, 3) == 0) {
							t.fixed(u, 1, v, b(Blocks.LILY_PAD));
						}
					}
					continue;
				}
				boolean rim = false;
				for (int[] n : n4) {
					int nu = u + n[0];
					int nv = v + n[1];
					if (nu >= 0 && nu < w && nv >= 0 && nv <= pd && wet[nu][nv]) {
						rim = true;
					}
				}
				if (rim) {
					rim(t, u, v);
				}
			}
		}
		// the stone lantern on the front right corner (outside the ring)
		t.fixed(w - 1, 0, 0, b(Blocks.STONE_BRICKS));
		t.fixed(w - 1, 1, 0, b(Blocks.STONE_BRICK_WALL));
		t.fixed(w - 1, 2, 0, b(Blocks.LANTERN));
		t.fixed(w - 1, 3, 0, slab(Blocks.STONE_BRICK_SLAB));
		// a pebble on the front left corner
		t.fixed(0, 0, 0, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
	}

	private static void rim(Vox t, int u, int v) {
		int k = t.hash(u, 0, v, 8);
		BlockState s = switch (k) {
			case 0, 1 -> b(Blocks.MOSSY_STONE_BRICKS);
			case 2 -> b(Blocks.CRACKED_STONE_BRICKS);
			case 3 -> b(Blocks.MOSSY_COBBLESTONE);
			case 4 -> b(Blocks.COBBLESTONE);
			default -> b(Blocks.STONE_BRICKS);
		};
		t.fixed(u, 0, v, s);
		if (k <= 1 || k == 3) {
			int g = t.hash(u, 1, v, 4);
			if (g == 0) {
				t.cover(u, 1, v, b(Blocks.FERN));
			} else if (g == 1) {
				t.cover(u, 1, v, b(Blocks.MOSS_CARPET));
			}
		}
	}

	/** Behind the pool: a flowering azalea bush in one back corner and a mossy boulder in the other. */
	private static void garden(Vox t, int w, int d) {
		boolean flip = t.hash(1, 2, 3, 2) == 0;
		int bushU = flip ? w - 1 : 0;
		int rockU = flip ? 0 : w - 1;
		int in = flip ? -1 : 1; // from the bush corner towards the middle
		t.fixed(bushU, 0, d - 1, leaves(Blocks.AZALEA_LEAVES));
		t.fixed(bushU, 1, d - 1, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		t.fixed(bushU, 0, d - 2, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		t.fixed(bushU + in, 0, d - 1, leaves(Blocks.AZALEA_LEAVES));
		t.fixed(rockU, 0, d - 1, b(Blocks.MOSSY_COBBLESTONE));
		t.fixed(rockU, 1, d - 1, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
		t.fixed(rockU - in, 0, d - 1, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
		t.fixed(rockU, 0, d - 2, slab(Blocks.COBBLESTONE_SLAB));
	}
}
