package dev.t3craft.hq.citadel.catalog.plazacenter;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A monumental sundial that reads like a clock: a big round dial of calcite or quartz, raised on a stepped
 * platform that is climbed on foot from every side, in the middle of a paved plaza. The whole plot is a one-block
 * podium with a stair edge, paved in rings round the dial (polished andesite, stone brick, a deepslate frame) and a
 * light 3-wide cross that leads straight to the platform from the four sides.
 *
 * <ul>
 * <li><b>Dial:</b> a dark bezel (deepslate) with a gold or deepslate tick for each of the twelve hours, longer ticks
 * on the quarters and the numerals XII, III, VI, IX spelled in blocks (radial, readable from outside) when the dial is
 * big enough (radius 6 and up).</li>
 * <li><b>Gnomon:</b> a tall fin of stairs that climbs at 45 degrees from the centre to a vertical pier with a gold
 * ball and a lightning rod; quartz, polished diorite or smooth sandstone by seed. The dial is calcite, quartz or quartz brick
 * by seed.</li>
 * <li><b>Use:</b> cherry benches on arcs round the platform facing the dial, a lamp post on each corner of the top and a flower
 * bed in each corner; the axes stay free.</li>
 * </ul>
 *
 * <pre>
 *  top view (front at the top, 12 o'clock)       side view
 *   [bed] . . bench . . . . bench . [bed]              ╿ rod
 *    .   (lamp)  . XII . (lamp)   .                    ● gold
 *    bench   ___/ ticks \___  bench                    █ pier
 *    III   |  gnomon (fin)  |  IX                    ╱█ fin, 1 step per row
 *    bench   ‾‾‾\ VI  /‾‾‾  bench              ____╱ dial ____ platform steps ___ podium
 *   [bed] . . bench . . . . bench . [bed]
 * </pre>
 *
 * Size: 9–23 × 9–23 × 8+ (the plaza centres of the city are 13–21 wide at scale 2). The platform is a square ziggurat
 * (1 step, 2 from half-width 11) whose top square carries the dial.
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
		int pl; // platform half-size (square, Chebyshev)
		int ptop; // half-size of the top square that carries the dial
		int rd; // dial radius
		int tiers;
		int yd; // plot layer of the dial
		int pw; // flower bed size
		int lg; // half length of the gnomon
		boolean gold;
		Block dial;
		Block dial2;
		Block gStair;
		Block gBlock;
		Block markT;
		Block numM;
		byte[][] mark;
	}

	private static Direction out(int dx, int dz) {
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	private static void s(CitadelCanvas c, Plot p, int u, int y, int v, BlockState st) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, st);
		}
	}

	private static Cfg config(Plot p) {
		Cfg k = new Cfg();
		k.w = p.w();
		k.d = p.d();
		k.cu = k.w / 2;
		k.cv = k.d / 2;
		k.half = Math.min(Math.min(k.cu, k.w - 1 - k.cu), Math.min(k.cv, k.d - 1 - k.cv));
		k.pw = k.half >= 13 ? 3 : k.half >= 9 ? 2 : 1;
		k.tiers = k.half >= 11 ? 2 : 1;
		k.ptop = Math.max(2, k.half - 1 - k.pw - k.tiers);
		k.pl = k.ptop + k.tiers;
		k.rd = k.ptop;
		k.yd = k.tiers;
		int lg = Math.min(4, Math.max(k.rd >= 5 ? 2 : 1, (k.rd + 2) / 3));
		lg = Math.min(lg, k.rd - 1);
		while (lg > 1 && k.yd + 2 * lg + 4 > p.h() - 1) {
			lg--;
		}
		k.lg = lg;
		Random rng = p.rng(0x5D1A1L);
		int dk = rng.nextInt(3);
		int gk = rng.nextInt(3);
		k.gold = rng.nextBoolean();
		switch (dk) {
			case 0 -> {
				k.dial = Blocks.CALCITE;
				k.dial2 = Blocks.POLISHED_DIORITE;
			}
			case 1 -> {
				k.dial = Blocks.SMOOTH_QUARTZ;
				k.dial2 = Blocks.CALCITE;
			}
			default -> {
				k.dial = Blocks.QUARTZ_BRICKS;
				k.dial2 = Blocks.SMOOTH_QUARTZ;
			}
		}
		switch (gk) {
			case 0 -> {
				k.gStair = Blocks.QUARTZ_STAIRS;
				k.gBlock = Blocks.SMOOTH_QUARTZ;
			}
			case 1 -> {
				k.gStair = Blocks.POLISHED_DIORITE_STAIRS;
				k.gBlock = Blocks.POLISHED_DIORITE;
			}
			default -> {
				k.gStair = Blocks.SMOOTH_SANDSTONE_STAIRS;
				k.gBlock = Blocks.SMOOTH_SANDSTONE;
			}
		}
		k.markT = k.gold ? Blocks.GOLD_BLOCK : Blocks.POLISHED_DEEPSLATE;
		k.numM = k.gold ? Blocks.POLISHED_DEEPSLATE : Blocks.GOLD_BLOCK;
		return k;
	}

	// ------------------------------------------------------------------ dial marks (hours and numerals)

	private static final String[] WORDS = {"XII", "III", "VI", "IX"};
	private static final int[][] CARD = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

	/** Letter bitmaps, 3 rows (the top row points to the centre of the dial). */
	private static String[] glyph(char ch) {
		return switch (ch) {
			case 'V' -> new String[] {"#.#", "#.#", ".#."};
			case 'X' -> new String[] {"#.#", ".#.", "#.#"};
			default -> new String[] {"#", "#", "#"};
		};
	}

	/** Marks the numeral of the quarter in direction (ou, ov) if it fits on the dial; true when it did. */
	private static boolean numeral(Cfg k, int ou, int ov, String word) {
		String[] rows = {"", "", ""};
		for (int i = 0; i < word.length(); i++) {
			String[] g = glyph(word.charAt(i));
			for (int r = 0; r < 3; r++) {
				rows[r] += (i > 0 ? "." : "") + g[r];
			}
		}
		int width = rows[0].length();
		int c0 = (width - 1) / 2;
		int rIn = k.lg + 1;
		List<int[]> cells = new ArrayList<>();
		for (int ri = 0; ri < 3; ri++) {
			for (int ci = 0; ci < width; ci++) {
				if (rows[ri].charAt(ci) != '#') {
					continue;
				}
				int t = ci - c0;
				int rad = rIn + ri;
				int du = ou * rad + ov * t;
				int dv = ov * rad - ou * t;
				if (Math.hypot(du, dv) > k.rd - 0.1) {
					return false;
				}
				cells.add(new int[] {k.cu + du, k.cv + dv});
			}
		}
		for (int[] cell : cells) {
			if (cell[0] < 0 || cell[0] >= k.w || cell[1] < 0 || cell[1] >= k.d) {
				return false;
			}
		}
		for (int[] cell : cells) {
			k.mark[cell[0]][cell[1]] = 2;
		}
		return true;
	}

	/** Fills {@code k.mark}: 1 = hour tick on the plate, 2 = numeral, 3 = tick on the bezel. */
	private static void marks(Cfg k) {
		k.mark = new byte[k.w][k.d];
		boolean[] hasNum = new boolean[4];
		if (k.rd >= 6) {
			for (int q = 0; q < 4; q++) {
				hasNum[q] = numeral(k, CARD[q][0], CARD[q][1], WORDS[q]);
			}
		}
		for (int dv = -k.rd; dv <= k.rd; dv++) {
			for (int du = -k.rd; du <= k.rd; du++) {
				int u = k.cu + du;
				int v = k.cv + dv;
				if (u < 0 || u >= k.w || v < 0 || v >= k.d || k.mark[u][v] != 0) {
					continue;
				}
				double r = Math.hypot(du, dv);
				if (r > k.rd || r < 2.0) {
					continue;
				}
				for (int h = 0; h < 12; h++) {
					double th = Math.toRadians(30.0 * h);
					double ou = Math.sin(th);
					double ov = -Math.cos(th);
					double t = du * ou + dv * ov;
					if (t <= 0) {
						continue;
					}
					double perp = Math.abs(du * ov - dv * ou);
					boolean card = h % 3 == 0;
					boolean num = card && hasNum[h / 3];
					double wide = 0.55;
					if (perp >= wide) {
						continue;
					}
					if (r > k.rd - 1.0) {
						k.mark[u][v] = 3;
						break;
					}
					if (num) {
						continue;
					}
					if (t >= k.rd - (card ? 2.9 : 1.9)) {
						k.mark[u][v] = 1;
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
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				if (m <= k.ptop) {
					top(c, p, k, u, v, dx, dz);
				} else if (m <= k.pl) {
					step(c, p, k, u, v, dx, dz, m);
				} else {
					podium(c, p, k, u, v, dx, dz, m);
				}
			}
		}
		gnomon(c, p, k);
	}

	/** Solid hidden core under a platform cell, up to (not including) layer {@code y}. */
	private static void core(CitadelCanvas c, Plot p, int u, int v, int y) {
		for (int yy = 0; yy < y; yy++) {
			s(c, p, u, yy, v, yy == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.STONE_BRICKS));
		}
	}

	/** The top square: a round pale dial with a grey bezel in dark corners; a lamp post on each corner. */
	private void top(CitadelCanvas c, Plot p, Cfg k, int u, int v, int dx, int dz) {
		int y = k.yd;
		core(c, p, u, v, y);
		double r = Math.hypot(dx, dz);
		int m = k.mark[u][v];
		BlockState st;
		if (r > k.rd + 0.5) {
			// [palette] dark corners round the pale disc, so the dial reads as a circle
			st = p.hash(u, y, v, 4) == 0 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.POLISHED_DEEPSLATE);
		} else if (m == 2) {
			st = b(k.numM);
		} else if (m == 1) {
			st = b(k.markT);
		} else if (m == 3) {
			st = k.gold ? b(Blocks.GOLD_BLOCK) : b(k.dial);
		} else if (r > k.rd - 1.0) {
			st = b(Blocks.POLISHED_ANDESITE);
		} else if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
			st = b(Blocks.POLISHED_DEEPSLATE);
		} else {
			st = p.hash(u, y, v, 9) == 0 ? b(k.dial2) : b(k.dial);
		}
		s(c, p, u, y, v, st);
		if (Math.abs(dx) == k.ptop && Math.abs(dz) == k.ptop && k.ptop >= 3 && r > k.rd + 0.9) {
			// a lamp post on each corner of the top: polished base, two wall posts, a lantern
			s(c, p, u, y + 1, v, b(Blocks.POLISHED_DEEPSLATE));
			s(c, p, u, y + 2, v, b(Blocks.POLISHED_DEEPSLATE_WALL));
			s(c, p, u, y + 3, v, b(Blocks.POLISHED_DEEPSLATE_WALL));
			s(c, p, u, y + 4, v, b(Blocks.LANTERN));
		}
	}

	/** A square ring of the platform: stairs that rise towards the dial, light on the four axes, blocks on the corners. */
	private void step(CitadelCanvas c, Plot p, Cfg k, int u, int v, int dx, int dz, int m) {
		int st = m - k.ptop; // 1 = next to the dial
		int y = k.yd - st + 1;
		core(c, p, u, v, y);
		int a = Math.abs(dx);
		int bb = Math.abs(dz);
		if (a == bb) {
			s(c, p, u, y, v, b(Blocks.STONE_BRICKS));
			return;
		}
		boolean axis = a <= 1 || bb <= 1;
		Block sb;
		if (axis) {
			sb = Blocks.SMOOTH_QUARTZ_STAIRS;
		} else if (st == k.tiers) {
			sb = p.hash(u, y, v, 6) == 0 ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS;
		} else {
			sb = Blocks.POLISHED_ANDESITE_STAIRS;
		}
		Direction in = a > bb ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
		s(c, p, u, y, v, stairs(sb, in));
	}

	private void podium(CitadelCanvas c, Plot p, Cfg k, int u, int v, int dx, int dz, int m) {
		int eu = Math.min(u, k.w - 1 - u);
		int ev = Math.min(v, k.d - 1 - v);
		int e = Math.min(eu, ev);
		int a = Math.abs(dx);
		int bb = Math.abs(dz);
		if (e == 0) {
			// [depth] the single step up to the podium, low side out: stairs ascend towards the plaza
			Direction in = eu <= ev ? (u < k.cu ? Direction.EAST : Direction.WEST) : (v < k.cv ? Direction.SOUTH : Direction.NORTH);
			boolean axis = a <= 1 || bb <= 1;
			Block sb = axis ? Blocks.POLISHED_DIORITE_STAIRS : p.hash(u, 0, v, 5) == 0 ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS;
			s(c, p, u, 0, v, stairs(sb, in));
			return;
		}
		if (eu >= 1 && eu <= k.pw && ev >= 1 && ev <= k.pw) {
			bed(c, p, k, u, v, eu, ev);
			return;
		}
		paving(c, p, k, u, v, a, bb, m);
		int lim = k.half - k.pw - 1;
		int row = k.half - 1;
		if ((a == row && bb >= 2 && bb <= lim) || (bb == row && a >= 2 && a <= lim)) {
			// straight benches along the four sides, seat towards the dial, back outwards; the 3-wide axes stay free
			s(c, p, u, 1, v, stairs(Blocks.CHERRY_STAIRS, out(dx, dz)));
		}
	}

	/** Pavement in square rings round the platform and a light cross on the axes. */
	private void paving(CitadelCanvas c, Plot p, Cfg k, int u, int v, int a, int bb, int m) {
		BlockState st;
		if (a <= 1 || bb <= 1) {
			st = Math.min(a, bb) == 0 ? b(Blocks.SMOOTH_STONE) : b(Blocks.POLISHED_DIORITE);
		} else {
			int band = (m - k.pl - 1) % 3;
			int h = p.hash(u, 0, v, 8);
			if (band == 0) {
				st = b(Blocks.POLISHED_ANDESITE);
			} else if (band == 1) {
				st = h == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : h == 1 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
			} else {
				st = h == 0 ? b(Blocks.CALCITE) : b(Blocks.POLISHED_DIORITE);
			}
		}
		s(c, p, u, 0, v, st);
	}

	/** A corner flower bed: brick rim on the plaza side, grass, azalea and flowers. */
	private void bed(CitadelCanvas c, Plot p, Cfg k, int u, int v, int eu, int ev) {
		if (k.pw > 1 && (eu == k.pw || ev == k.pw)) {
			s(c, p, u, 0, v, p.hash(u, 0, v, 4) == 0 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
			s(c, p, u, 1, v, slab(Blocks.STONE_BRICK_SLAB));
			return;
		}
		s(c, p, u, 0, v, b(Blocks.GRASS_BLOCK));
		if (eu == 1 && ev == 1) {
			s(c, p, u, 1, v, CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			s(c, p, u, 2, v, CitadelCanvas.leaves(Blocks.AZALEA_LEAVES));
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

	/** A fin of stairs that climbs 1:1 towards the back, with a vertical pier, a gold ball and a rod at the high end. */
	private void gnomon(CitadelCanvas c, Plot p, Cfg k) {
		int cu = k.cu;
		int last = 2 * k.lg;
		for (int i = 0; i <= last; i++) {
			int v = k.cv - k.lg + i;
			int hi = 1 + i;
			for (int y = k.yd + 1; y < k.yd + hi; y++) {
				s(c, p, cu, y, v, b(k.gBlock));
			}
			if (i < last) {
				// facing SOUTH: the tall half is towards the back, so each column rises half a block on the last
				s(c, p, cu, k.yd + hi, v, stairs(k.gStair, Direction.SOUTH));
			} else {
				s(c, p, cu, k.yd + hi, v, b(k.gBlock));
				int ex = 0;
				for (int y = 1; y <= ex; y++) {
					s(c, p, cu, k.yd + hi + y, v, b(k.gBlock));
				}
				s(c, p, cu, k.yd + hi + ex + 1, v, b(Blocks.GOLD_BLOCK));
				s(c, p, cu, k.yd + hi + ex + 2, v, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
			}
			if (i <= 2 && i < last) {
				// a low foot on each side of the first columns, so the fin is a wedge and not a card
				s(c, p, cu - 1, k.yd + 1, v, stairs(k.gStair, Direction.EAST));
				s(c, p, cu + 1, k.yd + 1, v, stairs(k.gStair, Direction.WEST));
			}
		}
	}
}
