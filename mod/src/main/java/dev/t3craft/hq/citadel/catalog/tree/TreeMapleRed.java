package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Wood;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Japanese red maple (momiji) in autumn: a slim, gently S-bent dark trunk on a short flared foot, clear of branches
 * up to y 4 (so you walk under it), with its crown in horizontal <em>floors</em>. Floors sit two layers apart (y 4,
 * 6, 8 and, on some mid-size plots, 10), so there is always one layer of air between one floor and the next and the
 * slim trunk shows through it. Each floor holds a few flat, scalloped fans of leaves, <b>one block thick</b> (no cap,
 * no drips), held up by a single thin limb each; the fans of a floor sit over the gaps of the floor below (found by
 * a widest-gap search), the lowest ones are the widest and the crown over the leader is the smallest but brightest.
 * Big plots get more fans per floor, not more floors, so the tree grows wider rather than taller.
 *
 * <p>Colour is a fine mix of three untinted blocks (nether wart, red wool, a little orange wool and rare honeycomb
 * flecks on sunny tops): deep red and red nearly everywhere, orange only as an accent on the sun side of the upper
 * fans. A carpet of fallen leaves (leaf litter, red and orange carpet) lies below.
 *
 * <p>The maple uses untinted blocks for its foliage: every vanilla leaf that could be red is biome-tinted green
 * in the renders and in plains. Everything is drawn into a {@link Vox}, which also removes any leaf that would
 * float.
 *
 * <p>Size: 5–11 × 5–11 × 9+ (three floors reach y 8; a fourth floor, y 10, needs a plot 11 high).
 */
public final class TreeMapleRed implements Variant {
	private static final Wood WOOD = new Wood(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD, b(Blocks.MANGROVE_ROOTS));
	/** Deep red → orange; the honeycomb is a rare fleck on sunlit tops. */
	private static final BlockState[] RAMP = {
			b(Blocks.NETHER_WART_BLOCK),
			b(Blocks.WOOL.pick(DyeColor.RED)),
			b(Blocks.WOOL.pick(DyeColor.ORANGE)),
	};
	private static final BlockState HONEY = b(Blocks.HONEYCOMB_BLOCK);
	/** The lowest floor and the lowest wood of any limb: 3 free blocks over the foot (walkable underneath). */
	private static final int FLOOR0 = 4;

	@Override
	public String id() {
		return "tree_maple_red";
	}

	@Override
	public Family family() {
		return Family.TREE;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 5;
	}

	@Override
	public int minH() {
		return 9;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 11;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Vox t = new Vox(p);
		Random r = p.rng(0x3A91);
		int w = p.w();
		int d = p.d();
		int m = Math.min(w, d);
		// floors two layers apart: one layer of air between them
		int nF = p.h() >= 11 && m >= 7 && m <= 9 && r.nextInt(3) == 0 ? 4 : 3;
		int[] fy = new int[nF];
		for (int i = 0; i < nF; i++) {
			fy[i] = FLOOR0 + 2 * i;
		}
		int topY = fy[nF - 1];
		int ou = r.nextInt(2);
		int ov = r.nextInt(2);
		int bu = TreeKit.base(w, ou);
		int bv = TreeKit.base(d, ov);
		int cu0 = bu + ou;
		int cv0 = bv + ov;
		double bias = (r.nextInt(5) - 2) * 0.03; // some trees lean redder, some a little more orange
		double sun = r.nextDouble() * Math.PI * 2;
		double a0 = r.nextDouble() * Math.PI * 2;

		// ---- foot: a 2×2 flare of bark, short roots
		for (int a = 0; a < 2; a++) {
			for (int e = 0; e < 2; e++) {
				t.wood(bu + a, 0, bv + e, WOOD.bark());
			}
		}
		TreeKit.roots(t, WOOD, bu, bv, 2 + r.nextInt(2), 1, -1, 5);

		// ---- the slim trunk: straight to y 3, then a gentle S under the crown (one log per layer)
		double amp = m >= 8 ? 1.4 : 1.0;
		double phi = r.nextDouble() * Math.PI * 2;
		int[] tu = new int[topY + 1];
		int[] tv = new int[topY + 1];
		int cu = cu0;
		int cv = cv0;
		tu[0] = cu;
		tv[0] = cv;
		tu[1] = cu;
		tv[1] = cv;
		t.wood(cu, 1, cv, WOOD.log(Direction.Axis.Y));
		int span = Math.max(1, topY - 3);
		for (int y = 2; y <= topY - 1; y++) {
			int goalU = cu0;
			int goalV = cv0;
			if (y >= FLOOR0) {
				double s = Math.sin((y - 3.0) / span * Math.PI * 2);
				goalU = TreeKit.clamp(cu0 + (int) Math.round(amp * Math.cos(phi) * s), 1, w - 2);
				goalV = TreeKit.clamp(cv0 + (int) Math.round(amp * Math.sin(phi) * s), 1, d - 2);
			}
			int du = Integer.signum(goalU - cu);
			int dv = Integer.signum(goalV - cv);
			if (du != 0 && dv != 0) {
				if ((y & 1) == 0) {
					dv = 0;
				} else {
					du = 0;
				}
			}
			if (du != 0 || dv != 0) {
				// the step: bark on the layer below joins the old and the new column
				t.wood(cu + du, y - 1, cv + dv, WOOD.bark());
			}
			cu += du;
			cv += dv;
			tu[y] = cu;
			tv[y] = cv;
			t.wood(cu, y, cv, WOOD.log(Direction.Axis.Y));
		}
		tu[topY] = cu;
		tv[topY] = cv;

		// ---- the floors: wide low fans, fewer and smaller going up, each floor over the gaps of the ones below
		double rbase = TreeKit.clamp(m * 0.34, 1.5, 4.0);
		int nLow = m <= 5 ? 1 : m <= 8 ? 3 : 4;
		int nMid = m <= 5 ? 1 : m <= 8 ? 2 : 3;
		List<Double> used = new ArrayList<>();
		int id = 1;
		for (int i = 0; i < nF - 1; i++) {
			int n = i == 0 ? nLow : i == 1 ? nMid : 2;
			double step = Math.PI * 2 / n;
			double rf = rbase * (1.0 - 0.1 * i);
			double reach = i == 0 ? 0.82 : i == 1 ? 0.55 : 0.45;
			for (int k = 0; k < n; k++) {
				double ang = i == 0 ? a0 + k * step + (r.nextDouble() - 0.5) * 0.5 : widestGap(used, a0, r);
				used.add(ang);
				double rx = rf * (0.9 + r.nextDouble() * 0.3);
				double rz = rx * (0.72 + r.nextDouble() * 0.28);
				fan(t, tu, tv, fy[i], ang, reach, rx, rz, id++, sun, bias, i, false, m);
			}
		}
		// the crown, over the leader: the smallest fan and the brightest
		double rt = m <= 5 ? 1.5 + r.nextDouble() * 0.2 : m <= 8 ? 2.1 + r.nextDouble() * 0.4 : TreeKit.clamp(m * 0.26, 2.4, 3.2);
		fan(t, tu, tv, topY, 0, 0, rt, rt * (0.8 + r.nextDouble() * 0.2), id, sun, bias, nF - 1, true, m);

		// ---- fallen leaves under the crown, thick at the trunk and thinning out
		int salt = 100;
		TreeKit.ground(t, cu0 + 0.5, cv0 + 0.5, m / 2.0 + 0.9, (u, v, k) -> {
			int hsh = t.hash(u, 0, v, salt);
			if (hsh < 58 - 28 * k) {
				return b(Blocks.LEAF_LITTER);
			}
			if (hsh < 65 - 22 * k) {
				return b(Blocks.CARPET.pick(DyeColor.RED));
			}
			if (hsh < 70 - 14 * k) {
				return b(Blocks.CARPET.pick(DyeColor.ORANGE));
			}
			if (hsh < 75 && k > 0.4) {
				return b(Blocks.RED_MUSHROOM);
			}
			if (hsh < 82) {
				return b(Blocks.MOSS_CARPET);
			}
			return null;
		});
		t.prune();
		t.flush(c);
	}

	/** The angle (from 36 candidates) farthest from every angle already used: the floor above sits over the gaps. */
	private static double widestGap(List<Double> used, double a0, Random r) {
		double best = a0;
		double bestD = -1;
		for (int j = 0; j < 36; j++) {
			double a = a0 + j * Math.PI * 2 / 36 + (r.nextDouble() - 0.5) * 0.1;
			double dmin = 9;
			for (double u : used) {
				dmin = Math.min(dmin, Math.abs(Math.atan2(Math.sin(a - u), Math.cos(a - u))));
			}
			if (dmin > bestD + 1e-9) {
				bestD = dmin;
				best = a;
			}
		}
		return best;
	}

	/**
	 * One fan on the floor at {@code py} and its single thin limb. The crown ({@code crown}) sits on the top of the
	 * trunk and has no limb; any other fan is centred {@code reach} (a fraction of the half-plot) from the trunk along
	 * {@code ang}, shrunk to fit the plot, on a limb that leaves the trunk at y 4 or higher and ends under the fan
	 * (in the fan's layer on the lowest floor).
	 */
	private static void fan(Vox t, int[] tu, int[] tv, int py, double ang, double reach, double rx, double rz, int id,
			double sun, double bias, int floor, boolean crown, int m) {
		double hue = 0.40 + 0.14 * Math.cos(ang - sun) * (crown ? 0.5 : 1.0) + 0.02 * floor + bias + (crown ? 0.04 : 0.0);
		double fu;
		double fv;
		if (crown) {
			fu = tu[py];
			fv = tv[py];
		} else {
			int ey = Math.max(FLOOR0, py - 1);
			int y0 = Math.max(FLOOR0, py - 3);
			int su = tu[y0];
			int sv = tv[y0];
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			double room = TreeKit.room(t, su, sv, ex, ez, 0);
			double rad = Math.max(rx, rz);
			double rr = TreeKit.clamp(reach * m / 2.0 + 0.4, 1.3, Math.max(1.3, room * 0.8));
			double s = TreeKit.clamp((room - rr + 0.6) / (1.2 * rad), 0.75, 1.0);
			rx *= s;
			rz *= s;
			fu = su + ex * rr;
			fv = sv + ez * rr;
			int fu0 = TreeKit.clamp((int) Math.round(fu), 0, t.w - 1);
			int fv0 = TreeKit.clamp((int) Math.round(fv), 0, t.d - 1);
			int[] a = {su, y0, sv};
			int[] z = {fu0, ey, fv0};
			int[] mid = {(int) Math.round(a[0] + (z[0] - a[0]) * 0.6), y0 + (ey - y0) / 2, (int) Math.round(a[2] + (z[2] - a[2]) * 0.6)};
			TreeKit.branch(t, WOOD, a, mid, false);
			TreeKit.branch(t, WOOD, mid, z, false);
		}
		pad(t, fu, py, fv, rx, rz, ang, id, hue, floor >= 1);
	}

	/**
	 * A flat fan of leaves, exactly one block thick: a scalloped ellipse with a ragged edge, a notch cut towards one
	 * side and a few holes of sky on the big ones.
	 */
	private static void pad(Vox t, double cu, int cy, double cv, double rx, double rz, double rot, int id, double hue, boolean top) {
		double R = Math.max(rx, rz);
		int u0 = (int) Math.floor(cu - R * 1.3) - 1;
		int u1 = (int) Math.ceil(cu + R * 1.3) + 1;
		int v0 = (int) Math.floor(cv - R * 1.3) - 1;
		int v1 = (int) Math.ceil(cv + R * 1.3) + 1;
		double notch = rot + id * 2.1;
		int ci = (int) Math.round(cu);
		int cj = (int) Math.round(cv);
		// the centre first: it sits on the limb's end, so the whole fan hangs from it
		t.leaf(ci, cy, cj, leaf(t, hue, ci, cy, cj, top));
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u1; u++) {
				double dx = u - cu;
				double dz = v - cv;
				double q = Math.sqrt(Math.pow(dx / rx, 2) + Math.pow(dz / rz, 2));
				double scallop = 1.0 + 0.19 * Math.cos((Math.atan2(dz, dx) + rot) * 5.0);
				double qq = q / scallop;
				double jit = (t.hash(u, cy, v, 9) - 4.0) / 4.0 * 0.16;
				boolean centre = u == ci && v == cj;
				if (qq <= 1.0 + jit && !(qq > 0.8 && R >= 2.4 && t.hash(u + id, cy + 9, v, 3) == 0)) {
					double da = Math.abs(Math.atan2(Math.sin(Math.atan2(dz, dx) - notch), Math.cos(Math.atan2(dz, dx) - notch)));
					boolean cut = R >= 3.0 && da < 0.3 && qq > 0.6;
					boolean hole = cut || (R >= 2.9 && !centre && qq > 0.4 && qq < 0.85 && t.hash(u + id, cy + 5, v, 8) == 0);
					if (!hole) {
						t.leafOf(u, cy, v, leaf(t, hue + (qq - 0.5) * 0.12, u, cy, v, top), id);
					}
				}
			}
		}
	}

	/** One leaf: the ramp index follows {@code hue} (0 deep red … 1 orange) with a clumpy jitter, so tones blend. */
	private static BlockState leaf(Vox t, double hue, int u, int y, int v, boolean top) {
		double coarse = (t.hash(Math.floorDiv(u, 2) + 7, y, Math.floorDiv(v, 2), 16) - 7.5) / 7.5;
		double fine = (t.hash(u, y, v, 16) - 7.5) / 7.5;
		double h = TreeKit.clamp(hue + coarse * 0.17 + fine * 0.13, 0.0, 0.999);
		int i = (int) Math.floor(h * 3);
		if (i == 2 && top && t.hash(u + 3, y, v, 14) == 0) {
			return HONEY;
		}
		return RAMP[i];
	}
}
