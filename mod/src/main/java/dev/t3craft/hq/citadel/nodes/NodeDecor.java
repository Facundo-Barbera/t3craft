package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import static dev.t3craft.hq.citadel.CitadelCanvas.BAMBOO;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * The furniture and the small details of the node rooms (the "furnish" pass of {@code CitadelNodes.node}):
 * everything that is not the shell of the room (the shells are {@link TowerRoom}, {@link RotundaRoom},
 * {@link CloisterRoom} and {@link LoungeRoom}). It runs after every column of the node has been painted
 * and before the fixtures (screens, terminal, anchors), so a fixture always wins on a shared cell, and
 * every piece stays inside the node (a cell outside the node's zone is never written). All coordinates are
 * offsets {@code (dx, dz)} from the node's centre, the floor is {@code y = 0}, standing is {@code y = 1}.
 *
 * <p>Deterministic: nothing here is random; the pillars pick their variant from the index of the node.
 * The cells the game uses stay free: the four 3-wide door corridors of the tower and the rotunda, the
 * walk spots, the archive terminal and its {@code ARCHIVE} stand cell, and the bottom of the lounge.
 *
 * <ul>
 * <li>Tower: bookcases under the review screen, four review tables with chairs, two blackboards in the
 *     south wall, planters in the corners, lanterns under the mezzanine and the belfry floor, the bell
 *     lantern.</li>
 * <li>Rotunda: a lectern "calendar" in the middle, benches and planters round the wall, a bench facing the
 *     schedule screen.</li>
 * <li>Cloister: a fountain post in the pool, cherry trees in the court, bookcases along the walls, reading
 *     lecterns, barrels in the corners, lanterns under the gallery roof.</li>
 * <li>Lounge: an open campfire in the middle ringed by four stones (the fire cell is not walkable, so it
 *     is safe), carpets, hemicycle sofas round the fire, cherry trees and lamp posts on the rim.</li>
 * <li>Pillars: one of four monuments by node index (stone lantern, bell pavilion, cherry tree, obelisk).</li>
 * </ul>
 */
public final class NodeDecor {
	private static final BlockState LANTERN_UP = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
	private static final BlockState LANTERN_DOWN = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	/** A waxed lightning rod: finials and spires. */
	public static final BlockState ROD = Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState();

	// ---- sizes of the rooms (the coordinator wraps these when the citadel is scaled)
	/** Tower: half side of the hall (the wall plane is one ring further out). */
	private static final int TOWER_IN = 4;
	/** Tower: height of the mezzanine ring (the ceiling of the hall's ring), and of the belfry floor ring. */
	private static final int TOWER_LEDGE_Y = 11;
	private static final int BELFRY_LEDGE_Y = 22;
	/** Rotunda: inner radius (the wall is further out). */
	private static final double ROTUNDA_IN = 6.0;
	/** Cloister: ring of the bookcases (against the wall), of the lecterns and lanterns. */
	private static final int CLOISTER_SHELF = 6;
	private static final int CLOISTER_WALK = 5;
	/** Lounge: distance of the lamp posts from the centre along an axis. */
	private static final int LOUNGE_RIM = 6;

	private NodeDecor() {
	}

	/** A node's cell frame: writes only inside the node. */
	private record Site(CitadelCanvas c, Node nd) {
		boolean in(int dx, int dz) {
			Zone zn = c.zoneAt(nd.cx() + dx, nd.cz() + dz);
			return zn.kind() == Kind.NODE && zn.node() == nd.k();
		}

		void set(int dx, int y, int dz, BlockState s) {
			if (in(dx, dz)) {
				c.set(nd.cx() + dx, y, nd.cz() + dz, s);
			}
		}

		void put(int dx, int y, int dz, BlockState s, int layer) {
			if (in(dx, dz)) {
				c.put(nd.cx() + dx, y, nd.cz() + dz, s, layer);
			}
		}

		void fill(int dx, int y1, int y2, int dz, BlockState s) {
			for (int y = y1; y <= y2; y++) {
				set(dx, y, dz, s);
			}
		}
	}

	// ------------------------------------------------------------------ helpers

	/** The direction from the centre to the cell, along the dominant axis (east / west on a tie). */
	public static Direction outward(int dx, int dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** The direction from the cell to the centre (the high back of a roof stair). */
	public static Direction inward(int dx, int dz) {
		return outward(dx, dz).getOpposite();
	}

	/** Distance (in cells, along the arc) from a cell to the nearest of the 8 rays at multiples of 45 degrees. */
	public static double rayOffset(int dx, int dz) {
		double d = Math.hypot(dx, dz);
		if (d < 0.5) {
			return 99;
		}
		double a = Math.atan2(dz, dx);
		double n = Math.rint(a / (Math.PI / 4));
		return Math.abs(a - n * Math.PI / 4) * d;
	}

	/** An upside-down stair ({@code f}: the side of the lower step), for arches, coves and corbels. */
	public static BlockState invStairs(Block blk, Direction f) {
		return stairs(blk, f).setValue(StairBlock.HALF, Half.TOP);
	}

	/** A deterministic mix of the deepslate footing: one block in six is cracked. */
	public static BlockState footMix(int x, int y, int z) {
		return Math.floorMod(x * 3 + y * 5 + z * 7, 6) == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : b(Blocks.DEEPSLATE_TILES);
	}

	/** A deterministic mix of light stone bricks: plain, one in seven cracked, one in seven mossy. */
	public static BlockState stoneMix(int x, int y, int z) {
		return switch (Math.floorMod(x * 5 + y * 3 + z * 11, 7)) {
			case 0 -> b(Blocks.CRACKED_STONE_BRICKS);
			case 1 -> b(Blocks.MOSSY_STONE_BRICKS);
			default -> b(Blocks.STONE_BRICKS);
		};
	}

	public static BlockState facing(Block blk, Direction d) {
		return blk.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d);
	}

	private static BlockState lectern(Direction d) {
		return facing(Blocks.LECTERN, d).setValue(BlockStateProperties.HAS_BOOK, true);
	}

	public static BlockState topSlab(Block blk) {
		return blk.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	private static BlockState carpet(DyeColor c) {
		return Blocks.CARPET.pick(c).defaultBlockState();
	}

	/** The step of the lounge's amphitheatre at distance {@code d} (0 = rim, 6 = bottom). */
	public static int loungeStep(double d) {
		return (int) Math.max(0, Math.min(6, Math.round(7 - d)));
	}

	/** The variant (0..3) of a diagonal node: its index in the diagonal list (k = 1, 3, 5, 7). */
	public static int pillarVariant(int k) {
		return Math.floorMod((k - 1) / 2, 4);
	}

	/** Paints the furniture of node {@code nd}. */
	public static void furnish(CitadelCanvas c, Node nd) {
		Site s = new Site(c, nd);
		switch (nd.kind()) {
			case REVIEW_TOWER -> tower(s);
			case SCHEDULE_ROTUNDA -> rotunda(s);
			case ARCHIVE_CLOISTER -> cloister(s);
			case LOUNGE_SUNKEN -> lounge(s);
			case PILLAR -> pillar(s);
		}
	}

	/** A small tree: a log trunk and a rounded canopy of {@code leaf}, {@code h} trunk blocks from {@code y0}. */
	private static void tree(Site s, int dx, int dz, int y0, int h, BlockState leaf) {
		s.fill(dx, y0, y0 + h - 1, dz, LOG);
		int top = y0 + h;
		for (int ax = -1; ax <= 1; ax++) {
			for (int az = -1; az <= 1; az++) {
				if (Math.abs(ax) + Math.abs(az) <= 2) {
					s.set(dx + ax, top, dz + az, leaf);
				}
			}
		}
		for (int[] p : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			s.set(dx + p[0], top + 1, dz + p[1], leaf);
		}
		s.set(dx, top + 2, dz, leaf);
	}

	// ------------------------------------------------------------------ review tower

	private static void tower(Site s) {
		CitadelCanvas c = s.c();
		int in = TOWER_IN;
		// bookcases under the review screen, at the north wall (the middle three cells stay open: the door)
		for (int dx : new int[] {-3, -2, 2, 3}) {
			s.set(dx, 1, -in, facing(Blocks.CHISELED_BOOKSHELF, Direction.SOUTH));
			s.set(dx, 2, -in, b(Blocks.BOOKSHELF));
			s.set(dx, 3, -in, facing(Blocks.CHISELED_BOOKSHELF, Direction.SOUTH));
			s.set(dx, 4, -in, slab(Blocks.CHERRY_SLAB));
		}
		// four review tables (2 long) with chairs on both sides, a lantern at one end, papers at the other
		for (int sx : new int[] {-1, 1}) {
			for (int[] zr : new int[][] {{-3, -2}, {2, 3}}) {
				int tx = 3 * sx;
				for (int z = zr[0]; z <= zr[1]; z++) {
					s.set(tx, 1, z, topSlab(Blocks.CHERRY_SLAB));
					s.set(tx - sx, 1, z, stairs(Blocks.CHERRY_STAIRS, sx > 0 ? Direction.WEST : Direction.EAST));
					s.set(tx + sx, 1, z, stairs(Blocks.CHERRY_STAIRS, sx > 0 ? Direction.EAST : Direction.WEST));
				}
				int lanternZ = zr[0] < 0 ? -2 : 2;
				s.put(tx, 2, lanternZ, LANTERN_UP, DECOR);
				s.put(tx, 2, zr[0] < 0 ? -3 : 3, carpet(DyeColor.WHITE), DECOR);
			}
		}
		// two blackboards set into the south wall, either side of the south door, with a chalk tray
		for (int sx : new int[] {-1, 1}) {
			for (int a = 2; a <= 4; a++) {
				int x = sx * a;
				s.set(x, 1, in, slab(Blocks.STONE_BRICK_SLAB));
				s.set(x, 2, in + 1, b(Blocks.POLISHED_BLACKSTONE));
				s.set(x, 3, in + 1, b(Blocks.POLISHED_BLACKSTONE));
			}
		}
		// planters in the four corners
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				s.set(in * sx, 1, in * sz, c.kit.gardenPlanter());
			}
		}
		// lanterns under the mezzanine ring and under the belfry floor ring
		for (int dx = -in; dx <= in; dx++) {
			for (int dz = -in; dz <= in; dz++) {
				int r = Math.max(Math.abs(dx), Math.abs(dz));
				if (Math.floorMod(dx + dz, 4) == 0) {
					if (r == in) {
						s.put(dx, TOWER_LEDGE_Y - 1, dz, LANTERN_DOWN, HANGING);
					} else if (r == in - 1) {
						s.put(dx, BELFRY_LEDGE_Y - 1, dz, LANTERN_DOWN, HANGING);
					}
				}
			}
		}
		// the bell of the belfry: a big lantern under the log beam
		s.put(0, 28, 0, LANTERN_DOWN, HANGING);
	}

	// ------------------------------------------------------------------ schedule rotunda

	private static void rotunda(Site s) {
		CitadelCanvas c = s.c();
		// the calendar: a lectern with its book in the middle of the clock
		s.set(0, 1, 0, lectern(Direction.SOUTH));
		// benches round the wall (not behind the screen on the east), a planter on each diagonal
		int span = (int) Math.ceil(ROTUNDA_IN);
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				double d = Math.hypot(dx, dz);
				if (d < ROTUNDA_IN - 0.7 || d > ROTUNDA_IN || Math.min(Math.abs(dx), Math.abs(dz)) < 2) {
					continue;
				}
				if (Math.abs(dx) == 4 && Math.abs(dz) == 4) {
					s.set(dx, 1, dz, c.kit.gardenPlanter());
				} else if (!(dx > 0 && dx > Math.abs(dz))) {
					s.set(dx, 1, dz, stairs(Blocks.CHERRY_STAIRS, outward(dx, dz)));
				}
			}
		}
		// a bench facing the schedule screen
		s.set(2, 1, -1, stairs(Blocks.CHERRY_STAIRS, Direction.WEST));
		s.set(2, 1, 1, stairs(Blocks.CHERRY_STAIRS, Direction.WEST));
	}

	// ------------------------------------------------------------------ archive cloister

	private static void cloister(Site s) {
		if (CloisterRoom.grand(s.c().k)) {
			return; // the grand cloister paints its own furniture, in real offsets
		}
		// the fountain: a post and a lantern in the middle of the pool
		s.fill(0, 1, 2, 0, b(Blocks.STONE_BRICK_WALL));
		s.put(0, 3, 0, LANTERN_UP, DECOR);
		// a cherry tree in each corner of the court, beds under them
		BlockState pink = leaves(Blocks.CHERRY_LEAVES);
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				int tx = 2 * sx;
				int tz = 2 * sz;
				s.fill(tx, 1, 3, tz, LOG);
				for (int[] p : new int[][] {{0, 0}, {-sx, 0}, {0, -sz}, {-sx, -sz}}) {
					s.set(tx + p[0], 4, tz + p[1], pink);
				}
				s.set(tx, 5, tz, pink);
				s.set(tx - sx, 3, tz, pink);
				s.set(tx, 3, tz - sz, pink);
				// (the azalea beds that stood at (sx, 3sz) and (3sx, sz) narrowed the court's four arches to one
				// block: the arches are 3 wide and stay clear)
			}
		}
		// per side (0 north, 1 east, 2 south, 3 west): bookcases on the wall, a reading lectern in a bay of each
		// half. The lecterns stand IN the shelf line (ring 6), not in the aisle: the gallery's aisle (rings 4 and
		// 5) stays clear all the way round. The north wall east of its gate is the consultation bay of the
		// terminal ({@link ArchiveBay}), so no bookcase there.
		for (int side = 0; side < 4; side++) {
			Direction in = switch (side) {
				case 0 -> Direction.SOUTH;
				case 1 -> Direction.WEST;
				case 2 -> Direction.NORTH;
				default -> Direction.EAST;
			};
			for (int o = -CLOISTER_WALK; o <= CLOISTER_WALK; o++) {
				if (Math.abs(o) < 2 || (side == 0 && o >= ArchiveBay.BAY_FROM && o <= ArchiveBay.BAY_TO)) {
					continue;
				}
				int[] w = onSide(side, o, CLOISTER_SHELF);
				s.set(w[0], 1, w[1], Math.abs(o) == 4 ? lectern(in) : facing(Blocks.CHISELED_BOOKSHELF, in));
				s.set(w[0], 2, w[1], b(Blocks.BOOKSHELF));
				s.set(w[0], 3, w[1], facing(Blocks.CHISELED_BOOKSHELF, in));
			}
		}
		// barrels in the corners, a lantern on top
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				s.set(CLOISTER_SHELF * sx, 1, CLOISTER_SHELF * sz, b(Blocks.BARREL));
				s.set(CLOISTER_SHELF * sx, 2, CLOISTER_SHELF * sz, b(Blocks.BARREL));
				s.put(CLOISTER_SHELF * sx, 3, CLOISTER_SHELF * sz, LANTERN_UP, DECOR);
			}
		}
		// lanterns under the gallery roof (the ceiling at ring 5 is y = 5)
		for (int side = 0; side < 4; side++) {
			for (int o : new int[] {-4, 0, 4}) {
				int[] w = onSide(side, o, CLOISTER_WALK);
				s.put(w[0], 4, w[1], LANTERN_DOWN, HANGING);
			}
		}
	}

	/** The cell at offset {@code o} along a side (0 north, 1 east, 2 south, 3 west), {@code ring} out. */
	private static int[] onSide(int side, int o, int ring) {
		return switch (side) {
			case 0 -> new int[] {o, -ring};
			case 1 -> new int[] {ring, o};
			case 2 -> new int[] {o, ring};
			default -> new int[] {-ring, o};
		};
	}

	// ------------------------------------------------------------------ lounge

	private static void lounge(Site s) {
		// the hearth: an open campfire in the middle of the bottom, ringed by four stone blocks on the diagonals
		// (a fire pit, open to the sky: no wooden roof over it and no rugs touching it; the axes stay free, and
		// the walk spot east of it looks at the fire). Its cell has no standing room, so nobody steps in.
		s.set(0, -2, 0, Blocks.CAMPFIRE.defaultBlockState());
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				s.set(sx, -2, sz, slab(Blocks.COBBLED_DEEPSLATE_SLAB));
			}
		}
		// the hemicycle: sofas on the fourth step in the four diagonal sectors (the axes stay open as paths)
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				if (loungeStep(Math.hypot(dx, dz)) == 4 && Math.min(Math.abs(dx), Math.abs(dz)) >= 1) {
					s.set(dx, -1, dz, stairs(Blocks.CHERRY_STAIRS, outward(dx, dz)));
				}
			}
		}
		// a cherry tree on the rim in each quadrant, and lamp posts between the paths
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				tree(s, 4 * sx, 5 * sz, 0, 4, leaves(Blocks.CHERRY_LEAVES));
			}
		}
		int r = LOUNGE_RIM;
		for (int[] p : new int[][] {{3, r}, {-3, r}, {3, -r}, {-3, -r}, {r, 3}, {-r, 3}, {r, -3}, {-r, -3}}) {
			s.set(p[0], 1, p[1], b(Blocks.STONE_BRICK_WALL));
			s.set(p[0], 2, p[1], b(Blocks.STONE_BRICK_WALL));
			s.put(p[0], 3, p[1], LANTERN_UP, DECOR);
		}
	}

	// ------------------------------------------------------------------ pillars

	private static void pillar(Site s) {
		BlockState wall = b(Blocks.STONE_BRICK_WALL);
		BlockState tiles = slab(Blocks.DEEPSLATE_TILE_SLAB);
		switch (pillarVariant(s.nd().k())) {
			case 0 -> {
				// stone lantern: a slab plinth, a post, the lantern, a cross of slabs for the roof, a finial
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						s.set(dx, 1, dz, dx == 0 && dz == 0 ? b(Blocks.DEEPSLATE_TILES) : tiles);
					}
				}
				s.set(0, 2, 0, b(Blocks.CHISELED_STONE_BRICKS));
				s.fill(0, 3, 6, 0, wall);
				s.set(0, 7, 0, b(Blocks.DEEPSLATE_TILES));
				s.put(0, 8, 0, LANTERN_UP, DECOR);
				for (int[] p : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					s.set(p[0], 9, p[1], p[0] == 0 && p[1] == 0 ? b(Blocks.DEEPSLATE_TILES) : tiles);
				}
				s.set(0, 10, 0, ROD);
				for (int[] p : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
					s.set(p[0], 1, p[1], leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
			}
			case 1 -> {
				// bell pavilion: four posts, a stair roof, a hanging lantern, a rod on top
				for (int sx : new int[] {-1, 1}) {
					for (int sz : new int[] {-1, 1}) {
						s.fill(sx, 1, 4, sz, LOG);
					}
				}
				// ceiling 3 x 3, an eave of stairs round it (the 12 cells within the node), a smaller roof above
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (Math.hypot(dx, dz) > 2.5) {
							continue;
						}
						boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
						Direction in = inward(Math.abs(dx) >= Math.abs(dz) ? dx : 0, Math.abs(dx) >= Math.abs(dz) ? 0 : dz);
						s.set(dx, 5, dz, inner ? PLANKS : stairs(Blocks.CHERRY_STAIRS, in));
						if (inner) {
							s.set(dx, 6, dz, dx == 0 && dz == 0 ? BAMBOO : stairs(Blocks.CHERRY_STAIRS, inward(dx == 0 ? 0 : dx, dx == 0 ? dz : 0)));
						}
					}
				}
				s.set(0, 7, 0, ROD);
				s.put(0, 4, 0, LANTERN_DOWN, HANGING);
			}
			case 2 -> {
				// cherry tree: trunk, a wide pink canopy, a light hidden inside
				BlockState pink = leaves(Blocks.CHERRY_LEAVES);
				s.fill(0, 1, 5, 0, LOG);
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (Math.hypot(dx, dz) <= 2.5) {
							s.set(dx, 6, dz, pink);
							if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && (dx != 0 || dz != 0)) {
								s.set(dx, 5, dz, pink);
							}
						}
					}
				}
				for (int[] p : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
					s.set(p[0], 7, p[1], pink);
				}
				for (int[] p : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					s.set(p[0], 8, p[1], pink);
				}
				s.set(0, 9, 0, pink);
				s.set(0, 6, 0, LIGHT);
			}
			default -> {
				// obelisk: a slab plinth, a blackstone shaft with a band of light, a rod, lamps on posts
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx != 0 || dz != 0) {
							s.set(dx, 1, dz, slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB));
						}
					}
				}
				s.fill(0, 1, 7, 0, RIB);
				s.set(0, 4, 0, LIGHT);
				s.set(0, 8, 0, LIGHT);
				s.set(0, 9, 0, RIB);
				s.set(0, 10, 0, ROD);
				for (int[] p : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
					s.set(p[0], 1, p[1], wall);
					s.put(p[0], 2, p[1], LANTERN_UP, DECOR);
				}
			}
		}
	}
}
