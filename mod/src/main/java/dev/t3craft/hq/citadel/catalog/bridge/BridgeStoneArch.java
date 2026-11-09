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
 * Concept: a humpbacked stone footbridge with one big segmental arch. The deck is a smooth hump that
 * climbs in half-block steps (slab, full block, slab + block…) from the ground at both ends, flattening
 * at the crown, so it is walked without a single jump. Under the crown the whole bridge is cut by one
 * low round arch whose intrados is smoothed with upside-down stairs. On both faces the arch is a ring of
 * alternating dark (deepslate brick) and light (smooth stone) voussoirs with a chiseled keystone and a
 * lantern hanging from it. The abutments are heavy: a dark plinth, a stepped ledge, wall panels recessed
 * one block between pilasters, and a corbel course under the coping.
 *
 * <ul>
 * <li><b>Palette, dark → light, bottom → top</b>: deepslate bricks / cobbled / mossy at the plinth and the
 *     arch ring; stone bricks (mossy low, cracked) for the body and the paving; calcite coping; smooth
 *     stone caps; cherry fence and slab with a lantern on the end lamps (the accent).</li>
 * <li><b>Depth</b>: ring and pilasters stand 1 block proud of the recessed spandrel panels, corbel stairs
 *     under the coping, a stair ledge on the plinth, posts and low walls in the parapet.</li>
 * <li><b>Finish</b>: a cherry lamp at each of the four ends, lanterns on the parapet posts, lanterns hanging
 *     from the keystone, polished-andesite joints inlaid across the paving.</li>
 * </ul>
 *
 * <pre>
 *  side view (d = 24, h = 9)
 *  y5           ♦ ▫ ▫ ▫ ▫ ♦          lantern / cap on the parapet posts
 *  y4        ┬┬┬┬┬┬┬┬┬┬┬┬┬┬           parapet (low walls)
 *  y3   ♦  ▄▄▄▀▀▀▀▀▀▀▀▀▀▀▀▀▀▄▄▄  ♦    coping + deck, lamps on the newels
 *  y2   ┃ ▄██▀ ▒▓▒▓▒▓▒▓▒▓▒▓▒▓ ▀██▄ ┃  intrados ring: alternating voussoirs
 *  y1   ┃ ███▀               ▀███ ┃
 *  y0   ▓▓▓▓                   ▓▓▓▓   plinth, air under the arch
 * </pre>
 *
 * Size: 5–9 × 17–31 × 9+ (the deck is {@code w - 2} wide: 3 to 7 clear between the parapets). The crown
 * rises up to {@code 2 (h - 3)} half blocks and never more than the 1:2 ramp allows.
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
		return 5;
	}

	@Override
	public int minD() {
		return 17;
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
		return 31;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int h = p.h();

		// deck level in half blocks: 1 = a slab at y0, 2 = a full block at y0, 3 = full + slab at y1…
		// the ramps climb half a block per cell; the plateau keeps at least 7 cells (d - 2 (lvMax - 1))
		int lvMax = Math.max(4, Math.min(2 * (h - 3), ((d - 5) / 2) & ~1));
		int[] lv = profile(d, lvMax);
		int crown = (d - 1) / 2;
		double cm = (d - 1) / 2.0;

		// the arch: air for y < open[v] inside the span. A round-ish arch (half span 1.5 × its rise) in the middle,
		// with solid abutments of `a` cells on both sides (they carry the pilasters)
		int lvC = lv[crown];
		int rMax = Math.max(0, lvC / 2 - 1);
		double hs = Math.max(3.0, Math.round(rMax * 3.0) / 2.0); // half span in cells
		int a = Math.max(2, (int) Math.ceil(cm - hs));
		int[] open = new int[d];
		for (int v = 0; v < d; v++) {
			double x = (v - cm) / hs;
			if (Math.abs(x) < 1.0) {
				int arc = (int) Math.round(rMax * Math.sqrt(1.0 - x * x));
				open[v] = Math.max(0, Math.min(arc, lv[v] / 2 - 1)); // always keep a deck block over the void
			}
		}

		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				column(c, p, u, v, lv, open, cm, crown, a);
			}
		}

		// [finish] lanterns hanging from the keystone on both faces, when the arch is tall enough to pass under
		if (open[crown] >= 3) {
			for (int u : new int[] {0, w - 1}) {
				p.put(c, u, open[crown] - 1, crown, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			}
		}
	}

	/** Deck level per cell: ramps of half a block per cell up to a flat plateau over the arch. */
	private static int[] profile(int d, int lvMax) {
		int[] lv = new int[d];
		for (int v = 0; v < d; v++) {
			lv[v] = Math.min(lvMax, Math.min(v + 1, d - v));
		}
		return lv;
	}

	private static int railY(int[] lv, int v) {
		return (lv[v] + 1) / 2;
	}

	private static boolean pilaster(int k, int a) {
		return k >= 1 && k < a && (a - 1 - k) % 3 == 0;
	}

	/** One column of the bridge. */
	private static void column(CitadelCanvas c, Plot p, int u, int v, int[] lv, int[] open, double cm, int crown, int a) {
		int w = p.w();
		int d = p.d();
		boolean edge = u == 0 || u == w - 1;
		int lvv = lv[v];
		int tf = lvv / 2 - 1; // highest full block of the deck
		int ry = railY(lv, v);
		int cop = ry - 1; // coping (edge) top block
		int o = open[v];
		int k = Math.min(v, d - 1 - v);
		boolean towardsLow = v < cm;
		int nb = towardsLow ? v + 1 : v - 1;
		int oc = v != cm && nb >= 0 && nb < d ? open[nb] : o; // opening of the neighbour towards the middle
		boolean spring = o > 0 && oc > o;
		Direction outward = towardsLow ? Direction.NORTH : Direction.SOUTH; // the abutment side

		if (!edge) {
			for (int y = o; y <= tf; y++) {
				BlockState st;
				if (y == tf) {
					st = pave(p, u, y, v, k);
				} else if (y == o && o > 0) {
					// [depth] intrados of the vault: upside-down stairs on the springing steps
					st = spring ? stairs(Blocks.STONE_BRICK_STAIRS, outward).setValue(BlockStateProperties.HALF, Half.TOP) : b(Blocks.STONE_BRICKS);
				} else if (y == 0) {
					st = footing(p, u, v);
				} else {
					st = body(p, u, y, v);
				}
				p.set(c, u, y, v, st);
			}
			if (lvv % 2 == 1) {
				// half step of the walkway: slabs, the ends are the ground-level approach
				p.set(c, u, lvv / 2, v, paveSlab(p, u, lvv / 2, v, k));
			}
			return;
		}

		Direction inward = u == 0 ? Direction.EAST : Direction.WEST;
		if (v == 0 || v == d - 1) {
			lamp(c, p, u, v);
			return;
		}

		boolean hasRing = o > 0 || oc > o;
		int ringTop = Math.max(o, oc - 1);
		boolean keystone = o > 0 && (v == crown || (d % 2 == 0 && v == crown + 1));
		boolean pil = pilaster(k, a);
		boolean recess = cop >= 3 && !pil;
		for (int y = o; y <= cop; y++) {
			BlockState st = null;
			if (hasRing && y <= ringTop) {
				// [depth] the ring stands proud of the recessed panels; [palette] alternating dark / light voussoirs
				if (keystone && y == o) {
					st = b(Blocks.CHISELED_STONE_BRICKS);
				} else if (y == o && spring) {
					st = stairs(Blocks.DEEPSLATE_BRICK_STAIRS, outward).setValue(BlockStateProperties.HALF, Half.TOP);
				} else {
					st = ((v + y) & 1) == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.SMOOTH_STONE);
				}
			} else if (y == 0) {
				st = footing(p, u, v);
			} else if (y == cop) {
				// [palette] one clean light coping line that follows the hump
				st = b(Blocks.CALCITE);
			} else if (pil) {
				st = y == cop - 1 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_BRICKS);
			} else if (recess) {
				// [depth] panel recessed one block: a stair ledge on the plinth, air, a corbel under the coping
				if (y == 1) {
					st = stairs(Blocks.STONE_BRICK_STAIRS, inward);
				} else if (y == cop - 1) {
					st = stairs(Blocks.STONE_BRICK_STAIRS, inward).setValue(BlockStateProperties.HALF, Half.TOP);
				}
			} else {
				st = body(p, u, y, v);
			}
			if (st != null) {
				p.set(c, u, y, v, st);
			}
		}

		// parapet: low walls with capped posts
		boolean post = (k % 5 == 0 && k > 0) || (d % 2 == 1 && v == crown);
		boolean n = railY(lv, v - 1) >= ry;
		boolean s = railY(lv, v + 1) >= ry;
		BlockState wall = b(post ? Blocks.POLISHED_DEEPSLATE_WALL : p.hash(u, ry, v, 4) == 0 ? Blocks.MOSSY_STONE_BRICK_WALL : Blocks.STONE_BRICK_WALL)
				.setValue(BlockStateProperties.UP, post || !(n && s))
				.setValue(BlockStateProperties.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.EAST_WALL, WallSide.NONE)
				.setValue(BlockStateProperties.WEST_WALL, WallSide.NONE);
		p.set(c, u, ry, v, wall);
		if (post && ry + 1 < p.h()) {
			// [finish] a lantern on every other post (and the crown), a smooth stone cap on the rest
			boolean lit = (d % 2 == 1 && v == crown) || k % 10 == 5;
			p.set(c, u, ry + 1, v, lit ? b(Blocks.LANTERN) : slab(Blocks.SMOOTH_STONE_SLAB));
		}
	}

	/** [finish] end lamp: deepslate plinth and wall, cherry fence, a lantern under a cherry cap. */
	private static void lamp(CitadelCanvas c, Plot p, int u, int v) {
		p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE));
		p.set(c, u, 1, v, b(Blocks.DEEPSLATE_BRICK_WALL).setValue(BlockStateProperties.UP, true));
		p.set(c, u, 2, v, b(Blocks.CHERRY_FENCE));
		p.set(c, u, 3, v, b(Blocks.LANTERN));
	}

	/** [palette] walkway paving: stone bricks with polished andesite joints across the deck. */
	private static BlockState pave(Plot p, int u, int y, int v, int k) {
		if (k % 4 == 2) {
			return b(Blocks.POLISHED_ANDESITE);
		}
		return p.hash(u, y, v, 7) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
	}

	private static BlockState paveSlab(Plot p, int u, int y, int v, int k) {
		return k % 4 == 2 ? slab(Blocks.POLISHED_ANDESITE_SLAB) : slab(Blocks.STONE_BRICK_SLAB);
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
