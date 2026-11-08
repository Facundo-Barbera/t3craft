package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.site.mc.LevelBlockWorld;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * layout/vertical: towers with an atrium, bridges and a podium ({@link TowerLayout},
 * {@link TowerGenerator}). Determinism, stability under growth, freeing and reuse of half floors,
 * walkable and reachable anchors with 4, 10 and 20 projects, footprints, 3×2 desk screens.
 */
class VerticalLayoutTest {
	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
	}

	private static HqStyle vertical() {
		HqStyle s = HqStyles.byId("vertical");
		assertNotNull(s);
		return s;
	}

	private static HqGenerator.Result plan(HqInput in, HqLayout previous) {
		return HqGenerator.plan(in, previous, vertical());
	}

	@Test
	void selectableByIdButNotAmongTheSharedGeometryStyles() {
		assertTrue(HqStyles.ids().contains("vertical"));
		assertTrue(HqStyles.all().stream().noneMatch(s -> s.id().equals("vertical")), "all() keeps the shared-geometry styles only");
		assertEquals("vertical", HqStyles.parse(com.google.gson.JsonParser.parseString("{\"hq\":{\"style\":\"vertical\"}}").getAsJsonObject())
				.style().id());
		assertEquals("cherry", HqStyles.active().id(), "default unchanged");
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 4, 10, 20})
	void deterministicIdempotentAndIgnoresRosterOrder(int projects) {
		HqInput in = TowerLayout.sampleRoster(projects);
		HqGenerator.Result a = plan(in, null);
		assertEquals(a.plan().fingerprint(), plan(in, null).plan().fingerprint());
		HqGenerator.Result again = plan(in, a.layout());
		assertEquals(a.layout(), again.layout());
		assertEquals(a.plan().fingerprint(), again.plan().fingerprint());
		assertEquals(a.plan().anchors(), again.plan().anchors());
		List<HqInput.Entry> roster = HqTestSupport.roster(in);
		for (long seed = 1; seed <= 2; seed++) {
			List<HqInput.Entry> shuffled = new ArrayList<>(roster);
			Collections.shuffle(shuffled, new Random(seed));
			assertEquals(a.plan().fingerprint(), plan(HqInput.fromRoster(shuffled), null).plan().fingerprint(), "seed " + seed);
		}
	}

	@Test
	void towersAreBornAsTheProjectsGrow() {
		assertEquals(Set.of(1), TowerLayout.towers(plan(TowerLayout.sampleRoster(4), null).layout()));
		assertEquals(Set.of(1, 2), TowerLayout.towers(plan(TowerLayout.sampleRoster(10), null).layout()));
		assertEquals(Set.of(1, 2, 3), TowerLayout.towers(plan(TowerLayout.sampleRoster(20), null).layout()));
		// parcels: tower 1 bottom-up east then west, then tower 2, then tower 3
		assertEquals(1, TowerLayout.parcel(0).floor());
		assertEquals(1, TowerLayout.parcel(0).side());
		assertEquals(-1, TowerLayout.parcel(1).side());
		assertEquals(5, TowerLayout.parcel(9).floor());
		assertSame(TowerLayout.T2, TowerLayout.parcel(10).tower());
		assertSame(TowerLayout.T3, TowerLayout.parcel(TowerLayout.CAPACITY - 1).tower());
		assertNull(TowerLayout.parcel(TowerLayout.CAPACITY));
	}

	@Test
	void growingNeverMovesWhatAlreadyStands() {
		HqGenerator.Result r4 = plan(TowerLayout.sampleRoster(4), null);
		HqGenerator.Result r10 = plan(TowerLayout.sampleRoster(10), r4.layout());
		HqGenerator.Result r20 = plan(TowerLayout.sampleRoster(20), r10.layout());
		assertStable(r4, r10);
		assertStable(r10, r20);
	}

	/** Every cell of {@code a} stays in {@code b}, except vacant half floors (which fill) and T1's bridge doors (which open). */
	private static void assertStable(HqGenerator.Result a, HqGenerator.Result b) {
		Map<Integer, HqLayout.Module> next = new java.util.HashMap<>();
		for (HqLayout.Module m : b.layout().modules()) {
			next.put(m.index(), m);
		}
		for (HqLayout.Module m : a.layout().modules()) {
			HqLayout.Module n = next.get(m.index());
			assertNotNull(n, "module " + m.index() + " kept");
			assertEquals(m.projectKey(), n.projectKey());
			assertEquals(m.desks(), n.desks(), "desks kept in module " + m.index());
		}
		List<Box> doors = TowerGenerator.t1Openings();
		int changed = 0;
		for (Placement p : a.plan().placements()) {
			String g = a.plan().groupAt(p.rel());
			Placement q = b.plan().at(p.rel());
			boolean door = doors.stream().anyMatch(d -> d.contains(p.rel()));
			if (g.startsWith("parcel_") || door) {
				continue;
			}
			assertNotNull(q, "cell " + p.rel() + " of " + g + " vanished");
			assertEquals(shape(p.state()), shape(q.state()), "cell " + p.rel() + " of " + g + " changed");
			assertEquals(g, b.plan().groupAt(p.rel()), "cell " + p.rel() + " changed group");
			changed++;
		}
		assertTrue(changed > 10000);
		for (Workstation w : a.plan().workstations()) {
			assertEquals(w, b.plan().workstation(w.threadId()), "workstation of " + w.threadId());
		}
	}

	/** A state without its connection sides (a pane next to a new podium hall connects to it; nothing else may change). */
	private static BlockState shape(BlockState s) {
		for (var prop : List.of(BlockStateProperties.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.EAST, BlockStateProperties.WEST)) {
			if (s.hasProperty(prop)) {
				s = s.setValue(prop, false);
			}
		}
		return s;
	}

	@Test
	void aProjectThatLeavesFreesItsHalfFloorForTheNextOne() {
		HqInput in = TowerLayout.sampleRoster(6);
		HqGenerator.Result r = plan(in, null);
		HqLayout.Module gone = r.layout().modulesOf("acmesite:website").get(0);
		int index = gone.index();
		HqInput without = in;
		for (String t : in.project("acmesite:website").threads()) {
			without = HqTestSupport.minus(without, t);
		}
		HqGenerator.Result freed = plan(without, r.layout());
		assertTrue(freed.layout().modules().stream().noneMatch(m -> m.index() == index));
		assertTrue(freed.plan().groups().contains("parcel_" + index), "a vacant half floor: hanging garden");
		assertFalse(freed.plan().groups().contains(HqGenerator.group(index)));
		assertStable(freed, plan(without, freed.layout()));
		HqInput late = HqTestSupport.plus(without, "vps:late", "vps:late-t0");
		HqGenerator.Result reused = plan(late, freed.layout());
		assertEquals(index, reused.layout().modulesOf("vps:late").get(0).index(), "the freed parcel is reused first");
		assertNotNull(reused.plan().workstation("vps:late-t0"));
		HqAnchorPlacementTest.assertAnchorsValid(reused.plan(), "reused");
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 4, 10, 20})
	void everyStandingAnchorIsFreeStandableAndReachableOnFoot(int projects) {
		BuildPlan p = plan(TowerLayout.sampleRoster(projects), null).plan();
		HqAnchorPlacementTest.assertAnchorsValid(p, projects + " projects");
	}

	@Test
	void mixedHistoryKeepsAnchorsValid() {
		HqLayout layout = null;
		HqInput in = TowerLayout.sampleRoster(3);
		for (int step = 0; step < 6; step++) {
			HqGenerator.Result r = plan(in, layout);
			layout = r.layout();
			HqAnchorPlacementTest.assertAnchorsValid(r.plan(), "step " + step);
			in = TowerLayout.sampleRoster(3 + 4 * (step + 1));
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {4, 10, 20})
	void everyGroupStaysInsideItsFootprint(int projects) {
		HqGenerator.Result r = plan(TowerLayout.sampleRoster(projects), null);
		BuildPlan p = r.plan();
		int towers = TowerLayout.towers(r.layout()).size();
		HqFootprints.Area env = TowerLayout.envelope(towers);
		int top = 0;
		for (Placement pl : p.placements()) {
			BlockPos c = pl.rel();
			String g = p.groupAt(c);
			assertTrue(env.contains(c.getX(), c.getZ()), g + " outside the envelope at " + c);
			assertTrue(c.getY() >= 0 && c.getY() <= TowerLayout.TOP, "height " + c);
			top = Math.max(top, c.getY());
			assertTrue(area(g).contains(c.getX(), c.getZ()), g + " outside its area at " + c);
			BlockState s = pl.state();
			if (s.getBlock() instanceof LeavesBlock) {
				assertTrue(s.getValue(BlockStateProperties.PERSISTENT), "leaves must be persistent at " + c);
			}
		}
		assertEquals(TowerLayout.TOP, top, "the documented maximum y");
	}

	private static HqFootprints.Area area(String group) {
		if (group.startsWith("module_") || group.startsWith("parcel_")) {
			TowerLayout.Parcel parcel = TowerLayout.parcel(Integer.parseInt(group.substring(group.indexOf('_') + 1)));
			return parcel.tower() == TowerLayout.T1 ? TowerLayout.base() : TowerLayout.towerArea(parcel.tower());
		}
		if (group.equals("tower_2") || group.equals("podium_east") || group.startsWith("bridge_e")) {
			return TowerLayout.towerArea(TowerLayout.T2);
		}
		if (group.equals("tower_3") || group.equals("podium_west") || group.startsWith("bridge_w")) {
			return TowerLayout.towerArea(TowerLayout.T3);
		}
		return TowerLayout.base();
	}

	@Test
	void desksHaveOne3x2ScreenAndNoLampsAgentsStandFacingIt() {
		BuildPlan p = plan(TowerLayout.sampleRoster(20), null).plan();
		BlockState on = vertical().computer(true);
		Set<Direction3> facings = new HashSet<>();
		int desks = 0;
		for (Workstation w : p.workstations()) {
			desks++;
			Anchor stand = w.deskAnchor();
			facings.add(new Direction3(stand.facing()));
			assertEquals(Workstation.SCREEN_WIDTH * Workstation.SCREEN_HEIGHT, w.screenCells().size());
			for (BlockPos cell : w.screenCells()) {
				assertEquals(on, p.at(cell).state(), "screen cell " + cell);
				assertFalse(cell.equals(stand.block()) || cell.equals(stand.block().above()));
			}
			assertEquals(stand.block().relative(stand.facing()).above(), w.computerPos(), "screen right in front, eye level");
			assertEquals(stand.facing().getOpposite(), w.screenFacing());
			assertTrue(p.at(stand.block()).state().isAir() && p.at(stand.block().above()).state().isAir(), "no chair");
			assertFalse(p.at(stand.block().below()).state().isAir(), "floor below");
		}
		assertEquals(TowerLayout.sampleRoster(20).threadCount(), desks, "every active thread has a desk");
		assertTrue(facings.size() >= 3, "desks face north, south and the facades: " + facings);
		// the only bulbs are the board's
		BlockState lamp = vertical().statusLamp();
		long lamps = p.placements().stream().filter(pl -> pl.state().getBlock() == lamp.getBlock()).count();
		long boardLamps = p.anchors().keySet().stream().filter(n -> n.startsWith(AnchorNames.BOARD_LAMP)).count();
		assertEquals(boardLamps, lamps);
	}

	private record Direction3(net.minecraft.core.Direction d) {
	}

	@Test
	void hubKeepsItsFixturesAndTheHologramColumn() {
		BuildPlan p = plan(TowerLayout.sampleRoster(10), null).plan();
		for (String n : List.of(AnchorNames.BOARD, AnchorNames.HOLOGRAM, AnchorNames.BEACON, AnchorNames.THREAD_WALL, AnchorNames.THREAD_WALL_END,
				AnchorNames.ARCHIVE_TERMINAL, AnchorNames.ARCHIVE, AnchorNames.SPAWN, AnchorNames.ENTRANCE, "walk_review_0", "walk_schedule_0",
				"walk_lounge_0")) {
			assertNotNull(p.anchor(n), n);
		}
		int beaconY = p.anchor(AnchorNames.BEACON).block().getY();
		for (int y = 1; y < beaconY; y++) {
			Placement c = p.at(new BlockPos(0, y, 0));
			assertTrue(c == null || c.state().isAir(), "hologram column at y=" + y + ": " + (c == null ? "" : c.state()));
		}
		assertTrue(p.anchors().keySet().stream().anyMatch(n -> n.startsWith(AnchorNames.BIG_SCREEN + "hub")));
		assertTrue(p.anchors().keySet().stream().anyMatch(n -> n.startsWith(AnchorNames.BIG_SCREEN + "schedule")));
		assertEquals(TowerLayout.T1.floorY(1) + 1, p.anchor(AnchorNames.THREAD_WALL).block().getY(), "kanban wall on the first-floor balcony");
	}

	@Test
	void vanillaOnlyWithoutTheBlocksModule() {
		Set<BlockState> states = new HashSet<>();
		for (Placement pl : plan(TowerLayout.sampleRoster(20), null).plan().placements()) {
			states.add(pl.state());
		}
		for (BlockState st : states) {
			String key = LevelBlockWorld.keyOf(st);
			assertTrue(key.startsWith("minecraft:"), key);
			assertSame(st, LevelBlockWorld.parse(BuiltInRegistries.BLOCK, key), key);
		}
	}
}
