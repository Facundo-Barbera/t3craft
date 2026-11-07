package dev.t3craft.hq;

import static dev.t3craft.hq.HqTestSupport.input;
import static dev.t3craft.hq.HqTestSupport.minus;
import static dev.t3craft.hq.HqTestSupport.plus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The generator: determinism, fixed footprint, no collisions, anchors, modular annexes. */
class HqGeneratorTest {
	private static BlockState AIR;

	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
		AIR = Blocks.AIR.defaultBlockState();
	}

	private static BuildPlan plan(HqInput in) {
		return HqGenerator.plan(in, null).plan();
	}

	// ------------------------------------------------------------------ determinism

	@Test
	void samePlanTwice() {
		BuildPlan a = plan(input(3, 5));
		BuildPlan b = plan(input(3, 5));
		assertEquals(a.placements(), b.placements());
		assertEquals(a.anchors(), b.anchors());
		assertEquals(a.fingerprint(), b.fingerprint());
	}

	@Test
	void inputOrderDoesNotMatter() {
		HqInput in = input(5, 3);
		List<HqInput.Project> shuffled = new ArrayList<>(in.projects());
		Collections.shuffle(shuffled, new Random(7));
		assertEquals(plan(in).fingerprint(), plan(new HqInput(shuffled)).fingerprint());
	}

	@Test
	void differentInputDifferentPlan() {
		assertNotEquals(plan(input(3, 5)).fingerprint(), plan(input(3, 6)).fingerprint());
	}

	@Test
	void placementOrderPutsStructureBeforeDecorWithinEachModule() {
		BuildPlan p = plan(input(2, 3));
		// every hanging lantern comes after the block it hangs from
		Map<BlockPos, Integer> index = new java.util.HashMap<>();
		for (int i = 0; i < p.size(); i++) {
			index.put(p.placements().get(i).rel(), i);
		}
		int checked = 0;
		for (Placement pl : p.placements()) {
			if (pl.state().is(Blocks.LANTERN) || pl.state().getBlock().getDescriptionId().contains("lantern")) {
				if (pl.state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING)) {
					Integer above = index.get(pl.rel().above());
					assertNotNull(above, "hanging lantern without support in plan at " + pl.rel());
					assertTrue(above < index.get(pl.rel()), "support placed after lantern at " + pl.rel());
					assertFalse(p.at(pl.rel().above()).state().isAir(), "lantern hangs from air at " + pl.rel());
					checked++;
				}
			}
		}
		assertTrue(checked > 10, "found hanging lanterns: " + checked);
	}

	// ------------------------------------------------------------------ footprint

	@Test
	void emptyHqIsHubAndPortico() {
		BuildPlan p = plan(HqInput.EMPTY);
		assertEquals(new Box(-8, 0, -8, 8, 10, 13), p.footprint());
		assertEquals(List.of("hub", "portico"), p.modules().stream().map(BuildPlan.Module::id).toList());
		assertEquals(17 * 11 * 17 + 9 * 11 * 5, p.size(), "every cell of the volume is listed, air included");
		assertTrue(p.at(new BlockPos(0, 2, -8)).state().is(Blocks.GLASS_PANE), "no spine: the hub's north wall is a window");
	}

	@ParameterizedTest(name = "{0} projects x {1} threads")
	@CsvSource({
		// projects, threads, minX, maxX, minZ  (maxZ = 13: portico; y = 0..10)
		"1, 1,  -8, 17, -23",
		"2, 4, -17, 17, -23",
		"3, 4, -17, 17, -37",
		"4, 7, -21, 21, -37",
		"5, 0, -17, 17, -51"})
	void footprintFollowsTheFixedGrid(int projects, int threads, int minX, int maxX, int minZ) {
		assertEquals(new Box(minX, 0, minZ, maxX, 10, 13), plan(input(projects, threads)).footprint());
	}

	@Test
	void moduleSizesAreFixed() {
		BuildPlan p = plan(input(3, 7));
		for (BuildPlan.Module m : p.modules()) {
			Box b = m.box();
			String id = m.id();
			int[] size = {b.sizeX(), b.sizeY(), b.sizeZ()};
			int[] want;
			if (id.equals("hub")) {
				want = new int[] {17, 11, 17};
			} else if (id.equals("portico")) {
				want = new int[] {9, 11, 5};
			} else if (id.equals("spine_cap")) {
				want = new int[] {7, 11, 1};
			} else if (id.startsWith("spine_")) {
				want = new int[] {7, 11, 14};
			} else if (id.contains("_desk_")) {
				want = new int[] {3, 5, 2};
			} else if (id.contains("_seg_")) {
				want = new int[] {4, 11, 14};
			} else {
				want = new int[] {1, 11, 14}; // wing inner wall / cap
			}
			assertEquals(List.of(want[0], want[1], want[2]), List.of(size[0], size[1], size[2]), id);
		}
	}

	// ------------------------------------------------------------------ collisions

	@ParameterizedTest(name = "{0} projects x {1} threads")
	@CsvSource({"0,0", "1,1", "3,4", "6,9", "13,2", "20,30"})
	void noCollisionsAndEveryOwnedCellPlacedOnce(int projects, int threads) {
		BuildPlan p = plan(input(projects, threads));
		Set<BlockPos> seen = new HashSet<>();
		for (Placement pl : p.placements()) {
			assertTrue(seen.add(pl.rel()), "two placements at " + pl.rel());
			assertTrue(p.footprint().contains(pl.rel()), "outside the footprint: " + pl.rel());
			int owners = 0;
			for (BuildPlan.Module m : p.modules()) {
				if (m.owns(pl.rel().getX(), pl.rel().getY(), pl.rel().getZ())) {
					owners++;
				}
			}
			assertEquals(1, owners, "cell " + pl.rel() + " owned by " + owners + " modules");
		}
		long owned = 0;
		for (BuildPlan.Module m : p.modules()) {
			long holes = 0;
			for (Box h : m.holes()) {
				holes += h.volume();
				assertTrue(p.modules().stream().anyMatch(o -> o.box().equals(h)), m.id() + ": hole " + h + " not filled by a module");
			}
			owned += m.box().volume() - holes;
		}
		assertEquals(owned, p.size(), "placements = owned cells");
		for (int i = 0; i < p.modules().size(); i++) {
			for (int j = i + 1; j < p.modules().size(); j++) {
				BuildPlan.Module a = p.modules().get(i);
				BuildPlan.Module b = p.modules().get(j);
				if (a.box().intersects(b.box())) {
					assertTrue(a.holes().contains(b.box()), a.id() + " overlaps " + b.id() + " outside its holes");
				}
			}
		}
	}

	@Test
	void builderRefusesCollisions() {
		PlanBuilder b = new PlanBuilder();
		try (PlanBuilder.Canvas c = b.module("a", new Box(0, 0, 0, 3, 3, 3), List.of(new Box(1, 1, 1, 1, 1, 1)))) {
			assertThrows(IllegalStateException.class, () -> c.set(1, 1, 1, Blocks.STONE.defaultBlockState()), "writes into its own hole");
			assertThrows(IllegalStateException.class, () -> c.set(4, 0, 0, Blocks.STONE.defaultBlockState()), "writes outside its box");
		}
		assertThrows(IllegalStateException.class, () -> b.module("b", new Box(3, 3, 3, 5, 5, 5), List.of()), "overlaps outside a hole");
		b.module("c", new Box(1, 1, 1, 1, 1, 1), List.of()).close();
	}

	// ------------------------------------------------------------------ anchors

	@Test
	void fixedAnchors() {
		BuildPlan p = plan(input(2, 3));
		Anchor spawn = p.anchor(AnchorNames.SPAWN);
		Anchor entrance = p.anchor(AnchorNames.ENTRANCE);
		Anchor board = p.anchor(AnchorNames.BOARD);
		assertEquals(new BlockPos(0, 1, 11), spawn.block());
		assertEquals(Direction.NORTH, spawn.facing());
		assertEquals(new BlockPos(0, 1, 8), entrance.block());
		assertEquals(new BlockPos(0, 3, -4), board.block());
		assertEquals(Direction.SOUTH, board.facing());
		assertEquals(-3.0, board.z());
		assertEquals(0.5, board.x());
		assertEquals(3.5, board.y());
		for (int i = 0; i < 4; i++) {
			Anchor l = p.anchor(AnchorNames.boardLamp(i));
			assertNotNull(l);
			assertTrue(p.at(l.block()).state().is(Blocks.COPPER_BULB.waxed().unaffected()));
		}
		assertNull(p.anchor(AnchorNames.boardLamp(4)));
		assertTrue(p.anchor(AnchorNames.boardLamp(0)).x() < p.anchor(AnchorNames.boardLamp(3)).x(), "left to right seen from the front");
		// standing room at spawn, entrance and in front of the board
		for (BlockPos c : List.of(spawn.block(), entrance.block(), new BlockPos(0, 1, -3))) {
			assertTrue(walkable(p, c), "blocked at " + c);
		}
		// signs carry text
		Placement sign = p.at(new BlockPos(0, 5, -3));
		assertTrue(sign.state().getBlock() instanceof WallSignBlock);
		assertNotNull(sign.be());
		assertTrue(sign.be().toString().contains("attention"), sign.be().toString());
	}

	@Test
	void everyThreadHasADeskMonitorAndLamp() {
		HqInput in = input(3, 5);
		BuildPlan p = plan(in);
		int desks = 0;
		for (HqInput.Project pr : in.projects()) {
			Anchor wing = p.anchor(AnchorNames.wing(pr.key()));
			assertNotNull(wing, pr.key());
			assertTrue(walkable(p, wing.block()), "wing doorway " + wing);
			for (String t : pr.threads()) {
				Anchor seat = p.anchor(AnchorNames.desk(t));
				Anchor mon = p.anchor(AnchorNames.monitor(t));
				Anchor lamp = p.anchor(AnchorNames.lamp(t));
				assertNotNull(seat, t);
				assertNotNull(mon, t);
				assertNotNull(lamp, t);
				assertTrue(p.at(seat.block()).state().getBlock() instanceof StairBlock, "seat is a chair");
				assertEquals(seat.facing().getOpposite(), p.at(seat.block()).state().getValue(StairBlock.FACING), "backrest away from the desk");
				assertTrue(p.at(seat.block().above()).state().isAir(), "headroom over the seat");
				assertEquals(seat.block().relative(seat.facing()).above(), mon.block(), "monitor in front of the seat at eye level");
				assertTrue(p.at(mon.block()).state().is(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK)));
				assertEquals(mon.facing(), seat.facing().getOpposite(), "monitor faces the seat");
				assertTrue(p.at(lamp.block()).state().is(Blocks.COPPER_BULB.waxed().unaffected()));
				desks++;
			}
		}
		assertEquals(in.threadCount(), desks);
		long deskAnchors = p.anchors().keySet().stream().filter(n -> n.startsWith("desk_")).count();
		assertEquals(in.threadCount(), deskAnchors);
	}

	@Test
	void anchorsAreInsideTheFootprint() {
		BuildPlan p = plan(input(7, 9));
		for (Anchor a : p.anchors().values()) {
			assertTrue(p.footprint().contains(a.block()), a.toString());
		}
	}

	// ------------------------------------------------------------------ annexes

	@Test
	void newThreadWithAFreeDeskOnlyChangesThatDesk() {
		HqInput in = input(3, 4);
		HqGenerator.Result r1 = HqGenerator.plan(in, null);
		HqGenerator.Result r2 = HqGenerator.plan(plus(in, "env:p1", "env:p1-new"), r1.layout());
		assertEquals(r1.plan().footprint(), r2.plan().footprint());
		List<Placement> d = BuildPlan.diff(r1.plan(), r2.plan(), rel -> AIR);
		assertFalse(d.isEmpty());
		Box desk = HqGeometry.desk(1, 4);
		for (Placement pl : d) {
			assertTrue(desk.contains(pl.rel()), "changed outside the new desk: " + pl.rel());
		}
		assertKeptAnchors(r1.plan(), r2.plan());
	}

	@Test
	void fullWingGetsASegmentAnnex() {
		HqInput in = input(2, 6);
		HqGenerator.Result r1 = HqGenerator.plan(in, null);
		HqGenerator.Result r2 = HqGenerator.plan(plus(in, "env:p0", "env:p0-x"), r1.layout());
		assertEquals(4, r2.layout().wing("env:p0").segments());
		assertEquals(r1.plan().footprint().maxX() + HqGeometry.SEGMENT, r2.plan().footprint().maxX());
		Box annex = HqGeometry.wingBox(0, HqGeometry.segmentU(3), HqGeometry.segmentU(4), 0, HqGeometry.ROW - 1, 0, HqGeometry.TOP);
		for (Placement pl : BuildPlan.diff(r1.plan(), r2.plan(), rel -> AIR)) {
			assertTrue(annex.contains(pl.rel()), "changed outside the annex: " + pl.rel());
		}
		assertKeptAnchors(r1.plan(), r2.plan());
	}

	@Test
	void goneThreadLeavesAVacantDesk() {
		HqInput in = input(2, 4);
		HqGenerator.Result r1 = HqGenerator.plan(in, null);
		HqGenerator.Result r2 = HqGenerator.plan(minus(in, "env:p0-t1"), r1.layout());
		assertNull(r2.plan().anchor(AnchorNames.desk("env:p0-t1")));
		assertEquals(r1.plan().footprint(), r2.plan().footprint());
		Box desk = HqGeometry.desk(0, 1);
		List<Placement> d = BuildPlan.diff(r1.plan(), r2.plan(), rel -> AIR);
		assertEquals(1, d.size(), "only the monitor turns off: " + d);
		assertTrue(desk.contains(d.get(0).rel()));
		assertTrue(d.get(0).state().is(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.GRAY)));
		assertKeptAnchors(r2.plan(), r1.plan());
	}

	@Test
	void newProjectAddsAWingWithoutTouchingTheOthers() {
		HqInput in = input(2, 3);
		HqGenerator.Result r1 = HqGenerator.plan(in, null);
		HqGenerator.Result r2 = HqGenerator.plan(plus(in, "env:new", "env:new-t0"), r1.layout());
		assertEquals(2, r2.layout().wing("env:new").index());
		List<Box> untouched = new ArrayList<>();
		for (BuildPlan.Module m : r1.plan().modules()) {
			if (m.id().startsWith("wing_") || m.id().equals("hub") || m.id().equals("portico") || m.id().equals("spine_0")) {
				untouched.add(m.box());
			}
		}
		List<Placement> d = BuildPlan.diff(r1.plan(), r2.plan(), rel -> AIR);
		assertFalse(d.isEmpty());
		for (Placement pl : d) {
			for (Box b : untouched) {
				assertFalse(b.contains(pl.rel()), "touched an existing module at " + pl.rel());
			}
		}
		assertKeptAnchors(r1.plan(), r2.plan());
	}

	@Test
	void removedProjectFreesItsWingCells() {
		HqInput in = input(3, 2);
		HqGenerator.Result r1 = HqGenerator.plan(in, null);
		HqInput without = new HqInput(List.of(in.projects().get(0), in.projects().get(1)));
		HqGenerator.Result r2 = HqGenerator.plan(without, r1.layout());
		Box gone = HqGeometry.wingInner(2).union(HqGeometry.wingCap(2, 3));
		List<Placement> d = BuildPlan.diff(r1.plan(), r2.plan(), rel -> AIR);
		assertTrue(d.stream().anyMatch(pl -> gone.contains(pl.rel()) && pl.state().isAir()));
		for (Placement pl : r2.plan().placements()) {
			assertFalse(gone.contains(pl.rel()), "the removed wing is still planned at " + pl.rel());
		}
	}

	@Test
	void diffFromNothingIsTheWholePlan() {
		BuildPlan p = plan(input(1, 1));
		assertEquals(p.placements(), BuildPlan.diff(null, p, rel -> AIR));
		assertTrue(BuildPlan.diff(p, p, rel -> AIR).isEmpty());
	}

	// ------------------------------------------------------------------ helpers

	/** Every anchor of {@code a} is in {@code b} unchanged (b may have more). */
	private static void assertKeptAnchors(BuildPlan a, BuildPlan b) {
		for (Anchor an : a.anchors().values()) {
			assertEquals(an, b.anchor(an.name()), "anchor moved: " + an.name());
		}
	}

	/** Feet and head cells free (air or a carpet), floor below solid. */
	private static boolean walkable(BuildPlan p, BlockPos feet) {
		BlockState f = p.at(feet).state();
		BlockState h = p.at(feet.above()).state();
		BlockState below = p.at(feet.below()).state();
		boolean feetFree = f.isAir() || f.getBlock().getDescriptionId().contains("carpet");
		return feetFree && h.isAir() && !below.isAir();
	}
}
