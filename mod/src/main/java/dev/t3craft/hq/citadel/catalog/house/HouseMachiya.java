package dev.t3craft.hq.citadel.catalog.house;

import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.fence;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.glassPane;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.lantern;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.log;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.paneU;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.paneV;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.stair;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.trapdoor;
import static dev.t3craft.hq.citadel.catalog.house.JapaneseKit.wall;

import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.catalog.Family;
import dev.t3craft.hq.citadel.catalog.Plot;
import dev.t3craft.hq.citadel.catalog.Variant;
import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A Kyoto machiya: a narrow, deep town house that is lived in, not just looked at. Two storeys with 4 free
 * blocks on each, so a person walks everywhere and furniture one block high still leaves a roof of head room.
 *
 * <pre>
 *  plan (front = v 0, aisle on the left, flips by seed)         section through the aisle
 *  v0   porch, dog-fence, posts, canopy (hisashi) outer row     y10  plate, roof from y11 (tiles, 2:1)
 *  v1   koshi lattice, noren banners, canopy inner row          y6-9 upper floor: kitchen, table, beds
 *  v2   front wall: shoji panes, double door                    y5   floor beams
 *  v3.. mise (shop) beside the toori-niwa (earth aisle, 2-4     y1-4 mise, workshop, toori-niwa (4 free)
 *       wide, runs the whole depth), then a workshop            y0   plinth, mud, planks
 *  vb-3 switchback stair (kaidan) against the back wall, 2 wide, steps of 1, landing, rail round the hole
 *  vb   back wall with a 2-wide opening on the aisle
 *  vb+1.. tsubo-niwa: gravel, stepping stones, ishidoro, a small sakura, bamboo fence
 * </pre>
 *
 * Walkability (the gallery's {@code WalkAudit}): nothing is sealed and nothing is low. The ground floor
 * ceiling is at y5 and the upper one is the roof underside, so a 1-high counter still leaves 3 free blocks;
 * the stair cuts its own hole and fences it; no surface lies under an overhang (the canopy has no roof lip
 * over it, the sakura crown is a single thick layer), so there are no standable pockets nobody can reach.
 *
 * Size: 9–11 × 14–16 × 17+ (it grows with the plot, up to the plot's own maximum).
 */
public final class HouseMachiya implements Variant {
	/** Floor between the storeys (beam layer). The upper floor is stood on at y6. */
	private static final int F2 = 5;
	private static final int PLATE = 10;
	private static final int ROOF0 = 11;

	@Override
	public String id() {
		return "house_machiya";
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
		return 14;
	}

	@Override
	public int minH() {
		return 17;
	}

	@Override
	public int maxW() {
		return 11;
	}

	@Override
	public int maxD() {
		return 16;
	}

	/** [palette] per seed: noren colour and door wood. */
	private record Scheme(DyeColor noren, Block door) {
	}

	private static final Scheme[] SCHEMES = {
			new Scheme(DyeColor.BLUE, Blocks.DARK_OAK_DOOR),
			new Scheme(DyeColor.RED, Blocks.CHERRY_DOOR),
			new Scheme(DyeColor.CYAN, Blocks.DARK_OAK_DOOR),
	};

	/** Fixed lines of the plan, all in local coordinates of an unmirrored house (aisle on the low-u side). */
	private record Geo(int w, int d, int h, int u1, int u2, int a0, int a1, int e0, int e1, int r0, int r1, int vf, int vb, int rf, int rb, int vs,
			int x1, int x2, int step) {
		static Geo of(Plot p) {
			int w = p.w();
			int d = p.d();
			int h = p.h();
			int wi = w - 4;
			int ta = wi <= 6 ? 2 : wi <= 9 ? 3 : 4;
			int a0 = 2;
			int a1 = a0 + ta - 1;
			int e0 = a0 + (ta - 2) / 2;
			int r1 = w - 3;
			int vb = d - 5 - Math.max(0, (d - 14) / 3);
			int rf = 2;
			int rb = vb + 1;
			int kmax = (rb - rf) / 2;
			int step = 2;
			while (step < 8 && ROOF0 + kmax / step + 2 > h - 1) {
				step++;
			}
			return new Geo(w, d, h, 1, w - 2, a0, a1, e0, e0 + 1, a1 + 1, r1, 2, vb, rf, rb, vb - 3, r1 - 1, r1, step);
		}

		int rowK(int v) {
			return Math.min(v - rf, rb - v);
		}

		int kmax() {
			return (rb - rf) / 2;
		}

		int roofY(int v) {
			return ROOF0 + rowK(v) / step;
		}
	}

	/** Writes through the plot, flipping the house left to right when {@code mirror} (states are mirrored too). */
	private static final class Pen {
		final CitadelCanvas c;
		final Plot p;
		final boolean mirror;
		final int w;

		Pen(CitadelCanvas c, Plot p, boolean mirror) {
			this.c = c;
			this.p = p;
			this.mirror = mirror;
			this.w = p.w();
		}

		int mu(int u) {
			return mirror ? w - 1 - u : u;
		}

		void set(int u, int y, int v, BlockState s) {
			put(u, y, v, s, CitadelCanvas.SOLID);
		}

		void put(int u, int y, int v, BlockState s, int layer) {
			p.put(c, mu(u), y, v, mirror ? s.mirror(Mirror.FRONT_BACK) : s, layer);
		}

		void fill(int u1, int y1, int v1, int u2, int y2, int v2, BlockState s) {
			for (int y = y1; y <= y2; y++) {
				for (int v = v1; v <= v2; v++) {
					for (int u = u1; u <= u2; u++) {
						set(u, y, v, s);
					}
				}
			}
		}
	}

	@Override
	public void build(CitadelCanvas c, Plot p) {
		Random r = p.rng(0x4D41_4348_4959L);
		Scheme s = SCHEMES[r.nextInt(SCHEMES.length)];
		Pen g = new Pen(c, p, r.nextBoolean());
		Geo q = Geo.of(p);

		flanks(g, p, q);
		footing(g, p, q);
		frontGround(g, p, q, s);
		canopy(g, q);
		frontUpper(g, p, q);
		sides(g, p, q);
		back(g, p, q);
		floors(g, q);
		stairs(g, q);
		roof(g, p, q);
		udatsu(g, q);
		vent(g, q);
		interiorGround(g, p, q);
		interiorUpper(g, p, q);
		garden(g, p, q, r);
	}

	// ------------------------------------------------------------------ materials

	/** [palette] a lighter stone footing than the dark timber and plaster above it, so the planes read apart. */
	private static BlockState stone(Plot p, int u, int y, int v) {
		return switch (p.hash(u, y, v, 6)) {
			case 0 -> b(Blocks.CRACKED_STONE_BRICKS);
			case 1 -> b(Blocks.POLISHED_ANDESITE);
			default -> b(Blocks.STONE_BRICKS);
		};
	}

	private static BlockState plaster() {
		return b(Blocks.CALCITE);
	}

	private static BlockState beam(Direction.Axis axis) {
		return log(Blocks.DARK_OAK_LOG, axis);
	}

	private static BlockState shutter(Direction facing) {
		return trapdoor(Blocks.DARK_OAK_TRAPDOOR, facing, true, Half.BOTTOM);
	}

	private static boolean isPillar(Geo q, int u) {
		return u == q.u1() || u == q.u2() || u == q.e0() - 1 || u == q.e1() + 1;
	}

	private static boolean entrance(Geo q, int u) {
		return u == q.e0() || u == q.e1();
	}

	/** Post positions between {@code a} and {@code bb}: both ends and evenly spaced, about every 4 cells. */
	private static int[] posts(int a, int bb) {
		int span = bb - a;
		int nb = Math.max(1, (int) Math.round(span / 4.5));
		int[] out = new int[nb + 1];
		for (int k = 0; k <= nb; k++) {
			out[k] = a + (int) Math.round(k * (double) span / nb);
		}
		return out;
	}

	/** Per cell from {@code a} to {@code bb}: 0 wall, 1 post, 2 window. */
	private static int[] kinds(int a, int bb) {
		int[] kind = new int[bb - a + 1];
		int[] ps = posts(a, bb);
		for (int pp : ps) {
			kind[pp - a] = 1;
		}
		for (int k = 0; k + 1 < ps.length; k++) {
			int s = ps[k] + 1;
			int e = ps[k + 1] - 1;
			if (e - s + 1 >= 5) {
				s++;
				e--;
			}
			for (int i = s; i <= e; i++) {
				if (kind[i - a] == 0) {
					kind[i - a] = 2;
				}
			}
		}
		return kind;
	}

	// ------------------------------------------------------------------ flanks and footing (y0)

	/** A low deepslate wall along both sides, with bamboo here and there: the plot's edge, and the garden's wall. */
	private static void flanks(Pen g, Plot p, Geo q) {
		for (int v = 0; v <= q.d() - 1; v++) {
			for (int u : new int[] {0, q.w() - 1}) {
				boolean prev = v - 1 >= 0;
				boolean next = v + 1 <= q.d() - 1;
				g.set(u, 0, v, wall(Blocks.COBBLED_DEEPSLATE_WALL, true, prev, false, next, false));
				if (v >= 4 && p.hash(u, 1, v, 4) == 0) {
					g.set(u, 1, v, b(Blocks.BAMBOO));
					g.set(u, 2, v, b(Blocks.BAMBOO));
				}
			}
		}
	}

	private static void footing(Pen g, Plot p, Geo q) {
		for (int v = 0; v <= q.vb(); v++) {
			for (int u = q.u1(); u <= q.u2(); u++) {
				g.set(u, 0, v, floorBlock(p, q, u, v));
			}
		}
	}

	private static BlockState floorBlock(Plot p, Geo q, int u, int v) {
		boolean edge = u == q.u1() || u == q.u2() || v == q.vf() || v == q.vb();
		if (v == 0) {
			return stone(p, u, 0, v);
		}
		if (v == 1) {
			// [palette] porch paving
			return p.hash(u, 0, v, 2) == 0 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.STONE_BRICKS);
		}
		if (edge) {
			return stone(p, u, 0, v);
		}
		if (u <= q.a1()) {
			// the toori-niwa: packed earth, with stepping stones down the middle
			if (v % 2 == 0 && (u == q.e0() || u == q.e1())) {
				return b(Blocks.POLISHED_ANDESITE);
			}
			return switch (p.hash(u, 0, v, 5)) {
				case 0 -> b(Blocks.MUD_BRICKS);
				case 1 -> b(Blocks.COARSE_DIRT);
				default -> b(Blocks.PACKED_MUD);
			};
		}
		if (u == q.r0()) {
			return b(Blocks.DARK_OAK_PLANKS); // the step line between earth and boards
		}
		return ((u + v) & 1) == 0 ? b(Blocks.SPRUCE_PLANKS) : b(Blocks.BAMBOO_MOSAIC);
	}

	// ------------------------------------------------------------------ front (v0..v2)

	private static BlockState door(Scheme s, boolean leftHinge, boolean lower) {
		return s.door().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
				.setValue(BlockStateProperties.DOOR_HINGE, leftHinge ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
	}

	private static void frontGround(Pen g, Plot p, Geo q, Scheme s) {
		int vf = q.vf();
		BlockState paper = paneU(glassPane(DyeColor.WHITE));
		BlockState banner = b(Blocks.WALL_BANNER.pick(s.noren())).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
		for (int u = q.u1(); u <= q.u2(); u++) {
			// wall plane (v2): a double door in the aisle, shoji panes over plaster elsewhere
			if (entrance(q, u)) {
				boolean left = u == q.e0();
				g.set(u, 1, vf, door(s, left, true));
				g.set(u, 2, vf, door(s, left, false));
				g.set(u, 4, vf, beam(Direction.Axis.X)); // lintel; y3 stays open so the doorway keeps its head room
			} else if (isPillar(q, u)) {
				for (int y = 1; y <= 4; y++) {
					g.set(u, y, vf, beam(Direction.Axis.Y));
				}
			} else {
				g.set(u, 1, vf, stone(p, u, 1, vf));
				g.set(u, 2, vf, paper);
				g.set(u, 3, vf, paper);
				g.set(u, 4, vf, plaster());
			}
			// lattice plane (v1): [depth] koshi fins one block proud of the wall, a kick plate, a header
			if (entrance(q, u)) {
				g.put(u, 4, 1, banner, CitadelCanvas.DECOR); // [finish] the split noren: one flap each side of the slit
			} else if (isPillar(q, u)) {
				for (int y = 1; y <= 4; y++) {
					g.set(u, y, 1, beam(Direction.Axis.Y));
				}
			} else {
				Direction side = (u - q.u1()) % 2 == 0 ? Direction.EAST : Direction.WEST;
				// a fence rail at the foot: its post is in the middle of the cell, so nobody could stand in the gap
				boolean fl = u - 1 >= q.u1() && !entrance(q, u - 1);
				boolean fr = u + 1 <= q.u2() && !entrance(q, u + 1);
				g.set(u, 1, 1, fence(Blocks.DARK_OAK_FENCE, false, fr, false, fl));
				g.set(u, 2, 1, shutter(side));
				g.set(u, 3, 1, shutter(side));
				g.set(u, 4, 1, beam(Direction.Axis.X));
			}
			// porch plane (v0): posts and a low bamboo dog-fence (inu-yarai), the doorway left open
			if (entrance(q, u)) {
				g.set(u, 0, 0, stair(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH)); // the step up from the street
			} else if (isPillar(q, u)) {
				for (int y = 1; y <= 3; y++) {
					g.set(u, y, 0, beam(Direction.Axis.Y));
				}
			} else {
				boolean l = u - 1 >= q.u1() && !entrance(q, u - 1);
				boolean rr = u + 1 <= q.u2() && !entrance(q, u + 1);
				g.set(u, 1, 0, fence(Blocks.BAMBOO_FENCE, false, rr, false, l));
				// [finish] a lantern beside the entrance and a beam tail under the canopy every other cell
				if (u == q.e0() - 2 || u == q.e1() + 2) {
					g.put(u, 3, 0, lantern(true), CitadelCanvas.HANGING);
				} else if ((u - q.u1()) % 2 == 0) {
					g.set(u, 3, 0, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
				}
			}
		}
	}

	/**
	 * [depth] the hisashi: a two-step lean-to of tiles, one row at v0 over the porch and one at v1 over the
	 * lattice. Nothing overhangs it (the main roof starts flush at v2), so no surface is left under a roof
	 * where nobody could reach it.
	 */
	private static void canopy(Pen g, Geo q) {
		int[] kind = kinds(q.u1(), q.u2());
		for (int u = 0; u < q.w(); u++) {
			g.set(u, 4, 0, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH));
			boolean post = u >= q.u1() && u <= q.u2() && kind[u - q.u1()] == 1;
			if (post) {
				for (int y = 5; y <= 9; y++) {
					g.set(u, y, 1, beam(Direction.Axis.Y)); // [depth] posts stand proud of the upper floor
				}
			} else {
				g.set(u, 5, 1, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH));
			}
		}
	}

	private static void frontUpper(Pen g, Plot p, Geo q) {
		int[] kind = kinds(q.u1(), q.u2());
		for (int u = q.u1(); u <= q.u2(); u++) {
			int k = kind[u - q.u1()];
			for (int y = 6; y <= 9; y++) {
				BlockState st;
				if (k == 1) {
					st = beam(Direction.Axis.Y);
				} else if (k == 2 && y == 7) {
					st = shutter(Direction.NORTH); // [depth] mushiko-mado: a slatted window sunk into the wall
				} else if (k == 2 && y == 8) {
					st = paneU(glassPane(DyeColor.WHITE)); // paper above the slats; its post also keeps the sill from being a ledge
				} else {
					st = plaster();
				}
				g.set(u, y, q.vf(), st);
			}
		}
	}

	// ------------------------------------------------------------------ side and back walls

	private static void sides(Pen g, Plot p, Geo q) {
		int vf = q.vf();
		int[] kind = kinds(vf, q.vb());
		BlockState glass = paneV(b(Blocks.GLASS_PANE));
		for (int uc : new int[] {q.u1(), q.u2()}) {
			boolean west = uc == q.u1();
			int out = west ? 0 : q.w() - 1;
			Direction face = west ? Direction.WEST : Direction.EAST;
			for (int v = vf + 1; v < q.vb(); v++) {
				int k = kind[v - vf];
				for (int y : new int[] {1, 2, 3, 4, 6, 7, 8, 9}) {
					BlockState st;
					if (k == 1) {
						st = beam(Direction.Axis.Y);
					} else if (y == 1) {
						st = stone(p, uc, y, v);
					} else if (k == 2 && (y == 2 || y == 3 || y == 7 || y == 8)) {
						st = glass;
					} else {
						st = plaster();
					}
					g.set(uc, y, v, st);
				}
				if (k == 2) {
					// [depth] open shutters either side of the window group, on the outside face
					for (int dv : new int[] {-1, 1}) {
						int vv = v + dv;
						if (vv > vf && vv < q.vb() && kind[vv - vf] != 2) {
							for (int y : new int[] {2, 3, 7, 8}) {
								g.set(out, y, vv, shutter(face));
							}
						}
					}
				}
			}
		}
	}

	private static void back(Pen g, Plot p, Geo q) {
		int vb = q.vb();
		int[] kind = kinds(q.u1(), q.u2());
		BlockState glass = paneU(b(Blocks.GLASS_PANE));
		for (int u = q.u1(); u <= q.u2(); u++) {
			// ground: a 2-wide opening at the end of the aisle, windows in the rooms' stretch
			if (!entrance(q, u)) {
				for (int y = 1; y <= 4; y++) {
					BlockState st;
					if (isPillar(q, u)) {
						st = beam(Direction.Axis.Y);
					} else if (y == 1) {
						st = stone(p, u, y, vb);
					} else if (u >= q.r0() && (y == 2 || y == 3)) {
						st = glass;
					} else {
						st = plaster();
					}
					g.set(u, y, vb, st);
				}
			}
			int k = kind[u - q.u1()];
			for (int y = 6; y <= 9; y++) {
				BlockState st;
				if (k == 1) {
					st = beam(Direction.Axis.Y);
				} else if (k == 2 && (y == 7 || y == 8)) {
					st = glass;
				} else {
					st = plaster();
				}
				g.set(u, y, vb, st);
			}
		}
	}

	/** Beams round the walls at both floors, and the floor between the storeys (with a beam every third row). */
	private static void floors(Pen g, Geo q) {
		for (int u = q.u1(); u <= q.u2(); u++) {
			for (int y : new int[] {F2, PLATE}) {
				g.set(u, y, q.vf(), beam(Direction.Axis.X));
				g.set(u, y, q.vb(), beam(Direction.Axis.X));
			}
		}
		for (int v = q.vf(); v <= q.vb(); v++) {
			for (int u : new int[] {q.u1(), q.u2()}) {
				g.set(u, F2, v, beam(Direction.Axis.Z));
				g.set(u, PLATE, v, beam(Direction.Axis.Z));
			}
		}
		for (int v = q.vf() + 1; v < q.vb(); v++) {
			for (int u = q.u1() + 1; u < q.u2(); u++) {
				g.set(u, F2, v, (v - q.vf()) % 3 == 0 ? beam(Direction.Axis.X) : b(Blocks.SPRUCE_PLANKS));
			}
		}
	}

	// ------------------------------------------------------------------ the stair (kaidan)

	/**
	 * A switchback flight against the back wall, 2 wide, steps of 1 block (rise 5: 2 + landing + 3). First step
	 * met head-on from the front, a landing 2 wide at the back, 3 free blocks over every step: the floor above
	 * is cut over the flight and fenced, except where the last step walks off onto it.
	 */
	private static void stairs(Pen g, Geo q) {
		int vs = q.vs();
		int x1 = q.x1();
		int x2 = q.x2();
		BlockState core = b(Blocks.SPRUCE_PLANKS);
		Block step = Blocks.SPRUCE_STAIRS;
		// flight 1 (inner column), heading to the back: steps at y1, y2
		g.set(x1, 1, vs, stair(step, Direction.SOUTH));
		g.set(x1, 1, vs + 1, core);
		g.set(x1, 2, vs + 1, stair(step, Direction.SOUTH));
		// landing, 2 wide, top at 3.0 (the high half of the second step)
		for (int x : new int[] {x1, x2}) {
			g.set(x, 1, vs + 2, core);
			g.set(x, 2, vs + 2, core);
		}
		// flight 2 (wall column), heading to the front: steps at y3, y4, y5 (the last one is the floor's own layer)
		g.fill(x2, 1, vs + 1, x2, 2, vs + 1, core);
		g.set(x2, 3, vs + 1, stair(step, Direction.NORTH));
		g.fill(x2, 1, vs, x2, 3, vs, core);
		g.set(x2, 4, vs, stair(step, Direction.NORTH));
		g.fill(x2, 1, vs - 1, x2, 4, vs - 1, core);
		g.set(x2, F2, vs - 1, stair(step, Direction.NORTH));
		// the hole in the floor over the flight (from the second step on)
		g.set(x1, F2, vs + 1, CitadelCanvas.AIR);
		g.set(x1, F2, vs + 2, CitadelCanvas.AIR);
		g.set(x2, F2, vs + 2, CitadelCanvas.AIR);
		g.set(x2, F2, vs + 1, CitadelCanvas.AIR);
		g.set(x2, F2, vs, CitadelCanvas.AIR);
		// the rail round it on the upper floor
		Block rail = Blocks.DARK_OAK_FENCE;
		g.set(x1 - 1, 6, vs, fence(rail, false, true, true, false));
		g.set(x1, 6, vs, fence(rail, false, false, false, true));
		g.set(x1 - 1, 6, vs + 1, fence(rail, true, false, true, false));
		g.set(x1 - 1, 6, vs + 2, fence(rail, true, false, false, false));
	}

	// ------------------------------------------------------------------ roof

	/**
	 * [finish] a tiled gable, 2:1 (flatter on deep plots): an inward stair then full blocks, a dark oak
	 * fascia at both eaves and a ridge with a slab cap. The front edge is flush with the wall; the back
	 * eave overhangs the garden on rafter tails.
	 */
	private static void roof(Pen g, Plot p, Geo q) {
		int kmax = q.kmax();
		for (int v = q.rf(); v <= q.rb(); v++) {
			int k = q.rowK(v);
			int y = q.roofY(v);
			boolean ridge = k == kmax;
			boolean frontHalf = v - q.rf() < q.rb() - v;
			Direction up = frontHalf ? Direction.SOUTH : Direction.NORTH;
			for (int u = q.u1(); u <= q.u2(); u++) {
				boolean end = u == q.u1() || u == q.u2();
				if (end && v > q.rf() && v < q.rb()) {
					continue; // the udatsu stand here
				}
				BlockState st;
				if (ridge || k % q.step() != 0) {
					st = b(Blocks.DEEPSLATE_TILES);
				} else {
					Block sb = k == 0 ? Blocks.DARK_OAK_STAIRS : p.hash(u, y, v, 6) == 0 ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.DEEPSLATE_TILE_STAIRS;
					st = stair(sb, up);
				}
				g.set(u, y, v, st);
			}
			if (ridge) {
				for (int u = q.u1(); u <= q.u2(); u++) {
					g.set(u, y + 1, v, slab(Blocks.DEEPSLATE_BRICK_SLAB));
				}
			}
		}
		// [depth] rafter tails under the back eave, with a corbel under every other one
		for (int u = q.u1(); u <= q.u2(); u++) {
			if ((u - q.u1()) % 2 == 0) {
				g.set(u, PLATE, q.rb(), log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
			} else {
				g.set(u, PLATE, q.rb(), b(Blocks.DARK_OAK_PLANKS));
			}
		}
	}

	/** [depth][palette] the side walls climb above the roof in plaster steps capped with tile slabs. */
	private static void udatsu(Pen g, Geo q) {
		for (int v = q.rf() + 1; v < q.rb(); v++) {
			int y = q.roofY(v);
			for (int u : new int[] {q.u1(), q.u2()}) {
				for (int yy = PLATE + 1; yy <= y; yy++) {
					g.set(u, yy, v, plaster());
				}
				g.set(u, y + 1, v, slab(Blocks.DEEPSLATE_TILE_SLAB));
			}
		}
	}

	/** [finish] a small smoke vent (kemuri-dashi) on the ridge: a plaster box with log corners and a tiled cap. */
	private static void vent(Pen g, Geo q) {
		int ry = ROOF0 + q.kmax() / q.step();
		if (ry + 2 > q.h() - 1) {
			return;
		}
		int n = q.rb() - q.rf() + 1;
		int v0 = q.rf() + q.kmax();
		int v1 = n % 2 == 0 ? v0 + 1 : v0;
		int cu = q.w() / 2;
		for (int u = cu - 1; u <= cu + 1; u++) {
			for (int v = v0; v <= v1; v++) {
				boolean corner = (u == cu - 1 || u == cu + 1) && (v == v0 || v == v1);
				g.set(u, ry + 1, v, corner ? beam(Direction.Axis.Y) : plaster());
			}
			for (int v = v0; v <= v1; v++) {
				g.set(u, ry + 2, v, slab(Blocks.DEEPSLATE_TILE_SLAB)); // no overhang: it would leave a standable ledge under it
			}
		}
	}

	// ------------------------------------------------------------------ ground floor furniture

	private static BlockState barrel() {
		return b(Blocks.BARREL).setValue(BlockStateProperties.FACING, Direction.UP);
	}

	private static BlockState faced(Block bl, Direction f) {
		BlockState s = b(bl);
		return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(BlockStateProperties.HORIZONTAL_FACING, f) : s;
	}

	/**
	 * Mise (shop) at the front beside the earth aisle: a counter on the step line and stock along the wall, then
	 * a workshop. Only one-block pieces on the floor, nothing hanging over them, and the shelves sunk into the wall.
	 */
	private static void interiorGround(Pen g, Plot p, Geo q) {
		int vs = q.vs();
		int free = vs - 4; // rows 3..vs-2 before the stair's approach row
		int zm = free <= 3 ? free : (int) Math.ceil(free * 0.6);
		int m0 = 3;
		int m1 = m0 + zm - 1;
		for (int v = m0; v <= m1; v++) {
			boolean even = (v - m0) % 2 == 0;
			g.set(q.r0(), 1, v, even ? b(Blocks.DARK_OAK_PLANKS) : barrel()); // the counter
			// stock and shelves along the wall: one block high, so they can be walked over and leave no pocket
			g.set(q.r1(), 1, v, even ? barrel() : faced(Blocks.CHISELED_BOOKSHELF, Direction.WEST));
		}
		// the workshop, behind the shop
		Block[] bench = {Blocks.CRAFTING_TABLE, Blocks.BARREL, Blocks.FLETCHING_TABLE, Blocks.LOOM};
		for (int v = m1 + 1; v <= vs - 2; v++) {
			Block bl = bench[(v - m1 - 1) % bench.length];
			g.set(q.r1(), 1, v, bl == Blocks.BARREL ? barrel() : faced(bl, Direction.WEST));
		}
		// the back room behind the stair's approach: stock against the wall, the rest left free
		for (int u = q.r0(); u < q.x1(); u += 2) {
			g.set(u, 1, q.vb() - 1, barrel());
		}
		// light: lanterns hang over free floor only (never over a piece, which would lower the head room)
		g.put(q.r0() + 1, 4, m0 + zm / 2, lantern(true), CitadelCanvas.HANGING);
		for (int v = 4; v < q.vb() - 1; v += 4) {
			g.put(q.e0(), 4, v, lantern(true), CitadelCanvas.HANGING);
		}
	}

	private static BlockState bed(DyeColor color, BedPart part) {
		return b(Blocks.BED.pick(color)).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH).setValue(BlockStateProperties.BED_PART, part);
	}

	/** The upper floor: kitchen along the front wall, a low table with stools, beds at the back, books on the wall. */
	private static void interiorUpper(Pen g, Plot p, Geo q) {
		Block[] kit = {Blocks.SMOKER, Blocks.DARK_OAK_PLANKS, Blocks.FURNACE, Blocks.BARREL, Blocks.DARK_OAK_PLANKS, Blocks.CRAFTING_TABLE};
		for (int u = q.a0(); u <= q.r1(); u++) {
			Block bl = kit[(u - q.a0()) % kit.length];
			g.set(u, 6, 3, bl == Blocks.BARREL ? barrel() : faced(bl, Direction.SOUTH));
		}
		int tv = q.vs() - 1;
		int tu = q.a0() + 1;
		g.set(tu, 6, tv, slab(Blocks.SPRUCE_SLAB));
		g.set(tu - 1, 6, tv, stair(Blocks.SPRUCE_STAIRS, Direction.WEST));
		g.set(tu + 1, 6, tv, stair(Blocks.SPRUCE_STAIRS, Direction.EAST));
		for (int i = 0; i < 2; i++) {
			int u = q.a0() + i;
			g.set(u, 6, q.vb() - 2, bed(i == 0 ? DyeColor.WHITE : DyeColor.RED, BedPart.FOOT));
			g.set(u, 6, q.vb() - 1, bed(i == 0 ? DyeColor.WHITE : DyeColor.RED, BedPart.HEAD));
		}
		for (int v = q.vs(); v <= q.vb() - 3; v++) {
			g.set(q.a0(), 6, v, faced(Blocks.CHISELED_BOOKSHELF, Direction.EAST)); // books along the wall, one block high
		}
		// lanterns hang from the roof over the table and the kitchen
		g.put(tu, q.roofY(tv) - 1, tv, lantern(true), CitadelCanvas.HANGING);
		g.put(q.a0() + 1, q.roofY(5) - 1, 5, lantern(true), CitadelCanvas.HANGING);
	}

	// ------------------------------------------------------------------ garden (tsubo-niwa)

	private static void garden(Pen g, Plot p, Geo q, Random r) {
		int vb = q.vb();
		int d = q.d();
		int w = q.w();
		for (int v = vb + 1; v <= d - 1; v++) {
			for (int u = 1; u <= w - 2; u++) {
				BlockState st = switch (p.hash(u, 0, v, 5)) {
					case 0 -> b(Blocks.MOSS_BLOCK);
					case 1 -> b(Blocks.COARSE_DIRT);
					default -> b(Blocks.GRAVEL);
				};
				g.set(u, 0, v, st);
			}
		}
		// stepping stones from the end of the aisle
		for (int v = vb + 1; v <= d - 3; v++) {
			if ((v - vb) % 2 == 1) {
				g.set(q.e0(), 0, v, b(Blocks.POLISHED_ANDESITE));
				g.set(q.e1(), 0, v, b(Blocks.POLISHED_ANDESITE));
			}
		}
		g.set(q.e1() + 1, 1, vb + 1, b(Blocks.POTTED_FERN));
		// a bamboo fence closes the garden, two rails high
		for (int u = 1; u <= w - 2; u++) {
			BlockState f = fence(Blocks.BAMBOO_FENCE, false, u < w - 2, false, u > 1);
			g.set(u, 1, d - 1, f);
			g.set(u, 2, d - 1, f);
		}
		// the sakura and the lantern on opposite sides, bamboo in the corners
		boolean left = r.nextBoolean();
		int ut = left ? 2 : w - 3;
		int ul = left ? w - 3 : 2;
		sakura(g, p, ut, d - 2);
		JapaneseKit.stoneLantern(g.c, g.p, g.mu(ul), 1, d - 2);
		for (int u : new int[] {1, w - 2}) {
			for (int y = 1; y <= 3; y++) {
				g.set(u, y, d - 2, b(Blocks.BAMBOO));
			}
		}
	}

	/**
	 * A small weeping sakura with a single thick crown: a 3x3 layer under a plus-shaped one, so no leaf is
	 * standable under another with a gap, and petals on the ground round the foot.
	 */
	private static void sakura(Pen g, Plot p, int u, int v) {
		for (int y = 1; y <= 3; y++) {
			g.set(u, y, v, b(Blocks.CHERRY_LOG));
		}
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				boolean corner = du != 0 && dv != 0;
				if (corner && p.hash(u + du, 4, v + dv, 3) == 0) {
					continue;
				}
				g.set(u + du, 4, v + dv, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
				if (du == 0 || dv == 0) {
					g.set(u + du, 5, v + dv, CitadelCanvas.leaves(Blocks.CHERRY_LEAVES));
				}
			}
		}
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				if ((du != 0 || dv != 0) && p.hash(u + du, 1, v + dv, 2) == 0) {
					g.set(u + du, 1, v + dv, b(Blocks.PINK_PETALS));
				}
			}
		}
	}
}
