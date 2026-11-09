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

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqGeometry;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.DeskSpot;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.citadel.CitadelCanvas.Mats;
import dev.t3craft.hq.citadel.barrio.HallStyle;
import dev.t3craft.hq.lead.LeadOffices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * feat/lead-office: a lead's office in a citadel hall, built on <b>both floors</b> of the hall (twice the
 * floor area of an ordinary desk hall; a lead never shares its barrio, so the upper floor is free). The hall
 * is a {@link HqLayout.Type#LEAD_OFFICE} module on the ground floor ({@link #isLead}); {@code CitadelBarrios.hall}
 * builds it with two floors, skips the generic furniture and the centre stairs, and calls {@link #flight} and
 * {@link #place} instead. All cells below are {@code (u, v)} of the hall's {@link Room} ({@code u} along the
 * depth towards the back wall, {@code v} along the width, {@code mid} the centre line).
 *
 * <pre>
 *   ground floor, the briefing room            upper floor, the planning room
 *   u=D-1  banner [chests/barrels] banner      counter: bench, chest, barrel, lectern, maps, shelves
 *          big screen 7x3 on the wall          (door of stairs: the flight up the west wall)
 *   u=D-2  the lead stands here, behind...      lanes 3 wide
 *   u=D-3  ...its desk (3 wide, faces the door) conference table 3x2 with 6 chairs
 *   u=6    free lane                            stair hole with a railing (west wall)
 *   u=5    row of chairs (4)  aisle  (4)
 *   u=4    free lane
 *   u=3    row of chairs (4)  aisle  (4)       whiteboard on the east wall (kanban notes)
 *   u=0..2 lobby, the flight of stairs up (west wall), a tub plant
 *   (door, centre of the u=-1 wall)
 * </pre>
 *
 * Walk rules: the central aisle (3 wide) runs from the door to the lead's pocket; rows of chairs leave a free
 * lane in front of every chair (that cell is the {@code leadseat} anchor, where a child awaiting review stands);
 * the stairs climb along the west wall: first step ahead of whoever enters, two free cells over every step.
 *
 * <p>The big screen is {@code bigscreen_lead@<module>@7x3=project:lead:<npcId>}, the lead's children feed; that
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

	/**
	 * The flight of stairs of a lead hall: along the west wall ({@code v = 0}), climbing towards the back, so the
	 * centre of the briefing room stays free. Replaces {@code CitadelBarrios.hallStairs} and moves the hall's
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
	 * Paints the office of lead module {@code mod} (both floors of hall {@code r}). Returns false when {@code mod}
	 * is not a lead office on the ground floor (nothing painted; the caller draws the ordinary desks).
	 */
	public static boolean place(CitadelCanvas c, HqLayout.Module mod, Room r, int f, HallStyle style, Mats mats) {
		if (f != 0 || !isLead(mod)) {
			return false;
		}
		int w = r.w();
		int d = r.d();
		int mid = r.c();
		int h = fh();
		DyeColor env = HqStyle.envColor(mod);
		String thread = mod.desks().get(0).threadId();
		@Nullable String lead = mod.leadNpcId();
		Direction fwd = r.back();
		Direction toDoor = fwd.getOpposite();
		Direction side = r.side();
		ground(c, mod, r, style, mats, env, thread, lead, w, d, mid, h, fwd, toDoor, side);
		upper(c, mod, r, style, env, w, d, mid, h, toDoor, side);
		return true;
	}

	// ------------------------------------------------------------------ ground floor: the briefing room

	private static void ground(CitadelCanvas c, HqLayout.Module mod, Room r, HallStyle style, Mats mats, DyeColor env, String thread,
			@Nullable String lead, int w, int d, int mid, int h, Direction fwd, Direction toDoor, Direction side) {
		// the runner of the aisle (door to the lead's desk) and the lead's dais
		BlockState runner = b(Blocks.CARPET.pick(env));
		BlockState trim = b(Blocks.CARPET.pick(style.alt(env)));
		for (int u = 0; u <= d - 4; u++) {
			c.set(r.cell(u, mid, 1), runner);
			c.set(r.cell(u, mid - 1, 1), trim);
			c.set(r.cell(u, mid + 1, 1), trim);
		}
		BlockState dais = b(Blocks.CARPET.pick(DyeColor.GRAY));
		for (int v = mid - 3; v <= mid + 3; v++) {
			if (v != mid) {
				c.set(r.cell(d - 2, v, 1), dais);
			}
		}

		// the lead's desk: three blocks across the aisle's end, the lead stands behind it and looks at the door
		DeskSpot spot = new DeskSpot(r.cell(d - 3, mid, 1), toDoor);
		CitadelBarrios.deskAt(c, mod.index(), 0, spot, thread);

		// the big screen behind the lead, above its head, and a low cabinet of chests and barrels under it
		int sw = Math.min(7, w - 2);
		int half = sw / 2;
		BlockPos origin = r.cell(d - 1, mid - half, 3);
		CitadelHub.bigScreen(c, new HqGeometry.Surface(origin, side, toDoor, sw, 3), LeadOffices.SCREEN_KEY + mod.index(),
				"project:" + mod.projectKey(), true);
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, toDoor);
		BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, toDoor);
		for (int v = mid - half, i = 0; v <= mid + half; v++, i++) {
			c.set(r.cell(d - 1, v, 1), i % 2 == 0 ? chest : barrel);
		}
		// the lead's standards in the two back corners
		BlockState banner = b(Blocks.BANNER.pick(env));
		c.put(r.cell(d - 1, 0, 1).getX(), 1, r.cell(d - 1, 0, 1).getZ(), banner, DECOR);
		c.put(r.cell(d - 1, w - 1, 1).getX(), 1, r.cell(d - 1, w - 1, 1).getZ(), banner, DECOR);

		// the waiting area: rows of chairs either side of the aisle, all looking at the lead; the cell in front of
		// each chair is the leadseat anchor (the nearest seats first)
		BlockState chair = stairs(Blocks.CHERRY_STAIRS, toDoor);
		int seat = 0;
		for (int u = d - 5; u >= 2; u -= 2) {
			for (int off = 2; off <= mid - 1; off++) {
				for (int sgn = -1; sgn <= 1; sgn += 2) {
					BlockPos cell = r.cell(u, mid + sgn * off, 1);
					c.set(cell, chair);
					if (lead != null) {
						c.anchor(Anchor.at(AnchorNames.leadSeat(lead, seat++), cell.relative(fwd), fwd));
					}
				}
			}
		}

		whiteboard(c, mod, r, mats, env, w, d);

		// the lobby: a tub plant by the door, lanterns over the lanes
		c.set(r.cell(0, w - 1, 1), Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		c.set(r.cell(0, w - 1, 2), leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
		for (int u : new int[] {2, d - 4}) {
			for (int v : new int[] {2, w - 3}) {
				BlockPos p = r.cell(u, v, h - 1);
				c.put(p.getX(), p.getY(), p.getZ(), lantern, HANGING);
			}
		}
	}

	/**
	 * The kanban whiteboard on the east wall: 4 wide by 3 high of white concrete with coloured notes (a pattern of
	 * the module's index, not random), a cabinet under it and a frame on top. The window panes behind it become wall.
	 */
	private static void whiteboard(CitadelCanvas c, HqLayout.Module mod, Room r, Mats mats, DyeColor env, int w, int d) {
		DyeColor[] notes = {DyeColor.YELLOW, DyeColor.LIGHT_BLUE, DyeColor.LIME, DyeColor.PINK, env};
		BlockState white = b(Blocks.CONCRETE.pick(DyeColor.WHITE));
		for (int u = d - 5; u <= d - 2; u++) {
			for (int y = 2; y <= 4; y++) {
				BlockPos wall = r.cell(u, w, y);
				BlockState cur = c.at(wall.getX(), wall.getY(), wall.getZ());
				if (cur != null && cur.getBlock().toString().contains("glass_pane")) {
					c.set(wall, mats.wall());
				}
				int hsh = HallStyle.hash(mod.index(), u, y);
				c.set(r.cell(u, w - 1, y), hsh % 3 == 0 ? b(Blocks.CONCRETE.pick(notes[hsh / 3 % notes.length])) : white);
			}
			c.set(r.cell(u, w - 1, 1), PLANKS);
			c.set(r.cell(u, w - 1, 5), PLANKS);
		}
	}

	// ------------------------------------------------------------------ upper floor: the planning room

	private static void upper(CitadelCanvas c, HqLayout.Module mod, Room r, HallStyle style, DyeColor env, int w, int d, int mid, int h,
			Direction toDoor, Direction side) {
		int yu = h + 1;
		BlockState fence = b(style.fence());
		// the rail of the stair hole (the flight up the west wall)
		for (int u = h - 4; u <= h - 1; u++) {
			c.set(r.cell(u, 1, yu), fence);
		}
		c.set(r.cell(h - 5, 0, yu), fence);

		// the conference table (3x2, legs at the ends) with six chairs and a lantern
		int u0 = 3;
		int u1 = d - 5;
		int t1 = mid + 1;
		int t2 = mid + 2;
		BlockState top = slab(Blocks.CHERRY_SLAB);
		for (int u = u0; u <= u1; u++) {
			for (int v = t1; v <= t2; v++) {
				if (u == u0 || u == u1) {
					c.set(r.cell(u, v, yu), fence);
				}
				c.set(r.cell(u, v, yu + 1), top);
			}
		}
		BlockPos lamp = r.cell((u0 + u1) / 2, t1, yu + 2);
		c.put(lamp.getX(), lamp.getY(), lamp.getZ(), Blocks.LANTERN.defaultBlockState(), DECOR);
		for (int u = u0; u <= u1; u++) {
			c.set(r.cell(u, mid, yu), stairs(Blocks.CHERRY_STAIRS, side.getOpposite()));
			c.set(r.cell(u, mid + 3, yu), stairs(Blocks.CHERRY_STAIRS, side));
		}

		// the work counter on the back wall: a bench, storage, a lectern, maps, shelves
		BlockState[] counter = {b(Blocks.CRAFTING_TABLE),
				Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, toDoor),
				Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP),
				Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, toDoor),
				b(Blocks.CARTOGRAPHY_TABLE)};
		for (int v = 2, i = 0; v <= w - 2; v++, i++) {
			c.set(r.cell(d - 1, v, yu), i < counter.length ? counter[i] : b(Blocks.BOOKSHELF));
		}
		for (int v : new int[] {2, 4}) {
			BlockPos p = r.cell(d - 1, v, yu + 1);
			c.put(p.getX(), p.getY(), p.getZ(), HallStyle.pot(HallStyle.hash(mod.index(), v)), DECOR);
		}
		// bookshelf columns on the east wall piers, two high
		for (int u = 6; u <= d - 1; u += 3) {
			c.set(r.cell(u, w - 1, yu), b(Blocks.BOOKSHELF));
			c.set(r.cell(u, w - 1, yu + 1), b(Blocks.BOOKSHELF));
		}
		// a rug on the landing and lanterns over the room
		BlockState rug = b(Blocks.CARPET.pick(env));
		for (int u = h + 1; u <= d - 2; u++) {
			for (int v = 2; v <= w - 3; v++) {
				c.set(r.cell(u, v, yu), rug);
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
