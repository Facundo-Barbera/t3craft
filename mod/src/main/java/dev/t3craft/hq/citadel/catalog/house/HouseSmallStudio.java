package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * A small studio-cottage for the little corners of a barrio (the other houses need 9×10 and up, this one fits 9×9).
 * One storey with the roof as the ceiling: 5 free blocks at the walls and up to 9 under the ridge, so it never feels
 * low even on 9×9. Nothing is sealed and there is no ladder.
 * <ul>
 * <li><b>Outside:</b> a covered porch (stout stone-based columns with corbel brackets, a wide step, hanging lanterns, a
 *     king post under the front gable), a gable roof with eaves, a rake and a capped ridge, big three-high windows
 *     with sills, flower boxes and open shutters, a brick or stone chimney with a body of its own (stone base, a
 *     stack three deep through the slope, a corbelled crown with two pots).</li>
 * <li><b>Inside:</b> a library wall under a clerestory strip along the back, a lectern, a bed with a chest at its
 *     foot, a workshop row (crafting, cartography, fletching and smithing tables) and a hearth you can stand in front
 *     of, a hanging lantern on a chain and tie beams. From 10×12 on a study table with two chairs.</li>
 * </ul>
 * The door is a doorway 3 free blocks high. The house stops at 12 × 12; the rest of a bigger plot (plots grow with the
 * scale) is left to the leader's yard, with a few plants.
 *
 * <p>[silhouette] By seed one of three shapes, so a barrio does not repeat one outline:
 * <ul>
 * <li><b>dormer</b>: small gabled dormers on the long roof slope (two on deep plots);</li>
 * <li><b>cross gable</b>: a lateral gable wing flush with the side wall that climbs to the ridge;</li>
 * <li><b>lean-to</b>: an open shed (one-pitch roof, a log pile, a bench, a lantern) against the side wall, for firewood
 *     and an outdoor workshop; the body gets narrower to make room for it.</li>
 * </ul>
 * On the big plots (11 and up) the roof breaks: a tall transverse bay (a gable that crosses the ridge two blocks
 * higher, with clerestory windows) and, for the dormer and cross shapes, a timber terrace with a pergola, a table and
 * a side door; the lean-to shape keeps its shed as the annex.
 *
 * <p>[palette] Two schemes by seed (cherry roof over calcite and dark oak, or slate roof over stone brick and spruce) and
 * a mirror flip (the chimney on either side) so a barrio does not repeat one house.
 *
 * <pre>
 *  local layout (front = v 0, looking down)           elevation
 *   porch: posts, beam, rails, steps  v 0..pd-1        y9  ridge
 *   front wall + door                 v pd             y5  plate beam, roof eave
 *   room: bed + chest left, hearth right              y2-4 windows
 *   back wall: shelves y1-3, clerestory y4            y1  stone footing   y0 floor
 * </pre>
 *
 * Size: 9–12 × 9–12 × 12+.
 */
public final class HouseSmallStudio implements Variant {
	@Override
	public String id() {
		return "house_small_studio";
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
		return 9;
	}

	@Override
	public int minH() {
		return 12;
	}

	@Override
	public int maxW() {
		return 12;
	}

	@Override
	public int maxD() {
		return 12;
	}

	private record Scheme(Block frame, Block beam, BlockState[] wall, BlockState[] foot, BlockState[] porch, BlockState[] floor, Block rug,
			Block roof, Block eave, Block alt, Block ridge, Block ridgeSlab, Block door, Block shutter, Block fence, Block sill,
			Block step, BlockState[] chimney, Block chimneyStair, Block chimneySlab, DyeColor bed, DyeColor glass, Block lintel) {
	}

	/** A gabled wing whose ridge runs along u: centre row, half width, ridge height, the sides it shows on, tall = through bay. */
	private record Wing(int dv, int hw, int r, boolean left, boolean right, boolean tall) {
	}

	private static BlockState[] mix(Block... bl) {
		BlockState[] r = new BlockState[bl.length];
		for (int i = 0; i < bl.length; i++) {
			r[i] = b(bl[i]);
		}
		return r;
	}

	private static final Scheme[] SCHEMES = {
			// cherry roof, slate crest and eave, calcite plaster, dark oak frame, deepslate footing
			new Scheme(Blocks.STRIPPED_SPRUCE_LOG, Blocks.DARK_OAK_LOG,
					mix(Blocks.CALCITE, Blocks.CALCITE, Blocks.CALCITE, Blocks.CALCITE, Blocks.POLISHED_DIORITE),
					mix(Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES),
					mix(Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES),
					mix(Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS), Blocks.CHERRY_PLANKS,
					Blocks.CHERRY_STAIRS, Blocks.DEEPSLATE_TILE_STAIRS, null, Blocks.CHERRY_PLANKS, Blocks.DEEPSLATE_TILE_SLAB,
					Blocks.DARK_OAK_DOOR, Blocks.CHERRY_TRAPDOOR, Blocks.CHERRY_FENCE, Blocks.DEEPSLATE_BRICK_STAIRS,
					Blocks.DEEPSLATE_BRICK_STAIRS,
					mix(Blocks.BRICKS, Blocks.BRICKS, Blocks.BRICKS, Blocks.STONE_BRICKS), Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB,
					DyeColor.PINK, DyeColor.PINK, Blocks.CHERRY_PLANKS),
			// slate roof with a spruce eave, stone-brick walls, spruce frame, mossy cobble footing
			new Scheme(Blocks.STRIPPED_BIRCH_LOG, Blocks.SPRUCE_LOG,
					mix(Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS),
					mix(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS),
					mix(Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS),
					mix(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS), Blocks.BAMBOO_MOSAIC,
					Blocks.DEEPSLATE_TILE_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB,
					Blocks.CHERRY_DOOR, Blocks.CHERRY_TRAPDOOR, Blocks.SPRUCE_FENCE, Blocks.STONE_BRICK_STAIRS,
					Blocks.STONE_BRICK_STAIRS,
					mix(Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS), Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
					DyeColor.RED, DyeColor.LIGHT_BLUE, Blocks.CHISELED_STONE_BRICKS),
	};

	/** Height of the roof's first row (the eave) and of the plate beam on the walls. */
	private static final int ROOF = 5;

	/** The height a house of frontage {@code w} needs: the chimney cap stands above the ridge (two more under a tall bay). */
	private static int need(int w, boolean tall) {
		return ROOF + (w - 1) / 2 + 3 + (tall ? 2 : 0);
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x57D10E5L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		boolean flip = r.nextBoolean();
		// 0 dormers, 1 cross gable, 2 lean-to: by seed mod 3, so neighbouring seeds always differ in outline
		int shape = (int) Math.floorMod(p.seed(), 3L);
		int pw = Math.min(p.w(), 12);
		int d = Math.min(p.d(), 12);
		// the side strip: a lean-to shed (1 wide on 9, else 2) or, on the big plots, a terrace
		int lt = shape == 2 ? (pw >= 10 ? 2 : 1) : (pw >= 11 ? 2 : 0);
		int w = pw - lt;
		while (w > 8 && need(w, false) > p.h()) {
			w--;
		}
		boolean big = pw >= 11 || d >= 11;
		boolean tall = big && need(w, true) <= p.h();
		new Build(c, p, s, w, d, lt, shape, tall, flip).run();
	}

	private static final class Build {
		final CitadelCanvas c;
		final Plot p;
		final Scheme s;
		final int W;
		final int D;
		/** Width of the side strip (shed or terrace) at u -lt..-1, and which shape this house has. */
		final int lt;
		final int shape;
		final int ox;
		final boolean flip;
		final int H;
		/** Porch depth. */
		final int pd;
		/** Interior rectangle. */
		final int iu0;
		final int iu1;
		final int iv0;
		final int iv1;
		/** Chimney rows (along v) and the ridge height. */
		final int cv0;
		final int cv1;
		final int ridgeY;
		/** Door columns (one or two) and the step bay. */
		final int doorLo;
		final int doorHi;
		/** Row of the side door onto the strip, or -1. */
		final int sdv;
		final List<Wing> wings = new ArrayList<>();
		final BlockState[][][] g;

		Build(CitadelCanvas c, Plot p, Scheme s, int w, int d, int lt, int shape, boolean tall, boolean flip) {
			this.c = c;
			this.p = p;
			this.s = s;
			this.W = w;
			this.D = d;
			this.lt = lt;
			this.shape = shape;
			int slack = (p.w() - (w + lt)) / 2;
			this.ox = flip ? slack : slack + lt;
			this.flip = flip;
			this.H = p.h();
			this.pd = d >= 11 ? 3 : 2;
			this.iu0 = 2;
			this.iu1 = w - 3;
			this.iv0 = pd + 1;
			this.iv1 = d - 2;
			this.cv0 = d - 5;
			this.cv1 = d - 4;
			this.ridgeY = ROOF + (w - 1) / 2;
			this.doorLo = (w - 1) / 2;
			this.doorHi = w / 2;
			int mid = (pd + d - 1) / 2;
			int lm = (d - pd - 2) >= 6 ? pd + 1 + (d - pd - 2) / 2 : -99;
			this.sdv = lt > 0 ? (lm > 0 ? lm : mid) : -1;
			this.g = new BlockState[w + lt][H][d];
			planWings(tall);
		}

		/** [silhouette] The roof wings of this shape: dormers, a cross gable, and on the big plots a tall transverse bay. */
		void planWings(boolean tall) {
			int rLo = pd + 1;
			int rHi = D - 2;
			int n = rHi - rLo + 1;
			int centre = (rLo + rHi) / 2;
			if (tall) {
				if (shape == 1) {
					wings.add(new Wing(centre, 2, ridgeY + 2, true, true, true));
				} else {
					wings.add(new Wing(shape == 0 ? rLo + 1 : centre, 1, ridgeY + 2, true, true, true));
					if (shape == 0 && D >= 12) {
						wings.add(new Wing(rHi - 1, 1, ridgeY - 1, true, false, false));
					}
				}
				return;
			}
			if (shape == 0) {
				if (n >= 7) {
					wings.add(new Wing(rLo + 1, 1, ridgeY - 1, true, false, false));
					wings.add(new Wing(rHi - 1, 1, ridgeY - 1, true, false, false));
				} else {
					wings.add(new Wing(centre, 1, ridgeY - 1, true, false, false));
				}
			} else if (shape == 1) {
				wings.add(new Wing(centre, 2, ridgeY, true, false, false));
			}
		}

		// -------------------------------------------------------------- writes (design coordinates, flipped on the way out)

		void set(int u, int y, int v, BlockState st) {
			put(u, y, v, st, CitadelCanvas.SOLID);
		}

		void put(int u, int y, int v, BlockState st, int layer) {
			if (u < -lt || u >= W || v < 0 || v >= D || y < 0 || y >= H) {
				return;
			}
			g[u + lt][y][v] = st;
			int pu = ox + (flip ? W - 1 - u : u);
			p.put(c, pu, y, v, flip ? st.mirror(Mirror.FRONT_BACK) : st, layer);
		}

		boolean free(int u, int y, int v) {
			if (u < -lt || u >= W || v < 0 || v >= D || y < 0 || y >= H) {
				return false;
			}
			BlockState st = g[u + lt][y][v];
			return st == null || st.isAir();
		}

		void line(boolean alongU, int line, int i, int y, BlockState st) {
			if (alongU) {
				set(i, y, line, st);
			} else {
				set(line, y, i, st);
			}
		}

		BlockState pick(BlockState[] m, int u, int y, int v) {
			return m[p.hash(u, y, v, m.length)];
		}

		BlockState post() {
			return TimberKit.log(s.frame(), Direction.Axis.Y);
		}

		BlockState beam(Direction.Axis axis) {
			return TimberKit.log(s.beam(), axis);
		}

		BlockState face(Block bl, Direction f) {
			return b(bl).setValue(BlockStateProperties.HORIZONTAL_FACING, f);
		}

		BlockState barrel() {
			return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
		}

		// -------------------------------------------------------------- roof geometry

		/** Height of the main gable's surface at column {@code u}. */
		int hMain(int u) {
			return ROOF + Math.min(u, W - 1 - u);
		}

		/** The wing whose roof is above the main one at {@code (u, v)}, or null. The eave columns never take a wing. */
		Wing wingAt(int u, int v) {
			if (u <= 0 || u >= W - 1) {
				return null;
			}
			Wing best = null;
			int bt = hMain(u);
			for (Wing w : wings) {
				int t = Math.abs(v - w.dv());
				if (t > w.hw()) {
					continue;
				}
				boolean side = (w.left() && w.right()) || (w.left() && u <= (W - 1) / 2) || (w.right() && u >= W / 2);
				if (side && w.r() - t > bt) {
					best = w;
					bt = w.r() - t;
				}
			}
			return best;
		}

		/** The roof surface height at {@code (u, v)}. */
		int topRoof(int u, int v) {
			Wing w = wingAt(u, v);
			return w == null ? hMain(u) : w.r() - Math.abs(v - w.dv());
		}

		/** The highest roof line over row {@code v}. */
		int ridgeAt(int v) {
			int r = ridgeY;
			for (Wing w : wings) {
				if (Math.abs(v - w.dv()) <= w.hw()) {
					r = Math.max(r, w.r() - Math.abs(v - w.dv()));
				}
			}
			return r;
		}

		// -------------------------------------------------------------- run

		void run() {
			floors();
			IntPredicate none = i -> false;
			boolean[] winL = wall(false, 1, pd, D - 1, i -> i == pd || i == D - 1 || i == leftMid(), none, sdv, sdv, 1, 4);
			boolean[] winR = wall(false, W - 2, pd, D - 1, i -> i == pd || i == D - 1 || i == cv0 || i == cv1, none, -1, -1, 1, 4);
			wall(true, pd, 1, W - 2, i -> i == 1 || i == W - 2, none, doorLo, doorHi, 1, 4);
			// the back: shelves stand against the plain ends, the middle is a big window over the desk
			wall(true, D - 1, 1, W - 2, i -> i == 1 || i == W - 2, i -> i < iu0 + shelfN() || i > iu1 - shelfN(), -1, -1, 1, 4);
			doors();
			gables();
			roof();
			porch();
			chimney();
			if (lt == 0) {
				dressSide(false, winL, i -> i == pd || i == D - 1 || i == leftMid());
			} else if (shape == 2) {
				leanTo();
			} else {
				terrace();
			}
			dressSide(true, winR, i -> i == pd || i == D - 1 || i == cv0 || i == cv1);
			interior();
			yard();
		}

		int leftMid() {
			int span = D - pd - 2;
			return span >= 6 ? pd + 1 + span / 2 : -99;
		}

		/** Columns of bookshelves at each end of the back wall. */
		int shelfN() {
			return W - 4 >= 7 ? 2 : 1;
		}

		// -------------------------------------------------------------- floor

		void floors() {
			for (int u = 1; u <= W - 2; u++) {
				for (int v = 0; v < D; v++) {
					BlockState st;
					if (v < pd) {
						st = pick(s.porch(), u, 0, v);
					} else if (v >= pd && (u == 1 || u == W - 2 || v == pd || v == D - 1)) {
						st = pick(s.foot(), u, 0, v); // [palette] the plinth runs under the walls
					} else if (u == iu1 && v >= cv0 && v <= cv1) {
						st = b(Blocks.DEEPSLATE_TILES); // [palette] the hearth stone
					} else if (u > iu0 && u < iu1 && v > iv0 && v < iv1 - 1) {
						st = b(s.rug()); // [finish] an inlaid rug where the room opens out
					} else {
						st = pick(s.floor(), u, 0, v);
					}
					set(u, 0, v, st);
				}
			}
		}

		// -------------------------------------------------------------- walls

		/** Window cells of a run [a..b]: every free gap of up to 4 cells is all glass, wider ones keep their ends solid. */
		boolean[] windows(int a, int bEnd, IntPredicate solid) {
			boolean[] w = new boolean[bEnd + 2];
			int i = a;
			while (i <= bEnd) {
				if (solid.test(i)) {
					i++;
					continue;
				}
				int j = i;
				while (j + 1 <= bEnd && !solid.test(j + 1)) {
					j++;
				}
				int g2 = j - i + 1;
				int lo = g2 <= 4 ? i : i + 1;
				int hi = g2 <= 4 ? j : j - 1;
				for (int k = lo; k <= hi; k++) {
					w[k] = true;
				}
				i = j + 1;
			}
			return w;
		}

		/**
		 * [depth] A wall: posts the full height, a stone footing, plaster, big windows in rows {@code wy1..wy2} and a
		 * plate beam on top. A doorway ({@code doorLo..doorHi}) is 3 free blocks high under a lintel beam.
		 * Returns which cells are windows.
		 */
		boolean[] wall(boolean alongU, int ln, int a, int bEnd, IntPredicate post, IntPredicate plain, int dLo, int dHi, int wy1, int wy2) {
			IntPredicate solid = i -> post.test(i) || plain.test(i) || (i >= dLo && i <= dHi);
			boolean[] win = windows(a, bEnd, solid);
			Direction.Axis ax = alongU ? Direction.Axis.X : Direction.Axis.Z;
			for (int i = a; i <= bEnd; i++) {
				boolean isPost = post.test(i);
				boolean isDoor = i >= dLo && i <= dHi;
				for (int y = 1; y <= ROOF; y++) {
					BlockState st;
					if (isDoor) {
						st = y == 5 ? beam(ax) : y == 4 ? b(s.lintel()) : null; // y1-2 door (see doors()), y3 free, a light lintel over it
					} else if (isPost) {
						st = post();
					} else if (y == 1 && !(win[i] && wy1 <= 1)) {
						st = alongU ? pick(s.foot(), i, y, ln) : pick(s.foot(), ln, y, i);
					} else if (y == ROOF) {
						st = beam(ax);
					} else if (win[i] && y >= wy1 && y <= wy2) {
						st = TimberKit.pane(alongU, null);
					} else {
						st = alongU ? pick(s.wall(), i, y, ln) : pick(s.wall(), ln, y, i);
					}
					if (st != null) {
						line(alongU, ln, i, y, st);
					}
				}
			}
			// [depth] a proud post on the middle of a long side wall
			if (!alongU && ln == 1 && leftMid() > 0 && lt == 0) {
				for (int y = 1; y <= 4; y++) {
					set(0, y, leftMid(), post());
				}
			}
			return win;
		}

		void doors() {
			for (int u = doorLo; u <= doorHi; u++) {
				DoorHingeSide hinge = doorLo == doorHi || u == doorLo ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
				for (int k = 0; k < 2; k++) {
					set(u, 1 + k, pd, TimberKit.door(s.door(), Direction.NORTH, k == 1).setValue(BlockStateProperties.DOOR_HINGE, hinge));
				}
			}
			if (sdv > 0) { // the side door onto the shed or the terrace
				for (int k = 0; k < 2; k++) {
					set(1, 1 + k, sdv, TimberKit.door(s.door(), Direction.WEST, k == 1).setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT));
				}
			}
		}

		/** [depth] Open shutters flank every run of windows on a side wall (no ledges in the eave zone: nobody can reach them). */
		void dressSide(boolean right, boolean[] win, IntPredicate post) {
			int outer = right ? W - 1 : 0;
			Direction shut = right ? Direction.EAST : Direction.WEST;
			for (int v = pd + 1; v <= D - 2; v++) {
				if (!win[v]) {
					continue;
				}
				// [finish] a shrub at the foot of the window, where the ground meets the wall
				if (free(outer, 0, v) && p.hash(outer, 0, v, 2) == 0) {
					BlockState bush = TimberKit.groundPlant(p, outer, 0, v);
					if (bush != null) {
						set(outer, 0, v, bush);
					}
				}
				for (int e : new int[] {v - 1, v + 1}) {
					if (e > pd && e < D - 1 && !win[e] && !post.test(e) && free(outer, 3, e)) {
						for (int y = 1; y <= 4; y++) {
							set(outer, y, e, TimberKit.trap(s.shutter(), shut, true, false));
						}
					}
				}
			}
		}

		// -------------------------------------------------------------- roof

		void gables() {
			int mid0 = (W - 1) / 2;
			int mid1 = W / 2;
			for (int ln : new int[] {pd, D - 1}) {
				for (int u = 1; u <= W - 2; u++) {
					int h = ROOF + Math.min(u, W - 1 - u);
					for (int y = ROOF + 1; y < h; y++) {
						boolean glass = u >= mid0 && u <= mid1 && (y == ROOF + 1 || y == ROOF + 2);
						set(u, y, ln, glass ? TimberKit.pane(true, s.glass()) : pick(s.wall(), u, y, ln));
					}
				}
			}
		}

		/**
		 * [finish] A gable roof ridge along v: eaves a block out, a rake under the porch end, a capped ridge. [silhouette]
		 * The wings (dormers, the cross gable, the tall bay) replace the main roof where theirs is higher.
		 */
		void roof() {
			int mid0 = (W - 1) / 2;
			int mid1 = W / 2;
			for (int u = 0; u < W; u++) {
				int h = hMain(u);
				boolean ridge = u >= mid0 && u <= mid1;
				boolean edge = u == 0 || u == W - 1;
				Direction face = u < mid0 ? Direction.EAST : Direction.WEST;
				for (int v = 0; v < D; v++) {
					Wing w = wingAt(u, v);
					if (w != null) {
						wingCell(u, v, w, h);
						continue;
					}
					Block m = edge ? s.eave() : (s.alt() != null && p.hash(u, h, v, 5) == 0 ? s.alt() : s.roof());
					if (ridge) {
						set(u, h, v, b(s.ridge()));
						set(u, h + 1, v, TimberKit.slab(s.ridgeSlab(), false));
					} else {
						set(u, h, v, TimberKit.stair(m, face, false));
					}
				}
				if (!ridge && !edge) {
					set(u, h - 1, 0, TimberKit.stair(s.roof(), face.getOpposite(), true));
				}
			}
		}

		/** One cell of a wing's roof, with its gable face (at the side wall) or its cheek (at the wing's edge row). */
		void wingCell(int u, int v, Wing w, int h) {
			int t = Math.abs(v - w.dv());
			int top = w.r() - t;
			if (t == 0) {
				set(u, top, v, b(s.ridge()));
				set(u, top + 1, v, TimberKit.slab(s.ridgeSlab(), false));
			} else {
				set(u, top, v, TimberKit.stair(s.roof(), v < w.dv() ? Direction.SOUTH : Direction.NORTH, false));
			}
			if (t == w.hw()) {
				// the cheek: a wall from the main roof up to the wing's eave, glazed under the eave on a tall bay
				for (int y = h; y < top; y++) {
					boolean glass = w.tall() && u >= 2 && u <= W - 3 && y > h;
					set(u, y, v, glass ? TimberKit.pane(true, s.glass()) : pick(s.wall(), u, y, v));
				}
			} else if ((u == 1 && w.left()) || (u == W - 2 && w.right())) {
				// the gable face on the wall plane: glass up the middle, the top row stays plaster when it is tall
				int rows = top - h;
				for (int y = h; y < top; y++) {
					boolean glass = rows <= 2 || y <= top - 2;
					set(u, y, v, glass ? TimberKit.pane(false, s.glass()) : pick(s.wall(), u, y, v));
				}
			}
		}

		// -------------------------------------------------------------- porch

		void porch() {
			// [finish] a wide step: it spans the door and two blocks each side, between the corner columns
			int bayLo = Math.max(2, doorLo - 2);
			int bayHi = Math.min(W - 3, doorHi + 2);
			for (int u = bayLo; u <= bayHi; u++) {
				set(u, 0, 0, TimberKit.stair(s.step(), Direction.SOUTH, false));
			}
			boolean[] isPost = new boolean[W];
			isPost[1] = true;
			isPost[W - 2] = true;
			for (int u = 1; u <= W - 2; u++) {
				if (isPost[u]) {
					// [depth] a column with a base: two courses of stone under the shaft, a bracket under the beam
					set(u, 1, 0, pick(s.foot(), u, 1, 0));
					set(u, 2, 0, pick(s.foot(), u, 2, 0));
					for (int y = 3; y <= ROOF; y++) {
						set(u, y, 0, post());
					}
				} else {
					set(u, ROOF, 0, beam(Direction.Axis.X)); // [depth] the front beam
				}
			}
			set(2, 4, 0, TimberKit.stair(s.sill(), Direction.WEST, true));
			set(W - 3, 4, 0, TimberKit.stair(s.sill(), Direction.EAST, true));
			// rails between the posts, except across the steps
			for (int u = 2; u <= W - 3; u++) {
				if (u >= bayLo && u <= bayHi) {
					continue;
				}
				boolean e = u + 1 <= W - 3 ? !(u + 1 >= bayLo && u + 1 <= bayHi) : true;
				boolean wst = u - 1 >= 2 ? !(u - 1 >= bayLo && u - 1 <= bayHi) : true;
				set(u, 1, 0, TimberKit.fence(s.fence(), false, e, false, wst));
			}
			for (int v = 1; v < pd; v++) {
				set(1, 1, v, TimberKit.fence(s.fence(), true, false, true, false));
				set(W - 2, 1, v, TimberKit.fence(s.fence(), true, false, true, false));
			}
			// [finish] king post under the front gable
			for (int u = doorLo; u <= doorHi; u++) {
				for (int y = ROOF + 1; y < ridgeY; y++) {
					set(u, y, 0, post());
				}
			}
			// [finish] balusters fill the gable face where the beam top would leave 2 free blocks to stand in
			for (int u = 2; u <= W - 3; u++) {
				int h = ROOF + Math.min(u, W - 1 - u);
				if (isPost[u] || (u >= doorLo && u <= doorHi) || h - 7 < 2) {
					continue;
				}
				for (int y = ROOF + 1; y <= h - 2; y++) {
					set(u, y, 0, TimberKit.fence(s.fence(), false, false, false, false));
				}
			}
			// [finish] a lantern on a chain over the doorstep, so the door reads from far away
			for (int y = ROOF + 1; y < ridgeY; y++) {
				put(doorLo, y, pd - 1, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y), CitadelCanvas.HANGING);
			}
			put(doorLo, ROOF, pd - 1, TimberKit.lantern(true), CitadelCanvas.HANGING);
			// [finish] a lightning rod at each end of the ridge
			for (int v : new int[] {0, D - 1}) {
				set(doorLo, ridgeY + 2, v, b(Blocks.LIGHTNING_ROD.waxed().unaffected()));
			}
			// [finish] lanterns hang from the beam over the steps, planters stand at the foot of the columns
			for (int u : new int[] {bayLo + 1, bayHi - 1}) {
				if (free(u, 4, 0) && !isPost[u] && u != doorLo && u != doorHi) {
					put(u, 4, 0, TimberKit.lantern(true), CitadelCanvas.HANGING);
				}
			}
			for (int u : new int[] {2, W - 3}) {
				if (free(u, 1, 1)) {
					set(u, 1, 1, TimberKit.planter(p, u, 1, 1));
				}
			}
		}

		// -------------------------------------------------------------- chimney and hearth

		/** [depth] A chimney with a body: a stone base, a stack three deep through the slope, a corbelled crown with two pots. */
		void chimney() {
			int top = 0;
			for (int v = cv0; v <= cv1; v++) {
				top = Math.max(top, ridgeAt(v) + 1);
			}
			for (int v = cv0; v <= cv1; v++) {
				for (int u = W - 2; u <= W - 1; u++) {
					for (int y = 1; y <= top; y++) {
						set(u, y, v, y <= 2 && u == W - 1 ? pick(s.foot(), u, y, v) : pick(s.chimney(), u, y, v));
					}
				}
				// the stack climbs through the slope one column further in
				for (int y = hMain(W - 3); y <= top; y++) {
					set(W - 3, y, v, pick(s.chimney(), W - 3, y, v));
				}
				set(W - 2, 1, v, b(Blocks.CAMPFIRE)); // the fireplace opens on the room
			}
			// the crown: a dark collar course, then a slab cap on exactly the stack (a wider cap would roof the slope below)
			for (int u = W - 3; u <= W - 1; u++) {
				for (int v = cv0; v <= cv1; v++) {
					set(u, top, v, pick(s.foot(), u, top, v));
					set(u, top + 1, v, TimberKit.slab(s.chimneySlab(), false));
				}
			}
			Block potWall = s.chimneyStair() == Blocks.BRICK_STAIRS ? Blocks.BRICK_WALL : Blocks.STONE_BRICK_WALL;
			for (int v = cv0; v <= cv1; v++) {
				set(W - 2, top + 2, v, TimberKit.lowWall(potWall, false, false, false, false));
			}
		}

		// -------------------------------------------------------------- the side strip

		/** [silhouette] An open lean-to against the left wall: one-pitch roof, posts, a log pile, a bench and a lantern. */
		void leanTo() {
			int vA = pd + 1;
			int vm = (pd + D - 1) / 2;
			for (int u = -lt; u <= 0; u++) {
				for (int v = pd; v <= D - 1; v++) {
					set(u, 0, v, pick(s.porch(), u, 0, v));
				}
			}
			for (int v = pd; v <= D - 1; v++) {
				if (lt == 1) {
					set(-1, 4, v, TimberKit.stair(s.eave(), Direction.EAST, false));
				} else {
					set(-2, 4, v, TimberKit.stair(s.eave(), Direction.EAST, false));
					set(-1, 5, v, TimberKit.slab(s.ridgeSlab(), false));
				}
			}
			for (int v : new int[] {pd, vm, D - 1}) {
				for (int y = 1; y <= 3; y++) {
					set(-lt, y, v, post());
				}
			}
			// the log pile at the back of the outer row, two high (the roof is too close above it to stand on)
			for (int v = D - 3; v <= D - 2; v++) {
				for (int y = 1; y <= 2; y++) {
					set(-lt, y, v, TimberKit.log(Blocks.SPRUCE_LOG, Direction.Axis.X));
				}
			}
			// the bench against the wall, clear of the side door. A block you can climb needs 2.85 free above it, and only
			// the inner row of a 2-wide shed (roof y5) and the row under the eave (u 0) have that.
			int bu = lt == 2 ? -1 : 0;
			int[] bv = {vA, vA + 1, vA + 2};
			Block[] work = {Blocks.CRAFTING_TABLE, Blocks.STONECUTTER, Blocks.GRINDSTONE};
			for (int k = 0; k < bv.length; k++) {
				int v = bv[k];
				if (Math.abs(v - sdv) >= 2 && v < vm && free(bu, 1, v)) {
					set(bu, 1, v, b(work[k]));
				}
			}
			if (lt == 2 && Math.abs(D - 2 - sdv) >= 2) {
				set(-1, 1, D - 2, barrel());
			}
			// a lantern stands on the log pile (one hung under this low roof would cut the headroom)
			set(-lt, 3, D - 3, TimberKit.lantern(false));
		}

		/** [silhouette] A timber terrace along the left wall: deck, steps, rail, a pergola, a table and a side door. */
		void terrace() {
			int vA = pd + 1;
			int vB = D - 1;
			for (int u = -lt; u <= 0; u++) {
				for (int v = vA; v <= vB; v++) {
					set(u, 0, v, pick(s.floor(), u, 0, v));
				}
				set(u, 0, pd, TimberKit.stair(s.step(), Direction.SOUTH, false)); // steps up from the front
			}
			// the rail round the outer edge and the back
			for (int v = vA + 1; v <= vB; v++) {
				set(-lt, 1, v, TimberKit.fence(s.fence(), true, v == vB, v < vB, false));
			}
			for (int u = -lt + 1; u <= 0; u++) {
				set(u, 1, vB, TimberKit.fence(s.fence(), false, true, false, true));
			}
			// the pergola: two posts, a beam along the edge, cross rafters over the deck
			int v1 = vA + 1;
			int v2 = D - 3;
			if (v2 - v1 >= 2) {
				for (int v : new int[] {v1, v2}) {
					for (int y = 1; y <= 4; y++) {
						set(-lt, y, v, post());
					}
				}
				for (int v = v1; v <= v2; v++) {
					set(-lt, 5, v, beam(Direction.Axis.Z));
				}
				// rafters reach in one block (the eave stairs hold the other end), high enough to stand under
				for (int v : new int[] {v1, v2}) {
					for (int u = -lt + 1; u <= -1; u++) {
						set(u, 5, v, beam(Direction.Axis.X));
					}
				}
				// lanterns hang over the outer row, where nobody stands on a chair under them
				put(-lt, 4, (v1 + v2) / 2, TimberKit.lantern(true), CitadelCanvas.HANGING);
				// a table with two chairs
				int tv = (v1 + v2) / 2;
				for (int v = tv; v <= tv + 1; v++) {
					set(-1, 1, v, TimberKit.slab(Blocks.DARK_OAK_SLAB, false));
				}
				set(-1, 1, tv - 1, TimberKit.stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
				if (tv + 2 <= D - 2) {
					set(-1, 1, tv + 2, TimberKit.stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
				}
			}
		}

		// -------------------------------------------------------------- interior

		void furn(int u, int v, BlockState st) {
			if (free(u, 1, v)) {
				set(u, 1, v, st);
			}
		}

		void interior() {
			int cu = (W - 1) / 2;
			// the library: shelves to the roof at both ends of the back wall, a window over the desk between them
			for (int u = iu0; u <= iu1; u++) {
				if (u >= iu0 + shelfN() && u <= iu1 - shelfN()) {
					continue;
				}
				// the stack runs up to the roof underside, so there is no shelf top to stand on
				for (int y = 1; y < topRoof(u, iv1); y++) {
					set(u, y, iv1, y % 2 == 0 && p.hash(u, y, iv1, 3) == 0 ? face(Blocks.CHISELED_BOOKSHELF, Direction.NORTH) : b(Blocks.BOOKSHELF));
				}
			}
			set(cu, 1, iv1 - 1, face(Blocks.LECTERN, Direction.NORTH).setValue(BlockStateProperties.HAS_BOOK, true));
			// sleeping corner on the left: bed head at the back, a chest at its foot
			BlockState bed = b(Blocks.BED.pick(s.bed())).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
			set(iu0, 1, iv1 - 1, bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
			set(iu0, 1, iv1 - 2, bed.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
			furn(iu0, iv1 - 3, face(Blocks.CHEST, Direction.EAST));
			for (int v = iv0, k = 0; v <= iv1 - 4; v++, k++) {
				if (v == sdv) {
					continue; // keep the way to the side door clear
				}
				furn(iu0, v, k % 2 == 0 ? barrel() : b(Blocks.POTTED_FERN));
			}
			// the workshop row on the right, in front of the hearth
			Block[] bench = {Blocks.CRAFTING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.SMITHING_TABLE};
			for (int v = iv0, k = 0; v < cv0; v++, k++) {
				furn(iu1, v, b(bench[k % bench.length]));
			}
			if (cv0 > iv0) {
				furn(iu1, iv0, b(Blocks.CRAFTING_TABLE));
			}
			for (int v = cv1 + 1; v < iv1; v++) {
				furn(iu1, v, (v - cv1) % 2 == 1 ? face(Blocks.CHEST, Direction.WEST) : barrel());
			}
			// a study table with two chairs when the room is big enough
			int cvm = (iv0 + iv1) / 2;
			if (W >= 10 && iv1 - iv0 + 1 >= 7) {
				int tv = iv0 + 3;
				cvm = tv;
				for (int u = cu; u <= cu + 1; u++) {
					set(u, 1, tv, TimberKit.slab(Blocks.DARK_OAK_SLAB, true));
				}
				furn(cu, tv - 1, TimberKit.stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
				furn(cu + 1, tv + 1, TimberKit.stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
			}
			// [depth] exposed rafter ribs hug the roof underside (no beam top to stand on), then a lantern on a chain
			for (int v : new int[] {cvm - 2, cvm + 2}) {
				if (v >= iv0 && v <= iv1 && v != cvm) {
					for (int u = iu0; u <= iu1; u++) {
						int ry = hMain(u) - 1;
						if (free(u, ry, v) && !free(u, ry + 1, v)) {
							set(u, ry, v, beam(Direction.Axis.X));
						}
					}
				}
			}
			int ceil = topRoof(cu, cvm);
			for (int y = 6; y < ceil; y++) {
				put(cu, y, cvm, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y), CitadelCanvas.HANGING);
			}
			put(cu, 5, cvm, TimberKit.lantern(true), CitadelCanvas.HANGING);
		}

		// -------------------------------------------------------------- the rest of a bigger plot

		void yard() {
			int lo = flip ? ox : ox - lt;
			int hi = flip ? ox + W - 1 + lt : ox + W - 1;
			for (int pu = 0; pu < p.w(); pu++) {
				for (int v = 0; v < p.d(); v++) {
					boolean house = pu >= lo && pu <= hi && v < D;
					if (house || p.hash(pu, 0, v, 3) != 0) {
						continue;
					}
					BlockState st = TimberKit.groundPlant(p, pu, 0, v);
					if (st != null) {
						p.set(c, pu, 0, v, st);
					}
				}
			}
		}
	}
}
