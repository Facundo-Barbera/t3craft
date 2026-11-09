package dev.t3craft.hq.citadel.nodes;

import dev.t3craft.hq.RingCampusGeometry.Kind;
import dev.t3craft.hq.RingCampusGeometry.Node;
import dev.t3craft.hq.RingCampusGeometry.Zone;
import dev.t3craft.hq.citadel.CitadelCanvas;
import dev.t3craft.hq.citadel.CitadelHub;
import dev.t3craft.hq.citadel.CitadelNodes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.BAMBOO;
import static dev.t3craft.hq.citadel.CitadelCanvas.CALCITE;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.LOG;
import static dev.t3craft.hq.citadel.CitadelCanvas.RIB;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * The scheduled-tasks rotunda: a round drum of radius 7 under a banded dome with a glass cupola.
 *
 * <ul>
 * <li><b>Floor</b>: a clock face: 12 hour marks at d = 5 (lights at 12, 3, 6 and 9), hands at ten past ten
 *     and an accent ring at d = 4.</li>
 * <li><b>Drum</b> (d &gt; 6, 10 high): dark base (a cracked one in six), a calcite body, cherry-log pilasters on the
 *     8 rays of 45 degrees, tall glass windows between them (y 4..8), a lintel course, a lit band at the
 *     top, and doors 3 wide on the four sides.</li>
 * <li><b>Dome</b> (radius 7.2 over the drum): a dark skirt of deepslate tiles, cherry planks, a golden cap of
 *     bamboo mosaic; wherever the ring inside is exactly one higher the top block is a stair, so the dome is
 *     smooth; four lanterns hang from it on the diagonals.</li>
 * <li><b>Cupola</b> on top: 3 × 3 of glass between four logs, a light inside it, a stair roof, a gold block and
 *     a rod.</li>
 * </ul>
 */
public final class RotundaRoom {
	/** Radius inside which the hall is open (the wall is further out). */
	private static final double WALL_R = 6.0;
	/** Top of the drum (a lit band). */
	private static final int WALL_TOP = 10;
	/** Radius of the dome sphere. */
	private static final double DOME_R = 7.2;
	/** The dome is stretched in height by this factor (a taller, more pointed dome). */
	private static final double STRETCH = 1.2;
	/** Height of the dome above the drum at the centre. */
	private static final int DOME_H = (int) Math.round(STRETCH * DOME_R);

	private static int domeTop(double d) {
		return WALL_TOP + (int) Math.round(STRETCH * Math.sqrt(Math.max(0, DOME_R * DOME_R - d * d)));
	}

	private RotundaRoom() {
	}

	/** Paints the column {@code (x, z)} at offset {@code (dx, dz)}, {@code d} from the centre. */
	public static void cell(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		floor(c, x, z, dx, dz, d);
		wall(c, x, z, dx, dz, d);
		dome(c, x, z, dx, dz, d);
	}

	/** The floor: a clock face. */
	public static void floor(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		BlockState s = Math.abs(d - 4) < 0.5 ? c.accent() : c.floor();
		for (int h = 0; h < 12; h++) {
			double a = h * Math.PI / 6;
			if (dx == (int) Math.round(5 * Math.sin(a)) && dz == (int) Math.round(-5 * Math.cos(a))) {
				s = h % 3 == 0 ? LIGHT : RIB;
			}
		}
		for (int r = 1; r <= 3; r++) {
			double hour = 5 * Math.PI / 3;
			double minute = Math.PI / 3;
			boolean h1 = r <= 2 && dx == (int) Math.round(r * Math.sin(hour)) && dz == (int) Math.round(-r * Math.cos(hour));
			boolean m1 = dx == (int) Math.round(r * Math.sin(minute)) && dz == (int) Math.round(-r * Math.cos(minute));
			if (h1 || m1) {
				s = RIB;
			}
		}
		c.set(x, 0, z, s);
	}

	/** The drum. */
	public static void wall(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		if (d <= WALL_R || d > WALL_R + 1.0) {
			return; // inside the hall, or the apron round the drum when the citadel is scaled (the zone grows, the room does not)
		}
		double off = NodeDecor.rayOffset(dx, dz);
		for (int y = 1; y <= WALL_TOP; y++) {
			BlockState s;
			if (y == WALL_TOP) {
				s = c.kit.wallPanel(true);
			} else if (off < 0.55) {
				s = y <= 2 ? RIB : LOG;
			} else if (y <= 2) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y == 3) {
				s = CALCITE;
			} else if (y <= WALL_TOP - 2 && off >= 1.3) {
				s = GLASS;
			} else if (y == WALL_TOP - 1) {
				s = RIB;
			} else {
				s = CALCITE;
			}
			c.set(x, y, z, s);
		}
		if (CitadelNodes.door(dx, dz, 6)) {
			c.fill(x, 1, 4, z, AIR);
		}
	}

	private static BlockState shell(int y, net.minecraft.core.Direction inward, boolean stair) {
		Block full;
		Block stairs;
		if (y <= WALL_TOP + 2) {
			full = Blocks.DEEPSLATE_TILES;
			stairs = Blocks.DEEPSLATE_TILE_STAIRS;
		} else if (y >= WALL_TOP + DOME_H - 2) {
			full = Blocks.BAMBOO_MOSAIC;
			stairs = Blocks.BAMBOO_MOSAIC_STAIRS;
		} else {
			full = Blocks.CHERRY_PLANKS;
			stairs = Blocks.CHERRY_STAIRS;
		}
		return stair ? stairs(stairs, inward) : full.defaultBlockState();
	}

	/** The dome and its cupola. */
	public static void dome(CitadelCanvas c, int x, int z, int dx, int dz, double d) {
		int top = domeTop(d);
		// the shell starts at the top of the ring outside it, so the rings touch face to face
		int low = domeTop(Math.min(DOME_R, d + 1));
		int lo = Math.max(WALL_TOP + 1, Math.min(low, top));
		int topIn = domeTop(Math.max(0, d - 1));
		net.minecraft.core.Direction in = NodeDecor.inward(dx, dz);
		for (int y = lo; y <= top; y++) {
			boolean ramp = y == top && d >= 1 && topIn == top + 1;
			c.set(x, y, z, d < 1 ? GLASS : shell(y, in, ramp));
		}
		// the cupola: 3 x 3 of glass between four logs, a light inside, a roof, a gold block, a rod
		if (d < 1.5) {
			boolean center = dx == 0 && dz == 0;
			boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
			for (int y = top + 1; y <= top + 3; y++) {
				if (center) {
					if (y == top + 2) {
						c.set(x, y, z, LIGHT);
					}
				} else {
					c.set(x, y, z, corner ? LOG : GLASS);
				}
			}
			c.set(x, top + 4, z, center ? BAMBOO : stairs(Blocks.CHERRY_STAIRS, in));
			if (center) {
				c.set(x, top + 5, z, NodeDecor.ROD);
			}
		}
		// a lantern hangs from the dome on each diagonal at d ~ 4.5
		if (NodeDecor.rayOffset(dx, dz) < 0.55 && d >= 4.2 && d <= 4.9) {
			c.put(x, lo - 1, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), HANGING);
		}
	}

	// =====================================================================================================
	// The scaled rotunda (scale > 1): painted directly at its real size, not magnified from the design size.
	//
	// Plan (R = the zone's radius, 14 at scale 2; everything is derived from it):
	//   hall      radius R - 2.5 (11.5), open floor. The floor clock is a complete dial of calcite (gold rim, dark
	//             outer ring, 12 marks, hands at ten past ten) pushed 3 west of the centre so nothing stands on it;
	//             the planning table (a cartography table) is the hub the hands turn on
	//   drum      d in (R - 2.5, R - 1], height h = sy(10) (14): deepslate base, calcite string course, 16 cherry
	//             pilasters, tall windows with blackstone mullions, a dark blackstone cornice; doors 5 wide, 5 high
	//             on N, S and W (the street runs N-S through the node; the east side is the display wall)
	//   tambour   a dark clerestory drum of 4 courses on the cornice: string course, deepslate frieze with a
	//             blackstone strip over every pilaster and one lit oculus over every window, upper cornice
	//   plinth    d in (R - 1, R]: a slab ring with a buttress at every pilaster
	//   dome      uniform frosted glass on 8 cherry ribs, a cherry belt at the springing, a
	//             bamboo cap; an open oculus under the lantern (a round glass lantern drum with a roof, a gold
	//             ball and a rod)
	//   display   the east side of the hall is cut flat at x = xw: a wall of archive shelves with the
	//             "schedule" screen (7 x 3, y 2..4) framed by piers, a lintel and a cornice, facing west; over it
	//             a row of plaques (the colours of the board and its title)
	//   seating   two rows of benches facing the screen, a 3-wide aisle between them, a clear stage in front of
	//             the screen with a presenter's atril on each side
	//   archive   twelve month chests with a plaque each, and a register atril (with its plaque) after every
	//             third one, round the west and north-south part of the drum wall; door plaques over N, S and W
	// =====================================================================================================

	/** Dimensions of the scaled rotunda, all from the zone's radius. */
	private static final BlockState SLOG = b(Blocks.STRIPPED_CHERRY_LOG);
	/** The dome's skin: frosted, so the room under it does not show through. */
	private static final BlockState DOME_GLASS = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState();
	/** Courses of the dark tambour between the colonnade and the dome. */
	private static final int TAMBOUR = 4;
	/** A lit oculus of the tambour: warm, so it glows like a lamp and does not burn out. */
	private static final BlockState OCULUS = b(Blocks.OCHRE_FROGLIGHT);
	/** The months of the archive chests. */
	private static final String[] MONTHS = {"ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC"};

	private record Geo(double size, double ri, double rw, double rd, int h, int dh, int xw, double lr, double dial) {
		static Geo of(CitadelCanvas c, Node nd) {
			double size = nd.size();
			double ri = size - 2.5;
			double rw = size - 1.0;
			double rd = rw - 0.5;
			int h = c.sy(10);
			int dh = (int) Math.round(1.12 * rd);
			int xw = (int) Math.floor(ri) - 1;
			double lr = size >= 12 ? 3.5 : 2.0;
			return new Geo(size, ri, rw, rd, h, dh, xw, lr, Math.round(ri * 0.43));
		}

		/** x of the screen plane (one in front of the display wall). */
		int xs() {
			return xw - 1;
		}

		int doorH() {
			return Math.min(5, h - 4);
		}

		/** Springing of the dome: the top of the tambour. */
		int base() {
			return h + TAMBOUR;
		}

		/** x of the centre of the floor clock (west of the middle of the hall, so the seats stay off the dial). */
		int dialX() {
			return -3;
		}

		/** Top of the dome shell at distance {@code d} from the centre. */
		int top(double d) {
			double q = Math.max(0, 1 - (d / rd) * (d / rd));
			return base() + (int) Math.round(dh * Math.sqrt(q));
		}
	}

	/** Arc distance (in cells) from the cell to the nearest of {@code n} rays; {@code phase} shifts the rays by that many steps. */
	private static double arc(int dx, int dz, int n, double phase) {
		double d = Math.hypot(dx, dz);
		if (d < 0.5) {
			return 99;
		}
		double step = 2 * Math.PI / n;
		double a = Math.atan2(dz, dx) / step - phase;
		return Math.abs(a - Math.rint(a)) * step * d;
	}

	private static boolean in(CitadelCanvas c, Node nd, int dx, int dz) {
		Zone zn = c.zoneAt(nd.cx() + dx, nd.cz() + dz);
		return zn.kind() == Kind.NODE && zn.node() == nd.k();
	}

	/** A doorway: 5 wide on the north, south and west axes, through the drum and the plinth. */
	private static boolean door(Geo g, int dx, int dz) {
		int a = Math.min(Math.abs(dx), Math.abs(dz));
		int m = Math.max(Math.abs(dx), Math.abs(dz));
		return a <= 2 && m >= (int) Math.floor(g.ri) - 1 && !(dx > 0 && Math.abs(dz) <= 2);
	}

	/** The pier beside a doorway. */
	private static boolean jamb(Geo g, int dx, int dz) {
		int a = Math.min(Math.abs(dx), Math.abs(dz));
		int m = Math.max(Math.abs(dx), Math.abs(dz));
		return a == 3 && m >= (int) Math.floor(g.ri) - 1 && !(dx > 0);
	}

	/** Paints the whole scaled rotunda: every column of the node, then the furniture. Fixtures go after (see {@link #fixtures}). */
	public static void build(CitadelCanvas c, Node nd) {
		Geo g = Geo.of(c, nd);
		int span = (int) Math.ceil(g.size) + 1;
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				if (!in(c, nd, dx, dz)) {
					continue;
				}
				int x = nd.cx() + dx;
				int z = nd.cz() + dz;
				double d = Math.hypot(dx, dz);
				dialFloor(c, g, x, z, dx, dz, d);
				drum(c, g, x, z, dx, dz, d);
				tambour(c, g, x, z, dx, dz, d);
				displayWall(c, g, x, z, dx, dz, d);
				glassDome(c, g, x, z, dx, dz, d);
			}
		}
		furnishHall(c, g, nd);
	}

	/** The fixtures of the scaled rotunda: the schedule screen (7 x 3 at reading height, with its base) and a walk spot in the aisle. */
	public static void fixtures(CitadelCanvas c, Node nd) {
		Geo g = Geo.of(c, nd);
		int cx = nd.cx();
		int cz = nd.cz();
		CitadelHub.bigScreen(c, new dev.t3craft.hq.HqGeometry.Surface(new net.minecraft.core.BlockPos(cx + g.xs(), 2, cz - 3),
				net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST, 7, 3), "schedule", "", true);
		c.walkSpot(new net.minecraft.core.BlockPos(cx + 5, 1, cz), net.minecraft.core.Direction.EAST);
	}

	// ------------------------------------------------------------------ floor: the clock

	private static void dialFloor(CitadelCanvas c, Geo g, int x, int z, int dx, int dz, double d) {
		BlockState s = c.floor();
		if (d > g.ri) {
			s = door(g, dx, dz) ? c.floor() : CitadelCanvas.FOOT;
			c.set(x, 0, z, s);
			return;
		}
		if (d > g.ri - 1.0) {
			s = RIB; // a dark skirting ring under the archive chests
		}
		int u = dx - g.dialX();
		double r = Math.hypot(u, dz);
		double face = g.dial - 0.5;
		if (r <= face) {
			s = CALCITE;
			double mr = face - 1.0;
			for (int hr = 0; hr < 12; hr++) {
				double a = hr * Math.PI / 6;
				int mx = (int) Math.round(mr * Math.sin(a));
				int mz = (int) Math.round(-mr * Math.cos(a));
				if (u == mx && dz == mz) {
					s = b(Blocks.GOLD_BLOCK);
				}
			}
			if (hand(u, dz, 5 * Math.PI / 3, mr - 0.4, 0.6) || hand(u, dz, Math.PI / 3, mr + 0.7, 0.6)) {
				s = RIB;
			}
		} else if (r <= face + 1.0) {
			s = BAMBOO;
		} else if (r <= face + 2.0) {
			s = RIB;
		}
		c.set(x, 0, z, s);
	}

	/** A clock hand from the hub along the clockwise angle {@code a} from north, {@code len} long, {@code thick} wide. */
	private static boolean hand(int dx, int dz, double a, double len, double thick) {
		double ux = Math.sin(a);
		double uz = -Math.cos(a);
		double t = dx * ux + dz * uz;
		double perp = Math.abs(dx * uz - dz * ux);
		return t >= 0.9 && t <= len && perp <= thick;
	}

	// ------------------------------------------------------------------ the drum and its plinth

	private static void drum(CitadelCanvas c, Geo g, int x, int z, int dx, int dz, double d) {
		if (d <= g.ri) {
			return;
		}
		boolean door = door(g, dx, dz);
		double pil = arc(dx, dz, 16, 0);
		if (d > g.rw) {
			// plinth: a slab ring, a buttress where a pilaster meets the ground
			if (!door) {
				c.set(x, g.h - 1, z, RIB);
				c.set(x, g.h, z, NodeDecor.topSlab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB));
				if (jamb(g, dx, dz)) {
					c.set(x, 1, z, CitadelCanvas.FOOT);
					c.set(x, 2, z, CitadelCanvas.FOOT);
					c.put(x, 3, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false), CitadelCanvas.DECOR);
				} else if (pil < 0.7) {
					c.set(x, 1, z, CitadelCanvas.FOOT);
					c.set(x, 2, z, CitadelCanvas.FOOT);
					c.set(x, 3, z, stairs(Blocks.DEEPSLATE_TILE_STAIRS, NodeDecor.inward(dx, dz)));
				} else {
					c.set(x, 1, z, slab(Blocks.DEEPSLATE_TILE_SLAB));
				}
			}
			return;
		}
		boolean jamb = jamb(g, dx, dz);
		boolean mullion = arc(dx, dz, 16, 0.5) < 0.5;
		for (int y = 1; y <= g.h; y++) {
			BlockState s;
			if (door && y <= g.doorH()) {
				c.set(x, y, z, AIR);
				continue;
			}
			if (y == g.h) {
				// the cornice of the colonnade: dark, with an upside-down stair lip all round
				s = d > g.rd - 0.5 ? NodeDecor.topSlab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB) : RIB;
			} else if (jamb || pil < 0.55) {
				s = y <= 2 ? RIB : SLOG;
			} else if (y <= 3) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y == 4) {
				s = CALCITE;
			} else if (y <= g.h - 3 && pil >= 1.3) {
				s = mullion ? RIB : GLASS;
			} else if (y == g.h - 2) {
				s = RIB;
			} else {
				s = CALCITE;
			}
			c.set(x, y, z, s);
		}
	}

	/**
	 * The tambour: a dark drum of {@link #TAMBOUR} courses between the colonnade and the dome. Course 1 a blackstone
	 * string course (a slab ledge outside), courses 2 to 4 a deepslate frieze with a blackstone strip over each of the
	 * 16 pilaster axes and one lit oculus (a froglight, framed by the strips and a lintel) over each window, the top
	 * course a blackstone cornice with an upside-down stair lip.
	 */
	private static void tambour(CitadelCanvas c, Geo g, int x, int z, int dx, int dz, double d) {
		if (d <= g.ri || d > g.rw) {
			return;
		}
		double pil = arc(dx, dz, 16, 0);
		boolean strip = pil < 0.6;
		boolean oculus = arc(dx, dz, 16, 0.5) < 0.5;
		boolean wall = d <= g.rd;
		net.minecraft.core.Direction in = NodeDecor.inward(dx, dz);
		for (int i = 1; i <= TAMBOUR; i++) {
			int y = g.h + i;
			BlockState s = null;
			if (i == 1) {
				s = wall ? RIB : slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
			} else if (i == TAMBOUR) {
				s = wall ? RIB : NodeDecor.topSlab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
			} else if (strip) {
				s = RIB;
			} else if (wall) {
				s = oculus && i == 2 ? OCULUS : NodeDecor.footMix(x, y, z);
			}
			if (s != null) {
				c.set(x, y, z, s);
			}
		}
	}

	// ------------------------------------------------------------------ the flat east wall: shelves behind the screen

	private static void displayWall(CitadelCanvas c, Geo g, int x, int z, int dx, int dz, double d) {
		if (d > g.ri || dx < g.xw) {
			return;
		}
		for (int y = 1; y <= g.h; y++) {
			BlockState s;
			if (y == g.h) {
				s = RIB;
			} else if (dx > g.xw) {
				s = CitadelCanvas.FOOT;
			} else if (y <= 3) {
				s = NodeDecor.footMix(x, y, z);
			} else if (y <= 6) {
				s = CALCITE;
			} else if (y == g.h - 2) {
				s = RIB;
			} else if (y == g.h - 1) {
				s = CALCITE;
			} else if (Math.floorMod(dz, 3) == 0) {
				s = SLOG;
			} else if (y % 2 == 1) {
				s = CitadelCanvas.b(Blocks.BOOKSHELF);
			} else {
				s = NodeDecor.facing(Blocks.CHISELED_BOOKSHELF, net.minecraft.core.Direction.WEST);
			}
			c.set(x, y, z, s);
		}
	}

	// ------------------------------------------------------------------ the glass dome and its lantern

	private static void glassDome(CitadelCanvas c, Geo g, int x, int z, int dx, int dz, double d) {
		if (d > g.rd) {
			return;
		}
		int base = g.base();
		int top = g.top(d);
		int low = g.top(Math.min(g.rd, d + 1));
		int lo = Math.max(base + 1, Math.min(low, top));
		net.minecraft.core.Direction in = NodeDecor.inward(dx, dz);
		int topIn = g.top(Math.max(0, d - 1));
		if (d >= g.lr - 0.5) {
			double rib = arc(dx, dz, 8, 0);
			for (int y = lo; y <= top; y++) {
				int rel = y - base;
				boolean ramp = y == top && d >= 1 && topIn == top + 1;
				BlockState s;
				if (rel <= 2) {
					s = CitadelCanvas.PLANKS; // the cherry belt at the springing
				} else if (rel >= g.dh - 1 && d < g.lr + 2.0) {
					s = y == top && d > g.lr + 0.5 ? stairs(Blocks.CHERRY_STAIRS, in) : CitadelCanvas.PLANKS;
				} else if (rib < 0.6 && d >= g.lr + 1.5) {
					s = ramp ? stairs(Blocks.CHERRY_STAIRS, in) : SLOG;
				} else {
					s = DOME_GLASS;
				}
				c.set(x, y, z, s);
			}
		}
		// the lantern: a round drum of glass between 8 posts, a light inside, a tiered roof with an eave,
		// a gold ball and a rod
		if (d <= g.lr + 1.0) {
			int lb = g.top(g.lr);
			if (d <= g.lr && d > g.lr - 1.0) {
				for (int y = lb + 1; y <= lb + 5; y++) {
					c.set(x, y, z, arc(dx, dz, 8, 0) < 0.6 ? SLOG : GLASS);
				}
			}
			if (dx == 0 && dz == 0) {
				c.set(x, lb + 3, z, LIGHT);
			}
			double[] tier = {g.lr + 1.0, g.lr - 0.5, g.lr - 2.0};
			for (int t = 0; t < tier.length; t++) {
				if (d <= tier[t]) {
					boolean edge = d > tier[t] - 1.0;
					c.set(x, lb + 6 + t, z, edge ? stairs(t < 2 ? Blocks.CHERRY_STAIRS : Blocks.BAMBOO_MOSAIC_STAIRS, in) : BAMBOO);
				}
			}
			if (dx == 0 && dz == 0) {
				c.set(x, lb + 9, z, b(Blocks.GOLD_BLOCK));
				c.set(x, lb + 10, z, NodeDecor.ROD);
			}
		}
	}

	// ------------------------------------------------------------------ furniture

	private static void furnishHall(CitadelCanvas c, Geo g, Node nd) {
		BlockState lanternUp = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
		net.minecraft.core.Direction west = net.minecraft.core.Direction.WEST;
		// the planning table is the hub of the floor clock: a cartography table the hands turn on
		put(c, nd, g.dialX(), 1, 0, b(Blocks.CARTOGRAPHY_TABLE), CitadelCanvas.SOLID);
		// two rows of benches facing the screen, a 3-wide aisle between them; the dial ends before the first row
		for (int row : new int[] {4, 6}) {
			for (int dz = 2; dz <= 6; dz++) {
				put(c, nd, row, 1, dz, stairs(Blocks.CHERRY_STAIRS, west), CitadelCanvas.SOLID);
				put(c, nd, row, 1, -dz, stairs(Blocks.CHERRY_STAIRS, west), CitadelCanvas.SOLID);
			}
		}
		// a red runner down the aisle, from the dial to the stage, so the room leads to the screen
		for (int rx = 4; rx <= 8; rx++) {
			for (int rz = -1; rz <= 1; rz++) {
				put(c, nd, rx, 1, rz, Blocks.CARPET.pick(DyeColor.RED).defaultBlockState(), CitadelCanvas.DECOR);
			}
		}
		// the archive: twelve month chests and, after every third, a register atril, each with a plaque on the wall
		archive(c, g, nd);
		// the frame of the screen (plane xs): piers, a lintel, a cornice, lanterns on top
		int xs = g.xs();
		for (int dz = -5; dz <= 5; dz++) {
			int az = Math.abs(dz);
			for (int y = 1; y <= 6; y++) {
				if (az <= 3 && y >= 1 && y <= 4) {
					continue; // the screen and its base
				}
				BlockState s;
				if (y == 6) {
					s = NodeDecor.topSlab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
				} else if (y == 1) {
					s = CitadelCanvas.FOOT;
				} else if (y == 5) {
					s = az <= 3 ? CitadelCanvas.PLANKS : az == 4 ? SLOG : RIB;
				} else {
					s = az == 4 ? SLOG : RIB;
				}
				put(c, nd, xs, y, dz, s, CitadelCanvas.SOLID);
			}
		}
		// the board's title and key over the screen: what each colour of the screen means (see feat-schedule-board)
		sign(c, nd, xs, 7, -4, west, DyeColor.CYAN, "Corriendo", "ahora");
		sign(c, nd, xs, 7, -2, west, DyeColor.RED, "Fallo", "ultima vez");
		sign(c, nd, xs, 7, 0, west, DyeColor.ORANGE, "Proximas", "tareas", "programadas");
		sign(c, nd, xs, 7, 2, west, DyeColor.LIME, "Todo bien", "ultima vez");
		sign(c, nd, xs, 7, 4, west, DyeColor.GRAY, "Pausada", "o bloqueada");
		// a presenter's atril at each side of the screen, a planter beyond it
		for (int sz : new int[] {-1, 1}) {
			put(c, nd, xs - 1, 1, 6 * sz, NodeDecor.facing(Blocks.LECTERN, west).setValue(BlockStateProperties.HAS_BOOK, true), CitadelCanvas.SOLID);
			put(c, nd, xs - 1, 1, 8 * sz, c.kit.gardenPlanter(), CitadelCanvas.SOLID);
		}
		put(c, nd, xs, 7, -5, lanternUp, CitadelCanvas.DECOR);
		put(c, nd, xs, 7, 5, lanternUp, CitadelCanvas.DECOR);
		// door plaques over the pilasters above the three doors
		sign(c, nd, -11, 7, 0, net.minecraft.core.Direction.EAST, DyeColor.ORANGE, false, "Rotonda", "Tareas", "programadas");
		sign(c, nd, 0, 7, -11, net.minecraft.core.Direction.SOUTH, DyeColor.ORANGE, false, "Rotonda", "Tareas", "programadas");
		sign(c, nd, 0, 7, 11, net.minecraft.core.Direction.NORTH, DyeColor.ORANGE, false, "Rotonda", "Tareas", "programadas");
		// eight chandeliers on the ribs: a chain from the dome and a lantern above head height
		for (int j = 0; j < 8; j++) {
			double a = j * Math.PI / 4;
			int dx = (int) Math.round(8.5 * Math.cos(a));
			int dz = (int) Math.round(8.5 * Math.sin(a));
			double d = Math.hypot(dx, dz);
			if (dx >= g.xs() || !in(c, nd, dx, dz) || arc(dx, dz, 16, 0) > 0.6) {
				continue;
			}
			int lo = Math.max(g.base() + 1, Math.min(g.top(Math.min(g.rd, d + 1)), g.top(d)));
			for (int y = g.h - 3; y < lo; y++) {
				put(c, nd, dx, y, dz, Blocks.IRON_CHAIN.defaultBlockState(), CitadelCanvas.HANGING);
			}
			put(c, nd, dx, g.h - 4, dz, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), CitadelCanvas.HANGING);
		}
	}

	/**
	 * The archive of the rotunda: the cells of the ring just inside the drum wall (clear of the three doors and of the
	 * display wall), in clockwise order from the west door. Sixteen are used, evenly spread: after every third month
	 * chest comes a register atril (a lectern with its book). Each has a plaque above it on the wall behind: the
	 * month, or the quarter.
	 */
	private static void archive(CitadelCanvas c, Geo g, Node nd) {
		java.util.List<int[]> ring = new java.util.ArrayList<>();
		int span = (int) Math.ceil(g.ri);
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				double d = Math.hypot(dx, dz);
				if (d > g.ri || d <= g.ri - 1.0 || Math.min(Math.abs(dx), Math.abs(dz)) < 4 || dx >= g.xw - 3 || !in(c, nd, dx, dz)) {
					continue;
				}
				ring.add(new int[] {dx, dz});
			}
		}
		ring.sort(java.util.Comparator.comparingDouble(p -> Math.atan2(p[1], p[0])));
		int slots = 16;
		if (ring.size() < slots) {
			slots = ring.size();
		}
		int month = 0;
		int quarter = 0;
		for (int i = 0; i < slots; i++) {
			int[] p = ring.get((int) Math.round(i * (ring.size() - 1) / (double) Math.max(1, slots - 1)));
			net.minecraft.core.Direction face = NodeDecor.inward(p[0], p[1]);
			if (i % 4 == 3 || month >= MONTHS.length) {
				put(c, nd, p[0], 1, p[1], NodeDecor.facing(Blocks.LECTERN, face).setValue(BlockStateProperties.HAS_BOOK, true), CitadelCanvas.SOLID);
				sign(c, nd, p[0], 2, p[1], face, DyeColor.YELLOW, "Registro", "T" + (++quarter));
			} else {
				put(c, nd, p[0], 1, p[1], NodeDecor.facing(Blocks.CHEST, face), CitadelCanvas.SOLID);
				sign(c, nd, p[0], 2, p[1], face, DyeColor.ORANGE, "Archivo", MONTHS[month++]);
			}
		}
	}

	/** A plaque (a fixture, never replaced by the light pass) at the node offset {@code (dx, y, dz)}, its front towards {@code face}. */
	private static void sign(CitadelCanvas c, Node nd, int dx, int y, int dz, net.minecraft.core.Direction face, DyeColor color, String... lines) {
		sign(c, nd, dx, y, dz, face, color, true, lines);
	}

	private static void sign(CitadelCanvas c, Node nd, int dx, int y, int dz, net.minecraft.core.Direction face, DyeColor color, boolean backing, String... lines) {
		if (!in(c, nd, dx, dz)) {
			return;
		}
		int x = nd.cx() + dx;
		int z = nd.cz() + dz;
		if (backing && in(c, nd, dx - face.getStepX(), dz - face.getStepZ())) {
			c.set(x - face.getStepX(), y, z - face.getStepZ(), CALCITE); // a pale ground, so the text reads
		}
		c.put(x, y, z, c.kit.plaque(face), CitadelCanvas.DECOR);
		long k = BlockPos.asLong(x, y, z);
		c.data.put(k, c.kit.plaqueData(color, lines));
		c.fixtures.add(k);
	}

	private static void put(CitadelCanvas c, Node nd, int dx, int y, int dz, BlockState s, int layer) {
		if (in(c, nd, dx, dz)) {
			c.put(nd.cx() + dx, y, nd.cz() + dz, s, layer);
		}
	}
}
