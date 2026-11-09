package dev.t3craft.hq.citadel.catalog.bench;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
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

/**
 * A place to sit: a cherry bench (stairs, so the seat is half a block up and the stair's back is the first
 * backrest) held between dark deepslate planter pillars that act as arms, with a planter strip behind it
 * (the backrest wherever there is depth) and a lantern post at one end so it can be used at night.
 *
 * <p>Three layouts by plot size:
 * <ul>
 * <li><b>Row</b> (w &lt; 6 or d &lt; 4; what the leader asks for, 2–3 × 1 × 3): one bench, pillars at the ends,
 *     a backing planter strip when d ≥ 2 and flower beds behind it when d ≥ 3. A long plot is cut into runs of
 *     four seats by planter pillars (no stretched bench). With height it gets a shade: a cherry awning on fence
 *     posts (h ≥ 6) or a small cherry tree behind (h ≥ 7, d ≥ 3), chosen by seed.</li>
 * <li><b>U nook</b> (w ≥ 6, d ≥ 4): two benches face each other across a low table (slab top on wall legs, a
 *     potted shrub), planter strips behind each and behind the table, the front left open to sit down from the
 *     path. Wider or deeper plots keep the nook at most 8 × 6 and fill the rest with flower beds.</li>
 * <li><b>L nook</b> (w ≥ 6, d ≥ 5, by seed against the U): a bench along the back and one along the left,
 *     meeting at a little corner table with a lantern; the low table sits inside the L.</li>
 * </ul>
 * Nooks may carry a cherry pergola (h ≥ 6) with slats and a hanging lantern.
 *
 * <p>Planters are real: every plant stands on soil (moss, rooted dirt, dirt or coarse dirt by seed, never on bare
 * stone), so {@code CitadelSupport} has nothing to swap. Plant set by seed: blossom, wildflowers or lily-of-the-valley and azalea.
 * Palette: deepslate (base), cherry (seat, posts, roof), moss and soil, plants, a lantern.
 *
 * <p>Size: 2–12 × 1–8 × 2+. Always facing the front: sitters look out through it.
 */
public final class BenchCherryPlanters implements Variant {
	private static final BlockState BACK = b(Blocks.CHERRY_TRAPDOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
			.setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HALF, Half.BOTTOM);
	private static final BlockState LANTERN = b(Blocks.LANTERN);
	private static final BlockState WALL = b(Blocks.POLISHED_DEEPSLATE_WALL);
	private static final BlockState FENCE = b(Blocks.CHERRY_FENCE);

	@Override
	public String id() {
		return "bench_cherry_planters";
	}

	@Override
	public Family family() {
		return Family.BENCH;
	}

	@Override
	public int minW() {
		return 2;
	}

	@Override
	public int minD() {
		return 1;
	}

	@Override
	public int minH() {
		return 2;
	}

	@Override
	public int maxW() {
		return 12;
	}

	@Override
	public int maxD() {
		return 8;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		if (w >= 6 && d >= 4 && p.h() >= 3) {
			boolean l = d >= 5 && p.hash(7, 0, 7, 2) == 1;
			nook(c, p, l);
		} else {
			row(c, p);
		}
	}

	// ------------------------------------------------------------------ row

	private static void row(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int h = p.h();
		boolean[] pillar = new boolean[w];
		int lanternCol;
		int s0;
		int s1;
		if (w == 2) {
			// two seats and no pillar: a bench for two. With depth, one cell of the planter strip carries a lantern
			s0 = 0;
			s1 = 1;
			lanternCol = -1;
		} else if (w >= 4) {
			pillar[0] = true;
			pillar[w - 1] = true;
			s0 = 1;
			s1 = w - 2;
			lanternCol = p.hash(1, 0, 0, 2) == 0 ? 0 : w - 1;
		} else {
			boolean left = p.hash(0, 0, 0, 2) == 0;
			int col = left ? 0 : w - 1;
			pillar[col] = true;
			s0 = left ? 1 : 0;
			s1 = left ? w - 1 : w - 2;
			lanternCol = p.hash(2, 0, 0, 2) == 0 ? col : -1;
		}
		for (int u = s0; u <= s1; u++) { // a pillar breaks a long run: runs of four seats
			if ((u - s0) % 5 == 4 && s1 - u >= 2) {
				pillar[u] = true;
			}
		}
		int pd = Math.min(d, 2);

		// --- seats (+ a trapdoor backrest when nothing stands behind)
		for (int u = s0; u <= s1; u++) {
			if (pillar[u]) {
				continue;
			}
			seat(c, p, u, 0, Direction.SOUTH);
			if (d == 1) {
				set(c, p, u, 1, 0, w == 2 && p.hash(4, 4, 4, 2) == 1 ? b(Blocks.CHERRY_FENCE_GATE) : BACK);
			}
		}
		// --- pillars: planter at both ends and between runs; one end carries the lantern
		for (int u = 0; u < w; u++) {
			if (!pillar[u]) {
				continue;
			}
			for (int v = 0; v < pd; v++) {
				if (v == 0 && u == lanternCol) {
					lanternPost(c, p, u, v);
				} else {
					bed(c, p, u, v, true);
				}
			}
		}
		// --- backing planter strip behind the seats
		if (d >= 2) {
			for (int u = s0; u <= s1; u++) {
				if (!pillar[u]) {
					bed(c, p, u, 1, true);
				}
			}
		}
		if (w == 2 && d >= 2 && h >= 3) {
			set(c, p, p.hash(3, 3, 3, 2), 2, 1, LANTERN); // a lantern on the soil behind one seat
		}
		// --- flower beds behind
		for (int v = 2; v < d; v++) {
			for (int u = 0; u < w; u++) {
				low(c, p, u, v);
			}
		}
		// --- shade
		int shade = p.hash(5, 5, 5, 3); // 0 none, 1 awning, 2 tree
		if (shade == 1 && h >= 6 && w >= 4) {
			for (int u = 0; u < w; u++) {
				if (pillar[u]) {
					for (int v = 0; v < pd; v++) {
						post(c, p, u, v);
					}
				}
			}
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < pd; v++) {
					set(c, p, u, 5, v, slab(Blocks.CHERRY_SLAB));
				}
			}
			softUnder(c, p, 0, 0, w - 1, pd - 1);
			for (int u = s0 + 1; u <= s1; u += 4) { // a lantern over every run
				p.put(c, u, 4, 0, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			}
		} else if (shade == 2 && h >= 7 && d >= 3 && w >= 4) {
			tree(c, p, p.hash(6, 0, 0, 2) == 0 ? 1 : w - 2, d - 1);
		}
	}

	/** A small cherry behind the bench: trunk on moss, a round crown from y4 up (all above head height, even on a planter top). */
	private static void tree(CitadelCanvas c, Plot p, int tu, int tv) {
		set(c, p, tu, 0, tv, b(Blocks.MOSS_BLOCK));
		for (int y = 1; y <= 4; y++) {
			set(c, p, tu, y, tv, b(Blocks.CHERRY_LOG));
		}
		for (int y = 4; y <= 6; y++) {
			int r = y == 5 ? 2 : 1;
			for (int du = -r; du <= r; du++) {
				for (int dv = -r; dv <= r; dv++) {
					if (Math.abs(du) + Math.abs(dv) > r + (y == 5 ? 1 : 0) || (y == 4 && du == 0 && dv == 0)) {
						continue;
					}
					int u = tu + du;
					int v = tv + dv;
					set(c, p, u, y, v, p.hash(u, y, v, 3) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
	}

	// ------------------------------------------------------------------ nooks

	private static void nook(CitadelCanvas c, Plot p, boolean lShape) {
		int w = p.w();
		int d = p.d();
		int h = p.h();
		int nw = Math.min(w, 7);
		int nd = Math.min(d, 6);
		int o = (w - nw) / 2;
		// the rest of the plot is a garden
		for (int v = 0; v < d; v++) {
			for (int u = 0; u < w; u++) {
				if (u < o || u >= o + nw || v >= nd) {
					low(c, p, u, v);
				}
			}
		}
		if (lShape) {
			lNook(c, p, o, nw, nd);
		} else {
			uNook(c, p, o, nw, nd);
		}
		if (h >= 6) {
			pergola(c, p, o, 0, o + nw - 1, nd - 1);
		}
	}

	private static void uNook(CitadelCanvas c, Plot p, int o, int nw, int nd) {
		for (int v = 0; v < nd; v++) {
			for (int x : new int[] {0, nw - 1}) { // planter strips behind the benches, a lantern post at the front
				if (v == 0) {
					lanternPost(c, p, o + x, v);
				} else {
					arm(c, p, o + x, v);
				}
			}
			if (v < nd - 1) { // benches face each other; their backs are the stair backs and the arms
				seat(c, p, o + 1, v, Direction.WEST);
				seat(c, p, o + nw - 2, v, Direction.EAST);
			}
		}
		for (int x = 1; x < nw - 1; x++) {
			bed(c, p, o + x, nd - 1, true);
		}
		int zw = nw - 4;
		int tw = zw;
		int td = Math.min(nd - 2, 3);
		table(c, p, o + 2 + (zw - tw) / 2, 1 + (nd - 2 - td) / 2, tw, td);
	}

	private static void lNook(CitadelCanvas c, Plot p, int o, int nw, int nd) {
		for (int x = 0; x < nw; x++) {
			bed(c, p, o + x, nd - 1, true);
		}
		for (int v = 0; v < nd - 1; v++) {
			if (v == 0) {
				lanternPost(c, p, o, v);
			} else {
				arm(c, p, o, v);
			}
		}
		for (int x = 2; x < nw - 1; x++) {
			seat(c, p, o + x, nd - 2, Direction.SOUTH); // along the back, facing out
		}
		bed(c, p, o + nw - 1, nd - 2, true); // the arm at the open end
		for (int v = 0; v <= nd - 3; v++) {
			seat(c, p, o + 1, v, Direction.WEST); // along the left
		}
		// the elbow is a corner table with a light
		set(c, p, o + 1, 0, nd - 2, b(Blocks.CHISELED_DEEPSLATE));
		set(c, p, o + 1, 1, nd - 2, LANTERN);
		table(c, p, o + 2, nd - 3, 2, 1);
	}

	/** A low table: slab top at y1, wall legs on the corners, a potted shrub when it is big enough. */
	private static void table(CitadelCanvas c, Plot p, int u0, int v0, int tw, int td) {
		for (int v = v0; v < v0 + td; v++) {
			for (int u = u0; u < u0 + tw; u++) {
				boolean leg = (u == u0 || u == u0 + tw - 1) && (v == v0 || v == v0 + td - 1);
				if (leg) {
					set(c, p, u, 0, v, FENCE);
				}
				set(c, p, u, 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
		}
		if (p.h() < 6 && (tw * td >= 4 || tw * td == 2)) {
			set(c, p, u0 + tw / 2, 2, v0 + td / 2, b(Blocks.POTTED_FLOWERING_AZALEA));
		}
	}

	/** Four fence posts, slat roof of cherry slabs, and two hanging lanterns over the way in. */
	private static void pergola(CitadelCanvas c, Plot p, int u0, int v0, int u1, int v1) {
		for (int u : new int[] {u0, u1}) {
			for (int v : new int[] {v0, v1}) {
				post(c, p, u, v);
			}
		}
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u1; u++) {
				boolean beam = v == v0 || v == v1; // beams along the front and the back, slats across: the nook stays visible from above
				if (beam || (u - u0) % 2 == 0) {
					set(c, p, u, 5, v, beam ? slab(Blocks.DEEPSLATE_TILE_SLAB) : slab(Blocks.CHERRY_SLAB)); // dark beams, pink slats
				}
			}
		}
		softUnder(c, p, u0, v0, u1, v1);
		for (int u : new int[] {u0 + 2, u1 - 2}) { // two lanterns on the front beam, over the way in (over the table they would leave 2.6 blocks)
			p.put(c, u, 4, v0, LANTERN.setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		}
		braces(c, p, u0, v0, u1, v1);
	}

	// ------------------------------------------------------------------ parts

	/** Upside-down cherry stairs hugging each post under the beam (the post-and-beam joint) of the front beam: depth on the roof. */
	private static void braces(CitadelCanvas c, Plot p, int u0, int v0, int u1, int v1) {
		for (int v : new int[] {v0}) { // the front posts only: over the planter strips at the back they would leave 2 blocks to stand under
			set(c, p, u0 + 1, 4, v, stairs(Blocks.CHERRY_STAIRS, Direction.WEST).setValue(BlockStateProperties.HALF, Half.TOP));
			set(c, p, u1 - 1, 4, v, stairs(Blocks.CHERRY_STAIRS, Direction.EAST).setValue(BlockStateProperties.HALF, Half.TOP));
		}
	}

	/** An armrest: deepslate box, a cherry slab on top, a potted flower on every other one. */
	private static void arm(CitadelCanvas c, Plot p, int u, int v) {
		boolean roofed = p.h() >= 6; // a pergola goes over every nook: no pots to stand on under it
		set(c, p, u, 0, v, box(p, u, 0, v));
		set(c, p, u, 1, v, slab(Blocks.CHERRY_SLAB));
		if (!roofed && p.hash(u, 2, v, 2) == 0) {
			set(c, p, u, 2, v, switch (p.hash(u, 3, v, 4)) {
				case 0 -> b(Blocks.POTTED_FLOWERING_AZALEA);
				case 1 -> b(Blocks.POTTED_ALLIUM);
				case 2 -> b(Blocks.POTTED_RED_TULIP);
				default -> b(Blocks.POTTED_AZALEA);
			});
		}
	}

	/**
	 * Under a roof an azalea would be a place to stand (it has a collision box) with only two blocks of headroom, so
	 * the roofed cells get soft plants (flowers) instead.
	 */
	private static void softUnder(CitadelCanvas c, Plot p, int u0, int v0, int u1, int v1) {
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u1; u++) {
				for (int y = 0; y <= 3; y++) {
					BlockState at = p.has(u, y, v) ? p.at(c, u, y, v) : null;
					if (at != null && (at.is(Blocks.AZALEA) || at.is(Blocks.FLOWERING_AZALEA))) {
						int k = p.hash(u, y, v, 3);
						p.set(c, u, y, v, k == 0 ? b(Blocks.AZURE_BLUET) : k == 1 ? b(Blocks.CORNFLOWER) : b(Blocks.LILY_OF_THE_VALLEY));
					}
				}
			}
		}
	}

	/** A cherry stair seat; {@code back} is the side of the tall half (local). */
	private static void seat(CitadelCanvas c, Plot p, int u, int v, Direction back) {
		set(c, p, u, 0, v, stairs(Blocks.CHERRY_STAIRS, back));
	}

	/** A deepslate box with a polished / tiled / cracked mix, never uniform. */
	private static BlockState box(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 4)) {
			case 0 -> b(Blocks.DEEPSLATE_TILES);
			case 1 -> b(Blocks.CRACKED_DEEPSLATE_TILES);
			default -> b(Blocks.POLISHED_DEEPSLATE);
		};
	}

	/** Soil a plant may stand on: moss and rooted dirt, or dirt and coarse dirt, by seed. */
	private static BlockState soil(Plot p, int u, int y, int v) {
		int k = p.hash(u, y, v, 6);
		if (p.hash(9, 9, 9, 4) != 0) { // three seeds in four are mossy
			return k == 0 ? b(Blocks.ROOTED_DIRT) : b(Blocks.MOSS_BLOCK);
		}
		return k == 0 ? b(Blocks.COARSE_DIRT) : k == 1 ? b(Blocks.ROOTED_DIRT) : b(Blocks.MOSS_BLOCK); // earth seeds keep moss in the mix
	}

	private static BlockState plant(Plot p, int u, int y, int v) {
		int k = p.hash(u, y, v, 8);
		return switch (p.hash(8, 8, 8, 3)) {
			case 0 -> switch (k) { // blossom
				case 0, 1 -> b(Blocks.PINK_TULIP);
				case 2, 3 -> b(Blocks.PINK_PETALS).setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + p.hash(u, y, v, 4))
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
				case 4 -> b(Blocks.FLOWERING_AZALEA);
				case 5 -> b(Blocks.ALLIUM);
				default -> b(Blocks.RED_TULIP);
			};
			case 1 -> switch (k) { // wildflowers
				case 0, 1 -> b(Blocks.ALLIUM);
				case 2, 3 -> b(Blocks.CORNFLOWER);
				case 4 -> b(Blocks.AZURE_BLUET);
				case 5 -> b(Blocks.OXEYE_DAISY);
				case 6 -> b(Blocks.AZALEA);
				default -> b(Blocks.RED_TULIP);
			};
			default -> switch (k) { // white and green
				case 0, 1, 2 -> b(Blocks.LILY_OF_THE_VALLEY);
				case 3, 4 -> b(Blocks.AZURE_BLUET);
				case 5 -> b(Blocks.OXEYE_DAISY);
				case 6 -> b(Blocks.AZALEA);
				default -> b(Blocks.FLOWERING_AZALEA);
			};
		};
	}

	/**
	 * A raised planter cell: deepslate box (y0), soil (y1), plant (y2). Too low for that (h &lt; 3): soil at y0 and
	 * the plant at y1. A cell in six stays bare soil so the top is ragged.
	 */
	private static void bed(CitadelCanvas c, Plot p, int u, int v, boolean tall) {
		if (tall && p.h() >= 3) {
			set(c, p, u, 0, v, box(p, u, 0, v));
			set(c, p, u, 1, v, soil(p, u, 1, v));
			set(c, p, u, 2, v, p.hash(u, 2, v, 6) != 0 ? plant(p, u, 2, v) : b(Blocks.MOSS_CARPET));
		} else {
			set(c, p, u, 0, v, soil(p, u, 0, v));
			set(c, p, u, 1, v, plant(p, u, 1, v));
		}
	}

	/** A bed on the ground: soil at y0, a plant at y1; one cell in eight stays grass. */
	private static void low(CitadelCanvas c, Plot p, int u, int v) {
		int k = p.hash(u, 5, v, 8);
		if (k == 0) {
			return;
		}
		set(c, p, u, 0, v, soil(p, u, 0, v));
		set(c, p, u, 1, v, k != 1 ? plant(p, u, 1, v) : b(Blocks.MOSS_CARPET));
	}

	/** A deepslate post with a lantern on top: light at sitting height. */
	private static void lanternPost(CitadelCanvas c, Plot p, int u, int v) {
		set(c, p, u, 0, v, box(p, u, 0, v));
		if (p.h() >= 3) {
			set(c, p, u, 1, v, WALL);
			set(c, p, u, 2, v, LANTERN);
		} else {
			set(c, p, u, 1, v, LANTERN);
		}
	}

	/** A roof post: deepslate foot, wall, three cherry fence blocks (top at y4, the roof is y5: 3 blocks over a planter top). */
	private static void post(CitadelCanvas c, Plot p, int u, int v) {
		set(c, p, u, 0, v, box(p, u, 0, v));
		set(c, p, u, 1, v, WALL);
		set(c, p, u, 2, v, FENCE);
		set(c, p, u, 3, v, FENCE);
		set(c, p, u, 4, v, FENCE);
	}

	/** Writes only inside the box, so the report shows no clipping. */
	private static void set(CitadelCanvas c, Plot p, int u, int y, int v, BlockState s) {
		if (p.has(u, y, v)) {
			p.set(c, u, y, v, s);
		}
	}
}
