package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.CitadelCanvas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
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
 *     hearth (an open campfire between four stone piers, under a bell hood and a hollow 3 x 3 chimney that climbs to
 *     y = 10: nobody can step in, no water anywhere near) stand four curved sofas (a seat of two cherry slabs against a
 *     back of dark oak stairs) and a low table with a pot in front of each. The four 5-wide aisles ({@code |dx| <= 2}
 *     or {@code |dz| <= 2}: cherry centre line, dark oak, polished deepslate edge) stay clear to the hearth.</li>
 * <li><b>Four flights</b> of stairs, 3 wide with a rail on each side, one on each axis: two stone-brick treads with
 *     deepslate-tile edges, the first step head-on, nothing over them; the wall between the flights is a 2-high dark
 *     base with a light coping, a ring of cherry benches and a low rail with pots.</li>
 * <li><b>The terrace</b> ({@code d > 8.5}): flush with the street, with the four 5-wide axes as entrances and a quarter
 *     for each use, from the north-west clockwise: <b>cafe</b> (smooth stone chequer, tiles at the bar; a bar with chest,
 *     barrel and brewing stand, smoker, cauldron, chest; a pantry of barrels, chests and a composter; a low table with
 *     benches), <b>library</b> (dark oak boards, a red rug; shelf walls, two lecterns, a reading table), <b>music</b>
 *     (tile chequer and a cherry dais; a jukebox with its disc, a record rack and chest, speakers, benches and tables
 *     round it), <b>garden</b> (grass and a mossy brick patio; azalea and flower beds, a cherry tree, a tea table).</li>
 * <li><b>The pergola</b> ({@code y = 6}, 4 clear under the awning): an octagonal ring beam of stripped cherry (straight
 *     on the axes, clean 45 degree diagonals joined with slabs) on 12 dark oak posts with knee-brace brackets (the eight
 *     at the entrances make four gates, with a hanging lantern; the four diagonal posts end in lantern finials), an awning
 *     of cherry slabs with a dark oak edge outside it. No glow panel hangs anywhere; glow strips run along the foot of
 *     the bar, the shelves and the rack.</li>
 * </ul>
 *
 * <p>Palette: deepslate tiles and polished deepslate (base), stone brick (coping, hearth, chimney), dark oak (sofa backs,
 * boards, posts), cherry (seats, beams, awning), moss and greens, a little gold in the lanterns. Designed for scale 2; at a larger scale the
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
	/** The ring beam: octagon of half width 11 and diagonal sum 16. The awning reaches out to 13 and 19. */
	private static final int BEAM_R = 11;
	private static final int BEAM_T = 16;
	private static final int AWN_R = 13;
	private static final int AWN_T = 19;
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

		/** The floor of the five-wide axes (pit and entrances): a cherry centre line, dark oak, a polished deepslate edge. */
		BlockState aisle(int a) {
			return switch (a) {
				case 0 -> b(Blocks.CHERRY_PLANKS);
				case 1 -> b(Blocks.DARK_OAK_PLANKS);
				default -> b(Blocks.POLISHED_DEEPSLATE);
			};
		}

		BlockState terrace(int dx, int dz, int ax, int az, int a, double d) {
			if (a <= 2) {
				return aisle(a);
			}
			int sx = dx < 0 ? -1 : 1;
			int sz = dz < 0 ? -1 : 1;
			if (d > 13.0) {
				return theme(sx, sz) == 3 ? GRASS : NodeDecor.footMix(dx, 0, dz);
			}
			if (d <= 9.3) {
				return ((ax + az) & 1) == 0 ? b(Blocks.POLISHED_DEEPSLATE) : NodeDecor.footMix(dx, 0, dz);
			}
			boolean apron = Math.max(ax, az) >= 10;
			boolean rug = ax >= 7 && ax <= 10 && az >= 3 && az <= 7;
			boolean dais = ax >= 8 && ax <= 10 && az >= 8 && az <= 10;
			return switch (theme(sx, sz)) {
				// cafe: smooth stone and polished deepslate in a chequer, deepslate tiles in front of the bar and pantry
				case 0 -> apron ? NodeDecor.footMix(dx, 0, dz) : ((ax + az) & 1) == 0 ? b(Blocks.SMOOTH_STONE) : b(Blocks.POLISHED_DEEPSLATE);
				// library: dark oak boards, tiles in front of the shelves, a red wool rug under the reading table
				case 1 -> rug ? b(Blocks.WOOL.pick(DyeColor.RED)) : apron ? NodeDecor.footMix(dx, 0, dz) : b(Blocks.DARK_OAK_PLANKS);
				// music: tiles and polished deepslate in a chequer, a cherry dais under the jukebox
				case 2 -> dais ? b(Blocks.CHERRY_PLANKS) : ((ax + az) & 1) == 0 ? NodeDecor.footMix(dx, 0, dz) : b(Blocks.POLISHED_DEEPSLATE);
				// garden: grass (plants stand on grass; moss is not accepted by the vegetation support check), a mossy brick patio
				default -> rug ? NodeDecor.stoneMix(dx, 0, dz) : GRASS;
			};
		}

		/**
		 * The hearth: an open campfire in a ring of stone-brick wall, under a bell hood and a tall chimney. Four stone
		 * piers on the corners of the ring carry a lintel 4 above the pit floor (the four sides stay open), a skirt of
		 * stairs flares out round it, and a hollow 3 x 3 shaft rises through the awning's open middle to a crowned top.
		 * Nobody can step into the fire, nothing is wet, and the smoke has a way out.
		 */
		void hearth() {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						set(0, PIT_Y + 1, 0, b(Blocks.CAMPFIRE));
					} else if (dx != 0 && dz != 0) {
						// pier: deepslate tiles at the foot, stone brick above, a polished course under the lintel
						set(dx, PIT_Y + 1, dz, b(Blocks.DEEPSLATE_TILES));
						set(dx, 0, dz, NodeDecor.stoneMix(dx, 0, dz));
						set(dx, 1, dz, b(Blocks.STONE_BRICKS));
						set(dx, 2, dz, b(Blocks.CHISELED_STONE_BRICKS));
					} else {
						set(dx, PIT_Y + 1, dz, b(Blocks.STONE_BRICK_WALL));
					}
				}
			}
			// bell: the lintel ring, and a skirt of stairs that flares out (high back against the lintel)
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					int ax = Math.abs(dx);
					int az = Math.abs(dz);
					if (Math.max(ax, az) == 1) {
						set(dx, 3, dz, b(Blocks.STONE_BRICKS));
					} else if (Math.max(ax, az) == 2 && Math.min(ax, az) <= 1) {
						set(dx, 3, dz, stairs(Blocks.STONE_BRICK_STAIRS, NodeDecor.outward(dx, dz)));
					}
				}
			}
			// the shaft: a hollow 3 x 3 (the campfire's smoke goes up the middle), banded, with a crown and a cap
			for (int y = 4; y <= 8; y++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx != 0 || dz != 0) {
							boolean band = y == 6 || y == 8;
							set(dx, y, dz, band ? b(Blocks.POLISHED_DEEPSLATE) : NodeDecor.stoneMix(dx, y, dz));
						}
					}
				}
			}
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
						set(dx, 9, dz, NodeDecor.topSlab(Blocks.STONE_BRICK_SLAB));
					} else {
						set(dx, 9, dz, b(Blocks.DEEPSLATE_TILES));
						set(dx, 10, dz, slab(Blocks.DEEPSLATE_TILE_SLAB));
					}
				}
			}
		}

		/**
		 * Four curved sofas in the pit. Each is a seat of two cherry slabs against a back of dark oak stairs that follows
		 * the wall, and a low table with a pot between them and the aisle.
		 */
		void sofas() {
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -6; dz <= 6; dz++) {
					double d = Math.hypot(dx, dz);
					if (Math.min(Math.abs(dx), Math.abs(dz)) < 3 || d > PIT_R) {
						continue;
					}
					if (d > 5.5) {
						set(dx, PIT_Y + 1, dz, stairs(Blocks.DARK_OAK_STAIRS, NodeDecor.outward(dx, dz)));
					} else if (d > 4.5) {
						set(dx, PIT_Y + 1, dz, slab(Blocks.CHERRY_SLAB));
					} else {
						set(dx, PIT_Y + 1, dz, slab(Blocks.DARK_OAK_SLAB));
						set(dx, PIT_Y + 2, dz, pot(dx * 2 + dz + 7));
					}
				}
			}
		}

		/**
		 * The edge of the pit: a ring of cherry benches on the coping looking down into the pit (their backs are the rail),
		 * a low rail of stone-brick wall on each side of the four flights and pots on the outer ring of the coping.
		 */
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
					if (a == 2) {
						set(dx, 1, dz, b(Blocks.STONE_BRICK_WALL));
					} else if (d <= 7.5) {
						set(dx, 1, dz, stairs(Blocks.CHERRY_STAIRS, NodeDecor.outward(dx, dz)));
					} else if (Math.floorMod(dx * 5 + dz * 3, 3) == 0) {
						set(dx, 1, dz, pot(dx * 3 + dz));
					}
				}
			}
		}

		/** Whether {@code (ax, az)} (absolute offsets) is inside the octagon of half width {@code r} and diagonal sum {@code t}. */
		private static boolean oct(int ax, int az, int r, int t) {
			return Math.max(ax, az) <= r && ax + az <= t;
		}

		/**
		 * The pergola: a regular-ish octagon ring beam (straight sides on the axes, clean 45 degree diagonals) on twelve
		 * dark oak posts with knee-brace brackets, and outside it an awning of cherry slabs flat under the beam out to an
		 * octagonal edge (so the bars, shelves and the rack are under a roof). The entrances stay open between
		 * their posts. The diagonal posts rise into lantern finials.
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
					if (!oct(ax, az, AWN_R, AWN_T)) {
						continue;
					}
					if (oct(ax, az, BEAM_R, BEAM_T)) {
						boolean strictlyIn = oct(ax, az, BEAM_R - 1, BEAM_T - 1);
						if (!strictlyIn) {
							if (ax == BEAM_R && az <= BEAM_R - 7) {
								set(dx, ROOF_Y, dz, beam(Direction.Axis.Z));
							} else if (az == BEAM_R && ax <= BEAM_R - 7) {
								set(dx, ROOF_Y, dz, beam(Direction.Axis.X));
							} else {
								set(dx, ROOF_Y, dz, b(Blocks.STRIPPED_CHERRY_WOOD));
							}
						} else if (ax + az == BEAM_T - 1 && Math.max(ax, az) <= BEAM_R - 1) {
							// fills the step between two diagonal cells: the diagonal reads as one band
							set(dx, ROOF_Y, dz, NodeDecor.topSlab(Blocks.CHERRY_SLAB));
						}
						continue;
					}
					if (a >= 3) {
												set(dx, ROOF_Y - 1, dz, NodeDecor.topSlab(Blocks.CHERRY_SLAB));
						int sx = dx < 0 ? -1 : 1;
						int sz = dz < 0 ? -1 : 1;
						if (theme(sx, sz) == 3 && Math.hypot(dx, dz) > 12.2 && Math.floorMod(ax + az * 2, 3) != 0) {
							set(dx, ROOF_Y, dz, leaves(Blocks.CHERRY_LEAVES));
						}
					}
				}
			}
			int[][] posts = {{11, 3}, {11, -3}, {-11, 3}, {-11, -3}, {3, 11}, {-3, 11}, {3, -11}, {-3, -11}, {8, 8}, {8, -8}, {-8, 8}, {-8, -8}};
			for (int[] p : posts) {
				set(p[0], 1, p[1], b(Blocks.STONE_BRICKS));
				for (int y = 2; y < ROOF_Y; y++) {
					set(p[0], y, p[1], b(Blocks.STRIPPED_DARK_OAK_LOG));
				}
				// knee braces: upside-down stairs under the beam, full height against the post
				int sx = Integer.signum(p[0]);
				int sz = Integer.signum(p[1]);
				int[][] braces;
				if (Math.abs(p[0]) == BEAM_R) {
					braces = new int[][] {{p[0], p[1] - 1}, {p[0], p[1] + 1}};
				} else if (Math.abs(p[1]) == BEAM_R) {
					braces = new int[][] {{p[0] - 1, p[1]}, {p[0] + 1, p[1]}};
				} else {
					braces = new int[][] {{p[0] - sx, p[1]}, {p[0], p[1] - sz}};
				}
				for (int[] br : braces) {
					int ddx = p[0] - br[0];
					int ddz = p[1] - br[1];
					Direction towardPost = ddx > 0 ? Direction.EAST : ddx < 0 ? Direction.WEST : ddz > 0 ? Direction.SOUTH : Direction.NORTH;
					set(br[0], ROOF_Y - 1, br[1], NodeDecor.invStairs(Blocks.DARK_OAK_STAIRS, towardPost.getOpposite()));
				}
				if (Math.abs(p[0]) == 8) {
					// finial: the post climbs through the beam to a lantern
					set(p[0], ROOF_Y + 1, p[1], b(Blocks.DARK_OAK_FENCE));
					set(p[0], ROOF_Y + 2, p[1], lanternUp());
				}
			}
			// the four gates: a lantern under the beam over each entrance, and two under each diagonal
			for (int[] g : new int[][] {{11, 0}, {-11, 0}, {0, 11}, {0, -11}}) {
				put(g[0], ROOF_Y - 1, g[1], lanternDown(), HANGING);
			}
			for (int sx : new int[] {-1, 1}) {
				for (int sz : new int[] {-1, 1}) {
					put(sx * 9, ROOF_Y - 1, sz * 7, lanternDown(), HANGING);
					put(sx * 7, ROOF_Y - 1, sz * 9, lanternDown(), HANGING);
				}
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
			set(u, 1, v, slab(Blocks.DARK_OAK_SLAB));
		}

		/** A low table {@code len} long along u at row {@code v}, a bench on each side facing it. */
		void tea(int u0, int v, int len) {
			for (int u = u0; u < u0 + len; u++) {
				table(u, v);
				bench(u, v - 1, toZ());
				bench(u, v + 1, toZ().getOpposite());
			}
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
	}

	/** Garden: azalea and flower beds on grass along v = 10..11, a cherry tree in the corner, a tea table with benches. */
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
