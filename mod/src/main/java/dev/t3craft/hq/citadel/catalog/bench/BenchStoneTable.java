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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * A stone meeting place: a polished slab table on a pedestal or trestle, stair seats on two or four sides,
 * a lantern, and (in tall plots) shade. It grows with the plot instead of stretching one table.
 *
 * <p>Concept: a courtyard where people sit down. Gradient dark → light: dark footing, brick pedestal, pale
 * slab top, cherry shade on top. One of three stones per plot (stone brick, deepslate, calcite) and one of
 * three table shapes per set (square, octagon, round), so two tables of one plot differ.
 *
 * <ul>
 * <li><b>1–2 deep</b>: a stone bench with a lantern post at one end and a planter at the other (with a low
 *     wall at its back when 2 deep). It is the only layout that fits the street furniture slots (2–3 × 1 × 3).</li>
 * <li><b>3–4 deep, or a long thin plot</b>: a banquet table with stair benches along both long sides, trestle
 *     legs, lamp stands on the table, a lantern post and a planter at the two heads. Tall plots roof it with a
 *     cherry pergola whose posts stand between the benches.</li>
 * <li><b>5+ deep and wide</b>: seated sets, one per 9–11 cells, each a table with seats on all four sides
 *     (cell by cell: every free cell next to the table is a seat facing it). Its centre is a two-tone slab
 *     game board, a pedestal lamp, or a planter; its corners are lantern posts and planters; it may stand
 *     under a cherry pergola or a parasol. Big plots hold 2+ sets, and tall ones may swap one for a bench
 *     ring round a small cherry tree.</li>
 * </ul>
 * Everything the sitters need is walkable (seats are stairs, half a block), the rings stay open to the
 * path round the plot, lanterns stand on posts or hang from beams. Size: 2–21 × 1–15 × 2+ (pergola: h ≥ 5;
 * tree: h ≥ 8).
 */
public final class BenchStoneTable implements Variant {
	private enum Shape {
		SQUARE, OCTAGON, ROUND, TREE
	}

	private enum Shade {
		NONE, PERGOLA, PARASOL
	}

	/** The stone of a plot: seats (two kinds), body, cap, wall, dark footing and the plain table top. */
	private record Mat(Block seatA, Block seatB, Block brick, Block brickAlt, Block cap, Block wall, Block foot, Block top) {
	}

	private static final Mat[] MATS = {
			new Mat(Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
					Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICK_WALL, Blocks.DEEPSLATE_BRICKS, Blocks.SMOOTH_STONE_SLAB),
			new Mat(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS,
					Blocks.CHISELED_DEEPSLATE, Blocks.DEEPSLATE_BRICK_WALL, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE_SLAB),
			new Mat(Blocks.SMOOTH_QUARTZ_STAIRS, Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.CALCITE, Blocks.QUARTZ_BRICKS,
					Blocks.CHISELED_QUARTZ_BLOCK, Blocks.DIORITE_WALL, Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_QUARTZ_SLAB)};

	private static final Direction[] ORDER = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

	@Override
	public String id() {
		return "bench_stone_table";
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
		return 21;
	}

	@Override
	public int maxD() {
		return 15;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Ctx x = new Ctx(c, p, MATS[p.hash(0, 0, 0, 3)]);
		int w = p.w();
		int d = p.d();
		boolean shadeOn = p.h() >= 6 && p.hash(1, 0, 0, 4) != 0; // three plots in four get shade
		if (d <= 2) {
			x.bench(w, d);
		} else if (d <= 4 || w < 5 || (d <= 6 && w >= 13)) {
			x.banquet(w, d, shadeOn);
		} else {
			x.sets(w, d, shadeOn);
		}
	}

	/** One build: the canvas, the plot and the stone, with the little helpers every layout shares. */
	private static final class Ctx {
		final CitadelCanvas c;
		final Plot p;
		final Mat m;

		Ctx(CitadelCanvas c, Plot p, Mat m) {
			this.c = c;
			this.p = p;
			this.m = m;
		}

		void set(int u, int y, int v, BlockState s) {
			p.set(c, u, y, v, s);
		}

		BlockState brick(int u, int y, int v) {
			return b(p.hash(u, y, v, 4) == 0 ? m.brickAlt() : m.brick());
		}

		/** A seat stair whose tall back is on {@code back} (the sitter faces the other way). */
		BlockState seat(int u, int v, Direction back) {
			return stairs(p.hash(u, 0, v, 3) == 0 ? m.seatB() : m.seatA(), back);
		}

		BlockState wall(boolean west, boolean east) {
			return b(m.wall()).setValue(BlockStateProperties.WEST_WALL, west ? WallSide.LOW : WallSide.NONE)
					.setValue(BlockStateProperties.EAST_WALL, east ? WallSide.LOW : WallSide.NONE)
					.setValue(BlockStateProperties.UP, !(west && east));
		}

		void lantern(int u, int y, int v, boolean hanging) {
			if (hanging) {
				p.put(c, u, y, v, b(Blocks.LANTERN).setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
			} else {
				set(u, y, v, b(Blocks.LANTERN));
			}
		}

		/** A dark footing, a wall shaft and a lantern on top: it stands, it does not hang. */
		void lampPost(int u, int v) {
			int top = p.h() >= 5 ? 3 : p.h() >= 3 ? 2 : 1;
			set(u, 0, v, b(m.foot()));
			for (int y = 1; y < top; y++) {
				set(u, y, v, wall(false, false));
			}
			lantern(u, top, v, false);
		}

		/** A raised cap block with a plant on it. */
		void planter(int u, int v) {
			set(u, 0, v, b(m.foot()));
			if (p.h() >= 2) {
				int k = p.hash(u, 1, v, 3);
				set(u, 1, v, k == 0 ? b(Blocks.FLOWERING_AZALEA) : k == 1 ? b(Blocks.AZALEA) : b(Blocks.POTTED_FERN));
			}
		}

		/** Moss, petals or nothing on the ground cells the layout leaves open. */
		void groundCover(int u, int v) {
			int k = p.hash(u, 0, v, 4);
			if (k == 1) {
				set(u, 0, v, b(Blocks.MOSS_CARPET));
			} else if (k == 2) {
				set(u, 0, v, b(Blocks.PINK_PETALS).setValue(BlockStateProperties.FLOWER_AMOUNT, 2)
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
			}
		}

		// ------------------------------------------------------------------ 1–2 deep: a stone bench

		void bench(int w, int d) {
			boolean lampLeft = p.hash(0, 0, 1, 2) == 0;
			int a = 0;
			int z = w - 1;
			int lampU = -1;
			int planterU = -1;
			if (w >= 3) {
				lampU = lampLeft ? 0 : w - 1;
				a = lampLeft ? 1 : 0;
				z = lampLeft ? w - 1 : w - 2;
			}
			if (w >= 4) {
				planterU = lampLeft ? w - 1 : 0;
				a = 1;
				z = w - 2;
			}
			int vSeat = 0;
			for (int u = a; u <= z; u++) {
				set(u, 0, vSeat, seat(u, vSeat, Direction.SOUTH));
			}
			if (d == 1) {
				if (lampU >= 0) {
					lampPost(lampU, 0);
				}
				if (planterU >= 0) {
					planter(planterU, 0);
				}
				return;
			}
			// two deep: a low wall behind the seats, the lamp and the planter at its ends
			for (int u = a; u <= z; u++) {
				set(u, 0, 1, wall(u > a, u < z));
				if (p.h() >= 2 && p.hash(u, 1, 1, 3) == 0) {
					set(u, 1, 1, b(Blocks.FLOWERING_AZALEA));
				}
			}
			if (lampU >= 0) {
				lampPost(lampU, 1);
				set(lampU, 0, 0, seat(lampU, 0, Direction.SOUTH));
			}
			if (planterU >= 0) {
				planter(planterU, 1);
				set(planterU, 0, 0, seat(planterU, 0, Direction.SOUTH));
			}
		}

		// ------------------------------------------------------------------ 3–4 deep: a banquet table

		void banquet(int w, int d, boolean shadeOn) {
			int td = d >= 4 ? 2 : 1;
			int sd = td + 2;
			int v0 = (d - sd) / 2;
			boolean heads = w >= 4;
			int a = heads ? 1 : 0;
			int z = heads ? w - 2 : w - 1;
			boolean pergola = shadeOn && w >= 5;
			// pergola posts: both heads and one between benches every ~6 cells
			int posts = Math.max(1, Math.round((w - 1) / 6f));
			boolean[] postCol = new boolean[w];
			if (pergola) {
				for (int i = 0; i <= posts; i++) {
					postCol[Math.round(i * (w - 1f) / posts)] = true;
				}
			}
			int vBack = v0 + sd - 1;
			for (int u = a; u <= z; u++) {
				boolean leg = u == a || u == z || (z - a >= 6 && (u - a) % 4 == 0 && z - u >= 2);
				boolean postHere = postCol[u];
				if (!postHere) {
					set(u, 0, v0, seat(u, v0, Direction.NORTH));
					set(u, 0, vBack, seat(u, vBack, Direction.SOUTH));
				}
				for (int j = 0; j < td; j++) {
					int v = v0 + 1 + j;
					if (leg) {
						set(u, 0, v, wall(false, false));
					}
					set(u, 1, v, slab(m.top()));
				}
				// lamp stands on the table at the middle legs (the pergola carries its own lights)
				if (leg && u != a && u != z && !pergola && p.h() >= 3) {
					int v = v0 + 1 + p.hash(u, 1, 1, td);
					set(u, 1, v, wall(false, false));
					lantern(u, 2, v, false);
				}
			}
			if (heads) {
				boolean lampLeft = p.hash(0, 0, 1, 2) == 0;
				head(0, v0 + 1, td, lampLeft && !pergola);
				head(w - 1, v0 + 1, td, !lampLeft && !pergola);
				if (!pergola) {
					for (int u : new int[] {0, w - 1}) {
						for (int v : new int[] {v0, vBack}) {
							groundCover(u, v);
						}
					}
				}
			}
			if (pergola) {
				pergola(0, v0, w, sd, postCol, p.h() >= 7);
			}
		}

		/** A table head: a lantern post (and a plant beside it when 2 deep), or planters. */
		void head(int u, int v, int td, boolean lamp) {
			if (lamp) {
				lampPost(u, v);
				if (td == 2) {
					planter(u, v + 1);
				}
			} else {
				for (int j = 0; j < td; j++) {
					planter(u, v + j);
				}
			}
		}

		// ------------------------------------------------------------------ 5+ deep and wide: seated sets

		void sets(int w, int d, boolean shadeOn) {
			int gap = 2; // two free cells between sets: a walk, and the lamp posts stay apart
			int nu = Math.max(1, Math.min(4, (w + gap) / (9 + gap)));
			int nv = Math.max(1, Math.min(3, (d + gap) / (7 + gap)));
			int sv = Math.min(7, (d + gap) / nv - gap);
			int su = Math.min(Math.min(9, sv + 2), (w + gap) / nu - gap); // a table is never much wider than deep
			int ou = (w - (nu * su + (nu - 1) * gap)) / 2;
			int ov = (d - (nv * sv + (nv - 1) * gap)) / 2;
			int slots = nu * nv;
			int treeSlot = slots >= 3 && p.h() >= 8 && p.hash(3, 0, 0, 2) == 0 ? slots - 1 : -1;
			int shapeBase = p.hash(2, 0, 0, 3);
			int shadeBase = p.hash(4, 0, 0, 3);
			if (w >= 12 && d >= 8) {
				// planters in the plot's four corners that no set covers
				for (int u : new int[] {0, w - 1}) {
					for (int v : new int[] {0, d - 1}) {
						boolean covered = u >= ou && u < ou + nu * su + (nu - 1) * gap && v >= ov && v < ov + nv * sv + (nv - 1) * gap;
						if (!covered) {
							planter(u, v);
						}
					}
				}
			}
			// between two sets side by side: a lamp post and a planter, so the walk between them has a heart
			for (int iv = 0; iv < nv; iv++) {
				for (int iu = 0; iu + 1 < nu; iu++) {
					int gu = ou + (iu + 1) * su + iu * gap;
					int gv = ov + iv * (sv + gap) + sv / 2;
					lampPost(gu, gv);
					planter(gu + 1, gv);
				}
			}
			for (int iv = 0; iv < nv; iv++) {
				for (int iu = 0; iu < nu; iu++) {
					int slot = iu + iv * nu;
					int u0 = ou + iu * (su + gap);
					int v0 = ov + iv * (sv + gap);
					Shape shape = Shape.values()[(shapeBase + slot) % 3];
					if (su - 2 < 3 || sv - 2 < 3) {
						shape = Shape.SQUARE;
					}
					Shade shade = Shade.NONE;
					if (shadeOn) {
						int k = (shadeBase + slot) % 2;
						shade = k == 0 ? Shade.PERGOLA : Shade.PARASOL;
					}
					if (slot == treeSlot) {
						shape = Shape.TREE;
						shade = Shade.NONE;
					}
					gather(u0, v0, su, sv, shape, shade);
				}
			}
		}

		static boolean inTable(Shape s, int i, int j, int tw, int td) {
			switch (s) {
				case OCTAGON: {
					int cut = Math.max(1, Math.min(tw, td) / 3);
					return Math.min(i, tw - 1 - i) + Math.min(j, td - 1 - j) >= cut;
				}
				case ROUND: {
					double ex = (i + 0.5 - tw / 2.0) / (tw / 2.0);
					double ey = (j + 0.5 - td / 2.0) / (td / 2.0);
					return ex * ex + ey * ey <= 0.85;
				}
				default:
					return true;
			}
		}

		/** A table (or a tree) with seats round it, in the box {@code sw × sd} at {@code (u0, v0)}. */
		void gather(int u0, int v0, int sw, int sd, Shape shape, Shade shade) {
			int tw = sw - 2;
			int td = sd - 2;
			boolean tree = shape == Shape.TREE;
			boolean[][] tab = new boolean[sw][sd];
			if (tree) {
				tab[sw / 2][sd / 2] = true;
			} else {
				for (int i = 0; i < tw; i++) {
					for (int j = 0; j < td; j++) {
						tab[i + 1][j + 1] = inTable(shape, i, j, tw, td);
					}
				}
			}
			boolean diag = p.hash(u0, 6, v0, 2) == 0;
			// ---- seats, corner posts and open ground cell by cell
			for (int i = 0; i < sw; i++) {
				for (int j = 0; j < sd; j++) {
					if (tab[i][j]) {
						continue;
					}
					Direction toward = null;
					for (Direction dir : ORDER) {
						int ni = i + dir.getStepX();
						int nj = j + dir.getStepZ();
						if (ni >= 0 && ni < sw && nj >= 0 && nj < sd && tab[ni][nj]) {
							toward = dir;
							break;
						}
					}
					int u = u0 + i;
					int v = v0 + j;
					if (toward != null) {
						set(u, 0, v, seat(u, v, toward.getOpposite()));
					} else if (!((i == 0 || i == sw - 1) && (j == 0 || j == sd - 1))) {
						groundCover(u, v);
					}
				}
			}
			// ---- corners: pergola posts, or lantern posts on one diagonal and planters on the other
			if (shade == Shade.PARASOL && tw >= td + 3) {
				shade = Shade.PERGOLA; // a long thin table has no centre pole
			}
			boolean pergola = shade == Shade.PERGOLA;
			for (int ci = 0; ci < 2; ci++) {
				for (int cj = 0; cj < 2; cj++) {
					int u = u0 + ci * (sw - 1);
					int v = v0 + cj * (sd - 1);
					if (pergola) {
						continue;
					}
					if ((ci == cj) == diag) {
						lampPost(u, v);
					} else {
						planter(u, v);
					}
				}
			}
			if (tree) {
				tree(u0 + sw / 2, v0 + sd / 2);
				return;
			}
			// ---- the table: slab top on a pedestal, or on trestles when long and thin
			int cx = tw / 2;
			int cy = td / 2;
			boolean trestle = tw >= td + 3;
			double mx = (tw - 1) / 2.0;
			double my = (td - 1) / 2.0;
			int r = Math.max(0, Math.min(tw, td) / 2 - 1);
			boolean centre = tw >= 3 && td >= 3 && !trestle;
			int feature = p.hash(u0, 5, v0, 5); // 0,1,2 board; 3 lamp; 4 planter
			if (pergola && feature == 3) {
				feature = 0;
			}
			if (shade == Shade.PARASOL) {
				feature = 9;
			}
			for (int i = 0; i < tw; i++) {
				for (int j = 0; j < td; j++) {
					if (!tab[i + 1][j + 1]) {
						continue;
					}
					int u = u0 + 1 + i;
					int v = v0 + 1 + j;
					boolean leg = trestle ? i == 1 || i == tw - 2 : Math.abs(i - mx) + Math.abs(j - my) <= r + 0.5;
					if (leg) {
						set(u, 0, v, brick(u, 0, v));
					}
					set(u, 1, v, slab(m.top()));
					if (centre && tw >= 5 && td >= 5 && isRim(tab, i + 1, j + 1)) {
						set(u, 1, v, slab(Blocks.POLISHED_DEEPSLATE_SLAB)); // a dark frame round a big top
					}
					if (trestle && j == td / 2 && i > 0 && i < tw - 1) {
						set(u, 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB)); // a dark runner down the middle
					}
					if (feature <= 2 && centre) {
						boolean rim = tw >= 5 && td >= 5 && isRim(tab, i + 1, j + 1);
						if (!rim) {
							set(u, 1, v, slab((i + j) % 2 == 0 ? Blocks.SMOOTH_QUARTZ_SLAB : Blocks.DEEPSLATE_TILE_SLAB));
						}
					}
				}
			}
			int uc = u0 + 1 + cx;
			int vc = v0 + 1 + cy;
			if (centre && feature == 3 && p.h() >= 3) {
				set(uc, 1, vc, wall(false, false));
				lantern(uc, 2, vc, false);
			} else if (centre && feature == 4 && p.h() >= 3) {
				set(uc, 1, vc, b(m.cap()));
				set(uc, 2, vc, p.hash(uc, 2, vc, 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
			} else if (!centre && !pergola && p.h() >= 3) {
				// a long thin table: lamp stands every 4 cells down the runner
				for (int i = cx % 4; i < tw; i += 4) {
					if (i < 1 || i > tw - 2 || !tab[i + 1][cy + 1]) {
						continue;
					}
					int us = u0 + 1 + i;
					set(us, 0, vc, brick(us, 0, vc));
					set(us, 1, vc, wall(false, false));
					lantern(us, 2, vc, false);
				}
			}
			if (pergola) {
				pergola(u0, v0, sw, sd, null, p.h() >= 7);
			} else if (shade == Shade.PARASOL) {
				parasol(u0, v0, sw, sd, uc, vc);
			}
		}

		static boolean isRim(boolean[][] tab, int i, int j) {
			for (Direction dir : ORDER) {
				if (!tab[i + dir.getStepX()][j + dir.getStepZ()]) {
					return true;
				}
			}
			return false;
		}

		// ------------------------------------------------------------------ shade

		/**
		 * A light cherry pergola over the box {@code sw × sd}, five up so a sitter (or someone walking the table) has
		 * headroom: posts (dark footing, a log foot, thin fence shaft), a ring of slab beams with planks at the corners,
		 * a spine through the middle when deep, slab slats over the rest and lanterns hanging from the beams.
		 * {@code postCol} lists the columns that carry posts (null: the two end columns).
		 */
		void pergola(int u0, int v0, int sw, int sd, boolean[] postCol, boolean blossom) {
			int ub = u0 + sw - 1;
			int vb = v0 + sd - 1;
			int top = 5;
			BlockState beam = slab(Blocks.CHERRY_SLAB);
			for (int u = u0; u <= ub; u++) {
				boolean post = postCol == null ? u == u0 || u == ub : postCol[u - u0];
				if (!post) {
					continue;
				}
				for (int v : new int[] {v0, vb}) {
					set(u, 0, v, b(m.foot()));
					set(u, 1, v, b(Blocks.STRIPPED_CHERRY_LOG));
					for (int y = 2; y < top; y++) {
						set(u, y, v, b(Blocks.CHERRY_FENCE));
					}
					set(u, top, v, b(Blocks.CHERRY_PLANKS));
				}
			}
			for (int u = u0; u <= ub; u++) {
				for (int v : new int[] {v0, vb}) {
					boolean post = postCol == null ? u == u0 || u == ub : postCol[u - u0];
					if (!post) {
						set(u, top, v, beam);
					}
				}
			}
			for (int v = v0 + 1; v < vb; v++) {
				set(u0, top, v, beam);
				set(ub, top, v, beam);
			}
			int spine = sd >= 5 ? v0 + sd / 2 : -1;
			int mid = v0 + sd / 2;
			for (int v = v0 + 1; v < vb; v++) {
				for (int u = u0 + 1; u < ub; u++) {
					if (v == spine || (u - u0) % 2 == 0) {
						set(u, top, v, beam);
					}
				}
			}
			// lanterns hang from the spine (or from a slat column of a shallow roof), one every 4 cells
			int lampV = spine >= 0 ? spine : mid;
			int c0 = u0 + sw / 2;
			if (spine < 0 && (c0 - u0) % 2 != 0) {
				c0--;
			}
			for (int u = c0; u > u0 + 1; u -= 4) {
				lantern(u, top - 1, lampV, true);
			}
			for (int u = c0 + 4; u < ub - 1; u += 4) {
				lantern(u, top - 1, lampV, true);
			}
			if (blossom) {
				// one clump of blossom on the back beam, the rest open to the sky
				int cu = p.hash(u0, 4, v0, 2) == 0 ? u0 + 1 : ub - 1;
				int dir = cu == u0 + 1 ? 1 : -1;
				for (int k = 0; k < 3; k++) {
					set(cu + k * dir, top + 1, vb, leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}

		/**
		 * A parasol over the table: a log foot and fence pole from the pedestal, a stepped canopy (slab rim, plank
		 * middle, a raised slab cap with blossom) and a lantern as the finial; lanterns hang beneath.
		 */
		void parasol(int u0, int v0, int sw, int sd, int uc, int vc) {
			for (int y = 1; y <= 4; y++) {
				set(uc, y, vc, b(y == 1 ? Blocks.STRIPPED_CHERRY_LOG : Blocks.CHERRY_FENCE));
			}
			for (int i = 0; i < sw; i++) {
				for (int j = 0; j < sd; j++) {
					double ex = (i + 0.5 - sw / 2.0) / (sw / 2.0);
					double ey = (j + 0.5 - sd / 2.0) / (sd / 2.0);
					double e = ex * ex + ey * ey;
					if (e > 0.66) {
						continue;
					}
					int u = u0 + i;
					int v = v0 + j;
					// the rim is slabs, the middle full planks, so the canopy has a thick edge and a lip
					set(u, 5, v, e > 0.40 ? slab(Blocks.CHERRY_SLAB) : b(Blocks.CHERRY_PLANKS));
					if (p.h() >= 7 && e <= 0.40) {
						boolean cap = e <= 0.12;
						set(u, 6, v, cap ? b(Blocks.CHERRY_PLANKS) : slab(Blocks.CHERRY_SLAB));
						if (!cap && p.hash(u, 7, v, 3) == 0 && p.h() >= 8) {
							set(u, 7, v, leaves(Blocks.CHERRY_LEAVES));
						}
					}
				}
			}
			if (p.h() >= 8) {
				lantern(uc, 7, vc, false); // a gold lantern as the finial
			}
			int off = sw >= 7 ? 2 : 1;
			lantern(uc - off, 4, vc, true);
			lantern(uc + off, 4, vc, true);
		}

		/** A small cherry tree: trunk, a blossom crown, petals on the ground. */
		void tree(int uc, int vc) {
			for (int y = 0; y <= 4; y++) {
				set(uc, y, vc, b(Blocks.CHERRY_LOG));
			}
			for (int dy = 4; dy <= 7; dy++) {
				for (int du = -2; du <= 2; du++) {
					for (int dv = -2; dv <= 2; dv++) {
						int e = Math.abs(du) + Math.abs(dv);
						boolean in = dy == 4 || dy == 7 ? e <= 1 : e <= 3;
						if (dy == 4 && e == 0) {
							continue; // the trunk
						}
						if (in) {
							set(uc + du, dy, vc + dv, leaves(Blocks.CHERRY_LEAVES));
						}
					}
				}
			}
		}
	}
}
