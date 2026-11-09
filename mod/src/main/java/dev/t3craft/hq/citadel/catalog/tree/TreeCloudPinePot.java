package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.CitadelCanvas.terracotta;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Leaves;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Wood;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A cloud pine (niwaki) in a big planter, the courtyard bonsai: the planter is a real piece of furniture, one
 * full block of wall with a half-slab rim (1.5 high: you can sit on it), corner posts a little taller, a soil
 * of moss, podzol and coarse dirt. Out of it climbs a twisted spruce trunk that steps one cell every two or
 * three layers (bark at the joints) and leans towards the side of the clouds. Two or three flat, round,
 * ragged pads of needles (two layers each, a big disc and a smaller one on top, a bite taken out of the rim)
 * sit one over the other with clear air between them, each on its own visible limb, the first pad on the
 * cloud side and the next on the opposite one, the last one on the leader. There is never anything solid
 * under y = 3 outside the planter, so you walk round it.
 *
 * <p>Seed variation: the planter's material (polished deepslate with calcite, orange terracotta, brick,
 * cherry planks with stripped posts), its shape (square, octagon with cut corners, or a long window box),
 * the side of the clouds, the trunk's steps, each pad's rim and bite, a dead stub, the lantern's corner.
 * On plots with room round the planter, 3–5 stair seats on the outer faces turn it into a bench, and from
 * 6×6 a stone lantern stands on a corner away from the clouds.
 *
 * <p>Size: 3–7 × 3–7 × 8+ (the tree stops at 14 high).
 */
public final class TreeCloudPinePot implements Variant {
	private static final Wood SPRUCE = new Wood(Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, b(Blocks.SPRUCE_WOOD));
	private static final Leaves NEEDLES = Leaves.of(Blocks.SPRUCE_LEAVES, Blocks.DARK_OAK_LEAVES).clumps(3)
			.withAccent(leaves(Blocks.AZALEA_LEAVES), 6);

	private static final int STONE = 0;
	private static final int ORANGE = 1;
	private static final int BRICK = 2;
	private static final int CHERRY = 3;

	@Override
	public String id() {
		return "tree_cloud_pine_pot";
	}

	@Override
	public Family family() {
		return Family.TREE;
	}

	@Override
	public int minW() {
		return 3;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 7;
	}

	@Override
	public int maxD() {
		return 7;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0xC10D);
		int w = p.w();
		int d = p.d();
		int small = Math.min(w, d);
		boolean three = small >= 7 && p.h() >= 13 && r.nextBoolean(); // three clouds only on the big plots
		int H = Math.min(p.h(), three ? 13 : small >= 5 ? 11 : 9);
		int sch = r.nextInt(4);
		int shape = r.nextInt(3); // 0 square, 1 octagon, 2 long window box
		boolean alongU = w > d || (w == d && r.nextBoolean()); // the axis the clouds spread along
		int sg = r.nextBoolean() ? 1 : -1; // the side of the first cloud along that axis
		int pdir = r.nextBoolean() ? 1 : -1;
		boolean seatB = r.nextBoolean();
		boolean seatL = r.nextBoolean();
		boolean seatR = r.nextBoolean();
		int offBit = r.nextInt(2);

		// ---- the planter: footprint
		int pw = w >= 7 ? 5 : w >= 5 ? w - 2 : w; // room for a seat all round on 5+ plots
		int pd = d >= 7 ? 5 : d >= 5 ? d - 2 : d;
		if (shape == 2) {
			if (alongU && d >= 5) {
				pw = w;
				pd = 3;
			} else if (!alongU && w >= 5) {
				pd = d;
				pw = 3;
			} else {
				shape = 0;
			}
		}
		if (shape == 1 && (pw < 5 || pd < 5)) {
			shape = 0;
		}
		int pu0 = (w - pw) / 2 + ((w - pw) % 2 == 1 ? offBit : 0);
		int pv0 = (d - pd) / 2 + ((d - pd) % 2 == 1 ? offBit : 0);
		int pu1 = pu0 + pw - 1;
		int pv1 = pv0 + pd - 1;
		int lo = pu0 + 1; // the soil
		int uHi = pu1 - 1;
		int vLo = pv0 + 1;
		int vHi = pv1 - 1;
		planter(t, sch, shape == 1, pu0, pu1, pv0, pv1);

		// ---- seats on the outer faces, a lantern on a corner (only where the apron has room)
		int apF = pv0;
		int apB = d - 1 - pv1;
		int apL = pu0;
		int apR = w - 1 - pu1;
		if (apF >= 1) {
			seatRow(t, sch, pu0, pu1, pv0 - 1, true, Direction.NORTH);
		}
		if (apB >= 1 && seatB) {
			seatRow(t, sch, pu0, pu1, pv1 + 1, true, Direction.SOUTH);
		}
		if (apL >= 1 && seatL) {
			seatRow(t, sch, pv0, pv1, pu0 - 1, false, Direction.WEST);
		}
		if (apR >= 1 && seatR) {
			seatRow(t, sch, pv0, pv1, pu1 + 1, false, Direction.EAST);
		}
		if (w >= 6 && d >= 6) {
			int lu;
			int lv;
			int perp = r.nextBoolean() ? 1 : 0;
			if (alongU) {
				lu = sg > 0 ? 0 : w - 1;
				lv = perp == 1 ? 0 : d - 1;
			} else {
				lv = sg > 0 ? 0 : d - 1;
				lu = perp == 1 ? 0 : w - 1;
			}
			boolean inPot = lu >= pu0 && lu <= pu1 && lv >= pv0 && lv <= pv1;
			if (!inPot) {
				lantern(t, sch, lu, lv);
			}
		}

		// ---- the trunk: a twisted 1×1 that steps every 2–3 layers, leaning to the cloud side
		int lenA = alongU ? w : d;
		int lenP = alongU ? d : w;
		int aLo = alongU ? lo : vLo;
		int aHi = alongU ? uHi : vHi;
		int pLo = alongU ? vLo : lo;
		int pHi = alongU ? vHi : uHi;
		int innerA = aHi - aLo + 1;
		int a0 = (aLo + aHi + (sg > 0 ? 0 : 1)) / 2;
		if (innerA >= 5) {
			a0 -= sg;
		}
		int p0 = (pLo + pHi + (r.nextBoolean() ? 0 : 1)) / 2;
		int cu = alongU ? a0 : p0;
		int cv = alongU ? p0 : a0;
		int au = cu;
		int av = cv;
		int top = H - 2; // the top pad: layers top and top + 1
		int trunkTop = top - 1;
		int[] tu = new int[H + 2];
		int[] tv = new int[H + 2];
		tu[0] = cu;
		tv[0] = cv;
		tu[1] = cu;
		tv[1] = cv;
		t.wood(cu, 1, cv, SPRUCE.bark());
		// the first step is above head height from the planter's rim and its corner posts (small plots: lower)
		int nextMove = 3 + r.nextInt(2);
		int mi = 0;
		for (int y = 2; y <= trunkTop; y++) {
			if (y == nextMove) {
				int step = mi % 4;
				int dA = step == 0 || step == 1 || step == 3 ? sg : 0;
				int dP = step == 2 ? pdir : 0;
				int du = alongU ? dA : dP;
				int dv = alongU ? dP : dA;
				int nu = cu + du;
				int nv = cv + dv;
				// below head height from the rim (y < 6) the trunk may only step inside the soil
				boolean low = small >= 5 && y < 6;
				int uMin = low ? lo : 1;
				int uMax = low ? uHi : w - 2;
				int vMin = low ? vLo : 1;
				int vMax = low ? vHi : d - 2;
				if (nu >= uMin && nu <= uMax && nv >= vMin && nv <= vMax) {
					t.wood(cu, y - 1, cv, SPRUCE.bark());
					t.wood(nu, y - 1, nv, SPRUCE.bark());
					cu = nu;
					cv = nv;
					mi++;
					nextMove = y + 2;
				} else if (low) {
					nextMove = y + 1; // retry higher up
				} else {
					mi++;
					nextMove = y + 2 + r.nextInt(2);
				}
			}
			tu[y] = cu;
			tv[y] = cv;
			t.wood(cu, y, cv, y == trunkTop ? SPRUCE.bark() : SPRUCE.log(Direction.Axis.Y));
		}
		for (int y = trunkTop + 1; y <= H + 1; y++) {
			tu[y] = cu;
			tv[y] = cv;
		}
		// surface roots over the soil
		int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] o : nb) {
			int ru = au + o[0];
			int rv = av + o[1];
			if (ru >= lo && ru <= uHi && rv >= vLo && rv <= vHi && t.hash(ru, 1, rv, 3) == 0) {
				t.woodSoft(ru, 1, rv, SPRUCE.log(o[0] != 0 ? Direction.Axis.X : Direction.Axis.Z));
			}
		}

		// ---- the pads: heights and sizes
		int n = three ? 3 : 2;
		int[] py = new int[n];
		py[n - 1] = top;
		// one clear layer between the clouds, and the lowest one above the planter's rim (a seat) with room to walk
		if (n == 3) {
			py[0] = 5;
			py[1] = 8;
		} else {
			py[0] = small >= 5 ? top - 4 : top - 3;
		}
		double base = small >= 7 ? 2.9 : small == 6 ? 2.5 : small == 5 ? 2.1 : small == 4 ? 1.8 : 1.5;
		double[] pr = new double[n];
		for (int k = 0; k < n; k++) {
			pr[k] = base * (k == n - 1 ? 0.88 : k == 0 ? 1.0 : 0.94);
		}

		// side pads: alternate sides along the cloud axis, each on a visible limb from the trunk
		for (int k = 0; k < n - 1; k++) {
			int side = k % 2 == 0 ? sg : -sg;
			// the limb runs level under the pad (small plots: it may start lower, nobody walks there)
			int ys = Math.max(3, py[k] - 3);
			double ra = Math.min(pr[k] * 1.25, (lenA - 1) / 2.0 + 0.4);
			double rp = Math.min(pr[k] * 0.95, (lenP - 1) / 2.0 + 0.4);
			double off = Math.max(2.0, Math.round(ra * 1.35));
			double tA = alongU ? tu[ys] : tv[ys];
			double tP = alongU ? tv[ys] : tu[ys];
			double ca = TreeKit.clamp(tA + side * off, ra - 0.4, lenA - 1 - (ra - 0.4));
			double cp = TreeKit.clamp(tP + (k % 2 == 0 ? pdir : -pdir) * r.nextInt(2), rp - 0.4, lenP - 1 - (rp - 0.4));
			double cuD = alongU ? ca : cp;
			double cvD = alongU ? cp : ca;
			int ci = (int) Math.round(cuD);
			int cj = (int) Math.round(cvD);
			TreeKit.branch(t, SPRUCE, new int[] {tu[ys], ys, tv[ys]}, new int[] {ci, py[k] - 1, cj}, false);
			double rx = alongU ? ra : rp;
			double rz = alongU ? rp : ra;
			cloud(t, cuD, cvD, py[k], rx, rz, k + 1, r.nextDouble() * 6.28, r.nextDouble() * 6.28);
		}

		// a dead stub (a pruned limb) on the side away from the first pad, low on the trunk
		if (small >= 5) {
			int ys = py[0];
			int su = alongU ? -sg : 0;
			int sv = alongU ? 0 : -sg;
			int s1u = tu[ys] + su;
			int s1v = tv[ys] + sv;
			int s2u = tu[ys] + 2 * su;
			int s2v = tv[ys] + 2 * sv;
			if (s2u >= 0 && s2u < w && s2v >= 0 && s2v < d && t.empty(s1u, ys, s1v) && t.empty(s2u, ys, s2v)) {
				t.woodSoft(s1u, ys, s1v, SPRUCE.log(alongU ? Direction.Axis.X : Direction.Axis.Z));
				t.woodSoft(s2u, ys, s2v, SPRUCE.bark());
				t.woodSoft(s2u, ys + 1, s2v, SPRUCE.bark());
			}
		}

		// the top pad on the leader, a little off centre when it is big enough
		{
			double rc = pr[n - 1];
			double ra = Math.min(rc * 1.1, (lenA - 1) / 2.0 + 0.4);
			double rp = Math.min(rc * 0.9, (lenP - 1) / 2.0 + 0.4);
			double tA = alongU ? tu[trunkTop] : tv[trunkTop];
			double tP = alongU ? tv[trunkTop] : tu[trunkTop];
			double shift = rc >= 2.4 ? -2 * (n % 2 == 0 ? sg : -sg) : rc >= 1.7 ? -(n % 2 == 0 ? sg : -sg) : 0;
			double ca = TreeKit.clamp(tA + shift, ra - 0.4, lenA - 1 - (ra - 0.4));
			double cp = TreeKit.clamp(tP, rp - 0.4, lenP - 1 - (rp - 0.4));
			double cuD = alongU ? ca : cp;
			double cvD = alongU ? cp : ca;
			double rx = alongU ? ra : rp;
			double rz = alongU ? rp : ra;
			cloud(t, cuD, cvD, top, rx, rz, n, r.nextDouble() * 6.28, r.nextDouble() * 6.28);
		}

		// ---- the soil: moss, podzol, coarse dirt; moss carpet and ferns (shrubs where it is too small to walk)
		int free = (uHi - lo + 1) * (vHi - vLo + 1) - 1;
		for (int u = lo; u <= uHi; u++) {
			for (int v = vLo; v <= vHi; v++) {
				int k = t.hash(u, 0, v, 7);
				t.fixed(u, 0, v, k <= 2 ? b(Blocks.MOSS_BLOCK) : k <= 4 ? b(Blocks.PODZOL) : k == 5 ? b(Blocks.COARSE_DIRT) : b(Blocks.MOSS_BLOCK));
			}
		}
		for (int u = lo; u <= uHi; u++) {
			for (int v = vLo; v <= vHi; v++) {
				if (!t.empty(u, 1, v)) {
					continue;
				}
				int hsh = t.hash(u, 2, v, 6);
				if (free <= 4) {
					t.fixed(u, 1, v, hsh <= 2 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.MOSSY_COBBLESTONE));
				} else if (hsh <= 2) {
					t.cover(u, 1, v, b(Blocks.MOSS_CARPET));
				} else if (hsh <= 4) {
					t.cover(u, 1, v, b(Blocks.FERN));
				} else if (hsh == 5) {
					t.fixed(u, 1, v, b(Blocks.FLOWERING_AZALEA));
				}
			}
		}
		t.prune();
		t.flush(c);
	}

	// ------------------------------------------------------------------ the planter

	/** The planter's wall (y = 0): a full block, mixed by material. */
	private static BlockState wall(int sch, Vox t, int u, int v, boolean alongRow) {
		int k = t.hash(u, 0, v, 7);
		int idx = alongRow ? u : v;
		switch (sch) {
			case STONE:
				return k == 0 ? b(Blocks.DEEPSLATE_BRICKS) : k == 1 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.POLISHED_DEEPSLATE);
			case ORANGE:
				return k == 0 ? b(Blocks.TERRACOTTA) : k == 1 ? terracotta(DyeColor.RED) : terracotta(DyeColor.ORANGE);
			case BRICK:
				return k == 0 ? b(Blocks.TERRACOTTA) : b(Blocks.BRICKS);
			default:
				return Math.floorMod(idx, 3) == 1
						? log(Blocks.STRIPPED_CHERRY_LOG, alongRow ? Direction.Axis.X : Direction.Axis.Z)
						: b(Blocks.CHERRY_PLANKS);
		}
	}

	private static BlockState log(Block b, Direction.Axis axis) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	private static BlockState rim(int sch) {
		return switch (sch) {
			case STONE -> slab(Blocks.SMOOTH_STONE_SLAB);
			case ORANGE -> slab(Blocks.CUT_RED_SANDSTONE_SLAB);
			case BRICK -> slab(Blocks.MUD_BRICK_SLAB);
			default -> slab(Blocks.CHERRY_SLAB);
		};
	}

	private static BlockState post(int sch, int y) {
		return switch (sch) {
			case STONE -> y == 0 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.CALCITE);
			case ORANGE -> y == 0 ? b(Blocks.TERRACOTTA) : b(Blocks.CUT_RED_SANDSTONE);
			case BRICK -> y == 0 ? b(Blocks.BRICKS) : b(Blocks.MUD_BRICKS);
			default -> b(Blocks.STRIPPED_CHERRY_WOOD);
		};
	}

	private static BlockState seat(int sch, Direction out) {
		Block s = switch (sch) {
			case STONE -> Blocks.POLISHED_ANDESITE_STAIRS;
			case ORANGE -> Blocks.RED_SANDSTONE_STAIRS;
			case BRICK -> Blocks.BRICK_STAIRS;
			default -> Blocks.CHERRY_STAIRS;
		};
		return stairs(s, out);
	}

	/**
	 * The ring: wall at y = 0 and a half-slab rim at y = 1, corner posts two blocks high. With {@code cut} the
	 * four corner cells are left out (an octagon). The soil is filled by the caller.
	 */
	private static void planter(Vox t, int sch, boolean cut, int u0, int u1, int v0, int v1) {
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				boolean eu = u == u0 || u == u1;
				boolean ev = v == v0 || v == v1;
				if (!eu && !ev) {
					continue;
				}
				boolean corner = eu && ev;
				if (corner && cut) {
					continue;
				}
				if (corner) {
					t.fixed(u, 0, v, post(sch, 0));
					t.fixed(u, 1, v, post(sch, 1));
				} else {
					t.fixed(u, 0, v, wall(sch, t, u, v, ev));
					t.fixed(u, 1, v, rim(sch));
				}
			}
		}
	}

	/** A row of stair seats against a face of the planter, facing outwards (the back is the planter). */
	private static void seatRow(Vox t, int sch, int a0, int a1, int fixedAt, boolean alongU, Direction out) {
		int len = a1 - a0 + 1;
		int sl = len >= 7 ? 5 : Math.min(3, len);
		int start = a0 + (len - sl) / 2;
		for (int i = 0; i < sl; i++) {
			int a = start + i;
			if (alongU) {
				t.fixed(a, 0, fixedAt, seat(sch, out));
			} else {
				t.fixed(fixedAt, 0, a, seat(sch, out));
			}
		}
	}

	/** A stone lantern: base, post, lantern, slab roof. */
	private static void lantern(Vox t, int sch, int u, int v) {
		boolean cherry = sch == CHERRY;
		t.fixed(u, 0, v, cherry ? b(Blocks.STRIPPED_CHERRY_WOOD) : b(Blocks.STONE_BRICKS));
		t.fixed(u, 1, v, cherry ? b(Blocks.CHERRY_FENCE) : b(Blocks.STONE_BRICK_WALL));
		t.fixed(u, 2, v, b(Blocks.LANTERN));
		t.fixed(u, 3, v, cherry ? slab(Blocks.CHERRY_SLAB) : slab(Blocks.STONE_BRICK_SLAB));
	}

	// ------------------------------------------------------------------ the clouds

	/**
	 * One cloud: a flat round disc on {@code y0} and a smaller one on top (0.6 of it), the rim scalloped by two
	 * sine waves and a per-cell jitter, with a wedge bitten out of it ({@code hole}, in radians) so it is not a
	 * plate. The underside is flat: nothing hangs.
	 */
	private static void cloud(Vox t, double cu, double cv, int y0, double rx, double rz, int id, double ph, double hole) {
		for (int ly = 0; ly < 2; ly++) {
			int y = y0 + ly;
			if (y >= t.h) {
				break;
			}
			double sc = ly == 0 ? 1.0 : 0.5;
			for (int v = (int) Math.floor(cv - rz - 1); v <= (int) Math.ceil(cv + rz + 1); v++) {
				for (int u = (int) Math.floor(cu - rx - 1); u <= (int) Math.ceil(cu + rx + 1); u++) {
					double du = (u - cu) / rx;
					double dv = (v - cv) / rz;
					double q = Math.hypot(du, dv);
					double a = Math.atan2(dv, du);
					double m = 1 + 0.13 * Math.sin(3 * a + ph) + 0.07 * Math.sin(5 * a + 2 * ph);
					double lim = sc * m + (t.hash(u, y, v, 9) - 4) / 4.0 * 0.1;
					if (q > lim) {
						continue;
					}
					double da = Math.abs(Math.atan2(Math.sin(a - hole), Math.cos(a - hole)));
					if (da < 0.3 && q > 0.5 * sc) {
						continue; // the bite
					}
					if (ly == 1 && q > lim - 0.25 && t.hash(u, y + 3, v, 3) == 0) {
						continue; // the upper rim comes and goes
					}
					t.leafOf(u, y, v, TreeKit.tone(t, NEEDLES, u, y, v, ly == 0 ? 0.0 : 1.0, q, 20 + id), id);
				}
			}
		}
	}
}
