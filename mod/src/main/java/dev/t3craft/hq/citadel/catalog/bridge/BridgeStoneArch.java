package dev.t3craft.hq.citadel.catalog.bridge;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Concept: a humpbacked stone footbridge. The deck climbs in half-block steps (slabs, then full
 * blocks) from ground level at both ends to a crown in the middle, so it is walkable at {@code y = 0}
 * at {@code v = 0} and {@code v = d - 1}. Under the crown a single round arch is cut right through
 * the bridge from side to side (air under it: the channel is the leader's business), its curve
 * smoothed with upside-down stairs and marked on both faces by a ring of voussoirs with a chiseled
 * keystone. Both long edges carry a kerb and a balustrade of stone-brick walls that steps up with
 * the deck, broken every third cell by a capped post; the four ends stand on dark newels that hold
 * lanterns.
 *
 * <ul>
 * <li><b>Palette, dark → light, bottom → top</b>: deepslate bricks, cobbled deepslate and mossy
 *     cobblestone at the footings (y 0); stone bricks with mossy and cracked cells for the body;
 *     calcite and smooth stone on the kerb coping; smooth-stone slab caps on the posts.</li>
 * <li><b>Depth</b>: the arch opening (a void through the whole width) with stair-smoothed intrados and
 *     a contrasting voussoir ring; walls (thin) between posts (full) so the balustrade reads as
 *     pierced; the kerb sits half a block above the slab steps on odd rows.</li>
 * <li><b>Finish</b>: newels with standing lanterns at both approaches, lanterns hanging from the
 *     keystone on both faces when the arch is tall enough to walk under.</li>
 * </ul>
 *
 * <pre>
 *  side view (d = 15, h = 6)
 *  y5        ▫ ▫ ▫ ▫ ▫         post caps
 *  y4    ▫ ┬┬┬┬┬┬┬┬┬┬┬ ▫       balustrade steps with the deck
 *  y3  ▫┬┬▄▄▄▄▄▀▀▀▄▄▄▄▄┬┬▫     deck / kerb
 *  y2 ♦▄▄██▀         ▀██▄▄♦    lanterns on the newels
 *  y1 █████▀           ▀█████  arch ring (upside-down stairs)
 *  y0 ▓▓▓▓               ▓▓▓▓  footings, air under the arch
 * </pre>
 *
 * Size: 3–5 × 7–15 × 4+. The crown rises one block every two rows up to {@code min(4, h - 2)} blocks of
 * deck (y 3 for {@code h ≥ 6}); lower plots get a flatter crown and a low culvert under it.
 */
public final class BridgeStoneArch implements Variant {
	@Override
	public String id() {
		return "bridge_stone_arch";
	}

	@Override
	public Family family() {
		return Family.BRIDGE;
	}

	@Override
	public int minW() {
		return 3;
	}

	@Override
	public int minD() {
		return 7;
	}

	@Override
	public int minH() {
		return 4;
	}

	@Override
	public int maxW() {
		return 5;
	}

	@Override
	public int maxD() {
		return 15;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		// deck level in half blocks: 1 = a slab at y0, 2 = a full block at y0, 3 = full + slab at y1…
		// capped so the balustrade post cap (railY + 1) stays inside the box: railY ≤ h - 2
		int lMax = Math.min(2 * (p.h() - 2), 8);
		int[] lv = new int[d];
		for (int v = 0; v < d; v++) {
			lv[v] = Math.min(Math.min(v, d - 1 - v) + 1, lMax);
		}
		int crown = (d - 1) / 2;
		int peak = lv[crown] / 2 - 1; // highest full deck block at the crown (≥ 1: lv ≥ 4 there)

		// the arch: air for y < open[v], between the abutments
		int a = d >= 11 ? 2 : 1;
		int vs = a;
		int ve = d - 1 - a;
		double cm = (vs + ve) / 2.0;
		double hs = (ve - vs) / 2.0 + 1.0;
		int[] open = new int[d];
		for (int v = vs; v <= ve; v++) {
			double x = (v - cm) / hs;
			int arc = (int) Math.round(peak * Math.sqrt(Math.max(0.0, 1.0 - x * x)));
			open[v] = Math.max(0, Math.min(arc, lv[v] / 2 - 1)); // always keep a deck block over the void
		}

		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				column(c, p, u, v, lv, open, cm, crown, d);
			}
		}

		// [finish] lanterns hanging from the keystone on both faces, when the arch is tall enough
		if (open[crown] >= 3) {
			for (int u : new int[] {0, w - 1}) {
				p.put(c, u, open[crown] - 1, crown, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
				if (d % 2 == 0) {
					p.put(c, u, open[crown + 1] - 1, crown + 1, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
				}
			}
		}
	}

	private static int railY(int[] lv, int v) {
		return (lv[v] + 1) / 2;
	}

	/** One column of the bridge: footing, body, arch ring, deck or kerb, and the balustrade on the edges. */
	private static void column(CitadelCanvas c, Plot p, int u, int v, int[] lv, int[] open, double cm, int crown, int d) {
		int w = p.w();
		boolean edge = u == 0 || u == w - 1;
		boolean end = v == 0 || v == d - 1;
		int ry = railY(lv, v);
		int top = edge ? ry - 1 : lv[v] / 2 - 1; // highest full block of this column
		int o = open[v];
		boolean isCrown = v == crown || (d % 2 == 0 && v == crown + 1);

		// the neighbour towards the middle of the span: a higher opening there means this cell springs the curve
		int nb = v < cm ? v + 1 : v - 1;
		boolean spring = v != cm && nb >= 0 && nb < d && open[nb] > o;

		for (int y = o; y <= top; y++) {
			BlockState st;
			if (y == o && spring) {
				// [depth] intrados: upside-down stair, its full side on the abutment side, the cut towards the void
				st = stairs(edge || p.hash(u, y, v, 3) != 0 ? Blocks.STONE_BRICK_STAIRS : Blocks.MOSSY_STONE_BRICK_STAIRS,
						v < cm ? Direction.NORTH : Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP);
			} else if (y == o && o > 0) {
				// voussoirs over the void: keystone on the faces at the crown, polished ring elsewhere on the faces
				st = edge ? (isCrown ? b(Blocks.CHISELED_STONE_BRICKS) : b(Blocks.POLISHED_ANDESITE)) : body(p, u, y, v);
			} else if (y == top && edge) {
				// [palette] light coping on the kerb
				st = p.hash(u, y, v, 3) == 0 ? b(Blocks.SMOOTH_STONE) : b(Blocks.CALCITE);
			} else if (y == top) {
				// walkway paving
				st = p.hash(u, y, v, 4) == 0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.STONE_BRICKS);
			} else if (y == 0) {
				st = footing(p, u, v);
			} else {
				st = body(p, u, y, v);
			}
			p.set(c, u, y, v, st);
		}

		if (!edge) {
			if (lv[v] % 2 == 1) {
				// half step of the walkway: slabs, the ends are the ground-level approach
				BlockState s = end ? slab(Blocks.POLISHED_ANDESITE_SLAB)
						: p.hash(u, lv[v] / 2, v, 3) == 0 ? slab(Blocks.STONE_BRICK_SLAB) : slab(Blocks.SMOOTH_STONE_SLAB);
				p.set(c, u, lv[v] / 2, v, s);
			}
			return;
		}

		if (end) {
			// [finish] newel: dark plinth, chiseled block, standing lantern
			p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE));
			p.set(c, u, 1, v, b(Blocks.CHISELED_STONE_BRICKS));
			p.set(c, u, 2, v, b(Blocks.LANTERN));
			return;
		}

		int k = Math.min(v, d - 1 - v);
		if (k % 3 == 0) {
			// [depth] balustrade post with a smooth-stone cap
			p.set(c, u, ry, v, b(Blocks.STONE_BRICKS));
			p.set(c, u, ry + 1, v, slab(Blocks.SMOOTH_STONE_SLAB));
			return;
		}
		// walls between posts, joined to a neighbour at the same height or to a higher kerb
		boolean n = railY(lv, v - 1) >= ry;
		boolean s = railY(lv, v + 1) >= ry;
		BlockState wall = b(p.hash(u, ry, v, 4) == 0 ? Blocks.MOSSY_STONE_BRICK_WALL : Blocks.STONE_BRICK_WALL)
				.setValue(BlockStateProperties.UP, !(n && s))
				.setValue(BlockStateProperties.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.EAST_WALL, WallSide.NONE)
				.setValue(BlockStateProperties.WEST_WALL, WallSide.NONE);
		p.set(c, u, ry, v, wall);
	}

	/** [palette] heavy dark footing at y 0, mixed. */
	private static BlockState footing(Plot p, int u, int v) {
		return switch (p.hash(u, 0, v, 6)) {
			case 0 -> b(Blocks.COBBLED_DEEPSLATE);
			case 1 -> b(Blocks.MOSSY_COBBLESTONE);
			case 2 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	/** [palette] the body: stone bricks with mossy cells low down and cracked ones higher up. */
	private static BlockState body(Plot p, int u, int y, int v) {
		int h = p.hash(u, y, v, 6);
		if (h == 0) {
			return y <= 1 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.CRACKED_STONE_BRICKS);
		}
		if (h == 1 && y <= 1) {
			return b(Blocks.MOSSY_STONE_BRICKS);
		}
		return b(Blocks.STONE_BRICKS);
	}
}
