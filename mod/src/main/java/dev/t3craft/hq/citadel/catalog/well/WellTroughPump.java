package dev.t3craft.hq.citadel.catalog.well;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A village water point that is used: a stone trough with its water held to the brim, a hand pump at the head
 * of it (stone housing, chiseled head, a lever for the crank and a copper spout that pours into the water), a
 * bucket and a cauldron beside it, a bench and barrels, all on its own paving of slabs.
 *
 * <p>Sizes 5–16 × 3–14: the trough never stretches. It stays at most 6 water cells long; a wide plot
 * (11 and up) gets a <b>double</b> trough, one pump in the middle pouring to both sides; a deep plot (11 and up)
 * gets a second row, a <b>wash-house</b>, with its own pump, and a 3-wide walk between the two.
 *
 * <pre>
 *  plan, 10 wide × 7 deep, a pump house        section along the spout
 *  v0  ░ ░ ░ ░ ░ ░ ░ ░ ░ ░   paving, bucket     y5 ▀▀▀   roof on posts (when the seed says so)
 *  v1  ▣ ▬ ▬ ▬ ▬ ▬ ▬ ▣ ▣ B   front rim            y4 ┃ ▄ ┃
 *  v2  ▣ ≈ ≈ ≈ ≈ ≈ ≈ ▣ c ◘   water, cauldron      y3 ┃ ╫ ┃  neck
 *  v3  ▣ ▬ ▬ ▬ ▬ ▬ ▬ ▣ ▣ B   back rim              y2 ▣►≈◄▣  head, spout, water
 *  v4  ░ ░ ═ ═ ═ ░ ░ ░ ░ ░   bench                y1 ▓≈≈≈▓  rim, water
 * </pre>
 *
 * Materials by seed: grey stone brick, deepslate or red brick (3–5 per build), cherry for wood. No fire anywhere:
 * the only lights are lanterns on a post or under the roof.
 */
public final class WellTroughPump implements Variant {
	private static final BlockState BARREL_UP = b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
	private static final int MAX_WATER = 6;

	@Override
	public String id() {
		return "well_trough_pump";
	}

	@Override
	public Family family() {
		return Family.WELL;
	}

	@Override
	public int minW() {
		return 5;
	}

	@Override
	public int minD() {
		return 3;
	}

	@Override
	public int minH() {
		return 4;
	}

	@Override
	public int maxW() {
		return 16;
	}

	@Override
	public int maxD() {
		return 14;
	}

	/** The materials of one build: three families by seed, each a dark base, a body, a chiseled trim and its slabs. */
	private record Mat(Block rim, Block rimAlt, Block rimCrack, Block trim, Block wall, Block cap,
			Block pave1, Block pave2, Block edge, Block top, Block stair, Block roofStair, Block roofFull, int alt) {
	}

	private static Mat mat(Plot p) {
		return switch (p.hash(5, 5, 5, 3)) {
			case 0 -> new Mat(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
					Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICK_SLAB,
					Blocks.COBBLESTONE_SLAB, Blocks.ANDESITE_SLAB, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS,
					Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, 2);
			// deepslate bricks with pale tuff on top: the gradient keeps the dark build readable
			case 1 -> new Mat(Blocks.DEEPSLATE_BRICKS, Blocks.TUFF_BRICKS,
					Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.CHISELED_TUFF_BRICKS, Blocks.TUFF_BRICK_WALL, Blocks.TUFF_BRICK_SLAB,
					Blocks.TUFF_SLAB, Blocks.POLISHED_TUFF_SLAB, Blocks.DEEPSLATE_TILE_SLAB, Blocks.TUFF_BRICKS, Blocks.TUFF_BRICK_STAIRS,
					Blocks.CHERRY_STAIRS, Blocks.CHERRY_PLANKS, 3);
			// grey stone trough in a red brick yard: the water is framed by the contrast
			default -> new Mat(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
					Blocks.STONE_BRICK_WALL, Blocks.BRICK_SLAB, Blocks.BRICK_SLAB, Blocks.STONE_BRICK_SLAB, Blocks.COBBLESTONE_SLAB,
					Blocks.BRICKS, Blocks.BRICK_STAIRS,
					Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, 2);
		};
	}

	/** Where the pump of a row stands and how long its water runs. */
	private record Row(int vf, int um, int len, boolean dbl, int pu, int cu) {
		int vm() {
			return vf + 1;
		}

		int vk() {
			return vf + 2;
		}

		int capL() {
			return dbl ? um : -1;
		}

		int capR() {
			return um + len - 1;
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Mat m = mat(p);
		int w = p.w();
		int d = p.d();
		boolean roof = p.h() >= 5 && p.hash(6, 6, 6, 2) == 1;
		boolean dbl = w >= 11;
		int water = dbl ? Math.min(MAX_WATER, (w - 4) / 2) : (w <= 8 ? w - 3 : Math.min(MAX_WATER, w - 4));
		int len = dbl ? 2 * water + 3 : water + 2;
		int um = dbl || w >= 9 ? (w - len) / 2 : 0;
		int pu = dbl ? um + water + 1 : um;
		int cu = dbl ? pu : pu + 1;
		int vf1 = d >= 4 ? 1 : 0;
		boolean twoRows = d >= 11;

		paving(c, p, m);
		Row r1 = new Row(vf1, um, len, dbl, pu, cu);
		row(c, p, m, r1, roof, false);
		margins(c, p, r1, w);
		Row last = r1;
		if (twoRows) {
			Row r2 = new Row(vf1 + 6, um, len, dbl, pu, cu);
			row(c, p, m, r2, false, true);
			margins(c, p, r2, w);
			walk(c, p, r1);
			last = r2;
		}
		backYard(c, p, last, w, d);
	}

	// ------------------------------------------------------------------ paving

	/**
	 * [palette] the plot's own paving, in bottom slabs so nothing needs a jump: grit-grey (cobble, andesite) or
	 * red brick, an edge band of another stone, mixed by hash.
	 */
	private static void paving(CitadelCanvas c, Plot p, Mat m) {
		for (int u = 0; u < p.w(); u++) {
			for (int v = 0; v < p.d(); v++) {
				boolean edge = u == 0 || v == 0 || u == p.w() - 1 || v == p.d() - 1;
				// [palette] 2 × 2 tiles of two stones in a chessboard, a sixth of them flipped, so no yard is one flat colour
				boolean tile = (((u / 2) + (v / 2)) & 1) == 0 ^ p.hash(u, 0, v, 6) == 0;
				Block blk = edge ? m.edge() : tile ? m.pave1() : m.pave2();
				p.set(c, u, 0, v, slab(blk));
			}
		}
	}

	// ------------------------------------------------------------------ one row: trough(s) and pump

	private static BlockState rimStone(Plot p, Mat m, int u, int v) {
		int h = p.hash(u, 1, v, 6);
		return h < m.alt() ? b(m.rimAlt()) : h == m.alt() ? b(m.rimCrack()) : b(m.rim());
	}

	/**
	 * [depth] a low trough: the water at ground level with a rim of one block all round and a taller back wall and
	 * end piers of two, so from the front the water shows over a low lip while the back and the ends give it mass.
	 */
	private static void row(CitadelCanvas c, Plot p, Mat m, Row r, boolean roof, boolean wash) {
		int vf = r.vf();
		int vm = r.vm();
		int vk = r.vk();
		int u0 = r.um();
		int u1 = r.um() + r.len() - 1;
		for (int u = u0; u <= u1; u++) {
			for (int v = vf; v <= vk; v++) {
				boolean cell = wash ? (u - u0) % 5 == 0 || u == u1 : u == r.pu() || u == r.capL() || u == r.capR();
				BlockState low;
				BlockState high = null;
				if (cell) {
					low = v == vm && u != r.pu() ? b(m.rim()) : b(m.trim());
					if (wash || u != r.pu()) {
						high = v == vm ? b(m.rim()) : b(m.trim()); // end pier, two high
					}
				} else if (v == vm) {
					low = b(Blocks.WATER); // contained: rim all round, solid ground under
				} else {
					low = rimStone(p, m, u, v);
					if (wash && v == vf) {
						low = stairs(m.stair(), Direction.NORTH); // [depth] a washboard: the front rim as a sloped step
					}
					if (v == vk) {
						// the back wall: full height on deep plots, a half wall on shallow ones (the water must show)
						high = wash && !tapCol(u, u0, u1) ? slab(m.cap()) : p.d() >= 5 ? b(m.top()) : slab(m.cap());
					}
				}
				p.set(c, u, 0, v, low);
				if (high != null) {
					p.set(c, u, 1, v, high);
				}
			}
		}
		if (wash) {
			taps(c, p, r, m);
			lanternPosts(c, p, r, new int[] {u0, u1});
		} else {
			pump(c, p, m, r, !roof);
			spouts(c, p, r);
			if (roof) {
				canopy(c, p, r, m);
			} else {
				lanternPosts(c, p, r, r.dbl() ? new int[] {r.capL(), r.capR()} : new int[] {r.capR()});
			}
			lily(c, p, r);
			drains(c, p, r, new int[] {r.pu() - 1, r.pu() + 1});
		}
	}

	/**
	 * The wash-house: pilas of 4 (a pier every 5th cell), a copper tap on the back wall over each, a drain grate
	 * in the paving in front of each.
	 */
	private static void taps(CitadelCanvas c, Plot p, Row r, Mat m) {
		BlockState rod = Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH);
		int u0 = r.um();
		int u1 = r.um() + r.len() - 1;
		for (int a = u0; a < u1; a += 5) {
			int b2 = Math.min(a + 5, u1);
			int mid = (a + b2) / 2;
			if (b2 - a >= 2) {
				p.set(c, mid, 1, r.vm(), rod);
				drains(c, p, r, new int[] {mid});
			}
		}
	}

	/** Whether {@code u} is the middle of a pila of the wash-house (where its tap and its full back wall are). */
	private static boolean tapCol(int u, int u0, int u1) {
		for (int a = u0; a < u1; a += 5) {
			int b2 = Math.min(a + 5, u1);
			if (b2 - a >= 2 && u == (a + b2) / 2) {
				return true;
			}
		}
		return false;
	}

	/** [finish] drainage: an iron grate (a closed trapdoor, lower than the paving) in the apron in front of the water. */
	private static void drains(CitadelCanvas c, Plot p, Row r, int[] us) {
		if (r.vf() < 1) {
			return;
		}
		for (int u : us) {
			if (u >= 0 && u < p.w()) {
				p.set(c, u, 0, r.vf() - 1, b(Blocks.IRON_TRAPDOOR));
			}
		}
	}

	/**
	 * [depth] the pump: a chiseled housing across the rim (1 × 3 at y1), a head over the water row, a wall neck and
	 * a slab cap. The crank is a lever on the housing's front face, at hand height from the paving; with no room in
	 * front it is a stone button on top.
	 */
	private static void pump(CitadelCanvas c, Plot p, Mat m, Row r, boolean open) {
		int pu = r.pu();
		for (int v = r.vf(); v <= r.vk(); v++) {
			p.set(c, pu, 1, v, b(m.trim()));
		}
		int vm = r.vm();
		p.set(c, pu, 2, vm, b(m.wall()));
		if (open) {
			// [depth] no roof over it: a gate-like head, a cherry crossbar over the three wall posts with a lantern on
			// it, so the pump has a silhouette of its own and a colour that stands out from the stone
			BlockState bar = b(Blocks.STRIPPED_CHERRY_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
			for (int v = r.vf(); v <= r.vk(); v++) {
				p.set(c, pu, 3, v, bar);
			}
			if (p.h() >= 5) {
				p.set(c, pu, 4, vm, b(Blocks.LANTERN));
			}
		} else {
			p.set(c, pu, 3, vm, slab(m.cap()));
		}
		// [depth] a wall post on each end of the housing: a gate-like head, and no floor on top of it to stand on
		p.set(c, pu, 2, r.vk(), b(m.wall()));
		if (r.vf() >= 1) {
			p.set(c, pu, 2, r.vf(), b(m.wall()));
			p.put(c, pu, 1, r.vf() - 1, b(Blocks.LEVER)
					.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
					.setValue(BlockStateProperties.POWERED, p.hash(pu, 2, r.vf(), 2) == 0), CitadelCanvas.DECOR);
		} else {
			p.put(c, pu, 2, r.vf(), b(Blocks.STONE_BUTTON)
					.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), CitadelCanvas.DECOR);
		}
	}

	/** [finish] a copper spout (waxed lightning rod) pouring over the water, one each side of the pump when it is double. */
	private static void spouts(CitadelCanvas c, Plot p, Row r) {
		BlockState rod = Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState();
		p.set(c, r.pu() + 1, 1, r.vm(), rod.setValue(BlockStateProperties.FACING, Direction.EAST));
		if (r.dbl()) {
			p.set(c, r.pu() - 1, 1, r.vm(), rod.setValue(BlockStateProperties.FACING, Direction.WEST));
		}
	}

	/** [finish] a lily pad drifting at the far end of the water, away from the spouts (by seed). */
	private static void lily(CitadelCanvas c, Plot p, Row r) {
		int lo = r.pu() + 2;
		int hi = r.capR() - 1;
		if (lo <= hi && p.hash(r.vf(), 2, 9, 3) == 0) {
			p.set(c, lo + p.hash(r.vf(), 2, 3, hi - lo + 1), 1, r.vm(), b(Blocks.LILY_PAD));
		}
		if (r.dbl() && p.hash(r.vf(), 2, 8, 2) == 0) {
			int l2 = r.capL() + 1;
			int h2 = r.pu() - 2;
			if (l2 <= h2) {
				p.set(c, l2 + p.hash(r.vf(), 2, 4, h2 - l2 + 1), 1, r.vm(), b(Blocks.LILY_PAD));
			}
		}
	}

	/** A cherry post with a lantern on the back corner pier of each end (the pump house carries its own lights). */
	private static void lanternPosts(CitadelCanvas c, Plot p, Row r, int[] ends) {
		for (int u : ends) {
			p.set(c, u, 2, r.vk(), b(Blocks.CHERRY_FENCE));
			p.set(c, u, 3, r.vk(), b(Blocks.LANTERN));
		}
	}

	/**
	 * [finish] a pump house: four cherry posts and a 3 × 3 hip roof of cherry stairs at y4 (3 blocks over the rim, enough
	 * to stand under), a slab on top, a lantern hanging beside the spout. The rim cells it covers are filled (housing,
	 * a cauldron, a barrel) so nobody has a floor up there.
	 */
	private static void canopy(CitadelCanvas c, Plot p, Row r, Mat m) {
		int cu = r.cu();
		int vf = r.vf();
		int vm = r.vm();
		int vk = r.vk();
		for (int u : new int[] {cu - 1, cu + 1}) {
			for (int v : new int[] {vf, vk}) {
				for (int y = 1; y <= 3; y++) {
					if (u == r.pu() && y <= 2) {
						continue; // the pump housing and its wall posts are already there
					}
					p.set(c, u, y, v, y == 3 ? b(Blocks.STRIPPED_CHERRY_LOG) : b(Blocks.CHERRY_FENCE));
				}
			}
		}
		for (int u = cu - 1; u <= cu + 1; u++) {
			p.set(c, u, 4, vf, stairs(m.roofStair(), Direction.SOUTH));
			p.set(c, u, 4, vk, stairs(m.roofStair(), Direction.NORTH));
		}
		p.set(c, cu - 1, 4, vm, stairs(m.roofStair(), Direction.EAST));
		p.set(c, cu + 1, 4, vm, stairs(m.roofStair(), Direction.WEST));
		p.set(c, cu, 4, vm, b(m.roofFull()));
		if (p.h() > 5) {
			p.set(c, cu, 5, vm, slab(Blocks.CHERRY_SLAB));
		}
		p.put(c, cu + 1, 3, vm, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		if (r.dbl()) {
			p.put(c, cu - 1, 3, vm, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		} else {
			// single: the rim cells under the roof beside the spout hold the bucket and a barrel (no floor to stand on)
			p.set(c, cu, 1, vf, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			p.set(c, cu, 1, vk, BARREL_UP);
		}
	}

	// ------------------------------------------------------------------ props

	/**
	 * [finish] the column beside each end of a row: a barrel in front, a cauldron of water on the trough's axis (the
	 * bucket: low water, left; the cauldron: full, right), two barrels at the back. A 2nd column gets a bench end-on.
	 */
	private static void margins(CitadelCanvas c, Plot p, Row r, int w) {
		int left = r.um();
		int right = w - (r.um() + r.len());
		if (left > 0) {
			margin(c, p, r, left - 1, left >= 2 ? left - 2 : -1, Direction.WEST, 1);
		}
		if (right > 0) {
			margin(c, p, r, r.um() + r.len(), right >= 2 ? r.um() + r.len() + 1 : -1, Direction.EAST, 3);
		}
	}

	private static void margin(CitadelCanvas c, Plot p, Row r, int u, int benchU, Direction away, int level) {
		int vf = r.vf();
		int vm = r.vm();
		int vk = r.vk();
		p.set(c, u, 0, vf, p.hash(u, 0, vf, 2) == 0 ? BARREL_UP : b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.NORTH));
		p.set(c, u, 0, vm, b(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, level));
		p.set(c, u, 0, vk, BARREL_UP);
		p.set(c, u, 1, vk, BARREL_UP);
		if (benchU >= 0) {
			p.set(c, benchU, 0, vm, stairs(Blocks.CHERRY_STAIRS, away));
		}
	}

	/** The 3-wide walk between two rows: a lantern post at each end of the walk, on the edge band. */
	private static void walk(CitadelCanvas c, Plot p, Row r1) {
		int v = r1.vk() + 2;
		for (int u : new int[] {0, p.w() - 1}) {
			p.set(c, u, 0, v, b(Blocks.CHERRY_FENCE));
			p.set(c, u, 1, v, b(Blocks.CHERRY_FENCE));
			p.set(c, u, 2, v, b(Blocks.LANTERN));
		}
	}

	/**
	 * Behind the last row: a cherry bench facing the water with a barrel at each end; from 3 rows back, a hedge of
	 * azalea on the back edge (leaves only touch the plot's ground, never the trough).
	 */
	private static void backYard(CitadelCanvas c, Plot p, Row last, int w, int d) {
		int vb = last.vk() + 1;
		if (vb >= d) {
			return;
		}
		int bu = Math.max(1, last.cu() - 1);
		for (int u = bu; u <= bu + 2 && u < w; u++) {
			p.set(c, u, 0, vb, stairs(Blocks.CHERRY_STAIRS, Direction.SOUTH));
		}
		if (bu - 1 >= 0) {
			p.set(c, bu - 1, 0, vb, BARREL_UP);
		}
		if (bu + 3 < w) {
			p.set(c, bu + 3, 0, vb, BARREL_UP);
		}
		int vh = d - 1;
		if (vh >= vb + 2) {
			for (int u = 1; u < w - 1; u++) {
				if (p.hash(u, 0, vh, 5) != 0) {
					p.set(c, u, 0, vh, leaves(Blocks.AZALEA_LEAVES));
				}
			}
		}
	}
}
