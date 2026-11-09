package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Wood;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Japanese red maple (momiji) in autumn: a slim, sinuous dark trunk on a short flared foot that bends in an S and
 * opens, from 3–4 blocks up, into limbs that curve out and up. Each limb ends inside a flat, scalloped fan of
 * leaves; the fans sit at two or three levels (a low ring, a middle ring on tall plots, a crown over the leader)
 * with sky between them and holes in them, so the crown reads as horizontal layers and never as a cube. The
 * colour is a fine mix of four untinted blocks (nether wart, red wool, orange terracotta, orange wool, a few
 * honeycomb flecks on the sunny top) that runs from deep red under the crown to orange on the sunny side, and
 * each seed leans redder or more orange. A carpet of fallen leaves (leaf litter, red and orange carpet) below.
 *
 * <p>The maple uses untinted blocks for its foliage: every vanilla leaf that could be red is biome-tinted green
 * in the renders and in plains. Everything is drawn into a {@link Vox}, which also removes any leaf that would
 * float.
 *
 * <p>Size: 5–11 × 5–11 × 9+ (the crown height follows the seed, 9–14, and the fans scale with the footprint).
 */
public final class TreeMapleRed implements Variant {
	private static final Wood WOOD = new Wood(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD, b(Blocks.MANGROVE_ROOTS));
	/** Deep red → orange; the honeycomb is a rare fleck on sunlit tops. */
	private static final BlockState[] RAMP = {
			b(Blocks.NETHER_WART_BLOCK),
			b(Blocks.WOOL.pick(DyeColor.RED)),
			b(Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE)),
			b(Blocks.WOOL.pick(DyeColor.ORANGE)),
	};
	private static final BlockState HONEY = b(Blocks.HONEYCOMB_BLOCK);

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
		int H = Math.min(p.h(), 9 + r.nextInt(4) + (m >= 9 ? 2 : 0));
		int ou = r.nextInt(2);
		int ov = r.nextInt(2);
		int bu = TreeKit.base(w, ou);
		int bv = TreeKit.base(d, ov);
		int cu0 = bu + ou;
		int cv0 = bv + ov;
		double bias = (r.nextInt(5) - 2) * 0.08; // some trees lean redder, some more orange
		double sun = r.nextDouble() * Math.PI * 2;
		double a0 = r.nextDouble() * Math.PI * 2;
		int topY = H - 2; // main layer of the crown pad over the leader

		// ---- foot: a 2×2 flare of bark, short roots
		for (int a = 0; a < 2; a++) {
			for (int e = 0; e < 2; e++) {
				t.wood(bu + a, 0, bv + e, WOOD.bark());
				if (!(a != ou && e != ov)) {
					t.wood(bu + a, 1, bv + e, WOOD.bark());
				}
			}
		}
		TreeKit.roots(t, WOOD, bu, bv, 4 + r.nextInt(2), m >= 8 ? 2 : 1, -1, 5);

		// ---- the sinuous trunk: an S curve that comes back to the axis under the crown
		double amp = m >= 8 ? 1.6 : 1.2;
		double phi = r.nextDouble() * Math.PI * 2;
		int[] tu = new int[H + 1];
		int[] tv = new int[H + 1];
		int cu = cu0;
		int cv = cv0;
		tu[0] = cu;
		tv[0] = cv;
		tu[1] = cu;
		tv[1] = cv;
		int span = Math.max(1, topY - 1);
		for (int y = 2; y <= topY; y++) {
			double s = Math.sin((y - 1.0) / span * Math.PI * 2);
			int goalU = cu0 + (int) Math.round(amp * Math.cos(phi) * s);
			int goalV = cv0 + (int) Math.round(amp * Math.sin(phi) * s);
			goalU = TreeKit.clamp(goalU, 1, w - 2);
			goalV = TreeKit.clamp(goalV, 1, d - 2);
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
				t.wood(cu, y - 1, cv, WOOD.bark());
				t.wood(cu + du, y - 1, cv + dv, WOOD.bark());
			}
			cu += du;
			cv += dv;
			tu[y] = cu;
			tv[y] = cv;
			t.wood(cu, y, cv, WOOD.log(Direction.Axis.Y));
		}
		for (int y = topY + 1; y <= H; y++) {
			tu[y] = cu;
			tv[y] = cv;
		}
		double ccu = tu[topY];
		double ccv = tv[topY];

		// ---- the fans: a low ring, a middle ring on tall plots, the crown over the leader
		double rbase = TreeKit.clamp(m * 0.31, 1.7, 4.4);
		int nLow = m <= 7 ? 3 : m <= 10 ? 4 : 5;
		double step = Math.PI * 2 / nLow;
		int id = 1;
		for (int k = 0; k < nLow; k++) {
			double ang = a0 + k * step + (r.nextDouble() - 0.5) * 0.7;
			int py = 4 + ((k & 1) == 1 && H >= 10 ? 1 : 0) + (r.nextInt(5) == 0 ? 1 : 0);
			double rx = rbase * (0.9 + r.nextDouble() * 0.35);
			double rz = rx * (0.72 + r.nextDouble() * 0.28);
			fan(t, r, cu0, cv0, ang, 0.52 + r.nextDouble() * 0.12, rx, rz, py, 2 + (k & 1), tu, tv, id++, sun, bias, 0.0, m);
		}
		if (H >= 12) {
			int nMid = m >= 8 ? 3 : 2;
			double ms = Math.PI * 2 / nMid;
			for (int k = 0; k < nMid; k++) {
				double ang = a0 + step / 2 + k * ms + (r.nextDouble() - 0.5) * 0.6;
				int py = 7 + (H - 12) / 2 + (k & 1);
				py = Math.min(py, topY - 3);
				double rx = rbase * (0.78 + r.nextDouble() * 0.3);
				double rz = rx * (0.75 + r.nextDouble() * 0.25);
				fan(t, r, cu0, cv0, ang, 0.34 + r.nextDouble() * 0.1, rx, rz, py, Math.max(3, py - 3), tu, tv, id++, sun, bias, 0.1, m);
			}
		}
		// the crown, over the leader: the brightest
		double rt = rbase * (0.95 + r.nextDouble() * 0.2);
		fan(t, r, ccu, ccv, 0, 0.0, rt, rt * (0.8 + r.nextDouble() * 0.2), topY, topY - 3, tu, tv, id++, sun, bias, 0.2, m);

		// ---- fallen leaves under the crown, thick at the trunk and thinning out
		int salt = 100;
		TreeKit.ground(t, cu0 + 0.5, cv0 + 0.5, m / 2.0 + 0.9, (u, v, k) -> {
			int hsh = t.hash(u, 0, v, salt);
			if (hsh < 52 - 28 * k) {
				return b(Blocks.LEAF_LITTER);
			}
			if (hsh < 64 - 18 * k) {
				return b(Blocks.CARPET.pick(DyeColor.RED));
			}
			if (hsh < 72 - 8 * k) {
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

	/**
	 * One limb and its fan. The fan's centre is {@code reach} (a fraction of the half-plot) from {@code (cu, cv)} along
	 * {@code ang} at height {@code py}; with {@code reach == 0} it is centred on {@code (cu, cv)} (the crown). The limb
	 * starts on the trunk at {@code ys}, goes out and then up, and ends inside the fan.
	 */
	private static void fan(Vox t, Random r, double cu, double cv, double ang, double reach, double rx, double rz, int py,
			int ys, int[] tu, int[] tv, int id, double sun, double bias, double hueUp, int m) {
		double ex = Math.cos(ang);
		double ez = Math.sin(ang);
		double fu = cu;
		double fv = cv;
		if (reach > 0) {
			double half = m / 2.0;
			double rr = reach * half + 0.6;
			double room = TreeKit.room(t, cu, cv, ex, ez, 0) - Math.max(rx, rz) * 0.8;
			rr = TreeKit.clamp(Math.min(rr, room), 1.2, 6.0);
			fu = cu + ex * rr;
			fv = cv + ez * rr;
		}
		int fu0 = (int) Math.round(fu);
		int fv0 = (int) Math.round(fv);
		double hue = 0.42 + 0.3 * Math.cos(ang - sun) * (reach > 0 ? 1 : 0.4) + 0.045 * (py - 4) + hueUp + bias;
		if (reach > 0) {
			// the limb: from the trunk out and then up into the fan
			int y0 = TreeKit.clamp(ys, 2, py - 1);
			int[] a = {tu[y0], y0, tv[y0]};
			int[] z = {fu0, py, fv0};
			int[] mid = {(int) Math.round(a[0] + (z[0] - a[0]) * 0.72), y0 + Math.max(0, (py - y0) / 3),
					(int) Math.round(a[2] + (z[2] - a[2]) * 0.72)};
			TreeKit.branch(t, WOOD, a, mid, false);
			TreeKit.branch(t, WOOD, mid, z, false);
		}
		pad(t, fu, py, fv, rx, rz, ang, id, hue);
	}

	/**
	 * A flat fan of leaves: the main layer a scalloped ellipse, a ragged cap layer above it (smaller), a few drips
	 * under the rim, and holes of sky in the main layer on the big ones.
	 */
	private static void pad(Vox t, double cu, int cy, double cv, double rx, double rz, double rot, int id, double hue) {
		double R = Math.max(rx, rz);
		int u0 = (int) Math.floor(cu - R * 1.3) - 1;
		int u1 = (int) Math.ceil(cu + R * 1.3) + 1;
		int v0 = (int) Math.floor(cv - R * 1.3) - 1;
		int v1 = (int) Math.ceil(cv + R * 1.3) + 1;
		int ci = (int) Math.round(cu);
		int cj = (int) Math.round(cv);
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u1; u++) {
				double dx = u - cu;
				double dz = v - cv;
				double q = Math.sqrt(Math.pow(dx / rx, 2) + Math.pow(dz / rz, 2));
				double scallop = 1.0 + 0.17 * Math.cos((Math.atan2(dz, dx) + rot) * 5.0);
				double qq = q / scallop;
				double jit = (t.hash(u, cy, v, 9) - 4.0) / 4.0 * 0.16;
				boolean centre = u == ci && v == cj;
				if (qq <= 1.0 + jit) {
					boolean hole = R >= 2.2 && !centre && qq > 0.38 && qq < 0.85 && t.hash(u + id, cy + 5, v, 9) == 0;
					if (!hole) {
						t.leafOf(u, cy, v, leaf(t, hue + (qq - 0.5) * 0.12, u, cy, v, false), id);
					}
				}
				// the cap: smaller and ragged, brighter
				if (qq <= 0.62 + jit && (centre || t.hash(u, cy + 1, v + id, 6) != 0)) {
					t.leafOf(u, cy + 1, v, leaf(t, hue + 0.1, u, cy + 1, v, true), id);
				}
				// a few drips under the rim, darker
				if (qq > 0.5 && qq <= 1.0 + jit && t.hash(u + id, cy - 1, v, 7) == 0 && t.kind(u, cy, v) != TreeKit.EMPTY) {
					t.leafOf(u, cy - 1, v, leaf(t, hue - 0.14, u, cy - 1, v, false), id);
				}
			}
		}
	}

	/** One leaf: the ramp index follows {@code hue} (0 deep red … 1 orange) with a clumpy jitter, so tones blend. */
	private static BlockState leaf(Vox t, double hue, int u, int y, int v, boolean top) {
		double coarse = (t.hash(Math.floorDiv(u, 2) + 7, y, Math.floorDiv(v, 2), 16) - 7.5) / 7.5;
		double fine = (t.hash(u, y, v, 16) - 7.5) / 7.5;
		double h = TreeKit.clamp(hue + coarse * 0.14 + fine * 0.14, 0.0, 0.999);
		int i = (int) Math.floor(h * 4);
		if (i == 3 && top && t.hash(u + 3, y, v, 7) == 0) {
			return HONEY;
		}
		return RAMP[i];
	}
}
