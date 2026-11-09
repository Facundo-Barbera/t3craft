package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Vox;
import dev.t3craft.hq.citadel.catalog.tree.TreeKit.Wood;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A tall pine: a 2×2 spruce trunk with diagonal roots that thins to one leader, dressed from low down to the
 * tip in a cone of overlapping whorls. Every second layer is a whorl of five to seven needle arms of uneven
 * length (each whorl turned by the golden angle, so the arms never stack into a pagoda), whose tips droop a
 * block; the layer between is a fuller, narrower collar that hides the trunk, so the cone is continuous,
 * ragged at the edge and layered without bare trunk between floors. Spruce needles with clumps of dark-oak
 * green on the sunny tips, a small spire and ferns, moss and mushrooms at the foot.
 *
 * <p>Silhouette: the narrow tall spire (the maple is a broad umbrella, the birch a few thin strokes).
 *
 * <p>Size: 5–9 × 5–9 × 10+ (the tree stops at 15 high).
 */
public final class TreePineTiered implements Variant {
	private static final Wood WOOD = new Wood(Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, b(Blocks.MANGROVE_ROOTS));
	private static final BlockState NEEDLE = leaves(Blocks.SPRUCE_LEAVES);
	private static final BlockState TIP = leaves(Blocks.DARK_OAK_LEAVES);
	private static final double GOLDEN = 2.39996;

	@Override
	public String id() {
		return "tree_pine_tiered";
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
		return 10;
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
		Random r = p.rng(0x9177);
		int H = Math.min(p.h(), 15);
		int top = H - 1;
		int ou = r.nextInt(2);
		int ov = r.nextInt(2);
		int bu = TreeKit.base(p.w(), ou);
		int bv = TreeKit.base(p.d(), ov);
		int y2 = 2;
		int[][] lead = TreeKit.trunk(t, WOOD, bu, bv, y2, top - 2, ou, ov, 0, 0);
		TreeKit.roots(t, WOOD, bu, bv, 6, Math.min(p.w(), p.d()) >= 7 ? 2 : 1, -1, 7);

		int yBase = 3; // the lowest whorl: a little trunk shows at the foot, never between floors
		double phase = r.nextDouble() * Math.PI * 2;
		int arms = 5 + r.nextInt(3);
		for (int y = yBase; y <= top; y++) {
			double cu = y <= y2 ? bu + 0.5 : lead[0][Math.min(y, top - 2)];
			double cv = y <= y2 ? bv + 0.5 : lead[1][Math.min(y, top - 2)];
			double margin = Math.min(Math.min(cu, p.w() - 1 - cu), Math.min(cv, p.d() - 1 - cv)) + 0.45;
			double f = (top - y + 0.6) / (double) (top - yBase + 1); // 1 at the foot, 0 at the tip
			double rCone = margin * Math.pow(f, 0.85);
			boolean whorl = ((top - y) & 1) == 0 && y < top - 1;
			double ph = phase + (y / 2) * GOLDEN;
			int span = (int) Math.ceil(margin) + 1;
			for (int v = (int) Math.floor(cv) - span; v <= (int) Math.ceil(cv) + span; v++) {
				for (int u = (int) Math.floor(cu) - span; u <= (int) Math.ceil(cu) + span; u++) {
					double du = u - cu;
					double dv = v - cv;
					double dist = Math.hypot(du, dv);
					double th = Math.atan2(dv, du);
					double lim;
					if (whorl) {
						// arms: uneven lengths, a hash per arm
						int arm = (int) Math.floor(((th - ph) / (Math.PI * 2) + 10) * arms) % arms;
						double armLen = 0.8 + 0.25 * t.hash(arm, y, 3, 3);
						double wave = Math.pow(Math.max(0, Math.cos(arms * (th - ph) / 2)), 2);
						lim = rCone * (0.62 + 0.38 * wave) * armLen + 0.35;
					} else {
						lim = rCone * 0.72 + 0.3;
					}
					lim -= t.hash(u, y, v, 4) * 0.12;
					if (dist > lim) {
						continue;
					}
					boolean edge = dist > lim - 1.0;
					BlockState s = edge && t.hash(u >> 1, y, v >> 1, 3) == 0 && y > yBase + 1 ? TIP : NEEDLE;
					t.leaf(u, y, v, s);
					// the whorl's arm tips droop one block, a few two
					if (whorl && edge && dist > 1.2 && t.hash(u, y + 9, v, 3) != 0) {
						t.leaf(u, y - 1, v, NEEDLE);
						if (dist > 2.2 && t.hash(u, y + 4, v, 4) == 0) {
							t.leaf(u, y - 2, v, TIP);
						}
					}
				}
			}
			// stubs of branch inside the low whorls, seen between the arms from below
			if (whorl && y <= yBase + 4) {
				Direction dir = Direction.from2DDataValue(t.hash(y, 1, 1, 4));
				int su = (int) Math.round(cu) + dir.getStepX();
				int sv = (int) Math.round(cv) + dir.getStepZ();
				t.woodSoft(su, y, sv, WOOD.log(dir.getAxis()));
			}
		}
		// spire: the needles close over the leader and a last tuft
		int lu = lead[0][top - 2];
		int lv = lead[1][top - 2];
		t.leaf(lu, top - 1, lv, NEEDLE);
		t.leaf(lu, top, lv, NEEDLE);

		TreeKit.ground(t, bu + 0.5, bv + 0.5, Math.min(p.w(), p.d()) / 2.0 + 0.5, (u, v, k) -> switch (t.hash(u, 0, v, 9)) {
			case 0, 1 -> b(Blocks.FERN);
			case 2, 3 -> b(Blocks.MOSS_CARPET);
			case 4 -> k > 0.4 ? b(Blocks.BROWN_MUSHROOM) : null;
			case 5 -> b(Blocks.LEAF_LITTER);
			default -> null;
		});
		t.prune();
		t.flush(c);
	}
}
