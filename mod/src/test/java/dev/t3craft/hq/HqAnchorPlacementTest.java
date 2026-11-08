package dev.t3craft.hq;

import static dev.t3craft.hq.HqTestSupport.input;
import static dev.t3craft.hq.HqTestSupport.minus;
import static dev.t3craft.hq.HqTestSupport.plus;
import static dev.t3craft.hq.HqTestSupport.settle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Plan validator: every standing anchor (desks, board, doors, stroll points, benches, spawn, the
 * shelf spot) is in a free, standable cell where the NPC body fits exactly (no collision at the
 * feet or head, a floor right under the anchor) and that NPCs can reach from the spawn over free
 * cells, judged by the client navigation's own {@code Collision} (see {@link PlanWalkability}),
 * for many rosters, annexes and histories. NPCs that clipped into desks, walls and chairs in the
 * demo world came from anchors that broke this rule.
 */
class HqAnchorPlacementTest {
	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
	}

	/** Rosters: empty, single desks, full modules, annexes, many projects, settled threads. */
	static Stream<Arguments> rosters() {
		List<Arguments> out = new ArrayList<>();
		int[][] shapes = {{0, 0}, {1, 1}, {1, 6}, {1, 7}, {2, 3}, {3, 5}, {4, 13}, {5, 2}, {7, 9}, {9, 1}};
		for (int[] s : shapes) {
			out.add(Arguments.of(s[0] + "x" + s[1], input(s[0], s[1])));
		}
		HqInput mixed = input(3, 8);
		mixed = settle(mixed, "env:p0-t1");
		mixed = settle(mixed, "env:p2-t7");
		mixed = plus(mixed, "env:solo", "env:solo-t0");
		out.add(Arguments.of("mixed+settled", mixed));
		return out.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("rosters")
	void everyStandingAnchorIsFreeStandableAndReachable(String name, HqInput in) {
		assertAnchorsValid(HqGenerator.plan(in, null).plan(), name);
	}

	@Test
	void anchorsStayValidAcrossAHistoryOfAnnexesAndRemovals() {
		HqInput in = input(2, 4);
		HqLayout layout = null;
		List<HqInput> steps = new ArrayList<>();
		steps.add(in);
		for (int t = 4; t < 15; t++) {
			in = plus(in, "env:p1", "env:p1-t" + t);
			steps.add(in);
		}
		in = minus(in, "env:p1-t0");
		steps.add(in);
		in = settle(in, "env:p1-t5");
		steps.add(in);
		in = plus(in, "env:late", "env:late-t0");
		steps.add(in);
		for (int t = 1; t < 7; t++) {
			in = minus(in, "env:p1-t" + t);
			steps.add(in);
		}
		int i = 0;
		for (HqInput step : steps) {
			HqGenerator.Result r = HqGenerator.plan(step, layout);
			layout = r.layout();
			assertAnchorsValid(r.plan(), "step " + i++);
		}
	}

	@Test
	void standingAnchorsAreClassifiedByName() {
		BuildPlan p = HqGenerator.plan(input(2, 3), null).plan();
		for (String n : List.of(AnchorNames.SPAWN, AnchorNames.ENTRANCE, AnchorNames.HUB, AnchorNames.BOARD_FRONT, AnchorNames.ARCHIVE,
				AnchorNames.boardSlot(0), AnchorNames.bench(0), AnchorNames.walkHub(0), AnchorNames.desk("env:p0-t0"),
				AnchorNames.wing("env:p0"), AnchorNames.walk("env:p0"), AnchorNames.module(0), AnchorNames.walkModule(0),
				AnchorNames.deskSlot(0, 0))) {
			assertTrue(AnchorNames.standing(n), n);
		}
		for (String n : List.of(AnchorNames.BOARD, AnchorNames.boardLamp(0), AnchorNames.monitor("env:p0-t0"), AnchorNames.lamp("env:p0-t0"),
				AnchorNames.screenSlot(0, 0), AnchorNames.archive("env:p0-t0"), AnchorNames.archiveShelfSlot(0), AnchorNames.SHELF_MORE,
				AnchorNames.SHELF_HEADER, AnchorNames.BEACON)) {
			assertFalse(AnchorNames.standing(n), n);
		}
		// every anchor of a real plan is one or the other, and standing ones sit at a cell centre at foot level
		for (Anchor a : p.anchors().values()) {
			if (AnchorNames.standing(a.name())) {
				assertTrue(a.y() >= a.block().getY() && a.y() <= a.block().getY() + PlanBuilder.THIN_FLOOR, a.toString());
				assertEquals(a.block().getX() + 0.5, a.x(), 1e-9, a.toString());
				assertEquals(a.block().getZ() + 0.5, a.z(), 1e-9, a.toString());
			}
		}
	}

	@Test
	void npcsStandAtTheirDesksFacingTheComputer() {
		BuildPlan p = HqGenerator.plan(input(3, 6), null).plan();
		assertEquals(HqLayout.DESKS * 3, p.workstations().size());
		Set<Direction> seen = new java.util.HashSet<>();
		for (Workstation w : p.workstations()) {
			Anchor stand = w.deskAnchor();
			Direction f = stand.facing();
			seen.add(f);
			assertEquals(stand.block().relative(f).above(), w.computerPos(), "computer right in front of the NPC, at eye level");
			assertEquals(f.getOpposite(), w.screenFacing(), "the screen faces the NPC");
			assertEquals(f.toYRot(), stand.yaw(), 1e-6, "the NPC looks at the computer");
			// no chair: air at the feet and the head, the desk floor below
			assertTrue(p.at(stand.block()).state().isAir(), "feet " + p.at(stand.block()).state());
			assertTrue(p.at(stand.block().above()).state().isAir(), "head " + p.at(stand.block().above()).state());
			assertFalse(p.at(stand.block().below()).state().isAir(), "floor below");
			assertEquals(stand.block().getY(), stand.y(), 1e-9, "feet on the floor");
			// never the desk, the screen or a wall; the 3×2 screen keeps clear of the NPC's feet and head
			assertNotEquals(w.computerPos().below(), stand.block());
			for (net.minecraft.core.BlockPos cell : w.screenCells()) {
				assertNotEquals(cell, stand.block(), "screen cell at the feet");
				assertNotEquals(cell, stand.block().above(), "screen cell at the head");
			}
		}
		assertEquals(Set.of(Direction.NORTH, Direction.SOUTH), seen, "north and south desks");
	}

	@Test
	void deskAnchorsArePureFunctionsOfTheirPlace() {
		BuildPlan a = HqGenerator.plan(input(2, 3), null).plan();
		BuildPlan b = HqGenerator.plan(input(2, 3), null).plan();
		assertEquals(a.fingerprint(), b.fingerprint());
		assertEquals(a.workstations(), b.workstations());
		Map<String, Anchor> seats = new LinkedHashMap<>();
		for (Workstation w : HqGenerator.plan(input(2, 6), null).plan().workstations()) {
			seats.put(w.module() + "." + w.slot(), w.deskAnchor());
		}
		for (Workstation w : a.workstations()) {
			assertEquals(seats.get(w.module() + "." + w.slot()), w.deskAnchor(), "same place, same spot");
		}
		// idempotent: regenerating from the kept layout changes nothing
		HqGenerator.Result r = HqGenerator.plan(input(3, 7), null);
		HqGenerator.Result again = HqGenerator.plan(input(3, 7), r.layout());
		assertEquals(r.layout(), again.layout());
		assertEquals(r.plan().fingerprint(), again.plan().fingerprint());
		assertEquals(r.plan().anchors(), again.plan().anchors());
	}

	@Test
	void validatorRejectsTheCellsNpcsUsedToClipInto() {
		BuildPlan p = HqGenerator.plan(input(1, 2), null).plan();
		PlanWalkability walk = new PlanWalkability(p);
		Anchor bench = p.anchor(AnchorNames.bench(0));
		BlockPos benchStairs = bench.block().relative(bench.facing().getOpposite());
		assertTrue(Double.isNaN(walk.floor(benchStairs)), "standing on/in the bench stairs (the old bench anchor)");
		Workstation w = p.workstations().get(0);
		assertTrue(Double.isNaN(walk.floor(w.computerPos().below())), "the desk top");
		assertTrue(Double.isNaN(walk.floor(w.computerPos())), "the computer");
		Anchor runner = p.anchor(AnchorNames.walk("env:p0"));
		assertFalse(walk.fits(runner.x(), runner.block().getY(), runner.z()), "an anchor sunk into the runner carpet would overlap it");
	}

	// ------------------------------------------------------------------ validator

	static void assertAnchorsValid(BuildPlan p, String ctx) {
		PlanWalkability walk = new PlanWalkability(p);
		Anchor spawn = p.anchor(AnchorNames.SPAWN);
		assertNotNull(spawn, ctx);
		Set<BlockPos> reach = walk.reachable(spawn.block());
		assertFalse(reach.isEmpty(), ctx + ": spawn not standable");
		List<Anchor> standing = new ArrayList<>();
		for (Anchor a : p.anchors().values()) {
			if (AnchorNames.standing(a.name())) {
				standing.add(a);
			}
		}
		for (Workstation w : p.workstations()) {
			standing.add(w.deskAnchor());
		}
		assertTrue(standing.size() > 20, ctx + ": too few anchors checked");
		for (Anchor a : standing) {
			BlockPos c = a.block();
			Placement feet = p.at(c);
			assertNotNull(feet, ctx + ": anchor outside the plan " + a);
			double floor = walk.floor(c);
			assertFalse(Double.isNaN(floor), ctx + ": " + a + " not standable (feet " + feet.state() + ", head " + p.at(c.above()).state()
					+ ", below " + (p.at(c.below()) == null ? "nothing" : p.at(c.below()).state()) + ")");
			assertEquals(floor, a.y(), 1e-9, ctx + ": " + a + " floats or sinks (floor " + floor + ")");
			assertTrue(walk.fits(a.x(), a.y(), a.z()), ctx + ": the NPC body does not fit exactly at " + a);
			assertTrue(reach.contains(c), ctx + ": " + a + " not reachable from the spawn");
		}
	}
}
