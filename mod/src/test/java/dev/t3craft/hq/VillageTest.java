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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** layout/village: town plan, stable allocation and the generated plan (anchors, footprints, screens). */
class VillageTest {
	private static final Map<Integer, VillageGenerator.Result> GROWN = new HashMap<>();

	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
	}

	/** The village grown 4 → 10 → 20 projects, each step allocated against the previous layout. */
	private static synchronized VillageGenerator.Result grown(int n) {
		if (GROWN.isEmpty()) {
			VillageLayout prev = null;
			for (int k : new int[] {4, 10, 20}) {
				VillageGenerator.Result r = VillageGenerator.plan(VillageDemo.roster(k), prev);
				GROWN.put(k, r);
				prev = r.layout();
			}
		}
		return GROWN.get(n);
	}

	// ------------------------------------------------------------------ town plan

	@Test
	void townIsANetworkOnTerracesNotASpine() {
		VillageTown t = VillageTown.get();
		assertTrue(t.plots.size() >= 40, "plots " + t.plots.size());
		int links = 0;
		Set<Integer> levels = new HashSet<>();
		for (VillageTown.Street s : t.streetList) {
			links += s.links.size();
			levels.add(s.level);
			for (VillageTown.Street o : s.links) {
				assertTrue(Math.abs(o.level - s.level) <= 1, "linked streets one terrace apart at most: " + s + " " + o);
				assertEquals(1, Math.abs(o.i - s.i) + Math.abs(o.j - s.j), "links join neighbours");
			}
		}
		int edges = links / 2;
		int cycles = edges - t.streetList.size() + 4; // a forest of 4 trees has nodes - 4 edges
		assertTrue(cycles >= 3, "the street network closes loops (cycles " + cycles + ")");
		assertTrue(levels.size() >= 3, "terraces " + levels);
		// plots face a street on their own terrace
		for (VillageTown.Plot p : t.plots) {
			VillageTown.Street s = t.street(p.i() + p.front().getStepX(), p.j() + p.front().getStepZ());
			assertNotNull(s);
			assertEquals(s.level, p.level());
		}
	}

	@Test
	void consecutivePlotsGrowInSeveralDirections() {
		VillageTown t = VillageTown.get();
		Set<Integer> districts = new HashSet<>();
		for (int r = 0; r < 4; r++) {
			VillageTown.Plot p = t.plots.get(r);
			districts.add(VillageTown.district(p.i(), p.j()));
		}
		assertTrue(districts.size() >= 3, "the first four plots spread over the compass " + districts);
		for (int r = 1; r < t.plots.size(); r++) {
			assertTrue(t.plots.get(r - 1).street().round <= t.plots.get(r).street().round, "inner rings first");
		}
	}

	// ------------------------------------------------------------------ allocation

	@Test
	void allocationIsDeterministicAndIdempotent() {
		VillageLayout.Input in = VillageDemo.roster(10);
		VillageLayout a = VillageLayout.plan(in);
		assertEquals(a, VillageLayout.plan(in));
		assertEquals(a, VillageLayout.allocate(in, a));
		BuildPlan p1 = new VillageGenerator().generate(a);
		BuildPlan p2 = new VillageGenerator().generate(VillageLayout.allocate(in, a));
		assertEquals(p1.fingerprint(), p2.fingerprint());
		// roster order does not matter
		List<HqInput.Entry> lines = new ArrayList<>(HqTestSupport.roster(in.roster()));
		java.util.Collections.reverse(lines);
		assertEquals(a, VillageLayout.plan(new VillageLayout.Input(HqInput.fromRoster(lines), in.rooms())));
	}

	@Test
	void existingBuildingsNeverMoveWhenTheTownGrows() {
		VillageGenerator.Result small = grown(4);
		VillageGenerator.Result mid = grown(10);
		VillageGenerator.Result big = grown(20);
		for (VillageLayout.Hamlet h : small.layout().hamlets()) {
			for (VillageGenerator.Result later : List.of(mid, big)) {
				VillageLayout.Hamlet k = later.layout().hamletAt(h.plot());
				assertNotNull(k, "hamlet of " + h.projectKey() + " kept");
				assertEquals(h, k, "same plot, same desks");
				String g = VillageGenerator.plotGroup(h.plot());
				assertEquals(small.plan().placements(g), later.plan().placements(g), "plot " + h.plot() + " unchanged, block for block");
			}
		}
		for (VillageLayout.Hamlet h : mid.layout().hamlets()) {
			assertEquals(h, big.layout().hamletAt(h.plot()));
		}
		// the singular buildings and the town hall stay where they are
		for (String g : List.of("hub", "archive", "lounge", "schedule", "attention")) {
			assertEquals(small.plan().modules().stream().filter(m -> m.group().equals(g)).map(BuildPlan.Module::box).toList(),
					big.plan().modules().stream().filter(m -> m.group().equals(g)).map(BuildPlan.Module::box).toList(), g);
		}
		// workstations of existing threads keep their place
		for (Workstation w : small.plan().workstations()) {
			assertEquals(w, big.plan().workstation(w.threadId()), w.threadId());
		}
	}

	@Test
	void aProjectThatLeavesFreesItsPlotAndTheNextNewcomerFillsItFirst() {
		VillageLayout ten = grown(10).layout();
		VillageLayout.Input in = VillageDemo.roster(10);
		String gone = ten.hamlets().get(1).projectKey();
		int freed = ten.hamlets().get(1).plot();
		List<HqInput.Entry> lines = new ArrayList<>();
		for (HqInput.Entry e : HqTestSupport.roster(in.roster())) {
			if (!e.projectKey().equals(gone)) {
				lines.add(e);
			}
		}
		VillageLayout without = VillageLayout.allocate(new VillageLayout.Input(HqInput.fromRoster(lines), in.rooms()), ten);
		assertNull(without.hamletAt(freed), "plot " + freed + " freed");
		for (VillageLayout.Hamlet h : ten.hamlets()) {
			if (!h.projectKey().equals(gone)) {
				assertEquals(h, without.hamletAt(h.plot()), "the others stay");
			}
		}
		lines.add(new HqInput.Entry("vps", "VPS", "newcomer", "newcomer", "vps:newcomer-t0", false));
		VillageLayout refilled = VillageLayout.allocate(new VillageLayout.Input(HqInput.fromRoster(lines), in.rooms()), without);
		assertEquals("vps:newcomer", refilled.hamletAt(freed).projectKey(), "the interior gap is refilled before growing outwards");
	}

	@Test
	void hamletsGrowAnnexesAndSecondHamlets() {
		VillageLayout l = grown(20).layout();
		boolean annexB = false;
		boolean annexC = false;
		Set<String> projects = new HashSet<>();
		boolean twice = false;
		for (VillageLayout.Hamlet h : l.hamlets()) {
			annexB |= h.annexB();
			annexC |= h.annexC();
			twice |= !projects.add(h.projectKey());
			assertTrue(h.threads() <= VillageLayout.DESKS);
		}
		assertTrue(annexB && annexC, "annexes");
		assertTrue(twice, "a project with more than " + VillageLayout.DESKS + " threads takes a second plot");
		for (VillageLayout.Room r : VillageLayout.Room.values()) {
			if (r != VillageLayout.Room.WORK) {
				assertFalse(l.room(r).isEmpty(), r + " has threads");
				assertTrue(l.room(r).size() <= r.capacity());
			}
		}
	}

	// ------------------------------------------------------------------ plan

	@ParameterizedTest(name = "{0} projects")
	@ValueSource(ints = {4, 10, 20})
	void everyStandingAnchorIsFreeStandableAndReachable(int n) {
		HqAnchorPlacementTest.assertAnchorsValid(grown(n).plan(), n + " projects");
	}

	@ParameterizedTest(name = "{0} projects")
	@ValueSource(ints = {4, 10, 20})
	void everythingStaysInsideItsCell(int n) {
		VillageGenerator.Result r = grown(n);
		Map<String, HqFootprints.Area> areas = VillageFootprints.areas(r.layout());
		assertEquals(Set.copyOf(areas.keySet()), Set.copyOf(r.plan().groups()), "one group per built cell");
		for (BuildPlan.Module m : r.plan().modules()) {
			HqFootprints.Area a = areas.get(m.group());
			Box bx = m.box();
			assertTrue(a.contains(bx.minX(), bx.minZ()) && a.contains(bx.maxX(), bx.maxZ()), m.id() + " " + bx + " in " + a);
			assertTrue(bx.minY() >= VillageFootprints.bottom() && bx.maxY() <= VillageFootprints.top(), m.id());
		}
		HqFootprints.Area env = VillageFootprints.envelope(r.layout().plots().stream().mapToInt(Integer::intValue).max().orElse(0) + 1);
		for (Placement p : r.plan().placements()) {
			assertTrue(env.contains(p.rel().getX(), p.rel().getZ()), "placement " + p.rel() + " outside the envelope");
		}
		// the areas never overlap
		List<HqFootprints.Area> list = new ArrayList<>(areas.values());
		for (int i = 0; i < list.size(); i++) {
			for (int j = i + 1; j < list.size(); j++) {
				assertFalse(list.get(i).intersects(list.get(j)));
			}
		}
	}

	@ParameterizedTest(name = "{0} projects")
	@ValueSource(ints = {4, 10, 20})
	void desksHaveOneThreeByTwoScreenAndNoLamps(int n) {
		BuildPlan p = grown(n).plan();
		assertFalse(p.workstations().isEmpty());
		Set<BlockPos> boardLamps = new HashSet<>();
		p.anchors().forEach((k, a) -> {
			assertFalse(k.startsWith("lamp_"), "no desk lamps: " + k);
			if (k.startsWith(AnchorNames.BOARD_LAMP)) {
				boardLamps.add(a.block());
			}
		});
		for (Workstation w : p.workstations()) {
			List<BlockPos> cells = w.screenCells();
			assertEquals(Workstation.SCREEN_WIDTH * Workstation.SCREEN_HEIGHT, cells.size());
			for (BlockPos c : cells) {
				assertTrue(p.at(c).state().is(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK)), "screen cell " + c);
			}
			Anchor stand = w.deskAnchor();
			assertEquals(stand.block().relative(stand.facing()).above(), w.computerPos(), "screen right in front of the NPC");
			assertEquals(stand.facing().getOpposite(), w.screenFacing());
			assertTrue(p.at(stand.block()).state().isAir() && p.at(stand.block().above()).state().isAir(), "no chair");
			for (BlockPos q : w.screenCells()) {
				assertNotEquals(q, stand.block());
				assertNotEquals(q, stand.block().above());
			}
		}
		for (Placement pl : p.placements()) {
			if (pl.state().is(Blocks.COPPER_BULB.waxed().unaffected())) {
				assertTrue(boardLamps.contains(pl.rel()), "a bulb outside the board at " + pl.rel());
			}
		}
		assertNotNull(p.anchor(AnchorNames.ARCHIVE_TERMINAL));
		assertNotNull(p.anchor(AnchorNames.THREAD_WALL));
		assertNotNull(p.anchor(AnchorNames.BEACON));
		assertNotNull(p.anchor(AnchorNames.HOLOGRAM));
		for (int y = 1; y <= HqGeometry.HOLOGRAM_TOP; y++) {
			assertTrue(p.at(new BlockPos(0, VillageFootprints.hubFloor() + y, 0)).state().isAir(), "hologram column");
		}
	}

	@Test
	void everyActiveThreadHasAPlace() {
		VillageGenerator.Result r = grown(20);
		BuildPlan p = r.plan();
		for (HqInput.Project pr : VillageDemo.roster(20).roster().projects()) {
			for (String t : pr.threads()) {
				assertNotNull(p.anchor(AnchorNames.desk(t)), t);
			}
		}
	}
}
