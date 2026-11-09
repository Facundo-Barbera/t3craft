package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.DECOR;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.fh;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

import dev.t3craft.hq.HqGeometry;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.DeskSpot;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.citadel.CitadelCanvas.Mats;
import dev.t3craft.hq.citadel.barrio.HallStyle;
import dev.t3craft.hq.lead.LeadOffices;
import dev.t3craft.hq.plan.Anchor;
import dev.t3craft.hq.plan.AnchorNames;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import org.jspecify.annotations.Nullable;

/**
 * feat/lead-office: a lead's office in a citadel hall ({@link HqLayout.Type#LEAD_OFFICE} module on the ground
 * floor, {@link #isLead}). {@code CitadelBarrios.hall} skips the generic furniture for it and calls
 * {@link #flight} (two-floor offices only) and {@link #place}. All cells below are {@code (u, v)} of the hall's
 * {@link Room} ({@code u} along the depth towards the back wall, {@code v} along the width, {@code mid} the
 * centre line); nothing is a bare size: everything follows {@code r.w()}, {@code r.d()} and {@code fh()}, so the
 * office is right at every citadel scale (it was designed at scale 1 and 2, the default).
 *
 * <h2>Two sizes</h2>
 * <ul>
 * <li><b>Large hall</b> ({@code w >= 13}, the default scale 2: 17 x 20, 7 high): one floor holds everything.</li>
 * <li><b>Small hall</b> ({@code w < 13}, scale 1: 9 x 10): the office takes <i>both</i> floors of the hall
 * ({@link #twoFloors}; a lead never shares its barrio) to get the space: the briefing room below, a planning room
 * above, joined by a flight of stairs along the {@code v = 0} wall.</li>
 * </ul>
 *
 * <pre>
 *   briefing room (ground floor)
 *   u=D-1  chests and barrels under the big screen (7x3 small, 11x4 large) between two standards
 *   u=D-3  the lead's desk (3 wide, against the monitor side); the lead stands at u=D-4, in the aisle, facing it
 *   u=D-5, D-7, D-9  rows of chairs either side of the aisle (3 rows at most), all facing the lead and the screen
 *   west wall: notes whiteboard (4 / 8 wide) at the lead's right hand
 *   u=0..  lobby: archive shelves on the west wall, tub plants, lanterns
 *   large hall: meeting table (4x2, 4 chairs a side) on the east side with a presentation board on the front wall,
 *               a work counter on the east wall (bench, chest, barrel, lectern, maps, shelves)
 *   small hall: flight of stairs on the east wall; upstairs the planning room (table 4x2, 8 chairs, rug, board on
 *               the front wall, counter on the back wall, shelves)
 * </pre>
 *
 * Walk rules: the aisle (3 wide small, 5 large) runs from the door to the lead's desk; rows of chairs leave a free
 * lane in front of every chair (that cell is the {@code leadseat} anchor, where a child awaiting review stands);
 * {@code LeadBriefing} sends a new child 1.6 blocks behind the lead (in the aisle: free). The stairs of a small
 * office climb along the east wall: first step ahead of whoever enters, two free cells over every step.
 *
 * <p>The big screen is {@code bigscreen_lead@<module>@WxH=project:lead:<npcId>}, the lead's children feed; that
 * key also tells the generic fallback ({@code LeadOfficeDecor}) that this module already has its office.
 */
public final class CitadelLeadOffice {
	private CitadelLeadOffice() {
	}

	/** True for the ground-floor module of a lead office with a thread. */
	public static boolean isLead(HqLayout.@Nullable Module mod) {
		return mod != null && mod.type() == HqLayout.Type.LEAD_OFFICE && !mod.desks().isEmpty()
				&& RingCampusGeometry.floorOf(mod.index()) == 0;
	}

	/** True when the hall is too small for everything on one floor: the office takes both floors. */
	public static boolean twoFloors(Room r) {
		return r.w() < 13;
	}

	/**
	 * The flight of stairs of a two-floor lead hall: along the wall at {@code v = 0}, climbing towards the back, so
	 * the centre of the briefing room stays free. Replaces {@code CitadelBarrios.hallStairs} and moves the hall's
	 * stair hole from the centre line to this flight.
	 */
	public static void flight(CitadelCanvas c, Room r, HallStyle style) {
		int h = fh();
		int mid = r.c();
		for (int u = h - 4; u <= h - 1; u++) {
			c.set(r.cell(u, mid, h), c.floor());
			c.set(r.cell(u, 0, h), AIR);
		}
		for (int i = 0; i < h; i++) {
			BlockPos p = r.cell(1 + i, 0, 1 + i);
			for (int y = 1; y <= i; y++) {
				c.set(p.atY(y), style.stairCore());
			}
			c.set(p, stairs(style.stairBlock(), r.back()));
		}
	}

	/**
	 * Paints the office of lead module {@code mod}. Returns false when {@code mod} is not a lead office on the
	 * ground floor (nothing painted; the caller draws the ordinary desks).
	 */
	public static boolean place(CitadelCanvas c, HqLayout.Module mod, Room r, int f, HallStyle style, Mats mats) {
		if (f != 0 || !isLead(mod)) {
			return false;
		}
		DyeColor env = HqStyle.envColor(mod);
		boolean two = twoFloors(r);
		ground(c, mod, r, style, mats, env, two);
		if (two) {
			upper(c, mod, r, style, mats, env);
		} else {
			meeting(c, mod, r, style, mats, env);
		}
		dress(c, r, style, env, two);
		flag(c, r, style, env, two ? 2 * fh() : fh());
		return true;
	}

	/**
	 * The lead's flag: a pole on the highest block of the roof over the middle of the hall, with a 2x2 pennant of
	 * the environment's colour (concrete, so it also reads in the plan renders). The only thing that tells a lead's
	 * hall from its neighbours at a distance. Call after the roof.
	 */
	private static void flag(CitadelCanvas c, Room r, HallStyle style, DyeColor env, int ceiling) {
		// the first column along the ridge whose highest block is not a chimney (bricks)
		BlockPos at = r.cell(r.d() / 2, r.c(), 0);
		int top = ceiling;
		for (int u : new int[] {r.d() / 2, r.d() / 4, 3 * r.d() / 4, r.d() / 3, 2 * r.d() / 3, 2}) {
			BlockPos p = r.cell(u, r.c(), 0);
			for (int y = ceiling + fh() + 12; y >= ceiling; y--) {
				BlockState s = c.at(p.getX(), y, p.getZ());
				if (s != null && !s.isAir()) {
					if (!s.is(Blocks.BRICKS)) {
						at = p;
						top = y;
					}
					break;
				}
			}
			if (top > ceiling && at.equals(p)) {
				break;
			}
		}
		// a tall pole of dark wall blocks with a gold finial, and a swallow-tailed banner 8 long and 6 high in the
		// environment's colour with a stripe of its second colour (concrete: it also reads in the plan renders)
		int pole = 10;
		BlockState mast = b(Blocks.DEEPSLATE_BRICK_WALL);
		for (int y = top + 1; y <= top + pole; y++) {
			c.set(at.getX(), y, at.getZ(), mast);
		}
		c.set(at.getX(), top + pole + 1, at.getZ(), b(Blocks.GOLD_BLOCK));
		BlockState cloth = b(Blocks.CONCRETE.pick(env));
		BlockState stripe = b(Blocks.CONCRETE.pick(DyeColor.YELLOW));
		int[] length = {8, 8, 8, 7, 5, 3};
		for (int row = 0; row < length.length; row++) {
			for (int k = 1; k <= length[row]; k++) {
				BlockPos p = at.relative(r.side(), k);
				c.set(p.getX(), top + pole - row, p.getZ(), row == 1 || row == 4 ? stripe : cloth);
			}
		}
		// a yard arm: the banner hangs from a bar along its top edge
		for (int k = 1; k <= 8; k++) {
			BlockPos p = at.relative(r.side(), k);
			c.set(p.getX(), top + pole + 1, p.getZ(), b(Blocks.DEEPSLATE_BRICK_WALL));
		}
	}

	// ------------------------------------------------------------------ the briefing room

	private static void ground(CitadelCanvas c, HqLayout.Module mod, Room r, HallStyle style, Mats mats, DyeColor env, boolean two) {
		int w = r.w();
		int d = r.d();
		int mid = r.c();
		int h = fh();
		boolean big = !two;
		int ah = big ? 2 : 1;
		String thread = mod.desks().get(0).threadId();
		@Nullable String lead = mod.leadNpcId();
		Direction fwd = r.back();
		Direction side = r.side();

		// the runner of the aisle (door to the lead's desk): the edges in the second colour
		BlockState runner = b(Blocks.CARPET.pick(env));
		BlockState trim = b(Blocks.CARPET.pick(DyeColor.BLACK));
		BlockState line = b(Blocks.CARPET.pick(DyeColor.YELLOW));
		for (int u = 0; u < dais0(r); u++) {
			for (int dv = -ah; dv <= ah; dv++) {
				c.set(r.cell(u, mid + dv, 1), Math.abs(dv) == ah ? trim : big && Math.abs(dv) == ah - 1 ? line : runner);
			}
		}

		// the lead's desk: three blocks across the end of the aisle, the lead stands in the aisle in front of it and
		// looks at its monitor and, behind, the big screen (LeadBriefing sends a new child to a spot 1.6 blocks behind
		// the lead, which is the aisle: reachable)
		DeskSpot spot = new DeskSpot(r.cell(d - 3, mid, 1), fwd);
		CitadelBarrios.deskAt(c, mod.index(), 0, spot, thread);

		// the big screen on the back wall, above the lead's monitor, and a low cabinet of chests and barrels under it
		int sw = big ? Math.min(11, w - 4) : Math.min(7, w - 2);
		int sh = h >= 8 ? 4 : 3;
		int half = sw / 2;
		BlockPos origin = r.cell(d - 1, mid - half, 3);
		CitadelHub.bigScreen(c, new HqGeometry.Surface(origin, side, fwd.getOpposite(), sw, sh), LeadOffices.SCREEN_KEY + mod.index(),
				"project:" + mod.projectKey(), true);
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, fwd.getOpposite());
		BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, fwd.getOpposite());
		for (int v = mid - half, i = 0; v <= mid + half; v++, i++) {
			c.set(r.cell(d - 1, v, 1), i % 2 == 0 ? chest : barrel);
		}
		// the lead's standards in the two back corners: a pole topped with a block of the environment's colour
		BlockState pole = b(style.fence());
		BlockState finial = b(Blocks.CONCRETE.pick(env));
		for (int v : new int[] {0, w - 1}) {
			for (int y = 1; y <= 3; y++) {
				c.set(r.cell(d - 1, v, y), pole);
			}
			c.set(r.cell(d - 1, v, 4), finial);
		}

		// the waiting area: rows of chairs (3 at most) either side of the aisle, all looking at the lead; the cell in
		// front of each chair is the leadseat anchor (the nearest seats first)
		BlockState chair = stairs(Blocks.CHERRY_STAIRS, fwd.getOpposite());
		int seat = 0;
		int rows = 0;
		int step = big ? 3 : 2;
		for (int u = big ? d - 6 : d - 5; u >= 2 && rows < 3; u -= step, rows++) {
			for (int off = ah + 1; off <= Math.min(mid - 1, ah + 3); off++) {
				for (int sgn = -1; sgn <= 1; sgn += 2) {
					BlockPos cell = r.cell(u, mid + sgn * off, 1);
					c.set(cell, chair);
					if (lead != null) {
						c.anchor(Anchor.at(AnchorNames.leadSeat(lead, seat++), cell.relative(fwd), fwd));
					}
				}
			}
		}

		// the notes whiteboard on the west wall (v = w - 1), at the lead's right hand
		int bn = big ? 8 : 4;
		int brows = h >= 8 ? 4 : 3;
		int bu = d - 1 - bn;
		board(c, mats, env, mod.index(), bn, brows, 1, (i, y) -> r.cell(bu + i, w - 1, y), (i, y) -> r.cell(bu + i, w, y));

		// the lobby: archive shelves on the west wall, tub plants, lanterns over the lanes
		int shelves = big ? 6 : 3;
		int sHigh = h >= 8 ? 3 : 2;
		for (int u = 1; u <= shelves; u++) {
			for (int y = 1; y <= sHigh; y++) {
				c.set(r.cell(u, w - 1, y), b(Blocks.BOOKSHELF));
			}
		}
		BlockPos pot = r.cell(2, w - 1, sHigh + 1);
		c.put(pot.getX(), pot.getY(), pot.getZ(), HallStyle.pot(HallStyle.hash(mod.index(), 5)), DECOR);
		tub(c, r.cell(0, w - 1, 1));
		if (big) {
			tub(c, r.cell(0, mid - ah - 1, 1));
			tub(c, r.cell(0, mid + ah + 1, 1));
			tub(c, r.cell(d - 2, 1, 1));
			tub(c, r.cell(d - 2, w - 2, 1));
			lounge(c, mod, r, env, style);
		}
		ceiling(c, r, big);
		floors(c, r, big, ah, env);
	}

	// ------------------------------------------------------------------ look: ceiling, floors, walls, screen frame

	private static boolean big(Room r) {
		return !twoFloors(r);
	}

	/** First row ({@code u}) of the command dais in front of the lead's desk (it ends at {@code d - 2}). */
	private static int dais0(Room r) {
		return big(r) ? r.d() - 5 : r.d() - 4;
	}

	/** Half width of the command dais (centre line plus/minus this). */
	private static int daisHalf(Room r) {
		return big(r) ? 5 : 3;
	}

	/** Half width of the big screen (same figure as {@code ground}). */
	private static int screenHalf(Room r) {
		return (big(r) ? Math.min(11, r.w() - 4) : Math.min(7, r.w() - 2)) / 2;
	}

	private static boolean free(CitadelCanvas c, BlockPos p) {
		BlockState s = c.at(p.getX(), p.getY(), p.getZ());
		return s == null || s.isAir();
	}

	private static void putFree(CitadelCanvas c, BlockPos p, BlockState s) {
		if (free(c, p)) {
			c.set(p, s);
		}
	}

	private static BlockState log(Direction.Axis axis) {
		return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	private static BlockState upsideDown(Block block, Direction facing) {
		return stairs(block, facing).setValue(StairBlock.HALF, Half.TOP);
	}

	/**
	 * The coffered ceiling: a lattice of dark beams just under the ceiling plane (large hall: crossbeams every 6 rows
	 * that rest on the wall pilasters, three beams along the depth; small hall: two crossbeams), with a lantern hanging
	 * from every crossing.
	 */
	private static void ceiling(CitadelCanvas c, Room r, boolean big) {
		int w = r.w();
		int d = r.d();
		int mid = r.c();
		int h = fh();
		BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
		BlockState across = log(r.side().getAxis());
		BlockState along = log(r.back().getAxis());
		if (big) {
			int[] vs = {3, mid, w - 4};
			for (int v : vs) {
				for (int u = 0; u <= d - 2; u++) {
					c.set(r.cell(u, v, h - 1), along);
				}
			}
			for (int u = 3; u < d - 2; u += 6) {
				for (int v = 0; v < w; v++) {
					c.set(r.cell(u, v, h - 1), across);
					c.set(r.cell(u, v, h - 2), across);
				}
				for (int v : vs) {
					BlockPos p = r.cell(u, v, h - 3);
					c.put(p.getX(), p.getY(), p.getZ(), lantern, HANGING);
				}
			}
			// the coffers: a dark inlay in the ceiling plane at the middle of every panel between the beams
			BlockState inlay = b(Blocks.DARK_OAK_PLANKS);
			for (int u = 3; u + 6 < d - 1; u += 6) {
				for (int a = 0; a < vs.length - 1; a++) {
					for (int uu = u + 2; uu <= u + 4; uu++) {
						for (int v = vs[a] + 2; v <= vs[a + 1] - 2; v++) {
							c.set(r.cell(uu, v, h), inlay);
						}
					}
				}
			}
			return;
		}
		for (int u : new int[] {2, d - 4}) {
			for (int v = 1; v < w; v++) {
				c.set(r.cell(u, v, h - 1), across);
			}
			for (int v : new int[] {2, w - 3}) {
				BlockPos p = r.cell(u, v, h - 2);
				c.put(p.getX(), p.getY(), p.getZ(), lantern, HANGING);
			}
		}
	}

	/**
	 * Floors by zone: the lobby row stays stone; the waiting wings (either side of the aisle) are a 2x2 checker of the
	 * hall's floor and bamboo mosaic; the command dais in front of the lead's desk is deepslate (tile border, polished
	 * field) with a medallion of the environment's colour where the lead stands. Rugs of the lounge and the meeting
	 * corner are their own zones. All of it is the floor plane (y = 0): nothing changes height or walkability.
	 */
	private static void floors(CitadelCanvas c, Room r, boolean big, int ah, DyeColor env) {
		int w = r.w();
		int d = r.d();
		int mid = r.c();
		int d0 = dais0(r);
		int dh = daisHalf(r);
		if (big) {
			for (int v = 0; v < w; v++) {
				for (int u = 0; u <= 1; u++) {
					BlockPos p = r.cell(u, v, 0);
					c.set(p.getX(), 0, p.getZ(), c.accent());
				}
			}
		}
		for (int u = big ? 2 : 1; u < d0; u++) {
			for (int v = 0; v < w; v++) {
				if (Math.abs(v - mid) <= ah) {
					continue;
				}
				BlockPos p = r.cell(u, v, 0);
				boolean alt = (Math.floorMod(u / 2, 2) + Math.floorMod(v / 2, 2)) % 2 == 0;
				c.set(p.getX(), 0, p.getZ(), alt ? CitadelCanvas.CALCITE : c.floor());
			}
		}
		BlockState border = b(Blocks.DEEPSLATE_TILES);
		BlockState field = b(Blocks.POLISHED_DEEPSLATE);
		BlockState medal = b(Blocks.CONCRETE.pick(env));
		for (int u = d0; u <= d - 2; u++) {
			for (int v = mid - dh; v <= mid + dh; v++) {
				boolean edge = u == d0 || u == d - 2 || Math.abs(v - mid) == dh;
				BlockPos p = r.cell(u, v, 0);
				BlockState s = edge ? border : field;
				if (u == d - 4 && Math.abs(v - mid) <= 1) {
					s = v == mid ? b(Blocks.GOLD_BLOCK) : medal;
				}
				c.set(p.getX(), 0, p.getZ(), s);
			}
		}
	}

	/**
	 * What dresses the room once the furniture stands (so it only ever takes cells that are still air): gilded frame
	 * round the big screen, lamp posts at the dais corners, stained clerestory in the top band of the walls, and on
	 * every floor of the office the deepslate pilasters (base, polished shaft, chiselled band, flared capital, bracket
	 * under the ceiling, a foot step) on the wall piers with a dark skirting of stairs between them.
	 */
	private static void dress(CitadelCanvas c, Room r, HallStyle style, DyeColor env, boolean two) {
		int w = r.w();
		int d = r.d();
		int mid = r.c();
		int h = fh();
		int ah = two ? 1 : 2;
		Direction fwd = r.back();
		Direction side = r.side();
		int sh = h >= 8 ? 4 : 3;
		int half = screenHalf(r);

		// the frame round the big screen (it is drawn on y = 3 .. 2 + sh at u = d - 1): gold on a dark corner
		BlockState gold = b(Blocks.GOLD_BLOCK);
		BlockState corner = b(Blocks.CHISELED_POLISHED_BLACKSTONE);
		int yTop = 3 + sh;
		for (int v = mid - half - 1; v <= mid + half + 1; v++) {
			boolean end = v == mid - half - 1 || v == mid + half + 1;
			BlockPos rail = r.cell(d - 1, v, 2);
			if (end) {
				putFree(c, rail, corner);
			} else {
				c.set(rail, gold);
			}
			BlockPos top = r.cell(d - 1, v, yTop);
			if (yTop <= h) {
				c.set(top, end ? corner : gold);
			}
		}
		for (int y = 3; y < yTop; y++) {
			putFree(c, r.cell(d - 1, mid - half - 1, y), gold);
			putFree(c, r.cell(d - 1, mid + half + 1, y), gold);
		}

		// lamp posts on the dais, either side of the desk
		int lp = two ? 3 : 4;
		BlockState post = b(Blocks.DEEPSLATE_BRICK_WALL);
		for (int sgn = -1; sgn <= 1; sgn += 2) {
			BlockPos p = r.cell(d - 2, mid + sgn * lp, 1);
			if (free(c, p) && free(c, p.above()) && free(c, p.above(2))) {
				c.set(p, post);
				c.set(p.above(), post);
				c.put(p.getX(), p.getY() + 2, p.getZ(), Blocks.LANTERN.defaultBlockState(), DECOR);
			}
		}

		BlockState base = b(Blocks.DEEPSLATE_BRICKS);
		BlockState shaft = b(Blocks.POLISHED_DEEPSLATE);
		BlockState band = b(Blocks.CHISELED_DEEPSLATE);
		BlockState cap = b(Blocks.DEEPSLATE_TILES);
		for (int f = 0; f < (two ? 2 : 1); f++) {
			int yb = f * h;
			clerestory(c, r, style, env, yb);
			if (!two) {
				wallBay(c, r, d - 2 - 8, w - 1, side, true, yb, base, shaft, band, cap);
			}
			for (int v = 0; v < w; v++) {
				if (Math.abs(v - mid) > ah + 1) {
					wallBay(c, r, 0, v, fwd.getOpposite(), v % 3 == 0 && v > 0 && v < w - 1 && Math.abs(v - mid) > ah + 2, yb, base, shaft, band, cap);
				}
				wallBay(c, r, d - 1, v, fwd, v % 3 == 0 && v > 0 && v < w - 1 && Math.abs(v - mid) > half + 2, yb, base, shaft, band, cap);
			}
			for (int u = 1; u < d - 1; u++) {
				boolean pier = u % 3 == 0 && u >= 3 && u <= d - 4;
				if (!two) {
					wallBay(c, r, u, 0, side.getOpposite(), pier, yb, base, shaft, band, cap);
				}
				wallBay(c, r, u, w - 1, side, pier, yb, base, shaft, band, cap);
			}
		}
	}

	/** One interior cell against a wall: a pilaster when {@code pier}, else a dark skirting stair. {@code toWall} points at the wall. */
	private static void wallBay(CitadelCanvas c, Room r, int u, int v, Direction toWall, boolean pier, int yb, BlockState base, BlockState shaft,
			BlockState band, BlockState cap) {
		int h = fh();
		BlockPos foot = r.cell(u, v, yb + 1);
		if (!free(c, foot)) {
			return;
		}
		if (!pier) {
			c.set(foot, stairs(Blocks.DEEPSLATE_TILE_STAIRS, toWall));
			return;
		}
		Direction in = toWall.getOpposite();
		for (int ly = 1; ly <= h - 1; ly++) {
			BlockPos p = r.cell(u, v, yb + ly);
			if (!free(c, p)) {
				continue;
			}
			BlockState s;
			if (ly == 1) {
				s = base;
			} else if (ly <= h - 4) {
				s = shaft;
			} else if (ly == h - 3) {
				s = band;
			} else if (ly == h - 2) {
				s = upsideDown(Blocks.DEEPSLATE_TILE_STAIRS, toWall);
			} else {
				s = cap;
			}
			c.set(p, s);
		}
		// a foot step in front of the base and a bracket under the ceiling, only if the cells are free
		putFree(c, foot.relative(in), stairs(Blocks.DEEPSLATE_BRICK_STAIRS, toWall));
		putFree(c, r.cell(u, v, yb + h - 1).relative(in), upsideDown(Blocks.DEEPSLATE_BRICK_STAIRS, toWall));
	}

	/**
	 * The top band of the walls as stained glass: in every window column (the piers stay stone) the rows
	 * {@code h - 2 .. h - 1} of the floor become panes of the environment's colour, its second colour and yellow in turn,
	 * where there is room inside (nothing built against the wall at that height).
	 */
	private static void clerestory(CitadelCanvas c, Room r, HallStyle style, DyeColor env, int yb) {
		int w = r.w();
		int d = r.d();
		int h = fh();
		DyeColor[] colours = {DyeColor.YELLOW, DyeColor.PINK, env, DyeColor.LIGHT_BLUE};
		for (int u = -1; u <= d; u++) {
			for (int v = -1; v <= w; v++) {
				if (!r.wall(u, v) || r.corner(u, v)) {
					continue;
				}
				int t = u == -1 || u == d ? v : u;
				if (Math.floorMod(t, 3) == 0) {
					continue;
				}
				int iu = Math.max(0, Math.min(d - 1, u));
				int iv = Math.max(0, Math.min(w - 1, v));
				BlockState pane = b(Blocks.STAINED_GLASS_PANE.pick(colours[Math.floorMod(t / 3, colours.length)]));
				for (int ly = h >= 7 ? h - 3 : h - 2; ly <= h - 1; ly++) {
					BlockPos p = r.cell(u, v, yb + ly);
					BlockState cur = c.at(p.getX(), p.getY(), p.getZ());
					if (cur == null || cur.isAir() || !(ly >= 4 || cur.getBlock().toString().contains("glass_pane"))) {
						continue;
					}
					if (free(c, r.cell(iu, iv, yb + ly))) {
						c.set(p, pane);
					}
				}
			}
		}
	}

	/**
	 * Large hall: a lounge on the west side of the lobby (between the shelves and the aisle): a low table with eight
	 * seats round it on a rug, for whoever waits for the lead. Seats leave a one-cell lane to the aisle side.
	 */
	private static void lounge(CitadelCanvas c, HqLayout.Module mod, Room r, DyeColor env, HallStyle style) {
		int w = r.w();
		int mid = r.c();
		Direction fwd = r.back();
		Direction side = r.side();
		int v0 = mid + 3;
		int v1 = w - 2;
		int u0 = 3;
		int u1 = 9;
		BlockState rug = b(Blocks.CARPET.pick(env));
		BlockState rugTrim = b(Blocks.CARPET.pick(style.alt(env)));
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				c.set(r.cell(u, v, 1), u == u0 || u == u1 || v == v0 || v == v1 ? rugTrim : rug);
			}
		}
		int tu0 = u0 + 2;
		int tu1 = u1 - 2;
		int tv0 = v0 + 2;
		int tv1 = v1 - 2;
		BlockState top = slab(Blocks.CHERRY_SLAB);
		for (int u = tu0; u <= tu1; u++) {
			for (int v = tv0; v <= tv1; v++) {
				c.set(r.cell(u, v, 1), top);
			}
		}
		BlockPos pot = r.cell((tu0 + tu1) / 2, tv0, 2);
		c.put(pot.getX(), pot.getY(), pot.getZ(), HallStyle.pot(HallStyle.hash(mod.index(), 11)), DECOR);
		for (int u = tu0; u <= tu1; u++) {
			c.set(r.cell(u, v0 + 1, 1), stairs(Blocks.BAMBOO_MOSAIC_STAIRS, side.getOpposite()));
			c.set(r.cell(u, v1 - 1, 1), stairs(Blocks.BAMBOO_MOSAIC_STAIRS, side));
		}
		for (int v = tv0; v <= tv1; v++) {
			c.set(r.cell(u0 + 1, v, 1), stairs(Blocks.BAMBOO_MOSAIC_STAIRS, fwd.getOpposite()));
			c.set(r.cell(u1 - 1, v, 1), stairs(Blocks.BAMBOO_MOSAIC_STAIRS, fwd));
		}
	}

	/** A tub plant: a barrel with a flowering azalea bush on top. */
	private static void tub(CitadelCanvas c, BlockPos base) {
		c.set(base, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		c.set(base.above(), leaves(Blocks.FLOWERING_AZALEA_LEAVES));
	}

	/**
	 * A notes board on a wall: {@code n} wide, {@code rows} high (rows {@code y0 + 1 .. y0 + rows}) of white concrete
	 * with a few coloured notes (a pattern of {@code seed}, not random), a cabinet of planks under it ({@code y0}) and a
	 * frame over it. The window panes of the wall behind it become wall.
	 *
	 * @param in   the cell of the board for column {@code i} at height {@code y}
	 * @param wall the cell of the wall behind it
	 */
	private static void board(CitadelCanvas c, Mats mats, DyeColor env, int seed, int n, int rows, int y0,
			BiFunction<Integer, Integer, BlockPos> in, BiFunction<Integer, Integer, BlockPos> wall) {
		DyeColor[] notes = {DyeColor.YELLOW, DyeColor.LIGHT_BLUE, DyeColor.LIME, DyeColor.PINK, env};
		BlockState white = b(Blocks.CONCRETE.pick(DyeColor.WHITE));
		for (int i = 0; i < n; i++) {
			for (int y = y0 + 1; y <= y0 + rows; y++) {
				BlockPos wp = wall.apply(i, y);
				BlockState cur = c.at(wp.getX(), wp.getY(), wp.getZ());
				if (cur != null && cur.getBlock().toString().contains("glass_pane")) {
					c.set(wp, mats.wall());
				}
				int hsh = HallStyle.hash(seed, i, y);
				c.set(in.apply(i, y), hsh % 5 == 0 ? b(Blocks.CONCRETE.pick(notes[hsh / 5 % notes.length])) : white);
			}
			c.set(in.apply(i, y0), PLANKS);
			c.set(in.apply(i, y0 + rows + 1), PLANKS);
		}
	}

	// ------------------------------------------------------------------ large hall: the meeting corner

	/**
	 * Large hall only (everything on one floor): the meeting table on the east side, 4 long with 4 chairs a side, a
	 * rug under it and a presentation board on the front wall at its head; and the work counter on the east wall.
	 */
	private static void meeting(CitadelCanvas c, HqLayout.Module mod, Room r, HallStyle style, Mats mats, DyeColor env) {
		int d = r.d();
		int h = fh();
		Direction side = r.side();
		Direction toRoom = side;
		BlockState fence = b(style.fence());
		BlockState rug = b(Blocks.CARPET.pick(env));
		BlockState rugTrim = b(Blocks.CARPET.pick(style.alt(env)));
		int u0 = 4;
		int u1 = 7;
		// table on v = 2..3, chairs on v = 1 and v = 4; the rug under all of it
		for (int u = u0 - 1; u <= u1 + 1; u++) {
			for (int v = 1; v <= 4; v++) {
				boolean edge = u == u0 - 1 || u == u1 + 1 || v == 1 || v == 4;
				c.set(r.cell(u, v, 1), edge ? rugTrim : rug);
			}
		}
		BlockState top = slab(Blocks.CHERRY_SLAB);
		for (int u = u0; u <= u1; u++) {
			for (int v = 2; v <= 3; v++) {
				c.set(r.cell(u, v, 1), u == u0 || u == u1 ? fence : rug);
				c.set(r.cell(u, v, 2), top);
			}
		}
		BlockPos centre = r.cell((u0 + u1) / 2, 2, 3);
		c.put(centre.getX(), centre.getY(), centre.getZ(), HallStyle.pot(HallStyle.hash(mod.index(), 9)), DECOR);
		for (int u = u0; u <= u1; u++) {
			c.set(r.cell(u, 1, 1), stairs(Blocks.DARK_OAK_STAIRS, toRoom.getOpposite()));
			c.set(r.cell(u, 4, 1), stairs(Blocks.DARK_OAK_STAIRS, toRoom));
		}
		for (int v = 2; v <= 3; v++) {
			c.set(r.cell(u0 - 1, v, 1), stairs(Blocks.DARK_OAK_STAIRS, r.back().getOpposite()));
			c.set(r.cell(u1 + 1, v, 1), stairs(Blocks.DARK_OAK_STAIRS, r.back()));
		}
		// the presentation board on the front wall (u = 0), 4 wide at the table's head, 3 or 4 high
		int brows = h >= 8 ? 4 : 3;
		board(c, mats, env, mod.index() + 7, 4, brows, 1, (i, y) -> r.cell(0, 1 + i, y), (i, y) -> r.cell(-1, 1 + i, y));

		// the work counter on the east wall (v = 0): a bench, storage, a lectern, maps, then shelves; stand on v = 1
		Direction face = side;
		BlockState[] counter = {b(Blocks.CRAFTING_TABLE), Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, face),
				Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP),
				Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, face), b(Blocks.CARTOGRAPHY_TABLE)};
		int cu = 10;
		for (int i = 0; i < counter.length + 4 && cu + i <= d - 4; i++) {
			c.set(r.cell(cu + i, 0, 1), i < counter.length ? counter[i] : b(Blocks.BOOKSHELF));
		}
		for (int i : new int[] {0, 2}) {
			BlockPos p = r.cell(cu + i, 0, 2);
			c.put(p.getX(), p.getY(), p.getZ(), HallStyle.pot(HallStyle.hash(mod.index(), i)), DECOR);
		}
	}

	// ------------------------------------------------------------------ small hall: the planning room upstairs

	private static void upper(CitadelCanvas c, HqLayout.Module mod, Room r, HallStyle style, Mats mats, DyeColor env) {
		int w = r.w();
		int d = r.d();
		int mid = r.c();
		int h = fh();
		Direction toDoor = r.back().getOpposite();
		Direction side = r.side();
		int yu = h + 1;
		BlockState fence = b(style.fence());
		// the rail of the stair hole (the flight along the wall at v = 0)
		for (int u = h - 4; u <= h - 1; u++) {
			c.set(r.cell(u, 1, yu), fence);
		}
		c.set(r.cell(h - 5, 0, yu), fence);

		// a rug under the table and its chairs (the chairs and legs go over it)
		BlockState rug = b(Blocks.CARPET.pick(env));
		BlockState rugTrim = b(Blocks.CARPET.pick(style.alt(env)));
		int u0 = 3;
		int u1 = d - 4;
		int t1 = mid + 1;
		int t2 = mid + 2;
		for (int u = u0 - 1; u <= u1 + 1; u++) {
			for (int v = mid; v <= mid + 3; v++) {
				boolean edge = u == u0 - 1 || u == u1 + 1 || v == mid || v == mid + 3;
				c.set(r.cell(u, v, yu), edge ? rugTrim : rug);
			}
		}
		// the conference table (4x2, legs at the ends) with eight chairs and a pot
		BlockState top = slab(Blocks.CHERRY_SLAB);
		for (int u = u0; u <= u1; u++) {
			for (int v = t1; v <= t2; v++) {
				c.set(r.cell(u, v, yu), u == u0 || u == u1 ? fence : rug);
				c.set(r.cell(u, v, yu + 1), top);
			}
		}
		BlockPos centre = r.cell((u0 + u1) / 2, t1, yu + 2);
		c.put(centre.getX(), centre.getY(), centre.getZ(), HallStyle.pot(HallStyle.hash(mod.index(), 9)), DECOR);
		for (int u = u0; u <= u1; u++) {
			c.set(r.cell(u, mid, yu), stairs(Blocks.CHERRY_STAIRS, side.getOpposite()));
			c.set(r.cell(u, mid + 3, yu), stairs(Blocks.CHERRY_STAIRS, side));
		}

		// the presentation wall on the front wall (u = 0): a 5 wide notes board, flanked by tub plants
		int bw = Math.min(5, w - 4);
		int bv0 = mid - bw / 2;
		board(c, mats, env, mod.index() + 7, bw, 3, yu, (i, y) -> r.cell(0, bv0 + i, y), (i, y) -> r.cell(-1, bv0 + i, y));
		tub(c, r.cell(0, bv0 - 1, yu));
		tub(c, r.cell(0, bv0 + bw, yu));

		// the work counter on the back wall: a bench, storage, a lectern, maps, shelves
		BlockState[] counter = {b(Blocks.CRAFTING_TABLE), Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, toDoor),
				Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP),
				Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, toDoor), b(Blocks.CARTOGRAPHY_TABLE)};
		for (int v = 2, i = 0; v <= w - 2; v++, i++) {
			c.set(r.cell(d - 1, v, yu), i < counter.length ? counter[i] : b(Blocks.BOOKSHELF));
		}
		for (int v : new int[] {2, 4}) {
			BlockPos p = r.cell(d - 1, v, yu + 1);
			c.put(p.getX(), p.getY(), p.getZ(), HallStyle.pot(HallStyle.hash(mod.index(), v)), DECOR);
		}
		// bookshelf columns on the west wall piers, two high
		for (int u = 6; u <= d - 1; u += 3) {
			c.set(r.cell(u, w - 1, yu), b(Blocks.BOOKSHELF));
			c.set(r.cell(u, w - 1, yu + 1), b(Blocks.BOOKSHELF));
		}
		// a rug on the landing at the top of the flight, and lanterns over the room
		for (int u = h + 1; u <= d - 2; u++) {
			for (int v = 1; v <= 3; v++) {
				c.set(r.cell(u, v, yu), u == h + 1 || v == 1 ? rugTrim : rug);
			}
		}
		BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
		for (int u : new int[] {3, d - 4}) {
			for (int v : new int[] {2, w - 2}) {
				BlockPos p = r.cell(u, v, 2 * h - 1);
				c.put(p.getX(), p.getY(), p.getZ(), lantern, HANGING);
			}
		}
	}
}
