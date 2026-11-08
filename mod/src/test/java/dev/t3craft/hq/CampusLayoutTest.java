package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.site.mc.LevelBlockWorld;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The campus layout ({@code hq.style = campus}): deterministic and idempotent, stable when projects
 * and threads are added (nothing that exists moves), parcels freed and reused, every standing anchor
 * free and reachable on foot from the spawn with 4, 10 and 20 projects, every group inside its
 * footprint, one 3×2 screen per desk and no desk lamps.
 */
class CampusLayoutTest {
	private static HqStyle campus;

	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
		HqFixtures.use(null);
		HqKit.use(null);
		campus = HqStyles.byId("campus");
	}

	@AfterAll
	static void reset() {
		HqStyles.use(null);
	}

	/** Thread counts of the demo roster (one big project grows upper storeys, one small, one with 13). */
	static final int[] THREADS = {9, 4, 13, 2, 6, 1, 7, 3, 26, 5, 2, 8, 1, 4, 11, 3, 6, 2, 5, 3};
	static final String[] ENVS = {"vps", "acmesite", "laptop"};

	/** {@code n} projects with varied thread counts, three environments, a settled thread in every third project. */
	static HqInput roster(int n) {
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < n; p++) {
			String env = ENVS[p % ENVS.length];
			String key = env + ":proj" + (char) ('a' + p);
			for (int t = 0; t < THREADS[p % THREADS.length]; t++) {
				b.thread(key, "proj" + (char) ('a' + p), env, env.toUpperCase(java.util.Locale.ROOT), key + "-t" + t);
			}
			if (p % 3 == 0) {
				b.thread(key, "proj" + (char) ('a' + p), env, env.toUpperCase(java.util.Locale.ROOT), key + "-s0", true);
			}
		}
		return b.build();
	}

	private static HqGenerator.Result plan(HqInput in, HqLayout previous) {
		return HqGenerator.plan(in, previous, campus);
	}

	@Test
	void campusIsSelectableButNotAmongTheSpineStyles() {
		assertNotNull(campus);
		assertTrue(campus.ownLayout());
		assertFalse(HqStyles.all().contains(campus), "the spine-geometry tests keep cherry and classic only");
		assertTrue(HqStyles.ids().contains("campus"));
		assertEquals("campus", HqStyles.parse(com.google.gson.JsonParser.parseString("{\"hq\":{\"style\":\"campus\"}}").getAsJsonObject())
				.style().id());
		for (HqStyle s : HqStyles.all()) {
			assertFalse(s.ownLayout(), s.id());
			assertNull(s.layoutPlan(HqLayout.plan(roster(2))), s.id());
		}
		// the active layout (feat/hq-layouts) drives HqPlans' allocation
		dev.t3craft.hq.layout.HqLayouts.use("campus");
		HqInput in = roster(3);
		try {
			assertEquals(CampusLayout.allocate(in, null), HqPlans.plan(in, null).layout());
		} finally {
			dev.t3craft.hq.layout.HqLayouts.use(null);
		}
		assertEquals(HqLayout.allocate(in, null), HqPlans.plan(in, null).layout());
	}

	@Test
	void deterministicIdempotentAndIndependentOfRosterOrder() {
		for (int n : new int[] {0, 4, 10}) {
			HqInput in = roster(n);
			HqGenerator.Result a = plan(in, null);
			HqGenerator.Result b = plan(in, null);
			assertEquals(a.layout(), b.layout());
			assertEquals(a.plan().fingerprint(), b.plan().fingerprint(), n + " projects");
			HqGenerator.Result again = plan(in, a.layout());
			assertEquals(a.layout(), again.layout(), "idempotent");
			assertEquals(a.plan().fingerprint(), again.plan().fingerprint());
			List<HqInput.Entry> lines = HqTestSupport.roster(in);
			Collections.shuffle(lines, new Random(n));
			assertEquals(a.plan().fingerprint(), plan(HqInput.fromRoster(lines), null).plan().fingerprint(), "roster order");
		}
	}

	@Test
	void parcelsFollowTheSpiralAndProjectsNeverLeaveTheirBarrio() {
		HqLayout l = CampusLayout.allocate(roster(20), null);
		Map<String, Integer> parcel = new HashMap<>();
		for (HqLayout.Module m : l.modules()) {
			Integer old = parcel.put(m.projectKey(), CampusLayout.parcelOf(m.index()));
			assertTrue(old == null || old == CampusLayout.parcelOf(m.index()), "one barrio per project: " + m.projectKey());
			assertTrue(CampusLayout.buildingOf(m.index()) < CampusLayout.BUILDINGS);
		}
		assertEquals(20, parcel.size());
		assertEquals(Set.copyOf(java.util.stream.IntStream.range(0, 20).boxed().toList()), Set.copyOf(parcel.values()), "dense parcels 0..19");
		// the big project (26 threads) fills its three halls and adds upper storeys, in its own barrio
		String big = "laptop:proj" + (char) ('a' + 8);
		List<HqLayout.Module> mine = l.modulesOf(big);
		assertEquals(5, mine.size());
		assertEquals(List.of(0, 1, 2, 3, 4), mine.stream().map(m -> CampusLayout.buildingOf(m.index())).toList());
		// ring 1 (6 parcels) is full before ring 2 opens; the corners alternate east / west
		assertEquals(1, CampusGeometry.ringOf(5));
		assertEquals(2, CampusGeometry.ringOf(6));
		assertEquals(new CampusGeometry.Hex(1, 0), CampusGeometry.parcel(0));
		assertEquals(new CampusGeometry.Hex(-1, 0), CampusGeometry.parcel(1));
		for (int p = 0; p < 60; p++) {
			for (int o = 0; o < p; o++) {
				assertNotEquals(CampusGeometry.parcel(o), CampusGeometry.parcel(p));
			}
		}
	}

	@Test
	void addingProjectsAndThreadsMovesNothingThatExists() {
		HqGenerator.Result r4 = plan(roster(4), null);
		HqGenerator.Result r10 = plan(roster(10), r4.layout());
		HqGenerator.Result r20 = plan(roster(20), r10.layout());
		assertKept(r4, r10);
		assertKept(r10, r20);
		assertKept(r4, r20);
		// one more thread for a project with a full hall: a new hall in the same barrio, the rest untouched
		HqInput more = roster(4);
		String key = "vps:proja";
		for (int t = 9; t < 14; t++) {
			more = HqTestSupport.plus(more, key, key + "-t" + t);
		}
		HqGenerator.Result grown = plan(more, r4.layout());
		assertKept(r4, grown);
		assertEquals(3, grown.layout().modulesOf(key).size(), "9 + 5 threads: a third hall");
		for (HqLayout.Module m : grown.layout().modulesOf(key)) {
			assertEquals(CampusLayout.parcelOf(r4.layout().modulesOf(key).get(0).index()), CampusLayout.parcelOf(m.index()), "same barrio");
		}
	}

	/** Every module of {@code a} keeps its index and desks in {@code b}; every block of {@code a}'s buildings and barrios is unchanged. */
	private static void assertKept(HqGenerator.Result a, HqGenerator.Result b) {
		for (HqLayout.Module m : a.layout().modules()) {
			HqLayout.Module n = b.layout().moduleAt(m.index());
			assertNotNull(n, "module " + m.index() + " kept");
			assertEquals(m.projectKey(), n.projectKey());
			for (HqLayout.Desk d : m.desks()) {
				assertEquals(d.threadId(), n.threadAt(d.slot()), "desk kept");
			}
		}
		int checked = 0;
		// what may change: a building that got new desks, the barrio (lot paving) of a parcel with a new
		// building, and the roof of a hall that got an upper storey (the roof moves up a storey)
		Set<String> grown = new HashSet<>();
		for (HqLayout.Module m : b.layout().modules()) {
			HqLayout.Module old = a.layout().moduleAt(m.index());
			if (old == null) {
				grown.add("barrio_" + CampusLayout.parcelOf(m.index()));
				if (CampusLayout.storeyOf(CampusLayout.buildingOf(m.index())) == 1) {
					grown.add(HqGenerator.group(m.index() - 3));
				}
			} else if (!old.desks().equals(m.desks())) {
				grown.add(HqGenerator.group(m.index()));
			}
		}
		for (Placement p : a.plan().placements()) {
			String g = a.plan().groupAt(p.rel());
			if (g == null || g.startsWith("park_") || grown.contains(g)) {
				continue; // a park may become a barrio; a barrio that gains a hall repaves that lot
			}
			Placement q = b.plan().at(p.rel());
			assertNotNull(q, g + " lost " + p.rel());
			assertTrue(p.sameContent(q), g + " changed at " + p.rel() + ": " + p.state() + " -> " + q.state());
			checked++;
		}
		assertTrue(checked > 10_000, "checked " + checked);
		for (Workstation w : a.plan().workstations()) {
			assertEquals(w, b.plan().workstation(w.threadId()), "workstation of " + w.threadId());
		}
	}

	@Test
	void aProjectThatLeavesFreesItsParcelForTheNextOne() {
		HqInput in = roster(6);
		HqLayout l1 = CampusLayout.allocate(in, null);
		String gone = "acmesite:projb";
		int freed = CampusLayout.parcelOf(l1.modulesOf(gone).get(0).index());
		HqInput without = in;
		for (String t : in.project(gone).threads()) {
			without = HqTestSupport.minus(without, t);
		}
		HqLayout l2 = CampusLayout.allocate(without, l1);
		assertTrue(l2.modulesOf(gone).isEmpty());
		for (HqLayout.Module m : l2.modules()) {
			assertEquals(l1.moduleAt(m.index()).projectKey(), m.projectKey(), "the others stay");
		}
		HqLayout l3 = CampusLayout.allocate(HqTestSupport.plus(without, "vps:newcomer", "vps:newcomer-t0"), l2);
		assertEquals(freed, CampusLayout.parcelOf(l3.modulesOf("vps:newcomer").get(0).index()), "lowest free parcel reused");
		// and its plan has a park where the barrio was until then
		BuildPlan p2 = new HqGenerator(campus).generate(l2);
		assertTrue(p2.groups().contains("park_" + freed));
	}

	@ParameterizedTest(name = "{0} projects")
	@ValueSource(ints = {4, 10, 20})
	void everyStandingAnchorIsFreeStandableAndReachable(int n) {
		BuildPlan p = plan(roster(n), null).plan();
		HqAnchorPlacementTest.assertAnchorsValid(p, n + " projects");
		// the special rooms publish their spots
		for (String room : List.of("review", "scheduled", "errors", "lounge", "archive")) {
			assertNotNull(p.anchor("walk_room_" + room), room);
		}
		assertNotNull(p.anchor(AnchorNames.ARCHIVE_TERMINAL));
		assertNotNull(p.anchor(AnchorNames.THREAD_WALL));
		assertNotNull(p.anchor(AnchorNames.BEACON));
	}

	@Test
	void upperStoreysAndAHistoryKeepAnchorsReachable() {
		HqInput in = roster(3);
		HqLayout layout = null;
		String key = "laptop:projc";
		for (int t = 13; t < 33; t += 4) {
			for (int k = t; k < t + 4; k++) {
				in = HqTestSupport.plus(in, key, key + "-t" + k);
			}
			HqGenerator.Result r = plan(in, layout);
			layout = r.layout();
			HqAnchorPlacementTest.assertAnchorsValid(r.plan(), t + " threads");
		}
		assertTrue(layout.modulesOf(key).size() >= 5, "upper storeys were added");
		// empty the ground hall of lot 1 while its upper storey stays: the shell is kept, the stair still leads up
		HqLayout.Module ground = layout.moduleAt(CampusLayout.index(CampusLayout.parcelOf(layout.modulesOf(key).get(0).index()), 0));
		for (HqLayout.Desk d : ground.desks()) {
			in = HqTestSupport.settle(in, d.threadId());
		}
		HqGenerator.Result r = plan(in, layout);
		assertNull(r.layout().moduleAt(ground.index()));
		HqAnchorPlacementTest.assertAnchorsValid(r.plan(), "ground hall emptied");
	}

	@ParameterizedTest(name = "{0} projects")
	@ValueSource(ints = {4, 10, 20})
	void everythingStaysInsideItsFootprint(int n) {
		HqGenerator.Result r = plan(roster(n), null);
		BuildPlan p = r.plan();
		int parcels = 0;
		for (HqLayout.Module m : r.layout().modules()) {
			parcels = Math.max(parcels, CampusLayout.parcelOf(m.index()) + 1);
		}
		HqFootprints.Area env = CampusFootprints.envelope(parcels);
		List<HqFootprints.Area> plots = new ArrayList<>();
		plots.add(CampusFootprints.hub());
		for (String k : CampusFootprints.pavilions()) {
			plots.add(CampusFootprints.pavilion(k));
		}
		for (int q = 0; q < CampusFootprints.parcelsWithin(3); q++) {
			plots.add(CampusFootprints.parcel(q));
		}
		for (int i = 0; i < plots.size(); i++) {
			for (int j = 0; j < i; j++) {
				assertFalse(plots.get(i).intersects(plots.get(j)), "plots overlap: " + plots.get(i) + " " + plots.get(j));
			}
		}
		for (Placement pl : p.placements()) {
			BlockPos c = pl.rel();
			String g = p.groupAt(c);
			assertNotNull(g, "no owner " + c);
			assertTrue(c.getY() >= CampusFootprints.bottom() && c.getY() <= CampusFootprints.top(), "height " + c);
			assertTrue(env.contains(c.getX(), c.getZ()), "outside the envelope " + c + " (" + g + ")");
			HqFootprints.Area own;
			if (g.equals("hub")) {
				own = CampusFootprints.hub();
			} else if (g.equals("archive")) {
				own = CampusFootprints.pavilion("archive");
			} else if (g.equals("gate")) {
				own = CampusFootprints.pavilion("gate");
			} else if (g.startsWith("pavilion_")) {
				own = CampusFootprints.pavilion(g.substring("pavilion_".length()));
			} else if (g.startsWith("barrio_") || g.startsWith("park_")) {
				own = CampusFootprints.parcel(Integer.parseInt(g.substring(g.indexOf('_') + 1)));
			} else if (g.startsWith("module_")) {
				own = CampusFootprints.parcel(CampusFootprints.parcelOf(Integer.parseInt(g.substring("module_".length()))));
			} else {
				assertTrue(g.startsWith("grounds_"), g);
				for (HqFootprints.Area a : plots) {
					assertFalse(a.contains(c.getX(), c.getZ()), "network cell " + c + " inside a plot " + a);
				}
				continue;
			}
			assertTrue(own.contains(c.getX(), c.getZ()), g + " outside its area at " + c);
		}
	}

	@Test
	void everyDeskHasOne3x2ScreenAndNoLamp() {
		BuildPlan p = plan(roster(10), null).plan();
		BlockState computer = campus.computer(true);
		BlockState lamp = campus.statusLamp();
		Set<BlockPos> boardLamps = new HashSet<>();
		for (Anchor a : p.anchors().values()) {
			if (a.name().startsWith(AnchorNames.BOARD_LAMP)) {
				boardLamps.add(a.block());
			}
		}
		for (Placement pl : p.placements()) {
			if (pl.state().getBlock() == lamp.getBlock()) {
				assertTrue(boardLamps.contains(pl.rel()), "a lamp outside the board at " + pl.rel());
			}
			if (pl.state().getBlock() instanceof LeavesBlock) {
				assertTrue(pl.state().getValue(BlockStateProperties.PERSISTENT), "persistent leaves " + pl.rel());
			}
		}
		int threads = 0;
		for (HqInput.Project pr : roster(10).projects()) {
			threads += pr.threads().size();
		}
		assertEquals(threads, p.workstations().size(), "a desk for every active thread");
		Set<BlockPos> screens = new HashSet<>();
		for (Workstation w : p.workstations()) {
			List<BlockPos> cells = w.screenCells();
			assertEquals(6, cells.size());
			for (BlockPos c : cells) {
				assertEquals(computer, p.at(c).state(), "screen cell " + c);
				assertTrue(screens.add(c), "screens never shared");
			}
			assertEquals(w.screenOrigin(), Workstation.screenOriginFor(w.computerPos(), w.screenFacing()));
			BlockPos stand = w.deskAnchor().block();
			assertEquals(w.computerPos(), stand.relative(w.deskAnchor().facing()).above(), "monitor in front, at eye level");
			assertTrue(p.at(stand).state().isAir() && p.at(stand.above()).state().isAir(), "free standing cell " + stand);
			assertFalse(p.at(stand.below()).state().isAir());
			assertNull(p.anchor(AnchorNames.lamp(w.threadId())), "no desk lamp anchor");
		}
		// desks face all four directions (the barrios turn)
		Set<net.minecraft.core.Direction> facings = new HashSet<>();
		p.workstations().forEach(w -> facings.add(w.deskAnchor().facing()));
		assertEquals(4, facings.size());
	}

	@Test
	void theHubKeepsItsFixtures() {
		BuildPlan p = plan(roster(4), null).plan();
		for (int y = 1; y <= HqGeometry.HOLOGRAM_TOP; y++) {
			assertTrue(p.at(new BlockPos(0, y, 0)).state().isAir(), "hologram column y=" + y);
		}
		assertFalse(p.at(p.anchor(AnchorNames.BEACON).block()).state().isAir());
		Anchor wall = p.anchor(AnchorNames.THREAD_WALL);
		Anchor end = p.anchor(AnchorNames.THREAD_WALL_END);
		assertEquals(wall.block().getY() + 3, end.block().getY());
		int big = 0;
		for (Anchor a : p.anchors().values()) {
			AnchorNames.BigScreen bs = AnchorNames.parseBigScreen(a.name());
			if (bs != null) {
				big++;
				assertFalse(p.at(a.block()).state().isAir(), a.name());
			}
		}
		assertTrue(big >= 4 + 4, "hub, pavilions and one per barrio: " + big);
		assertEquals(Blocks.LECTERN, p.at(p.anchor(AnchorNames.ARCHIVE_TERMINAL).block()).state().getBlock(), "archive terminal stand-in");
	}

	@Test
	void everyBlockParsesWithTheRealParserWithoutTheBlocksModule() {
		Set<BlockState> states = new LinkedHashSet<>();
		for (Placement p : plan(roster(10), null).plan().placements()) {
			states.add(p.state());
		}
		for (BlockState st : states) {
			String key = LevelBlockWorld.keyOf(st);
			assertTrue(key.startsWith("minecraft:"), "vanilla only without the blocks module, got " + key);
			assertSame(st, LevelBlockWorld.parse(BuiltInRegistries.BLOCK, key), key);
		}
	}

	@Test
	void sparseModulesOwnOnlyTheirCells() {
		BuildPlan p = plan(roster(4), null).plan();
		int sparse = 0;
		for (BuildPlan.Module m : p.modules()) {
			if (m.sparse()) {
				sparse++;
				for (long k : m.cells()) {
					BlockPos c = BlockPos.of(k);
					assertNotNull(p.at(c));
					assertEquals(m.group(), p.groupAt(c), "cell " + c + " of " + m.id());
				}
			}
		}
		assertTrue(sparse > 10);
		// the spine styles have no sparse module
		for (BuildPlan.Module m : new HqGenerator(HqStyles.defaultStyle()).generate(HqLayout.plan(roster(4))).modules()) {
			assertFalse(m.sparse());
		}
	}
}
