package dev.t3craft.hq.citadel.catalog.gate;

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

/**
 * Neighbourhood gate: a stone arch between two crenellated piers, with a calcite keystone, a
 * machicolation course, hanging lanterns and pink pennants on the pier faces.
 *
 * <ul>
 * <li><b>Walkable centre</b>: the vault is at least 3 high on every opening cell and 4+ in the middle;
 *     the stairs that round it are inverted stairs above head height.</li>
 * <li><b>Gradient</b>: deepslate plinth, stone-brick body (cracked / mossy mix by hash), calcite trim
 *     and cherry accents on top.</li>
 * <li><b>Depth</b>: piers carry buttress corners and a plinth, the cornice projects with inverted
 *     stairs, the merlons alternate with crenels, the pennants sit in a recessed bay.</li>
 * </ul>
 * Size: 5–11 × 2–4 × 8–10.
 */
public final class GateArchBattlement implements Variant {
	private static final BlockState BRICK = b(Blocks.STONE_BRICKS);
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
	private BlockState wall(Plot p, int u, int y, int v) {
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
		int pl = w >= 9 ? 3 : w >= 7 ? 2 : 1; // pier width
		int ow = w - 2 * pl; // opening width
		int emax = (ow - 1) / 2;
		int side = p.h() >= 9 ? 4 : 3; // clear height at the edges of the opening
		int crown = side + emax; // y of the keystone course (the top of the vault)
		int cornice = crown + 1;
		int merlon = cornice + 1;
		int towerTop = Math.min(p.h() - 1, merlon + 1);
		int a0 = pl;
		int a1 = pl + ow - 1;

		// --- piers: deepslate plinth, stone-brick body, tower rising above the wall
		for (int sd = 0; sd < 2; sd++) {
			int u0 = sd == 0 ? 0 : w - pl;
			for (int u = u0; u < u0 + pl; u++) {
				for (int v = 0; v < d; v++) {
					p.set(c, u, 0, v, b(p.hash(u, 0, v, 3) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS));
					for (int y = 1; y < towerTop; y++) {
						p.set(c, u, y, v, wall(p, u, y, v));
					}
					crenellate(c, p, u, v, u0, pl, d, towerTop);
				}
			}
			// calcite and chiselled quoins up the outer corners, every other course
			int outer = sd == 0 ? 0 : w - 1;
			for (int y = 1; y < towerTop; y++) {
				for (int v : new int[] {0, d - 1}) {
					if (y % 2 == 1) {
						p.set(c, outer, y, v, p.hash(outer, y, v, 3) == 0 ? CALCITE : b(Blocks.CHISELED_STONE_BRICKS));
					}
				}
			}
			// polished deepslate pilaster at the foot of the inner edge, facing the opening
			int inner = sd == 0 ? pl - 1 : w - pl;
			for (int v = 0; v < d; v++) {
				p.set(c, inner, 1, v, b(Blocks.POLISHED_DEEPSLATE));
			}
			// pennant bay (recessed one block when the tower is thick enough)
			int bu = pl >= 3 ? u0 + 1 : sd == 0 ? pl - 1 : w - pl; // mirrored on both piers
			bay(c, p, bu, towerTop, pl >= 3 && d >= 3);
			// cherry post and lantern in the middle of the tower roof
			if (pl >= 2 && towerTop - 1 > 0) {
				int mu = u0 + pl / 2;
				int mv = d / 2;
				p.set(c, mu, towerTop, mv, b(Blocks.CHERRY_FENCE));
				if (towerTop + 1 < p.h()) {
					p.set(c, mu, towerTop + 1, mv, LANTERN);
				}
			}
		}

		// --- wall over the arch: vault stones, crown, cornice of inverted stairs, merlons and crenels
		for (int i = 0; i < ow; i++) {
			int e = Math.min(i, ow - 1 - i);
			int u = a0 + i;
			boolean left = i < ow - 1 - i;
			for (int v = 0; v < d; v++) {
				if (e < emax) {
					p.set(c, u, side + e, v, stairs(Blocks.STONE_BRICK_STAIRS, left ? Direction.WEST : Direction.EAST)
							.setValue(BlockStateProperties.HALF, Half.TOP));
				}
				for (int y = side + e + 1; y <= crown; y++) {
					p.set(c, u, y, v, wall(p, u, y, v));
				}
				boolean edge = v == 0 || v == d - 1;
				if (edge && d >= 3) {
					Direction in = v == 0 ? Direction.SOUTH : Direction.NORTH;
					p.set(c, u, cornice, v, stairs(Blocks.CHERRY_STAIRS, in).setValue(BlockStateProperties.HALF, Half.TOP));
					if ((u - a0) % 2 == 0) {
						p.set(c, u, merlon, v, wall(p, u, merlon, v));
					}
				} else {
					p.set(c, u, cornice, v, b(p.hash(u, cornice, v, 4) == 0 ? Blocks.STRIPPED_CHERRY_WOOD : Blocks.CHERRY_PLANKS));
					if (d == 2 && (u - a0) % 2 == 0) {
						p.set(c, u, merlon, v, wall(p, u, merlon, v));
					}
				}
			}
		}
		// keystone: calcite in the middle of the arch on both faces, with a cherry slab above as a cap
		int cu = a0 + ow / 2;
		for (int v : new int[] {0, d - 1}) {
			p.set(c, cu, crown, v, CALCITE);
			if (ow % 2 == 0) {
				p.set(c, cu - 1, crown, v, CALCITE);
			}
		}

		// --- lanterns hang at the edges of the vault (the centre stays clear), mid depth
		int lv = d / 2;
		p.put(c, a0, side - 1, lv, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		p.put(c, a1, side - 1, lv, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
	}

	/** Tower roof: a parapet of alternating merlons and crenels, corners always solid, the middle open. */
	private void crenellate(CitadelCanvas c, Plot p, int u, int v, int u0, int pl, int d, int top) {
		boolean edge = u == u0 || u == u0 + pl - 1 || v == 0 || v == d - 1;
		if (!edge) {
			return;
		}
		boolean corner = (u == u0 || u == u0 + pl - 1) && (v == 0 || v == d - 1);
		if (corner || (u + v) % 2 == 0 || pl == 1 && d == 2) {
			p.set(c, u, top, v, wall(p, u, top, v));
		}
	}

	/** A pink pennant on the front face of a tower, topped in gold and ending in a swallowtail. */
	private void bay(CitadelCanvas c, Plot p, int u, int top, boolean recessed) {
		Block wool = Blocks.WOOL.pick(DyeColor.PINK);
		int v = recessed ? 1 : 0;
		int y1 = Math.min(top - 2, 5);
		for (int y = y1; y >= 2; y--) {
			p.set(c, u, y, v, b(wool));
			if (recessed) {
				p.set(c, u, y, 0, b(Blocks.AIR));
			}
		}
		p.set(c, u, y1 + 1, v, b(Blocks.GOLD_BLOCK));
		if (recessed) {
			p.set(c, u, y1 + 1, 0, b(Blocks.AIR));
		}
		p.set(c, u, 1, v, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP));
	}
}
