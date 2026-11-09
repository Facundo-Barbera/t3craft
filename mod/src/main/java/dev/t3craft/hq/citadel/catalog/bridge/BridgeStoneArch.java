package dev.t3craft.hq.citadel.catalog.bridge;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
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
 * Concept: a humpbacked stone footbridge with one big round arch. Two flights of stairs (half-block steps,
 * walked without a jump) climb from the ground at both ends to a flat deck that rides on the arch. The arch
 * is a ring of alternating dark (deepslate brick) and light (polished andesite) voussoirs with a chiseled
 * keystone and a lantern hanging from it; its intrados is smoothed with upside-down stairs. The abutments are
 * heavy: a dark plinth, a stepped ledge, wall panels recessed one block between pilasters (so the ring and the
 * pilasters stand proud), and a corbel course under the stair cornice.
 *
 * <ul>
 * <li><b>Palette, dark → light, bottom → top</b>: deepslate bricks / cobbled / mossy at the plinth and the
 *     arch ring; stone bricks (mossy low, cracked) for the body; polished andesite curbs and voussoirs;
 *     calcite coping; cherry fence posts with lanterns on the four end lamps (the accent).</li>
 * <li><b>Depth</b>: ring and pilasters 1 block proud of the recessed spandrel panels, corbel stairs under the
 *     cornice, a stair ledge on the plinth, posts and low walls in the parapet.</li>
 * <li><b>Finish</b>: a cherry lamp at each of the four ends, a lantern on every parapet post, a lantern
 *     hanging from the keystone, a polished andesite curb along both sides of the walkway.</li>
 * </ul>
 *
 * <pre>
 *  side view (d = 24, h = 10)
 *  y8          ♦  ♦  ♦  ♦  ♦          lanterns on the posts
 *  y7        ┬┬┬┬┬┬┬┬┬┬┬┬┬┬┬┬         parapet
 *  y6     ▄▄▄▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▄▄▄       cornice (stairs) and flat deck
 *  y5  ♦ ▄█▀▓▒▓▒▓▒▓▒▓▒▓▒▓▒▓▒▓▀█▄ ♦      keystone ring
 *  y3   ▄██▀                 ▀██▄
 *  y0  ▓▓▓▓                     ▓▓▓▓    plinth, air under the arch
 * </pre>
 *
 * Size: 5–9 × 17–31 × 10+. The deck is {@code w - 2} wide (3 to 7 clear between the parapets). The flat deck
 * stands {@code min(h - 3, (d - 7) / 2)} blocks above the ground and the arch rises two blocks short of it.
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
		return 10;
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
		int crown = (d - 1) / 2;
		double cm = (d - 1) / 2.0;

		// deck: flights of P stairs (one block per cell, half-block steps) up to a flat deck whose top is P above the ground
		int deck = Math.max(3, Math.min(h - 3, (d - 7) / 2));
		int[] top = new int[d]; // highest block of the deck column: a stair on the flights, a full block on the flat
		boolean[] flight = new boolean[d];
		for (int v = 0; v < d; v++) {
			int k = Math.min(v, d - 1 - v);
			flight[v] = k < deck;
			top[v] = flight[v] ? k : deck - 1;
		}

		// the arch: air for y < open[v]. A round-ish arch (half span 1.5 × its rise) in the middle,
		// with solid abutments of `a` cells at both ends (they carry the pilasters)
		int rMax = Math.max(1, deck - 2);
		double hs = Math.max(3.0, Math.round(rMax * 3.0) / 2.0); // half span in cells
		int a = Math.max(2, (int) Math.ceil(cm - hs));
		int[] open = new int[d];
		for (int v = 0; v < d; v++) {
			double x = (v - cm) / hs;
			if (Math.abs(x) < 1.0) {
				int arc = (int) Math.round(rMax * Math.sqrt(1.0 - x * x));
				open[v] = Math.max(0, Math.min(arc, top[v] - 1)); // a deck block always stays over the void
			}
		}

		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				column(c, p, u, v, top, flight, open, cm, crown, a, deck);
			}
		}

		// [finish] a lantern hanging from the keystone on both faces, when the arch is tall enough to pass under
		if (open[crown] >= 3) {
			for (int u : new int[] {0, w - 1}) {
				p.put(c, u, open[crown] - 1, crown, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			}
		}
	}

	private static boolean pilaster(int k, int a) {
		return k >= 1 && k < a && (a - 1 - k) % 3 == 0;
	}

	/** One column of the bridge. */
	private static void column(CitadelCanvas c, Plot p, int u, int v, int[] top, boolean[] flight, int[] open, double cm, int crown, int a, int deck) {
		int w = p.w();
		int d = p.d();
		boolean edge = u == 0 || u == w - 1;
		int t = top[v];
		int o = open[v];
		int k = Math.min(v, d - 1 - v);
		boolean towardsLow = v < cm;
		int nb = towardsLow ? v + 1 : v - 1;
		int oc = v != cm && nb >= 0 && nb < d ? open[nb] : o; // opening of the neighbour towards the middle
		boolean spring = o > 0 && oc > o;
		Direction outward = towardsLow ? Direction.NORTH : Direction.SOUTH; // the abutment side
		Direction up = towardsLow ? Direction.SOUTH : Direction.NORTH; // the way a flight climbs

		if (!edge) {
			boolean curb = u == 1 || u == w - 2;
			for (int y = o; y <= t; y++) {
				BlockState st;
				if (y == t) {
					if (flight[v]) {
						st = stairs(curb ? Blocks.POLISHED_ANDESITE_STAIRS : Blocks.STONE_BRICK_STAIRS, up);
					} else {
						st = pave(p, u, y, v, curb);
					}
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
		boolean recess = t >= 3 && !pil;
		for (int y = o; y <= t; y++) {
			BlockState st = null;
			if (hasRing && y <= ringTop) {
				// [depth] the ring stands proud of the recessed panels; [palette] alternating dark / light voussoirs
				if (keystone && y == o) {
					st = b(Blocks.CHISELED_STONE_BRICKS);
				} else if (y == o && spring) {
					st = stairs(Blocks.DEEPSLATE_BRICK_STAIRS, outward).setValue(BlockStateProperties.HALF, Half.TOP);
				} else {
					st = ((v + y) & 1) == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.POLISHED_ANDESITE);
				}
			} else if (y == 0) {
				st = footing(p, u, v);
			} else if (y == t) {
				// [palette] the cornice: the flight's own stairs on the ramps, a clean calcite line on the flat
				st = flight[v] ? stairs(Blocks.STONE_BRICK_STAIRS, up) : b(Blocks.CALCITE);
			} else if (pil) {
				st = y == t - 1 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_BRICKS);
			} else if (recess) {
				// [depth] panel recessed one block: a stair ledge on the plinth, air, a corbel under the cornice
				if (y == 1) {
					st = stairs(Blocks.STONE_BRICK_STAIRS, inward);
				} else if (y == t - 1) {
					st = stairs(Blocks.STONE_BRICK_STAIRS, inward).setValue(BlockStateProperties.HALF, Half.TOP);
				}
			} else {
				st = body(p, u, y, v);
			}
			if (st != null) {
				p.set(c, u, y, v, st);
			}
		}

		// parapet: low walls with posts, a lantern on every post
		int ry = t + 1;
		boolean post = (k > 0 && k % 4 == 0) || (d % 2 == 1 && v == crown);
		boolean n = top[v - 1] >= t;
		boolean s = top[v + 1] >= t;
		BlockState wall = b(post ? Blocks.POLISHED_DEEPSLATE_WALL : p.hash(u, ry, v, 4) == 0 ? Blocks.MOSSY_STONE_BRICK_WALL : Blocks.STONE_BRICK_WALL)
				.setValue(BlockStateProperties.UP, post || !(n && s))
				.setValue(BlockStateProperties.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.SOUTH_WALL, s ? WallSide.LOW : WallSide.NONE)
				.setValue(BlockStateProperties.EAST_WALL, WallSide.NONE)
				.setValue(BlockStateProperties.WEST_WALL, WallSide.NONE);
		p.set(c, u, ry, v, wall);
		if (post && ry + 1 < p.h()) {
			p.set(c, u, ry + 1, v, b(Blocks.LANTERN));
		}
	}

	/** [finish] end lamp: deepslate plinth and wall, cherry fence, a lantern on top. */
	private static void lamp(CitadelCanvas c, Plot p, int u, int v) {
		p.set(c, u, 0, v, b(Blocks.POLISHED_DEEPSLATE));
		p.set(c, u, 1, v, b(Blocks.DEEPSLATE_BRICK_WALL).setValue(BlockStateProperties.UP, true));
		p.set(c, u, 2, v, b(Blocks.CHERRY_FENCE));
		p.set(c, u, 3, v, b(Blocks.LANTERN));
	}

	/** [palette] flat deck: polished andesite curb along both sides, stone bricks with the odd cracked one between. */
	private static BlockState pave(Plot p, int u, int y, int v, boolean curb) {
		if (curb) {
			return b(Blocks.POLISHED_ANDESITE);
		}
		return p.hash(u, y, v, 7) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
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
