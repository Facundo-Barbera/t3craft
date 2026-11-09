package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.door;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.gable;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.gableY;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.gapWin;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.log;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.mix;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.pane;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.slab;
import static dev.t3craft.hq.citadel.catalog.house.TimberKit.stair;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.IntPredicate;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A long merchant's house-and-shop. The whole front is a deep <b>arcade</b> (a covered market walk, four
 * blocks deep and five high) of stripped-cherry piers with a dark capital and timber arches, under a dark
 * oak eave beam. Behind it the shop, a store room and a kitchen with a switchback stair; over it a
 * plastered upper floor with the family rooms (living room, study) and, over the entrance bay, a cross
 * gable that shelters a balcony.
 *
 * <pre>
 *  section through the entrance bay (front on the left)
 *   y12+  roof ─────────╲╲╲╲╲╲        cross gable face on the front plane, balcony under it
 *   y7-10 upper floor: balcony v1-3, rooms from v2 (4 clear at the walls, vaulted in the middle)
 *   y6    dark eave beam, floor of the upper storey, ceiling of the market walk
 *   y1-5  ▐ arch ▌ market walk v1-4 | shop v6.. (5 clear)
 *   y0    steps, flagstones
 *     v0    v1..4      v5  v6 .. vb
 * </pre>
 *
 * Plan along the front (unflipped; the seed may mirror it): store room (wide plots only), shop with the
 * entrance and the counter, and the back house (kitchen and larder with a two-flight stair, landing and a
 * floor opening with rails). Upstairs: back house = dining room over the stair, then study and living
 * room. The seed picks the roof (dark tile, spruce or mangrove), the mirror, the number of arches (wide
 * plots) and which bay carries the cross gable. Palette: stripped cherry, white plaster, dark oak,
 * deepslate and tuff, and the roof. Size: 16-24 x 14-15 x 22+.
 */
public final class HouseTimberArcade implements Variant {
	private static final int FL = 6; // floor of the upper storey (one stands at FL + 1)
	private static final int PLATE = 11; // top plate of the walls; the roof starts here
	private static final int CB = PLATE + 2; // base of the cross gable roof
	private static final int VF = 6; // first interior row of the ground floor

	@Override
	public String id() {
		return "house_timber_arcade";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 16;
	}

	@Override
	public int minD() {
		return 14;
	}

	@Override
	public int minH() {
		return 22;
	}

	@Override
	public int maxW() {
		return 24;
	}

	@Override
	public int maxD() {
		return 15;
	}

	/** [palette] the roof by seed: dark tile, spruce or mangrove, always under a dark oak eave. */
	private record Roof(Block stairs, Block eave, Block ridge, Block slab, Block alt) {
	}

	private static final Roof[] ROOFS = {
			new Roof(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_BRICK_STAIRS),
			new Roof(Blocks.SPRUCE_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, Blocks.DARK_OAK_STAIRS),
			new Roof(Blocks.MANGROVE_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_SLAB, null),
	};

	@Override
	public void build(CitadelCanvas c, Plot p) {
		new Arcade(c, p).build();
	}

	private static final class Arcade {
		final CitadelCanvas c;
		final Plot p;
		final Roof roof;
		final boolean flip;
		final int w;
		final int d;
		final int vb; // back wall row
		final int vL; // last interior row
		final int bays;
		final int[] pier;
		final boolean hasStore;
		int a0;
		int a1; // store room columns
		int s0;
		int s1; // shop columns
		int k0;
		int k1; // back house columns
		int p1; // partition between shop and back house
		int p2; // partition between store and shop (-1 without store)
		int ap; // the stair's approach column (next to p1)
		int sg; // direction of the stair run away from p1 (+1 east, -1 west)
		int eb; // entrance bay
		int pa;
		int pb; // piers of the entrance bay
		int cd; // door column of the shop and the balcony
		int bd; // back door column of the shop
		int qSplit; // study/living partition, -1 when the rest is one room

		Arcade(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			Random r = p.rng(0xA4CADE5L);
			roof = ROOFS[r.nextInt(ROOFS.length)];
			flip = r.nextBoolean();
			w = p.w();
			d = Math.min(p.d(), 15);
			vb = d - 2;
			vL = vb - 1;
			int base = Math.max(2, (w - 3) / 5);
			int nb = base;
			if (base > 2 && (w - 3) / (base - 1) <= 7 && r.nextBoolean()) {
				nb = base - 1;
			}
			bays = nb;
			pier = new int[bays + 1];
			for (int k = 0; k <= bays; k++) {
				pier[k] = 1 + (k * (w - 3) + bays / 2) / bays;
			}
			int wi = w - 4;
			int sw = wi >= 20 ? 5 : 0;
			hasStore = sw > 0;
			int shopW = wi - 7 - (sw > 0 ? sw + 1 : 0);
			int ua0 = 2;
			int ua1 = 1 + sw;
			int up2 = sw > 0 ? ua1 + 1 : -1;
			int us0 = sw > 0 ? up2 + 1 : 2;
			int us1 = us0 + shopW - 1;
			int up1 = us1 + 1;
			int uk0 = up1 + 1;
			int uk1 = w - 3;
			if (!flip) {
				a0 = ua0;
				a1 = ua1;
				s0 = us0;
				s1 = us1;
				k0 = uk0;
				k1 = uk1;
				p1 = up1;
				p2 = up2;
				ap = k0;
				sg = 1;
			} else {
				a0 = w - 1 - ua1;
				a1 = w - 1 - ua0;
				s0 = w - 1 - us1;
				s1 = w - 1 - us0;
				k0 = w - 1 - uk1;
				k1 = w - 1 - uk0;
				p1 = w - 1 - up1;
				p2 = sw > 0 ? w - 1 - up2 : -1;
				ap = k1;
				sg = -1;
			}
			// the entrance bay: a bay whose middle three columns fit in the shop (the seed picks among them)
			int[] cand = new int[bays];
			int n = 0;
			for (int k = 0; k < bays; k++) {
				int lo = Math.max(s0 + 1, pier[k] + 2);
				int hi = Math.min(s1 - 1, pier[k + 1] - 2);
				if (lo <= hi) {
					cand[n++] = k;
				}
			}
			int shopMid = (s0 + s1) / 2;
			if (n > 0) {
				eb = cand[r.nextInt(n)];
			} else {
				eb = 0;
				int best = Integer.MAX_VALUE;
				for (int k = 0; k < bays; k++) {
					int dist = Math.abs((pier[k] + pier[k + 1]) / 2 - shopMid);
					if (dist < best) {
						best = dist;
						eb = k;
					}
				}
			}
			pa = pier[eb];
			pb = pier[eb + 1];
			int lo = Math.max(s0 + 1, pa + 2);
			int hi = Math.min(s1 - 1, pb - 2);
			int mid = (pa + pb) / 2;
			if (lo <= hi) {
				cd = Math.max(lo, Math.min(hi, mid));
			} else {
				cd = Math.max(s0 + 1, Math.min(s1 - 1, mid));
			}
			// the back door of the shop, never on a pier column
			int t = shopMid;
			if (isPier(t)) {
				t = t + 1 <= s1 - 1 ? t + 1 : t - 1;
			}
			bd = t;
			// study and living room upstairs: split only when the rest is wide, and never on the balcony door
			int restLo = flip ? p1 + 1 : 2;
			int restHi = flip ? w - 3 : p1 - 1;
			int q = -1;
			if (restHi - restLo + 1 >= 11) {
				q = flip ? p1 + 6 : p1 - 6;
				if (Math.abs(q - cd) <= 1) {
					q = -1;
				}
			}
			qSplit = q;
		}

		void build() {
			footing();
			floors();
			walls();
			plates();
			gableEnds();
			rakePosts();
			roofs();
			loggia();
			arcade();
			ledge();
			openings();
			partitions();
			flights();
			market();
			shop();
			backHouse();
			store();
			upper();
			chimney();
		}

		// ------------------------------------------------------------------ small helpers

		boolean isPier(int u) {
			for (int k : pier) {
				if (k == u) {
					return true;
				}
			}
			return false;
		}

		int col(int i) {
			return ap + sg * i;
		}

		int ridgeY(int v) {
			return PLATE + Math.min(v, d - 1 - v);
		}

		void set(int u, int y, int v, BlockState s) {
			p.set(c, u, y, v, s);
		}

		boolean air(int u, int y, int v) {
			if (!p.has(u, y, v)) {
				return false;
			}
			BlockState s = p.at(c, u, y, v);
			return s == null || s.isAir();
		}

		/** Sets a furniture block only on a free cell. */
		void item(int u, int y, int v, BlockState s) {
			if (air(u, y, v)) {
				set(u, y, v, s);
			}
		}

		static BlockState face(Block bl, Direction dir) {
			return b(bl).setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
		}

		static BlockState barrel() {
			return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
		}

		BlockState plaster(int u, int y, int v) {
			return mix(p, u, y, v, CitadelCanvas.terracotta(DyeColor.WHITE), CitadelCanvas.terracotta(DyeColor.WHITE), b(Blocks.CALCITE),
					CitadelCanvas.terracotta(DyeColor.WHITE));
		}

		BlockState baseStone(int u, int y, int v) {
			return mix(p, u, y, v, b(Blocks.DEEPSLATE_BRICKS), b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_TILES), b(Blocks.DEEPSLATE_BRICKS));
		}

		BlockState flag(int u, int v) {
			return mix(p, u, 0, v, b(Blocks.TUFF_BRICKS), b(Blocks.POLISHED_TUFF), b(Blocks.STONE_BRICKS), b(Blocks.TUFF_BRICKS));
		}

		BlockState plank(int u, int y, int v) {
			return mix(p, u, y, v, b(Blocks.SPRUCE_PLANKS), b(Blocks.SPRUCE_PLANKS), b(Blocks.SPRUCE_PLANKS), b(Blocks.DARK_OAK_PLANKS));
		}

		/** [palette] the light stripped cherry of the arcade. */
		static BlockState pale() {
			return log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y);
		}

		/** [palette] the timber frame of the walls: the same stripped cherry as the piers, so the pillars run unbroken to the roof; the beams stay dark oak. */
		static BlockState dark() {
			return log(Blocks.STRIPPED_CHERRY_LOG, Direction.Axis.Y);
		}

		/** A window cell: in every bay the cells at odd distance from the nearest pier, so slim windows alternate with plaster. */
		boolean winBay(int i) {
			if (isPier(i)) {
				return false;
			}
			int l = i;
			while (!isPier(l)) {
				l--;
			}
			int r = i;
			while (!isPier(r)) {
				r++;
			}
			int m = r - l - 1;
			int j = i - l - 1;
			return Math.min(j, m - 1 - j) % 2 == 1;
		}

		void hang(int u, int y, int v) {
			int ym = ridgeY(v);
			p.put(c, u, y, v, lantern(true), CitadelCanvas.HANGING);
			for (int yy = y + 1; yy < ym; yy++) {
				p.put(c, u, yy, v, Blocks.IRON_CHAIN.defaultBlockState(), CitadelCanvas.HANGING);
			}
		}

		/** Thickens the posts of a wall so there is no empty cell behind them (no pockets nobody can reach). */
		void backfill(boolean alongU, int fixed, int outward, int a, int bb, int y1, int y2, IntPredicate post, TimberKit.Cell infill) {
			int in = fixed - outward;
			for (int i = a; i <= bb; i++) {
				if (!post.test(i)) {
					continue;
				}
				for (int y = y1; y <= y2; y++) {
					TimberKit.put(c, p, alongU, in, i, y, infill.at(i, y));
				}
			}
		}

		// ------------------------------------------------------------------ footing and floors

		void footing() {
			for (int u = 1; u <= w - 2; u++) {
				for (int v = 1; v <= vb; v++) {
					boolean edge = u == 1 || u == w - 2 || v == 1 || v == vb;
					BlockState s;
					if (v <= 4) {
						s = flag(u, v); // the market walk is flagstone
					} else if (edge) {
						s = baseStone(u, 0, v);
					} else {
						s = plank(u, 0, v);
					}
					set(u, 0, v, s);
				}
			}
			// [depth] the footing step round the sides and the back
			for (int v = 1; v <= vb; v++) {
				set(0, 0, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.EAST, false));
				set(w - 1, 0, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.WEST, false));
			}
			for (int u = 1; u <= w - 2; u++) {
				set(u, 0, d - 1, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH, false));
			}
		}

		void floors() {
			for (int u = 1; u <= w - 2; u++) {
				for (int v = 1; v <= vb; v++) {
					boolean edgeV = v == 1 || v == vb;
					boolean edge = edgeV || u == 1 || u == w - 2;
					BlockState s;
					if (edge) {
						s = log(Blocks.DARK_OAK_LOG, edgeV ? Direction.Axis.X : Direction.Axis.Z);
					} else if (v <= 5 && isPier(u)) {
						s = log(Blocks.DARK_OAK_LOG, Direction.Axis.Z); // [depth] cross beams in the ceiling of the market walk
					} else {
						s = plank(u, FL, v);
					}
					set(u, FL, v, s);
				}
			}
			// [depth] the dark eave beam over the arches
			for (int u = 1; u <= w - 2; u++) {
				set(u, FL, 0, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			}
		}

		// ------------------------------------------------------------------ walls

		void walls() {
			IntPredicate postP = this::isPier;
			IntPredicate postS = i -> i == 1 || i == 5 || i == vb || ((i - 1) % 4 == 0 && vb - i >= 2);
			IntPredicate winFU = i -> (i < pa || i > pb) && winBay(i);
			IntPredicate winBU = this::winBay;
			IntPredicate winSU = i -> gapWin(postS, i, 1, vb);
			Block shut = Blocks.CHERRY_TRAPDOOR;
			int y1 = FL + 1;
			int y2 = PLATE - 1;
			int w1 = FL + 2;
			int w2 = FL + 3;
			// upper floor: dark frame, plaster, windows with shutters
			TimberKit.wall(c, p, true, 0, -1, 1, w - 2, y1, y2, postP, winFU, w1, w2, null, (i, y) -> dark(), null, (i, y) -> plaster(i, y, 1));
			backfill(true, 0, -1, 1, w - 2, y1, y2, postP, (i, y) -> plaster(i, y, 1));
			TimberKit.dress(c, p, true, 0, -1, 1, w - 2, postP, winFU, w1, w2, null, shut, false);
			TimberKit.wall(c, p, true, vb + 1, 1, 1, w - 2, y1, y2, postP, winBU, w1, w2, null, (i, y) -> dark(), null, (i, y) -> plaster(i, y, vb));
			backfill(true, vb + 1, 1, 1, w - 2, y1, y2, postP, (i, y) -> plaster(i, y, vb));
			TimberKit.dress(c, p, true, vb + 1, 1, 1, w - 2, postP, winBU, w1, w2, null, shut, false);
			TimberKit.wall(c, p, false, 0, -1, 1, vb, y1, y2, postS, winSU, w1, w2, null, (i, y) -> dark(), null, (i, y) -> plaster(1, y, i));
			backfill(false, 0, -1, 1, vb, y1, y2, postS, (i, y) -> plaster(1, y, i));
			TimberKit.dress(c, p, false, 0, -1, 1, vb, postS, winSU, w1, w2, null, shut, false);
			TimberKit.wall(c, p, false, w - 1, 1, 1, vb, y1, y2, postS, winSU, w1, w2, null, (i, y) -> dark(), null, (i, y) -> plaster(w - 2, y, i));
			backfill(false, w - 1, 1, 1, vb, y1, y2, postS, (i, y) -> plaster(w - 2, y, i));
			TimberKit.dress(c, p, false, w - 1, 1, 1, vb, postS, winSU, w1, w2, null, shut, false);
			// ground floor: stone foot, pilasters on the market side, display windows
			int g2 = FL - 1;
			IntPredicate winF0 = i -> Math.abs(i - cd) > 1 && winBay(i);
			TimberKit.wall(c, p, true, 4, -1, 1, w - 2, 1, g2, postP, winF0, 2, 3, null, (i, y) -> pale(),
					(i, y) -> y == 1 ? baseStone(i, 1, 4) : null, (i, y) -> plaster(i, y, 5));
			backfill(true, 4, -1, 1, w - 2, 1, g2, postP, (i, y) -> plaster(i, y, 5));
			TimberKit.dress(c, p, true, 4, -1, 1, w - 2, postP, winF0, 2, 3, null, shut, false);
			IntPredicate winSG = i -> i > 5 && gapWin(postS, i, 1, vb);
			TimberKit.wall(c, p, false, 0, -1, 1, vb, 1, g2, postS, winSG, 2, 3, null, (i, y) -> dark(),
					(i, y) -> y == 1 ? baseStone(0, 1, i) : null, (i, y) -> plaster(1, y, i));
			backfill(false, 0, -1, 1, vb, 1, g2, postS, (i, y) -> plaster(1, y, i));
			TimberKit.dress(c, p, false, 0, -1, 1, vb, postS, winSG, 2, 3, null, shut, false);
			TimberKit.wall(c, p, false, w - 1, 1, 1, vb, 1, g2, postS, winSG, 2, 3, null, (i, y) -> dark(),
					(i, y) -> y == 1 ? baseStone(w - 1, 1, i) : null, (i, y) -> plaster(w - 2, y, i));
			backfill(false, w - 1, 1, 1, vb, 1, g2, postS, (i, y) -> plaster(w - 2, y, i));
			TimberKit.dress(c, p, false, w - 1, 1, 1, vb, postS, winSG, 2, 3, null, shut, false);
			IntPredicate winBG = i -> Math.abs(i - bd) > 1 && winBay(i);
			TimberKit.wall(c, p, true, vb + 1, 1, 1, w - 2, 1, g2, postP, winBG, 2, 3, null, (i, y) -> dark(),
					(i, y) -> y == 1 ? baseStone(i, 1, vb + 1) : null, (i, y) -> plaster(i, y, vb));
			backfill(true, vb + 1, 1, 1, w - 2, 1, g2, postP, (i, y) -> plaster(i, y, vb));
			TimberKit.dress(c, p, true, vb + 1, 1, 1, w - 2, postP, winBG, 2, 3, null, shut, false);
		}

		/** The wall plates under the roof. */
		void plates() {
			for (int u = 1; u <= w - 2; u++) {
				set(u, PLATE, 1, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
				set(u, PLATE, vb, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			}
			for (int v = 2; v <= vb - 1; v++) {
				set(1, PLATE, v, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
				set(w - 2, PLATE, v, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
			}
		}

		/** [palette] the gable ends: plaster in the triangle, a king post and a pair of pink lights. */
		void gableEnds() {
			int m0 = (d - 1) / 2;
			int m1 = d / 2;
			for (int u : new int[] {1, w - 2}) {
				for (int v = 1; v <= vb; v++) {
					int top = gableY(v, 0, d - 1, PLATE);
					for (int y = PLATE + 1; y < top; y++) {
						BlockState st;
						if (v == m0 || v == m1) {
							st = dark();
						} else if (y == PLATE + 1 && (v == m0 - 1 || v == m1 + 1)) {
							st = pane(false, DyeColor.PINK);
						} else {
							st = plaster(u, y, v);
						}
						set(u, y, v, st);
					}
				}
			}
		}

		/** The side posts rise to the rake of the roof overhang, so their tops are not a ledge under it. */
		void rakePosts() {
			for (int v = 1; v <= vb; v++) {
				boolean post = v == 1 || v == 5 || v == vb || ((v - 1) % 4 == 0 && vb - v >= 2);
				if (!post) {
					continue;
				}
				for (int y = PLATE; y <= ridgeY(v) - 2; y++) {
					set(0, y, v, dark());
					set(w - 1, y, v, dark());
				}
			}
		}

		// ------------------------------------------------------------------ roof

		void roofs() {
			gable(c, p, true, 0, w - 1, 0, d - 1, PLATE, roof.stairs(), roof.eave(), roof.ridge(), roof.slab(), roof.alt(), true, true);
			// [finish] a deepslate post with a copper rod on both ridge ends
			int mid = (d - 1) / 2;
			int top = PLATE + mid + 2;
			for (int u : new int[] {0, w - 1}) {
				set(u, top, mid, b(Blocks.DEEPSLATE_TILE_WALL));
				set(u, top + 1, mid, Blocks.LIGHTNING_ROD.waxed().exposed().defaultBlockState());
			}
		}

		void chimney() {
			int u = flip ? 3 : w - 4;
			int v = d - 3;
			int top = Math.min(PLATE + (d - 1) / 2 + 2, p.h() - 2);
			for (int y = ridgeY(v); y < top; y++) {
				set(u, y, v, mix(p, u, y, v, b(Blocks.BRICKS), b(Blocks.BRICKS), b(Blocks.DEEPSLATE_BRICKS)));
			}
			set(u, top, v, slab(Blocks.DEEPSLATE_BRICK_SLAB, false));
		}

		// ------------------------------------------------------------------ the balcony under the cross gable

		/**
		 * [finish] Over the entrance bay the upper wall steps back to row 4 and a balcony (3 deep) sits on
		 * the arcade, behind a cherry rail; a cross gable roofs it, its face on the front plane over a
		 * tie beam, with a king post and pink lights. The main roof is cut away under it.
		 */
		void loggia() {
			int y1 = FL + 1;
			int y2 = PLATE - 1;
			for (int u = pa + 1; u <= pb - 1; u++) {
				for (int v = 1; v <= 3; v++) {
					for (int y = y1; y <= y2; y++) {
						set(u, y, v, CitadelCanvas.AIR);
					}
					set(u, FL, v, b(Blocks.CHERRY_PLANKS)); // the deck
				}
				set(u, PLATE, 1, CitadelCanvas.AIR); // no plate beam in the open front of the balcony
			}
			// side walls and the back wall of the balcony
			for (int v = 1; v <= 4; v++) {
				int top = Math.max(CB, ridgeY(v)) - 1;
				for (int y = y1; y <= top; y++) {
					set(pa, y, v, v == 4 ? dark() : plaster(pa, y, v));
					set(pb, y, v, v == 4 ? dark() : plaster(pb, y, v));
				}
			}
			for (int u = pa + 1; u <= pb - 1; u++) {
				int top = Math.max(CB + Math.min(u - pa, pb - u), ridgeY(4)) - 1;
				for (int y = y1; y <= top; y++) {
					set(u, y, 4, plaster(u, y, 4));
				}
				set(u, y1, 0, fence(Blocks.CHERRY_FENCE, false, true, false, true));
			}
			set(cd, y1, 4, door(Blocks.CHERRY_DOOR, Direction.NORTH, false));
			set(cd, y1 + 1, 4, door(Blocks.CHERRY_DOOR, Direction.NORTH, true));
			set(cd, y1 + 2, 4, pane(true, DyeColor.PINK));
			for (int u : new int[] {cd - 2, cd + 2}) {
				if (u > pa && u < pb) {
					set(u, y1 + 1, 4, pane(true, DyeColor.PINK));
					set(u, y1 + 2, 4, pane(true, DyeColor.PINK));
				}
			}
			p.put(c, pa + 1, y2, 0, lantern(true), CitadelCanvas.HANGING);
			p.put(c, pb - 1, y2, 0, lantern(true), CitadelCanvas.HANGING);
			// the cross roof
			int span = pb - pa;
			int dmax = span / 2;
			for (int u = pa; u <= pb; u++) {
				int dd = Math.min(u - pa, pb - u);
				int yc = CB + dd;
				boolean ridge = dd == dmax;
				boolean left = 2 * u < pa + pb;
				for (int v = 0; v <= (d - 1) / 2; v++) {
					int ym = ridgeY(v);
					if (yc <= ym) {
						continue;
					}
					if (u > pa && u < pb) {
						set(u, ym, v, CitadelCanvas.AIR); // cut the main roof away under the cross gable
					}
					if (ridge) {
						set(u, yc, v, b(roof.ridge()));
						set(u, yc + 1, v, slab(roof.slab(), false));
					} else {
						set(u, yc, v, stair(dd == 0 ? roof.eave() : roof.stairs(), left ? Direction.EAST : Direction.WEST, false));
					}
				}
				// the face of the gable on the front plane
				for (int y = PLATE + 1; y < yc; y++) {
					BlockState st;
					if (dd == 0 || ridge) {
						st = dark();
					} else if (dd == dmax - 1 && dmax >= 2 && y == PLATE + 1) {
						st = pane(true, DyeColor.PINK);
					} else {
						st = plaster(u, y, 0);
					}
					set(u, y, 0, st);
				}
				set(u, PLATE, 0, log(Blocks.DARK_OAK_LOG, Direction.Axis.X)); // the tie beam
			}
		}

		// ------------------------------------------------------------------ the arcade

		/**
		 * [depth] The arcade: stripped cherry piers on a deepslate foot with a dark oak capital, and in every
		 * bay a timber arch of upside-down stair corbels and top slabs under a plank spandrel. The arch
		 * never comes lower than four blocks, so a person under it keeps their headroom.
		 */
		void arcade() {
			for (int k = 0; k <= bays; k++) {
				int u = pier[k];
				set(u, 0, 0, b(Blocks.POLISHED_DEEPSLATE));
				set(u, 1, 0, b(Blocks.POLISHED_DEEPSLATE));
				for (int y = 2; y <= 4; y++) {
					set(u, y, 0, pale());
				}
				set(u, 5, 0, log(Blocks.DARK_OAK_LOG, Direction.Axis.X)); // [finish] capital
				if (k + 1 >= pier.length) {
					continue;
				}
				int a = u + 1;
				int bb = pier[k + 1] - 1;
				int m = bb - a + 1;
				for (int x = a; x <= bb; x++) {
					int e = Math.min(x - a, bb - x);
					set(x, 5, 0, b(Blocks.CHERRY_PLANKS));
					if (e == 0) {
						set(x, 4, 0, stair(Blocks.CHERRY_STAIRS, x == a ? Direction.WEST : Direction.EAST, true));
					} else if (e == 1 && m >= 5) {
						set(x, 4, 0, slab(Blocks.CHERRY_SLAB, true));
					}
				}
			}
			// the steps between the piers
			for (int u = 1; u <= w - 2; u++) {
				if (!isPier(u)) {
					set(u, 0, 0, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
				}
			}
		}

		/** [depth] A dark soffit under the eave (upside-down stairs) and a fence rail in front of the upper windows. */
		void ledge() {
			for (int u = 1; u <= w - 2; u++) {
				if (isPier(u) || (u >= pa && u <= pb)) {
					continue;
				}
				set(u, PLATE - 1, 0, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, true));
				set(u, FL + 1, 0, fence(Blocks.CHERRY_FENCE, false, true, false, true)); // a window rail, not a ledge
			}
		}

		/** The market walk: stalls with goods against the shop front, barrels and crates, lanterns hanging. */
		void market() {
			for (int k = 0; k < bays; k++) {
				int a = pier[k] + 1;
				int bb = pier[k + 1] - 1;
				int mid = (a + bb) / 2;
				p.put(c, mid, 5, 2, lantern(true), CitadelCanvas.HANGING);
				for (int x = a; x <= bb; x++) {
					if (k == eb && Math.abs(x - cd) <= 1) {
						continue;
					}
					if (k % 2 == 0 && k != eb) {
						// a stall: the counter is the goods (planks, melons, pumpkins, hay, a barrel)
						BlockState goods = switch (p.hash(x, 1, 4, 6)) {
							case 0 -> b(Blocks.MELON);
							case 1 -> b(Blocks.PUMPKIN);
							case 2 -> b(Blocks.HAY_BLOCK);
							case 3 -> barrel();
							default -> b(Blocks.DARK_OAK_PLANKS);
						};
						set(x, 1, 4, goods);
					} else {
						// barrels, crates and chests
						BlockState st = switch (p.hash(x, 1, 4, 3)) {
							case 0 -> barrel();
							case 1 -> face(Blocks.CHEST, Direction.NORTH);
							default -> barrel();
						};
						set(x, 1, 4, st);
					}
				}
			}
		}

		/** The shop door, the back door and the seats at the foot of the shop front. */
		void openings() {
			for (int u = cd - 1; u <= cd + 1; u++) {
				for (int y = 1; y <= 3; y++) {
					set(u, y, 5, CitadelCanvas.AIR);
					set(u, y, 4, CitadelCanvas.AIR);
				}
				set(u, 4, 5, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
			}
			set(bd, 1, vb, door(Blocks.CHERRY_DOOR, Direction.SOUTH, false));
			set(bd, 2, vb, door(Blocks.CHERRY_DOOR, Direction.SOUTH, true));
			set(bd, 3, vb, pane(true, DyeColor.YELLOW));
			for (int y = 1; y <= 3; y++) {
				set(bd, y, vb + 1, CitadelCanvas.AIR);
			}
		}

		// ------------------------------------------------------------------ partitions

		void partitions() {
			partG(p1);
			if (hasStore) {
				partG(p2);
			}
			partU(p1);
			if (qSplit >= 0) {
				partU(qSplit);
			}
		}

		/** A ground floor partition with a two-wide doorway at rows VF+1 and VF+2. */
		void partG(int u) {
			int vd = VF + 1;
			for (int v = VF; v <= vL; v++) {
				boolean doorway = v == vd || v == vd + 1;
				boolean postRow = v == VF || v == vL || v == vd + 2;
				for (int y = 1; y <= FL - 1; y++) {
					BlockState s;
					if (doorway && y <= 3) {
						s = CitadelCanvas.AIR;
					} else if (doorway && y == 4) {
						s = log(Blocks.DARK_OAK_LOG, Direction.Axis.Z);
					} else if (postRow) {
						s = dark();
					} else if (y == 1) {
						s = b(Blocks.TUFF_BRICKS);
					} else {
						s = plaster(u, y, v);
					}
					set(u, y, v, s);
				}
			}
		}

		/** An upper floor partition under the open roof: dark frame and plaster up to the plate, a tie beam above. */
		void partU(int u) {
			int vs = (u >= pa && u <= pb) ? 4 : 2;
			int vd = (vL + 1) / 2;
			for (int v = vs; v <= vL; v++) {
				boolean doorway = v == vd || v == vd + 1;
				boolean postRow = v == vs || v == vL || v == vd - 1 || v == vd + 2;
				for (int y = FL + 1; y < ridgeY(v); y++) { // up to the roof, so nothing can be stood on top
					BlockState s;
					if (doorway && y <= FL + 3) {
						s = CitadelCanvas.AIR;
					} else if (doorway && y == FL + 4) {
						s = log(Blocks.DARK_OAK_LOG, Direction.Axis.Z);
					} else if (postRow || y == PLATE) {
						s = y == PLATE ? log(Blocks.DARK_OAK_LOG, Direction.Axis.Z) : dark();
					} else {
						s = plaster(u, y, v);
					}
					set(u, y, v, s);
				}
			}
		}

		// ------------------------------------------------------------------ the stair

		/**
		 * A two-flight stair on the back wall of the back house: three steps up away from the partition, a
		 * landing two blocks wide, three steps back. The first step is met head on from the approach cell,
		 * every step has the air of the floor opening over it, and the upper floor is left through the cell
		 * next to the partition. Rails round the opening upstairs.
		 */
		void flights() {
			int r1 = vL - 1;
			int r2 = vL;
			BlockState core = b(Blocks.SPRUCE_PLANKS);
			Direction up = sg > 0 ? Direction.EAST : Direction.WEST;
			Direction back = sg > 0 ? Direction.WEST : Direction.EAST;
			int c1 = col(1);
			int c2 = col(2);
			int c3 = col(3);
			int c4 = col(4);
			set(c1, 1, r1, stair(Blocks.SPRUCE_STAIRS, up, false));
			set(c2, 1, r1, core);
			set(c2, 2, r1, stair(Blocks.SPRUCE_STAIRS, up, false));
			set(c3, 1, r1, core);
			set(c3, 2, r1, core);
			set(c3, 3, r1, stair(Blocks.SPRUCE_STAIRS, up, false));
			for (int y = 1; y <= 3; y++) {
				set(c4, y, r1, core);
				set(c4, y, r2, core);
				set(c3, y, r2, core);
			}
			set(c3, 4, r2, stair(Blocks.SPRUCE_STAIRS, back, false));
			for (int y = 1; y <= 4; y++) {
				set(c2, y, r2, core);
			}
			set(c2, 5, r2, stair(Blocks.SPRUCE_STAIRS, back, false));
			for (int y = 1; y <= 5; y++) {
				set(c1, y, r2, core);
			}
			set(c1, FL, r2, stair(Blocks.SPRUCE_STAIRS, back, false)); // the top step takes the floor's own layer
			// the opening in the floor over the flights and the landing
			for (int i = 1; i <= 4; i++) {
				set(col(i), FL, r1, CitadelCanvas.AIR);
			}
			set(c4, FL, r2, CitadelCanvas.AIR);
			set(c3, FL, r2, CitadelCanvas.AIR);
			set(c2, FL, r2, CitadelCanvas.AIR);
			// the rail round it upstairs, open at the cells next to the partition (where one climbs out)
			Set<Integer> rail = new HashSet<>();
			for (int i = 1; i <= 4; i++) {
				rail.add(key(col(i), r1 - 1));
			}
			rail.add(key(c1, r1));
			rail.add(key(col(5), r1));
			rail.add(key(col(5), r2));
			for (int k : rail) {
				int u = k >> 8;
				int v = k & 255;
				set(u, FL + 1, v, fence(Blocks.DARK_OAK_FENCE, rail.contains(key(u, v - 1)), rail.contains(key(u + 1, v)),
						rail.contains(key(u, v + 1)), rail.contains(key(u - 1, v))));
			}
		}

		static int key(int u, int v) {
			return u << 8 | v;
		}

		// ------------------------------------------------------------------ ground floor rooms

		/** The shop: shelves on the back wall, a counter with a way round it, stock by the door, lanterns. */
		void shop() {
			for (int u = s0; u <= s1; u++) {
				if (Math.abs(u - bd) <= 1) {
					continue;
				}
				for (int y = 1; y <= FL - 1; y++) { // floor to ceiling: no ledge on top
					set(u, y, vL, y % 2 == 0 && p.hash(u, y, 0, 3) == 0 ? face(Blocks.CHISELED_BOOKSHELF, Direction.NORTH) : b(Blocks.BOOKSHELF));
				}
			}
			int cv = vL - 2;
			for (int u = s0 + 1; u <= s1 - 1; u++) {
				// the counter: plain boards with a chest or a barrel of stock here and there (nothing on top)
				int h = p.hash(u, 1, cv, 5);
				set(u, 1, cv, h == 0 ? barrel() : h == 1 ? face(Blocks.CHEST, Direction.SOUTH) : b(Blocks.DARK_OAK_PLANKS));
			}
			// stock by the front wall, clear of the door
			for (int u : new int[] {s0 + 1, s1 - 1}) {
				if (Math.abs(u - cd) > 1) {
					item(u, 1, VF, p.hash(u, 1, VF, 2) == 0 ? barrel() : face(Blocks.CHEST, Direction.SOUTH));
				}
			}
			p.put(c, cd, 5, 8, lantern(true), CitadelCanvas.HANGING);
			p.put(c, bd, 5, vL - 1, lantern(true), CitadelCanvas.HANGING);
		}

		/** The kitchen and larder of the back house, in the rows in front of the stair. */
		void backHouse() {
			int fc = col(5);
			Direction in = sg > 0 ? Direction.WEST : Direction.EAST;
			for (int v = VF; v <= vL - 3; v++) {
				BlockState s = switch ((v - VF) % 4) {
					case 0 -> face(Blocks.SMOKER, in);
					case 1 -> face(Blocks.FURNACE, in).setValue(BlockStateProperties.LIT, true);
					case 2 -> b(Blocks.CRAFTING_TABLE);
					default -> b(Blocks.WATER_CAULDRON);
				};
				item(fc, 1, v, s);
			}
			item(fc, 1, vL - 1, barrel());
			item(fc, 1, vL, face(Blocks.CHEST, in));
			if (vL >= 12) {
				for (int i = 1; i <= 4; i++) {
					item(col(i), 1, VF, i % 2 == 0 ? barrel() : face(Blocks.CHEST, Direction.SOUTH));
				}
			}
			p.put(c, col(2), 5, VF + 2, lantern(true), CitadelCanvas.HANGING);
		}

		/** The store room (wide plots): chests and barrels along the outer wall and the back wall. */
		void store() {
			if (!hasStore) {
				return;
			}
			int fc = flip ? a1 : a0;
			Direction in = flip ? Direction.WEST : Direction.EAST;
			for (int v = VF; v <= vL; v++) {
				if (v % 2 == 0) {
					item(fc, 1, v, face(Blocks.CHEST, in));
				} else {
					item(fc, 1, v, barrel());
				}
			}
			for (int u = a0; u <= a1; u++) {
				if (u == fc) {
					continue;
				}
				item(u, 1, vL, p.hash(u, 1, vL, 2) == 0 ? barrel() : face(Blocks.CHEST, Direction.NORTH));
			}
			p.put(c, (a0 + a1) / 2, 5, VF + 3, lantern(true), CitadelCanvas.HANGING);
		}

		// ------------------------------------------------------------------ upper floor rooms

		void upper() {
			int y = FL + 1;
			// the dining room over the stair and larder shelves on the far wall
			int fc = col(5);
			Direction in = sg > 0 ? Direction.WEST : Direction.EAST;
			for (int v = 2; v <= vL - 3; v++) {
				BlockState s = switch ((v - 2) % 4) {
					case 0 -> face(Blocks.CHEST, in);
					case 1 -> barrel();
					case 2 -> face(Blocks.LOOM, in);
					default -> face(Blocks.CHEST, in);
				};
				item(fc, y, v, s);
			}
			int t1 = col(2);
			int t2 = col(3);
			item(t1, y, 6, b(Blocks.DARK_OAK_PLANKS));
			item(t2, y, 6, b(Blocks.DARK_OAK_PLANKS));
			item(t1, y + 1, 6, lantern(false));
			for (int tu : new int[] {t1, t2}) {
				item(tu, y, 5, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
				item(tu, y, 7, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
			}
			hang(col(1), 10, 3);
			// the rest of the floor: study and living room
			int restLo = flip ? p1 + 1 : 2;
			int restHi = flip ? w - 3 : p1 - 1;
			if (qSplit >= 0) {
				if (!flip) {
					living(restLo, qSplit - 1, false);
					study(qSplit + 1, p1 - 1);
				} else {
					living(qSplit + 1, restHi, false);
					study(p1 + 1, qSplit - 1);
				}
			} else {
				living(restLo, restHi, true);
			}
		}

		/** Bedroom with two beds on the back wall; in a small plot the study corner is on the outer wall too. */
		void living(int l0, int l1, boolean combined) {
			int y = FL + 1;
			BlockState bed = b(Blocks.BED.pick(DyeColor.RED)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
			int[] bu = l1 - l0 >= 4 ? new int[] {l0 + 1, l1 - 1} : new int[] {l0 + 1};
			for (int u : bu) {
				item(u, y, vL, bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
				item(u, y, vL - 1, bed.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
			}
			if (bu.length == 2 && bu[1] - bu[0] >= 2) {
				item((bu[0] + bu[1]) / 2, y, vL, face(Blocks.CHEST, Direction.NORTH));
			}
			int mx = (l0 + l1) / 2;
			if (combined) {
				int far = flip ? l1 : l0;
				Direction in = flip ? Direction.WEST : Direction.EAST;
				int chair = far + (flip ? -1 : 1);
				item(far, y, 5, face(Blocks.LECTERN, in).setValue(BlockStateProperties.HAS_BOOK, true));
				for (int v = 6; v <= 8; v++) {
					item(far, y, v, b(Blocks.DARK_OAK_PLANKS));
				}
				item(far, y + 1, 7, lantern(false));
				item(chair, y, 7, stair(Blocks.SPRUCE_STAIRS, in, false));
				item(far, y, 9, b(Blocks.CARTOGRAPHY_TABLE));
				hang(mx, 10, 6);
			} else {
				item(mx, y, 7, b(Blocks.DARK_OAK_PLANKS));
				item(mx + 1, y, 7, b(Blocks.DARK_OAK_PLANKS));
				item(mx, y, 6, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
				item(mx + 1, y, 6, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
				item(mx, y, 8, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
				item(mx + 1, y, 8, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
				item(mx, y + 1, 7, lantern(false));
				hang(mx, 10, 5);
			}
		}

		/** The study: shelves on the back wall, a desk with a chair, a lectern and a cartography table. */
		void study(int s0c, int s1c) {
			int y = FL + 1;
			for (int u = s0c; u <= s1c; u++) {
				for (int yy = y; yy < ridgeY(vL); yy++) { // up to the roof: no ledge on top
					item(u, yy, vL, (yy - y) % 2 == 1 && p.hash(u, yy, vL, 3) == 0 ? face(Blocks.CHISELED_BOOKSHELF, Direction.NORTH) : b(Blocks.BOOKSHELF));
				}
			}
			int mx = (s0c + s1c) / 2;
			for (int u = mx - 1; u <= mx + 1; u++) {
				item(u, y, 7, b(Blocks.DARK_OAK_PLANKS));
			}
			item(mx, y + 1, 7, lantern(false));
			item(mx, y, 6, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
			int cv = vL - 2;
			item(s0c, y, cv, face(Blocks.LECTERN, Direction.NORTH).setValue(BlockStateProperties.HAS_BOOK, true));
			item(s1c, y, cv, b(Blocks.CARTOGRAPHY_TABLE));
			hang(mx, 10, cv);
		}
	}
}
