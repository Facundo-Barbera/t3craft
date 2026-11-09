package dev.t3craft.hq.citadel;

import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqStyle;
import dev.t3craft.hq.RingCampusGeometry;
import dev.t3craft.hq.RingCampusGeometry.DeskSpot;
import dev.t3craft.hq.RingCampusGeometry.Lot;
import dev.t3craft.hq.RingCampusGeometry.Room;
import dev.t3craft.hq.SignTags;
import dev.t3craft.hq.Workstation;
import dev.t3craft.hq.citadel.CitadelCanvas.Mats;
import dev.t3craft.hq.citadel.barrio.BarrioCourt;
import dev.t3craft.hq.citadel.barrio.BarrioGate;
import dev.t3craft.hq.citadel.barrio.BarrioRoofs;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import static dev.t3craft.hq.citadel.CitadelCanvas.AIR;
import static dev.t3craft.hq.citadel.CitadelCanvas.FH;
import static dev.t3craft.hq.citadel.CitadelCanvas.FOOT;
import static dev.t3craft.hq.citadel.CitadelCanvas.GLASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.GRASS;
import static dev.t3craft.hq.citadel.CitadelCanvas.HANGING;
import static dev.t3craft.hq.citadel.CitadelCanvas.LIGHT;
import static dev.t3craft.hq.citadel.CitadelCanvas.MOSS;
import static dev.t3craft.hq.citadel.CitadelCanvas.PLANKS;
import static dev.t3craft.hq.citadel.CitadelCanvas.WATER;
import static dev.t3craft.hq.citadel.CitadelCanvas.b;
import static dev.t3craft.hq.citadel.CitadelCanvas.col;
import static dev.t3craft.hq.citadel.CitadelCanvas.colX;
import static dev.t3craft.hq.citadel.CitadelCanvas.colZ;
import static dev.t3craft.hq.citadel.CitadelCanvas.lattice;
import static dev.t3craft.hq.citadel.CitadelCanvas.leaves;
import static dev.t3craft.hq.citadel.CitadelCanvas.slab;
import static dev.t3craft.hq.citadel.CitadelCanvas.stairs;

/**
 * The barrios of the citadel: the lots of the projects, one per project, each with its courtyard, its
 * work halls, its gatehouse and its gardens. Guide for the design agents: every piece is a static
 * method taking the canvas first; the order of the calls decides which block wins on a shared cell, so
 * it never changes without changing the plan.
 *
 * <h2>Pieces</h2>
 *
 * <ul>
 * <li>{@link #barrio(CitadelCanvas, int, TreeMap)}: the whole lot, in this order: {@link #lotGround},
 *     {@link #courtyardCentre}, then for each of the {@value RingCampusGeometry#HALLS} hall slots a
 *     {@link #hall} (if the slot has a module on either floor) or a {@link #slotGarden}, then the
 *     {@link #gatehouse} and the two project anchors. Parameters: {@code lot} (the lot id, which also
 *     picks the {@link Mats} of the barrio, see {@link CitadelCanvas#mats}) and {@code mods} (module
 *     slot 0..5 of the lot → module; slot {@code h} is the ground floor of hall {@code h}, slot
 *     {@code h + 3} its upper floor; the first entry is the project's lead module).</li>
 * <li>{@link #lotGround}: the courtyard floor (lit accent/floor squares), the paved cross of paths and
 *     the azalea bushes of the garden belt and the verge. Parameters: the lot id and its {@code Lot}
 *     geometry.</li>
 * <li>{@link #courtyardCentre}: the middle of the courtyard, a variant by the lot id's parity: even
 *     lots a cherry tree ({@code CitadelGrounds.tree}), odd lots a pool ({@code WATER} over {@code FOOT})
 *     ringed by four garden planters.</li>
 * <li>{@link #slotGarden}: an empty hall slot, a little garden (grass and moss, azalea at the corners,
 *     a cherry tree). Parameter: the hall's {@code Room}.</li>
 * <li>{@link #hall}: a work hall of one or two floors on its {@code Room} ({@code l} the lot, {@code h}
 *     the hall slot, {@code ground}/{@code upper} the modules of each floor, either may be null but
 *     not both, {@code mats} the barrio materials). It is {@link #hallShell} (walls, windows, door,
 *     floors and ceiling), {@link #roof}, {@link #hallStairs} (two floors only) and the
 *     {@link #desk}s plus the module anchors.</li>
 * <li>{@link #roof}: one of three roofs by {@code type = (h + lot) mod 3}, each its own piece:
 *     {@link #roofGable} (0, ridge along the depth), {@link #roofHipped} (1) and {@link #roofFlat}
 *     (2, a green roof behind a parapet). Parameters: the hall {@code Room}, {@code top} (y of the
 *     ceiling slab, {@code floors * FH}) and the {@code Mats}.</li>
 * <li>{@link #gatehouse}: the arched passage on the hub side, a pyramid roof over it, the project's
 *     hanging sign under the arch. Parameters: the lot, its lead module (for the sign) and the
 *     {@code Mats}.</li>
 * <li>{@link #barrioSign} / {@link #rotation}: the text of the project's sign (label split over two
 *     lines, desk count, environment) and the hanging sign's rotation for a facing.</li>
 * <li>{@link #desk}: a standing desk of three blocks, its 3×2 screen, the cleared standing cell and
 *     the workstation with its anchors. Parameters: module index, hall {@code Room}, floor, slot and the
 *     thread bound to it.</li>
 * </ul>
 *
 * <h2>Blocks and roles</h2>
 *
 * Fixed blocks of the canvas: {@code FOOT} (hall walls' base, gatehouse body and floor, pool bottom),
 * {@code GLASS} (windows), {@code PLANKS} (stairs' core), {@code GRASS}/{@code MOSS}/{@code WATER}/
 * {@code LIGHT} (gardens, pool, lit floor), azalea leaves, moss carpet, deepslate tile slabs (flat
 * roof), cherry stairs and a cherry hanging sign. From the kit: {@code floor()}, {@code accent()},
 * {@code wallPanel(true)} (the plaque course of the gatehouse), {@code beam(axis)} (the wall band and the
 * arch lintel when the barrio's band equals its post), {@code gardenPlanter()} and {@code desk(facing)}.
 * From the style: {@code computer(true)} for the screens. From {@link Mats}: {@code wall}, {@code post}
 * (corner posts), {@code band}, {@code roof} (stairs), {@code ridge} and {@code ceiling}.
 *
 * <h2>Anchors, groups and fixtures</h2>
 *
 * Anchors: {@code wing(project)} and {@code walk(project)} (once per project, in {@link #barrio}),
 * {@code module(i)} (the hall's door) and {@code walkModule(i)} (inside the hall) per module,
 * {@code deskSlot(i, slot)}, {@code screenSlot(i, slot)}, {@code desk(thread)} and
 * {@code monitor(thread)} per bound desk. Workstations: one per bound desk. Fixtures (never replaced
 * by the light pass): the desks, the screens and the gatehouse's sign. Groups (by
 * {@code CitadelFinish.groupOf}, from the zone and {@code hallCols}): the courtyard, paths, gardens
 * and gatehouse are {@code lot_<id>}; the cells of a hall are {@code module_<index>} of its module
 * ({@link #hallShell} registers its columns in {@code hallCols}).
 */
public final class CitadelBarrios {
	private CitadelBarrios() {
	}

	// ------------------------------------------------------------------ the lot

	public static void barrio(CitadelCanvas c, int lot, TreeMap<Integer, HqLayout.Module> mods) {
		Lot l = RingCampusGeometry.lot(lot);
		HqLayout.Module lead = mods.firstEntry().getValue();
		DyeColor env = HqStyle.envColor(lead);
		Mats mats = c.mats(lot, env);
		lotGround(c, lot, l);
		// the courtyard's centre: a cherry tree or a pool with planters
		courtyardCentre(c, lot, l);
		for (int h = 0; h < RingCampusGeometry.HALLS; h++) {
			HqLayout.Module ground = mods.get(h);
			HqLayout.Module upper = mods.get(h + RingCampusGeometry.HALLS);
			if (ground != null || upper != null) {
				hall(c, l, h, ground, upper, mats);
			} else {
				slotGarden(c, l.hall(h));
			}
		}
		gatehouse(c, l, lead, mats);
		HqLayout.Module first = c.layout.modulesOf(lead.projectKey()).get(0);
		Direction in = l.out();
		int g = l.court();
		c.anchor(Anchor.at(AnchorNames.wing(first.projectKey()), l.at(-(g - 1), 0, 1), in));
		c.anchor(Anchor.at(AnchorNames.walk(first.projectKey()), l.at(2, 2, 1), in));
	}

	/** The ground of a lot: courtyard, paved paths, garden belt and verge. */
	public static void lotGround(CitadelCanvas c, int lot, Lot l) {
		int g = l.court();
		int reach = l.reach();
		for (long k : c.lotCols.getOrDefault(lot, List.of())) {
			int x = colX(k);
			int z = colZ(k);
			int a = Math.abs(l.a(x, z));
			int bb = Math.abs(l.b(x, z));
			if (a <= g && bb <= g) {
				c.set(x, 0, z, lattice(x, z) ? LIGHT : (a == g || bb == g ? c.accent() : c.floor()));
			} else if (a <= reach && bb <= reach && (a <= g + 1 || bb <= g + 1)) {
				c.set(x, 0, z, lattice(x, z) ? LIGHT : c.accent());
			} else if (a <= reach && bb <= reach) {
				if (Math.floorMod(a * 3 + bb * 5, 7) == 0 && !lattice(x, z)) {
					c.set(x, 1, z, BarrioCourt.bush(x, z));
				}
			} else if (Math.floorMod(x * 7 + z * 11, 29) == 0 && !lattice(x, z)) {
				c.set(x, 1, z, BarrioCourt.bush(x, z));
			}
		}
	}

	/** The courtyard's centre and its dressing, one of four variants by the lot's hash ({@link BarrioCourt}). */
	public static void courtyardCentre(CitadelCanvas c, int lot, Lot l) {
		BarrioCourt.centre(c, lot, l);
	}

	/** Variant 0 of the courtyard's centre: a cherry tree with benches. */
	public static void courtyardTree(CitadelCanvas c, Lot l) {
		BarrioCourt.tree(c, l);
	}

	/** Variant 1 of the courtyard's centre: a pool with planters. */
	public static void courtyardPool(CitadelCanvas c, Lot l) {
		BarrioCourt.pool(c, l);
	}

	/** An empty hall slot: a little garden ({@link BarrioCourt#slotGarden}). */
	public static void slotGarden(CitadelCanvas c, Room r) {
		BarrioCourt.slotGarden(c, r);
	}

	// ------------------------------------------------------------------ halls

	/**
	 * A work hall of one or two floors: walls with windows, the door on the courtyard, 6 standing
	 * desks per floor with their 3×2 screens, straight stairs up the centre line, one of three roofs.
	 */
	public static void hall(CitadelCanvas c, Lot l, int h, HqLayout.@Nullable Module ground, HqLayout.@Nullable Module upper, Mats mats) {
		Room r = l.hall(h);
		int D = r.d();
		int mid = r.c();
		int F = upper != null ? 2 : 1;
		int top = F * FH;
		hallShell(c, l, h, r, F, mats);
		roof(c, r, top, Math.floorMod(h + l.id(), 3), mats);
		if (F == 2) {
			hallStairs(c, r);
		}
		for (HqLayout.Module mod : new HqLayout.Module[] {ground, upper}) {
			if (mod == null) {
				continue;
			}
			int f = RingCampusGeometry.floorOf(mod.index());
			int y = f * FH + 1;
			for (int slot = 0; slot < mod.capacity(); slot++) {
				String thread = mod.threadAt(slot);
				if (thread != null) {
					desk(c, mod.index(), r, f, slot, thread);
				}
			}
			BlockPos door = f == 0 ? r.cell(0, mid - 1, y) : r.cell(7, mid, y);
			BlockPos inside = f == 0 ? r.cell(2, mid - 1, y) : r.cell(D - 2, mid, y);
			c.anchor(Anchor.at(AnchorNames.module(mod.index()), door, r.back()));
			c.anchor(Anchor.at(AnchorNames.walkModule(mod.index()), inside, r.back()));
		}
	}

	/** The shell of a hall: floor, walls with windows and the door, the floors' slabs (with the stair hole) and the ceiling. */
	public static void hallShell(CitadelCanvas c, Lot l, int h, Room r, int F, Mats mats) {
		int W = r.w();
		int D = r.d();
		int mid = r.c();
		Set<Long> hole = new HashSet<>();
		if (F == 2) {
			for (int u = 2; u <= 5; u++) {
				hole.add(r.cell(u, mid, FH).asLong());
			}
		}
		for (int u = -1; u <= D; u++) {
			for (int v = -1; v <= W; v++) {
				BlockPos g0 = r.cell(u, v, 0);
				c.hallCols.put(col(g0.getX(), g0.getZ()), new int[] {l.id(), h});
				boolean wall = r.wall(u, v);
				boolean corner = r.corner(u, v);
				c.set(g0, wall ? FOOT : (lattice(g0.getX(), g0.getZ()) ? LIGHT : c.floor()));
				Direction.Axis along = (u == -1 || u == D) ? r.side().getAxis() : r.back().getAxis();
				for (int f = 0; f < F; f++) {
					int yb = f * FH;
					if (wall) {
						int t = (u == -1 || u == D) ? v : u;
						for (int y = yb + 1; y < yb + FH; y++) {
							int ly = y - yb;
							BlockState s;
							if (corner) {
								s = mats.post();
							} else if (ly >= 2 && ly <= 4 && Math.floorMod(t, 3) != 0) {
								s = GLASS;
							} else {
								s = mats.wall();
							}
							c.set(g0.atY(y), s);
						}
						if (f == 0 && u == -1 && Math.abs(v - mid) <= 1) {
							c.fill(g0.getX(), 1, 3, g0.getZ(), AIR);
						}
						c.set(g0.atY(yb + FH), corner ? mats.post() : (mats.band() == mats.post() ? c.kit.beam(along) : mats.band()));
					} else {
						for (int y = yb + 1; y < yb + FH; y++) {
							c.set(g0.atY(y), AIR);
						}
						BlockPos ceil = g0.atY(yb + FH);
						if (f < F - 1) {
							c.set(ceil, hole.contains(ceil.asLong()) ? AIR : (lattice(g0.getX(), g0.getZ()) ? LIGHT : c.floor()));
						} else {
							c.set(ceil, (u % 4 == 1 && v % 4 == 1) ? LIGHT : mats.ceiling());
						}
					}
				}
			}
		}
	}

	/** Straight stairs up the centre line of a two-floor hall, through the hole of {@link #hallShell}. */
	public static void hallStairs(CitadelCanvas c, Room r) {
		int mid = r.c();
		for (int i = 0; i < RingCampusGeometry.STAIR_STEPS; i++) {
			BlockPos p = r.cell(1 + i, mid, 1 + i);
			for (int y = 1; y <= i; y++) {
				c.set(p.atY(y), PLANKS);
			}
			c.set(p, stairs(Blocks.CHERRY_STAIRS, r.back()));
		}
	}

	// ------------------------------------------------------------------ roofs

	/** Roofs: 0 gable (ridge along the depth), 1 hipped, 2 flat green roof behind a parapet. */
	public static void roof(CitadelCanvas c, Room r, int top, int type, Mats mats) {
		switch (type) {
			case 0 -> roofGable(c, r, top, mats);
			case 1 -> roofHipped(c, r, top, mats);
			default -> roofFlat(c, r, top, mats);
		}
	}

	/** Gable roof (type 0): ridge along the depth ({@link BarrioRoofs#gable}). */
	public static void roofGable(CitadelCanvas c, Room r, int top, Mats mats) {
		BarrioRoofs.gable(c, r, top, mats);
	}

	/** Hipped roof (type 1): four slopes closed by corner stairs ({@link BarrioRoofs#hipped}). */
	public static void roofHipped(CitadelCanvas c, Room r, int top, Mats mats) {
		BarrioRoofs.hipped(c, r, top, mats);
	}

	/** Flat roof (type 2): a parapet and a green roof with a tree and a pergola ({@link BarrioRoofs#flat}). */
	public static void roofFlat(CitadelCanvas c, Room r, int top, Mats mats) {
		BarrioRoofs.flat(c, r, top, mats);
	}

	// ------------------------------------------------------------------ gatehouse and sign

	/** The gatehouse on the hub side ({@link BarrioGate#gatehouse}). */
	public static void gatehouse(CitadelCanvas c, Lot l, HqLayout.Module lead, Mats mats) {
		BarrioGate.gatehouse(c, l, lead, mats);
	}

	public static int rotation(Direction facing) {
		return switch (facing) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			default -> 12;
		};
	}

	public static HqStyle.SignText barrioSign(CitadelCanvas c, HqLayout.Module w) {
		return BarrioGate.barrioSign(c, w);
	}

	// ------------------------------------------------------------------ desks

	public static void desk(CitadelCanvas c, int module, Room r, int f, int slot, String thread) {
		DeskSpot d = RingCampusGeometry.desk(r, f, slot);
		Direction face = d.facing();
		Direction right = face.getClockWise();
		BlockPos desk = d.desk();
		for (int k = -1; k <= 1; k++) {
			c.fixture(desk.relative(right, k), c.kit.desk(face.getOpposite()));
		}
		BlockPos monitor = desk.above();
		Direction screenFacing = face.getOpposite();
		for (BlockPos p : Workstation.screenCells(Workstation.screenOriginFor(monitor, screenFacing), screenFacing.getCounterClockWise())) {
			c.fixture(p, c.style.computer(true));
		}
		BlockPos stand = d.stand();
		c.set(stand, AIR);
		c.set(stand.above(), AIR);
		Anchor deskA = Anchor.at(AnchorNames.deskSlot(module, slot), stand, face);
		Anchor screen = Anchor.face(AnchorNames.screenSlot(module, slot), monitor, screenFacing);
		c.workstations.add(new Workstation(module, slot, thread, deskA, monitor, screen, Workstation.screenOriginFor(monitor, screenFacing)));
		c.anchor(deskA.renamed(AnchorNames.desk(thread)));
		c.anchor(screen.renamed(AnchorNames.monitor(thread)));
	}
}
