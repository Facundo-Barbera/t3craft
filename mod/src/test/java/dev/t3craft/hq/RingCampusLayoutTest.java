package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.layout.HqLayouts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * design/hybrid-ringcampus ("Ciudadela en anillos con barrios"): registry, growth rule,
 * determinism, stability while growing 4 → 10 → 20, barrio geometry, anchors reachable on foot,
 * footprints, 3×2 desk screens without lamps, block light ≥ 10 on every walkable cell, no loose
 * lanterns and supported decoration.
 */
class RingCampusLayoutTest {
	private static RingCampusStyle STYLE;

	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
		HqKit.use(null); // the vanilla kit, whatever another test left active
		STYLE = (RingCampusStyle) HqStyles.byId(RingCampusGeometry.ID);
	}

	/** {@code n} projects with varied thread counts (7+ threads: a second hall, 13+: an upper floor). */
	static HqInput roster(int n) {
		int[] threads = {5, 9, 3, 6, 2, 13, 4, 1, 7, 3, 2, 5, 8, 1, 4, 3, 6, 2, 1, 4};
		String[] envs = {"vps", "laptop", "acmesite", "cloud"};
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < n; p++) {
			String env = envs[p % envs.length];
			String key = (char) ('a' + p) + env + ":proj"; // keys sort in project order
			for (int t = 0; t < threads[p]; t++) {
				b.thread(key, "Project " + p, env, env, key + "-t" + t);
			}
			if (p % 3 == 0) {
				b.thread(key, "Project " + p, env, env, key + "-s0", true);
			}
		}
		return b.build();
	}

	private static List<HqGenerator.Result> cached;

	/** Plans for 4, 10 and 20 projects, each allocated against the previous one. */
	private static synchronized List<HqGenerator.Result> plans() {
		if (cached == null) {
			List<HqGenerator.Result> out = new ArrayList<>();
			HqLayout layout = null;
			for (int n : new int[] {4, 10, 20}) {
				HqGenerator.Result r = HqGenerator.plan(roster(n), layout, STYLE);
				layout = r.layout();
				out.add(r);
			}
			cached = out;
		}
		return cached;
	}

	private static List<Integer> indices(HqLayout l) {
		return l.modules().stream().map(HqLayout.Module::index).toList();
	}

	// ------------------------------------------------------------------ registry

	@Test
	void registeredAsALayoutWithItsOwnStyle() {
		assertTrue(HqLayouts.find(RingCampusGeometry.ID).isPresent());
		assertSame(STYLE, HqStyles.forLayout(RingCampusGeometry.ID));
		assertTrue(STYLE.ownLayout());
		assertFalse(HqStyles.all().contains(STYLE), "all() stays the spine styles");
		assertEquals(new CherryStyle().palette(), STYLE.palette(), "cherry palette");
		assertEquals("ring", HqLayouts.find("ring").orElseThrow().id(), "the existing layouts stay");
	}

	// ------------------------------------------------------------------ growth rule

	@Test
	void ringOneFillsFirstThenRingTwoAndBigProjectsDensifyTheirBarrio() {
		HqLayout l4 = plans().get(0).layout();
		Map<String, List<Integer>> by = new HashMap<>();
		for (HqLayout.Module m : l4.modules()) {
			by.computeIfAbsent(m.projectKey(), k -> new ArrayList<>()).add(m.index());
		}
		assertEquals(List.of(0), by.get("avps:proj"));
		assertEquals(List.of(8, 9), by.get("blaptop:proj"), "9 threads: a second hall in its own barrio");
		assertEquals(List.of(16), by.get("cacmesite:proj"));
		assertEquals(List.of(24), by.get("dcloud:proj"));
		HqLayout l10 = plans().get(1).layout();
		Set<Integer> lots10 = new TreeSet<>();
		for (HqLayout.Module m : l10.modules()) {
			lots10.add(RingCampusGeometry.lotOf(m.index()));
		}
		assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), lots10, "ring 1 full, ring 2 opened with two lots");
		assertEquals(2, RingCampusGeometry.rings(indices(l10)));
		assertEquals(List.of(5 * 8, 5 * 8 + 1, 5 * 8 + 3), l10.modulesOf("fcloud:proj").stream().map(HqLayout.Module::index).toList(),
				"13 threads: halls 0 and 1, then an upper floor on hall 0");
		HqLayout l20 = plans().get(2).layout();
		assertEquals(2, RingCampusGeometry.rings(indices(l20)), "20 projects fill rings 1 and 2 exactly");
		Set<Integer> lots20 = new TreeSet<>();
		for (HqLayout.Module m : l20.modules()) {
			lots20.add(RingCampusGeometry.lotOf(m.index()));
		}
		assertEquals(20, lots20.size(), "one barrio per project");
	}

	@Test
	void fillOrderSpreadsLotsRoundEachRing() {
		for (int m = 1; m <= RingCampusGeometry.MAX_RINGS; m++) {
			Set<Integer> pos = new TreeSet<>();
			for (int j = 0; j < RingCampusGeometry.lotsIn(m); j++) {
				int lot = RingCampusGeometry.base(m) + j;
				assertEquals(m, RingCampusGeometry.ringOfLot(lot));
				pos.add(RingCampusGeometry.position(lot));
				assertEquals(lot, RingCampusGeometry.lotAt(m, RingCampusGeometry.position(lot)));
			}
			assertEquals(RingCampusGeometry.lotsIn(m), pos.size(), "ring " + m + " positions are a permutation");
		}
		// the first four lots make a pinwheel: one per quadrant
		Set<Integer> quadrants = new TreeSet<>();
		for (int lot = 0; lot < 4; lot++) {
			quadrants.add((int) (RingCampusGeometry.centreAngle(lot) / (Math.PI / 2)));
		}
		assertEquals(Set.of(0, 1, 2, 3), quadrants);
	}

	@Test
	void aFreedLotIsRefilledAndABarrioNeverSpillsIntoAnother() {
		HqLayout l = plans().get(1).layout();
		HqInput in = roster(10);
		List<HqInput.Project> ps = new ArrayList<>(in.projects());
		ps.removeIf(p -> p.key().equals("cacmesite:proj"));
		ps.add(new HqInput.Project("zvps:late", "Late", "vps", "vps", List.of("zvps:late-t0")));
		HqLayout after = HqLayout.allocate(new HqInput(ps), l, STYLE.placer());
		assertEquals(2 * RingCampusGeometry.LOT_STRIDE, after.modulesOf("zvps:late").get(0).index(), "the freed lot 2");
		for (HqLayout.Module m : l.modules()) {
			if (!m.projectKey().equals("cacmesite:proj")) {
				assertNotNull(after.moduleAt(m.index()), "module " + m.index() + " kept");
			}
		}
		// densify: the six modules of a barrio in order, then nothing in another lot
		SortedSet<Integer> own = new TreeSet<>(List.of(3 * 8));
		SortedSet<Integer> used = new TreeSet<>(own);
		List<Integer> got = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			int next = RingCampusGeometry.place("x", own, used);
			got.add(next);
			own.add(next);
			used.add(next);
		}
		assertEquals(List.of(25, 27, 26, 28, 29), got);
		int over = RingCampusGeometry.place("x", own, used);
		assertEquals(-1, RingCampusGeometry.lotOf(over), "a full barrio never takes another lot");
	}

	// ------------------------------------------------------------------ geometry

	@Test
	void everyBarrioFitsItsLotInEveryRing() {
		int rings = RingCampusGeometry.MAX_RINGS;
		for (int lot = 0; lot < RingCampusGeometry.MAX_LOTS; lot++) {
			RingCampusGeometry.Lot l = RingCampusGeometry.lot(lot);
			List<RingCampusGeometry.Room> rooms = new ArrayList<>();
			for (int h = 0; h < RingCampusGeometry.HALLS; h++) {
				rooms.add(l.hall(h));
			}
			Set<Long> taken = new HashSet<>();
			for (RingCampusGeometry.Room r : rooms) {
				for (int u = -1; u <= r.d(); u++) {
					for (int v = -1; v <= r.w(); v++) {
						BlockPos c = r.cell(u, v, 0);
						assertInLot(lot, c, rings, "hall");
						assertTrue(taken.add(c.asLong()), "lot " + lot + ": two buildings at " + c);
					}
				}
				// desks, stands and stairs inside the hall's interior, never on each other
				Set<Long> used = new HashSet<>();
				for (int i = 0; i < RingCampusGeometry.STAIR_STEPS; i++) {
					used.add(r.cell(1 + i, r.c(), 0).asLong());
				}
				for (int slot = 0; slot < HqLayout.DESKS; slot++) {
					RingCampusGeometry.DeskSpot d = RingCampusGeometry.desk(r, 0, slot);
					Direction right = d.facing().getClockWise();
					for (BlockPos p : List.of(d.desk().relative(right, -1), d.desk(), d.desk().relative(right, 1), d.stand())) {
						BlockPos g = p.atY(0);
						assertTrue(interior(r, g), "lot " + lot + " desk " + slot + " outside the hall at " + g);
						assertTrue(used.add(g.asLong()), "lot " + lot + " desk " + slot + " overlaps at " + g);
					}
				}
			}
			RingCampusGeometry.Room gate = l.gate();
			for (int u = 0; u < gate.d(); u++) {
				for (int v = 0; v < gate.w(); v++) {
					BlockPos c = gate.cell(u, v, 0);
					assertInLot(lot, c, rings, "gatehouse");
					assertTrue(taken.add(c.asLong()), "lot " + lot + ": gatehouse on a hall at " + c);
				}
			}
			for (int a = -l.court(); a <= l.court(); a++) {
				for (int b = -l.court(); b <= l.court(); b++) {
					BlockPos c = l.at(a, b, 0);
					assertInLot(lot, c, rings, "courtyard");
					assertFalse(taken.contains(c.asLong()), "lot " + lot + ": a building in the courtyard at " + c);
				}
			}
		}
	}

	private static boolean interior(RingCampusGeometry.Room r, BlockPos p) {
		for (int u = 0; u < r.d(); u++) {
			for (int v = 0; v < r.w(); v++) {
				if (r.cell(u, v, 0).equals(p)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void assertInLot(int lot, BlockPos c, int rings, String what) {
		RingCampusGeometry.Zone z = RingCampusGeometry.zone(c.getX(), c.getZ(), rings);
		assertTrue(z.kind() == RingCampusGeometry.Kind.LOT && z.lot() == lot, "lot " + lot + " " + what + " outside its lot at " + c + ": " + z);
	}

	// ------------------------------------------------------------------ determinism and stability

	@Test
	void sameRosterSameCitadelAndIdempotent() {
		HqGenerator.Result a = HqGenerator.plan(roster(10), null, STYLE);
		HqGenerator.Result b = HqGenerator.plan(roster(10), null, STYLE);
		assertEquals(a.layout(), b.layout());
		assertEquals(a.plan().fingerprint(), b.plan().fingerprint());
		HqGenerator.Result again = HqGenerator.plan(roster(10), a.layout(), STYLE);
		assertEquals(a.layout(), again.layout());
		assertEquals(a.plan().fingerprint(), again.plan().fingerprint());
		assertEquals(a.plan().anchors(), again.plan().anchors());
	}

	@Test
	void addingProjectsNeverMovesWhatExists() {
		List<HqGenerator.Result> g = plans();
		for (int i = 1; i < g.size(); i++) {
			BuildPlan before = g.get(i - 1).plan();
			BuildPlan after = g.get(i).plan();
			HqLayout lb = g.get(i - 1).layout();
			HqLayout la = g.get(i).layout();
			Set<String> groups = new TreeSet<>();
			for (HqLayout.Module m : lb.modules()) {
				HqLayout.Module same = la.moduleAt(m.index());
				assertNotNull(same, "module " + m.index() + " kept");
				assertEquals(m.projectKey(), same.projectKey());
				assertEquals(m.desks(), same.desks(), "desks kept");
				groups.add(HqGenerator.group(m.index()));
			}
			// every hall floor, the hub, every special room, the archive and the core: identical cells
			for (String gname : before.groups()) {
				if (gname.startsWith("node_") || gname.equals(HqGenerator.GROUP_ARCHIVE) || gname.equals("cloister") || gname.equals("hub")) {
					groups.add(gname);
				}
			}
			for (String gname : groups) {
				Map<BlockPos, BlockState> a = cells(before, gname);
				Map<BlockPos, BlockState> b = cells(after, gname);
				if (!a.equals(b)) {
					List<String> diff = new ArrayList<>();
					Set<BlockPos> keys = new HashSet<>(a.keySet());
					keys.addAll(b.keySet());
					for (BlockPos k : keys) {
						if (!java.util.Objects.equals(a.get(k), b.get(k)) && diff.size() < 6) {
							diff.add(k.toShortString() + ": " + a.get(k) + " -> " + b.get(k));
						}
					}
					throw new AssertionError("group " + gname + " changed growing to step " + i + ": " + diff);
				}
			}
			for (Workstation w : before.workstations()) {
				assertEquals(w, after.workstation(w.threadId()), "workstation of " + w.threadId());
			}
		}
	}

	private static Map<BlockPos, BlockState> cells(BuildPlan p, String group) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (Placement pl : p.placements(group)) {
			out.put(pl.rel(), pl.state());
		}
		return out;
	}

	// ------------------------------------------------------------------ anchors

	@Test
	void anchorsValidAndReachableWith4Projects() {
		HqAnchorPlacementTest.assertAnchorsValid(plans().get(0).plan(), "ringcampus 4p");
	}

	@Test
	void anchorsValidAndReachableWith10Projects() {
		HqAnchorPlacementTest.assertAnchorsValid(plans().get(1).plan(), "ringcampus 10p");
	}

	@Test
	void anchorsValidAndReachableWith20Projects() {
		HqAnchorPlacementTest.assertAnchorsValid(plans().get(2).plan(), "ringcampus 20p");
	}

	@Test
	void upperFloorsAreReachedByTheirStairs() {
		BuildPlan p = plans().get(2).plan();
		boolean upper = false;
		for (HqLayout.Module m : plans().get(2).layout().modules()) {
			if (RingCampusGeometry.floorOf(m.index()) == 1) {
				upper = true;
				assertEquals(RingCampusGeometry.FLOOR_H + 1, p.anchor(AnchorNames.module(m.index())).block().getY());
			}
		}
		assertTrue(upper, "a project with an upper floor");
	}

	@Test
	void theHubKeepsItsFixtures() {
		BuildPlan p = plans().get(0).plan();
		for (String n : List.of(AnchorNames.SPAWN, AnchorNames.ENTRANCE, AnchorNames.HUB, AnchorNames.HOLOGRAM, AnchorNames.BEACON, AnchorNames.BOARD,
				AnchorNames.BOARD_FRONT, AnchorNames.THREAD_WALL, AnchorNames.THREAD_WALL_END, AnchorNames.THREAD_WALL_FRONT, AnchorNames.ARCHIVE,
				AnchorNames.ARCHIVE_TERMINAL)) {
			assertNotNull(p.anchor(n), n);
		}
		for (String key : List.of("hub@", "review@", "schedule@")) {
			assertTrue(p.anchors().keySet().stream().anyMatch(k -> k.startsWith(AnchorNames.BIG_SCREEN + key)), key);
		}
		for (int y = RingCampusGeometry.PODIUM + 1; y <= RingCampusGeometry.PODIUM + 1 + HqGeometry.HOLOGRAM_TOP; y++) {
			assertTrue(p.at(new BlockPos(0, y, 0)).state().isAir(), "hologram column at y=" + y);
		}
		Anchor beacon = p.anchor(AnchorNames.BEACON);
		assertFalse(p.at(beacon.block().below()).state().isAir(), "beacon support");
		assertEquals(STYLE.archiveTerminal(Direction.SOUTH), p.at(p.anchor(AnchorNames.ARCHIVE_TERMINAL).block()).state());
		// the nave vault: 12 above the floor where it springs, 17 at the crown
		assertTrue(beacon.block().getY() - 1 - RingCampusGeometry.PODIUM >= 17, "vault crown");
	}

	// ------------------------------------------------------------------ footprints

	@Test
	void everythingInsideItsFootprintAndModulesNeverOverlap() {
		for (HqGenerator.Result r : plans()) {
			BuildPlan p = r.plan();
			int rings = RingCampusGeometry.rings(indices(r.layout()));
			HqFootprints.Area env = STYLE.envelope(r.layout());
			for (Placement pl : p.placements()) {
				BlockPos c = pl.rel();
				assertTrue(env.contains(c.getX(), c.getZ()), "outside the envelope " + c);
				assertTrue(c.getY() >= STYLE.bottom() && c.getY() <= STYLE.top(), "height " + c);
				RingCampusGeometry.Zone z = RingCampusGeometry.zone(c.getX(), c.getZ(), rings);
				assertFalse(z.kind() == RingCampusGeometry.Kind.NONE, "no zone at " + c);
				String g = p.groupAt(c);
				assertNotNull(g, "no group " + c);
				if (g.startsWith("module_")) {
					int lot = RingCampusGeometry.lotOf(Integer.parseInt(g.substring(7)));
					assertEquals(RingCampusGeometry.Kind.LOT, z.kind(), g + " outside its lot at " + c);
					assertEquals(lot, z.lot(), g + " in another lot at " + c);
					assertTrue(RingCampusGeometry.lotBounds(lot).contains(c), g + " outside the lot's bounds " + c);
				}
			}
			Map<Long, String> owner = new HashMap<>();
			for (BuildPlan.Module m : p.modules()) {
				Box b = m.box();
				for (int x = b.minX(); x <= b.maxX(); x++) {
					for (int z = b.minZ(); z <= b.maxZ(); z++) {
						for (int y = b.minY(); y <= b.maxY(); y++) {
							String prev = owner.put(BlockPos.asLong(x, y, z), m.id());
							assertNull(prev, "modules " + prev + " and " + m.id() + " overlap");
						}
					}
				}
			}
			for (Placement pl : p.placements()) {
				assertTrue(owner.containsKey(pl.rel().asLong()), "cell " + pl.rel() + " in no module");
			}
		}
	}

	// ------------------------------------------------------------------ desks

	@Test
	void everyDeskHasOneThreeByTwoScreenAndNoLamp() {
		for (HqGenerator.Result r : plans()) {
			BuildPlan p = r.plan();
			assertEquals(r.layout().seats().size(), p.workstations().size());
			Set<BlockPos> screenCells = new HashSet<>();
			for (Workstation w : p.workstations()) {
				List<BlockPos> cells = w.screenCells();
				assertEquals(Workstation.SCREEN_WIDTH * Workstation.SCREEN_HEIGHT, cells.size());
				for (BlockPos c : cells) {
					assertEquals(STYLE.computer(true), p.at(c).state(), "screen cell " + c);
					assertTrue(screenCells.add(c), "two screens share " + c);
				}
				Anchor stand = w.deskAnchor();
				assertEquals(stand.block().relative(stand.facing()).above(), w.computerPos());
				assertTrue(p.at(stand.block()).state().isAir() && p.at(stand.block().above()).state().isAir(), "free standing spot " + stand);
				assertFalse(p.at(stand.block().below()).state().isAir(), "floor under " + stand);
				assertNull(p.anchor(AnchorNames.lamp(w.threadId())), "no desk lamp anchor");
			}
			Set<BlockPos> boardLamps = new HashSet<>();
			p.anchors().forEach((n, a) -> {
				if (n.startsWith(AnchorNames.BOARD_LAMP)) {
					boardLamps.add(a.block());
				}
			});
			for (Placement pl : p.placements()) {
				if (pl.state().equals(STYLE.statusLamp())) {
					assertTrue(boardLamps.contains(pl.rel()), "lamp off the board at " + pl.rel());
				}
			}
		}
	}

	// ------------------------------------------------------------------ light and decoration

	@Test
	void everyWalkableCellHasLightTenOrMore() {
		for (HqGenerator.Result r : plans()) {
			BuildPlan p = r.plan();
			int rings = RingCampusGeometry.rings(indices(r.layout()));
			PlanLight light = PlanLight.of(p);
			Set<BlockPos> reach = new PlanWalkability(p).reachable(p.anchor(AnchorNames.SPAWN).block());
			assertTrue(reach.size() > 1000, "walkable cells: " + reach.size());
			List<String> dark = new ArrayList<>();
			for (BlockPos c : reach) {
				if (RingCampusGeometry.zone(c.getX(), c.getZ(), rings).kind() == RingCampusGeometry.Kind.LAWN) {
					continue;
				}
				int v = light.get(c);
				Placement below = p.at(c.below());
				if (below != null && below.state().getLightDampening() < 15) {
					v = Math.min(v, light.get(c.below()));
				}
				if (v < RingCampusGenerator.MIN_LIGHT && dark.size() < 12) {
					dark.add(c.toShortString() + "=" + v);
				}
			}
			assertTrue(dark.isEmpty(), r.layout().modules().size() + " modules: dark walkable cells " + dark);
		}
	}

	@Test
	void noLooseLanternsAndDecorationIsSupported() {
		BuildPlan p = plans().get(2).plan();
		Set<BlockState> loose = new HashSet<>(List.of(Blocks.LANTERN.defaultBlockState(), HqKit.active().lantern(), HqKit.active().gardenLantern()));
		for (Placement pl : p.placements()) {
			BlockState s = pl.state();
			assertFalse(s.getBlock() == Blocks.LANTERN || s.getBlock() == Blocks.SOUL_LANTERN || loose.contains(s), "a loose lantern at " + pl.rel());
			if (s.getBlock() instanceof CeilingHangingSignBlock) {
				Placement above = p.at(pl.rel().above());
				assertTrue(above != null && !above.state().isAir(), "a hanging sign without a ceiling at " + pl.rel());
			}
			if (s.getBlock() == Blocks.FLOWERING_AZALEA || s.equals(HqKit.active().gardenPlanter()) || s.getBlock() == Blocks.MOSS_CARPET) {
				Placement below = p.at(pl.rel().below());
				assertTrue(below != null && !below.state().isAir(), "decoration without support at " + pl.rel());
			}
		}
	}
}
