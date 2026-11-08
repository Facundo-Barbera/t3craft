package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * layout/ring ("Ciudad en anillos"): growth rule, determinism, stability, anchors, footprints and
 * desk screens of {@link RingStyle} / {@link RingGenerator}.
 */
class RingLayoutTest {
	private static RingStyle RING;

	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
		RING = (RingStyle) HqStyles.byId("ring");
	}

	/** {@code n} projects with varied thread counts (7+ threads: a second floor, 13+: a third). */
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

	/** Layouts for 4, 10 and 20 projects, each allocated against the previous one. */
	static List<HqGenerator.Result> growth() {
		List<HqGenerator.Result> out = new ArrayList<>();
		HqLayout layout = null;
		for (int n : new int[] {4, 10, 20}) {
			HqGenerator.Result r = HqGenerator.plan(roster(n), layout, RING);
			layout = r.layout();
			out.add(r);
		}
		return out;
	}

	private static List<HqGenerator.Result> cached;

	private static synchronized List<HqGenerator.Result> plans() {
		if (cached == null) {
			cached = growth();
		}
		return cached;
	}

	// ------------------------------------------------------------------ growth rule

	@Test
	void styleIsSelectableButKeepsTheGridStylesUntouched() {
		assertNotNull(HqStyles.byId("ring"));
		assertTrue(HqStyles.ids().contains("ring"));
		assertFalse(HqStyles.all().contains(RING), "all() stays the grid styles (cherry, classic)");
		assertFalse(HqStyles.defaultStyle().ownLayout());
		assertFalse(HqStyles.classic().ownLayout());
		assertTrue(RING.ownLayout());
	}

	@Test
	void projectsFillRingOneThenRingTwoAndBigProjectsGrowFloors() {
		HqLayout l4 = plans().get(0).layout();
		Map<String, List<Integer>> by = new HashMap<>();
		for (HqLayout.Module m : l4.modules()) {
			by.computeIfAbsent(m.projectKey(), k -> new ArrayList<>()).add(m.index());
		}
		assertEquals(List.of(0), by.get("avps:proj"));
		assertEquals(List.of(1, 101), by.get("blaptop:proj"), "9 threads: sector 1 plus a floor");
		assertEquals(List.of(2), by.get("cacmesite:proj"));
		assertEquals(List.of(3), by.get("dcloud:proj"));
		HqLayout l10 = plans().get(1).layout();
		Set<Integer> sectors10 = new TreeSet<>();
		for (HqLayout.Module m : l10.modules()) {
			sectors10.add(RingGeometry.sectorOf(m.index()));
			assertTrue(RingGeometry.floorOf(m.index()) < RingGeometry.MAX_FLOORS);
		}
		assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), sectors10, "ring 1 full, ring 2 opened with two sectors");
		assertEquals(2, RingGeometry.rings(indices(l10)));
		HqLayout l20 = plans().get(2).layout();
		assertEquals(2, RingGeometry.rings(indices(l20)), "20 projects fit in rings 1 and 2");
		long floors = l20.modules().stream().filter(m -> RingGeometry.floorOf(m.index()) > 0).count();
		assertEquals(5, floors, "9, 13 (two floors), 7 and 8 threads");
	}

	private static List<Integer> indices(HqLayout l) {
		return l.modules().stream().map(HqLayout.Module::index).toList();
	}

	@Test
	void fixedOrderSpreadsTheFirstProjectsRoundTheRing() {
		assertEquals(List.of(0, 4, 2, 6, 1, 5, 3, 7), java.util.stream.IntStream.range(0, 8).map(RingGeometry::position).boxed().toList());
		for (int s = 0; s < RingGeometry.MAX_SECTORS; s++) {
			int m = RingGeometry.ringOf(s);
			assertEquals(s, RingGeometry.sectorAt(m, RingGeometry.position(s)));
		}
	}

	@Test
	void aFreedSectorIsReusedAndAFullFloorSpillsToTheNeighbour() {
		HqLayout l = plans().get(1).layout();
		// project c (sector 2) leaves: its sector is free and the next new project takes it
		HqInput in = roster(10);
		List<HqInput.Project> ps = new ArrayList<>(in.projects());
		ps.removeIf(p -> p.key().equals("cacmesite:proj"));
		ps.add(new HqInput.Project("zvps:late", "Late", "vps", "vps", List.of("zvps:late-t0")));
		HqLayout after = HqLayout.allocate(new HqInput(ps), l, RING.placer());
		assertEquals(2, after.modulesOf("zvps:late").get(0).index(), "the freed sector 2");
		for (HqLayout.Module m : l.modules()) {
			if (!m.projectKey().equals("cacmesite:proj")) {
				assertEquals(m.index(), after.modulesOf(m.projectKey()).stream().filter(x -> x.index() == m.index()).findFirst().orElseThrow().index());
			}
		}
		// a project with more than three floors' worth of threads spills into a contiguous sector
		java.util.SortedSet<Integer> used = new TreeSet<>(List.of(0, 100, 200));
		int next = RingGeometry.place("x", new TreeSet<>(List.of(0, 100, 200)), used);
		int p0 = RingGeometry.position(0);
		int pn = RingGeometry.position(next);
		assertEquals(1, Math.min(Math.floorMod(pn - p0, 8), Math.floorMod(p0 - pn, 8)), "neighbour of sector 0: " + next);
	}

	// ------------------------------------------------------------------ determinism and stability

	@Test
	void sameRosterSameCityAndIdempotent() {
		HqGenerator.Result a = HqGenerator.plan(roster(10), null, RING);
		HqGenerator.Result b = HqGenerator.plan(roster(10), null, RING);
		assertEquals(a.layout(), b.layout());
		assertEquals(a.plan().fingerprint(), b.plan().fingerprint());
		HqGenerator.Result again = HqGenerator.plan(roster(10), a.layout(), RING);
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
			// every sector building, every special room and the archive: identical cells
			for (String gname : before.groups()) {
				if (gname.startsWith("node_") || gname.equals(HqGenerator.GROUP_ARCHIVE) || gname.equals("cloister")) {
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
		HqAnchorPlacementTest.assertAnchorsValid(plans().get(0).plan(), "ring 4p");
	}

	@Test
	void anchorsValidAndReachableWith10Projects() {
		HqAnchorPlacementTest.assertAnchorsValid(plans().get(1).plan(), "ring 10p");
	}

	@Test
	void anchorsValidAndReachableWith20Projects() {
		HqAnchorPlacementTest.assertAnchorsValid(plans().get(2).plan(), "ring 20p");
	}

	@Test
	void theHubKeepsItsFixtures() {
		BuildPlan p = plans().get(0).plan();
		for (String n : List.of(AnchorNames.SPAWN, AnchorNames.ENTRANCE, AnchorNames.HUB, AnchorNames.HOLOGRAM, AnchorNames.BEACON, AnchorNames.BOARD,
				AnchorNames.BOARD_FRONT, AnchorNames.THREAD_WALL, AnchorNames.THREAD_WALL_END, AnchorNames.THREAD_WALL_FRONT, AnchorNames.ARCHIVE,
				AnchorNames.ARCHIVE_TERMINAL)) {
			assertNotNull(p.anchor(n), n);
		}
		assertTrue(p.anchors().keySet().stream().anyMatch(k -> k.startsWith(AnchorNames.BIG_SCREEN + "hub@")), "hub big screen");
		assertTrue(p.anchors().keySet().stream().anyMatch(k -> k.startsWith(AnchorNames.BIG_SCREEN + "review@")), "review tower screen");
		// the hologram's column is free above the hub floor, the beacon sits on the dome
		Anchor holo = p.anchor(AnchorNames.HOLOGRAM);
		for (int y = RingGeometry.PODIUM + 1; y <= RingGeometry.PODIUM + 1 + HqGeometry.HOLOGRAM_TOP; y++) {
			assertTrue(p.at(new BlockPos(0, y, 0)).state().isAir(), "hologram column at y=" + y);
		}
		assertTrue(holo.y() > RingGeometry.PODIUM);
		Anchor beacon = p.anchor(AnchorNames.BEACON);
		assertFalse(p.at(beacon.block().below()).state().isAir(), "beacon support");
		// the archive is a terminal, not shelves
		assertEquals(RING.archiveTerminal(net.minecraft.core.Direction.NORTH), p.at(p.anchor(AnchorNames.ARCHIVE_TERMINAL).block()).state());
	}

	// ------------------------------------------------------------------ footprints

	@Test
	void everythingInsideItsFootprint() {
		for (HqGenerator.Result r : plans()) {
			BuildPlan p = r.plan();
			int rings = RingGeometry.rings(indices(r.layout()));
			int R = RingGeometry.radius(rings);
			Map<String, Integer> sectorOfGroup = new HashMap<>();
			for (HqLayout.Module m : r.layout().modules()) {
				sectorOfGroup.put(HqGenerator.group(m.index()), RingGeometry.sectorOf(m.index()));
			}
			for (Placement pl : p.placements()) {
				BlockPos c = pl.rel();
				assertTrue(Math.abs(c.getX()) <= R && Math.abs(c.getZ()) <= R, "outside the envelope " + c);
				RingGeometry.Zone z = RingGeometry.zone(c.getX(), c.getZ(), rings);
				assertNotEquals(RingGeometry.Kind.NONE, z.kind(), "no zone at " + c);
				String g = p.groupAt(c);
				assertNotNull(g, "no group " + c);
				boolean ownedByModule = false;
				for (BuildPlan.Module m : p.modules()) {
					if (m.group().equals(g) && m.owns(c.getX(), c.getY(), c.getZ())) {
						ownedByModule = true;
						break;
					}
				}
				if (!ownedByModule) {
					throw new AssertionError("cell " + c + " of " + g + " outside that group's modules");
				}
				if (g.startsWith("module_")) {
					assertEquals(RingGeometry.Kind.SECTOR, z.kind(), g + " outside its sector at " + c);
					assertEquals((int) sectorOfGroup.getOrDefault(g, z.sector()), z.sector(), g + " in another sector at " + c);
					Box sb = RingGeometry.sectorBounds(z.sector());
					assertTrue(sb.contains(c), g + " outside the sector's bounds " + c);
				}
			}
			// module boxes never overlap
			List<BuildPlan.Module> ms = p.modules();
			Map<Long, String> owner = new HashMap<>();
			for (BuildPlan.Module m : ms) {
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
		}
	}

	// ------------------------------------------------------------------ desks

	@Test
	void deskSpotsAndStairsFitInsideEverySector() {
		RingGenerator gen = new RingGenerator(RING);
		for (int s = 0; s < RingGeometry.MAX_SECTORS; s++) {
			int rings = RingGeometry.ringOf(s);
			Set<Long> used = new HashSet<>();
			RingGeometry.Stairs st = RingGeometry.stairs(s);
			for (int f = 0; f < RingGeometry.MAX_FLOORS - 1; f++) {
				for (int v : st.lane(f)) {
					for (int i = -1; i <= 7; i++) {
						BlockPos c = st.cell(i, v, 0);
						assertInterior(s, c, rings, "stairs");
						used.add(col(c));
					}
				}
			}
			for (int slot = 0; slot < HqLayout.DESKS; slot++) {
				RingGeometry.DeskSpot d = RingGeometry.desk(s, 0, slot);
				net.minecraft.core.Direction right = d.facing().getClockWise();
				List<BlockPos> mine = List.of(d.desk().relative(right, -1), d.desk(), d.desk().relative(right, 1), d.stand());
				for (BlockPos c : mine) {
					assertInterior(s, c, rings, "desk " + slot);
					assertTrue(used.add(col(c)), "sector " + s + " desk " + slot + " overlaps at " + c);
				}
			}
		}
	}

	private static long col(BlockPos p) {
		return ((long) p.getX() << 32) | (p.getZ() & 0xFFFFFFFFL);
	}

	private static void assertInterior(int s, BlockPos c, int rings, String what) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (Math.abs(dx) + Math.abs(dz) > 1) {
					continue;
				}
				RingGeometry.Zone z = RingGeometry.zone(c.getX() + dx, c.getZ() + dz, rings);
				assertTrue(z.kind() == RingGeometry.Kind.SECTOR && z.sector() == s, "sector " + s + " " + what + " not inside at " + c);
			}
		}
	}

	@Test
	void everyDeskHasOneMergedThreeByTwoScreenAndNoLamp() {
		for (HqGenerator.Result r : plans()) {
			BuildPlan p = r.plan();
			int seated = r.layout().seats().size();
			assertEquals(seated, p.workstations().size());
			Set<BlockPos> screenCells = new HashSet<>();
			for (Workstation w : p.workstations()) {
				List<BlockPos> cells = w.screenCells();
				assertEquals(Workstation.SCREEN_WIDTH * Workstation.SCREEN_HEIGHT, cells.size());
				assertEquals(Workstation.screenCells(Workstation.screenOriginFor(w.computerPos(), w.screenFacing()), w.screenRight()), cells,
						"the screen comes from Workstation.screenOriginFor / screenCells");
				for (BlockPos c : cells) {
					assertEquals(RING.computer(true), p.at(c).state(), "screen cell " + c);
					assertTrue(screenCells.add(c), "two screens share " + c);
				}
				Anchor stand = w.deskAnchor();
				assertEquals(stand.block().relative(stand.facing()).above(), w.computerPos());
				assertTrue(p.at(stand.block()).state().isAir() && p.at(stand.block().above()).state().isAir(), "free standing spot " + stand);
				assertFalse(p.at(stand.block().below()).state().isAir(), "floor under " + stand);
			}
			// status lamps only on the hub board (desks have none)
			Set<BlockPos> boardLamps = new HashSet<>();
			p.anchors().forEach((n, a) -> {
				if (n.startsWith(AnchorNames.BOARD_LAMP)) {
					boardLamps.add(a.block());
				}
			});
			for (Placement pl : p.placements()) {
				if (pl.state().equals(RING.statusLamp())) {
					assertTrue(boardLamps.contains(pl.rel()), "lamp off the board at " + pl.rel());
				}
			}
		}
	}

	@Test
	void sectorsStackFloorsWithWalkableStairs() {
		BuildPlan p = plans().get(2).plan();
		HqLayout l = plans().get(2).layout();
		boolean third = false;
		for (HqLayout.Module m : l.modules()) {
			if (RingGeometry.floorOf(m.index()) == 2) {
				third = true;
				// the third floor's door anchor is reachable from the spawn (checked by the validator): it stands at y = 15
				assertEquals(RingGeometry.floorY(2) + 1, p.anchor(AnchorNames.module(m.index())).block().getY());
			}
		}
		assertTrue(third, "a project with three floors");
	}
}
