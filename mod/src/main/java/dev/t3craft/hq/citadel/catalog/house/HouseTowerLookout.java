package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.hangingLantern;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.log;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.pane;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.put;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.set;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.slab;
import static dev.t3craft.hq.citadel.catalog.house.HouseTowerKit.stair;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * A lookout tower you walk up: a square stone shaft of three storeys (guard room, watch quarters, and a glazed
 * lookout cabin) with a square spiral stair inside, under a copper hip roof, with a corbelled timber balcony round
 * the cabin.
 *
 * <p>The stair is a flight of 5 steps per storey (one step per cell, first step head-on, the floor above cut over the
 * run) that hugs the walls of the 5x5 core room: storey 0 to 1 climbs the west wall (south-bound), storey 1 to 2 the
 * south wall (east-bound). The ring is {@code k} cells wide (2 when the plot is 15x15 or more, else 1) and every flight
 * is that wide. The cut in each floor is ringed by shelves, barrels and chests (a one-block parapet nobody falls over
 * without meaning to), so no fences are needed and nothing is a sealed pocket.
 *
 * <p>Palette, dark to light, bottom to top: deepslate bricks (cracked / cobbled mix) with polished-deepslate quoins,
 * stone bricks (mossy / cracked) with a chiselled string course, then timber (dark oak or cherry) and glass for the
 * cabin, under oxidised copper edged in cherry with a bright copper crown and a lightning rod.
 *
 * <pre>
 *  u: ox..ox+R-1 plot of the balcony (R = B+4) | u0..u1 shaft (B = I+2, I = 5+2k)
 *  v: 0-1 portico steps | v0=2 front wall | v1 back wall
 *  y: 0 floor | 1-4 guard room | 5 floor | 6-9 watch room | 10 floor+balcony deck | 11-14 cabin | 14 eave | 14-19 roof
 *  stair: flight 1 on the west wall, v k..k+4, y1..5  (door at the north wall, first step from the NW corner)
 *         flight 2 on the south wall, u k..k+4, y6..10 (first step from the SW corner, ends in the cabin)
 * </pre>
 *
 * Size: 13–18 × 13–18 × 21+.
 */
public final class HouseTowerLookout implements Variant {
	private static final int F1 = 5;
	private static final int F2 = 10;
	private static final int EAVE = 14;

	@Override
	public String id() {
		return "house_tower_lookout";
	}

	@Override
	public Family family() {
		return Family.HOUSE;
	}

	@Override
	public int minW() {
		return 13;
	}

	@Override
	public int minD() {
		return 13;
	}

	@Override
	public int minH() {
		return 21;
	}

	@Override
	public int maxW() {
		return 18;
	}

	@Override
	public int maxD() {
		return 18;
	}

	private record Scheme(Block log, Block stripped, Block planks, Block fence, Block door, Block roofStair, Block roofBlock, Block eaveStair,
			Block crownStair, Block crownBlock) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_DOOR,
					Blocks.CUT_COPPER_STAIRS.waxed().oxidized(), Blocks.CUT_COPPER.waxed().oxidized(), Blocks.CHERRY_STAIRS,
					Blocks.CUT_COPPER_STAIRS.waxed().unaffected(), Blocks.CUT_COPPER.waxed().unaffected()),
			new Scheme(Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.CHERRY_PLANKS, Blocks.CHERRY_FENCE, Blocks.CHERRY_DOOR,
					Blocks.CUT_COPPER_STAIRS.waxed().oxidized(), Blocks.CUT_COPPER.waxed().oxidized(), Blocks.DARK_OAK_STAIRS,
					Blocks.CUT_COPPER_STAIRS.waxed().unaffected(), Blocks.CUT_COPPER.waxed().unaffected()),
	};

	/**
	 * Geometry of one build. Interior cells are {@code (a, b)}, 0..in-1, a west to east, b front to back; the shaft
	 * walls are the rectangle u0..u1 x v0..v1; the balcony ring is the two rows of cells round it.
	 */
	private record G(Plot p, Scheme s, int k, int in, int bw, int rw, int ox, int u0, int u1, int v0, int v1, int m, int c) {
		int iu(int a) {
			return u0 + 1 + a;
		}

		int iv(int bb) {
			return v0 + 1 + bb;
		}

		/** Chebyshev distance to the shaft rectangle (0 inside or on the wall). */
		int dist(int u, int v) {
			int du = u < u0 ? u0 - u : Math.max(0, u - u1);
			int dv = v < v0 ? v0 - v : Math.max(0, v - v1);
			return Math.max(du, dv);
		}

		boolean corner(int u, int v) {
			return (u < u0 || u > u1) && (v < v0 || v > v1);
		}

		/** The direction from an outside cell towards the shaft. */
		Direction toward(int u, int v) {
			if (u < u0) {
				return Direction.EAST;
			}
			if (u > u1) {
				return Direction.WEST;
			}
			return v < v0 ? Direction.SOUTH : Direction.NORTH;
		}

		/** Position along the face of an outside, non-corner cell. */
		int along(int u, int v) {
			Direction t = toward(u, v);
			return t == Direction.EAST || t == Direction.WEST ? v - v0 : u - u0;
		}

		boolean outer(int u, int v) {
			return u >= ox && u < ox + rw && v >= 0 && v < rw && dist(u, v) == 2;
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x100C_0A11L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		int k = p.w() >= 15 && p.d() >= 15 ? 2 : 1;
		int in = 5 + 2 * k;
		int bw = in + 2;
		int rw = bw + 4;
		int ox = (p.w() - rw) / 2;
		int u0 = ox + 2;
		G g = new G(p, s, k, in, bw, rw, ox, u0, u0 + bw - 1, 2, 2 + bw - 1, (bw - 1) / 2, (in - 1) / 2);
		foundation(c, g);
		walls(c, g);
		floors(c, g);
		stairs(c, g);
		balcony(c, g);
		roof(c, g);
		furnish(c, g);
		yard(c, g);
	}

	// ------------------------------------------------------------------ materials

	private static BlockState deepslate(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 9)) {
			case 0 -> b(Blocks.CRACKED_DEEPSLATE_BRICKS);
			case 1 -> b(Blocks.COBBLED_DEEPSLATE);
			default -> b(Blocks.DEEPSLATE_BRICKS);
		};
	}

	private static BlockState stone(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 12)) {
			case 0 -> b(Blocks.MOSSY_STONE_BRICKS);
			case 1 -> b(Blocks.CRACKED_STONE_BRICKS);
			default -> b(Blocks.STONE_BRICKS);
		};
	}

	private static Block pinkPane() {
		return Blocks.STAINED_GLASS_PANE.pick(DyeColor.PINK);
	}

	private static BlockState door(G g, Direction facing, boolean lower) {
		return g.s.door().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
	}

	private static BlockState facing(Block block, Direction dir) {
		return b(block).setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
	}

	private static BlockState barrel() {
		return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
	}

	/** An interior cell write: {@code (a, b)} are interior coordinates. */
	private static void fi(CitadelCanvas c, G g, int a, int y, int bb, BlockState st) {
		set(c, g.p, g.iu(a), y, g.iv(bb), st);
	}

	// ------------------------------------------------------------------ foundation

	/** y0: the shaft's base course and the ground floor, a talus of stairs round the foot, pilasters on every face. */
	private void foundation(CitadelCanvas c, G g) {
		for (int u = g.ox; u < g.ox + g.rw; u++) {
			for (int v = 0; v < g.rw; v++) {
				int d = g.dist(u, v);
				if (d == 0) {
					boolean wallCell = u == g.u0 || u == g.u1 || v == g.v0 || v == g.v1;
					if (wallCell) {
						set(c, g.p, u, 0, v, deepslate(g.p, u, 0, v));
					} else {
						int a = u - g.u0 - 1;
						int bb = v - g.v0 - 1;
						set(c, g.p, u, 0, v, ((a + bb) & 1) == 0 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.POLISHED_DEEPSLATE));
					}
				} else if (d == 1) {
					if (g.corner(u, v)) {
						set(c, g.p, u, 0, v, b(Blocks.POLISHED_DEEPSLATE));
						continue;
					}
					int t = g.along(u, v);
					boolean entry = g.toward(u, v) == Direction.SOUTH && Math.abs(t - g.m) <= 1;
					if (entry) {
						continue;
					}
					if (Math.abs(t - g.m) == 2) {
						// [depth] a pilaster: a stone rib that stands one block proud of the wall up to the balcony
						for (int y = 0; y <= 8; y++) {
							set(c, g.p, u, y, v, y <= 1 ? deepslate(g.p, u, y, v) : stone(g.p, u, y, v));
						}
						continue;
					}
					set(c, g.p, u, 0, v, stair(Blocks.DEEPSLATE_BRICK_STAIRS, g.toward(u, v), false));
				}
			}
		}
	}

	// ------------------------------------------------------------------ walls

	private static boolean windowT(G g, int t) {
		return t >= 1 && t <= g.m - 3 || t >= g.m + 3 && t <= g.bw - 2 || t == g.m;
	}

	private void walls(CitadelCanvas c, G g) {
		for (int u = g.u0; u <= g.u1; u++) {
			for (int v = g.v0; v <= g.v1; v++) {
				boolean n = v == g.v0;
				boolean sh = v == g.v1;
				boolean w = u == g.u0;
				boolean e = u == g.u1;
				if (!(n || sh || w || e)) {
					continue;
				}
				boolean corner = (n || sh) && (w || e);
				boolean alongU = n || sh;
				int t = alongU ? u - g.u0 : v - g.v0;
				for (int y = 1; y <= EAVE; y++) {
					set(c, g.p, u, y, v, wallCell(g, u, y, v, corner, alongU, t, n, e, w));
				}
				if (!corner && windowT(g, t)) {
					// [depth] a sill sticking out under each slit window
					Direction out = n ? Direction.NORTH : sh ? Direction.SOUTH : w ? Direction.WEST : Direction.EAST;
					int su = u + out.getStepX();
					int sv = v + out.getStepZ();
					for (int wy : new int[] {2, 7}) {
						set(c, g.p, su, wy - 1, sv, slab(Blocks.POLISHED_DEEPSLATE_SLAB, true));
					}
				}
			}
		}
	}

	private BlockState wallCell(G g, int u, int y, int v, boolean corner, boolean alongU, int t, boolean n, boolean e, boolean w) {
		Scheme s = g.s;
		if (corner) {
			if (y <= 9) {
				return y % 2 == 1 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_BRICKS); // dark quoins up the corners
			}
			return y == 10 ? log(s.stripped(), Direction.Axis.Y) : log(s.log(), Direction.Axis.Y);
		}
		Direction.Axis ax = alongU ? Direction.Axis.X : Direction.Axis.Z;
		if (y == 10) {
			return log(s.stripped(), ax); // the beam under the lookout floor
		}
		if (y >= 11) {
			return cabinCell(g, u, y, v, alongU, t, n, e, w, ax);
		}
		if (n && t == g.m && (y == 1 || y == 2)) {
			return door(g, Direction.NORTH, y == 1);
		}
		if (n && Math.abs(t - g.m) == 1 && y <= 2) {
			return log(s.log(), Direction.Axis.Y); // [depth] timber jambs
		}
		if (n && Math.abs(t - g.m) <= 1 && y == 3) {
			return log(s.stripped(), Direction.Axis.X); // lintel
		}
		if (windowT(g, t) && (y == 2 || y == 3 || y == 7 || y == 8)) {
			return pane(g.p.hash(u, y, v, 5) == 0 ? pinkPane() : Blocks.GLASS_PANE, alongU);
		}
		if (y <= 2) {
			return deepslate(g.p, u, y, v);
		}
		if (y == F1) {
			return b(Blocks.CHISELED_STONE_BRICKS);
		}
		if (y == 9) {
			return b(Blocks.POLISHED_DEEPSLATE);
		}
		return stone(g.p, u, y, v);
	}

	private BlockState cabinCell(G g, int u, int y, int v, boolean alongU, int t, boolean n, boolean e, boolean w, Direction.Axis ax) {
		Scheme s = g.s;
		if (t == g.m - 1 || t == g.m + 1) {
			return log(s.log(), Direction.Axis.Y);
		}
		if (y == EAVE) {
			return log(s.stripped(), ax);
		}
		if (t == g.m && (n || e || w)) {
			if (y == 11 || y == 12) {
				return door(g, n ? Direction.NORTH : e ? Direction.EAST : Direction.WEST, y == 11);
			}
			return b(s.planks());
		}
		if (y == 11) {
			return b(Blocks.STONE_BRICKS);
		}
		return pane(g.p.hash(u, y, v, 4) == 0 ? pinkPane() : Blocks.GLASS_PANE, alongU);
	}

	// ------------------------------------------------------------------ floors and stairs

	private static boolean hole0(G g, int a, int bb) {
		return a < g.k && bb >= g.k && bb <= g.k + 3;
	}

	private static boolean flight0End(G g, int a, int bb) {
		return a < g.k && bb == g.k + 4;
	}

	private static boolean hole1(G g, int a, int bb) {
		return bb >= g.in - g.k && a >= g.k && a <= g.k + 3;
	}

	private static boolean flight1End(G g, int a, int bb) {
		return bb >= g.in - g.k && a == g.k + 4;
	}

	private void floors(CitadelCanvas c, G g) {
		for (int a = 0; a < g.in; a++) {
			for (int bb = 0; bb < g.in; bb++) {
				if (!hole0(g, a, bb) && !flight0End(g, a, bb)) {
					fi(c, g, a, F1, bb, b(Blocks.SPRUCE_PLANKS));
				}
				if (!hole1(g, a, bb) && !flight1End(g, a, bb)) {
					fi(c, g, a, F2, bb, b(g.s.planks()));
				}
			}
		}
	}

	/** Two masonry flights of five steps; the step cells are solid beneath, so nobody walks under a low ceiling. */
	private void stairs(CitadelCanvas c, G g) {
		for (int j = 1; j <= 5; j++) {
			for (int lane = 0; lane < g.k; lane++) {
				// flight 1: west wall, going south, from the ground floor to y5
				int u0 = g.iu(lane);
				int v0 = g.iv(g.k + j - 1);
				set(c, g.p, u0, j, v0, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
				for (int y = 1; y < j; y++) {
					set(c, g.p, u0, y, v0, stone(g.p, u0, y, v0));
				}
				// flight 2: south wall, going east, from y5 to y10
				int u1 = g.iu(g.k + j - 1);
				int v1 = g.iv(g.in - g.k + lane);
				set(c, g.p, u1, F1 + j, v1, stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST, false));
				for (int y = F1 + 1; y < F1 + j; y++) {
					set(c, g.p, u1, y, v1, stone(g.p, u1, y, v1));
				}
			}
		}
	}

	// ------------------------------------------------------------------ balcony

	private void balcony(CitadelCanvas c, G g) {
		for (int u = g.ox; u < g.ox + g.rw; u++) {
			for (int v = 0; v < g.rw; v++) {
				int d = g.dist(u, v);
				if (d < 1 || d > 2) {
					continue;
				}
				boolean corner = g.corner(u, v);
				Direction to = g.toward(u, v);
				int t = g.along(u, v);
				boolean entry = !corner && to == Direction.SOUTH && Math.abs(t - g.m) <= 1;
				// [depth] the corbels under the deck: stairs against the wall, a top slab on the outer edge
				if (entry) {
					set(c, g.p, u, 9, v, log(g.s.stripped(), Direction.Axis.X));
				} else if (d == 1) {
					set(c, g.p, u, 9, v, stair(Blocks.POLISHED_DEEPSLATE_STAIRS, to, true));
				} else {
					set(c, g.p, u, 9, v, slab(Blocks.POLISHED_DEEPSLATE_SLAB, true));
				}
				if (d == 2) {
					Direction.Axis ax = to == Direction.NORTH || to == Direction.SOUTH ? Direction.Axis.X : Direction.Axis.Z;
					set(c, g.p, u, F2, v, log(g.s.stripped(), ax));
					boolean post = corner || Math.abs(t - g.m) == 2;
					if (post) {
						for (int y = 11; y <= 13; y++) {
							set(c, g.p, u, y, v, log(g.s.log(), Direction.Axis.Y));
						}
					} else {
						set(c, g.p, u, 11, v, fence(g.s.fence(), g.outer(u, v - 1), g.outer(u + 1, v), g.outer(u, v + 1), g.outer(u - 1, v)));
					}
				} else {
					set(c, g.p, u, F2, v, b(g.s.planks()));
				}
			}
		}
		// the spyglasses: a copper tube on the rail, one east and one south; lanterns hang over the front and west rails
		int eu = g.ox + g.rw - 1;
		int ev = g.v0 + g.m;
		set(c, g.p, eu, 12, ev, b(Blocks.LIGHTNING_ROD.waxed().unaffected()).setValue(BlockStateProperties.FACING, Direction.EAST));
		int su = g.u0 + g.m;
		int sv = g.rw - 1;
		set(c, g.p, su, 12, sv, b(Blocks.LIGHTNING_ROD.waxed().unaffected()).setValue(BlockStateProperties.FACING, Direction.SOUTH));
		put(c, g.p, g.u0 + g.m, 13, 0, hangingLantern(), CitadelCanvas.HANGING);
		put(c, g.p, g.ox, 13, g.v0 + g.m, hangingLantern(), CitadelCanvas.HANGING);
		// portico lanterns under the front beam, either side of the steps
		put(c, g.p, g.u0 + g.m - 1, 8, 1, hangingLantern(), CitadelCanvas.HANGING);
		put(c, g.p, g.u0 + g.m + 1, 8, 1, hangingLantern(), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ roof

	/** A hip roof: stair rims round solid layers, one block in per layer; the eave ring rides on the balcony posts. */
	private void roof(CitadelCanvas c, G g) {
		int size = g.rw;
		for (int l = 0; size >= 3; l++, size -= 2) {
			int y = EAVE + l;
			int umin = g.ox + l;
			int umax = g.ox + g.rw - 1 - l;
			int vmin = l;
			int vmax = g.rw - 1 - l;
			boolean crown = size <= 5;
			for (int u = umin; u <= umax; u++) {
				for (int v = vmin; v <= vmax; v++) {
					if (l == 0 && g.dist(u, v) == 0) {
						continue; // the cabin stays open up to its beam
					}
					boolean cu = u == umin || u == umax;
					boolean cv = v == vmin || v == vmax;
					if (!cu && !cv) {
						set(c, g.p, u, y, v, b(crown ? g.s.crownBlock() : g.s.roofBlock()));
						continue;
					}
					Block sb = l == 0 ? g.s.eaveStair() : crown ? g.s.crownStair() : g.s.roofStair();
					Direction fx = u == umin ? Direction.EAST : Direction.WEST;
					Direction fz = v == vmin ? Direction.SOUTH : Direction.NORTH;
					if (cu && cv) {
						StairsShape shape = fz == fx.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
						set(c, g.p, u, y, v, stair(sb, fx, false).setValue(BlockStateProperties.STAIRS_SHAPE, shape));
					} else {
						set(c, g.p, u, y, v, stair(sb, cu ? fx : fz, false));
					}
				}
			}
			if (size == 3) {
				set(c, g.p, (umin + umax) / 2, y + 1, (vmin + vmax) / 2, b(Blocks.LIGHTNING_ROD.waxed().unaffected()));
			}
		}
	}

	// ------------------------------------------------------------------ furniture

	private static BlockState bed(Direction head, boolean isHead) {
		return b(Blocks.PINK_BED).setValue(BlockStateProperties.HORIZONTAL_FACING, head)
				.setValue(BlockStateProperties.BED_PART, isHead ? BedPart.HEAD : BedPart.FOOT);
	}

	private void furnish(CitadelCanvas c, G g) {
		int in = g.in;
		int k = g.k;
		int cc = g.c;
		// ---- ground floor, the guard room: stores along the east wall, a workbench row on the south wall, a bench by the door
		fi(c, g, in - 1, 1, 1, barrel());
		fi(c, g, in - 1, 1, 2, barrel());
		fi(c, g, in - 1, 1, 3, facing(Blocks.CHEST, Direction.WEST));
		fi(c, g, cc - 1, 1, in - 1, b(Blocks.CRAFTING_TABLE));
		fi(c, g, cc, 1, in - 1, facing(Blocks.FURNACE, Direction.NORTH));
		fi(c, g, cc + 1, 1, in - 1, b(Blocks.SMITHING_TABLE));
		fi(c, g, cc + 2, 1, 0, stair(g.s.eaveStair(), Direction.NORTH, false));
		fi(c, g, cc + 3, 1, 0, barrel());
		put(c, g.p, g.iu(cc), 4, g.iv(cc), hangingLantern(), CitadelCanvas.HANGING);

		// ---- first floor, the watch room: a wall of shelves, a reading desk, two bunks, stores; a parapet round the stair well
		for (int a = cc - 2; a <= cc + 2; a++) {
			fi(c, g, a, F1 + 1, 0, b(Blocks.BOOKSHELF));
		}
		fi(c, g, cc, F1 + 1, 1, facing(Blocks.LECTERN, Direction.SOUTH).setValue(BlockStateProperties.HAS_BOOK, true));
		for (int bb : new int[] {2, 4}) {
			fi(c, g, in - 2, F1 + 1, bb, bed(Direction.EAST, false));
			fi(c, g, in - 1, F1 + 1, bb, bed(Direction.EAST, true));
		}
		fi(c, g, in - 1, F1 + 1, 1, barrel());
		fi(c, g, in - 1, F1 + 1, 3, facing(Blocks.CHEST, Direction.WEST));
		fi(c, g, in - 1, F1 + 1, 5, barrel());
		for (int bb = k; bb <= k + 3; bb++) {
			fi(c, g, k, F1 + 1, bb, b(Blocks.BOOKSHELF));
		}
		for (int a = 0; a < k; a++) {
			fi(c, g, a, F1 + 1, k - 1, barrel());
		}
		put(c, g.p, g.iu(cc), 9, g.iv(cc), hangingLantern(), CitadelCanvas.HANGING);

		// ---- the lookout cabin: a map table in the middle, benches under the windows, a parapet round the stair well
		fi(c, g, cc, F2 + 1, cc, b(Blocks.CARTOGRAPHY_TABLE));
		for (int a = k; a <= k + 3; a++) {
			fi(c, g, a, F2 + 1, in - k - 1, a % 2 == 0 ? barrel() : facing(Blocks.CHEST, Direction.NORTH));
		}
		for (int a = 0; a < k; a++) {
			for (int bb = in - k; bb < in; bb++) {
				fi(c, g, a, F2 + 1, bb, barrel());
			}
		}
		for (int a = 1; a <= in - 2; a++) {
			if (a != cc) {
				fi(c, g, a, F2 + 1, 0, stair(g.s.eaveStair(), Direction.NORTH, false));
			}
		}
		for (int bb = 1; bb < cc; bb++) {
			fi(c, g, in - 1, F2 + 1, bb, stair(g.s.eaveStair(), Direction.EAST, false));
			fi(c, g, 0, F2 + 1, bb, stair(g.s.eaveStair(), Direction.WEST, false));
		}
		put(c, g.p, g.iu(cc - 1), 14, g.iv(cc + 1), hangingLantern(), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ yard

	private void yard(CitadelCanvas c, G g) {
		int eu = g.u0 + g.m;
		// the portico steps: a polished slab, then three stairs head-on to the door
		for (int dx = -1; dx <= 1; dx++) {
			set(c, g.p, eu + dx, 0, 0, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
			set(c, g.p, eu + dx, 0, 1, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH, false));
		}
		// planters either side of the steps and shrubs at the four corners
		for (int side = -1; side <= 1; side += 2) {
			set(c, g.p, eu + side * 3, 0, 0, b(Blocks.MOSS_BLOCK));
			set(c, g.p, eu + side * 3, 1, 0, side < 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.AZALEA));
		}
		int[][] corners = {{g.ox, 0}, {g.ox + g.rw - 1, 0}, {g.ox, g.rw - 1}, {g.ox + g.rw - 1, g.rw - 1}};
		for (int[] q : corners) {
			set(c, g.p, q[0], 0, q[1], b(Blocks.MOSS_BLOCK));
			set(c, g.p, q[0], 1, q[1], g.p.hash(q[0], 1, q[1], 2) == 0 ? b(Blocks.FLOWERING_AZALEA) : b(Blocks.FERN));
		}
		// a barrel and a woodpile by the back wall
		set(c, g.p, g.u0 + 1, 0, g.v1 + 2, barrel());
		set(c, g.p, g.u1 - 1, 0, g.v1 + 2, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
	}
}
