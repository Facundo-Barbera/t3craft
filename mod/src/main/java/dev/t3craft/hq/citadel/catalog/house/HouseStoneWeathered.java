package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.invisibleLight;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import dev.t3craft.hq.citadel.catalog.house.StoneKit.Wall;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * An old stone house of a craftsman or a farmer, lived in. Two storeys with 4 free blocks each under a gabled roof
 * (slate or weathered boards, by seed), a tall stone portal with a recessed door, an outside chimney on the gable end,
 * and on lots 15+ wide (two seeds in three) an L: a one-storey forge lean-to with its own chimney.
 *
 * <pre>
 *  ground floor (kitchen side mirrored by seed)           upper floor
 *  v+  ┌──────────────────────────┐                       ┌──────────────────────────┐
 *      │ hearth  table   aisle  W │ W: workshop           │ chests   rug      beds   │
 *      │ cook    seats   (3)    s │ (crafting, smithing,  │ (store)           (sleep)│
 *      │ [land][5 steps ...][exit]│  grindstone or loom)  │ rail ─────  hole  exit ◄─┤
 *      └──────────────────────────┘                       └──────────────────────────┘
 * </pre>
 *
 * The stair is made by hand along the back wall: a landing, five steps (head-on first step, the hole above them
 * fenced) and an exit on the upper floor. The lot gets a vegetable bed, a woodpile and a trough, a flagstone path
 * and a dressing of moss, ferns and flowers. The walls are one controlled gradient (mossy cobble plinth, mossy and
 * cracked bricks low, clean bricks above); the amount of moss, the roof, the chimney side, the work stations and
 * the plan come from the seed. Nothing a person could stand on is left without a way up (walkability audit): no
 * hoods or sills above head height, no unreachable tops under the eaves, the roof is open to the upper room.
 *
 * <p>Size: 11–17 × 10–17 × 18+ (the building stops at 14 + the forge and sits at the front of a bigger lot).
 */
public final class HouseStoneWeathered implements Variant {
	private static final int P = 10; // plate row: top of the walls
	private static final int F2 = 5; // upper floor layer
	private static final int PA = 5; // wall top of the lean-to forge

	@Override
	public String id() {
		return "house_stone_weathered";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 11;
	}

	@Override
	public int minD() {
		return 10;
	}

	@Override
	public int minH() {
		return 18;
	}

	@Override
	public int maxW() {
		return 17;
	}

	@Override
	public int maxD() {
		return 17;
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		new Bld(c, p).build();
	}

	private static final class Bld {
		final CitadelCanvas c;
		final Plot p;
		final int w;
		final int d;
		final boolean ell;
		final boolean annexLeft;
		final boolean kRight;
		final int ai; // forge interior width
		final int mw; // main block outer width
		final int mu0; // main wall columns
		final int mu1;
		final int ao; // forge outer wall column
		final int va; // front wall row
		final int vb; // back wall row
		final int lmax;
		final int du; // door column
		final int moss;
		final int roofKind;
		final int setK;
		final int yardK;
		final int iu0;
		final int iu1;
		final int iv0;
		final int iv1;
		final int iw;
		final Direction inK; // from the kitchen wall into the room
		final Direction inW;
		Wall front;
		Wall back;
		Wall wLeft;
		Wall wRight;
		Wall outer;

		Bld(CitadelCanvas c, Plot p) {
			this.c = c;
			this.p = p;
			w = p.w();
			d = p.d();
			ell = w >= 15 && p.hash(3, 3, 3, 3) != 0;
			annexLeft = ell && p.hash(1, 2, 3, 2) == 0;
			ai = w >= 17 ? 4 : 3;
			int annexSpan = ell ? ai + 1 : 0;
			mw = Math.min(w - 2 - annexSpan, 14);
			int span = mw + annexSpan;
			int left = 1 + (w - 2 - span) / 2;
			mu0 = ell && annexLeft ? left + ai + 1 : left;
			mu1 = mu0 + mw - 1;
			ao = !ell ? -1 : annexLeft ? left : mu1 + ai + 1;
			kRight = ell ? annexLeft : p.hash(2, 2, 2, 2) == 0;
			vb = d - 2;
			int db = Math.min(10, vb);
			va = vb - db + 1;
			lmax = (db + 1) / 2;
			du = (mu0 + mu1) / 2;
			moss = p.hash(7, 7, 7, 3);
			roofKind = p.hash(5, 5, 5, 2);
			setK = p.hash(4, 4, 4, 3);
			yardK = p.hash(6, 6, 6, 2);
			iu0 = mu0 + 1;
			iu1 = mu1 - 1;
			iv0 = va + 1;
			iv1 = vb - 1;
			iw = iu1 - iu0 + 1;
			inK = kRight ? Direction.WEST : Direction.EAST;
			inW = inK.getOpposite();
		}

		// -------------------------------------------------------------- helpers

		/** Interior column {@code i} cells from the kitchen wall. */
		int ik(int i) {
			return kRight ? iu1 - i : iu0 + i;
		}

		/** Interior column {@code i} cells from the workshop wall. */
		int iwk(int i) {
			return kRight ? iu0 + i : iu1 - i;
		}

		int roofL(int v) {
			return Math.min(v - (va - 1), (vb + 1) - v);
		}

		void set(int u, int y, int v, BlockState s) {
			p.set(c, u, y, v, s);
		}

		boolean free(int u, int y, int v) {
			return EaveKit.free(c, p, u, y, v);
		}

		static BlockState fac(Block blk, Direction dir) {
			BlockState s = blk.defaultBlockState();
			if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
				s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
			}
			if (s.hasProperty(BlockStateProperties.ATTACH_FACE)) {
				s = s.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
			}
			return s;
		}

		static BlockState lit(Block blk, Direction dir) {
			return fac(blk, dir).setValue(BlockStateProperties.LIT, true);
		}

		static BlockState bed(DyeColor col, BedPart part, Direction toHead) {
			return b(Blocks.BED.pick(col)).setValue(BlockStateProperties.HORIZONTAL_FACING, toHead).setValue(BlockStateProperties.BED_PART, part);
		}

		static BlockState logAxis(Block log, Direction.Axis axis) {
			return StoneKit.log(log, axis);
		}

		static BlockState vine(Direction face) {
			var v = Blocks.VINE.defaultBlockState();
			return switch (face) {
				case NORTH -> v.setValue(BlockStateProperties.NORTH, true);
				case EAST -> v.setValue(BlockStateProperties.EAST, true);
				case SOUTH -> v.setValue(BlockStateProperties.SOUTH, true);
				default -> v.setValue(BlockStateProperties.WEST, true);
			};
		}

		// -------------------------------------------------------------- build

		void build() {
			front = new Wall(c, p, va, Direction.NORTH);
			back = new Wall(c, p, vb, Direction.SOUTH);
			wLeft = new Wall(c, p, mu0, Direction.WEST);
			wRight = new Wall(c, p, mu1, Direction.EAST);
			walls();
			if (ell) {
				annexWalls();
			}
			floors();
			vines();
			windows();
			portal();
			roofMain();
			if (ell) {
				roofAnnex();
			}
			gables();
			chimneys();
			eaves();
			stair();
			groundFloor();
			upperFloor();
			if (ell) {
				forge();
			}
			yard();
		}

		// -------------------------------------------------------------- walls

		/** [palette] one gradient: mossy cobble plinth, mossy and cracked bricks low, clean bricks above. */
		BlockState mat(int u, int y, int v) {
			int h = p.hash(u, y, v, 20);
			return switch (y) {
				case 0 -> h < 6 + 4 * moss ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.COBBLESTONE);
				case 1 -> h < 2 + 3 * moss ? b(Blocks.MOSSY_STONE_BRICKS) : h < 6 + 3 * moss ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
				case 2 -> h < 2 * moss ? b(Blocks.MOSSY_STONE_BRICKS) : h < 2 * moss + 3 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
				case 3, 4 -> h < moss ? b(Blocks.MOSSY_STONE_BRICKS) : h < moss + 2 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
				case 6 -> h < moss ? b(Blocks.MOSSY_STONE_BRICKS) : h < moss + 3 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
				default -> h < 3 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
			};
		}

		/** One column of a wall up to {@code top}; the floor joists and the plate show as dark oak bands. */
		void wallCol(Wall wl, int t, int top) {
			Direction.Axis ax = wl.alongU() ? Direction.Axis.X : Direction.Axis.Z;
			for (int y = 0; y <= top; y++) {
				if (y == F2 || y == P) {
					wl.set(t, y, 0, logAxis(Blocks.STRIPPED_DARK_OAK_LOG, ax));
				} else {
					wl.set(t, y, 0, mat(wl.u(t, 0), y, wl.v(t, 0)));
				}
			}
		}

		/** [depth] reinforced corner: rough cobble alternating with polished stone, mossy at the foot. */
		void quoin(int u, int v, int top) {
			for (int y = 0; y <= top; y++) {
				BlockState s;
				if (y == F2 || y == P) {
					s = logAxis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X);
				} else if (y % 2 == 0) {
					s = b(Blocks.POLISHED_ANDESITE);
				} else {
					s = y <= 3 && p.hash(u, y, v, 10) < 3 + 2 * moss ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.COBBLESTONE);
				}
				set(u, y, v, s);
			}
		}

		/** [depth] sloping plinth ledge round the foot, in cobble with some moss. */
		BlockState ledge(Wall wl, int t) {
			Block bl = p.hash(wl.u(t, 1), 0, wl.v(t, 1), 10) < 3 + 2 * moss ? Blocks.MOSSY_COBBLESTONE_STAIRS : Blocks.COBBLESTONE_STAIRS;
			return wl.stairIn(bl, false);
		}

		void walls() {
			for (int t = mu0; t <= mu1; t++) {
				wallCol(front, t, P);
				wallCol(back, t, P);
			}
			for (int t = va + 1; t <= vb - 1; t++) {
				wallCol(wLeft, t, P);
				wallCol(wRight, t, P);
			}
			quoin(mu0, va, P);
			quoin(mu1, va, P);
			quoin(mu0, vb, P);
			quoin(mu1, vb, P);
			for (int t = mu0 + 1; t <= mu1 - 1; t++) {
				front.set(t, 0, 1, ledge(front, t));
				back.set(t, 0, 1, ledge(back, t));
			}
			for (int t = va + 1; t <= vb - 1; t++) {
				if (!(ell && annexLeft)) {
					wLeft.set(t, 0, 1, ledge(wLeft, t));
				}
				if (!(ell && !annexLeft)) {
					wRight.set(t, 0, 1, ledge(wRight, t));
				}
			}
		}

		int step() {
			return annexLeft ? 1 : -1;
		}

		void annexWalls() {
			outer = new Wall(c, p, ao, annexLeft ? Direction.WEST : Direction.EAST);
			for (int t = va + 1; t <= vb - 1; t++) {
				wallCol(outer, t, PA);
				outer.set(t, 0, 1, ledge(outer, t));
			}
			for (int j = 0; j <= ai; j++) {
				int col = ao + j * step();
				int top = PA + j;
				wallCol(front, col, top);
				wallCol(back, col, top);
				if (j > 0) {
					front.set(col, 0, 1, ledge(front, col));
					back.set(col, 0, 1, ledge(back, col));
				}
			}
			quoin(ao, va, PA);
			quoin(ao, vb, PA);
			// the opening between the house and the forge: 3 wide, 3 high, a timber lintel
			int shared = annexLeft ? mu0 : mu1;
			for (int v = iv0 + 1; v <= iv0 + 3; v++) {
				for (int y = 1; y <= 3; y++) {
					set(shared, y, v, AIR);
				}
				set(shared, 4, v, logAxis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z));
			}
		}

		void floors() {
			for (int u = iu0; u <= iu1; u++) {
				for (int v = iv0; v <= iv1; v++) {
					int dist = kRight ? iu1 - u : u - iu0;
					set(u, 0, v, dist <= 3 ? b(Blocks.SPRUCE_PLANKS) : stoneFloor(u, v));
					set(u, F2, v, (v - iv0) % 3 == 0 ? logAxis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X) : b(Blocks.DARK_OAK_PLANKS));
				}
			}
			if (ell) {
				for (int j = 0; j < ai; j++) {
					int col = ao + step() * (1 + j);
					for (int v = iv0; v <= iv1; v++) {
						set(col, 0, v, stoneFloor(col, v));
					}
				}
			}
		}

		BlockState stoneFloor(int u, int v) {
			int h = p.hash(u, 0, v, 10);
			return h < 4 ? b(Blocks.STONE_BRICKS) : h < 6 ? b(Blocks.COBBLESTONE) : h < 8 ? b(Blocks.ANDESITE) : h < 9 && moss > 0 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.CRACKED_STONE_BRICKS);
		}

		/** [finish] vines creeping up the walls, more with more moss. */
		void vines() {
			vine(front, mu0 + 1, mu1 - 1, 1);
			vine(back, mu0 + 1, mu1 - 1, 3);
			if (!(ell && annexLeft)) {
				vine(wLeft, va + 2, vb - 2, 5);
			}
			if (!(ell && !annexLeft)) {
				vine(wRight, va + 2, vb - 2, 7);
			}
			if (ell) {
				vine(outer, va + 2, vb - 2, 9);
			}
		}

		void vine(Wall wl, int t0, int t1, int salt) {
			Direction toWall = wl.out.getOpposite();
			int den = moss == 0 ? 9 : moss == 1 ? 6 : 4;
			for (int t = t0; t <= t1; t++) {
				if (p.hash(t, salt, wl.a, den) != 0) {
					continue;
				}
				int len = 2 + p.hash(t, salt, 1, 3) + moss;
				for (int y = 1; y <= 1 + len; y++) {
					wl.set(t, y, 1, vine(toWall));
				}
			}
		}

		// -------------------------------------------------------------- windows and portal

		/** [depth] a window: timber lintel three wide, stone sill (ground floor only), open shutters. */
		void window(Wall wl, int a, int y, boolean sill) {
			Direction.Axis ax = wl.alongU() ? Direction.Axis.X : Direction.Axis.Z;
			wl.set(a, y, 0, wl.pane());
			wl.set(a, y + 1, 0, wl.pane());
			for (int t = a - 1; t <= a + 1; t++) {
				wl.set(t, y + 2, 0, logAxis(Blocks.STRIPPED_DARK_OAK_LOG, ax));
			}
			if (sill) {
				wl.set(a, y - 1, 1, wl.stairIn(Blocks.STONE_BRICK_STAIRS, false));
			}
			for (int dt = -1; dt <= 1; dt += 2) {
				for (int k = 0; k < 2; k++) {
					wl.set(a + dt, y + k, 1, wl.shutter(Blocks.DARK_OAK_TRAPDOOR));
				}
			}
		}

		void windows() {
			int off = mw >= 11 ? 3 : 2;
			for (int a : new int[] {du - off, du + off}) {
				window(front, a, 2, true);
				window(front, a, 7, false);
				window(back, a, 2, true);
				window(back, a, 7, false);
			}
			window(back, du, 7, false);
			// end walls: the kitchen end keeps the window at the back (the chimney takes the front), the other end the middle
			Wall kw = kRight ? wRight : wLeft;
			window(kw, vb - 2, 2, true);
			window(kw, vb - 2, 7, false);
			if (!ell) {
				Wall ww = kRight ? wLeft : wRight;
				int mv = (va + vb) / 2;
				window(ww, mv, 2, true);
				window(ww, mv, 7, false);
			} else {
				int wc = ao + step() * 2;
				window(front, wc, 2, true);
				window(back, wc, 2, true);
				window(outer, iv0 + 2, 2, true);
			}
		}

		/**
		 * [depth] the portal: two pilasters up to the eave, a timber lintel and a stone bay above, so the door sits in a deep
		 * recess and nothing is left to stand on under the roof (walkability audit).
		 */
		void portal() {
			int n = va - 1;
			for (int s = -1; s <= 1; s += 2) {
				int u = du + s;
				for (int y = 0; y <= 4; y++) {
					BlockState st = y == 0 ? b(Blocks.MOSSY_COBBLESTONE) : y % 2 == 0 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.COBBLESTONE);
					set(u, y, n, st);
				}
			}
			for (int u = du - 1; u <= du + 1; u++) {
				set(u, 4, n, logAxis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
				set(u, F2, n, logAxis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
				for (int y = F2 + 1; y <= P - 1; y++) {
					BlockState st = u == du && y == P - 2 ? b(Blocks.CHISELED_STONE_BRICKS) : p.hash(u, y, n, 6) == 0 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
					set(u, y, n, st);
				}
			}
			// the door in the front wall, a transom, a timber lintel
			front.set(du, 1, 0, front.door(Blocks.DARK_OAK_DOOR, true));
			front.set(du, 2, 0, front.door(Blocks.DARK_OAK_DOOR, false));
			front.set(du, 3, 0, front.pane());
			for (int t = du - 1; t <= du + 1; t++) {
				front.set(t, 4, 0, logAxis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
			}
			// the step, flush with the floor
			set(du, 0, n, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH));
		}

		// -------------------------------------------------------------- roofs

		Block roofStair(int u, int v, boolean edge) {
			if (edge) {
				return Blocks.DARK_OAK_STAIRS;
			}
			int h = p.hash(u, 31, v, 9);
			if (roofKind == 0) {
				return h == 0 ? Blocks.COBBLED_DEEPSLATE_STAIRS : h == 1 ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
			}
			return h <= 1 ? Blocks.DARK_OAK_STAIRS : Blocks.SPRUCE_STAIRS;
		}

		BlockState roofSolid() {
			return roofKind == 0 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.SPRUCE_PLANKS);
		}

		BlockState ridgeSlab() {
			return roofKind == 0 ? slab(Blocks.DEEPSLATE_TILE_SLAB) : slab(Blocks.DARK_OAK_SLAB);
		}

		/** [finish] gabled roof, ridge along u, eave and verge overhang with dark oak boards, slab ridge. */
		void roofMain() {
			int uFrom = ell && annexLeft ? mu0 : mu0 - 1;
			int uTo = ell && !annexLeft ? mu1 : mu1 + 1;
			for (int v = va - 1; v <= vb + 1; v++) {
				int l = roofL(v);
				boolean frontHalf = v - (va - 1) < (vb + 1) - v;
				boolean mid = v - (va - 1) == (vb + 1) - v;
				for (int u = uFrom; u <= uTo; u++) {
					boolean verge = u == mu0 - 1 || u == mu1 + 1;
					boolean edge = l == 0 || verge;
					if (mid) {
						set(u, P + l, v, verge ? b(Blocks.DARK_OAK_PLANKS) : roofSolid());
					} else {
						set(u, P + l, v, stairs(roofStair(u, v, edge), frontHalf ? Direction.SOUTH : Direction.NORTH));
					}
					if (l == lmax) {
						set(u, P + lmax + 1, v, ridgeSlab());
					}
				}
			}
		}

		/** The forge lean-to: one slope rising to the house wall. */
		void roofAnnex() {
			Direction rise = annexLeft ? Direction.EAST : Direction.WEST;
			for (int v = va - 1; v <= vb + 1; v++) {
				boolean vedge = v == va - 1 || v == vb + 1;
				for (int j = -1; j <= ai; j++) {
					int col = ao + j * step();
					int y = j == -1 ? PA : PA + 1 + j;
					set(col, y, v, stairs(roofStair(col, v, vedge || j == -1), rise));
				}
			}
		}

		/** [depth] gable triangles in stone with a timber tie beam and a tinted light. */
		void gables() {
			int vc = iv0 + 1;
			for (int col : new int[] {mu0, mu1}) {
				boolean kitchen = col == (kRight ? mu1 : mu0);
				for (int v = va + 1; v <= vb - 1; v++) {
					int l = roofL(v);
					for (int y = P + 1; y <= P + l - 1; y++) {
						int h = p.hash(col, y, v, 10);
						BlockState s = y == P + 1 ? logAxis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z)
								: h < 2 ? b(Blocks.POLISHED_ANDESITE) : h < 3 + moss ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
						set(col, y, v, s);
					}
					if (l == lmax && !(kitchen && (v == vc || v == vc + 1))) {
						set(col, P + lmax - 2, v, b(Blocks.TINTED_GLASS));
					}
				}
			}
		}

		void stack(int col, int v0, int top, boolean broad) {
			for (int y = 0; y <= top; y++) {
				for (int v = v0; v <= v0 + 1; v++) {
					BlockState s;
					if (y == top) {
						s = slab(Blocks.STONE_BRICK_SLAB);
					} else if (y <= 1) {
						s = p.hash(col, y, v, 10) < 5 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.COBBLESTONE);
					} else {
						int h = p.hash(col, y, v, 10);
						s = h < 2 ? b(Blocks.CRACKED_STONE_BRICKS) : h < 3 && y < 6 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
					}
					set(col, y, v, s);
				}
			}
			if (broad) {
				set(col, 0, v0 - 1, b(Blocks.COBBLESTONE));
				set(col, 0, v0 + 2, b(Blocks.MOSSY_COBBLESTONE));
			}
		}

		void chimneys() {
			int vc = iv0 + 1;
			int mk = kRight ? mu1 : mu0;
			int col = kRight ? mu1 + 1 : mu0 - 1;
			stack(col, vc, Math.min(p.h() - 1, P + lmax + 2), true);
			// the hearth: a furnace and a smoker in the wall plane, facing the room
			set(mk, 1, vc, lit(Blocks.FURNACE, inK));
			set(mk, 1, vc + 1, lit(Blocks.SMOKER, inK));
			if (ell) {
				int fcol = annexLeft ? ao - 1 : ao + 1;
				stack(fcol, iv1 - 1, Math.min(p.h() - 1, 12), false);
				Direction inA = annexLeft ? Direction.EAST : Direction.WEST;
				set(ao, 1, iv1 - 1, lit(Blocks.BLAST_FURNACE, inA));
				set(ao, 1, iv1, lit(Blocks.FURNACE, inA));
			}
		}

		/** [depth] beam tails under the long eaves, every second cell (cells taken by shutters are skipped). */
		void eaves() {
			for (int u = mu0 + 1; u <= mu1 - 1; u += 2) {
				EaveKit.corbel(c, p, u, P - 1, va - 1, Direction.SOUTH, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS);
				EaveKit.corbel(c, p, u, P - 1, vb + 1, Direction.NORTH, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS);
			}
		}

		// -------------------------------------------------------------- stair

		/**
		 * The stair, along the back wall: a landing at the kitchen end, five steps, an exit at the other end. Head-on first
		 * step, free air over every step, the floor above cut over the flight and fenced.
		 */
		void stair() {
			Direction dir = inK;
			for (int k = 0; k < 5; k++) {
				int u = ik(k + 1);
				for (int y = 1; y <= k; y++) {
					set(u, y, iv1, b(Blocks.STONE_BRICKS));
				}
				set(u, 1 + k, iv1, stairs(Blocks.STONE_BRICK_STAIRS, dir));
				if (k < 4) {
					set(u, F2, iv1, AIR);
				}
			}
			List<int[]> rail = new ArrayList<>();
			for (int k = 0; k < 4; k++) {
				rail.add(new int[] {ik(k + 1), iv1 - 1});
			}
			rail.add(new int[] {ik(0), iv1});
			for (int[] q : rail) {
				boolean n = has(rail, q[0], q[1] - 1) || q[1] - 1 < iv0;
				boolean s = has(rail, q[0], q[1] + 1) || q[1] + 1 > iv1;
				boolean wst = has(rail, q[0] - 1, q[1]) || q[0] - 1 < iu0;
				boolean e = has(rail, q[0] + 1, q[1]) || q[0] + 1 > iu1;
				set(q[0], 6, q[1], Blocks.DARK_OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.NORTH, n)
						.setValue(BlockStateProperties.EAST, e).setValue(BlockStateProperties.SOUTH, s).setValue(BlockStateProperties.WEST, wst));
			}
		}

		static boolean has(List<int[]> l, int u, int v) {
			for (int[] q : l) {
				if (q[0] == u && q[1] == v) {
					return true;
				}
			}
			return false;
		}

		// -------------------------------------------------------------- furniture

		void groundFloor() {
			int nR = iv1 - iv0; // front rows (the back row is the stair row)
			// kitchen wall: barrel, hearth in the wall (aisle in front of it), counter
			set(ik(0), 1, iv0, StoneKit.barrel());
			set(ik(0), 1, iv0 + 3, slab(Blocks.SMOOTH_STONE_SLAB));
			// dining table with an end seat on each side
			int tc = ik(2);
			for (int v = iv0 + 1; v <= iv0 + 3; v++) {
				set(tc, 1, v, slab(Blocks.DARK_OAK_SLAB));
			}
			set(tc, 2, iv0 + 2, b(Blocks.POTTED_FLOWERING_AZALEA));
			set(tc, 1, iv0, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
			set(tc, 1, iv0 + 4, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
			// workshop wall (or the forge opening): crafting and two stations by seed, chest, barrel
			int wc = iwk(0);
			if (!ell) {
				Block[][] sets = {{Blocks.SMITHING_TABLE, Blocks.GRINDSTONE}, {Blocks.LOOM, Blocks.GRINDSTONE}, {Blocks.SMITHING_TABLE, Blocks.LOOM}};
				set(wc, 1, iv0, b(Blocks.CRAFTING_TABLE));
				set(wc, 1, iv0 + 1, fac(sets[setK][0], inW));
				set(wc, 1, iv0 + 2, fac(sets[setK][1], inW));
				set(wc, 1, iv0 + 3, fac(Blocks.CHEST, inW));
				set(wc, 1, iv0 + 4, StoneKit.barrel());
			} else {
				set(wc, 1, iv0, StoneKit.barrel());
				set(wc, 1, iv0 + 4, fac(Blocks.CHEST, inW));
			}
			// storage along the back wall past the stair exit
			for (int i = 7; i < iw; i++) {
				set(ik(i), 1, iv1, i % 2 == 1 ? fac(Blocks.CHEST, Direction.NORTH) : StoneKit.barrel());
			}
			if (nR >= 7) {
				set(ik(0), 1, iv1 - 2, b(Blocks.HAY_BLOCK));
			}
			// a red runner from the door and two hanging lanterns over the free aisle
			for (int v = iv0; v <= iv1 - 1; v++) {
				set(du, 1, v, b(Blocks.CARPET.pick(DyeColor.RED)));
			}
			int lc = iwk(2);
			for (int v : new int[] {iv0 + 1, iv0 + 3}) {
				p.put(c, lc, 4, v, StoneKit.lantern(true), CitadelCanvas.HANGING);
			}
			for (int v : new int[] {iv0 + 2, iv1 - 1}) {
				set(ik(1), 3, v, invisibleLight(12));
				set(iwk(1), 3, v, invisibleLight(12));
			}
		}

		void upperFloor() {
			// storage along the kitchen end: chests and a barrel with a lantern
			set(ik(0), 6, iv0, fac(Blocks.CHEST, inK));
			set(ik(0), 6, iv0 + 1, fac(Blocks.CHEST, inK));
			set(ik(0), 6, iv0 + 2, StoneKit.barrel());
			set(ik(0), 7, iv0 + 2, StoneKit.lantern(false));
			set(ik(0), 6, iv0 + 3, fac(Blocks.CHEST, inK));
			// a study corner on the front wall
			set(ik(1), 6, iv0, b(Blocks.BOOKSHELF));
			set(ik(2), 6, iv0, b(Blocks.BOOKSHELF));
			// beds with their heads to the workshop end
			DyeColor[] cols = {DyeColor.RED, DyeColor.BLUE, DyeColor.WHITE, DyeColor.CYAN};
			int[] rows = iv1 - 2 >= iv0 + 3 ? new int[] {iv0 + 1, iv0 + 3} : new int[] {iv0 + 1};
			for (int r : rows) {
				DyeColor col = cols[p.hash(r, 6, 3, cols.length)];
				set(iwk(0), 6, r, bed(col, BedPart.HEAD, inK));
				set(iwk(1), 6, r, bed(col, BedPart.FOOT, inK));
			}
			set(iwk(0), 6, iv0 + 2, StoneKit.barrel());
			set(iwk(0), 7, iv0 + 2, StoneKit.lantern(false));
			set(iwk(0), 6, iv0, fac(Blocks.CHEST, inW));
			// a rug in the middle and light under the roof
			for (int v = iv0 + 1; v <= iv0 + 3; v++) {
				for (int i = 2; i <= 4; i++) {
					set(ik(i), 6, v, b(Blocks.CARPET.pick(DyeColor.BROWN)));
				}
			}
			for (int v : new int[] {iv0 + 1, iv0 + 3}) {
				set(ik(4), 8, v, invisibleLight(13));
			}
		}

		/** The forge: stations by seed along the outer wall, quench cauldron, barrel, chest. */
		void forge() {
			Direction inA = annexLeft ? Direction.EAST : Direction.WEST;
			int c0 = ao + step();
			Block[][] sets = {{Blocks.SMITHING_TABLE, Blocks.GRINDSTONE}, {Blocks.LOOM, Blocks.GRINDSTONE}, {Blocks.SMITHING_TABLE, Blocks.LOOM}};
			set(c0, 1, iv0, b(Blocks.CRAFTING_TABLE));
			set(c0, 1, iv0 + 1, fac(sets[setK][0], inA));
			set(c0, 1, iv0 + 2, fac(sets[setK][1], inA));
			set(c0, 1, iv0 + 3, fac(Blocks.ANVIL, inA.getClockWise()));
			int cm = ao + step() * ai;
			set(cm, 1, iv1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
			set(cm, 1, iv1 - 1, StoneKit.barrel());
			set(cm, 1, iv0, fac(Blocks.CHEST, inA.getOpposite()));
			set(ao + step() * 2, 5, iv0 + 2, invisibleLight(13));
			set(ao + step() * 2, 3, iv0 + 2, invisibleLight(13));
		}

		// -------------------------------------------------------------- lot

		void yard() {
			int bl = ell && annexLeft ? ao : mu0;
			int br = ell && !annexLeft ? ao : mu1;
			// flagstone path (half slabs) from the street to the step
			for (int v = 0; v <= va - 2; v++) {
				for (int u = du - 1; u <= du + 1; u++) {
					int h = p.hash(u, 0, v, 5);
					set(u, 0, v, h == 0 ? slab(Blocks.MOSSY_COBBLESTONE_SLAB) : h < 3 ? slab(Blocks.STONE_BRICK_SLAB) : slab(Blocks.COBBLESTONE_SLAB));
				}
			}
			// front beds: vegetables left, a flower bed or the trough right
			if (va >= 3) {
				int rows = Math.min(va - 1, 4);
				int r1 = va - 2;
				int r0 = r1 - rows + 1;
				if (du - 3 - 1 >= 1) {
					vegBed(1, du - 3, r0, r1);
				}
				if (w - 2 - (du + 3) >= 1) {
					if (yardK == 0) {
						flowerBed(du + 3, w - 2, r0, r1);
					} else {
						trough(du + 3, r1);
						set(du + 3, 0, r1 - 1, b(Blocks.HAY_BLOCK));
						set(du + 4, 0, r1 - 1, b(Blocks.HAY_BLOCK));
					}
				}
			}
			// back strip under the eave: a woodpile at the workshop end, a trough and a barrel at the kitchen end
			int bk = d - 1;
			for (int i = 0; i < 4; i++) {
				set(iwk(i), 0, bk, logAxis(Blocks.SPRUCE_LOG, Direction.Axis.X));
			}
			set(ik(0), 0, bk, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
			set(ik(1), 0, bk, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
			set(ik(2), 0, bk, StoneKit.barrel());
			// dress what is left: ferns, flowers, azalea, moss
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					boolean footprint = u >= bl - 1 && u <= br + 1 && v >= va - 1 && v <= vb + 1;
					if (footprint || !free(u, 0, v)) {
						continue;
					}
					BlockState s = switch (p.hash(u, 9, v, 14)) {
						case 0, 1 -> b(Blocks.FERN);
						case 2 -> b(Blocks.PINK_PETALS);
						case 3 -> b(Blocks.POPPY);
						case 4 -> b(Blocks.DANDELION);
						case 5 -> b(Blocks.CORNFLOWER);
						case 6 -> b(Blocks.AZALEA);
						case 7 -> b(Blocks.FLOWERING_AZALEA);
						case 8 -> b(Blocks.MOSS_CARPET);
						default -> null;
					};
					if (s != null) {
						set(u, 0, v, s);
					}
				}
			}
			// a low azalea hedge across the front corners of the lot, one block high
			for (int u : new int[] {1, w - 2}) {
				if (Math.abs(u - du) > 3 && free(u, 0, 0)) {
					set(u, 0, 0, p.hash(u, 1, 0, 3) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.AZALEA_LEAVES));
				}
			}
		}

		void trough(int u, int v) {
			BlockState water = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
			set(u, 0, v, water);
			set(u + 1, 0, v, water);
		}

		void vegBed(int u0, int u1, int v0, int v1) {
			BlockState farm = Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7);
			int cu = (u0 + u1) / 2;
			int cv = (v0 + v1) / 2;
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					set(u, 0, v, farm);
					Block crop = (u + v) % 3 == 0 ? Blocks.WHEAT : (u + v) % 3 == 1 ? Blocks.CARROTS : Blocks.POTATOES;
					set(u, 1, v, crop.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7));
				}
			}
			if ((u1 - u0 + 1) * (v1 - v0 + 1) >= 6) {
				set(cu, 1, cv, Blocks.DARK_OAK_FENCE.defaultBlockState());
				set(cu, 2, cv, b(Blocks.HAY_BLOCK));
				set(cu, 3, cv, b(Blocks.CARVED_PUMPKIN).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
			}
		}

		void flowerBed(int u0, int u1, int v0, int v1) {
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					set(u, 0, v, b(Blocks.MOSS_BLOCK));
					int k = p.hash(u, 1, v, 5);
					set(u, 1, v, k == 0 ? b(Blocks.FLOWERING_AZALEA) : k == 1 ? b(Blocks.AZALEA) : k == 2 ? b(Blocks.FERN) : k == 3 ? b(Blocks.PINK_PETALS) : b(Blocks.CORNFLOWER));
				}
			}
		}
	}
}
