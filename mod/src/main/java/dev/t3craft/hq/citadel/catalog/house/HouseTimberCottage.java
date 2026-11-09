package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * A timber-framed cottage you can live in. Stone plinth, calcite plaster between dark oak posts, a steep gable roof with
 * deep eaves, a covered porch with a bench, gabled dormers and a brick chimney that leans on the right
 * gable. Inside: a hearth you can stand in front of, a kitchen row (crafting table, furnace, smoker, barrels, chests),
 * a dining table with chairs and a bed. Every size has a way to every floor:
 * <ul>
 * <li><b>One storey</b> (plots below 12 × 12, or too low): a single high room under the open rafters (4 free blocks at the
 *     walls, the roof is the ceiling), with the bed in the room. Nothing to climb, nothing to seal.</li>
 * <li><b>Two storeys</b> (12+ wide, 12+ deep, 18+ high): a walk-up flight of five steps along the left wall, cut by this class (not
 *     searched), a fence round the hole, a bedroom above with beds, chests and a balcony over the porch.</li>
 * </ul>
 * Plots bigger than the house (the plot grows with the scale) keep the house at most 18 × 14 and put the rest to use: a
 * fenced front garden with a path, vegetable beds on the sides and at the back.
 *
 * <p>Walkability rules kept (see {@code WalkAudit}): 3 free blocks over every cell you can stand on, furniture one block
 * high only (so no table or counter top is a floor nobody reaches), no hanging lantern over a walkable cell lower than
 * 3 blocks, a fire with no water near it.
 *
 * <pre>
 *  local layout, front = v 0, looking down           elevation (single storey)
 *  fy  . garden: path, beds, fence .                   y9   ridge (slab cap)
 *  fy  | | porch posts, rails, bay + steps |           y5   eave on the beam over the porch posts, plate on the walls
 *  fy+2 ■ front wall + door ■                          y1-4 walls: footing, windows (y2-3), frieze (y4)
 *  ...  ■ room: kitchen back, bed left, hearth right   y0   plinth floor
 *  vb   ■ back wall, pilasters at v+1 ■
 * </pre>
 *
 * Size: 10–21 × 10–21 × 14+ (the house itself stops at 18 × 14; the rest of a bigger plot becomes garden).
 */
public final class HouseTimberCottage implements Variant {
	@Override
	public String id() {
		return "house_timber_cottage";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 10;
	}

	@Override
	public int minD() {
		return 10;
	}

	@Override
	public int minH() {
		return 14;
	}

	@Override
	public int maxW() {
		return 21;
	}

	@Override
	public int maxD() {
		return 21;
	}

	/** [palette] two roofs per seed: slate with a cherry eave, or cherry with a slate eave. Frame and plaster are shared. */
	private record Scheme(Block frame, BlockState plaster, Block roof, Block eave, Block alt, Block ridge, Block ridgeSlab, Block door,
			Block shutter, Block sill) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.STRIPPED_SPRUCE_LOG, b(Blocks.CALCITE), Blocks.DEEPSLATE_TILE_STAIRS, Blocks.CHERRY_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS,
					Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB, Blocks.CHERRY_DOOR, Blocks.CHERRY_TRAPDOOR, Blocks.SPRUCE_STAIRS),
			new Scheme(Blocks.STRIPPED_SPRUCE_LOG, b(Blocks.CALCITE), Blocks.CHERRY_STAIRS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.MANGROVE_STAIRS,
					Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.SPRUCE_STAIRS),
	};

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Scheme s = SCHEMES[p.rng(0xC077A6E5L).nextInt(SCHEMES.length)];
		new Build(c, p, s).run();
	}

	// ------------------------------------------------------------------ post layout helpers

	/** Posts of a run a..b (inclusive): the anchors, plus a post in the middle of any gap wider than 5 cells. Indexed {@code i - a}. */
	private static boolean[] posts(int a, int b, int... anchors) {
		boolean[] r = new boolean[b - a + 1];
		for (int x : anchors) {
			if (x >= a && x <= b) {
				r[x - a] = true;
			}
		}
		int prev = -1;
		for (int i = 0; i < r.length; i++) {
			if (r[i]) {
				if (prev >= 0) {
					split(r, prev, i);
				}
				prev = i;
			}
		}
		return r;
	}

	private static void split(boolean[] r, int lo, int hi) {
		int g = hi - lo - 1;
		if (g > 5) {
			int m = lo + 1 + g / 2;
			r[m] = true;
			split(r, lo, m);
			split(r, m, hi);
		}
	}

	/** [palette] the heavy, dark band: three deepslate textures mixed by a per-cell hash, never uniform. */
	private static BlockState stoneAt(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			case 1 -> b(Blocks.COBBLED_DEEPSLATE);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	/** The hearth and chimney: brick with a few dark bricks. */
	private static BlockState brickAt(Plot p, int u, int y, int v) {
		return p.hash(u, y, v, 4) == 0 ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.BRICKS);
	}

	// ------------------------------------------------------------------ the builder

	private static final class Build {
		final CitadelCanvas c;
		final Plot p;
		final Scheme s;
		final int w;
		final int d;
		final int h;
		/** House width, left margin, front garden depth, house depth, spare rows behind the house. */
		final int bw;
		final int ox;
		final int fy;
		final int bd;
		final int rear;
		/** Wall planes: left u, right u, ground front v, back v, upper front v; roof plate layer. */
		final int ua;
		final int ub;
		final int vf;
		final int vb;
		final int vfU;
		final int plate;
		/** Interior of the ground floor (first free cells inside the walls). */
		final int iu0;
		final int iu1;
		final int iv0;
		final int iv1;
		final int doorW;
		final int d0;
		/** First row of the hearth (two cells wide) and whether the porch has posts at the flanks of the door bay. */
		final int hv;
		final boolean hoodOk;
		final boolean two;
		final boolean[] frontP;
		final boolean[] backP;
		final boolean[] sideP;
		final boolean[] porchP;

		Build(CitadelCanvas c, Plot p, Scheme s) {
			this.c = c;
			this.p = p;
			this.s = s;
			w = p.w();
			d = p.d();
			h = p.h();
			bw = Math.min(w, 18);
			ox = (w - bw) / 2;
			fy = d >= 14 ? 2 : 0;
			int bd0 = Math.min(d - fy, 14);
			two = bw >= 12 && bd0 >= 12 && h >= 18;
			plate = two ? 9 : 5;
			int bdMax = 2 * (h - plate - 4) + 2;
			bd = Math.max(8, Math.min(bd0, bdMax));
			rear = d - fy - bd;
			ua = ox + 1;
			ub = ox + bw - 2;
			vf = fy + 2;
			vb = fy + bd - 2;
			vfU = two ? fy + 1 : vf;
			iu0 = ua + 1;
			iu1 = ub - 1;
			iv0 = vf + 1;
			iv1 = vb - 1;
			int len = ub - ua + 1;
			doorW = len % 2 == 0 ? 2 : 1;
			d0 = ua + (len - doorW) / 2;
			int id = iv1 - iv0 + 1;
			hv = iv0 + Math.max(0, (id - 4) / 2) + 1;
			hoodOk = true; // posts at the flanks of the door bay
			frontP = posts(ua, ub, ua, ub, d0 - 1, d0 + doorW);
			backP = posts(ua, ub, ua, ub);
			sideP = posts(vfU, vb, vfU, vb);
			boolean[] pp = hoodOk ? posts(ua, ub, d0 - 2, d0 + doorW + 1, ua + 1 == d0 - 2 ? -1 : ua, ub - 1 == d0 + doorW + 1 ? -1 : ub) : posts(ua, ub, ua, ub);
			for (int u = d0 - 1; u <= d0 + doorW; u++) {
				if (u >= ua && u <= ub) {
					pp[u - ua] = false;
				}
			}
			porchP = pp;
		}

		// -------------------------------------------------------------- writers

		void set(int u, int y, int v, BlockState st) {
			p.set(c, u, y, v, st);
		}

		void line(boolean alongU, int line, int i, int y, BlockState st) {
			if (alongU) {
				set(i, y, line, st);
			} else {
				set(line, y, i, st);
			}
		}

		BlockState frame() {
			return s.frame().defaultBlockState();
		}

		BlockState beam(Direction.Axis axis) {
			return TimberKit.log(s.frame(), axis);
		}

		boolean inBay(int u) {
			return u >= d0 - 1 && u <= d0 + doorW;
		}

		/** Height of the roof surface over row v. */
		int roofY(int v) {
			return plate + Math.min(v - fy, fy + bd - 1 - v);
		}

		boolean free(int u, int y, int v) {
			if (!p.has(u, y, v)) {
				return false;
			}
			BlockState st = p.at(c, u, y, v);
			return st == null || st.isAir();
		}

		void run() {
			floor();
			if (two) {
				upperFloor();
			}
			storey(1, 4, 5, vf, true);
			if (two) {
				storey(6, 3, plate, vfU, false);
			}
			proud();
			roof();
			gables();
			dormers();
			porch();
			hearth();
			furnishGround();
			if (two) {
				stair();
				furnishUpper();
			}
			chandelier();
			yard();
		}

		// -------------------------------------------------------------- floor

		void floor() {
			for (int v = fy; v <= vb; v++) {
				for (int u = ua; u <= ub; u++) {
					boolean edge = u == ua || u == ub || v == vb || v == fy;
					set(u, 0, v, edge ? stoneAt(p, u, 0, v) : p.hash(u, 0, v, 3) == 0 ? b(Blocks.DARK_OAK_PLANKS) : b(Blocks.SPRUCE_PLANKS));
				}
			}
		}

		/** The floor of the upper storey (layer 5): logs round the edge and under the balcony, planks inside. */
		void upperFloor() {
			for (int v = fy; v <= vb; v++) {
				for (int u = ua; u <= ub; u++) {
					boolean sideEdge = u == ua || u == ub;
					boolean frontEdge = v == fy || v == vfU || v == vb;
					set(u, 5, v, sideEdge ? beam(Direction.Axis.Z) : frontEdge ? beam(Direction.Axis.X) : b(Blocks.SPRUCE_PLANKS));
				}
			}
		}

		// -------------------------------------------------------------- walls

		/**
		 * One storey of the four wall planes. {@code fb} is the first free layer, {@code hh} the layers of wall (footing, two window rows,
		 * frieze), {@code beamY} the layer of the log that closes the wall, {@code frontV} the front plane (the upper floor's moves forward
		 * over the porch).
		 */
		void storey(int fb, int hh, int beamY, int frontV, boolean ground) {
			wallRun(true, frontV, -1, ua, ub, fb, hh, beamY, frontP, ua, d0, d0 + doorW - 1, ground, false, null);
			wallRun(true, vb, 1, ua, ub, fb, hh, beamY, backP, ua, -1, -2, ground, true, null);
			// the right side wall has the hearth in it: no windows there
			IntPredicate hearthCells = v -> v >= hv - 1 && v <= hv + 2 && fb == 1;
			wallRun(false, ua, -1, frontV + 1, vb - 1, fb, hh, beamY, sideP, vfU, -1, -2, ground, true, null);
			wallRun(false, ub, 1, frontV + 1, vb - 1, fb, hh, beamY, sideP, vfU, -1, -2, ground, true, hearthCells);
		}

		void wallRun(boolean alongU, int line, int outward, int a, int bEnd, int fb, int hh, int beamY, boolean[] post, int pBase, int doorLo, int doorHi,
				boolean ground, boolean dress, IntPredicate solid) {
			int pad = alongU ? 0 : 1;
			IntPredicate isP = k -> k < a || k > bEnd || (k - pBase >= 0 && k - pBase < post.length && post[k - pBase]) || (k >= doorLo && k <= doorHi)
					|| (solid != null && solid.test(k));
			Direction.Axis axis = alongU ? Direction.Axis.X : Direction.Axis.Z;
			boolean[] winCell = new boolean[bEnd - a + 1];
			for (int i = a; i <= bEnd; i++) {
				boolean isDoor = i >= doorLo && i <= doorHi;
				boolean isPost = post[i - pBase];
				boolean win = !isP.test(i) && TimberKit.gapWin(isP, i, a - pad, bEnd + pad);
				winCell[i - a] = win;
				for (int k = 0; k < hh; k++) {
					int y = fb + k;
					BlockState st;
					if (isDoor) {
						if (k <= 1) {
							st = TimberKit.door(s.door(), Direction.NORTH, k == 1).setValue(BlockStateProperties.DOOR_HINGE,
									i == doorLo ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
						} else if (k == 2) {
							st = AIR; // 3 free blocks in the doorway: the lintel is the layer above
						} else {
							st = beam(axis); // [depth] lintel
						}
					} else if (isPost) {
						// [depth] on the side and back walls the post stands proud outside; the wall behind it stays white
						boolean corner = alongU && (i == a || i == bEnd);
						st = dress && !corner ? s.plaster() : frame();
					} else if (win && (k == 1 || k == 2)) {
						st = TimberKit.pane(alongU, null);
					} else if (win && k == 3) {
						st = beam(axis); // [depth] a dark lintel over every window
					} else if (ground && k == 0) {
						st = alongU ? stoneAt(p, i, y, line) : stoneAt(p, line, y, i); // [palette] heavy stone footing = the sill
					} else {
						st = s.plaster();
					}
					line(alongU, line, i, y, st);
				}
				line(alongU, line, i, beamY, beam(axis));
			}
			if (!dress || ground) {
				return;
			}
			// [depth] upper storey: open shutters beside every window run. (No ledge, sill or box: a ledge under the eave is a floor nobody reaches.)
			Direction outD = TimberKit.out(alongU, outward);
			int outer = line + outward;
			int i = a;
			while (i <= bEnd) {
				if (!winCell[i - a]) {
					i++;
					continue;
				}
				int j = i;
				while (j + 1 <= bEnd && winCell[j + 1 - a]) {
					j++;
				}
				int lm = i - 1;
				int rm = j + 1;
				if (lm >= a && !isP.test(lm)) {
					line(alongU, outer, lm, fb + 1, TimberKit.trap(s.shutter(), alongU ? Direction.WEST : Direction.NORTH, true, false));
					line(alongU, outer, lm, fb + 2, TimberKit.trap(s.shutter(), alongU ? Direction.WEST : Direction.NORTH, true, false));
				}
				if (rm <= bEnd && !isP.test(rm)) {
					line(alongU, outer, rm, fb + 1, TimberKit.trap(s.shutter(), alongU ? Direction.EAST : Direction.SOUTH, true, false));
					line(alongU, outer, rm, fb + 2, TimberKit.trap(s.shutter(), alongU ? Direction.EAST : Direction.SOUTH, true, false));
				}
				i = j + 1;
			}
		}

		/**
		 * [depth] posts that stand proud of the walls: along both sides under the rake (up to the barge board), and as pilasters on the
		 * back wall under the eave, each on a stone foot.
		 */
		void proud() {
			int right = ox + bw - 1;
			for (int v = vfU; v <= vb; v++) {
				if (!sideP[v - vfU]) {
					continue;
				}
				int top = Math.max(plate - 1, roofY(v) - 2);
				for (int u : new int[] {ox, right}) {
					set(u, 0, v, stoneAt(p, u, 0, v));
					for (int y = 1; y <= top; y++) {
						set(u, y, v, frame());
					}
				}
			}
			int bv = fy + bd - 1;
			for (int u = ua; u <= ub; u++) {
				if (backP[u - ua]) {
					set(u, 0, bv, stoneAt(p, u, 0, bv));
					for (int y = 1; y < plate; y++) {
						set(u, y, bv, frame());
					}
				}
			}
			for (int u : new int[] {ox, right}) {
				set(u, 0, bv, stoneAt(p, u, 0, bv));
				for (int y = 1; y < plate; y++) {
					set(u, y, bv, frame());
				}
			}
		}

		// -------------------------------------------------------------- roof

		void roof() {
			TimberKit.gable(c, p, true, ox, ox + bw - 1, fy, fy + bd - 1, plate, s.roof(), s.eave(), s.ridge(), s.ridgeSlab(), s.alt(), true, true);
			// the front plane of a one-storey house stops under the roof: a frieze closes the gap
			if (!two) {
				for (int u = ua; u <= ub; u++) {
					set(u, plate + 1, vf, s.plaster());
				}
			}
		}

		/** Plaster gable ends with a king post, a dark tie and a pair of tinted lights. */
		void gables() {
			int mid0 = fy + (bd - 1) / 2;
			int mid1 = fy + bd / 2;
			for (int u : new int[] {ua, ub}) {
				for (int v = vfU; v <= vb; v++) {
					int top = roofY(v);
					for (int y = plate + 1; y < top; y++) {
						BlockState st = s.plaster();
						if (mid0 == mid1 && v == mid0) {
							st = frame();
						} else if (y == plate + 2 && mid0 == mid1 && Math.abs(v - mid0) == 1) {
							st = TimberKit.pane(false, DyeColor.PINK);
						} else if ((y == plate + 2 || y == plate + 3) && mid0 != mid1 && (v == mid0 || v == mid1)) {
							st = TimberKit.pane(false, DyeColor.PINK);
						} else if (y == plate + 3) {
							st = beam(Direction.Axis.Z);
						}
						if (y == top - 1 && v != mid0 && v != mid1 && st == s.plaster()) {
							// [depth] a spruce rafter band under the barge board: the gable looks framed, not flat
							st = stairs(Blocks.SPRUCE_STAIRS, v < mid0 ? Direction.SOUTH : Direction.NORTH);
						}
						set(u, y, v, st);
					}
				}
			}
		}

		/**
		 * [finish] gabled dormers on the front slope, one over the door or two on wide houses: a framed window in the slope under a
		 * small cross gable that runs back into the main slope. The cavity behind them is never taller than one block (the main roof
		 * stairs stay in place under the dormer roof), so there is no floor up there for the audit to count.
		 */
		void dormers() {
			int r = Math.max(fy + 1, Math.min(fy + 2, fy + (bd - 1) / 2 - 3));
			int base = plate + (r - fy);
			int[] cols;
			if (bw >= 16) {
				int q = (ub - ua) / 4;
				cols = new int[] {ua + q + 1, ub - q - 1};
			} else {
				cols = new int[] {d0 + (doorW - 1) / 2};
			}
			for (int dc : cols) {
				if (dc - 1 <= ua || dc + 1 >= ub) {
					continue;
				}
				for (int u = dc - 1; u <= dc + 1; u++) {
					for (int y = base; y <= base + 1; y++) {
						set(u, y, r, u == dc ? TimberKit.pane(true, y == base + 1 ? DyeColor.PINK : null) : frame());
					}
				}
				set(dc, base + 2, r, s.plaster());
				TimberKit.gable(c, p, false, r, r + 2, dc - 1, dc + 1, base + 2, s.roof(), s.roof(), s.ridge(), s.ridgeSlab(), true, false);
				for (int v = r; v <= r + 2; v++) {
					set(dc, base + 3, v, TimberKit.slab(s.ridgeSlab(), false)); // a thin cap, not a full block and a slab
					set(dc, base + 4, v, AIR);
				}
			}
		}

		// -------------------------------------------------------------- porch

		void porch() {
			int pTop = two ? 4 : 3;
			int beamY = two ? 5 : 4;
			for (int u = ua; u <= ub; u++) {
				if (porchP[u - ua]) {
					for (int y = 1; y <= pTop; y++) {
						set(u, y, fy, frame());
					}
					if (two) {
						for (int y = 6; y < plate; y++) {
							set(u, y, fy, frame());
						}
					}
				}
				set(u, beamY, fy, beam(Direction.Axis.X)); // [depth] fascia beam on the posts
			}
			// rails between the posts, open in front of the door
			for (int u = ua; u <= ub; u++) {
				if (rail(u)) {
					set(u, 1, fy, railState(u, false));
					if (!two && p.hash(u, 2, fy, 3) != 0) {
						set(u, 2, fy, TimberKit.planter(p, u, 2, fy)); // [finish] flower boxes on the rail
					}
				}
				if (two && !porchP[u - ua]) {
					set(u, 6, fy, railState(u, true));
					if (p.hash(u, 7, fy, 3) != 0) {
						set(u, 7, fy, TimberKit.planter(p, u, 7, fy));
					}
				}
			}
			// steps down to the path, as wide as the bay
			int sv = fy > 0 ? fy - 1 : fy;
			for (int u = d0 - 1; u <= d0 + doorW; u++) {
				set(u, 0, sv, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH));
			}
			// [finish] lanterns hang from the fascia over the rails beside the bay (nobody walks under them)
			int lanternY = beamY - 1;
			int lu = d0 - 2;
			while (lu > ua && !rail(lu)) {
				lu--;
			}
			int ru = d0 + doorW + 1;
			while (ru < ub && !rail(ru)) {
				ru++;
			}
			for (int u : new int[] {lu, ru}) {
				if (rail(u)) {
					p.put(c, u, lanternY, fy, TimberKit.lantern(true), CitadelCanvas.HANGING);
				}
			}
			// [finish] a bench and a pot or two on the porch, out of the way of the door
			List<Integer> left = new ArrayList<>();
			List<Integer> right = new ArrayList<>();
			for (int u = ua + 1; u <= d0 - 2; u++) {
				left.add(u);
			}
			for (int u = d0 + doorW + 1; u <= ub - 1; u++) {
				right.add(u);
			}
			if (left.size() >= 2) {
				int k = left.size();
				set(left.get(k - 1), 1, fy + 1, TimberKit.stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
				set(left.get(k - 2), 1, fy + 1, TimberKit.stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
				if (k >= 3) {
					set(left.get(0), 1, fy + 1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
				}
			} else if (left.size() == 1) {
				set(left.get(0), 1, fy + 1, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
			}
			for (int k = 0; k < right.size(); k++) {
				int u = right.get(k);
				if (k == 0 || k == right.size() - 1) {
					set(u, 1, fy + 1, TimberKit.planter(p, u, 1, fy + 1));
				}
			}
		}

		boolean rail(int u) {
			return u >= ua && u <= ub && !porchP[u - ua] && !inBay(u);
		}

		BlockState railState(int u, boolean upper) {
			boolean e = u + 1 <= ub && (porchP[u + 1 - ua] || upper || rail(u + 1));
			boolean wst = u - 1 >= ua && (porchP[u - 1 - ua] || upper || rail(u - 1));
			return TimberKit.fence(Blocks.DARK_OAK_FENCE, false, e, false, wst);
		}

		// -------------------------------------------------------------- hearth

		/**
		 * The hearth on the right gable wall: a brick breast that rises through the roof as a stack, a four-high opening (so the fire
		 * has 3 blocks over it), two campfires and a rug.
		 */
		void hearth() {
			int right = ub;
			for (int v = hv - 1; v <= hv + 2; v++) {
				int top = roofY(v) - 1;
				for (int y = 1; y <= top; y++) {
					set(iu1, y, v, brickAt(p, iu1, y, v));
					if (y <= plate) {
						set(right, y, v, brickAt(p, right, y, v));
					}
				}
			}
			for (int v = hv; v <= hv + 1; v++) {
				for (int y = 1; y <= 4; y++) {
					set(iu1, y, v, AIR);
				}
				set(iu1, 1, v, b(Blocks.CAMPFIRE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
				set(iu1 - 1, 1, v, b(Blocks.CARPET.pick(DyeColor.RED))); // the rug in front of the fire
			}
			int stackTop = Math.max(plate + 1, Math.min(plate + (bd - 1) / 2 + 2, h - 2));
			for (int u = iu1; u <= right; u++) {
				for (int v = hv; v <= hv + 1; v++) {
					for (int y = plate; y <= stackTop; y++) {
						set(u, y, v, brickAt(p, u, y, v));
					}
					set(u, stackTop + 1, v, slab(Blocks.BRICK_SLAB));
				}
			}
		}

		// -------------------------------------------------------------- furniture

		/** Occupancy of one floor of the interior, so furniture never lands on a doorway, a flight or a hearth. */
		final class Room {
			final int y;
			final int v0;
			final boolean[][] occ = new boolean[w][d];

			Room(int y, int v0) {
				this.y = y;
				this.v0 = v0;
			}

			boolean free(int u, int v) {
				return u >= iu0 && u <= iu1 && v >= v0 && v <= iv1 && !occ[u][v];
			}

			void block(int ua2, int va2, int ub2, int vb2) {
				for (int u = Math.max(0, ua2); u <= Math.min(w - 1, ub2); u++) {
					for (int v = Math.max(0, va2); v <= Math.min(d - 1, vb2); v++) {
						occ[u][v] = true;
					}
				}
			}

			void put(int u, int v, BlockState st) {
				Build.this.set(u, y, v, st);
				occ[u][v] = true;
			}
		}

		BlockState item(int code, Direction f) {
			return switch (code) {
				case 0 -> b(Blocks.CRAFTING_TABLE);
				case 1 -> b(Blocks.FURNACE).setValue(BlockStateProperties.HORIZONTAL_FACING, f);
				case 2 -> b(Blocks.SMOKER).setValue(BlockStateProperties.HORIZONTAL_FACING, f);
				case 3 -> b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
				case 4 -> b(Blocks.CHEST).setValue(BlockStateProperties.HORIZONTAL_FACING, f);
				case 5 -> b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
				case 9 -> b(Blocks.BOOKSHELF);
				case 6 -> b(Blocks.LECTERN).setValue(BlockStateProperties.HORIZONTAL_FACING, f);
				default -> b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
			};
		}

		/** Wall cells of a room, back row first, then the front row, then the left and right columns; the last number is the facing. */
		List<int[]> slots(Room r) {
			List<int[]> l = new ArrayList<>();
			for (int u = iu0; u <= iu1; u++) {
				l.add(new int[] {u, iv1, 0});
			}
			for (int u = iu0; u <= iu1; u++) {
				l.add(new int[] {u, r.v0, 1});
			}
			for (int v = r.v0 + 1; v < iv1; v++) {
				l.add(new int[] {iu0, v, 2});
			}
			for (int v = r.v0 + 1; v < iv1; v++) {
				l.add(new int[] {iu1, v, 3});
			}
			return l;
		}

		void fill(Room r, int... items) {
			Direction[] face = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
			List<int[]> sl = slots(r);
			int k = 0;
			for (int code : items) {
				while (k < sl.size() && !r.free(sl.get(k)[0], sl.get(k)[1])) {
					k++;
				}
				if (k >= sl.size()) {
					return;
				}
				int[] sp = sl.get(k++);
				r.put(sp[0], sp[1], item(code, face[sp[2]]));
			}
		}

		void bed(Room r, int uh, int vh, int uf, int vfoot, Direction head) {
			BlockState base = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, head);
			r.put(uh, vh, base.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
			r.put(uf, vfoot, base.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		}

		void furnishGround() {
			Room r = new Room(1, iv0);
			r.block(d0, iv0, d0 + doorW - 1, iv0 + 1); // the way in
			r.block(iu1 - 1, hv - 1, iu1, hv + 2); // the hearth and the floor in front of it
			if (two) {
				r.block(iu0, iv0, iu0 + 1, iv0 + 6); // the flight, its approach and the passage beside it
			}
			int id = iv1 - iv0 + 1;
			int iw = iu1 - iu0 + 1;
			// the dining table, two or three cells long, with chairs
			int len = id >= 7 ? 3 : 2;
			for (int tu = iu1 - 3; tu >= iu0 + 1; tu--) {
				boolean ok = true;
				for (int k = 0; k < len; k++) {
					ok &= r.free(tu, hv + k);
				}
				if (!ok) {
					continue;
				}
				for (int k = 0; k < len; k++) {
					r.put(tu, hv + k, TimberKit.slab(Blocks.DARK_OAK_SLAB, true));
					if (r.free(tu - 1, hv + k)) {
						r.put(tu - 1, hv + k, TimberKit.stair(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
					}
					if (iw >= 8 && r.free(tu + 1, hv + k)) {
						r.put(tu + 1, hv + k, TimberKit.stair(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
					}
				}
				break;
			}
			if (!two) {
				if (r.free(iu0, iv0) && r.free(iu0 + 1, iv0)) {
					bed(r, iu0, iv0, iu0 + 1, iv0, Direction.WEST);
					if (r.free(iu0, iv0 + 1)) {
						r.put(iu0, iv0 + 1, item(4, Direction.EAST)); // a chest at the bed head
					}
				}
			}
			// kitchen along the back wall, then storage
			fill(r, 0, 1, 2, 3, 4, 5, 4, 3, 9, 9, 9, 9);
		}

		void furnishUpper() {
			Room r = new Room(6, vfU + 1);
			r.block(d0, vfU + 1, d0 + doorW - 1, vfU + 2); // the way to the balcony
			r.block(iu0, vfU + 1, iu0 + 1, iv0 + 6); // the flight and the room to leave it
			r.block(iu1, hv - 1, iu1, hv + 2); // the chimney breast
			int iw = iu1 - iu0 + 1;
			int beds = iw >= 10 ? 3 : 2;
			int placed = 0;
			for (int u = iu1 - 1; placed < beds && u >= iu0 + 2; u -= 2) {
				if (r.free(u, iv1) && r.free(u, iv1 - 1)) {
					bed(r, u, iv1, u, iv1 - 1, Direction.SOUTH);
					placed++;
					if (placed < beds && r.free(u - 1, iv1)) {
						r.put(u - 1, iv1, item(4, Direction.NORTH));
					}
				}
			}
			// a study table with two chairs in the middle of the room
			int cu = (iu0 + iu1) / 2;
			int tv = (vfU + 1 + iv1) / 2;
			if (r.free(cu, tv) && r.free(cu + 1, tv) && r.free(cu, tv - 1) && r.free(cu, tv + 1)) {
				r.put(cu, tv, TimberKit.slab(Blocks.DARK_OAK_SLAB, true));
				r.put(cu + 1, tv, TimberKit.slab(Blocks.DARK_OAK_SLAB, true));
				r.put(cu, tv - 1, TimberKit.stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
				r.put(cu, tv + 1, TimberKit.stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
			}
			fill(r, 4, 6, 4, 3, 4, 9, 9, 9, 9);
		}

		/** The walk-up flight along the left wall: five steps from the floor to the upper floor, a fence round the hole. */
		void stair() {
			int su = iu0;
			for (int j = 0; j < 5; j++) {
				int v = iv0 + 1 + j;
				for (int y = 1; y < 1 + j; y++) {
					set(su, y, v, b(Blocks.SPRUCE_PLANKS));
				}
				set(su, 1 + j, v, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
				if (j < 4) {
					set(su, 5, v, AIR); // the hole over the flight (the top step is the floor's own layer)
				}
			}
			boolean[][] rail = new boolean[w][d];
			rail[su][iv0] = true;
			rail[su + 1][iv0] = true;
			for (int v = iv0 + 1; v <= iv0 + 4; v++) {
				rail[su + 1][v] = true;
			}
			for (int u = 0; u < w; u++) {
				for (int v = 0; v < d; v++) {
					if (!rail[u][v]) {
						continue;
					}
					boolean n = v > 0 && rail[u][v - 1];
					boolean e = u + 1 < w && rail[u + 1][v];
					boolean sOut = v + 1 < d && rail[u][v + 1];
					boolean wst = u > 0 && rail[u - 1][v];
					set(u, 6, v, TimberKit.fence(Blocks.SPRUCE_FENCE, n, e, sOut, wst));
				}
			}
		}

		/** An iron chain from the ridge and a lantern over the middle of the room (only over free floor). */
		void chandelier() {
			int feet = two ? 6 : 1;
			int ly = feet + 3;
			int mid = fy + (bd - 1) / 2;
			int ridgeY = plate + (bd - 1) / 2;
			int cu = (iu0 + iu1) / 2;
			int[] cols = iu1 - iu0 >= 8 ? new int[] {iu0 + (iu1 - iu0) / 3, iu1 - (iu1 - iu0) / 3} : new int[] {cu};
			for (int u : cols) {
				// the lantern hangs where the cell below is empty floor in both rooms
				if (!free(u, ly, mid) || !free(u, feet, mid)) {
					continue;
				}
				if (two && !free(u, 1, mid)) {
					continue;
				}
				for (int y = ly + 1; y < ridgeY; y++) {
					p.put(c, u, y, mid, b(Blocks.IRON_CHAIN).setValue(BlockStateProperties.AXIS, Direction.Axis.Y), CitadelCanvas.HANGING);
				}
				p.put(c, u, ly, mid, TimberKit.lantern(true), CitadelCanvas.HANGING);
			}
		}

		// -------------------------------------------------------------- yard

		void yard() {
			backStrip();
			if (fy > 0) {
				frontGarden();
			}
			if (ox > 0) {
				beds(0, ox - 1, fy, fy + bd - 1);
				beds(ox + bw, w - 1, fy, fy + bd - 1);
			}
			if (rear > 0) {
				if (rear >= 4 && w >= 14) {
					shed();
				}
				rearYard();
			}
		}

		/** A woodshed in the back corner: four posts, a slab roof 4 high and a pile of logs (one block high), open on every side. */
		void shed() {
			int u0 = 2;
			int u1 = 5;
			int v0 = d - 4;
			int v1 = d - 2;
			for (int v = v0; v <= v1; v++) {
				for (int u = u0; u <= u1; u++) {
					set(u, 0, v, slab(Blocks.COBBLESTONE_SLAB));
					set(u, 4, v, slab(Blocks.SPRUCE_SLAB));
				}
			}
			for (int u : new int[] {u0, u1}) {
				for (int v : new int[] {v0, v1}) {
					for (int y = 0; y <= 3; y++) {
						set(u, y, v, frame());
					}
				}
			}
			for (int u = u0 + 1; u < u1; u++) {
				set(u, 0, v1, b(Blocks.STRIPPED_SPRUCE_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.X));
			}
			set(u0 + 1, 0, v0, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
		}

		/** Barrels, a woodpile and a composter against the pilasters of the back wall and the sides, one block high. */
		void backStrip() {
			int bv = fy + bd - 1;
			for (int u = ua + 1; u < ub; u++) {
				if (!backP[u - ua]) {
					strip(u, bv);
				}
			}
			for (int v = vf + 1; v < bv; v++) {
				if (!sideP[v - vfU]) {
					strip(ox, v);
					strip(ox + bw - 1, v);
				}
			}
		}

		void strip(int u, int v) {
			if (!free(u, 0, v)) {
				return;
			}
			switch (p.hash(u, 0, v, 5)) {
				case 0 -> set(u, 0, v, b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP));
				case 1 -> set(u, 0, v, b(Blocks.STRIPPED_SPRUCE_LOG).setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
				case 2 -> set(u, 0, v, b(Blocks.POTTED_FERN));
				case 3 -> set(u, 0, v, b(Blocks.FLOWERING_AZALEA));
				default -> {
				}
			}
		}

		/** Two rows in front of the porch: a fence with a gap for the path, flower beds, the path itself. */
		void frontGarden() {
			for (int u = 0; u < w; u++) {
				if (inBay(u)) {
					set(u, 0, 0, slab(Blocks.STONE_BRICK_SLAB));
				} else {
					boolean e = u + 1 < w && !inBay(u + 1);
					boolean wst = u > 0 && !inBay(u - 1);
					set(u, 0, 0, TimberKit.fence(Blocks.DARK_OAK_FENCE, false, e, false, wst));
				}
			}
			// lanterns stand on the posts either side of the gap
			set(d0 - 2, 1, 0, TimberKit.lantern(false));
			set(d0 + doorW + 1, 1, 0, TimberKit.lantern(false));
			for (int u = 0; u < w; u++) {
				if (!inBay(u) && free(u, 0, 1)) {
					BlockState pl = TimberKit.groundPlant(p, u, 0, 1);
					if (pl != null) {
						set(u, 0, 1, pl);
					}
				}
			}
		}

		/** Vegetable beds (wheat, carrots, potatoes) with a stone path between them, in the yards at the sides and the back. */
		void beds(int u0, int u1, int v0, int v1) {
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					if (!free(u, 0, v)) {
						continue;
					}
					int k = u - u0;
					if (u1 - u0 >= 1 && k % 3 == 2) {
						set(u, 0, v, slab(Blocks.STONE_BRICK_SLAB));
					} else if (u1 == u0) {
						BlockState pl = TimberKit.groundPlant(p, u, 0, v);
						if (pl != null) {
							set(u, 0, v, pl);
						}
					} else {
						crop(u, v);
					}
				}
			}
		}

		void crop(int u, int v) {
			set(u, 0, v, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
			Block cropBlock = switch ((v / 2) % 3) {
				case 0 -> Blocks.WHEAT;
				case 1 -> Blocks.CARROTS;
				default -> Blocks.POTATOES;
			};
			set(u, 1, v, cropBlock.defaultBlockState().setValue(BlockStateProperties.AGE_7, 3 + p.hash(u, 1, v, 5)));
		}

		void rearYard() {
			int v0 = fy + bd;
			for (int v = v0; v <= d - 2; v++) {
				for (int u = 0; u < w; u++) {
					if (!free(u, 0, v) || !free(u, 1, v)) {
						continue;
					}
					if ((v - v0) % 3 == 2) {
						set(u, 0, v, slab(Blocks.STONE_BRICK_SLAB));
					} else {
						crop(u, v);
					}
				}
			}
			for (int u = 0; u < w; u++) {
				boolean e = u + 1 < w;
				boolean wst = u > 0;
				set(u, 0, d - 1, TimberKit.fence(Blocks.DARK_OAK_FENCE, false, e, false, wst));
			}
		}
	}
}
