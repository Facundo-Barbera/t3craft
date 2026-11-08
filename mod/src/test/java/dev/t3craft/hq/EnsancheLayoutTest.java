package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * layout/blocks ("ensanche"): manzanas with patios on a street grid around a plaza mayor. Checks the
 * spiral, the growth rules (up, around the patio, next door, lowest free), determinism, stability
 * (adding projects never moves a building), freeing and reuse of parcels, valid and reachable anchors
 * with 4, 10 and 20 projects, footprints, and the 3×2 desk screens without lamps. Cherry and classic
 * are untouched (their own tests run unchanged).
 */
class EnsancheLayoutTest {
	private static HqStyle style;

	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
		style = HqStyles.byId("ensanche");
	}

	/** {@code n} projects with varied thread counts (the render's roster), one settled thread each. */
	static HqInput roster(int n) {
		int[] threads = {9, 4, 7, 2, 13, 3, 1, 6, 5, 20, 2, 8, 4, 3, 11, 1, 6, 2, 5, 3};
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < n; p++) {
			String key = "env" + (p % 3) + ":" + String.format(Locale.ROOT, "p%02d", p);
			for (int t = 0; t < threads[p]; t++) {
				b.thread(key, "Project " + p, "env" + (p % 3), "ENV" + (p % 3), key + "-t" + t);
			}
			b.thread(key, "Project " + p, "env" + (p % 3), "ENV" + (p % 3), key + "-s0", true);
		}
		return b.build();
	}

	private static HqGenerator.Result plan(HqInput in, HqLayout prev) {
		return HqGenerator.plan(in, prev, style);
	}

	/** 4 → 10 → 20 projects, each step from the previous layout (like the live HQ). */
	private static List<HqGenerator.Result> growth() {
		List<HqGenerator.Result> out = new ArrayList<>();
		HqLayout prev = null;
		for (int n : new int[] {4, 10, 20}) {
			HqGenerator.Result r = plan(roster(n), prev);
			out.add(r);
			prev = r.layout();
		}
		return out;
	}

	// ------------------------------------------------------------------ grid

	@Test
	void styleIsSelectableAndKeepsCherryAndClassicAsTheyAre() {
		assertNotNull(style);
		assertSame(style, HqStyles.parse(com.google.gson.JsonParser.parseString("{\"hq\":{\"style\":\"ensanche\"}}").getAsJsonObject()).style());
		assertFalse(HqStyles.all().contains(style), "the shared-geometry list is unchanged");
		assertTrue(HqStyles.towns().contains(style));
		assertNull(HqStyles.defaultStyle().town());
		assertNull(HqStyles.classic().town());
		for (HqStyle.Role r : HqStyle.Role.values()) {
			assertNotNull(style.palette().get(r), r.name());
		}
	}

	@Test
	void spiralVisitsEveryCellOnceRingByRing() {
		Set<String> seen = new HashSet<>();
		for (int k = 0; k < 81; k++) {
			int[] c = EnsancheGrid.cell(k);
			assertTrue(seen.add(c[0] + "," + c[1]), "cell " + k);
			assertEquals(k, EnsancheGrid.index(c[0], c[1]));
			if (k > 0) {
				// every cell touches an earlier one: the built cells 0..k are always connected
				boolean touches = false;
				for (int j : EnsancheGrid.neighbours(k)) {
					touches |= j < k;
				}
				assertTrue(touches, "cell " + k + " touches an earlier cell");
			}
		}
		assertEquals(EnsancheGrid.Kind.PLAZA, EnsancheGrid.kind(0));
		assertEquals(EnsancheGrid.Kind.MARKET, EnsancheGrid.kind(3));
		assertEquals(EnsancheGrid.Kind.PLAZUELA, EnsancheGrid.kind(2));
		assertEquals(EnsancheGrid.Kind.PLAZUELA, EnsancheGrid.kind(11));
		assertEquals(EnsancheGrid.Kind.WORK, EnsancheGrid.kind(1));
	}

	@Test
	void parcelsTileTheManzanaAroundThePatio() {
		for (int k : new int[] {1, 4, 13}) {
			HqFootprints.Area block = EnsancheGrid.blockArea(k);
			HqFootprints.Area patio = EnsancheGrid.patioArea(k);
			int cells = 0;
			for (int side = 0; side < EnsancheGrid.SIDES; side++) {
				HqFootprints.Area a = EnsancheGrid.parcelArea(EnsancheGrid.parcel(k, side));
				assertFalse(a.intersects(patio), "parcel " + side + " vs patio");
				for (int o = side + 1; o < EnsancheGrid.SIDES; o++) {
					assertFalse(a.intersects(EnsancheGrid.parcelArea(EnsancheGrid.parcel(k, o))), side + " vs " + o);
				}
				cells += (a.maxX() - a.minX() + 1) * (a.maxZ() - a.minZ() + 1);
			}
			cells += (patio.maxX() - patio.minX() + 1) * (patio.maxZ() - patio.minZ() + 1);
			assertEquals(EnsancheGrid.BLOCK * EnsancheGrid.BLOCK, cells, "four parcels and the patio fill the manzana");
			assertTrue(block.contains(patio.minX(), patio.minZ()) && block.contains(patio.maxX(), patio.maxZ()));
		}
	}

	// ------------------------------------------------------------------ growth rules

	@Test
	void aProjectGrowsUpThenAroundItsPatioThenNextDoor() {
		HqInput in = HqInput.builder().project("e:a", "A", "e", "E").build();
		HqLayout l = null;
		List<Integer> order = new ArrayList<>();
		for (int t = 0; t < 6 * 3 * 4 + 6; t++) {
			in = HqTestSupport.plus(in, "e:a", "e:a-" + String.format(Locale.ROOT, "%03d", t));
			l = HqLayout.allocate(in, l, style);
			for (HqLayout.Module m : l.modules()) {
				if (!order.contains(m.index())) {
					order.add(m.index());
				}
			}
		}
		// the first building's three floors, then the other three parcels of the same manzana, floors first
		int k = EnsancheGrid.cellOfParcel(EnsancheGrid.parcelOfModule(order.get(0)));
		assertEquals(1, k, "the first project takes the first work manzana of the spiral");
		for (int i = 0; i < 12; i++) {
			int idx = order.get(i);
			assertEquals(k, EnsancheGrid.cellOfParcel(EnsancheGrid.parcelOfModule(idx)), "room " + i + " stays in its manzana");
			assertEquals(i % 3, EnsancheGrid.floorOfModule(idx), "room " + i + ": up before around");
		}
		int next = EnsancheGrid.cellOfParcel(EnsancheGrid.parcelOfModule(order.get(12)));
		assertTrue(EnsancheGrid.neighbours(k).contains(next), "then a manzana next door, got cell " + next);
	}

	@Test
	void newProjectsFillFreeParcelsBeforeTheFrontierMoves() {
		HqLayout l = HqLayout.allocate(HqTestSupport.input(4, 1), null, style);
		Set<Integer> cells = new TreeSet<>();
		for (HqLayout.Module m : l.modules()) {
			cells.add(EnsancheGrid.cellOfParcel(EnsancheGrid.parcelOfModule(m.index())));
		}
		assertEquals(Set.of(1), cells, "four one-room projects share the first manzana");
		HqLayout five = HqLayout.allocate(HqTestSupport.input(5, 1), l, style);
		assertEquals(4, EnsancheGrid.cellOfParcel(EnsancheGrid.parcelOfModule(five.modulesOf("env:p4").get(0).index())),
				"the fifth goes to the next work manzana of the spiral (2 and 3 are the square and the market)");
	}

	@Test
	void aFreedParcelIsReusedAndNothingElseMoves() {
		HqInput in = HqTestSupport.input(6, 2);
		HqLayout l = HqLayout.allocate(in, null, style);
		int freed = l.modulesOf("env:p1").get(0).index();
		HqInput without = HqTestSupport.minus(HqTestSupport.minus(in, "env:p1-t0"), "env:p1-t1");
		HqLayout l2 = HqLayout.allocate(without, l, style);
		assertTrue(l2.modulesOf("env:p1").isEmpty(), "an empty project frees its parcel");
		for (HqLayout.Module m : l2.modules()) {
			assertEquals(l.moduleAt(m.index()).projectKey(), m.projectKey(), "nobody moves");
		}
		HqLayout l3 = HqLayout.allocate(HqTestSupport.plus(without, "env:zz", "env:zz-t0"), l2, style);
		assertEquals(freed, l3.modulesOf("env:zz").get(0).index(), "the freed parcel is the lowest free one");
		BuildPlan p2 = plan(without, l).plan();
		assertNotNull(p2.anchor("walk_plazuela_2"));
	}

	// ------------------------------------------------------------------ determinism and stability

	@Test
	void generationIsDeterministicIdempotentAndIgnoresRosterOrder() {
		HqInput in = roster(10);
		HqGenerator.Result a = plan(in, null);
		HqGenerator.Result b = plan(in, null);
		assertEquals(a.plan().fingerprint(), b.plan().fingerprint());
		assertEquals(a.layout(), b.layout());
		HqGenerator.Result again = plan(in, a.layout());
		assertEquals(a.layout(), again.layout(), "allocate(in, allocate(in)) == allocate(in)");
		assertEquals(a.plan().fingerprint(), again.plan().fingerprint());
		List<HqInput.Entry> roster = HqTestSupport.roster(in);
		Collections.shuffle(roster, new Random(7));
		assertEquals(a.plan().fingerprint(), plan(HqInput.fromRoster(roster), null).plan().fingerprint());
		assertEquals(a.layout(), HqLayout.fromJson(a.layout().toJson()), "the layout survives its JSON");
	}

	@Test
	void growingFrom4To10To20ProjectsNeverMovesABuilding() {
		List<HqGenerator.Result> g = growth();
		for (int i = 1; i < g.size(); i++) {
			HqGenerator.Result before = g.get(i - 1);
			HqGenerator.Result after = g.get(i);
			for (HqLayout.Module m : before.layout().modules()) {
				HqLayout.Module now = after.layout().moduleAt(m.index());
				assertNotNull(now, "room " + m.index() + " kept");
				assertEquals(m, now, "same project, same desks");
			}
			Map<String, Workstation> ws = new HashMap<>();
			for (Workstation w : after.plan().workstations()) {
				ws.put(w.threadId(), w);
			}
			for (Workstation w : before.plan().workstations()) {
				assertEquals(w, ws.get(w.threadId()), "desk of " + w.threadId() + " unchanged");
			}
			// buildings whose rooms did not change are block-for-block the same
			for (String group : before.plan().groups()) {
				if (!group.startsWith("parcel_") && !group.startsWith("patio_") && !group.equals("hub")) {
					continue;
				}
				if (group.startsWith("parcel_") && changed(before.layout(), after.layout(), Integer.parseInt(group.substring(7)))) {
					continue;
				}
				List<Placement> a = before.plan().placements(group);
				List<Placement> b = after.plan().placements(group);
				if (group.equals("hub")) {
					// the hub's big screen binding may follow the biggest environment; everything else is the same
					assertEquals(a.size(), b.size(), group);
					continue;
				}
				// same cells, same blocks (a pane may connect to a new neighbour's wall: only its shape flags change)
				assertEquals(a.size(), b.size(), group);
				for (int j = 0; j < a.size(); j++) {
					assertEquals(a.get(j).rel(), b.get(j).rel(), group);
					assertSame(a.get(j).state().getBlock(), b.get(j).state().getBlock(), group + " at " + a.get(j).rel());
				}
			}
		}
		// and the town grows by manzanas, not along a corridor: 20 projects use a 3x3 neighbourhood or more
		var fp = g.get(2).plan().footprint();
		assertTrue(fp.sizeX() >= 3 * EnsancheGrid.PITCH && fp.sizeZ() >= 3 * EnsancheGrid.PITCH, "a barrio, not an avenue: " + fp);
		assertTrue(Math.max(fp.sizeX(), fp.sizeZ()) <= 2 * Math.min(fp.sizeX(), fp.sizeZ()), "roughly square: " + fp);
	}

	private static boolean changed(HqLayout a, HqLayout b, int parcel) {
		for (int f = 0; f < EnsancheGrid.MAX_FLOORS; f++) {
			int i = EnsancheGrid.module(parcel, f);
			HqLayout.Module x = a.moduleAt(i);
			HqLayout.Module y = b.moduleAt(i);
			if (x == null ? y != null : !x.equals(y)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ anchors, footprints, screens

	@ParameterizedTest(name = "{0} projects")
	@ValueSource(ints = {4, 10, 20})
	void anchorsAreFreeStandableAndReachableFromTheSpawn(int n) {
		HqLayout prev = null;
		for (int m : new int[] {4, 10, 20}) {
			HqGenerator.Result r = plan(roster(m), prev);
			prev = r.layout();
			if (m == n) {
				HqAnchorPlacementTest.assertAnchorsValid(r.plan(), n + " projects");
				for (HqLayout.Module mod : r.layout().modules()) {
					assertNotNull(r.plan().anchor(AnchorNames.module(mod.index())), "door anchor of room " + mod.index());
				}
				for (String a : List.of("walk_clock", "walk_review", "walk_lounge_0", "walk_plazuela_2", AnchorNames.ARCHIVE, AnchorNames.HOLOGRAM,
						AnchorNames.BEACON, AnchorNames.THREAD_WALL, AnchorNames.ARCHIVE_TERMINAL)) {
					assertNotNull(r.plan().anchor(a), a);
				}
			}
		}
	}

	@Test
	void upperFloorsAreReachableByTheStairs() {
		// a project with 15 threads has rooms on floors 0, 1 and 2 of one building
		HqInput in = HqTestSupport.input(1, 15);
		BuildPlan p = plan(in, null).plan();
		Set<Integer> floors = new TreeSet<>();
		for (Workstation w : p.workstations()) {
			floors.add(w.deskAnchor().block().getY() / EnsancheGrid.STOREY);
		}
		assertEquals(Set.of(0, 1, 2), floors);
		HqAnchorPlacementTest.assertAnchorsValid(p, "three floors");
	}

	@Test
	void everythingStaysInsideItsFootprints() {
		for (HqGenerator.Result r : growth()) {
			BuildPlan p = r.plan();
			int maxCell = EnsancheGrid.maxCell(r.layout());
			HqFootprints.Area env = EnsancheGrid.envelope(maxCell);
			for (Placement pl : p.placements()) {
				BlockPos q = pl.rel();
				assertTrue(env.contains(q.getX(), q.getZ()), "outside the envelope: " + q);
				assertTrue(q.getY() >= 0 && q.getY() <= EnsancheGrid.TOP, "height " + q);
			}
			for (BuildPlan.Module m : p.modules()) {
				Box b = m.box();
				String g = m.group();
				HqFootprints.Area area;
				if (g.startsWith("parcel_")) {
					area = EnsancheGrid.parcelArea(Integer.parseInt(g.substring(7)));
				} else if (g.startsWith("patio_")) {
					area = EnsancheGrid.patioArea(Integer.parseInt(g.substring(6)));
				} else if (g.startsWith("streets_") || g.startsWith("solar_") || g.startsWith("plazuela_")) {
					area = EnsancheGrid.cellArea(Integer.parseInt(g.substring(g.indexOf('_') + 1)));
				} else if (g.equals("lounge")) {
					area = EnsancheGrid.cellArea(3);
				} else {
					area = EnsancheGrid.cellArea(0);
				}
				assertTrue(area.contains(b.minX(), b.minZ()) && area.contains(b.maxX(), b.maxZ()), m.id() + " " + b + " outside " + area);
			}
		}
	}

	@Test
	void desksHaveOne3x2ScreenAndNoLamps() {
		BuildPlan p = growth().get(2).plan();
		Set<BlockPos> boardLamps = new HashSet<>();
		for (Anchor a : p.anchors().values()) {
			if (a.name().startsWith(AnchorNames.BOARD_LAMP)) {
				boardLamps.add(a.block());
			}
		}
		assertEquals(4, boardLamps.size());
		Set<BlockPos> screens = new HashSet<>();
		for (Workstation w : p.workstations()) {
			List<BlockPos> cells = w.screenCells();
			assertEquals(Workstation.SCREEN_WIDTH * Workstation.SCREEN_HEIGHT, cells.size());
			for (BlockPos c : cells) {
				assertEquals(style.computer(true), p.at(c).state(), "screen cell " + c);
				assertTrue(screens.add(c), "screens never shared");
				assertFalse(c.equals(w.deskAnchor().block()) || c.equals(w.deskAnchor().block().above()), "screen clear of the agent");
			}
			assertTrue(cells.contains(w.computerPos()));
			assertEquals(w.deskAnchor().block().relative(w.deskAnchor().facing()).above(), w.computerPos(), "screen right in front, eye level");
			assertNull(p.anchor(AnchorNames.lamp(w.threadId())), "no desk lamp anchor");
		}
		for (Placement pl : p.placements()) {
			if (pl.state().is(Blocks.COPPER_BULB.waxed().unaffected())) {
				assertTrue(boardLamps.contains(pl.rel()), "a bulb that is not the board's at " + pl.rel());
			}
		}
	}
}
