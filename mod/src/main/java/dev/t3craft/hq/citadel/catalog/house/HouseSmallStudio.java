package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
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
 * <li><b>Outside:</b> a covered porch (posts, beam, rails, hanging lanterns, a king post under the front gable), a
 *     gable roof with eaves, a rake and a capped ridge, big three-high windows with sills, flower boxes and open
 *     shutters, a brick or stone chimney that stands against the right gable.</li>
 * <li><b>Inside:</b> a library wall under a clerestory strip along the back, a lectern, a bed with a chest at its
 *     foot, a workshop row (crafting, cartography, fletching and smithing tables) and a hearth you can stand in front
 *     of, a hanging lantern on a chain and tie beams. From 10×12 on a study table with two chairs.</li>
 * </ul>
 * The door is a doorway 3 free blocks high. The house stops at 12 × 12; the rest of a bigger plot (plots grow with the
 * scale) is left to the leader's yard, with a few plants.
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
			Block step, BlockState[] chimney, Block chimneyStair, Block chimneySlab, DyeColor bed, DyeColor glass) {
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
			new Scheme(Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_LOG,
					mix(Blocks.CALCITE, Blocks.CALCITE, Blocks.CALCITE, Blocks.CALCITE, Blocks.POLISHED_DIORITE),
					mix(Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES),
					mix(Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES),
					mix(Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS), Blocks.CHERRY_PLANKS,
					Blocks.CHERRY_STAIRS, Blocks.DEEPSLATE_TILE_STAIRS, null, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB,
					Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.DARK_OAK_FENCE, Blocks.DEEPSLATE_BRICK_STAIRS,
					Blocks.DEEPSLATE_BRICK_STAIRS,
					mix(Blocks.BRICKS, Blocks.BRICKS, Blocks.BRICKS, Blocks.STONE_BRICKS), Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB,
					DyeColor.PINK, DyeColor.PINK),
			// slate roof with a spruce eave, stone-brick walls, spruce frame, mossy cobble footing
			new Scheme(Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_LOG,
					mix(Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS),
					mix(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS),
					mix(Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS),
					mix(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS), Blocks.BAMBOO_MOSAIC,
					Blocks.DEEPSLATE_TILE_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE_SLAB,
					Blocks.SPRUCE_DOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.SPRUCE_FENCE, Blocks.STONE_BRICK_STAIRS,
					Blocks.STONE_BRICK_STAIRS,
					mix(Blocks.STONE_BRICKS, Blocks.COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS), Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
					DyeColor.RED, DyeColor.LIGHT_BLUE),
	};

	/** Height of the roof's first row (the eave) and of the plate beam on the walls. */
	private static final int ROOF = 5;

	/** The height a house of frontage {@code w} needs: the chimney cap stands above the ridge. */
	private static int need(int w) {
		return ROOF + (w - 1) / 2 + 3;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x57D10E5L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		boolean flip = r.nextBoolean();
		int w = Math.min(p.w(), 12);
		while (w > 9 && need(w) > p.h()) {
			w--;
		}
		int d = Math.min(p.d(), 12);
		new Build(c, p, s, w, d, (p.w() - w) / 2, flip).run();
	}

	private static final class Build {
		final CitadelCanvas c;
		final Plot p;
		final Scheme s;
		final int W;
		final int D;
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
		final BlockState[][][] g;

		Build(CitadelCanvas c, Plot p, Scheme s, int w, int d, int ox, boolean flip) {
			this.c = c;
			this.p = p;
			this.s = s;
			this.W = w;
			this.D = d;
			this.ox = ox;
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
			this.g = new BlockState[w][H][d];
		}

		// -------------------------------------------------------------- writes (design coordinates, flipped on the way out)

		void set(int u, int y, int v, BlockState st) {
			put(u, y, v, st, CitadelCanvas.SOLID);
		}

		void put(int u, int y, int v, BlockState st, int layer) {
			if (u < 0 || u >= W || v < 0 || v >= D || y < 0 || y >= H) {
				return;
			}
			g[u][y][v] = st;
			int pu = ox + (flip ? W - 1 - u : u);
			p.put(c, pu, y, v, flip ? st.mirror(Mirror.FRONT_BACK) : st, layer);
		}

		boolean free(int u, int y, int v) {
			if (u < 0 || u >= W || v < 0 || v >= D || y < 0 || y >= H) {
				return false;
			}
			BlockState st = g[u][y][v];
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

		// -------------------------------------------------------------- run

		void run() {
			floors();
			IntPredicate none = i -> false;
			boolean[] winL = wall(false, 1, pd, D - 1, i -> i == pd || i == D - 1 || i == leftMid(), none, -1, -1, 2, 4);
			boolean[] winR = wall(false, W - 2, pd, D - 1, i -> i == pd || i == D - 1 || i == cv0 || i == cv1, none, -1, -1, 2, 4);
			wall(true, pd, 1, W - 2, i -> i == 1 || i == W - 2, none, doorLo, doorHi, 2, 4);
			// the back: shelves stand against the plain ends, the middle is a big window over the desk
			wall(true, D - 1, 1, W - 2, i -> i == 1 || i == W - 2, i -> i < iu0 + shelfN() || i > iu1 - shelfN(), -1, -1, 2, 4);
			doors();
			gables();
			roof();
			porch();
			chimney();
			dressSide(false, winL, i -> i == pd || i == D - 1 || i == leftMid());
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
						st = y == 4 || y == 5 ? beam(ax) : null; // y1-2 door (see doors()), y3 free
					} else if (isPost) {
						st = post();
					} else if (y == 1) {
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
			if (!alongU && ln == 1 && leftMid() > 0) {
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
		}

		/** [depth] Open shutters flank every run of windows on a side wall (no ledges in the eave zone: nobody can reach them). */
		void dressSide(boolean right, boolean[] win, IntPredicate post) {
			int outer = right ? W - 1 : 0;
			Direction shut = right ? Direction.EAST : Direction.WEST;
			for (int v = pd + 1; v <= D - 2; v++) {
				if (!win[v]) {
					continue;
				}
				for (int e : new int[] {v - 1, v + 1}) {
					if (e > pd && e < D - 1 && !win[e] && !post.test(e) && free(outer, 3, e)) {
						for (int y = 2; y <= 4; y++) {
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

		/** [finish] A gable roof ridge along v: eaves a block out, a rake under the porch end, a capped ridge. */
		void roof() {
			int mid0 = (W - 1) / 2;
			int mid1 = W / 2;
			for (int u = 0; u < W; u++) {
				int h = ROOF + Math.min(u, W - 1 - u);
				boolean ridge = u >= mid0 && u <= mid1;
				boolean edge = u == 0 || u == W - 1;
				Direction face = u < mid0 ? Direction.EAST : Direction.WEST;
				for (int v = 0; v < D; v++) {
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

		// -------------------------------------------------------------- porch

		void porch() {
			int bayLo = doorLo - 1;
			int bayHi = doorHi + 1;
			// steps down to the path
			for (int u = bayLo; u <= bayHi; u++) {
				set(u, 0, 0, TimberKit.stair(s.step(), Direction.SOUTH, false));
			}
			boolean[] isPost = new boolean[W];
			isPost[1] = true;
			isPost[W - 2] = true;
			if (bayLo - 1 >= 3) {
				isPost[bayLo - 1] = true;
				isPost[bayHi + 1] = true;
			}
			for (int u = 1; u <= W - 2; u++) {
				if (isPost[u]) {
					set(u, 1, 0, pick(s.foot(), u, 1, 0));
					for (int y = 2; y <= ROOF; y++) {
						set(u, y, 0, post());
					}
				} else {
					set(u, ROOF, 0, beam(Direction.Axis.X)); // [depth] the front beam
				}
			}
			// rails between the posts, except across the steps
			for (int u = 2; u <= W - 3; u++) {
				if (isPost[u] || (u >= bayLo && u <= bayHi)) {
					continue;
				}
				boolean e = isPost[u + 1] || (u + 1 <= W - 3 && !(u + 1 >= bayLo && u + 1 <= bayHi));
				boolean wst = isPost[u - 1] || (u - 1 >= 2 && !(u - 1 >= bayLo && u - 1 <= bayHi));
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
			// [finish] lanterns hang from the beam beside the steps, flower pots on the rail
			for (int u : new int[] {2, W - 3}) {
				if (free(u, 4, 0) && !isPost[u]) {
					put(u, 4, 0, TimberKit.lantern(true), CitadelCanvas.HANGING);
				}
				if (free(u, 2, 0) && !isPost[u] && !(u >= bayLo && u <= bayHi)) {
					set(u, 2, 0, TimberKit.planter(p, u, 2, 0));
				}
			}
		}

		// -------------------------------------------------------------- chimney and hearth

		void chimney() {
			int top = ridgeY + 1;
			for (int v = cv0; v <= cv1; v++) {
				for (int u = W - 2; u <= W - 1; u++) {
					for (int y = 1; y <= top; y++) {
						set(u, y, v, pick(s.chimney(), u, y, v));
					}
					set(u, top + 1, v, TimberKit.slab(s.chimneySlab(), false));
				}
				set(W - 2, 1, v, b(Blocks.CAMPFIRE)); // the fireplace opens on the room
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
				for (int y = 1; y < ROOF + Math.min(u, W - 1 - u); y++) {
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
						int ry = ROOF + Math.min(u, W - 1 - u) - 1;
						if (free(u, ry, v)) {
							set(u, ry, v, beam(Direction.Axis.X));
						}
					}
				}
			}
			for (int y = 6; y < ridgeY; y++) {
				put(cu, y, cvm, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y), CitadelCanvas.HANGING);
			}
			put(cu, 5, cvm, TimberKit.lantern(true), CitadelCanvas.HANGING);
		}

		// -------------------------------------------------------------- the rest of a bigger plot

		void yard() {
			for (int pu = 0; pu < p.w(); pu++) {
				for (int v = 0; v < p.d(); v++) {
					boolean house = pu >= ox && pu < ox + W && v < D;
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
