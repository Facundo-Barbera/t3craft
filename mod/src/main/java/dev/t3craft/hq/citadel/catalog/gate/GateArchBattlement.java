package dev.t3craft.hq.citadel.catalog.gate;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Neighbourhood gate: an arched passage between two towers. Each tower face is framed by pilasters with a
 * recessed panel between them (a pink pennant in the front panels of wide towers), a pale corbelled cornice
 * and a parapet whose merlons step in height (full corners, half middles, open crenels). The curtain over the
 * arch is lower than the towers, with its own cornice and a parapet whose middle merlon rises above the rest.
 *
 * <ul>
 * <li><b>Walkable centre</b>: the opening is at least 3 high at its edges; the arch shoulders are
 *     upside-down stairs above head height.</li>
 * <li><b>Depth</b>: no tower face is a flat wall: the pilasters stand one block proud of the recessed
 *     panels, the cornice of upside-down stairs overhangs them, slim towers get a window through.</li>
 * <li><b>Gradient</b>: deepslate plinth, stone-brick body (cracked / mossy mix by hash), polished
 *     andesite pilaster caps, pale diorite cornices, a calcite keystone, pink pennants.</li>
 * <li><b>Silhouette</b>: tower parapet (highest) → curtain parapet with a raised middle merlon → arch.</li>
 * </ul>
 * Size: 5–11 × 2–4 × 8+ (uses up to 10 blocks of height).
 */
public final class GateArchBattlement implements Variant {
	private static final BlockState CALCITE = b(Blocks.CALCITE);
	private static final BlockState LANTERN = b(Blocks.LANTERN);

	@Override
	public String id() {
		return "gate_arch_battlement";
	}

	@Override
	public Family family() {
		return Family.GATE;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 2;
	}

	@Override
	public int minH() {
		return 8;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 4;
	}

	/** Stone-brick body with a deterministic mix of cracked and mossy bricks. */
	private static BlockState wall(Plot p, int u, int y, int v) {
		int k = p.hash(u, y, v, 10);
		if (y <= 1) {
			return b(k < 3 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
		}
		return b(k == 0 ? Blocks.CRACKED_STONE_BRICKS : k == 1 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int hh = Math.min(p.h(), 10);
		int pl = w >= 9 ? 3 : w >= 7 ? 2 : 1; // tower width
		int ow = w - 2 * pl; // opening width
		int side = hh >= 9 ? 4 : 3; // clear height at the edges of the opening
		int rise = Math.max(0, Math.min((ow - 1) / 2, hh - 4 - side)); // arch shoulders, in stair steps
		int crown = side + rise; // the arch ring course at the top of the opening
		int cornice = crown + 1; // curtain cornice
		int merlon = crown + 2; // curtain parapet
		int tCornice = hh - 2; // tower cornice
		int tTop = hh - 1; // tower parapet
		int a0 = pl;

		// --- towers: deepslate plinth, stone-brick body, recessed panels, pale cornice, stepped parapet
		for (int sd = 0; sd < 2; sd++) {
			int u0 = sd == 0 ? 0 : w - pl;
			int u1 = u0 + pl - 1;
			int outer = sd == 0 ? u0 : u1;
			int inner = sd == 0 ? u1 : u0;
			Direction outDir = sd == 0 ? Direction.WEST : Direction.EAST;
			for (int u = u0; u <= u1; u++) {
				for (int v = 0; v < d; v++) {
					p.set(c, u, 0, v, b(p.hash(u, 0, v, 3) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS));
					for (int y = 1; y < tCornice; y++) {
						p.set(c, u, y, v, wall(p, u, y, v));
					}
					boolean edgeU = u == u0 || u == u1;
					boolean edgeV = v == 0 || v == d - 1;
					// [depth] cornice: upside-down pale stairs facing out round the top, so the parapet corbels out
					if (edgeV || u == outer) {
						Direction out = v == 0 ? Direction.NORTH : v == d - 1 ? Direction.SOUTH : outDir;
						p.set(c, u, tCornice, v, stairs(Blocks.POLISHED_DIORITE_STAIRS, out).setValue(BlockStateProperties.HALF, Half.TOP));
					} else {
						p.set(c, u, tCornice, v, b(Blocks.POLISHED_ANDESITE));
					}
					// parapet: solid merlons on the corners, half-height merlons and open crenels between
					if (edgeU || edgeV) {
						boolean corner = edgeU && edgeV;
						if (corner) {
							p.set(c, u, tTop, v, wall(p, u, tTop, v));
						} else if ((u + v) % 2 == 0) {
							p.set(c, u, tTop, v, slab(Blocks.STONE_BRICK_SLAB));
						}
					}
				}
			}
			// [depth] a string course at the springing of the arch: a pale band round the tower faces, so no face
			// runs more than three courses without a break (the recesses below cut through it where they open)
			for (int u = u0; u <= u1; u++) {
				for (int v = 0; v < d; v++) {
					if (v == 0 || v == d - 1 || u == outer) {
						p.set(c, u, side, v, b(p.hash(u, side, v, 3) == 0 ? Blocks.CHISELED_STONE_BRICKS : Blocks.POLISHED_ANDESITE));
					}
				}
			}
			// pilaster caps: polished andesite where the cornice meets the tower corners, one course down
			for (int v : new int[] {0, d - 1}) {
				p.set(c, outer, tCornice - 1, v, b(Blocks.POLISHED_ANDESITE));
				p.set(c, inner, tCornice - 1, v, b(Blocks.POLISHED_ANDESITE));
			}
			// [depth] recessed panels between the pilasters
			int panelTop = tCornice - 2;
			if (pl >= 3) {
				for (int u = u0 + 1; u < u1; u++) {
					// front: a pink pennant set into the recess, under a chiselled stone
					for (int y = 2; y <= panelTop; y++) {
						p.set(c, u, y, 0, AIR);
						p.set(c, u, y, 1, y == panelTop ? b(Blocks.CHISELED_STONE_BRICKS) : b(Blocks.WOOL.pick(DyeColor.PINK)));
					}
					p.set(c, u, 1, 0, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH)); // a sill at the foot of the recess
					if (d >= 3) {
						for (int y = 2; y <= panelTop; y++) {
							p.set(c, u, y, d - 1, AIR);
						}
					}
				}
			}
			if (d >= 3) {
				// the outer side: recessed between the front and back pilasters (through the tower when it is one block wide)
				for (int v = 1; v < d - 1; v++) {
					int yTop = pl == 1 ? side - 1 : panelTop;
					for (int y = 2; y <= yTop; y++) {
						p.set(c, outer, y, v, AIR);
					}
				}
			}
			if (d >= 3 && pl >= 2) {
				// the inner side, facing the passage: a niche between the front and back pilasters
				for (int v = 1; v < d - 1; v++) {
					for (int y = 2; y < side; y++) {
						p.set(c, inner, y, v, AIR);
					}
				}
			}
			// polished deepslate pilaster foot on the inner edge, facing the opening
			for (int v = 0; v < d; v++) {
				p.set(c, inner, 1, v, b(Blocks.POLISHED_DEEPSLATE));
			}
		}

		// --- curtain over the arch: shoulders, ring course, cornice, parapet with a raised middle merlon
		for (int i = 0; i < ow; i++) {
			int e = Math.min(i, ow - 1 - i);
			int u = a0 + i;
			boolean left = i < ow - 1 - i;
			int k = Math.abs(2 * i - (ow - 1)); // distance from the middle, doubled
			boolean isMerlon = (k / 2) % 2 == 0;
			boolean middle = k <= 1;
			for (int v = 0; v < d; v++) {
				int from = side + Math.min(e, rise);
				if (e < rise) {
					p.set(c, u, side + e, v, stairs(Blocks.STONE_BRICK_STAIRS, left ? Direction.WEST : Direction.EAST)
							.setValue(BlockStateProperties.HALF, Half.TOP));
					from = side + e + 1;
				}
				for (int y = from; y <= crown; y++) {
					p.set(c, u, y, v, wall(p, u, y, v));
				}
				boolean edge = v == 0 || v == d - 1;
				if (edge) {
					p.set(c, u, cornice, v, stairs(Blocks.POLISHED_DIORITE_STAIRS, v == 0 ? Direction.NORTH : Direction.SOUTH)
							.setValue(BlockStateProperties.HALF, Half.TOP));
					if (isMerlon) {
						p.set(c, u, merlon, v, wall(p, u, merlon, v));
						if (middle && merlon + 1 <= tTop) {
							p.set(c, u, merlon + 1, v, slab(Blocks.POLISHED_DIORITE_SLAB)); // the middle merlon stands taller
						}
					}
				} else {
					p.set(c, u, cornice, v, b(p.hash(u, cornice, v, 4) == 0 ? Blocks.STRIPPED_CHERRY_WOOD : Blocks.CHERRY_PLANKS));
				}
			}
		}
		// keystone: calcite in the middle of the ring course on both faces
		int cu = a0 + ow / 2;
		for (int v : new int[] {0, d - 1}) {
			p.set(c, cu, crown, v, CALCITE);
			if (ow % 2 == 0) {
				p.set(c, cu - 1, crown, v, CALCITE);
			}
		}

		// --- lanterns hang at the edges of the opening (the centre stays clear), mid depth
		int lv = d / 2;
		p.put(c, a0, side - 1, lv, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		p.put(c, a0 + ow - 1, side - 1, lv, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
	}
}
