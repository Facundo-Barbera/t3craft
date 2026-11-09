package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.log;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.stairTop;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.trapdoor;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * A tea house (chashitsu) at the end of a roji garden. A small plaster hut under a bamboo-shingle hip
 * roof with up-curled corners; the guest enters by a tiny crawl door (nijiriguchi) from a stone step.
 * In front: a brushwood fence with a roofed gate, stepping stones over moss and raked gravel, a stone
 * water basin (tsukubai), a stone lantern and a cherry. Meant for the odd small lot.
 *
 * <pre>
 *  plan (front = v 0)
 *  v0     fence ═══╤ gate ╤═══  (roofed, lanterns)
 *  v1-3   roji: stepping stones, tsukubai, ishidoro, cherry, moss
 *  d-6    eave ring 0  ┐
 *  d-5…   hut 5 × 4, crawl door in the front wall   roof rings 0-2, bamboo, lightning-rod finial
 *  d-2    back wall    ┘
 *  d-1    eave
 * </pre>
 *
 * Size: 9–12 × 10–12 × 10+.
 */
public final class HouseJapaneseTeahouse implements Variant {
	private static final int BEAM = 4;

	@Override
	public String id() {
		return "house_japanese_teahouse";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 9;
	}

	@Override
	public int minD() {
		return 10;
	}

	@Override
	public int minH() {
		return 10;
	}

	@Override
	public int maxW() {
		return 12;
	}

	@Override
	public int maxD() {
		return 12;
	}

	private static BlockState stone(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			case 1 -> b(Blocks.COBBLED_DEEPSLATE);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	private static Direction cornerFacing(boolean top, boolean left) {
		if (top) {
			return left ? Direction.SOUTH : Direction.WEST;
		}
		return left ? Direction.EAST : Direction.NORTH;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x5445_4148_4F55L);
		int w = p.w();
		int d = p.d();
		int bw = 5; // hut width
		int bu0 = (w - bw) / 2; // hut u0
		int bu1 = bu0 + bw - 1;
		int bv0 = d - 5; // hut front wall
		int bv1 = d - 2; // hut back wall
		int cu = bu0 + bw / 2; // crawl door column
		boolean mapleRight = r.nextBoolean();

		groundCover(c, p, bu0, bu1, bv0, bv1);
		hut(c, p, bu0, bu1, bv0, bv1, cu);
		roof(c, p, bu0 - 1, bu1 + 1, bv0 - 1, bv1 + 1);
		eaves(c, p, bu0, bu1, bv0, bv1);
		fenceAndGate(c, p, w, d, cu);
		roji(c, p, w, d, cu, bv0, mapleRight);
	}

	// ------------------------------------------------------------------ ground

	/** [palette] moss, raked gravel and bare earth in patches, a stone footing under the hut. */
	private static void groundCover(CitadelCanvas c, Plot p, int bu0, int bu1, int bv0, int bv1) {
		for (int u = 0; u < p.w(); u++) {
			for (int v = 0; v < p.d(); v++) {
				boolean hut = u >= bu0 - 1 && u <= bu1 + 1 && v >= bv0 - 1;
				if (hut) {
					continue;
				}
				BlockState st = switch (p.hash(u, 0, v, 7)) {
					case 0 -> b(Blocks.COARSE_DIRT);
					case 1, 2 -> b(Blocks.GRAVEL);
					default -> b(Blocks.MOSS_BLOCK);
				};
				p.set(c, u, 0, v, st);
			}
		}
	}

	// ------------------------------------------------------------------ the hut

	private static void hut(CitadelCanvas c, Plot p, int bu0, int bu1, int bv0, int bv1, int cu) {
		// footing and floor
		for (int u = bu0; u <= bu1; u++) {
			for (int v = bv0; v <= bv1; v++) {
				boolean edge = u == bu0 || u == bu1 || v == bv0 || v == bv1;
				p.set(c, u, 0, v, edge ? stone(p, u, 0, v) : b(Blocks.BAMBOO_MOSAIC));
			}
		}
		for (int y = 1; y <= 3; y++) {
			for (int u = bu0; u <= bu1; u++) {
				for (int v : new int[] {bv0, bv1}) {
					boolean corner = u == bu0 || u == bu1;
					BlockState st;
					if (corner) {
						st = log(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
					} else if (v == bv0 && u == cu && y == 1) {
						// [depth] the crawl door: a small hatch sunk into the wall
						st = trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, Half.BOTTOM);
					} else if (y == 2 && (u - bu0) % 2 == 0) {
						// [depth] a bamboo-lattice window: crossed rails behind the plaster line
						st = fence(Blocks.BAMBOO_FENCE, true, true, true, true);
					} else if (y == 1) {
						st = p.hash(u, y, v, 3) == 0 ? b(Blocks.STONE_BRICKS) : b(Blocks.TUFF_BRICKS);
					} else {
						st = p.hash(u, y, v, 8) == 0 ? b(Blocks.MUD_BRICKS) : b(Blocks.PACKED_MUD);
					}
					p.set(c, u, y, v, st);
				}
			}
			for (int v = bv0 + 1; v < bv1; v++) {
				for (int u : new int[] {bu0, bu1}) {
					BlockState st = y == 2 && v == (bv0 + bv1) / 2 ? fence(Blocks.BAMBOO_FENCE, true, false, true, false)
							: y == 1 ? b(Blocks.TUFF_BRICKS) : p.hash(u, y, v, 8) == 0 ? b(Blocks.MUD_BRICKS) : b(Blocks.PACKED_MUD);
					p.set(c, u, y, v, st);
				}
			}
		}
		// the crawl door's stone step and a hanging lantern beside it
		p.set(c, cu, 0, bv0 - 1, b(Blocks.POLISHED_ANDESITE));
		p.set(c, cu, 1, bv0 - 1, slab(Blocks.STONE_BRICK_SLAB));
		p.set(c, cu + 1, 1, bv0 - 1, b(Blocks.POTTED_BAMBOO));
		p.set(c, cu - 1, 1, bv0 - 1, b(Blocks.POTTED_CHERRY_SAPLING));
		// top beams all round
		for (int u = bu0; u <= bu1; u++) {
			p.set(c, u, BEAM, bv0, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			p.set(c, u, BEAM, bv1, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
		}
		for (int v = bv0; v <= bv1; v++) {
			p.set(c, bu0, BEAM, v, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
			p.set(c, bu1, BEAM, v, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
		}
	}

	// ------------------------------------------------------------------ roof

	/**
	 * [finish] a hipped bamboo roof of three stair rings, 1:1. Each ring corner is an outer-corner stair; the
	 * four corners of the lowest ring sweep up (with an upside-down stair under them), the top is capped
	 * with a slab row and a lightning-rod finial.
	 */
	private static void roof(CitadelCanvas c, Plot p, int ru0, int ru1, int rv0, int rv1) {
		for (int k = 0; k <= 2; k++) {
			int u0 = ru0 + k;
			int u1 = ru1 - k;
			int v0 = rv0 + k;
			int v1 = rv1 - k;
			Block mat = k == 1 ? Blocks.BAMBOO_STAIRS : Blocks.BAMBOO_MOSAIC_STAIRS; // [palette] a golden thatch, nothing like the dark tiles of the engawa house
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					boolean top = v == v0;
					boolean bot = v == v1;
					boolean left = u == u0;
					boolean right = u == u1;
					if (!(top || bot || left || right)) {
						continue;
					}
					int y = 4 + k;
					BlockState st;
					boolean corner = (top || bot) && (left || right);
					if (corner) {
						st = stair(mat, cornerFacing(top, left), Half.BOTTOM, StairsShape.OUTER_LEFT);
					} else if (top) {
						st = stair(mat, Direction.SOUTH);
					} else if (bot) {
						st = stair(mat, Direction.NORTH);
					} else if (left) {
						st = stair(mat, Direction.EAST);
					} else {
						st = stair(mat, Direction.WEST);
					}
					int cd = Math.min(u - u0, u1 - u) + Math.min(v - v0, v1 - v);
					boolean lifted = k == 0 && cd <= 1;
					if (lifted) {
						y = 5;
					}
					p.set(c, u, y, v, st);
					if (lifted) {
						BlockState sup = corner ? stair(Blocks.DARK_OAK_STAIRS, cornerFacing(top, left), Half.TOP, StairsShape.OUTER_LEFT)
								: stairTop(Blocks.DARK_OAK_STAIRS, top ? Direction.NORTH : bot ? Direction.SOUTH : left ? Direction.WEST : Direction.EAST);
						p.set(c, u, 4, v, sup);
					}
				}
			}
		}
		// cap: the flat top of the hip (ring 2's inside) gets a row of slabs, a finial at the centre
		int u0 = ru0 + 2;
		int u1 = ru1 - 2;
		int v0 = rv0 + 2;
		int v1 = rv1 - 2;
		for (int u = u0 + 1; u < u1; u++) {
			for (int v = v0 + 1; v < v1; v++) {
				p.set(c, u, 6, v, b(Blocks.BAMBOO_MOSAIC));
			}
		}
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				p.set(c, u, 7, v, slab(Blocks.DARK_OAK_SLAB));
			}
		}
		p.set(c, (u0 + u1) / 2, 8, (v0 + v1) / 2, JapaneseKit.wall(Blocks.DEEPSLATE_BRICK_WALL, true, false, false, false, false));
		p.set(c, (u0 + u1) / 2, 9, (v0 + v1) / 2, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		// [finish] a lantern hanging at each lifted corner
		for (int[] cc : new int[][] {{ru0, rv0}, {ru1, rv0}, {ru0, rv1}, {ru1, rv1}}) {
			p.put(c, cc[0], 3, cc[1], lantern(true), CitadelCanvas.HANGING);
		}
	}

	/**
	 * [depth] the thatch overhangs the walls on beam tails: a dark oak log pokes out of the wall under the eave at
	 * every second cell, with an upside-down stair hugging the wall under it.
	 */
	private static void eaves(CitadelCanvas c, Plot p, int bu0, int bu1, int bv0, int bv1) {
		Block log = Blocks.DARK_OAK_LOG;
		Block st = Blocks.DARK_OAK_STAIRS;
		for (int u = bu0; u <= bu1; u += 2) {
			EaveKit.corbel(c, p, u, 3, bv0 - 1, Direction.SOUTH, log, st);
			EaveKit.corbel(c, p, u, 3, bv1 + 1, Direction.NORTH, log, st);
		}
		for (int v = bv0 + 1; v <= bv1 - 1; v += 2) {
			EaveKit.corbel(c, p, bu0 - 1, 3, v, Direction.EAST, log, st);
			EaveKit.corbel(c, p, bu1 + 1, 3, v, Direction.WEST, log, st);
		}
	}

	// ------------------------------------------------------------------ fence and gate (v 0)

	/** [depth] a bamboo fence two rails high along the street and both flanks, and a roofed gate in the middle. */
	private static void fenceAndGate(CitadelCanvas c, Plot p, int w, int d, int cu) {
		int gu = cu; // gate centre aligned with the crawl door's path
		for (int u = 0; u < w; u++) {
			if (Math.abs(u - gu) <= 1) {
				continue;
			}
			boolean l = u > 0 && Math.abs(u - 1 - gu) > 1;
			boolean r = u < w - 1 && Math.abs(u + 1 - gu) > 1;
			p.set(c, u, 1, 0, fence(Blocks.BAMBOO_FENCE, false, r, false, l));
			p.set(c, u, 2, 0, fence(Blocks.BAMBOO_FENCE, false, r, false, l));
		}
		for (int v = 1; v < d - 6; v++) {
			for (int u : new int[] {0, w - 1}) {
				p.set(c, u, 1, v, fence(Blocks.BAMBOO_FENCE, v > 1, false, true, false));
				p.set(c, u, 2, v, fence(Blocks.BAMBOO_FENCE, false, false, true, false));
			}
		}
		// gate posts and a small tiled roof
		for (int u : new int[] {gu - 1, gu + 1}) {
			for (int y = 1; y <= 3; y++) {
				p.set(c, u, y, 0, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
			}
		}
		for (int u = gu - 2; u <= gu + 2; u++) {
			p.set(c, u, 4, 0, Math.abs(u - gu) == 2 ? stair(Blocks.BAMBOO_MOSAIC_STAIRS, u < gu ? Direction.EAST : Direction.WEST)
					: slab(Blocks.BAMBOO_MOSAIC_SLAB));
		}
		p.set(c, gu, 4, 0, b(Blocks.BAMBOO_MOSAIC));
		EaveKit.tail(c, p, gu - 2, 3, 0, Direction.EAST, Blocks.SPRUCE_LOG);
		EaveKit.tail(c, p, gu + 2, 3, 0, Direction.WEST, Blocks.SPRUCE_LOG);
		p.put(c, gu, 3, 0, lantern(true), CitadelCanvas.HANGING);
		p.set(c, gu, 0, 0, b(Blocks.POLISHED_ANDESITE));
		p.set(c, gu - 1, 0, 0, stone(p, gu - 1, 0, 0));
		p.set(c, gu + 1, 0, 0, stone(p, gu + 1, 0, 0));
	}

	// ------------------------------------------------------------------ roji (the garden path)

	private static void roji(CitadelCanvas c, Plot p, int w, int d, int cu, int bv0, boolean right) {
		// stepping stones, staggered, from the gate to the crawl door
		for (int v = 1; v < bv0 - 1; v++) {
			int u = cu + (v % 2 == 0 ? 0 : (right ? -1 : 1));
			p.set(c, u, 0, v, b(Blocks.POLISHED_ANDESITE));
			if (v % 2 == 1) {
				p.set(c, cu, 0, v, b(Blocks.STONE_BRICKS));
			}
		}
		// the tsukubai: a stone basin with water, a bamboo spout and a low rock on each side
		int bu = right ? cu + 2 : cu - 2;
		int bvv = Math.max(2, bv0 - 3);
		p.set(c, bu, 0, bvv, b(Blocks.MOSSY_COBBLESTONE));
		p.set(c, bu, 1, bvv, b(Blocks.WATER_CAULDRON));
		p.set(c, bu + (right ? 1 : -1), 1, bvv, b(Blocks.BAMBOO));
		p.set(c, bu + (right ? 1 : -1), 0, bvv, b(Blocks.MOSSY_COBBLESTONE));
		p.set(c, bu, 0, bvv - 1, stair(Blocks.MOSSY_COBBLESTONE_STAIRS, Direction.SOUTH));
		// the stone lantern on the other side and the cherry behind it
		int lu = right ? cu - 2 : cu + 2;
		JapaneseKit.stoneLantern(c, p, lu, 1, bvv);
		int tu = right ? cu - 3 : cu + 3;
		JapaneseKit.sakura(c, p, tu < 2 ? 2 : Math.min(tu, w - 3), 1, Math.min(bv0 - 2, 3), 0);
		// azalea and moss tufts in the corners
		p.set(c, 1, 1, 1, CitadelCanvas.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		p.set(c, w - 2, 1, 1, CitadelCanvas.leaves(Blocks.AZALEA_LEAVES));
	}
}
