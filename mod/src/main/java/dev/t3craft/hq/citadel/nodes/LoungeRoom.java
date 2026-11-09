package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * The lounge, the west node of ring 1. Two builds share this class.
 *
 * <h2>Scale 2 and up: {@link #build} (the real lounge, drawn at its real size)</h2>
 *
 * Not magnified from a design of radius 7 any more (that made 2-wide aisles, 2-deep sofas and a pit with nothing to do
 * in it): at {@code k >= 1.9} {@link #build} paints the whole disc (radius 14 at scale 2) cell by cell in real offsets
 * {@code (dx, dz)} from the centre. The ground is {@code y = 0}, standing is {@code y = 1}.
 *
 * <ul>
 * <li><b>The pit</b> ({@code d <= 6.5}): a conversation pit 2 blocks down ({@code y = -2}). Round a stone-ringed
 *     hearth (an open campfire inside a 3 x 3 ring of stone-brick wall: nobody can step in, no water anywhere near)
 *     stand four curved sofas against the pit wall and a low table in front of each. The four 5-wide aisles
 *     ({@code |dx| <= 2} or {@code |dz| <= 2}, cherry boards) stay clear to the hearth.</li>
 *     <li><b>Four flights</b> of stairs, 3 wide with a rail on each side, one on each axis: two stone-brick stairs,
 *     the first step head-on, nothing over them; the wall between the flights is a 2-high dark base with a light
 *     coping and a low rail with lantern posts and pots.</li>
 * <li><b>The terrace</b> ({@code d > 8.5}): flush with the street, with the four 5-wide axes as entrances (cherry
 *     boards) and a quarter for each use, from the north-west clockwise: <b>cafe</b> (a bar with chest, barrel and
 *     brewing stand, smoker, cauldron, chest; a pantry of barrels, chests and a composter; a low table with benches),
 *     <b>library</b> (shelf walls, two lecterns, a reading table), <b>music</b> (a jukebox with its disc, a record
 *     rack and chest, speakers, sofas and tables round it), <b>garden</b> (azalea and flower beds, a cherry tree, a tea
 *     table).</li>
 * <li><b>The pergola</b> ({@code y = 6}, 5 clear above the floor): a ring beam of stripped cherry on 12 posts (the
 *     eight at the entrances make four gates, with a hanging lantern), a lattice of slabs outside and inside it, and
 *     warm glow panels in the lattice (the kit's {@code glow_panel}). Glow strips run along the foot of the bar, the
 *     shelves and the rack. No lantern hangs anywhere else.</li>
 * </ul>
 *
 * <p>Palette: deepslate tiles and polished deepslate (base), stone brick (coping, counter bases), cherry (boards,
 * sofas, beams), moss and greens, a little gold in the lanterns and panels. Designed for scale 2; at a larger scale the
 * extra ring beyond radius 14 is plain terrace, at a smaller one (down to 1.9) the room is clipped by the node's
 * zone.
 *
 * <h2>Below scale 1.9: {@link #cell} (the legacy design)</h2>
 *
 * A sunken amphitheatre of radius 7 painted cell by cell and magnified by {@code CitadelNodes}: half steps
 * ({@code k = round(7 - d)}, 0 = rim .. 6 = bottom) down to {@code y = -3}, flower beds on the second ring, azalea
 * bushes on the rim. The furniture is {@link NodeDecor}'s.
 */
public final class LoungeRoom {
	/** Ring {@code k} of the flower beds, and the cells within this of an axis that stay paths (legacy). */
	private static final int BED_RING = 2;
	private static final int PATH_HALF = 1;

	/** Scale from which the lounge is drawn at its real size ({@link #build}) instead of magnified ({@link #cell}). */
	public static final double DIRECT_FROM = 1.9;
	/** Radius of the pit floor. */
	private static final double PIT_R = 6.5;
	/** Outer radius of the pit wall (and of the flights). */
	private static final double WALL_R = 8.5;
	/** Floor of the pit. */
	private static final int PIT_Y = -2;
	/** Height of the pergola's beams and rafters. */
	private static final int ROOF_Y = 6;
	/** The standing cell of the walk spot: east of the hearth in the pit. */
	public static final int SPOT_DX = 3;
	public static final int SPOT_Y = -1;

	private LoungeRoom() {
	}

	/** Whether this plan draws the lounge at its real size (scale 1.9 or more). */
	public static boolean direct(CitadelCanvas c) {
		return c.k >= DIRECT_FROM;
	}

	// ------------------------------------------------------------------ the real lounge

	/** Paints the whole lounge of node {@code nd} in real offsets. */
	public static void build(CitadelCanvas c, Node nd) {
		Room r = new Room(c, nd);
		int span = (int) Math.ceil(nd.size()) + 1;
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				if (r.in(dx, dz)) {
					r.ground(dx, dz);
				}
			}
		}
		r.hearth();
		r.sofas();
		r.rim();
		r.pergola();
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				Q q = new Q(r, sx, sz);
				switch (theme(sx, sz)) {
					case 0 -> cafe(q);
					case 1 -> library(q);
					case 2 -> music(q);
					default -> garden(q);
				}
			}
		}
	}

	/** The use of a quarter: 0 cafe (north-west), 1 library (north-east), 2 music (south-east), 3 garden (south-west). */
	private static int theme(int sx, int sz) {
		return sx < 0 ? (sz < 0 ? 0 : 3) : (sz < 0 ? 1 : 2);
	}

	// ---- states

	private static BlockState chest(Direction f) {
		return Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f);
	}

	private static BlockState barrel() {
		return Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
	}

	private static BlockState smoker(Direction f) {
		return Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f).setValue(BlockStateProperties.LIT, true);
	}

	private static BlockState lectern(Direction f) {
		return Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f).setValue(BlockStateProperties.HAS_BOOK, true);
	}

	private static BlockState jukebox() {
		return Blocks.JUKEBOX.defaultBlockState().setValue(BlockStateProperties.HAS_RECORD, true);
	}

	private static BlockState shelf(Direction f) {
		return Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f);
	}

	private static BlockState beam(Direction.Axis axis) {
		return Blocks.STRIPPED_CHERRY_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	private static BlockState lanternUp() {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
	}

	private static BlockState lanternDown() {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
	}

	/** A potted plant, one of five by {@code h}. */
	private static BlockState pot(int h) {
		return switch (Math.floorMod(h, 5)) {
			case 0 -> b(Blocks.POTTED_FERN);
			case 1 -> b(Blocks.POTTED_AZALEA);
			case 2 -> b(Blocks.POTTED_BAMBOO);
			case 3 -> b(Blocks.POTTED_PINK_TULIP);
			default -> b(Blocks.POTTED_FLOWERING_AZALEA);
		};
	}

	// ---- the room

	/** A node's cell frame in real offsets: writes only inside the node's zone. */
	private static final class Room {
		final CitadelCanvas c;
		final Node nd;
		final int cx;
		final int cz;

		Room(CitadelCanvas c, Node nd) {
			this.c = c;
			this.nd = nd;
			this.cx = nd.cx();
			this.cz = nd.cz();
		}

		boolean in(int dx, int dz) {
			Zone zn = c.zoneAt(cx + dx, cz + dz);
			return zn.kind() == Kind.NODE && zn.node() == nd.k();
		}

		void set(int dx, int y, int dz, BlockState s) {
			if (in(dx, dz)) {
				c.set(cx + dx, y, cz + dz, s);
			}
		}

		void put(int dx, int y, int dz, BlockState s, int layer) {
			if (in(dx, dz)) {
				c.put(cx + dx, y, cz + dz, s, layer);
			}
		}

		void clear(int dx, int dz) {
			for (int y = -4; y <= 9; y++) {
				long k = BlockPos.asLong(cx + dx, y, cz + dz);
				c.cells.remove(k);
				c.layers.remove(k);
				c.data.remove(k);
			}
		}

		/** Floor, pit, wall and flights of one column. */
		void ground(int dx, int dz) {
			clear(dx, dz);
			int ax = Math.abs(dx);
			int az = Math.abs(dz);
			int a = Math.min(ax, az);
			int along = Math.max(ax, az);
			double d = Math.hypot(dx, dz);
			boolean flight = a <= 1 && (along == 7 || along == 8);
			if (d <= PIT_R) {
				BlockState f;
				if (d <= 1.5) {
					f = b(Blocks.COBBLED_DEEPSLATE);
				} else if (a <= 2) {
					f = c.floor();
				} else {
					f = ((ax + az) & 1) == 0 ? NodeDecor.footMix(dx, PIT_Y, dz) : b(Blocks.POLISHED_DEEPSLATE);
				}
				set(dx, PIT_Y, dz, f);
			} else if (flight) {
				Direction up = NodeDecor.outward(dx, dz);
				set(dx, PIT_Y, dz, NodeDecor.footMix(dx, PIT_Y, dz));
				if (along == 7) {
					set(dx, PIT_Y + 1, dz, stairs(Blocks.STONE_BRICK_STAIRS, up));
				} else {
					set(dx, PIT_Y + 1, dz, b(Blocks.POLISHED_DEEPSLATE));
					set(dx, 0, dz, stairs(Blocks.STONE_BRICK_STAIRS, up));
				}
			} else if (d <= WALL_R) {
				set(dx, PIT_Y, dz, NodeDecor.footMix(dx, PIT_Y, dz));
				set(dx, PIT_Y + 1, dz, b(Blocks.POLISHED_DEEPSLATE));
				set(dx, 0, dz, NodeDecor.stoneMix(dx, 0, dz));
			} else {
				set(dx, 0, dz, terrace(dx, dz, ax, az, a, d));
			}
		}

		BlockState terrace(int dx, int dz, int ax, int az, int a, double d) {
			if (a <= 1) {
				return c.floor();
			}
			if (a == 2) {
				return c.accent();
			}
			int sx = dx < 0 ? -1 : 1;
			int sz = dz < 0 ? -1 : 1;
			if (d > 13.0) {
				return theme(sx, sz) == 3 ? MOSS : NodeDecor.footMix(dx, 0, dz);
			}
			if (d <= 9.3) {
				return ((ax + az) & 1) == 0 ? b(Blocks.POLISHED_DEEPSLATE) : NodeDecor.footMix(dx, 0, dz);
			}
			return switch (theme(sx, sz)) {
				case 0 -> ((ax + az) & 1) == 0 ? b(Blocks.SMOOTH_STONE) : b(Blocks.POLISHED_DEEPSLATE);
				case 1 -> c.floor();
				case 2 -> ((ax + az) & 1) == 0 ? NodeDecor.footMix(dx, 0, dz) : b(Blocks.POLISHED_DEEPSLATE);
				default -> Math.floorMod(ax * 3 + az * 7, 5) == 0 ? GRASS : MOSS;
			};
		}

		/** The hearth: an open campfire inside a ring of stone-brick wall. Nothing wet near, nothing to step in. */
		void hearth() {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						set(0, PIT_Y + 1, 0, b(Blocks.CAMPFIRE));
					} else {
						set(dx, PIT_Y + 1, dz, b(Blocks.STONE_BRICK_WALL));
					}
				}
			}
		}

		/** Four curved sofas against the pit wall and a low table in front of each. */
		void sofas() {
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -6; dz <= 6; dz++) {
					double d = Math.hypot(dx, dz);
					if (Math.min(Math.abs(dx), Math.abs(dz)) >= 3 && d > 5.4 && d <= PIT_R) {
						set(dx, PIT_Y + 1, dz, stairs(Blocks.CHERRY_STAIRS, NodeDecor.outward(dx, dz)));
					}
				}
			}
			for (int sx : new int[] {-1, 1}) {
				for (int sz : new int[] {-1, 1}) {
					for (int[] t : new int[][] {{3, 3}, {3, 4}, {4, 3}}) {
						set(sx * t[0], PIT_Y + 1, sz * t[1], slab(Blocks.CHERRY_SLAB));
					}
					set(sx * 3, PIT_Y + 2, sz * 3, pot(sx * 2 + sz + 7));
				}
			}
		}

		/** The rail round the pit, with lantern posts and pots. */
		void rim() {
			for (int dx = -9; dx <= 9; dx++) {
				for (int dz = -9; dz <= 9; dz++) {
					int ax = Math.abs(dx);
					int az = Math.abs(dz);
					int a = Math.min(ax, az);
					int along = Math.max(ax, az);
					double d = Math.hypot(dx, dz);
					boolean flight = a <= 1 && (along == 7 || along == 8);
					if (flight || d <= PIT_R || d > WALL_R || a < 2) {
						continue;
					}
					if (d <= 7.5 || a == 2) {
						set(dx, 1, dz, b(Blocks.STONE_BRICK_WALL));
						boolean post = a == 5 && along == 5;
						if (post) {
							set(dx, 2, dz, b(Blocks.STONE_BRICK_WALL));
							put(dx, 3, dz, lanternUp(), DECOR);
						} else if (a >= 3 && Math.floorMod(dx * 5 + dz * 3, 6) == 0) {
							set(dx, 2, dz, pot(dx * 3 + dz));
						}
					}
				}
			}
		}

		/**
		 * The pergola: a ring beam on twelve dark posts, and outside it an awning of cherry stairs that slopes down
		 * to the rim (so the bars, shelves and the rack are under a roof) with warm glow panels under it. The
		 * entrances stay open between their posts.
		 */
		void pergola() {
			for (int dx = -15; dx <= 15; dx++) {
				for (int dz = -15; dz <= 15; dz++) {
					if (!in(dx, dz)) {
						continue;
					}
					int ax = Math.abs(dx);
					int az = Math.abs(dz);
					int a = Math.min(ax, az);
					double d = Math.hypot(dx, dz);
					if (d <= 10.6 || d > 13.95) {
						continue;
					}
					boolean ring = d <= 11.9;
					if (ring) {
						set(dx, ROOF_Y, dz, beam(ax >= az ? Direction.Axis.Z : Direction.Axis.X));
					} else if (a >= 3) {
						set(dx, ROOF_Y - 1, dz, stairs(Blocks.CHERRY_STAIRS, NodeDecor.inward(dx, dz)));
						if (Math.floorMod(dx * 3 + dz * 5, 7) == 0) {
							// the panel hangs under its stair: its lit face is the bottom, its frame never shows from above
							set(dx, ROOF_Y - 2, dz, c.kit.glowPanel(Direction.DOWN, false));
						}
						int sx = dx < 0 ? -1 : 1;
						int sz = dz < 0 ? -1 : 1;
						if (theme(sx, sz) == 3 && d > 12.2 && Math.floorMod(ax + az * 2, 3) != 0) {
							set(dx, ROOF_Y, dz, leaves(Blocks.CHERRY_LEAVES));
						}
					}
				}
			}
			int[][] posts = {{11, 3}, {11, -3}, {-11, 3}, {-11, -3}, {3, 11}, {-3, 11}, {3, -11}, {-3, -11}, {8, 8}, {8, -8}, {-8, 8}, {-8, -8}};
			for (int[] p : posts) {
				set(p[0], 1, p[1], b(Blocks.STONE_BRICKS));
				for (int y = 2; y < ROOF_Y; y++) {
					set(p[0], y, p[1], RIB);
				}
			}
			// the four gates: a lantern under the beam over each entrance
			for (int[] g : new int[][] {{11, 0}, {-11, 0}, {0, 11}, {0, -11}}) {
				put(g[0], ROOF_Y - 1, g[1], lanternDown(), HANGING);
			}
		}
	}

	/** A quarter of the terrace in its own coordinates: {@code u} and {@code v} grow away from the centre along x and z. */
	private record Q(Room r, int sx, int sz) {
		void set(int u, int y, int v, BlockState s) {
			r.set(sx * u, y, sz * v, s);
		}

		void put(int u, int y, int v, BlockState s, int layer) {
			r.put(sx * u, y, sz * v, s, layer);
		}

		/** Towards the centre along x. */
		Direction toX() {
			return sx > 0 ? Direction.WEST : Direction.EAST;
		}

		/** Towards the centre along z. */
		Direction toZ() {
			return sz > 0 ? Direction.NORTH : Direction.SOUTH;
		}

		/** A bench cell whose back is towards {@code ascent} (the sitter looks the other way). */
		void bench(int u, int v, Direction ascent) {
			set(u, 1, v, stairs(Blocks.CHERRY_STAIRS, ascent));
		}

		void table(int u, int v) {
			set(u, 1, v, slab(Blocks.CHERRY_SLAB));
		}

		/** A low table {@code len} long along u at row {@code v}, a bench on each side facing it. */
		void tea(int u0, int v, int len) {
			for (int u = u0; u < u0 + len; u++) {
				table(u, v);
				bench(u, v - 1, toZ());
				bench(u, v + 1, toZ().getOpposite());
			}
		}

		/** A warm glow panel under a stair of the awning at {@code (u, v)}. */
		void lamp(int u, int v) {
			set(u, ROOF_Y - 1, v, stairs(Blocks.CHERRY_STAIRS, NodeDecor.inward(sx * u, sz * v)));
			set(u, ROOF_Y - 2, v, r.c.kit.glowPanel(Direction.DOWN, false));
		}

		/** A glow strip along the foot of a counter or shelf, in the cell where people stand. */
		void strip(int u, int v, Direction looks) {
			put(u, 1, v, r.c.kit.glowStrip(looks, false, false), DECOR);
		}
	}

	// ---- the four quarters

	/** Cafe: a bar along v = 11, a pantry along u = 11, a low table with benches, a planter in the corner. */
	private static void cafe(Q q) {
		Direction f = q.toZ();
		q.set(4, 1, 11, chest(f));
		q.set(5, 1, 11, barrel());
		q.set(6, 1, 11, smoker(f));
		q.set(7, 1, 11, b(Blocks.CAULDRON));
		q.set(8, 1, 11, chest(f));
		q.set(5, 2, 11, b(Blocks.BREWING_STAND));
		q.set(4, 2, 11, b(Blocks.POTTED_FERN));
		q.put(6, 2, 11, lanternUp(), DECOR);
		q.set(8, 2, 11, b(Blocks.POTTED_BAMBOO));
		for (int u = 4; u <= 6; u++) {
			q.set(u, 1, 12, barrel());
			q.set(u, 2, 12, barrel());
		}
		for (int u = 4; u <= 8; u++) {
			q.strip(u, 10, f);
		}
		Direction g = q.toX();
		q.set(11, 1, 4, barrel());
		q.set(11, 2, 4, barrel());
		q.set(11, 1, 5, barrel());
		q.set(11, 2, 5, barrel());
		q.set(11, 1, 6, chest(g));
		q.set(11, 1, 7, chest(g));
		q.set(11, 1, 8, b(Blocks.COMPOSTER));
		for (int v = 4; v <= 8; v++) {
			q.strip(10, v, g);
		}
		q.tea(8, 5, 2);
		q.set(8, 2, 5, pot(1));
		q.set(9, 1, 9, b(Blocks.POTTED_FLOWERING_AZALEA));
		q.lamp(9, 10);
	}

	/** Library: shelf walls along v = 11 and u = 11 with a lectern in each, a reading table with a lantern. */
	private static void library(Q q) {
		Direction f = q.toZ();
		for (int u = 4; u <= 8; u++) {
			if (u == 6) {
				q.set(u, 1, 11, lectern(f));
				continue;
			}
			q.set(u, 1, 11, shelf(f));
			q.set(u, 2, 11, b(Blocks.BOOKSHELF));
			q.set(u, 3, 11, shelf(f));
			q.set(u, 4, 11, slab(Blocks.CHERRY_SLAB));
		}
		Direction g = q.toX();
		for (int v = 4; v <= 8; v++) {
			if (v == 6) {
				q.set(11, 1, v, lectern(g));
				continue;
			}
			q.set(11, 1, v, shelf(g));
			q.set(11, 2, v, b(Blocks.BOOKSHELF));
			q.set(11, 3, v, shelf(g));
			q.set(11, 4, v, slab(Blocks.CHERRY_SLAB));
		}
		for (int u = 4; u <= 8; u++) {
			q.strip(u, 10, f);
		}
		for (int v = 4; v <= 8; v++) {
			q.strip(10, v, g);
		}
		q.tea(8, 5, 2);
		q.set(8, 2, 5, pot(0));
		q.set(9, 2, 5, pot(2));
		q.set(9, 1, 9, b(Blocks.CARTOGRAPHY_TABLE));
		q.lamp(9, 10);
	}

	/** Music: a jukebox with its disc and speakers, a record rack and chest, benches and tables round it. */
	private static void music(Q q) {
		Direction f = q.toZ();
		q.set(9, 1, 9, jukebox());
		q.set(10, 1, 9, b(Blocks.NOTE_BLOCK));
		q.set(9, 1, 10, b(Blocks.NOTE_BLOCK));
		q.put(9, 2, 9, lanternUp(), DECOR);
		for (int u = 4; u <= 8; u++) {
			if (u == 6) {
				q.set(u, 1, 11, chest(f));
				q.set(u, 2, 11, pot(3));
				continue;
			}
			q.set(u, 1, 11, shelf(f));
			q.set(u, 2, 11, shelf(f));
			q.set(u, 3, 11, slab(Blocks.CHERRY_SLAB));
		}
		for (int u = 4; u <= 8; u++) {
			q.strip(u, 10, f);
		}
		for (int v = 7; v <= 9; v++) {
			q.bench(6, v, q.toX());
		}
		q.table(7, 8);
		q.table(7, 7);
		for (int u = 8; u <= 10; u++) {
			q.bench(u, 6, q.toZ());
		}
		q.table(9, 7);
		q.set(9, 2, 7, pot(4));
		q.lamp(9, 9);
	}

	/** Garden: azalea and flower beds along v = 10..11, a cherry tree in the corner, a tea table with benches. */
	private static void garden(Q q) {
		for (int u = 4; u <= 9; u++) {
			q.set(u, 1, 9, slab(Blocks.STONE_BRICK_SLAB));
			for (int v = 10; v <= 11; v++) {
				BlockState plant = switch (Math.floorMod(u * 3 + v * 5, 6)) {
					case 0 -> b(Blocks.AZALEA);
					case 1 -> b(Blocks.FLOWERING_AZALEA);
					case 2 -> b(Blocks.FERN);
					case 3 -> b(Blocks.PINK_TULIP);
					case 4 -> b(Blocks.LILY_OF_THE_VALLEY);
					default -> b(Blocks.SHORT_GRASS);
				};
				q.set(u, 1, v, plant);
			}
		}
		// a cherry tree in the corner: the trunk goes through the pergola
		for (int y = 1; y <= 6; y++) {
			q.set(9, y, 9, LOG);
		}
		for (int lu = -2; lu <= 2; lu++) {
			for (int lv = -2; lv <= 2; lv++) {
				int m = Math.abs(lu) + Math.abs(lv);
				if (m <= 3 && (lu != 0 || lv != 0)) {
					q.set(9 + lu, 7, 9 + lv, leaves(Blocks.CHERRY_LEAVES));
					if (m <= 2) {
						q.set(9 + lu, 8, 9 + lv, leaves(Blocks.CHERRY_LEAVES));
					}
				}
			}
		}
		q.set(9, 7, 9, LOG);
		q.set(9, 8, 9, leaves(Blocks.CHERRY_LEAVES));
		q.tea(8, 5, 2);
		q.set(8, 2, 5, pot(2));
		q.set(9, 2, 5, pot(1));
		q.set(11, 1, 4, b(Blocks.POTTED_FLOWERING_AZALEA));
		q.set(11, 1, 6, b(Blocks.POTTED_BAMBOO));
		q.set(11, 1, 8, q.r.c.kit.gardenPlanter());
		q.lamp(9, 7);
	}

	// ------------------------------------------------------------------ legacy design (scale below 1.9)

	/** Paints the column {@code (x, z)} at offset {@code (dx, dz)}, {@code d} from the centre (legacy, magnified). */
	public static void cell(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		int k = NodeDecor.loungeStep(d);
		long g = BlockPos.asLong(x, 0, z);
		c.cells.remove(g);
		c.layers.remove(g);
		int solid = k % 2 == 0 ? -k / 2 : -(k + 1) / 2;
		c.set(x, solid, z, k % 2 == 0 ? (k == 6 ? c.accent() : MOSS) : FOOT);
		if (k % 2 == 1) {
			c.set(x, solid + 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
		}
		for (int y = solid + (k % 2 == 1 ? 2 : 1); y <= 2; y++) {
			c.set(x, y, z, AIR);
		}
		if (k == BED_RING && Math.min(Math.abs(dx), Math.abs(dz)) > PATH_HALF) {
			int pick = Math.floorMod(dx * 7 + dz * 13, 4);
			BlockState flower = switch (pick) {
				case 0 -> Blocks.FERN.defaultBlockState();
				case 1 -> Blocks.LILY_OF_THE_VALLEY.defaultBlockState();
				case 2 -> Blocks.PINK_PETALS.defaultBlockState();
				default -> Blocks.SHORT_GRASS.defaultBlockState();
			};
			c.set(x, solid + 1, z, flower);
		}
		if (Math.abs(dx) == 5 && Math.abs(dz) == 4) {
			c.set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			c.set(x, 2, z, leaves(Blocks.AZALEA_LEAVES));
		}
	}
}
