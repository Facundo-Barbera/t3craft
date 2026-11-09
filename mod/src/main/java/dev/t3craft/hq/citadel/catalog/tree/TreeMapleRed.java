package dev.t3craft.hq.citadel.catalog.tree;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Maple: a short, stout reddish trunk on mangrove roots that forks into four limbs, carrying a broad,
 * bumpy dome made of clustered lobes (one big crown, four lobes over the limb tips, four lower fillers),
 * wider than it is tall. Leaves are mangrove (dark, low and inside) and acacia (lighter, high and outside);
 * the underside hangs ragged and the ground below is a bed of leaf litter with a mushroom or two.
 *
 * <p>Silhouette: the round, wide "umbrella" (the birch is a slender grove, the pine a stepped spire).
 * Both leaf blocks are biome-tinted by the game, so the warm tones depend on the biome's foliage colour.
 *
 * <p>Size: 7–9 × 7–9 × 9–14 (the dome stops at {@code min(w, d) + 2}; extra height stays empty).
 */
public final class TreeMapleRed implements Variant {
	private static final BlockState BARK = b(Blocks.MANGROVE_LOG);
	private static final BlockState BARK_PALE = b(Blocks.STRIPPED_MANGROVE_LOG);
	private static final BlockState ROOTS = b(Blocks.MANGROVE_ROOTS);

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
		int w = p.w();
		int d = p.d();
		int top = Math.min(p.h() - 1, Math.min(w, d) + 2);
		int t = MixedTreeKit.clamp(Math.round((top + 1) * 0.45f), 4, 6); // fork height: a trunk that shows
		double cx = (w - 1) / 2.0;
		double cz = (d - 1) / 2.0;
		double radius = (Math.min(w, d) - 1) / 2.0; // 3..4
		int uLo = MixedTreeKit.lo(w);
		int uHi = MixedTreeKit.hi(w);
		int vLo = MixedTreeKit.lo(d);
		int vHi = MixedTreeKit.hi(d);

		ground(c, p, cx, cz, radius, uLo, uHi, vLo, vHi);
		canopy(c, p, cx, cz, radius, t, top);
		limbs(c, p, radius, t, uLo, uHi, vLo, vHi);
		trunk(c, p, t, uLo, uHi, vLo, vHi);
	}

	// ------------------------------------------------------------------ ground

	/** [finish] leaf litter under the dome, roots hugging the trunk, mushrooms and moss between them. */
	private static void ground(CitadelCanvas c, Plot p, double cx, double cz, double radius, int uLo, int uHi, int vLo, int vHi) {
		for (int u = 0; u < p.w(); u++) {
			for (int v = 0; v < p.d(); v++) {
				double dist = Math.hypot(u - cx, v - cz);
				if (dist <= radius + 0.6 && p.hash(u, 0, v, 3) == 0) {
					MixedTreeKit.put(c, p, u, 0, v, b(Blocks.LEAF_LITTER));
				}
			}
		}
		for (int u = uLo - 1; u <= uHi + 1; u++) {
			for (int v = vLo - 1; v <= vHi + 1; v++) {
				boolean insideU = u >= uLo && u <= uHi;
				boolean insideV = v >= vLo && v <= vHi;
				if (insideU && insideV) {
					continue;
				}
				int n = p.hash(u, 0, v, 4);
				if (insideU || insideV) { // next to the trunk: roots, a second tier on some
					MixedTreeKit.put(c, p, u, 0, v, n < 3 ? ROOTS : b(Blocks.MOSS_CARPET));
					if (n == 0) {
						MixedTreeKit.put(c, p, u, 1, v, ROOTS);
					}
				} else { // the corners: litter, a red mushroom or moss
					BlockState s = switch (p.hash(u, 1, v, 3)) {
						case 0 -> b(Blocks.RED_MUSHROOM);
						case 1 -> b(Blocks.MOSS_CARPET);
						default -> b(Blocks.LEAF_LITTER);
					};
					MixedTreeKit.put(c, p, u, 0, v, s);
				}
			}
		}
	}

	// ------------------------------------------------------------------ trunk and limbs

	private static void trunk(CitadelCanvas c, Plot p, int t, int uLo, int uHi, int vLo, int vHi) {
		for (int y = 0; y <= t + 1; y++) {
			for (int u = uLo; u <= uHi; u++) {
				for (int v = vLo; v <= vHi; v++) {
					// [palette] mostly bark, a few stripped (pale) patches higher up
					MixedTreeKit.put(c, p, u, y, v, y >= 2 && p.hash(u, y, v, 6) == 0 ? BARK_PALE : BARK);
				}
			}
		}
	}

	/** Four limbs, one per side, rising one block every two: horizontal logs, a vertical one where they climb. */
	private static void limbs(CitadelCanvas c, Plot p, double radius, int t, int uLo, int uHi, int vLo, int vHi) {
		int len = (int) Math.ceil(radius) - 1;
		int[][] dirs = {{1, 0, uHi, vLo}, {-1, 0, uLo, vHi}, {0, -1, uLo, vLo}, {0, 1, uHi, vHi}};
		for (int[] dir : dirs) {
			Direction.Axis axis = dir[0] != 0 ? Direction.Axis.X : Direction.Axis.Z;
			int prevY = t;
			for (int i = 1; i <= len; i++) {
				int u = dir[2] + dir[0] * i;
				int v = dir[3] + dir[1] * i;
				int y = t + (i - 1); // climbs one block per step
				if (y > prevY) {
					MixedTreeKit.put(c, p, u, y - 1, v, b(Blocks.MANGROVE_LOG));
				}
				MixedTreeKit.put(c, p, u, y, v, MixedTreeKit.log(Blocks.MANGROVE_LOG, axis));
				prevY = y;
			}
		}
	}

	// ------------------------------------------------------------------ canopy

	/** Lobe = {centre u, centre y, centre v, horizontal radius, vertical radius}. */
	private static double[][] lobes(double cx, double cz, double radius, int t, int top) {
		double ry = Math.max(1.8, (top - t) * 0.36);
		double[][] l = new double[9][];
		l[0] = new double[] {cx, top - ry + 0.5, cz, radius * 0.8 + 0.3, ry};
		double a = radius * 0.5; // lobes over the limb tips, kept inside the box with room to spare
		double tr = radius * 0.45 + 0.7;
		double ty = t + 2.8;
		l[1] = new double[] {cx + a, ty, cz, tr, 1.6};
		l[2] = new double[] {cx - a, ty, cz, tr, 1.6};
		l[3] = new double[] {cx, ty, cz + a, tr, 1.6};
		l[4] = new double[] {cx, ty, cz - a, tr, 1.6};
		double bd = radius * 0.42;
		double fr = radius * 0.4 + 0.6;
		double fy = t + 3.6;
		l[5] = new double[] {cx + bd, fy, cz + bd, fr, 1.5};
		l[6] = new double[] {cx - bd, fy, cz + bd, fr, 1.5};
		l[7] = new double[] {cx + bd, fy, cz - bd, fr, 1.5};
		l[8] = new double[] {cx - bd, fy, cz - bd, fr, 1.5};
		return l;
	}

	private static double density(double[][] lobes, int u, int y, int v) {
		double best = Double.MAX_VALUE;
		for (double[] l : lobes) {
			double du = u - l[0];
			double dy = y - l[1];
			double dv = v - l[2];
			best = Math.min(best, (du * du + dv * dv) / (l[3] * l[3]) + dy * dy / (l[4] * l[4]));
		}
		return best;
	}

	private static void canopy(CitadelCanvas c, Plot p, double cx, double cz, double radius, int t, int top) {
		int w = p.w();
		int d = p.d();
		double[][] lobes = lobes(cx, cz, radius, t, top);
		boolean[][][] g = new boolean[w][top + 1][d];
		for (int y = t + 1; y <= top; y++) {
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					double q = density(lobes, u, y, v);
					if (q > 1.0 || (q > 0.7 && p.hash(u, y, v, 5) == 0)) {
						continue; // [finish] ragged surface
					}
					g[u][y][v] = true;
				}
			}
		}
		// [finish] a fringe of leaves hanging from the underside (bottom-up so one row never cascades)
		for (int y = t + 1; y < top; y++) {
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (!g[u][y][v] && g[u][y + 1][v] && p.hash(u, y, v, 4) == 0) {
						g[u][y][v] = true;
					}
				}
			}
		}
		for (int y = t + 1; y <= top; y++) {
			double f = (y - t) / (double) Math.max(1, top - t);
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (g[u][y][v]) {
						// [palette] dark mangrove low, lighter acacia climbing towards the crown
						boolean light = p.hash(u, y, v, 10) < 2 + f * 5;
						BlockState s = leaves(light ? Blocks.ACACIA_LEAVES : Blocks.MANGROVE_LEAVES);
						// [palette] both leaves are biome-tinted (green on plains): red patches, untinted, on the sunny outer shell,
						// chosen per 2x2x2 chunk so they read as clusters of foliage and not as loose cubes
						if (f > 0.3 && density(lobes, u, y, v) > 0.4) {
							// [finish] colour by 2x2x2 chunk: orange-brown on the lower shell, scarlet towards the crown,
							// each chunk one colour with a few leaf blocks left in, so it reads as foliage and not confetti
							int zone = p.hash(u >> 1, y >> 1, v >> 1, 5);
							boolean lower = f < 0.62;
							if (zone < 3 && p.hash(u, y, v, 6) != 0) {
								s = lower ? dev.t3craft.hq.citadel.CitadelCanvas.terracotta(net.minecraft.world.item.DyeColor.ORANGE)
										: b(Blocks.NETHER_WART_BLOCK);
							}
						}
						MixedTreeKit.put(c, p, u, y, v, s);
					}
				}
			}
		}
	}
}
