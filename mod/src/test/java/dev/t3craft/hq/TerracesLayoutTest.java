package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.TerraceTown.Cell;
import dev.t3craft.hq.TerraceTown.Kind;
import dev.t3craft.hq.layout.HqLayouts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * design/hybrid-terraces: the "Acrópolis en terrazas" layout. Its plan (a network of terraces with
 * loops, no long branches, specials on distinct terraces, islands by bridges), its allocation
 * (deterministic, idempotent, stable while growing, interior solares refilled first) and its
 * plans with 4, 10 and 20 projects (anchors valid and reachable, everything inside the footprint,
 * 3×2 screens without lamps, block light ≥ 10 on every walkable cell, no loose lanterns, no
 * floating decoration).
 */
class TerracesLayoutTest {
	private static final String ID = "hybrid-terraces";

	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
	}

	private static HqStyle style() {
		return HqStyles.forLayout(ID);
	}

	private static HqGenerator.Result plan(HqInput in, HqLayout previous) {
		return HqGenerator.plan(in, previous, style());
	}

	// ------------------------------------------------------------------ registry

	@Test
	void registeredSelectableAndCherry() {
		assertEquals(ID, HqLayouts.find(ID).orElseThrow().id());
		assertEquals(ID, HqLayouts.all().get(HqLayouts.all().size() - 1).id(), "appended, the existing layouts keep their order");
		HqStyle s = style();
		assertEquals(ID, s.id());
		assertTrue(s.ownLayout());
		assertTrue(HqStyles.ids().contains(ID));
		assertFalse(HqStyles.all().contains(s), "all() keeps the shared-geometry styles only");
		assertEquals(new CherryStyle().palette(), s.palette());
	}

	// ------------------------------------------------------------------ the plan

	@Test
	void acropolisOnTopSpecialsOnDistinctTerracesIslandsByBridges() {
		TerraceTown town = TerraceTown.get();
		Cell plaza = town.cell(0, 0);
		assertEquals(Kind.PLAZA, plaza.kind);
		assertEquals(Kind.TEMPLE, town.cell(0, -1).kind, "the temple stands north of the plaza");
		for (Cell c : town.order) {
			assertTrue(c.level <= plaza.level, "the acropolis is the highest terrace: " + c);
		}
		Set<Integer> specialTerraces = new HashSet<>();
		for (Kind k : new Kind[] {Kind.LIBRARY, Kind.GUARD, Kind.TAVERN, Kind.LONJA, Kind.MILL_ISLAND}) {
			Cell c = town.order.stream().filter(x -> x.kind == k).findFirst().orElseThrow();
			assertTrue(specialTerraces.add(c.terrace), "each special building on its own terrace: " + k);
		}
		for (TerraceTown.Bridge b : town.bridges) {
			assertTrue(b.to().kind.island());
			assertEquals(b.from(), b.to().parent, "an island is reached by its bridge");
			assertEquals(b.over(), b.to().via);
			assertTrue(b.over().level <= b.from().level - 2, "the bridge passes over a lower garden terrace");
		}
	}

	@Test
	void terracesHoldTwoOrThreeBarriosAndEverySolarReachesThePlazaWithoutLongBranches() {
		TerraceTown town = TerraceTown.get();
		Map<Integer, Integer> sizes = new HashMap<>();
		for (Cell c : town.order) {
			if (c.kind == Kind.PLOT) {
				sizes.merge(c.terrace, 1, Integer::sum);
			}
		}
		long twoOrThree = sizes.values().stream().filter(n -> n == 2 || n == 3).count();
		assertTrue(sizes.values().stream().allMatch(n -> n <= 3), "terraces of at most three barrios");
		assertTrue(twoOrThree >= sizes.size() - 1, "terraces of two or three barrios: " + sizes);
		assertTrue(town.plots.size() >= 60, "room to grow: " + town.plots.size());
		for (Cell c : town.plots) {
			assertTrue(c.depth >= 0, "reachable: " + c);
			assertTrue(c.depth <= 3 * c.ring, "no long branch: " + c + " is " + c.depth + " links from the plaza");
			for (Cell p = c; p.parent != null; p = p.parent) {
				assertTrue(p.parent.ring <= p.ring + 1, "paths run inwards");
			}
		}
		// growth goes ring by ring: ranks never go back to an inner ring
		for (int r = 1; r < town.plots.size(); r++) {
			assertTrue(town.plots.get(r).ring >= town.plots.get(r - 1).ring, "rank order by ring");
		}
	}

	@Test
	void consecutiveSolaresOpenInSeveralDirections() {
		TerraceTown town = TerraceTown.get();
		Set<Integer> quadrants = new HashSet<>();
		for (int r = 0; r < 4; r++) {
			Cell c = town.plots.get(r);
			quadrants.add((int) Math.floor(TerraceTown.angle(c.i, c.j) * 4));
		}
		assertTrue(quadrants.size() >= 3, "the first four solares open in several directions: " + quadrants);
	}

	@ParameterizedTest
	@ValueSource(ints = {10, 20})
	void theNetworkHasLoops(int n) {
		HqLayout l = plan(HqLayoutsPlanTest.roster(n), null).layout();
		List<Cell> built = TerraceTown.get().builtCells(TerraceStyle.plots(l));
		Map<Long, Cell> by = TerraceTown.byKey(built);
		Map<Cell, Cell> root = new HashMap<>();
		built.forEach(c -> root.put(c, c));
		int edges = 0;
		for (Cell c : built) {
			for (Direction d : new Direction[] {Direction.EAST, Direction.SOUTH}) {
				Cell o = by.get(TerraceTown.key(c.i + d.getStepX(), c.j + d.getStepZ()));
				if (o != null && TerraceTown.walkable(c, o)) {
					edges++;
					union(root, c, o);
				}
			}
		}
		for (TerraceTown.Bridge b : TerraceTown.get().bridges) {
			if (by.containsKey(b.to().key())) {
				edges++;
				union(root, b.from(), b.to());
			}
		}
		Set<Cell> components = new HashSet<>();
		built.forEach(c -> components.add(find(root, c)));
		// cyclomatic number: independent loops of the walkable network
		int loops = edges - built.size() + components.size();
		assertTrue(loops >= (n >= 20 ? 7 : 5), n + " projects: independent loops " + loops + " (" + edges + " links, " + built.size() + " cells)");
		// one connected walkable network (plus at most the garden under the mill bridge, reached later)
		assertTrue(components.size() <= 2, n + " projects: components " + components.size());
	}

	private static Cell find(Map<Cell, Cell> root, Cell c) {
		while (root.get(c) != c) {
			c = root.get(c);
		}
		return c;
	}

	private static void union(Map<Cell, Cell> root, Cell a, Cell b) {
		root.put(find(root, a), find(root, b));
	}

	// ------------------------------------------------------------------ allocation

	@ParameterizedTest
	@ValueSource(ints = {0, 4, 10, 20})
	void deterministicIdempotentAndStableWhileGrowing(int n) {
		HqInput in = HqLayoutsPlanTest.roster(n);
		HqGenerator.Result a = plan(in, null);
		assertEquals(a.plan().fingerprint(), plan(in, null).plan().fingerprint());
		assertEquals(a.layout(), plan(in, a.layout()).layout());
		if (n > 0) {
			HqGenerator.Result smaller = plan(HqLayoutsPlanTest.roster(n / 2), null);
			HqGenerator.Result grown = plan(in, smaller.layout());
			for (HqLayout.Module m : smaller.layout().modules()) {
				HqLayout.Module same = grown.layout().moduleAt(m.index());
				assertNotNull(same, "a barrio never moves: " + m.index());
				assertEquals(m.projectKey(), same.projectKey());
				for (HqLayout.Desk d : m.desks()) {
					assertEquals(d.threadId(), same.threadAt(d.slot()), "a desk never moves");
				}
			}
		}
	}

	@Test
	void aFreedInteriorSolarIsRefilledFirst() {
		HqInput in = HqTestSupport.input(6, 2);
		HqLayout l = plan(in, null).layout();
		int freed = l.modulesOf("env:p2").get(0).index() / 4;
		HqInput without = in;
		for (String t : List.of("env:p2-t0", "env:p2-t1")) {
			without = HqTestSupport.minus(without, t);
		}
		HqLayout l2 = plan(without, l).layout();
		assertTrue(l2.modulesOf("env:p2").isEmpty());
		HqLayout l3 = plan(HqTestSupport.plus(without, "env:new", "env:new-t0"), l2).layout();
		assertEquals(freed, l3.modulesOf("env:new").get(0).index() / 4, "the newcomer takes the freed interior solar");
	}

	@Test
	void bigProjectsGrowAnnexesThenASecondBarrio() {
		HqInput in = HqTestSupport.input(1, TerraceStyle.BARRIO_DESKS + 3);
		HqLayout l = plan(in, null).layout();
		Set<Integer> parts = new HashSet<>();
		Set<Integer> plots = new HashSet<>();
		for (HqLayout.Module m : l.modules()) {
			parts.add(m.index() % 4);
			plots.add(m.index() / 4);
		}
		assertEquals(Set.of(0, 1, 2), parts, "hall, annex and tower");
		assertEquals(2, plots.size(), "a second barrio when the first is full");
		assertEquals(TerraceStyle.BARRIO_DESKS + 3, plan(in, null).plan().workstations().size());
	}

	// ------------------------------------------------------------------ plans

	@ParameterizedTest
	@ValueSource(ints = {4, 10, 20})
	void anchorsFootprintScreensAndLight(int n) {
		HqGenerator.Result r = plan(HqLayoutsPlanTest.roster(n), null);
		BuildPlan p = r.plan();
		String ctx = ID + " " + n + "p";
		HqAnchorPlacementTest.assertAnchorsValid(p, ctx);
		HqFootprints.Area env = style().envelope(r.layout());
		for (Placement pl : p.placements()) {
			assertTrue(env.contains(pl.rel().getX(), pl.rel().getZ()), ctx + ": outside the footprint " + pl.rel());
			assertTrue(pl.rel().getY() >= 0 && pl.rel().getY() <= TerraceTown.TOP_Y, ctx + ": height " + pl.rel());
		}
		for (Workstation w : p.workstations()) {
			assertEquals(6, w.screenCells().size());
			for (BlockPos c : w.screenCells()) {
				assertEquals(style().computer(true), p.at(c).state(), ctx + ": 3×2 screen " + c);
			}
			assertTrue(p.anchor(AnchorNames.lamp(w.threadId())) == null, ctx + ": no desk lamp");
		}
		// light ≥ 10 on every cell an NPC can reach from the spawn
		PlanWalkability walk = new PlanWalkability(p);
		Set<BlockPos> reach = walk.reachable(p.anchor(AnchorNames.SPAWN).block());
		assertTrue(reach.size() > 1000, ctx + ": walkable area " + reach.size());
		PlanLight light = PlanLight.of(p);
		List<BlockPos> dark = new ArrayList<>();
		for (BlockPos c : reach) {
			if (light.at(c) < TerraceGenerator.MIN_LIGHT) {
				dark.add(c);
			}
		}
		assertTrue(dark.isEmpty(), ctx + ": " + dark.size() + " walkable cells under light 10, e.g. "
				+ dark.subList(0, Math.min(8, dark.size())) + " light " + (dark.isEmpty() ? "" : light.at(dark.get(0))));
		// every island and the acropolis are reachable
		for (Cell c : TerraceTown.get().builtCells(TerraceStyle.plots(r.layout()))) {
			if (c.kind.island() || c.ring <= 1) {
				BlockPos centre = new BlockPos(c.cx() + (c.kind == Kind.PLAZA ? 7 : 12), c.floor() + 1, c.cz());
				assertTrue(reach.contains(centre) || reach.contains(new BlockPos(c.cx(), c.floor() + 1, c.cz() + 12))
						|| reach.contains(new BlockPos(c.cx(), c.floor() + 1, c.cz() - 12)), ctx + ": " + c + " reachable");
			}
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {4, 20})
	void noLooseLanternsAndNoFloatingDecoration(int n) {
		BuildPlan p = plan(HqLayoutsPlanTest.roster(n), null).plan();
		Set<net.minecraft.world.level.block.Block> lanterns = Set.of(Blocks.LANTERN, Blocks.SOUL_LANTERN, HqKit.active().lantern().getBlock(),
				HqKit.active().gardenLantern().getBlock());
		for (Placement pl : p.placements()) {
			BlockState s = pl.state();
			assertFalse(lanterns.contains(s.getBlock()) || s.getBlock() instanceof LanternBlock, "a loose lantern at " + pl.rel());
			BlockPos at = pl.rel();
			if (s.getBlock() instanceof WallSignBlock) {
				Direction facing = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
				assertSolid(p, at.relative(facing.getOpposite()), "sign at " + at);
			} else if (s.getBlock() instanceof FlowerPotBlock || s.getBlock() instanceof BellBlock || s.getBlock() instanceof CampfireBlock) {
				assertSolid(p, at.below(), s.getBlock() + " at " + at);
			}
		}
	}

	private static void assertSolid(BuildPlan p, BlockPos at, String what) {
		Placement pl = p.at(at);
		assertTrue(pl != null && !pl.state().isAir() && !(pl.state().getBlock() instanceof WallSignBlock), what + " floats (support " + at + ")");
	}

	@Test
	void theEnvelopeOnlyGrows() {
		HqLayout prev = null;
		HqFootprints.Area last = null;
		for (int n : new int[] {4, 10, 20}) {
			HqLayout l = plan(HqLayoutsPlanTest.roster(n), prev).layout();
			HqFootprints.Area a = style().envelope(l);
			assertTrue(a.contains(0, 0));
			if (last != null) {
				assertTrue(a.contains(last.minX(), last.minZ()) && a.contains(last.maxX(), last.maxZ()), n + "p: the envelope grows, never moves");
			}
			last = a;
			prev = l;
		}
	}
}
