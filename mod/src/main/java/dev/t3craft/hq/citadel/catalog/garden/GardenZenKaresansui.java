package dev.t3craft.hq.citadel.catalog.garden;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A dry rock garden (karesansui): raked sand in two tones that ripples round the rock islands and
 * runs in straight rake lines elsewhere, a stone lantern (toro) on a mossy island, a cloud-pruned
 * cherry bonsai on a rock, a tsukubai basin fed by a bamboo pipe, stepping stones and a bamboo fence
 * on a dark stone curb.
 *
 * <pre>
 *  v0   ▓▓▓▓ ▓▓ gap ▓▓▓▓▓▓      curb + entry (half-slab step), stone pillars with lanterns
 *  v1   ░ ░ ░ stepping stones ░  rake lines (ridge = half slab on sand)
 *  v3   ▒▒▒ rocks       ░ ░ ░    island L (rock triad + bonsai)
 *  vd-3 ▒▒▒ bonsai   ▒toro▒      island T (stone lantern)
 *  vd-1 ▓▓ bamboo fence ▓▓▓▓▓
 * </pre>
 *
 * Size: 9–12 × 8–12 × 5+. Techniques: [depth] ridges are half a block above the grooves, rocks are
 * built from a half-block height map with slabs and stairs; [palette] sand/sandstone (or calcite/
 * diorite) + deepslate curb + bamboo + moss and rock greys + one pink accent; [finish] lanterns,
 * pebbles, a water basin, moss carpets and a pink bonsai.
 */
public final class GardenZenKaresansui implements Variant {
	private static final Block[] ROCK = {Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE};
	private static final Block[] ROCK_SLAB = {Blocks.COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.STONE_SLAB, Blocks.ANDESITE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB};
	private static final Block[] ROCK_STAIRS = {Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.STONE_STAIRS, Blocks.ANDESITE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS};

	private static final Direction[] LOCAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	@Override
	public String id() {
		return "garden_zen_karesansui";
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
		return 8;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 12;
	}

	@Override
	public int maxD() {
		return 12;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		boolean[][] taken = new boolean[w][d];
		boolean white = p.hash(0, 0, 0, 2) == 1; // seed: sand/sandstone or calcite/diorite

		int cu = w / 2; // entry cells cu-1 and cu
		int tu = w - 3;
		int tv = d - 3; // stone lantern centre
		int lu = 2; // island L spine column
		int lv1 = 5;
		int lv2 = Math.max(lv1, d - 4);
		boolean lone = w >= 10 && d >= 9;

		border(c, p, taken, cu);
		// ---- islands (moss), rocks, lantern, bonsai
		island(c, p, taken, lu, lv1, lu, lv2, 1.6);
		island(c, p, taken, tu, tv, tu, tv, 1.7);
		rockTriad(c, p, lu, 3);
		bonsai(c, p, lu, lv2);
		toro(c, p, tu, tv);
		if (lone) {
			island(c, p, taken, tu, 2, tu, 2, 1.0);
			rock(c, p, tu - 1, 1, new int[][] {{0, 2, 0}, {3, 4, 2}, {0, 2, 1}}, 1);
		}
		tsukubai(c, p, taken, w - 2, 3);
		steppingStones(c, p, taken, cu, tu, tv);
		rake(c, p, taken, white, lu, lv1, lv2, tu, tv);
	}

	// ------------------------------------------------------------------ border, fence, entry

	private static BlockState curb(Plot p, int u, int v) {
		return switch (p.hash(u, 0, v, 6)) {
			case 0 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			case 1 -> b(Blocks.COBBLED_DEEPSLATE);
			case 2 -> b(Blocks.POLISHED_DEEPSLATE);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	private static boolean isPost(int i, int len) {
		return i == 0 || i == len - 1 || i % 3 == 0;
	}

	private static void border(CitadelCanvas c, Plot p, boolean[][] taken, int cu) {
		int w = p.w();
		int d = p.d();
		BlockState fence = b(Blocks.BAMBOO_FENCE);
		BlockState post = b(Blocks.BAMBOO_BLOCK);
		BlockState cap = slab(Blocks.BAMBOO_MOSAIC_SLAB);
		for (int u = 0; u < w; u++) {
			for (int v = 0; v < d; v++) {
				boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
				if (!edge) {
					continue;
				}
				taken[u][v] = true;
				boolean gap = v == 0 && (u == cu - 1 || u == cu);
				if (gap) {
					p.set(c, u, 0, v, slab(Blocks.STONE_BRICK_SLAB)); // half step: ground → slab → garden floor
					continue;
				}
				p.set(c, u, 0, v, curb(p, u, v));
				if (v == 0 && u > 0 && u < w - 1) {
					frontPlanting(c, p, u);
					continue;
				}
				// fence on the sides and the back, posts every 3 cells
				boolean back = v == d - 1;
				boolean side = u == 0 || u == w - 1;
				int i = back ? u : v;
				int len = back ? w : d;
				boolean pst = isPost(i, len) || (back && side) || (side && v == 0);
				if (side && v == 0) {
					pst = true;
				}
				// the back wall is tall (3 + cap); the sides stay low (1 + cap) so the garden reads from outside
				int top = back ? 3 : 1;
				if (pst) {
					int ph = back ? 3 : (v == 0 || v == d - 1 ? 2 : 1);
					for (int y = 1; y <= ph; y++) {
						p.set(c, u, y, v, post);
					}
					p.set(c, u, ph + 1, v, cap);
				} else {
					BlockState f = fence;
					if (back) {
						f = f.setValue(BlockStateProperties.WEST, true).setValue(BlockStateProperties.EAST, true);
					} else {
						f = f.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
					}
					for (int y = 1; y <= top - (back ? 1 : 0); y++) {
						p.set(c, u, y, v, f);
					}
					p.set(c, u, top + (back ? 0 : 1), v, cap);
				}
			}
		}
		// entry pillars: polished deepslate wall with a lantern on top, flanking the gap
		for (int u : new int[] {cu - 2, cu + 1}) {
			p.set(c, u, 1, 0, b(Blocks.POLISHED_DEEPSLATE_WALL));
			p.set(c, u, 2, 0, b(Blocks.POLISHED_DEEPSLATE_WALL));
			p.set(c, u, 3, 0, b(Blocks.LANTERN));
		}
	}

	/** Low planting along the front curb: moss, azaleas, ferns, pebbles. */
	private static void frontPlanting(CitadelCanvas c, Plot p, int u) {
		switch (p.hash(u, 1, 0, 5)) {
			case 0 -> p.set(c, u, 1, 0, b(Blocks.AZALEA));
			case 1 -> p.set(c, u, 1, 0, b(Blocks.MOSS_CARPET));
			case 2 -> p.set(c, u, 1, 0, b(Blocks.FERN));
			case 3 -> p.set(c, u, 1, 0, slab(Blocks.MOSSY_COBBLESTONE_SLAB));
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ rocks

	/**
	 * A rock from a half-block height map ({@code hm[v][u]}, odd = ends in a slab, or in a stair
	 * facing the higher neighbour). Materials are mixed per cell by hash.
	 */
	private static void rock(CitadelCanvas c, Plot p, int u0, int v0, int[][] hm, int baseY) {
		for (int j = 0; j < hm.length; j++) {
			for (int i = 0; i < hm[j].length; i++) {
				int hgt = hm[j][i];
				if (hgt <= 0) {
					continue;
				}
				int u = u0 + i;
				int v = v0 + j;
				int full = hgt / 2;
				for (int y = 0; y < full; y++) {
					p.set(c, u, baseY + y, v, b(ROCK[p.hash(u, baseY + y, v, ROCK.length)]));
				}
				int m = p.hash(u, baseY + full, v, ROCK.length);
				if (hgt % 2 == 1) {
					Direction up = null;
					int best = hgt;
					for (Direction dir : LOCAL) {
						int nh = heightAt(hm, i + dir.getStepX(), j + dir.getStepZ());
						if (nh >= hgt + 2 && nh > best) {
							up = dir;
							best = nh;
						}
					}
					p.set(c, u, baseY + full, v, up != null ? stairs(ROCK_STAIRS[m], up) : slab(ROCK_SLAB[m]));
				} else if (p.hash(u, 7, v, 3) == 0) {
					p.set(c, u, baseY + full, v, b(Blocks.MOSS_CARPET)); // moss on top of the full blocks
				}
			}
		}
	}

	private static int heightAt(int[][] hm, int i, int j) {
		return j >= 0 && j < hm.length && i >= 0 && i < hm[j].length ? hm[j][i] : 0;
	}

	/** Shin-gyo-so: a tall standing stone, a low reclining stone and a small one, on island L. */
	private static void rockTriad(CitadelCanvas c, Plot p, int lu, int v0) {
		rock(c, p, lu - 1, v0, new int[][] {
				{0, 6, 3},
				{2, 7, 4},
				{1, 3, 0}}, 1);
		// a flat stone beside it
		rock(c, p, lu + 1, v0 + 3, new int[][] {{2, 2}, {1, 3}}, 1);
		p.set(c, lu - 1, 1, v0 + 1, b(Blocks.FERN));
	}

	// ------------------------------------------------------------------ islands

	private static double distSeg(double x, double z, double x1, double z1, double x2, double z2) {
		double dx = x2 - x1;
		double dz = z2 - z1;
		double len2 = dx * dx + dz * dz;
		double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - x1) * dx + (z - z1) * dz) / len2));
		return Math.hypot(x - (x1 + t * dx), z - (z1 + t * dz));
	}

	/** A moss island: the cells within {@code rad} (plus a little noise) of a segment. */
	private static void island(CitadelCanvas c, Plot p, boolean[][] taken, int u1, int v1, int u2, int v2, double rad) {
		for (int u = 1; u < p.w() - 1; u++) {
			for (int v = 1; v < p.d() - 1; v++) {
				if (taken[u][v]) {
					continue;
				}
				double dist = distSeg(u, v, u1, v1, u2, v2) + (p.hash(u, 3, v, 5) - 2) * 0.18;
				if (dist <= rad) {
					taken[u][v] = true;
					p.set(c, u, 0, v, dist > rad - 0.7 && p.hash(u, 4, v, 3) == 0 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.MOSS_BLOCK));
				}
			}
		}
	}

	// ------------------------------------------------------------------ stone lantern

	/** Toro: stepped pedestal, wall post, lantern, 3×3 roof of stairs rising to the middle, finial. */
	private static void toro(CitadelCanvas c, Plot p, int u, int v) {
		BlockState brick = b(Blocks.STONE_BRICKS);
		BlockState mossy = b(Blocks.MOSSY_STONE_BRICKS);
		p.set(c, u, 0, v, mossy);
		for (Direction dir : LOCAL) {
			p.set(c, u + dir.getStepX(), 0, v + dir.getStepZ(), stairs(Blocks.STONE_BRICK_STAIRS, dir.getOpposite()));
		}
		p.set(c, u, 1, v, b(Blocks.STONE_BRICK_WALL));
		p.set(c, u, 2, v, b(Blocks.LANTERN));
		p.set(c, u, 3, v, brick);
		for (Direction dir : LOCAL) {
			p.set(c, u + dir.getStepX(), 3, v + dir.getStepZ(), stairs(Blocks.STONE_BRICK_STAIRS, dir.getOpposite()));
		}
		for (int du = -1; du <= 1; du += 2) {
			for (int dv = -1; dv <= 1; dv += 2) {
				p.set(c, u + du, 3, v + dv, slab(Blocks.STONE_BRICK_SLAB));
			}
		}
		p.set(c, u, 4, v, b(Blocks.STONE_BRICK_WALL));
		// corners of the pedestal: moss carpet and ferns
		p.set(c, u - 1, 1, v - 1, b(Blocks.FERN));
		p.set(c, u + 1, 1, v + 1, b(Blocks.MOSS_CARPET));
	}

	// ------------------------------------------------------------------ bonsai

	/** A cloud-pruned cherry on a rock: two puffs of blossom, one on a side branch. */
	private static void bonsai(CitadelCanvas c, Plot p, int u, int v) {
		rock(c, p, u - 1, v - 1, new int[][] {{0, 1, 0}, {1, 2, 1}, {0, 1, 0}}, 1);
		BlockState log = b(Blocks.CHERRY_LOG);
		BlockState leaf = leaves(Blocks.CHERRY_LEAVES);
		p.set(c, u, 2, v, log);
		p.set(c, u, 3, v, log);
		// main cloud
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				if (Math.abs(du) + Math.abs(dv) <= 1 || p.hash(u + du, 5, v + dv, 2) == 0) {
					p.set(c, u + du, 4, v + dv, leaf);
				}
			}
		}
		for (Direction dir : LOCAL) {
			p.set(c, u + dir.getStepX(), 3, v + dir.getStepZ(), p.hash(u, 6, v + dir.ordinal(), 2) == 0 ? leaf : b(Blocks.AIR));
		}
		// side branch towards the sand, with a smaller puff
		p.set(c, u + 1, 3, v, b(Blocks.CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		p.set(c, u + 2, 3, v, b(Blocks.CHERRY_LOG));
		p.set(c, u + 2, 4, v, leaf);
		p.set(c, u + 3, 3, v, leaf);
		p.set(c, u + 2, 3, v + 1, leaf);
		p.set(c, u + 2, 3, v - 1, leaf);
		p.set(c, u + 1, 4, v, leaf);
	}

	// ------------------------------------------------------------------ tsukubai

	/** A basin against the fence fed by a bamboo pipe, with a flat front stone. */
	private static void tsukubai(CitadelCanvas c, Plot p, boolean[][] taken, int u, int v) {
		taken[u][v] = true;
		taken[u - 1][v] = true;
		p.set(c, u, 0, v, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		p.set(c, u - 1, 0, v, b(Blocks.POLISHED_ANDESITE));
		p.set(c, u - 1, 1, v, slab(Blocks.POLISHED_ANDESITE_SLAB));
		p.set(c, u, 1, v, b(Blocks.BAMBOO_BLOCK).setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		p.set(c, u, 0, v - 1, b(Blocks.MOSS_BLOCK));
		p.set(c, u, 1, v - 1, b(Blocks.FERN));
		p.set(c, u, 0, v + 1, b(Blocks.MOSS_BLOCK));
		p.set(c, u, 1, v + 1, b(Blocks.AZALEA));
		taken[u][v - 1] = true;
		taken[u][v + 1] = true;
	}

	// ------------------------------------------------------------------ stepping stones

	private static void steppingStones(CitadelCanvas c, Plot p, boolean[][] taken, int cu, int tu, int tv) {
		int sv = 1;
		int ev = Math.max(sv + 2, tv - 3);
		int su = cu;
		int eu = Math.min(tu, p.w() - 3);
		int n = ev - sv;
		int last = -1;
		for (int i = 0; i <= n; i++) {
			double t = n == 0 ? 0 : (double) i / n;
			int u = (int) Math.round(su + (eu - su) * t + Math.sin(t * Math.PI * 2) * 1.0);
			int v = sv + i;
			for (int k = 0; k < (p.hash(u, 8, v, 3) == 0 ? 2 : 1); k++) {
				int uu = u + k;
				if (uu < 1 || uu >= p.w() - 1 || taken[uu][v]) {
					continue;
				}
				taken[uu][v] = true;
				Block base = switch (p.hash(uu, 9, v, 4)) {
					case 0 -> Blocks.ANDESITE;
					case 1 -> Blocks.STONE;
					case 2 -> Blocks.POLISHED_ANDESITE;
					default -> Blocks.SMOOTH_STONE;
				};
				p.set(c, uu, 0, v, b(base));
				if (p.hash(uu, 10, v, 2) == 0) {
					p.set(c, uu, 1, v, slab(Blocks.STONE_SLAB));
				}
			}
			last = u;
		}
	}

	// ------------------------------------------------------------------ rake pattern

	private static void rake(CitadelCanvas c, Plot p, boolean[][] taken, boolean white, int lu, int lv1, int lv2, int tu, int tv) {
		BlockState groove = white ? b(Blocks.CALCITE) : b(Blocks.SAND);
		BlockState ridgeBase = white ? b(Blocks.CALCITE) : b(Blocks.SAND);
		BlockState ridgeCap = white ? slab(Blocks.POLISHED_DIORITE_SLAB) : slab(Blocks.SMOOTH_SANDSTONE_SLAB);
		for (int u = 1; u < p.w() - 1; u++) {
			for (int v = 1; v < p.d() - 1; v++) {
				if (taken[u][v]) {
					continue;
				}
				double dmin = Math.min(distSeg(u, v, lu, lv1, lu, lv2), Math.hypot(u - tu, v - tv));
				boolean ridge = dmin <= 4.6 ? Math.round(dmin) % 2 == 0 : v % 2 == 0;
				p.set(c, u, 0, v, ridge && !white ? b(Blocks.SMOOTH_SANDSTONE) : groove);
				if (ridge) {
					p.set(c, u, 1, v, ridgeCap);
				} else if (p.hash(u, 11, v, 14) == 0) {
					p.set(c, u, 1, v, b(Blocks.STONE_BUTTON).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
				}
			}
		}
	}
}
