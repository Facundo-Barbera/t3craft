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
					c.set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
			} else if (Math.floorMod(x * 7 + z * 11, 29) == 0 && !lattice(x, z)) {
				c.set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
			}
		}
	}

	/** The courtyard's centre: a cherry tree on even lots, a pool with four planters on odd lots. */
	public static void courtyardCentre(CitadelCanvas c, int lot, Lot l) {
		if (lot % 2 == 0) {
			courtyardTree(c, l);
		} else {
			courtyardPool(c, l);
		}
	}

	/** Even lots: a cherry tree in the middle of the courtyard. */
	public static void courtyardTree(CitadelCanvas c, Lot l) {
		BlockPos ctr = l.centre();
		CitadelGrounds.tree(c, ctr.getX(), ctr.getZ());
	}

	/** Odd lots: a pool in the middle of the courtyard with a garden planter on each diagonal. */
	public static void courtyardPool(CitadelCanvas c, Lot l) {
		BlockPos ctr = l.centre();
		c.set(ctr.getX(), -1, ctr.getZ(), FOOT);
		c.set(ctr.getX(), 0, ctr.getZ(), WATER);
		for (int sa = -1; sa <= 1; sa += 2) {
			for (int sb = -1; sb <= 1; sb += 2) {
				c.set(l.at(sa, sb, 1), c.kit.gardenPlanter());
			}
		}
	}

	/** An empty hall slot: a little garden with a tree. */
	public static void slotGarden(CitadelCanvas c, Room r) {
		for (int u = -1; u <= r.d(); u++) {
			for (int v = -1; v <= r.w(); v++) {
				BlockPos p = r.cell(u, v, 0);
				c.set(p, lattice(p.getX(), p.getZ()) ? LIGHT : ((u + v) % 5 == 0 ? MOSS : GRASS));
				if (r.corner(u, v) && !lattice(p.getX(), p.getZ())) {
					c.set(p.above(), leaves(Blocks.FLOWERING_AZALEA_LEAVES));
				}
			}
		}
		BlockPos t = r.cell(r.d() / 2, r.c(), 0);
		CitadelGrounds.tree(c, t.getX(), t.getZ());
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
		var style = dev.t3craft.hq.citadel.barrio.HallStyle.of(l.id(), h);
		DyeColor env = dev.t3craft.hq.citadel.barrio.HallFacade.env(c, l.id());
		int seed = dev.t3craft.hq.citadel.barrio.HallStyle.seed(l.id(), h);
		hallShell(c, l, h, r, F, mats);
		// the facade (door, porch, windows) and the roof; then the inside: stairs, rugs, furniture, light
		dev.t3craft.hq.citadel.barrio.HallFacade.dress(c, r, F, mats, style, env, seed);
		roof(c, r, top, Math.floorMod(h + l.id(), 3), mats);
		if (F == 2) {
			hallStairs(c, r, style);
		}
		dev.t3craft.hq.citadel.barrio.HallInterior.furnish(c, r, F, mats, style, env, seed);
		for (HqLayout.Module mod : new HqLayout.Module[] {ground, upper}) {
			if (mod == null) {
				continue;
			}
			int f = RingCampusGeometry.floorOf(mod.index());
			int y = f * FH + 1;
			// feat/lead-office: a lead's module becomes its office (desk, children big screen, banner,
			// bookshelves, whiteboard, chairs); every other module keeps its six desks
			if (!CitadelLeadOffice.place(c, mod, r, f)) {
				for (int slot = 0; slot < mod.capacity(); slot++) {
					String thread = mod.threadAt(slot);
					if (thread != null) {
						desk(c, mod.index(), r, f, slot, thread);
					}
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
		var style = dev.t3craft.hq.citadel.barrio.HallStyle.of(l.id(), h);
		DyeColor env = dev.t3craft.hq.citadel.barrio.HallFacade.env(c, l.id());
		int seed = dev.t3craft.hq.citadel.barrio.HallStyle.seed(l.id(), h);
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
							BlockState s = corner ? mats.post()
									: dev.t3craft.hq.citadel.barrio.HallFacade.wallBlock(c, style, mats, env, seed, t, ly, along);
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
	public static void hallStairs(CitadelCanvas c, Room r, dev.t3craft.hq.citadel.barrio.HallStyle style) {
		int mid = r.c();
		for (int i = 0; i < RingCampusGeometry.STAIR_STEPS; i++) {
			BlockPos p = r.cell(1 + i, mid, 1 + i);
			for (int y = 1; y <= i; y++) {
				c.set(p.atY(y), style.stairCore());
			}
			c.set(p, stairs(style.stairBlock(), r.back()));
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

	/** Gable roof (type 0): the ridge runs along the depth, the gable ends are filled with the wall material. */
	public static void roofGable(CitadelCanvas c, Room r, int top, Mats mats) {
		int W = r.w();
		int D = r.d();
		int mid = r.c();
		for (int u = -1; u <= D; u++) {
			for (int v = -1; v <= W; v++) {
				BlockPos p = r.cell(u, v, 0);
				int dv1 = v + 1;
				int dv2 = W - v;
				int du1 = u + 1;
				int k = Math.min(dv1, dv2);
				int y = top + Math.max(1, k);
				boolean ridge = v == mid;
				BlockState s;
				if (ridge) {
					s = mats.ridge();
				} else {
					s = stairs(mats.roof(), slopeFacing(r, k, dv1, dv2, du1));
				}
				c.set(p.atY(y), s);
				if (u == -1 || u == D) {
					for (int yy = top + 1; yy < y; yy++) {
						c.set(p.atY(yy), mats.wall());
					}
				}
			}
		}
	}

	/** Hipped roof (type 1): four slopes, a short ridge along the centre. */
	public static void roofHipped(CitadelCanvas c, Room r, int top, Mats mats) {
		int W = r.w();
		int D = r.d();
		int mid = r.c();
		for (int u = -1; u <= D; u++) {
			for (int v = -1; v <= W; v++) {
				BlockPos p = r.cell(u, v, 0);
				int dv1 = v + 1;
				int dv2 = W - v;
				int du1 = u + 1;
				int du2 = D - u;
				int k = Math.min(Math.min(dv1, dv2), Math.min(du1, du2));
				int y = top + Math.max(1, k);
				boolean ridge = v == mid && Math.min(du1, du2) > mid + 1;
				BlockState s;
				if (ridge) {
					s = mats.ridge();
				} else {
					s = stairs(mats.roof(), slopeFacing(r, k, dv1, dv2, du1));
				}
				c.set(p.atY(y), s);
			}
		}
	}

	/** Flat roof (type 2): a parapet round the edge and a green roof (moss carpet, some azalea) inside. */
	public static void roofFlat(CitadelCanvas c, Room r, int top, Mats mats) {
		int W = r.w();
		int D = r.d();
		for (int u = -1; u <= D; u++) {
			for (int v = -1; v <= W; v++) {
				BlockPos p = r.cell(u, v, 0);
				if (r.wall(u, v)) {
					c.set(p.atY(top + 1), mats.wall());
					c.set(p.atY(top + 2), slab(Blocks.DEEPSLATE_TILE_SLAB));
				} else {
					c.set(p.atY(top + 1), Math.floorMod(u * 3 + v * 5, 7) == 0 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : b(Blocks.MOSS_CARPET));
				}
			}
		}
	}

	/** The facing of the roof stair on the slope whose distance to the edge is {@code k}. */
	private static Direction slopeFacing(Room r, int k, int dv1, int dv2, int du1) {
		if (k == dv1) {
			return r.side();
		} else if (k == dv2) {
			return r.side().getOpposite();
		} else if (k == du1) {
			return r.back();
		} else {
			return r.back().getOpposite();
		}
	}

	// ------------------------------------------------------------------ gatehouse and sign

	/** The gatehouse on the hub side: an arched passage into the courtyard, the project's sign under the arch. */
	public static void gatehouse(CitadelCanvas c, Lot l, HqLayout.Module lead, Mats mats) {
		Room r = l.gate();
		int W = r.w();
		int D = r.d();
		int mid = r.c();
		for (int u = 0; u < D; u++) {
			for (int v = 0; v < W; v++) {
				BlockPos p = r.cell(u, v, 0);
				boolean passage = Math.abs(v - mid) <= 1;
				c.set(p, passage ? c.accent() : FOOT);
				if (passage) {
					for (int y = 1; y <= 4; y++) {
						c.set(p.atY(y), AIR);
					}
					c.set(p.atY(5), c.kit.beam(r.back().getAxis()));
				} else {
					boolean corner = (u == 0 || u == D - 1) && (v == 0 || v == W - 1);
					for (int y = 1; y <= 5; y++) {
						c.set(p.atY(y), corner ? mats.post() : (y == 3 && u == D / 2 && (v == 0 || v == W - 1) ? c.kit.wallPanel(true) : mats.wall()));
					}
				}
				c.set(p.atY(6), mats.ridge());
				int k = Math.min(Math.min(u + 1, D - u), Math.min(v + 1, W - v));
				int kmax = Math.min((D + 1) / 2, (W + 1) / 2);
				BlockState s;
				if (k == kmax) {
					s = mats.ridge();
				} else if (k == u + 1) {
					s = stairs(mats.roof(), r.back());
				} else if (k == D - u) {
					s = stairs(mats.roof(), r.back().getOpposite());
				} else if (k == v + 1) {
					s = stairs(mats.roof(), r.side());
				} else {
					s = stairs(mats.roof(), r.side().getOpposite());
				}
				c.set(p.atY(6 + k), s);
			}
		}
		BlockPos sign = r.cell(D - 1, mid, 4);
		BlockState hs = Blocks.CHERRY_HANGING_SIGN.defaultBlockState().setValue(CeilingHangingSignBlock.ROTATION, rotation(r.back()))
				.setValue(CeilingHangingSignBlock.ATTACHED, false);
		c.put(sign.getX(), sign.getY(), sign.getZ(), hs, HANGING);
		c.data.put(sign.asLong(), barrioSign(c, lead).signTag());
		c.fixtures.add(sign.asLong());
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
		String label = w.label();
		String l1 = label.length() <= SignTags.LINE ? label : label.substring(0, SignTags.LINE);
		String l2 = label.length() <= SignTags.LINE ? "" : label.substring(SignTags.LINE);
		int desks = 0;
		for (HqLayout.Module m : c.layout.modulesOf(w.projectKey())) {
			desks += m.desks().size();
		}
		return new HqStyle.SignText(HqStyle.envColor(w), l1, l2, desks + (desks == 1 ? " desk" : " desks"), w.envLabel());
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
