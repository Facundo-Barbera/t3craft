package dev.t3craft.hq.citadel;

import static dev.t3craft.hq.citadel.CitadelCanvas.fh;

import dev.t3craft.hq.HqGeometry;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.DeskSpot;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.lead.LeadOffices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * feat/lead-office: a lead's office inside a citadel hall (one floor of a hall whose module is a
 * {@link HqLayout.Type#LEAD_OFFICE}). Uses the hall's six desk spots
 * ({@link RingCampusGeometry#desk}) so it fits every hall the barrios build:
 *
 * <pre>
 *   back wall:  [slot 0] the lead's desk (a normal desk + workstation)   banner   [slot 1] big screen 3×2
 *   side walls: [slot 2] bookshelves          [slot 3] whiteboard
 *               [slot 4] chairs facing the big screen  [slot 5] chairs facing the big screen
 *   (door)
 * </pre>
 *
 * The big screen is reserved as {@code bigscreen_lead@<module>@3x2=project:lead:<npcId>}, the lead's
 * children feed; that key also tells the generic fallback ({@code LeadOfficeDecor}) that this module
 * already has its office.
 *
 * <h2>Hook (wired: {@code CitadelBarrios.hall} calls {@link #place} before its desk loop)</h2>
 * In {@code CitadelBarrios.hall}, inside {@code for (HqLayout.Module mod : ...)}, replace the desk loop
 * <pre>{@code
 *   for (int slot = 0; slot < mod.capacity(); slot++) { ... desk(c, mod.index(), r, f, slot, thread); }
 * }</pre>
 * with
 * <pre>{@code
 *   if (!CitadelLeadOffice.place(c, mod, r, f)) {
 *       for (int slot = 0; slot < mod.capacity(); slot++) { ... as before ... }
 *   }
 * }</pre>
 * {@link #place} returns false (and paints nothing) for a module that is not a lead office, so the
 * call is a no-op for every other hall. Nothing else changes: the anchors {@code module@i} and
 * {@code walk_module_i} stay where the hall puts them.
 */
public final class CitadelLeadOffice {
	private CitadelLeadOffice() {
	}

	/**
	 * Paints the office of lead module {@code mod} on floor {@code f} of hall {@code r}. Returns false
	 * when {@code mod} is not a lead office or has no thread (nothing painted).
	 */
	public static boolean place(CitadelCanvas c, HqLayout.Module mod, Room r, int f) {
		if (mod.type() != HqLayout.Type.LEAD_OFFICE || mod.desks().isEmpty()) {
			return false;
		}
		String thread = mod.desks().get(0).threadId();
		int y = f * fh() + 1;
		// slot 0: the lead's own desk and workstation (desk anchor desk_<npcId>)
		CitadelBarrios.desk(c, mod.index(), r, f, 0, thread);
		// slot 1: the big screen with the lead's children, on a base, facing into the room
		DeskSpot s1 = RingCampusGeometry.desk(r, f, 1);
		Direction screenFacing = s1.facing().getOpposite();
		Direction along = screenFacing.getCounterClockWise();
		BlockPos origin = s1.desk().above().relative(along.getOpposite(), 1);
		CitadelHub.bigScreen(c, new HqGeometry.Surface(origin, along, screenFacing, 3, 2), LeadOffices.SCREEN_KEY + mod.index(),
				"project:" + mod.projectKey(), true);
		// the lead's banner between its desk and the screen, against the back wall
		c.put(r.cell(r.d() - 1, r.c(), y).getX(), y, r.cell(r.d() - 1, r.c(), y).getZ(), banner(HqStyle.envColor(mod)), CitadelCanvas.DECOR);
		// slot 2: bookshelves; slot 3: whiteboard (both two blocks high, against the side walls)
		row(c, RingCampusGeometry.desk(r, f, 2), Blocks.BOOKSHELF.defaultBlockState(), 2);
		row(c, RingCampusGeometry.desk(r, f, 3), Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState(), 2);
		// slots 4 and 5: chairs facing the back wall (the big screen and the lead)
		BlockState chair = CitadelCanvas.stairs(Blocks.CHERRY_STAIRS, r.back().getOpposite());
		for (int slot = 4; slot <= 5; slot++) {
			DeskSpot d = RingCampusGeometry.desk(r, f, slot);
			Direction right = d.facing().getClockWise();
			for (int k = -1; k <= 1; k += 2) {
				c.set(d.desk().relative(right, k), chair);
			}
		}
		return true;
	}

	/** The three cells of a desk spot, {@code h} blocks high, filled with {@code s} (fixtures: the light pass keeps them). */
	private static void row(CitadelCanvas c, DeskSpot d, BlockState s, int h) {
		Direction right = d.facing().getClockWise();
		for (int k = -1; k <= 1; k++) {
			for (int dy = 0; dy < h; dy++) {
				c.fixture(d.desk().relative(right, k).above(dy), s);
			}
		}
	}

	private static BlockState banner(DyeColor color) {
		return Blocks.BANNER.pick(color).defaultBlockState();
	}
}
