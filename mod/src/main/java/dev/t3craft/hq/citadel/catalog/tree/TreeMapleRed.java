package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Leaves;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Wood;
import java.util.Random;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

/**
 * Red maple in autumn: a stout dark 2×2 trunk on spreading roots that thins and forks low into four
 * diagonal limbs, carrying a broad umbrella of six rounded lobes of different sizes with sky between them.
 * The colour runs by lobe, from scarlet low and inside (nether wart and red wool) through red-orange to
 * orange at the sunny top (orange wool with honeycomb), each lobe two-toned. Leaf litter, a mushroom and
 * moss below.
 *
 * <p>The maple uses untinted blocks for its foliage: every vanilla leaf that could be red is biome-tinted
 * green in the renders and in plains.
 *
 * <p>Size: 7–9 × 7–9 × 9+ (the dome stops at 12 high; extra height stays empty).
 */
public final class TreeMapleRed implements Variant {
	private static final Wood WOOD = new Wood(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD, b(Blocks.MANGROVE_ROOTS));
	/** By lobe, low → high: scarlet, red, red-orange, orange. */
	private static final Leaves[] RAMP = {
			new Leaves(b(Blocks.NETHER_WART_BLOCK), b(Blocks.WOOL.pick(DyeColor.RED)), null, 3, 0),
			new Leaves(b(Blocks.WOOL.pick(DyeColor.RED)), b(Blocks.NETHER_WART_BLOCK), null, 4, 0),
			new Leaves(b(Blocks.WOOL.pick(DyeColor.RED)), b(Blocks.WOOL.pick(DyeColor.ORANGE)), null, 3, 0),
			new Leaves(b(Blocks.WOOL.pick(DyeColor.ORANGE)), b(Blocks.HONEYCOMB_BLOCK), null, 4, 0),
	};

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
		Random r = p.rng(0x3A91);
		int H = Math.min(p.h(), 12);
		double sc = Math.min(p.w(), p.d()) / 7.0;
		int ou = r.nextInt(2);
		int ov = r.nextInt(2);
		int bu = TreeKit.base(p.w(), ou);
		int bv = TreeKit.base(p.d(), ov);
		int y2 = 2;
		int fork = H >= 11 ? 4 : 3;
		int[][] lead = TreeKit.trunk(t, WOOD, bu, bv, y2, H - 3, ou, ov, 0, 0);
		TreeKit.roots(t, WOOD, bu, bv, 6, 2, -1, 5);

		// four limbs, low and diagonal, to the four side lobes; each lobe's tone follows its height
		double a0 = r.nextDouble() * Math.PI * 2;
		int[] lobeY = {fork + 2, fork + 4, fork + 3, fork + 5};
		for (int i = 0; i < 4; i++) {
			double ang = a0 + i * Math.PI / 2 + (r.nextDouble() - 0.5) * 0.5;
			double ex = Math.cos(ang);
			double ez = Math.sin(ang);
			int ly = Math.min(lobeY[i], H - 4);
			double rx = (2.0 + r.nextDouble() * 0.5) * sc;
			double rz = rx * (0.8 + r.nextDouble() * 0.2);
			int su = lead[0][fork];
			int sv = lead[1][fork];
			double reach = TreeKit.clamp(TreeKit.room(t, su, sv, ex, ez, 0) - Math.max(rx, rz) + 0.7, 1.2, 3.0 * sc);
			int tu = (int) Math.round(su + ex * reach);
			int tv = (int) Math.round(sv + ez * reach);
			TreeKit.branch(t, WOOD, new int[] {su, fork, sv}, new int[] {tu, ly, tv}, i % 2 == 0);
			int tone = ly <= fork + 2 ? 0 : ly <= fork + 3 ? 1 : 2;
			TreeKit.lobe(t, tu, ly + 0.2, tv, rx, rz, 2.3, 1.3, RAMP[tone], 0.45, i);
			// a low scarlet skirt lobe under the first limb, hanging off its elbow
			if (i == 0) {
				int mu = (su + tu * 2) / 3;
				int mv = (sv + tv * 2) / 3;
				TreeKit.lobe(t, mu + ex * 0.6, ly - 1.4, mv + ez * 0.6, 1.4 * sc, 1.3 * sc, 1.4, 1.0, RAMP[0], 0.35, 20 + i);
			}
		}
		// the high centre: an orange crown on the leader and a smaller red-orange cap beside it
		int top = H - 3;
		int cu = lead[0][top];
		int cv = lead[1][top];
		double cr = 2.5 * sc;
		TreeKit.lobe(t, cu, top + 0.4, cv, cr, cr * 0.9, 2.5, 1.3, RAMP[3], 0.45, 9);
		double ca = a0 + Math.PI / 4;
		int ku = (int) Math.round(lead[0][top - 2] + Math.cos(ca) * 1.6 * sc);
		int kv = (int) Math.round(lead[1][top - 2] + Math.sin(ca) * 1.6 * sc);
		TreeKit.branch(t, WOOD, new int[] {lead[0][top - 3], top - 3, lead[1][top - 3]}, new int[] {ku, top - 1, kv}, false);
		TreeKit.lobe(t, ku, top - 0.8, kv, 1.6 * sc, 1.5 * sc, 1.7, 1.1, RAMP[2], 0.4, 11);

		// leaf litter, thick under the dome, a mushroom and moss among the roots
		TreeKit.ground(t, bu + 0.5, bv + 0.5, Math.min(p.w(), p.d()) / 2.0 + 0.7, (u, v, k) -> {
			int hsh = t.hash(u, 0, v, 100);
			if (hsh < 62 - 25 * k) {
				return b(Blocks.LEAF_LITTER);
			}
			if (hsh < 66) {
				return b(Blocks.RED_MUSHROOM);
			}
			if (hsh < 76) {
				return b(Blocks.MOSS_CARPET);
			}
			return null;
		});
		t.prune();
		t.flush(c);
	}
}
