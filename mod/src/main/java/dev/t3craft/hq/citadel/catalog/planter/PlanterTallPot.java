package dev.t3craft.hq.citadel.catalog.planter;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;
import static dev.t3craft.hq.citadel.CitadelCanvas.terracotta;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Concept: the street planter of the citadel, a tall pot on a dark foot. Every pot reads bottom to top as
 * dark foot, coloured body, light lip, soil and plant; the body is two or three blocks high wherever the
 * plot is tall enough. The material changes with the seed: terracotta with a quartz lip, polished
 * deepslate with a polished-andesite lip, calcite with a cherry lip, or cherry planks with a quartz lip,
 * always on a deepslate foot.
 *
 * <p>The tricks:
 * <ul>
 * <li><b>Pot</b>: on a plot of 3 × 3 or more it is a vase (see {@code vase}): a stepped foot, a body
 *     pinched into a plus shape and a lip that flares out over the pinched corners (upper slabs) round the
 *     soil. On a single cell it is a column foot, body, lip band, soil (see {@code unit}).</li>
 * <li><b>Plants with presence</b>: an azalea mound (bush, leaves, moss carpet on the rim), a large fern,
 *     a dwarf cherry with a leaf crown, or a clump of bamboo of different heights. Always on moss or
 *     podzol that the pot itself carries, never on the ground of the plot.</li>
 * <li><b>Use</b>: wide plots are composed, not stretched. A group of three heights (tall, mid, and a low
 *     pedestal with a potted plant in front), a lantern post, and from 9 wide two vases flanking a
 *     stone bench with an azalea hedge as its back.</li>
 * </ul>
 * Size: 1–11 × 1–5 × 3+ (the leader asks for 1–2 × 1 × 3; at height 3 a pot is foot, body and a potted
 * plant or a leaf ball, from height 4 it carries real soil). Everything stays inside its plot, and the
 * pots are blocks of their own, so nothing narrows a path.
 */
public final class PlanterTallPot implements Variant {
	/** The material of one pot scheme: dark foot, coloured body, light lip. */
	private record Mat(BlockState foot, BlockState footAlt, Block footSlab, BlockState body, BlockState bodyAlt, BlockState urn,
			BlockState lip, Block lipStairs, Block lipSlab) {
	}

	@Override
	public String id() {
		return "planter_tall_pot";
	}

	@Override
	public Family family() {
		return Family.PLANTER;
	}

	@Override
	public int minW() {
		return 1;
	}

	@Override
	public int minD() {
		return 1;
	}

	@Override
	public int minH() {
		return 3;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 5;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		int w = p.w();
		int d = p.d();
		int h = p.h();
		Mat m = mat(p);
		boolean mir = p.hash(7, 0, 3, 2) == 0; // which side the big pot goes
		boolean vase = w >= 3 && d >= 3 && h >= 4;
		int vb = vase ? (d - 3) / 2 + 1 : d >= 2 ? Math.min(d - 1, 2) : 0; // the row the pots stand on

		if (w <= 2) {
			cluster(c, p, m, 0, w, Math.min(vb, d - 1), mir);
			return;
		}
		block(c, p, m, 0, vase, vb, mir, p.hash(0, 3, 0, 3));
		int rest = w - 3;
		if (rest <= 1) {
			return;
		}
		if (w >= 9) {
			// flanking pair with a bench between them (lanterns at the ends of a wide gap)
			block(c, p, m, w - 3, vase, vb, mir, (p.hash(0, 3, 0, 3) + 1) % 3);
			int g = w - 6;
			int bw = Math.min(g >= 5 ? g - 2 : g, 5);
			int bu = 3 + (g - bw) / 2;
			if (g >= 5) {
				lantern(c, p, m, mx(p, mir, 3, 1), vb);
				lantern(c, p, m, mx(p, mir, w - 4, 1), vb);
			}
			bench(c, p, m, bu, bw, vb, mir);
			return;
		}
		if (rest >= 3) {
			lantern(c, p, m, mx(p, mir, 3, 1), vb);
		}
		if (rest == 2) {
			cluster(c, p, m, 3, 2, vb, mir);
		} else {
			int n = Math.min(3, rest - 1);
			cluster(c, p, m, w - n, n, vb, mir);
		}
	}

	/** A three-wide block of the composition: the vase on plots deep enough for it, else a group of three pots. */
	private static void block(CitadelCanvas c, Plot p, Mat m, int u0, boolean vase, int vb, boolean mir, int kind) {
		if (vase) {
			vase(c, p, m, mx(p, mir, u0, 3), vb - 1, kind);
		} else {
			cluster(c, p, m, u0, 3, vb, mir);
		}
	}

	/** Mirrors a run of {@code width} cells starting at {@code u} when {@code mir}. */
	private static int mx(Plot p, boolean mir, int u, int width) {
		return mir ? p.w() - u - width : u;
	}

	// ------------------------------------------------------------------ materials

	private static Mat mat(Plot p) {
		return switch (p.hash(0, 0, 0, 4)) {
			case 0 -> new Mat(b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS), Blocks.POLISHED_DEEPSLATE_SLAB,
					terracotta(DyeColor.ORANGE), b(Blocks.TERRACOTTA), terracotta(DyeColor.ORANGE), b(Blocks.SMOOTH_QUARTZ),
					Blocks.SMOOTH_QUARTZ_STAIRS,
					Blocks.SMOOTH_QUARTZ_SLAB);
			case 1 -> new Mat(b(Blocks.DEEPSLATE_TILES), b(Blocks.CRACKED_DEEPSLATE_TILES), Blocks.DEEPSLATE_TILE_SLAB,
					b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS), b(Blocks.POLISHED_ANDESITE), b(Blocks.POLISHED_ANDESITE),
					Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_ANDESITE_SLAB);
			case 2 -> new Mat(b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS), Blocks.POLISHED_DEEPSLATE_SLAB,
					b(Blocks.CALCITE), b(Blocks.POLISHED_DIORITE), b(Blocks.CALCITE), b(Blocks.CHERRY_PLANKS), Blocks.CHERRY_STAIRS,
					Blocks.CHERRY_SLAB);
			default -> new Mat(b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_BRICKS), Blocks.POLISHED_DEEPSLATE_SLAB,
					b(Blocks.CHERRY_PLANKS), b(Blocks.STRIPPED_CHERRY_LOG), b(Blocks.CHERRY_PLANKS), b(Blocks.SMOOTH_QUARTZ),
					Blocks.SMOOTH_QUARTZ_STAIRS,
					Blocks.SMOOTH_QUARTZ_SLAB);
		};
	}

	private static BlockState foot(Mat m, Plot p, int u, int v) {
		return p.hash(u, 0, v, 4) == 0 ? m.footAlt() : m.foot();
	}

	private static BlockState body(Mat m, Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 5) == 0 ? m.bodyAlt() : m.body();
	}

	private static BlockState soil(Plot p, int u, int v) {
		return p.hash(u, 9, v, 3) == 0 ? b(Blocks.PODZOL) : b(Blocks.MOSS_BLOCK);
	}

	private static BlockState upperSlab(Block s) {
		return slab(s).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	/** 0 azalea, 1 fern, 2 bamboo, 3 dwarf tree. */
	private static int kindOf(Plot p, int u, int v) {
		return p.hash(u, 2, v, 4);
	}

	private static BlockState potted(int kind) {
		return switch (kind) {
			case 0 -> b(Blocks.POTTED_FLOWERING_AZALEA);
			case 1 -> b(Blocks.POTTED_FERN);
			case 2 -> b(Blocks.POTTED_BAMBOO);
			default -> b(Blocks.POTTED_CHERRY_SAPLING);
		};
	}

	// ------------------------------------------------------------------ the group

	/**
	 * {@code n} pots side by side from {@code u0} on row {@code vb}: of different heights (tall, mid, and a
	 * low one), with a low pedestal pot in front of some of them when the plot is deep enough.
	 */
	private static void cluster(CitadelCanvas c, Plot p, Mat m, int u0, int n, int vb, boolean mir) {
		int[] tiers = n == 1 ? new int[] {2} : n == 2 ? new int[] {2, 1} : new int[] {1, 2, 0};
		for (int i = 0; i < n; i++) {
			int u = mx(p, mir, u0 + i, 1);
			unit(c, p, m, u, vb, tiers[i], kindOf(p, u, vb));
			int vf = vb - 1;
			if (vf >= 0 && tiers[i] >= 1 && (n == 1 || p.hash(u, 5, vb, 2) == 0)) {
				unit(c, p, m, u, vf, 0, kindOf(p, u, vf));
			}
		}
	}

	/**
	 * One pot on a single cell. Tier 0 is a low pedestal with a potted plant; tiers 1 and 2 (mid and tall)
	 * are a dark foot, a coloured body of 1–3 blocks topped by a light lip band when it has two, moss or
	 * podzol, and the plant. At height 3 there is no room for soil: foot, body and a potted plant or a leaf
	 * ball.
	 */
	private static void unit(CitadelCanvas c, Plot p, Mat m, int u, int v, int tier, int kind) {
		int h = p.h();
		if (tier == 0) {
			p.set(c, u, 0, v, body(m, p, u, 0, v));
			p.set(c, u, 1, v, potted(kind));
			return;
		}
		p.set(c, u, 0, v, foot(m, p, u, v));
		if (h == 3) {
			p.set(c, u, 1, v, m.urn());
			// a ball of blossom or azalea (a shrub with presence) or a potted fern / bamboo
			p.set(c, u, 2, v, kind == 3 ? leaves(Blocks.CHERRY_LEAVES) : kind == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : potted(kind));
			return;
		}
		int body = Math.max(1, Math.min(tier == 2 ? 3 : 2, h - 4));
		for (int y = 1; y <= body; y++) {
			p.set(c, u, y, v, y == body && body >= 2 ? m.lip() : body(m, p, u, y, v));
		}
		int sy = body + 1;
		p.set(c, u, sy, v, soil(p, u, v));
		int room = h - sy - 1;
		int y1 = sy + 1;
		if (kind == 2 && room >= 2) {
			stalk(c, p, u, v, y1, y1 + Math.min(room, 4) - 1);
		} else if (kind == 1) {
			if (room >= 2) {
				p.set(c, u, y1, v, b(Blocks.LARGE_FERN).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
				p.set(c, u, y1 + 1, v, b(Blocks.LARGE_FERN).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
			} else {
				p.set(c, u, y1, v, b(Blocks.FERN));
			}
		} else if (kind == 3 && room >= 3) {
			int tr = room - 2;
			for (int y = y1; y < y1 + tr; y++) {
				p.set(c, u, y, v, b(Blocks.CHERRY_LOG));
			}
			p.set(c, u, y1 + tr, v, leaves(Blocks.CHERRY_LEAVES));
			p.set(c, u, y1 + tr + 1, v, leaves(Blocks.CHERRY_LEAVES));
		} else {
			p.set(c, u, y1, v, b(Blocks.FLOWERING_AZALEA));
			if (room >= 2) {
				p.set(c, u, y1 + 1, v, leaves(p.hash(u, 6, v, 2) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
			}
			if (room >= 4) {
				p.set(c, u, y1 + 2, v, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			}
		}
	}

	/** A bamboo stalk from y0 to y1 with the right leaves. */
	private static void stalk(CitadelCanvas c, Plot p, int u, int v, int y0, int y1) {
		for (int y = y0; y <= y1; y++) {
			BambooLeaves l = y == y1 ? BambooLeaves.LARGE : y == y1 - 1 && y1 > y0 ? BambooLeaves.SMALL : BambooLeaves.NONE;
			p.set(c, u, y, v, b(Blocks.BAMBOO).setValue(BlockStateProperties.AGE_1, 1).setValue(BlockStateProperties.BAMBOO_LEAVES, l));
		}
	}

	// ------------------------------------------------------------------ the vase

	/**
	 * A 3 × 3 vase at ({@code u0}, {@code v0}): a foot of full blocks with slab shoulders on the corners, a
	 * body pinched to a plus shape (1–3 blocks), and a lip layer of full blocks with upper slabs over the
	 * pinched corners (the flare) round one cell of soil. The plant crowns it.
	 */
	private static void vase(CitadelCanvas c, Plot p, Mat m, int u0, int v0, int kind) {
		int h = p.h();
		int body = h >= 9 ? 3 : h >= 7 ? 2 : 1;
		int ly = body + 1;
		int room = h - ly - 1;
		for (int du = 0; du < 3; du++) {
			for (int dv = 0; dv < 3; dv++) {
				int u = u0 + du;
				int v = v0 + dv;
				boolean corner = du != 1 && dv != 1;
				boolean centre = du == 1 && dv == 1;
				p.set(c, u, 0, v, corner ? slab(m.footSlab()) : foot(m, p, u, v));
				for (int y = 1; y <= body; y++) {
					// the corners are thin dark legs under the flare: nobody can stand in the pinch
					p.set(c, u, y, v, corner ? b(Blocks.POLISHED_DEEPSLATE_WALL).setValue(BlockStateProperties.UP, true) : body(m, p, u, y, v));
				}
				p.set(c, u, ly, v, centre ? soil(p, u, v) : corner ? upperSlab(m.lipSlab()) : m.lip());
			}
		}
		vasePlant(c, p, u0 + 1, v0 + 1, ly, room, kind);
	}

	private static void vasePlant(CitadelCanvas c, Plot p, int cu, int cv, int ly, int room, int kind) {
		int y1 = ly + 1;
		int[][] edge = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		if (kind == 2 && room >= 2) {
			// dwarf cherry: a short trunk, a ring of blossom at its top and a plus on it
			int tr = Math.max(1, Math.min(room - 1, 2)); // two at most: a taller trunk leaves a standable gap under the crown
			for (int y = y1; y < y1 + tr; y++) {
				p.set(c, cu, y, cv, b(Blocks.CHERRY_LOG));
			}
			int ry = y1 + tr - 1;
			for (int du = -1; du <= 1; du++) {
				for (int dv = -1; dv <= 1; dv++) {
					boolean cornerCell = du != 0 && dv != 0;
					if ((du == 0 && dv == 0) || (cornerCell && p.hash(cu + du, ry, cv + dv, 3) == 0)) {
						continue;
					}
					p.set(c, cu + du, ry, cv + dv, leaves(p.hash(cu + du, ry, cv + dv, 6) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.CHERRY_LEAVES));
				}
			}
			p.set(c, cu, ry + 1, cv, leaves(Blocks.CHERRY_LEAVES));
			for (int[] e : edge) {
				p.set(c, cu + e[0], ry + 1, cv + e[1], leaves(Blocks.CHERRY_LEAVES));
			}
			if (ry + 2 < p.h()) {
				p.set(c, cu, ry + 2, cv, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			}
		} else if (kind == 1) {
			// a large fern over a rim of moss carpet
			if (room >= 2) {
				p.set(c, cu, y1, cv, b(Blocks.LARGE_FERN).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
				p.set(c, cu, y1 + 1, cv, b(Blocks.LARGE_FERN).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
			} else {
				p.set(c, cu, y1, cv, b(Blocks.FERN));
			}
			for (int[] e : edge) {
				p.set(c, cu + e[0], y1, cv + e[1], b(Blocks.MOSS_CARPET));
			}
		} else {
			// an azalea mound: the bush in the middle, leaves and moss on the rim, a crown of leaves above
			p.set(c, cu, y1, cv, b(Blocks.FLOWERING_AZALEA));
			boolean[] leafy = new boolean[4];
			for (int i = 0; i < 4; i++) {
				int[] e = edge[i];
				int k = p.hash(cu + e[0], y1, cv + e[1], 4);
				leafy[i] = k <= 1;
				p.set(c, cu + e[0], y1, cv + e[1], k == 0 ? leaves(Blocks.AZALEA_LEAVES) : k == 1 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : b(Blocks.MOSS_CARPET));
			}
			if (room >= 2) {
				p.set(c, cu, y1 + 1, cv, leaves(Blocks.AZALEA_LEAVES));
				for (int i = 0; i < 4; i++) {
					if (leafy[i]) {
						p.set(c, cu + edge[i][0], y1 + 1, cv + edge[i][1], leaves(Blocks.FLOWERING_AZALEA_LEAVES));
					}
				}
			}
			if (room >= 3) {
				p.set(c, cu, y1 + 2, cv, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			}
		}
	}

	// ------------------------------------------------------------------ furniture

	/** A lantern post: a dark base, a wall shaft of up to three blocks, a lantern and, with room, a slab cap. */
	private static void lantern(CitadelCanvas c, Plot p, Mat m, int u, int v) {
		int shaft = Math.max(1, Math.min(p.h() - 2, 3));
		p.set(c, u, 0, v, foot(m, p, u, v));
		for (int y = 1; y <= shaft; y++) {
			p.set(c, u, y, v, b(Blocks.POLISHED_DEEPSLATE_WALL).setValue(BlockStateProperties.UP, true));
		}
		p.set(c, u, shaft + 1, v, b(Blocks.LANTERN));
		if (shaft + 2 < p.h()) {
			p.set(c, u, shaft + 2, v, slab(m.footSlab()));
		}
	}

	/** A bench of {@code len} stair seats facing the front, with a low azalea hedge for a back. */
	private static void bench(CitadelCanvas c, Plot p, Mat m, int u0, int len, int v, boolean mir) {
		for (int i = 0; i < len; i++) {
			int u = mx(p, mir, u0 + i, 1);
			p.set(c, u, 0, v, stairs(m.lipStairs(), Direction.NORTH));
			if (!p.has(u, 1, v + 1)) {
				continue;
			}
			p.set(c, u, 0, v + 1, leaves(Blocks.AZALEA_LEAVES));
			p.set(c, u, 1, v + 1, leaves(p.hash(u, 1, v + 1, 3) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
		}
	}
}
