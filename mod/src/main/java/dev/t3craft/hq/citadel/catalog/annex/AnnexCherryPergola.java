package dev.t3craft.hq.citadel.catalog.annex;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Support;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Cherry pergola, an open-air room to sit and work in: a raised floor (flagstones with a plank inlay under
 * the table, or a plank deck) inside a ring of low slab steps; posts with a deepslate base and a bracket
 * under the beams; stripped-cherry beams with cross beams over both ends (a portal at the front and at the
 * back) and carved rafter tails; a trellis of cherry fence and leaves at the back. Inside, a dining table
 * with chairs and a long bench against the trellis, corner pots, and by seed one of three uses (garden
 * dining with barrels and smokers, a reading corner with lecterns and low shelves, or a workstation with
 * crafting table and loom). Lanterns hang on chains from the rafters over the table and from the beams
 * over the side entrances. Seed also picks the cover (open, partial plank roof over the table, or a dense
 * leaf roof), the plants and the floor.
 *
 * <pre>
 *  plan (w = 8, d = 8)                 section (clear height k = 4..5 under the beams)
 *  v0  ╋ ═ ═ ═ ═ ═ ═ ╋   front, open   y=k+2   ╤══╤══╤══╤  rafters, leaves on top
 *  v1  ║ ✿ . . . . ✿ ║                y=k+1  ═╧══╧══╧══╧═  beams
 *  v2  ║ . . . . . . ║                y=k      ¦  o  ¦      bracket, hanging lantern
 *  v3  ║ . ▬ . ▬ . . ║  chairs         y=2..   ║  ╥╥╥  ║      posts, table
 *  v4  ◀ ┬ ┬ ┬ ┬ ┬ ▶ ║  table + ends   y=1     ║ ▬▬▬ ▬ ║      bench, chairs
 *  v5  ║ A ▬ ▬ ▬ C ║  bench, fixtures  y=0     ▓▒▒▒▒▒▒▒▓      slab ring, floor, deepslate bases
 *  v6  ╋ ≡ ≡ ≡ ≡ ≡ ╋   trellis (back)
 * </pre>
 *
 * Size: 5–11 × 4–10 × 5+. Below 7 × 6 (or under 6 high) it is a compact bower: floor, bench, trellis, pots.
 */
public final class AnnexCherryPergola implements Variant {
	private static final BlockState LOG = b(Blocks.STRIPPED_CHERRY_LOG);
	private static final BlockState BEAM_Z = b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
	private static final BlockState BEAM_X = b(Blocks.STRIPPED_DARK_OAK_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X);
	private static final BlockState LEAVES = leaves(Blocks.CHERRY_LEAVES);
	private static final BlockState AZALEA_LEAVES = leaves(Blocks.FLOWERING_AZALEA_LEAVES);
	private static final BlockState VINE = b(Blocks.VINE);

	@Override
	public String id() {
		return "annex_cherry_pergola";
	}

	@Override
	public Family family() {
		return Family.ANNEX;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 4;
	}

	@Override
	public int minH() {
		return 5;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 10;
	}

	/** The numbers every part of the build shares. */
	private static final class G {
		final int w;
		final int d;
		final int h;
		final int k; // clear cells under the beams (y1..k)
		final int beam;
		final int raf;
		final int mid; // middle post row on long sides, or -1
		final int tv; // table row (a rafter row)
		final boolean full; // table, chairs and fixtures
		final int ua; // table first and last column
		final int ub;
		final int fl; // fixture cells at each end of the back row
		final int use; // 0 dining, 1 reading, 2 work
		final int cover; // 0 open, 1 plank bay, 2 dense leaves
		final int plant; // 0 cherry, 1 azalea, 2 vines
		final int floor; // 0 flagstone with plank inlay, 1 plank deck
		final boolean[][] leaf;

		G(Plot p) {
			w = p.w();
			d = p.d();
			h = p.h();
			k = Math.max(2, Math.min(4, h - 3));
			beam = k + 1;
			raf = k + 2;
			mid = d >= 9 ? (d - 1) / 2 : -1;
			full = w >= 7 && d >= 6 && k >= 3;
			tv = full ? d - 3 : d / 2;
			int len = Math.min(w - 4, 5);
			ua = (w - len) / 2;
			ub = ua + len - 1;
			fl = w >= 10 ? 2 : 1;
			use = p.hash(3, 1, 4, 3);
			cover = p.hash(5, 2, 1, 3);
			plant = p.hash(2, 7, 3, 3);
			floor = p.hash(7, 3, 2, 3) == 0 ? 1 : 0;
			leaf = new boolean[w][d];
		}

		boolean postRow(int v) {
			return v == 0 || v == d - 1 || v == mid;
		}

		boolean post(int u, int v) {
			return (u == 0 || u == w - 1) && postRow(v);
		}

		boolean ring(int u, int v) {
			return u == 0 || u == w - 1 || v == 0 || v == d - 1;
		}

		/** Rafters run over the table row and every second row from it, plus both ends. */
		boolean rafterRow(int v) {
			return v == 0 || v == d - 1 || Math.floorMod(v - tv, 2) == 0;
		}

		boolean zone(int u, int v) {
			return full && v >= tv - 1 && v <= d - 2 && u >= ua - 1 && u <= ub + 1;
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		G g = new G(p);
		floor(c, p, g);
		posts(c, p, g);
		beams(c, p, g);
		trellis(c, p, g);
		furniture(c, p, g);
		canopy(c, p, g);
		vines(c, p, g);
		lights(c, p, g);
	}

	// ------------------------------------------------------------------ floor (y0)

	/**
	 * [palette] a ring of low steps (stone-brick slabs, mossy and polished andesite mixed by hash) and
	 * inside a floor that marks the zone: flagstones with a cherry-plank inlay under the table, or a
	 * deck of cherry planks and bamboo mosaic. Pink petals strewn on it.
	 */
	private static void floor(CitadelCanvas c, Plot p, G g) {
		Direction[] petals = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		for (int v = 0; v < g.d; v++) {
			for (int u = 0; u < g.w; u++) {
				if (g.post(u, v)) {
					continue;
				}
				if (g.ring(u, v)) {
					BlockState st = switch (p.hash(u, 0, v, 6)) {
						case 0, 1 -> slab(Blocks.MOSSY_STONE_BRICK_SLAB);
						case 2 -> slab(Blocks.POLISHED_ANDESITE_SLAB);
						default -> slab(Blocks.STONE_BRICK_SLAB);
					};
					p.set(c, u, 0, v, st);
					continue;
				}
				BlockState st;
				if (g.floor == 1) {
					st = g.zone(u, v) ? b(Blocks.BAMBOO_MOSAIC) : p.hash(u, 0, v, 4) == 0 ? b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z) : b(Blocks.CHERRY_PLANKS);
				} else if (g.zone(u, v)) {
					st = p.hash(u, 0, v, 5) == 0 ? b(Blocks.BAMBOO_MOSAIC) : b(Blocks.CHERRY_PLANKS);
				} else {
					st = switch (p.hash(u, 0, v, 6)) {
						case 0 -> b(Blocks.MOSSY_STONE_BRICKS);
						case 1 -> b(Blocks.CRACKED_STONE_BRICKS);
						case 2, 3 -> b(Blocks.POLISHED_ANDESITE);
						default -> b(Blocks.STONE_BRICKS);
					};
				}
				p.set(c, u, 0, v, st);
				if (p.hash(u, 1, v, 14) == 0 && v > 0 && v < g.d - 2 && !g.zone(u, v)) {
					p.set(c, u, 1, v, b(Blocks.PINK_PETALS).setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + p.hash(u, 2, v, 3))
							.setValue(BlockStateProperties.HORIZONTAL_FACING, petals[p.hash(u, 1, v, 4)]));
				}
			}
		}
	}

	// ------------------------------------------------------------------ posts, brackets, beams

	/** [palette] dark → light: deepslate-brick base, polished deepslate collar, stripped cherry shaft. */
	private static void posts(CitadelCanvas c, Plot p, G g) {
		for (int u : new int[] {0, g.w - 1}) {
			for (int v = 0; v < g.d; v++) {
				if (!g.postRow(v)) {
					continue;
				}
				p.set(c, u, 0, v, p.hash(u, 0, v, 3) == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS));
				p.set(c, u, 1, v, b(Blocks.POLISHED_DEEPSLATE));
				for (int y = 2; y <= g.k; y++) {
					p.set(c, u, y, v, LOG);
				}
			}
		}
	}

	/**
	 * [depth] stripped-cherry beams on both sides, upside-down stair brackets under them beside every
	 * post, cross beams over the front and back, and fence rafters with a chamfered stair tail at each end.
	 */
	private static void beams(CitadelCanvas c, Plot p, G g) {
		int w = g.w;
		int d = g.d;
		for (int u : new int[] {0, w - 1}) {
			for (int v = 0; v < d; v++) {
				p.set(c, u, g.beam, v, BEAM_Z);
				if (g.postRow(v) || g.k < 3) {
					continue;
				}
				if (g.postRow(v - 1)) {
					p.set(c, u, g.k, v, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH).setValue(BlockStateProperties.HALF, Half.TOP));
				} else if (g.postRow(v + 1)) {
					p.set(c, u, g.k, v, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH).setValue(BlockStateProperties.HALF, Half.TOP));
				}
			}
		}
		for (int v : new int[] {0, d - 1}) {
			for (int u = 1; u < w - 1; u++) {
				p.set(c, u, g.beam, v, BEAM_X);
			}
		}
		for (int v = 0; v < d; v++) {
			if (!g.rafterRow(v)) {
				continue;
			}
			p.set(c, 0, g.raf, v, stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST));
			p.set(c, w - 1, g.raf, v, stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST));
			for (int u = 1; u < w - 1; u++) {
				p.set(c, u, g.raf, v, b(Blocks.DARK_OAK_FENCE)
						.setValue(BlockStateProperties.WEST, true)
						.setValue(BlockStateProperties.EAST, true));
			}
		}
	}

	// ------------------------------------------------------------------ trellis (back wall)

	/**
	 * [finish] a lattice of cherry fence (rails at y2 and under the beam, uprights every other cell) with
	 * leaves growing through it. It closes the back; the front and both sides stay open.
	 */
	private static void trellis(CitadelCanvas c, Plot p, G g) {
		int v = g.d - 1;
		BlockState leafA = g.plant == 1 ? AZALEA_LEAVES : LEAVES;
		for (int u = 1; u < g.w - 1; u++) {
			boolean upright = u == 1 || u == g.w - 2 || u % 3 == 1;
			for (int y = 1; y <= g.k; y++) {
				boolean rail = y == 2 || y == g.k;
				if (upright || rail) {
					BlockState f = b(Blocks.DARK_OAK_FENCE);
					if (rail) {
						f = f.setValue(BlockStateProperties.WEST, true).setValue(BlockStateProperties.EAST, true);
					}
					p.set(c, u, y, v, f);
				} else if (y >= 3 && p.hash(u, y, v, 3) == 0) {
					p.set(c, u, y, v, p.hash(u, y, v, g.plant == 1 ? 2 : 4) == 0 ? AZALEA_LEAVES : leafA);
				}
			}
		}
	}

	// ------------------------------------------------------------------ furniture

	private static BlockState pot(Plot p, G g, int u, int v) {
		Block[][] sets = {
			{Blocks.POTTED_PINK_TULIP, Blocks.POTTED_AZALEA, Blocks.POTTED_CHERRY_SAPLING},
			{Blocks.POTTED_FLOWERING_AZALEA, Blocks.POTTED_ALLIUM, Blocks.POTTED_FERN},
			{Blocks.POTTED_CORNFLOWER, Blocks.POTTED_FLOWERING_AZALEA, Blocks.POTTED_PINK_TULIP},
		};
		return b(sets[g.plant][p.hash(u, 1, v, 3)]);
	}

	private static BlockState seat(Direction back) {
		return stairs(Blocks.CHERRY_STAIRS, back);
	}

	/** Table, chairs, long bench, pots and the fixtures of the chosen use; the compact bower gets bench and pots. */
	private static void furniture(CitadelCanvas c, Plot p, G g) {
		int w = g.w;
		int d = g.d;
		if (g.k < 3) {
			if (w >= 5 && d >= 5) {
				p.set(c, 1, 1, 1, pot(p, g, 1, 1));
				p.set(c, w - 2, 1, 1, pot(p, g, w - 2, 1));
			}
			return;
		}
		int bv = d - 2; // bench row, against the trellis
		if (!g.full) {
			for (int u = 1; u < w - 1; u++) {
				p.set(c, u, 1, bv, seat(Direction.NORTH));
			}
			if (w >= 6 && d >= 6) {
				p.set(c, 1, 1, 1, pot(p, g, 1, 1));
				p.set(c, w - 2, 1, 1, pot(p, g, w - 2, 1));
			}
			return;
		}
		// long bench, between the fixtures of both ends; the sitter faces the front (stairs face the sitter's way)
		for (int u = g.fl + 1; u <= w - 2 - g.fl; u++) {
			p.set(c, u, 1, bv, seat(Direction.NORTH));
		}
		// table, one block high: a fence post at each end, cherry slabs (top half) between them
		for (int u = g.ua; u <= g.ub; u++) {
			boolean end = u == g.ua || u == g.ub;
			p.set(c, u, 1, g.tv, end ? b(Blocks.DARK_OAK_FENCE)
					: b(Blocks.CHERRY_SLAB).setValue(BlockStateProperties.SLAB_TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
		}
		// chairs at both ends of the table and on its front side, facing the table
		p.set(c, g.ua - 1, 1, g.tv, seat(Direction.EAST));
		p.set(c, g.ub + 1, 1, g.tv, seat(Direction.WEST));
		for (int u = g.ua + 1; u < g.ub; u += 2) {
			p.set(c, u, 1, g.tv - 1, seat(Direction.SOUTH));
		}
		// a pot on the table's first post
		p.set(c, g.ua, 2, g.tv, pot(p, g, g.ua, g.tv));
		p.set(c, g.ub, 2, g.tv, b(Blocks.LANTERN)); // a standing lantern on the other end post
		// pots flanking the entrance, inside the front corners
		p.set(c, 1, 1, 1, pot(p, g, 1, 1));
		p.set(c, w - 2, 1, 1, pot(p, g, w - 2, 1));
		fixtures(c, p, g);
	}

	/** The use of the place: one run of fixtures in each back corner, facing the front. */
	private static void fixtures(CitadelCanvas c, Plot p, G g) {
		int bv = g.d - 2;
		for (int i = 0; i < g.fl; i++) {
			int ul = 1 + i;
			int ur = g.w - 2 - i;
			BlockState left;
			BlockState right;
			switch (g.use) {
				case 1 -> {
					left = i == 0 ? lectern() : shelf();
					right = i == 0 ? shelf() : lectern();
				}
				case 2 -> {
					left = i == 0 ? b(Blocks.CRAFTING_TABLE) : b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
					right = i == 0 ? b(Blocks.LOOM).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
							: b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH);
				}
				default -> {
					left = b(Blocks.BARREL).setValue(BlockStateProperties.FACING, i == 0 ? Direction.UP : Direction.NORTH);
					right = b(Blocks.SMOKER).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
				}
			}
			p.set(c, ul, 1, bv, left);
			p.set(c, ur, 1, bv, right);
			if (g.use != 2 && i == g.fl - 1 && g.fl > 1) {
				// a plant on top of the outer pair, where it is not a work surface
				p.set(c, ul, 2, bv, pot(p, g, ul, bv));
				p.set(c, ur, 2, bv, pot(p, g, ur, bv));
			}
		}
	}

	private static BlockState lectern() {
		return b(Blocks.LECTERN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH).setValue(BlockStateProperties.HAS_BOOK, true);
	}

	private static BlockState shelf() {
		return b(Blocks.CHISELED_BOOKSHELF).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.SLOT_0_OCCUPIED, true).setValue(BlockStateProperties.SLOT_1_OCCUPIED, true)
				.setValue(BlockStateProperties.SLOT_2_OCCUPIED, true).setValue(BlockStateProperties.SLOT_3_OCCUPIED, true)
				.setValue(BlockStateProperties.SLOT_4_OCCUPIED, true).setValue(BlockStateProperties.SLOT_5_OCCUPIED, true);
	}

	// ------------------------------------------------------------------ roof

	/**
	 * [finish] rafters carry the cover: open (leaf tufts between the rafters), a plank bay of cherry slabs
	 * over the table with leaves on top, or a dense leaf roof. A loose layer on top roughens the edge.
	 * Nothing hangs lower than the rafters except vines and lanterns, so the head room stays clear.
	 */
	private static void canopy(CitadelCanvas c, Plot p, G g) {
		int w = g.w;
		int d = g.d;
		for (int v = 0; v < d; v++) {
			boolean rafter = g.rafterRow(v);
			boolean bay = g.cover == 1 && g.full && !rafter && v >= g.tv - 1 && v <= g.tv + 1;
			for (int u = 0; u < w; u++) {
				if (!rafter) {
					if (bay) {
						p.set(c, u, g.raf, v, slab(Blocks.CHERRY_SLAB));
					} else if (switch (g.cover) {
						case 0 -> p.hash(u, g.raf, v, 3) == 0;
						case 1 -> p.hash(u, g.raf, v, 2) == 0;
						default -> p.hash(u, g.raf, v, 6) != 0;
					}) {
						p.set(c, u, g.raf, v, p.hash(u, g.raf, v, g.plant == 1 ? 3 : 7) == 0 ? AZALEA_LEAVES : LEAVES);
						g.leaf[u][v] = true;
					}
				}
				if (g.h > g.raf + 1) {
					boolean edge = u == 0 || u == w - 1 || v == 0 || v == d - 1;
					int n = bay ? 3 : edge ? 3 : 2;
					if (p.hash(u, g.raf + 1, v, n) == 0 && Support.present(c, p, u, g.raf, v)) {
						p.set(c, u, g.raf + 1, v, LEAVES);
					}
				}
			}
		}
	}

	/**
	 * Vines: up the inner face of every post, draping from some beams, and hanging from leaves overhead.
	 * No collision, and none lower than the top of the clear height minus two.
	 */
	private static void vines(CitadelCanvas c, Plot p, G g) {
		int w = g.w;
		int d = g.d;
		for (int v = 0; v < d; v++) {
			for (int side = 0; side < 2; side++) {
				int u = side == 0 ? 1 : w - 2;
				BlockState cling = VINE.setValue(side == 0 ? BlockStateProperties.WEST : BlockStateProperties.EAST, true);
				if (v == d - 1) {
					continue; // the trellis
				}
				if (g.postRow(v)) {
					int top = v == 0 ? g.k : g.beam;
					for (int y = 2; y <= top; y++) {
						if (y < g.beam || p.hash(side, 2, v, 3) != 0) {
							p.set(c, u, y, v, cling);
						}
					}
				} else if (p.hash(side, g.beam, v, 3) == 0) {
					p.set(c, u, g.beam, v, cling);
					p.set(c, u, g.k, v, cling);
				}
			}
			if (g.rafterRow(v)) {
				continue;
			}
			for (int u = 2; u < w - 2; u++) {
				if (g.leaf[u][v] && p.hash(u, g.beam, v, g.plant == 2 ? 2 : 3) == 0) {
					p.set(c, u, g.beam, v, VINE.setValue(BlockStateProperties.UP, true));
					if (p.hash(u, g.k, v, 4) == 0 && g.k >= 4) {
						p.set(c, u, g.k, v, VINE.setValue(BlockStateProperties.UP, true));
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ light

	/**
	 * Lanterns on chains from the rafters over the table, one lantern under the middle of each side beam,
	 * and one under each cross beam, all above head height.
	 */
	private static void lights(CitadelCanvas c, Plot p, G g) {
		if (g.k < 4) {
			return; // low: a lantern would leave under 2.85 of head room
		}
		int w = g.w;
		int d = g.d;
		BlockState chain = b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
		BlockState lamp = b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true);
		// chain lanterns from the rafter row in front of the table (over bare floor, never over chairs or the table)
		int lv = g.full ? g.tv - 2 : g.tv;
		int[] us = g.full && g.ub - g.ua >= 4 ? new int[] {g.ua + 1, g.ub - 1} : new int[] {w / 2};
		if (lv >= 1 && g.rafterRow(lv)) {
			for (int u : us) {
				p.put(c, u, g.beam, lv, chain, CitadelCanvas.HANGING);
				p.put(c, u, g.k, lv, lamp, CitadelCanvas.HANGING);
			}
		}
		// under the side beams, in the middle of each gap between posts wider than 2
		int prev = 0;
		for (int v = 1; v < d; v++) {
			if (!g.postRow(v)) {
				continue;
			}
			int gapW = v - prev - 1;
			if (gapW >= 3) {
				int mv = prev + 1 + gapW / 2;
				p.put(c, 0, g.k, mv, lamp, CitadelCanvas.HANGING);
				p.put(c, w - 1, g.k, mv, lamp, CitadelCanvas.HANGING);
			}
			prev = v;
		}
		// the entrance: under the front cross beam
		p.put(c, w / 2, g.k, 0, lamp, CitadelCanvas.HANGING);
	}
}
