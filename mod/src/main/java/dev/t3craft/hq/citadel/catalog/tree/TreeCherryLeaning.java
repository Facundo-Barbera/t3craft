package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.CherryKit.Vox;
import java.util.List;
import java.util.Random;
import net.minecraft.world.level.block.Blocks;

/**
 * The leaning cherry: a bonsai-garden tree whose 2×2 trunk bends to one side (the seed picks which),
 * with the roots heaped up on the opposite side as if the tree pulled on the ground. One heavy limb
 * overhangs the plot in a flat cloud, a shorter limb balances it on the other side, and a cherry fence
 * prop (a <i>tsukae</i>, as in Japanese gardens) holds up the overhang. Petals fall in the lee of the
 * canopy.
 *
 * <p>Size: 5–7 × 5–7 × 8+.
 */
public final class TreeCherryLeaning implements Variant {
	@Override
	public String id() {
		return "tree_cherry_leaning";
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
		Random r = p.rng(0x1EA2);
		double cu = (p.w() - 1) / 2.0;
		double cv = (p.d() - 1) / 2.0;
		int top = p.h() - 1;
		int cy = top - 1;
		int yFork = Math.max(4, cy - 3); // the 2×2 trunk goes most of the way up, so the bend is smooth

		// lean direction: one of the four sides, a little off-axis
		double ang = r.nextInt(4) * Math.PI / 2 + (r.nextDouble() - 0.5) * 0.6;
		double lx = Math.cos(ang);
		double lz = Math.sin(ang);
		double lean = Math.min(3.0, Math.min(p.w(), p.d()) / 2.0 + 0.2);
		int bu = (int) Math.round(cu - lx * lean * 0.45 - 0.5);
		int bv = (int) Math.round(cv - lz * lean * 0.45 - 0.5);
		bu = Math.max(0, Math.min(p.w() - 2, bu));
		bv = Math.max(0, Math.min(p.d() - 2, bv));

		int[][] path = CherryKit.path(p.h(), bu, bv, lx * lean, lz * lean, 1, cy - 1);
		int[][] lead = CherryKit.trunk(t, path, yFork, cy - 1, lx >= 0 ? 1 : 0, lz >= 0 ? 1 : 0);

		// roots heaped on the side opposite to the lean: octant of (-lx, -lz) in the kit's N, NE, E… order
		int opp = Math.floorMod((int) Math.round(Math.atan2(-lx, lz) / (Math.PI / 4)), 8);
		CherryKit.roots(t, bu, bv, 6, 3, opp);

		// --- the heavy limb: out over the lean, rising, ending in a wide flat cloud
		int y0 = yFork - 2;
		int su = lead[0][y0];
		int sv = lead[1][y0];
		double room = CherryKit.room(t, su, sv, lx, lz);
		double ra = Math.min(3.6, Math.max(1.8, room - 0.4));
		double s = Math.max(1.5, Math.min(3.8, room - ra + 0.5));
		int ty = Math.min(y0 + 2, cy - 1);
		int tu = (int) Math.round(su + lx * s);
		int tv = (int) Math.round(sv + lz * s);
		List<int[]> main = CherryKit.line(su, y0, sv, tu, ty, tv);
		CherryKit.branch(t, main, 2, lx, lz);
		CherryKit.cloud(t, tu + lx * 0.5, ty + 1, tv + lz * 0.5, ra, ra * 0.78, lx, lz, 1, 1, 2, 2);
		// a twig off the heavy limb with its own small cloud, to the side
		int[] mid = main.get(main.size() / 2);
		double sa = ang + 1.25;
		double sx = Math.cos(sa);
		double sz = Math.sin(sa);
		double sl = Math.min(2.0, CherryKit.room(t, mid[0], mid[2], sx, sz) - 1.2);
		if (sl >= 1.0) {
			int fu = (int) Math.round(mid[0] + sx * sl);
			int fv = (int) Math.round(mid[2] + sz * sl);
			CherryKit.branch(t, CherryKit.line(mid[0], mid[1], mid[2], fu, mid[1] + 1, fv), 0, sx, sz);
			CherryKit.cloud(t, fu + sx * 0.3, mid[1] + 2, fv + sz * 0.3, 1.8, 1.4, sx, sz, 0, 1, 7, 2);
		}

		// prop: a cherry fence post under the limb, on a small stone footing, about two thirds of the way out
		int[] pr = main.get(Math.min(main.size() - 1, main.size() * 2 / 3));
		if (pr[1] - 1 >= 2 && t.empty(pr[0], 0, pr[2])) {
			t.set(pr[0], 0, pr[2], slab(Blocks.MUD_BRICK_SLAB));
			for (int y = 1; y < pr[1]; y++) {
				if (t.empty(pr[0], y, pr[2])) {
					t.set(pr[0], y, pr[2], b(Blocks.CHERRY_FENCE));
				}
			}
		}

		// --- the counter limb on the other side, short, to balance
		int by = Math.min(yFork, cy - 2);
		int ku = lead[0][by];
		int kv = lead[1][by];
		double bx = -lx;
		double bz = -lz;
		double broom = CherryKit.room(t, ku, kv, bx, bz);
		double bra = Math.min(2.3, Math.max(1.5, broom - 0.2));
		double bs = Math.max(1.0, Math.min(2.2, broom - bra + 0.5));
		int bu2 = (int) Math.round(ku + bx * bs);
		int bv2 = (int) Math.round(kv + bz * bs);
		int bty = Math.min(by + 1, cy - 1);
		CherryKit.branch(t, CherryKit.line(ku, by, kv, bu2, bty, bv2), 1, bx, bz);
		CherryKit.cloud(t, bu2 + bx * 0.3, bty + 1, bv2 + bz * 0.3, bra, bra * 0.78, bx, bz, 1, 1, 4, 2);

		// --- the crown, over the leader
		int cu2 = lead[0][cy - 1];
		int cv2 = lead[1][cy - 1];
		double margin = Math.min(Math.min(cu2, p.w() - 1 - cu2), Math.min(cv2, p.d() - 1 - cv2)) + 0.5;
		double cra = Math.min(2.4, margin);
		CherryKit.cloud(t, cu2, cy, cv2, cra, cra * 0.85, -lz, lx, 1, 1, 9, 2);

		CherryKit.ground(t, cu + lx * 0.6, cv + lz * 0.6, Math.min(p.w(), p.d()) / 2.0 + 1.2);
		t.flush(c);
	}
}
