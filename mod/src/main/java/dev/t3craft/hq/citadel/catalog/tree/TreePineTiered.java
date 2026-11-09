package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A tall stepped pine: a dark trunk that flares at the root and climbs bare through the lowest metres, then
 * two to four tiers of needles, each a wide skirt over a narrower body with a gap of visible trunk (and
 * branch stubs) between tiers, shrinking to a cross and a thin spire.
 *
 * <p>Silhouette: a narrow, tall, stair-stepped spire (the maple is a wide dome, the birch a few thin
 * strokes). Tiers sit every three layers counted down from the tip, so any height from 10 up ends on
 * the spire.
 *
 * <p>Size: 5–9 × 5–9 × 10–14.
 */
public final class TreePineTiered implements Variant {
	private static final BlockState LOG = b(Blocks.SPRUCE_LOG);
	private static final BlockState LOG_DARK = b(Blocks.DARK_OAK_LOG);

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
		int w = p.w();
		int d = p.d();
		int h = p.h();
		int uLo = MixedTreeKit.lo(w); // one thin column: the tiers are centred on it, even on an even-sized plot
		int uHi = uLo;
		int vLo = MixedTreeKit.lo(d);
		int vHi = vLo;
		double cx = uLo;
		double cz = vLo;

		int rMax = (int) Math.min(3, Math.floor((Math.min(w, d) - 1) / 2.0)); // 2..3: a narrow spire however big the plot
		int rEnd = 1;
		int tiers = MixedTreeKit.clamp((h - 3) / 3, 2, 4);
		int last = h - 4; // base layer of the top tier: +2 cross, +3 spire tip

		ground(c, p, cx, cz, uLo, uHi, vLo, vHi);
		for (int k = 0; k < tiers; k++) {
			int baseY = last - 3 * (tiers - 1 - k);
			int r = (int) Math.round(rMax - k * (rMax - rEnd) / (double) (tiers - 1));
			tier(c, p, cx, cz, baseY, r, k, uLo, uHi, vLo, vHi);
		}
		// top: a cross of needles over the last tier, then the tip
		int crossY = last + 2;
		for (int[] o : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			MixedTreeKit.put(c, p, uLo + o[0], crossY, vLo + o[1], leaves(Blocks.SPRUCE_LEAVES));
		}
		trunk(c, p, 0, crossY, uLo, uHi, vLo, vHi);
		MixedTreeKit.put(c, p, uLo, h - 1, vLo, b(Blocks.SPRUCE_FENCE));
	}

	// ------------------------------------------------------------------ ground and trunk

	/** [depth] the trunk flares with stairs rising into it, between ferns, moss and a few mushrooms. */
	private static void ground(CitadelCanvas c, Plot p, double cx, double cz, int uLo, int uHi, int vLo, int vHi) {
		for (int u = 0; u < p.w(); u++) {
			for (int v = 0; v < p.d(); v++) {
				if (Math.hypot(u - cx, v - cz) > 2.6) {
					continue;
				}
				BlockState s = switch (p.hash(u, 0, v, 7)) {
					case 0 -> b(Blocks.FERN);
					case 1 -> b(Blocks.MOSS_CARPET);
					case 2 -> b(Blocks.BROWN_MUSHROOM);
					default -> null;
				};
				if (s != null) {
					MixedTreeKit.put(c, p, u, 0, v, s);
				}
			}
		}
		for (int u = uLo; u <= uHi; u++) {
			flare(c, p, u, vLo - 1, Direction.SOUTH);
			flare(c, p, u, vHi + 1, Direction.NORTH);
		}
		for (int v = vLo; v <= vHi; v++) {
			flare(c, p, uLo - 1, v, Direction.EAST);
			flare(c, p, uHi + 1, v, Direction.WEST);
		}
	}

	private static void flare(CitadelCanvas c, Plot p, int u, int v, Direction towardsTrunk) {
		boolean stair = p.hash(u, 0, v, 4) != 0;
		MixedTreeKit.put(c, p, u, 0, v, stair ? stairs(Blocks.SPRUCE_STAIRS, towardsTrunk) : b(Blocks.MOSSY_COBBLESTONE_SLAB));
	}

	/** Dark oak at the root, spruce above; a single thick-or-thin column once past the lower tiers. */
	private static void trunk(CitadelCanvas c, Plot p, int fullUntil, int topY, int uLo, int uHi, int vLo, int vHi) {
		for (int y = 0; y <= topY; y++) {
			for (int u = uLo; u <= uHi; u++) {
				for (int v = vLo; v <= vHi; v++) {
					if (y > fullUntil && (u != uLo || v != vLo)) {
						continue;
					}
					MixedTreeKit.put(c, p, u, y, v, y < 2 ? LOG_DARK : LOG);
				}
			}
		}
	}

	// ------------------------------------------------------------------ tiers

	private static boolean inDisc(double du, double dv, double r) {
		return du * du + dv * dv <= r * r + r * 0.6;
	}

	/** A skirt layer at {@code baseY}, a narrower body above it, droop and branch stubs in the gap below. */
	private static void tier(CitadelCanvas c, Plot p, double cx, double cz, int baseY, int r, int k, int uLo, int uHi, int vLo, int vHi) {
		for (int u = 0; u < p.w(); u++) {
			for (int v = 0; v < p.d(); v++) {
				double du = u - cx;
				double dv = v - cz;
				if (inDisc(du, dv, r)) {
					boolean rim = r > 1 && !inDisc(du, dv, r - 1);
					if (rim && p.hash(u, baseY, v, 5) == 0) {
						continue; // [finish] ragged skirt edge
					}
					if (rim && (u == uLo || v == vLo) && p.hash(u, baseY - 1, v, 3) != 0) {
						MixedTreeKit.put(c, p, u, baseY - 1, v, leaves(Blocks.SPRUCE_LEAVES)); // the four long tips droop
					}
					MixedTreeKit.put(c, p, u, baseY, v, needles(p, u, baseY, v, k));
				}
				int body = r >= 3 ? r - 2 : r - 1; // a wide skirt under a much narrower cap: the step reads from afar
				if (body >= 1 && inDisc(du, dv, body)) {
					MixedTreeKit.put(c, p, u, baseY + 1, v, needles(p, u, baseY + 1, v, k));
				}
			}
		}
		// [depth] branch stubs under the skirt, so the gap reads as trunk with limbs, not a bare pole
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] s : sides) {
			int u = (s[0] > 0 ? uHi : s[0] < 0 ? uLo : uLo) + s[0];
			int v = (s[1] > 0 ? vHi : s[1] < 0 ? vLo : vLo) + s[1];
			if (p.hash(u, baseY, v, 2) == 0) {
				MixedTreeKit.put(c, p, u, baseY - 1, v, MixedTreeKit.log(Blocks.SPRUCE_LOG, s[0] != 0 ? Direction.Axis.X : Direction.Axis.Z));
			}
		}
	}

	/** [palette] spruce needles; the upper tiers catch a few brighter azalea patches. */
	private static BlockState needles(Plot p, int u, int y, int v, int k) {
		int n = p.hash(u, y, v, 7);
		if (k >= 1 && n == 0) {
			return leaves(Blocks.AZALEA_LEAVES);
		}
		return leaves(Blocks.SPRUCE_LEAVES);
	}
}
