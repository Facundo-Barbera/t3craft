package dev.t3craft.hq.citadel.catalog.plazacenter;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A monumental sundial that reads like a clock: one pale disc with a continuous dark bezel, on a stepped platform
 * with a dark plinth and a light cornice, climbed on foot by a 3-wide stair on each of the four sides, in the middle
 * of a paved plaza whose benches sit right at the foot of the platform.
 *
 * <ul>
 * <li><b>Dial:</b> a single pale material (calcite, smooth quartz or quartz block by seed) inside a 2-thick bezel of
 * dark stone that is closed all the way round. The hours are gold (or chiselled quartz) dots in the bezel, the four
 * quarters are 3-wide plates in the bezel with a short stroke that reaches into the disc; 12 o'clock is the back and
 * its stroke is longer. No numerals, no checker: nothing inside the disc but the marks at its edge.</li>
 * <li><b>Gnomon:</b> a thin dark blade, one block wide, that climbs half a block per column (slab, block, slab,
 * block...) towards 12 o'clock and ends in a vertical edge with a gold ball and a rod: a right triangle seen in profile.
 * Its height follows the plot height (10 in the gallery, 14 in the city).</li>
 * <li><b>Platform:</b> square or octagonal ziggurat by seed (octagon from 19 wide), 1 to 3 tiers by size and seed. Each tier is
 * a full-block wall (dark at the foot, stone above), the top tier carries a ring of light slabs as a cornice and the four
 * 3-wide stairs are light. Nothing floats: every wall stands on a solid core.</li>
 * <li><b>Use:</b> a ring of benches (cherry, spruce or dark oak) at the foot of the platform facing the dial, a planter at
 * the end of each run, a lamp post on each corner of the plot, the four axes free; octagon variants get a hedge on
 * the diagonals and a flower bed in each corner.</li>
 * </ul>
 *
 * <pre>
 *  top view (front at the bottom, 12 o'clock at the back)
 *      [lamp] planter bench bench . bench bench planter [lamp]
 *      bench   ###### cornice, dark plinth ######   bench
 *      bench   #   (.  .  XII  .  .)  #             bench
 *      .       #  ( .   gnomon blade  . )  #        .   (3-wide stair on each side)
 *      bench   #   (.  .  VI   .  .)  #             bench
 * </pre>
 *
 * Size: 9–23 × 9–23 × 8+ (the plaza centres of the city are 13–21 wide at scale 2).
 */
public final class PlazaCenterMonumentalSundial implements Variant {
	@Override
	public String id() {
		return "plaza_center_monumental_sundial";
	}

	@Override
	public Family family() {
		return Family.PLAZA_CENTER;
	}

	@Override
	public int minW() {
		return 9;
	}

	@Override
	public int minD() {
		return 9;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 23;
	}

	@Override
	public int maxD() {
		return 23;
	}

	/** Everything the cell passes need, computed once from the plot and the seed. */
	private static final class Cfg {
		int w;
		int d;
		int cu;
		int cv;
		int half;
		boolean oct; // octagonal platform
		int tiers;
		int ptop; // top plate: metric distance <= ptop
		int pl; // outermost wall ring
		int pb; // bench ring (-1: none)
		int rd; // dial radius
		double ringIn; // the bezel is r in (ringIn, rd + 0.5]
		int yd; // layer of the dial block
		int g; // half length of the gnomon blade, in blocks of height
		Block dial;
		Block ring;
		Block ringAlt;
		Block mark;
		Block wallD;
		Block wallD2;
		Block wallM;
		Block wallM2;
		Block light; // top tier of the platform: the light cornice
		Block light2;
		Block bench;
		Block finBlock;
		Block finSlab;
		boolean[][] mk;
	}

	private static Direction out(int dx, int dz) {
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	private static void s(CitadelCanvas c, Plot p, int u, int y, int v, BlockState st) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, st);
		}
	}

	/** Distance in rings from the centre: Chebyshev for the square platform, with the corners cut for the octagon. */
	private static int metric(Cfg k, int a, int bb) {
		int cheb = Math.max(a, bb);
		return k.oct ? Math.max(cheb, (2 * (a + bb) + 2) / 3) : cheb;
	}

	private static Cfg config(Plot p) {
		Cfg k = new Cfg();
		k.w = p.w();
		k.d = p.d();
		k.cu = k.w / 2;
		k.cv = k.d / 2;
		int hf = Math.min(Math.min(k.cu, k.w - 1 - k.cu), Math.min(k.cv, k.d - 1 - k.cv));
		k.half = hf;
		Random rng = p.rng(0x5D1E0L);
		boolean boost = rng.nextInt(2) == 1;
		int dk = rng.nextInt(3);
		int rk = rng.nextInt(2);
		int mkk = rng.nextInt(2);
		int wk = rng.nextInt(2);
		int mdk = rng.nextInt(2);
		int bk = rng.nextInt(3);
		int fk = rng.nextInt(3);
		int ck = rng.nextInt(2);
		boolean octBit = rng.nextInt(2) == 1;

		k.oct = hf >= 9 && octBit;
		k.pb = hf >= 6 ? hf - 1 : -1;
		k.pl = hf >= 6 ? hf - 2 : hf - 1;
		int tiers = hf >= 9 ? 2 : 1;
		if (hf >= 10 && boost) {
			tiers++;
		}
		tiers = Math.min(tiers, Math.max(1, p.h() - 6));
		tiers = Math.min(tiers, Math.max(1, k.pl - 3));
		k.tiers = tiers;
		k.ptop = k.pl - tiers;
		k.rd = k.oct ? k.ptop - 1 : k.ptop;
		k.yd = tiers;
		k.ringIn = k.rd >= 4 ? k.rd - 1.4 : k.rd - 0.9; // 2-thick bezel, 1-thick on the small dials
		int surface = k.yd + 1;
		// a modest blade: about half the dial radius high, never taller than the plot allows
		k.g = Math.max(1, Math.min(Math.min(k.rd - 1, p.h() - 2 - surface), Math.max(2, (k.rd + 1) / 2)));

		k.dial = switch (dk) {
			case 0 -> Blocks.CALCITE;
			case 1 -> Blocks.SMOOTH_QUARTZ;
			default -> Blocks.QUARTZ_BLOCK;
		};
		if (rk == 0) {
			k.ring = Blocks.POLISHED_DEEPSLATE;
			k.ringAlt = Blocks.DEEPSLATE_TILES;
		} else {
			k.ring = Blocks.POLISHED_BLACKSTONE_BRICKS;
			k.ringAlt = Blocks.POLISHED_BLACKSTONE;
		}
		k.mark = mkk == 0 ? Blocks.GOLD_BLOCK : Blocks.RAW_GOLD_BLOCK; // both read on the dark bezel and on the pale disc
		if (wk == 0) {
			k.wallD = Blocks.DEEPSLATE_BRICKS;
			k.wallD2 = Blocks.DEEPSLATE_TILES;
		} else {
			k.wallD = Blocks.POLISHED_BLACKSTONE_BRICKS;
			k.wallD2 = Blocks.POLISHED_BLACKSTONE;
		}
		if (mdk == 0) {
			k.wallM = Blocks.STONE_BRICKS;
			k.wallM2 = Blocks.MOSSY_STONE_BRICKS;
		} else {
			k.wallM = Blocks.POLISHED_ANDESITE;
			k.wallM2 = Blocks.STONE_BRICKS;
		}
		if (ck == 0) {
			k.light = Blocks.SMOOTH_QUARTZ;
			k.light2 = Blocks.QUARTZ_BRICKS;
		} else {
			k.light = Blocks.POLISHED_DIORITE;
			k.light2 = Blocks.CALCITE;
		}
		k.bench = switch (bk) {
			case 0 -> Blocks.CHERRY_STAIRS;
			case 1 -> Blocks.SPRUCE_STAIRS;
			default -> Blocks.DARK_OAK_STAIRS;
		};
		switch (fk) {
			case 0 -> {
				k.finBlock = Blocks.POLISHED_DEEPSLATE;
				k.finSlab = Blocks.POLISHED_DEEPSLATE_SLAB;
			}
			case 1 -> {
				k.finBlock = Blocks.DEEPSLATE_TILES;
				k.finSlab = Blocks.DEEPSLATE_TILE_SLAB;
			}
			default -> {
				k.finBlock = Blocks.POLISHED_BLACKSTONE_BRICKS;
				k.finSlab = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB;
			}
		}
		return k;
	}

	// ------------------------------------------------------------------ dial marks

	/**
	 * Fills {@code k.mk}: gold dots for the twelve hours inside the bezel, bigger plates for the four quarters and a short
	 * stroke from each quarter into the disc (longer at 12 o'clock, which is the back of the plot, +v).
	 */
	private static void marks(Cfg k) {
		k.mk = new boolean[k.w][k.d];
		double ringIn = k.ringIn;
		double cardW = k.rd >= 6 ? 1.5 : 0.55; // 3-wide quarter plates only where the bezel is long enough for them
		for (int dv = -k.rd; dv <= k.rd; dv++) {
			for (int du = -k.rd; du <= k.rd; du++) {
				int u = k.cu + du;
				int v = k.cv + dv;
				if (u < 0 || u >= k.w || v < 0 || v >= k.d) {
					continue;
				}
				double r = Math.hypot(du, dv);
				if (r > k.rd + 0.5 || r < 1.0) {
					continue;
				}
				boolean inRing = r > ringIn;
				for (int h = 0; h < 12; h++) {
					double th = Math.toRadians(30.0 * h);
					double ou = Math.sin(th);
					double ov = Math.cos(th);
					double t = du * ou + dv * ov;
					if (t <= 0) {
						continue;
					}
					double perp = Math.abs(du * ov - dv * ou);
					boolean card = h % 3 == 0;
					if (!card && k.rd < 5) {
						continue; // on a small dial only the four quarters are marked (twelve dots would be a checker)
					}
					if (inRing) {
						// gold only on the outer layer of the bezel (the inner one stays dark, so the ring is continuous);
						// the quarters are 3 wide there and 1 wide on the inner layer
						boolean outer = r > k.rd - 0.8;
						double wide = card ? (outer ? cardW : 0.55) : (outer ? 0.55 : -1);
						if (perp < wide) {
							k.mk[u][v] = true;
							break;
						}
					} else if (card && k.rd >= 4 && r > k.rd - (h == 0 ? 3.6 : 2.6) && perp < 0.55) {
						k.mk[u][v] = true;
						break;
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ build

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Cfg k = config(p);
		marks(k);
		for (int v = 0; v < k.d; v++) {
			for (int u = 0; u < k.w; u++) {
				int dx = u - k.cu;
				int dz = v - k.cv;
				int a = Math.abs(dx);
				int bb = Math.abs(dz);
				int m = metric(k, a, bb);
				int eu = Math.min(u, k.w - 1 - u);
				int ev = Math.min(v, k.d - 1 - v);
				if (m <= k.ptop) {
					top(c, p, k, u, v, dx, dz);
				} else if (m <= k.pl) {
					step(c, p, k, u, v, dx, dz, a, bb, m);
				} else if (Math.min(eu, ev) == 0) {
					edge(c, p, k, u, v, a, bb, eu, ev);
				} else if (k.pb >= 0 && m == k.pb) {
					ring(c, p, k, u, v, dx, dz, a, bb);
				} else if (k.oct && k.pb >= 0 && m > k.pb && Math.max(a, bb) <= k.half - 1) {
					bed(c, p, k, u, v, a, bb);
				} else {
					apron(c, p, k, u, v, a, bb);
				}
			}
		}
		gnomon(c, p, k);
	}

	/** Solid hidden core under a platform cell, up to (not including) layer {@code y}. */
	private static void core(CitadelCanvas c, Plot p, int u, int y0, int v, int y) {
		for (int yy = y0; yy < y; yy++) {
			s(c, p, u, yy, v, yy == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.STONE_BRICKS));
		}
	}

	/** The top plate: one pale disc, a continuous dark bezel with the marks in it, grey corners round it. */
	private void top(CitadelCanvas c, Plot p, Cfg k, int u, int v, int dx, int dz) {
		int y = k.yd;
		core(c, p, u, 0, v, y);
		double r = Math.hypot(dx, dz);
		BlockState st;
		if (r > k.rd + 0.5) {
			// [palette] mid grey corners: the pale disc and its dark bezel stand out from them
			st = p.hash(u, y, v, 4) == 0 ? b(Blocks.STONE_BRICKS) : b(Blocks.POLISHED_ANDESITE);
		} else if (k.mk[u][v]) {
			st = b(k.mark);
		} else if (r > k.ringIn) {
			st = p.hash(u, y, v, 5) == 0 ? b(k.ringAlt) : b(k.ring);
		} else {
			st = b(k.dial);
		}
		s(c, p, u, y, v, st);
	}

	/**
	 * A ring of the platform. Axis cells are a light 3-wide stair that rises to the dial; the rest is a full-block wall,
	 * dark at the foot and stone above, with a light slab cornice on the top tier.
	 */
	private void step(CitadelCanvas c, Plot p, Cfg k, int u, int v, int dx, int dz, int a, int bb, int m) {
		int st = m - k.ptop; // 1 = next to the top plate, tiers = foot
		int y = k.yd - st + 1;
		core(c, p, u, 0, v, y);
		if (Math.min(a, bb) <= 1 && a != bb) {
			Direction in = a > bb ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
			Block sb = Math.min(a, bb) == 0 ? Blocks.SMOOTH_QUARTZ_STAIRS : Blocks.POLISHED_DIORITE_STAIRS;
			s(c, p, u, y, v, stairs(sb, in));
			return;
		}
		BlockState wall;
		if (st == 1) {
			// [finish] light cornice: the top tier is flush with the dial, so it never hides it from the plaza
			wall = p.hash(u, y, v, 5) == 0 ? b(k.light2) : b(k.light);
		} else if (st == k.tiers) {
			// [palette] dark plinth
			wall = p.hash(u, y, v, 4) == 0 ? b(k.wallD2) : b(k.wallD);
		} else {
			wall = p.hash(u, y, v, 5) == 0 ? b(k.wallM2) : b(k.wallM);
		}
		s(c, p, u, y, v, wall);
	}

	/** The single step up to the podium on the plot's edge, low side out: stairs ascend towards the plaza. */
	private void edge(CitadelCanvas c, Plot p, Cfg k, int u, int v, int a, int bb, int eu, int ev) {
		Direction in = eu <= ev ? (u < k.cu ? Direction.EAST : Direction.WEST) : (v < k.cv ? Direction.SOUTH : Direction.NORTH);
		boolean axis = a <= 1 || bb <= 1;
		Block sb = axis ? Blocks.POLISHED_DIORITE_STAIRS : p.hash(u, 0, v, 5) == 0 ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS;
		s(c, p, u, 0, v, stairs(sb, in));
	}

	/** Floor of the plaza outside the ring: a light cross on the axes, grey paving elsewhere. */
	private static BlockState cross(int a, int bb) {
		return Math.min(a, bb) == 0 ? b(Blocks.SMOOTH_STONE) : b(Blocks.POLISHED_DIORITE);
	}

	private void apron(CitadelCanvas c, Plot p, Cfg k, int u, int v, int a, int bb) {
		if (a <= 1 || bb <= 1) {
			s(c, p, u, 0, v, cross(a, bb));
			return;
		}
		int h = p.hash(u, 0, v, 8);
		s(c, p, u, 0, v, h == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : h == 1 ? b(Blocks.MOSSY_STONE_BRICKS) : h < 4 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.STONE_BRICKS));
	}

	private boolean isCorner(Cfg k, int a, int bb) {
		return k.pb >= 0 && a == k.half - 1 && bb == k.half - 1;
	}

	/** A lamp post: polished base, two wall posts, a lantern. */
	private static void lamp(CitadelCanvas c, Plot p, int u, int v) {
		s(c, p, u, 1, v, b(Blocks.POLISHED_DEEPSLATE));
		s(c, p, u, 2, v, b(Blocks.POLISHED_DEEPSLATE_WALL));
		s(c, p, u, 3, v, b(Blocks.POLISHED_DEEPSLATE_WALL));
		s(c, p, u, 4, v, b(Blocks.LANTERN));
	}

	/** The bench ring at the foot of the platform: dark floor, benches facing the dial, planters at the ends of the runs. */
	private void ring(CitadelCanvas c, Plot p, Cfg k, int u, int v, int dx, int dz, int a, int bb) {
		int t = Math.min(a, bb);
		if (t <= 1) {
			s(c, p, u, 0, v, cross(a, bb));
			return;
		}
		s(c, p, u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_TILES));
		boolean straight = Math.max(a, bb) == k.pb;
		if (!straight) {
			planter(c, p, u, v, false);
			return;
		}
		if (!k.oct && t == k.pb) {
			lamp(c, p, u, v);
			return;
		}
		int pt = k.oct ? k.pb / 2 : k.pb - 1; // the planter that ends each run (octagon: last straight cell)
		if (t == pt) {
			planter(c, p, u, v, true);
		} else if (t < pt) {
			s(c, p, u, 1, v, stairs(k.bench, out(dx, dz)));
		} else {
			planter(c, p, u, v, false);
		}
	}

	/** A raised planter: moss under a bush of azalea; the end-of-run ones a block taller. */
	private void planter(CitadelCanvas c, Plot p, int u, int v, boolean tall) {
		s(c, p, u, 0, v, b(Blocks.MOSS_BLOCK));
		s(c, p, u, 1, v, p.hash(u, 1, v, 2) == 0 ? CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES) : CitadelCanvas.leaves(Blocks.AZALEA_LEAVES));
		if (tall) {
			s(c, p, u, 2, v, CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		}
	}

	/** A corner flower bed outside the diagonal hedge of the octagon variant; the corner cell is a lamp post. */
	private void bed(CitadelCanvas c, Plot p, Cfg k, int u, int v, int a, int bb) {
		s(c, p, u, 0, v, b(Blocks.GRASS_BLOCK));
		if (isCorner(k, a, bb)) {
			lamp(c, p, u, v);
			return;
		}
		BlockState st = switch (p.hash(u, 1, v, 6)) {
			case 0 -> CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES);
			case 1 -> CitadelCanvas.leaves(Blocks.AZALEA_LEAVES);
			case 2 -> b(Blocks.PINK_PETALS);
			case 3 -> b(Blocks.ALLIUM);
			case 4 -> b(Blocks.CORNFLOWER);
			default -> b(Blocks.OXEYE_DAISY);
		};
		s(c, p, u, 1, v, st);
	}

	// ------------------------------------------------------------------ gnomon

	/**
	 * A thin blade, one block wide, that climbs half a block per column towards 12 o'clock (+v): slab, block, slab,
	 * block... It ends in a vertical edge with a gold ball and a lightning rod, so it reads as a triangle in profile.
	 */
	private void gnomon(CitadelCanvas c, Plot p, Cfg k) {
		int cu = k.cu;
		int base = k.yd + 1; // first free layer above the dial
		int n = 2 * k.g;
		for (int j = 0; j < n; j++) {
			int v = k.cv - k.g + j;
			int halves = j + 1;
			int full = halves / 2;
			for (int y = 0; y < full; y++) {
				s(c, p, cu, base + y, v, b(k.finBlock));
			}
			if (halves % 2 == 1) {
				s(c, p, cu, base + full, v, slab(k.finSlab));
			}
		}
		// a gold cap on the last column and a fine spire of two rods instead of a ball
		int top = k.cv + k.g - 1;
		s(c, p, cu, base + k.g - 1, top, b(Blocks.GOLD_BLOCK));
		BlockState rod = Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState();
		s(c, p, cu, base + k.g, top, rod);
		s(c, p, cu, base + k.g + 1, top, rod);
	}
}
