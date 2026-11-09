package dev.t3craft.hq.citadel.catalog.towertop;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Concept: a Renaissance-style lantern dome on a tower, a rounded counterpoint to the pointed spires.
 * At {@code y = 0} a corbelled cornice of upside-down polished-deepslate stairs (chiseled deepslate
 * corners) covers the full footprint. On it, a round drum: a dark plinth course, calcite piers with
 * polished diorite mixed in, tall openings glazed with pink (or light blue) panes, and arch heads of
 * upside-down diorite stairs (paired openings on even sizes form little round arches). On tops of 7+
 * a walk with a deepslate-wall balustrade and calcite corner posts carrying copper lanterns rings the
 * drum; smaller tops put the copper lanterns on the free corners. Over the drum a stepped dome
 * (slabs on the outer band, stairs nearer the crown) in waxed copper with dark tile ribs, or in
 * deepslate tiles with copper ribs, by seed. On its crown an open lantern: four cherry posts round a
 * hanging lantern under a copper roof, a gold boss and a copper finial.
 *
 * <ul>
 * <li><b>Palette, dark → light, bottom → top</b>: polished deepslate cornice and plinth; calcite and
 *     diorite drum; copper or tile dome; cherry posts, copper roof, gold and copper on the point.</li>
 * <li><b>Depth</b>: corbel under everything; glazed openings (thin panes) between solid piers; arch
 *     heads cut underneath; the dome rim overhangs the drum by half a block of slab; ribs.</li>
 * <li><b>Silhouette</b>: wide and round (drum + dome + small lantern), unlike the tapering spire.</li>
 * </ul>
 *
 * <pre>
 *  section (w = 9, h = 13)
 *  y12        ╿          rod
 *  y11        ▣          chiseled copper
 *  y10      ◢▀█▀◣        copper roof, gold boss
 *  y8-9     ┃ ♦ ┃        open lantern: cherry posts, hanging lantern
 *  y7     ▄▄▀▀▀▀▄▄       dome crown
 *  y6   ▄▀       ▀▄      dome (stairs)
 *  y5  ▂▀         ▀▂     dome rim (slabs)
 *  y4  ┃ ◥▀▀◤ ◥▀▀◤ ┃     arch heads
 *  y2-3┃ ▒▒ █ ▒▒ █ ┃     glazed openings, calcite piers
 *  y1  ♦ ▓▓▓▓▓▓▓▓▓ ♦     plinth, balustrade, copper lanterns
 *  y0  ◥▀▀▀▀▀▀▀▀▀▀▀◤     corbelled cornice
 * </pre>
 *
 * Size: 5–9 × 5–9 × 7+. Height above 7 goes, in order, to the finial rod, a plinth course, a dome
 * layer, a taller lantern, taller openings and a chiseled copper finial (h 13); more stays empty.
 */
public final class TowerTopLanternDome implements Variant {
	@Override
	public String id() {
		return "tower_top_lantern_dome";
	}

	@Override
	public Family family() {
		return Family.TOWER_TOP;
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
		return 7;
	}

	@Override
	public int maxW() {
		return 9;
	}

	@Override
	public int maxD() {
		return 9;
	}

	/** Shape of the footprint: a rounded rectangle (a circle on square plots) centred on the box. */
	private record Shape(double cx, double cz, double ex, double ez, double rd) {
		double du(int u) {
			return Math.max(0.0, Math.abs(u - cx) - ex);
		}

		double dv(int v) {
			return Math.max(0.0, Math.abs(v - cz) - ez);
		}

		double r(int u, int v) {
			double a = du(u);
			double b = dv(v);
			return Math.sqrt(a * a + b * b);
		}

		boolean disk(Plot p, int u, int v) {
			return u >= 0 && u < p.w() && v >= 0 && v < p.d() && r(u, v) <= rd + 0.5 + 1e-9;
		}

		/** The direction from {@code (u, v)} towards the centre (local: NORTH = towards the front). */
		Direction inward(int u, int v) {
			if (du(u) >= dv(v)) {
				return u > cx ? Direction.WEST : Direction.EAST;
			}
			return v > cz ? Direction.NORTH : Direction.SOUTH;
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		double cx = (w - 1) / 2.0;
		double cz = (d - 1) / 2.0;
		double rm = Math.min(cx, cz);
		boolean walk = rm >= 3.0;
		Shape s = new Shape(cx, cz, cx - rm, cz - rm, walk ? rm - 1.0 : rm);

		// height budget (h = 7 is the minimum; each extra block goes to one feature, in order)
		int e = p.h() - 7;
		int plinth = e >= 2 ? 1 : 0;
		int win = 1 + (e >= 5 ? 1 : 0);
		int head = plinth + win + 1; // the drum's top course
		int domeL = 2 + (e >= 3 ? 1 : 0);
		int lp = 1 + (e >= 4 ? 1 : 0);
		int fin = (e >= 1 ? 1 : 0) + (e >= 6 ? 1 : 0);
		int yb = head + 1;
		int yl = yb + domeL;
		int yr = yl + lp;

		// the lantern's rectangle: 3 cells on odd axes, 4 on even axes, centred
		int ul0 = w % 2 == 1 ? (w - 1) / 2 - 1 : w / 2 - 2;
		int ul1 = w % 2 == 1 ? (w - 1) / 2 + 1 : w / 2 + 1;
		int vl0 = d % 2 == 1 ? (d - 1) / 2 - 1 : d / 2 - 2;
		int vl1 = d % 2 == 1 ? (d - 1) / 2 + 1 : d / 2 + 1;
		double rTop = s.r(ul0, vl0) + 0.01; // the crown platform reaches the lantern posts

		boolean copperDome = p.hash(0, 0, 0, 2) == 0;
		DyeColor glass = p.hash(1, 0, 1, 3) == 0 ? DyeColor.LIGHT_BLUE : DyeColor.PINK;

		cornice(c, p);
		surround(c, p, s, walk);
		drum(c, p, s, plinth, win, head, glass);
		dome(c, p, s, yb, domeL, rTop, copperDome);
		lantern(c, p, ul0, ul1, vl0, vl1, yl, lp, yr, fin);

		// [finish] a lantern hanging inside the drum, seen through the glass
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				if (Math.abs(u - cx) < 0.6 && Math.abs(v - cz) < 0.6) {
					p.put(c, u, head, v, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y), CitadelCanvas.HANGING);
					p.put(c, u, head - 1, v, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
				}
			}
		}
	}

	// ------------------------------------------------------------------ cornice (y0)

	private static void cornice(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				boolean eu = u == 0 || u == w - 1;
				boolean ev = v == 0 || v == d - 1;
				BlockState st;
				if (eu && ev) {
					st = b(Blocks.CHISELED_DEEPSLATE);
				} else if (eu || ev) {
					// [depth] corbel: full side inwards, the cut outside
					Direction in = v == 0 ? Direction.SOUTH : v == d - 1 ? Direction.NORTH : u == 0 ? Direction.EAST : Direction.WEST;
					st = stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, in).setValue(BlockStateProperties.HALF, Half.TOP);
				} else {
					int k = p.hash(u, 0, v, 4);
					st = k == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : k == 1 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_TILES);
				}
				p.set(c, u, 0, v, st);
			}
		}
	}

	// ------------------------------------------------------------------ walk balustrade or corner lanterns (y1..y2)

	private static void surround(CitadelCanvas c, Plot p, Shape s, boolean walk) {
		int w = p.w();
		int d = p.d();
		BlockState copperLantern = b(Blocks.COPPER_LANTERN.waxed().unaffected());
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				boolean eu = u == 0 || u == w - 1;
				boolean ev = v == 0 || v == d - 1;
				if (!walk) {
					if (eu && ev && !s.disk(p, u, v)) {
						p.set(c, u, 1, v, copperLantern);
					}
					continue;
				}
				if (eu && ev) {
					p.set(c, u, 1, v, b(Blocks.CALCITE));
					p.set(c, u, 2, v, copperLantern);
				} else if (eu || ev) {
					p.set(c, u, 1, v, b(Blocks.POLISHED_DEEPSLATE_WALL)
							.setValue(BlockStateProperties.UP, false)
							.setValue(BlockStateProperties.NORTH_WALL, eu ? WallSide.LOW : WallSide.NONE)
							.setValue(BlockStateProperties.SOUTH_WALL, eu ? WallSide.LOW : WallSide.NONE)
							.setValue(BlockStateProperties.EAST_WALL, ev ? WallSide.LOW : WallSide.NONE)
							.setValue(BlockStateProperties.WEST_WALL, ev ? WallSide.LOW : WallSide.NONE));
				}
			}
		}
	}

	// ------------------------------------------------------------------ drum (y1..head)

	private static void drum(CitadelCanvas c, Plot p, Shape s, int plinth, int win, int head, DyeColor glass) {
		int w = p.w();
		int d = p.d();
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				if (!s.disk(p, u, v)) {
					continue;
				}
				boolean shell = false;
				boolean edge4 = false;
				for (int dv = -1; dv <= 1; dv++) {
					for (int du = -1; du <= 1; du++) {
						if ((du != 0 || dv != 0) && !s.disk(p, u + du, v + dv)) {
							shell = true;
							if (du == 0 || dv == 0) {
								edge4 = true;
							}
						}
					}
				}
				if (!shell) {
					continue; // the drum is hollow: the hanging lantern lives inside
				}
				boolean uFace = s.du(u) >= s.dv(v);
				double t = uFace ? s.dv(v) : s.du(u);
				boolean opening = edge4 && (s.rd() >= 3.0 ? Math.abs(t - 1.0) < 0.01 : t <= 0.51);
				boolean paired = opening && Math.abs(t - 0.5) < 0.01;

				for (int y = 1; y <= head; y++) {
					BlockState st;
					if (y <= plinth) {
						st = p.hash(u, y, v, 3) == 0 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.POLISHED_DEEPSLATE);
					} else if (y < head) {
						if (opening) {
							st = Blocks.STAINED_GLASS_PANE.pick(glass).defaultBlockState()
									.setValue(BlockStateProperties.NORTH, uFace)
									.setValue(BlockStateProperties.SOUTH, uFace)
									.setValue(BlockStateProperties.EAST, !uFace)
									.setValue(BlockStateProperties.WEST, !uFace);
						} else {
							st = p.hash(u, y, v, 4) == 0 ? b(Blocks.POLISHED_DIORITE) : b(Blocks.CALCITE);
						}
					} else if (opening) {
						// [depth] arch head: paired openings lean on their outer piers (a little round arch),
						// single ones are cut underneath on the outside
						Direction f;
						if (paired) {
							f = uFace ? (v > s.cz() ? Direction.SOUTH : Direction.NORTH) : (u > s.cx() ? Direction.EAST : Direction.WEST);
						} else {
							f = s.inward(u, v);
						}
						st = stairs(Blocks.POLISHED_DIORITE_STAIRS, f).setValue(BlockStateProperties.HALF, Half.TOP);
					} else {
						st = b(Blocks.POLISHED_DIORITE);
					}
					p.set(c, u, y, v, st);
				}
			}
		}
	}

	// ------------------------------------------------------------------ dome (yb..yb+domeL-1)

	private static void dome(CitadelCanvas c, Plot p, Shape s, int yb, int domeL, double rTop, boolean copperDome) {
		int w = p.w();
		int d = p.d();
		double r0 = s.rd() + 0.5;
		double[] rho = new double[domeL];
		for (int j = 0; j < domeL; j++) {
			rho[j] = rTop + (r0 - rTop) * Math.cos(Math.PI / 2 * j / (domeL - 1.0));
		}
		rho[0] = r0 + 1e-9;
		for (int j = 0; j < domeL; j++) {
			int y = yb + j;
			boolean last = j == domeL - 1;
			for (int v = 0; v < d; v++) {
				for (int u = 0; u < w; u++) {
					double r = s.r(u, v);
					if (r > rho[j]) {
						continue;
					}
					boolean exposed = !last && r > rho[j + 1];
					boolean rib = Math.abs(s.du(u) - s.dv(v)) < 0.6 && r > 1.0;
					BlockState st;
					if (exposed) {
						boolean outer = r > rho[j + 1] + 1.0; // outer band: a half step
						Block blk;
						if (outer) {
							blk = (copperDome != rib) ? copper(p, u, y, v, Blocks.CUT_COPPER_SLAB.waxed().unaffected(),
									Blocks.CUT_COPPER_SLAB.waxed().exposed(), Blocks.CUT_COPPER_SLAB.waxed().weathered()) : Blocks.DEEPSLATE_TILE_SLAB;
							st = slab(blk);
						} else {
							blk = (copperDome != rib) ? copper(p, u, y, v, Blocks.CUT_COPPER_STAIRS.waxed().unaffected(),
									Blocks.CUT_COPPER_STAIRS.waxed().exposed(), Blocks.CUT_COPPER_STAIRS.waxed().weathered()) : Blocks.DEEPSLATE_TILE_STAIRS;
							st = stairs(blk, s.inward(u, v));
						}
					} else if (last) {
						st = copperDome ? b(Blocks.CUT_COPPER.waxed().unaffected()) : b(Blocks.POLISHED_DEEPSLATE);
					} else {
						st = copperDome ? b(Blocks.CUT_COPPER.waxed().unaffected()) : b(Blocks.DEEPSLATE_TILES);
					}
					p.set(c, u, y, v, st);
				}
			}
		}
	}

	/** [palette] waxed copper with a little patina: mostly fresh, some exposed, a few weathered. */
	private static Block copper(Plot p, int u, int y, int v, Block fresh, Block exposed, Block weathered) {
		int k = p.hash(u, y, v, 6);
		return k == 0 ? weathered : k <= 2 ? exposed : fresh;
	}

	// ------------------------------------------------------------------ lantern, roof, finial

	private static void lantern(CitadelCanvas c, Plot p, int ul0, int ul1, int vl0, int vl1, int yl, int lp, int yr, int fin) {
		BlockState post = b(Blocks.CHERRY_FENCE)
				.setValue(BlockStateProperties.NORTH, false)
				.setValue(BlockStateProperties.SOUTH, false)
				.setValue(BlockStateProperties.EAST, false)
				.setValue(BlockStateProperties.WEST, false);
		for (int u : new int[] {ul0, ul1}) {
			for (int v : new int[] {vl0, vl1}) {
				for (int y = yl; y < yr; y++) {
					p.set(c, u, y, v, post);
				}
			}
		}
		BlockState rod = Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState();
		for (int v = vl0; v <= vl1; v++) {
			for (int u = ul0; u <= ul1; u++) {
				boolean onU = u == ul0 || u == ul1;
				boolean onV = v == vl0 || v == vl1;
				BlockState st;
				if (onU && onV) {
					st = slab(Blocks.CUT_COPPER_SLAB.waxed().unaffected());
				} else if (onU || onV) {
					Direction in = onU ? (u == ul0 ? Direction.EAST : Direction.WEST) : (v == vl0 ? Direction.SOUTH : Direction.NORTH);
					st = stairs(copper(p, u, yr, v, Blocks.CUT_COPPER_STAIRS.waxed().unaffected(), Blocks.CUT_COPPER_STAIRS.waxed().exposed(),
							Blocks.CUT_COPPER_STAIRS.waxed().weathered()), in);
				} else {
					// the centre cell(s): gold boss over a hanging lantern, finial on top
					st = b(Blocks.GOLD_BLOCK);
					p.put(c, u, yr - 1, v, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
					int y = yr + 1;
					if (fin >= 2) {
						p.set(c, u, y++, v, b(Blocks.CHISELED_COPPER.waxed().unaffected()));
					}
					if (fin >= 1) {
						p.set(c, u, y, v, rod);
					}
				}
				p.set(c, u, yr, v, st);
			}
		}
	}
}
