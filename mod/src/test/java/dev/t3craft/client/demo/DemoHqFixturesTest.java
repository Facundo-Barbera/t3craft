package dev.t3craft.client.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.t3craft.blocks.Binding;
import dev.t3craft.blocks.BoundBlockEntity;
import dev.t3craft.blocks.HqBindings;
import dev.t3craft.blocks.HqBlockFixtures;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.Workstation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The demo HQ with the blocks module on: every desk gets its 3×2 screen of monitors (no lamp), every module its bulb,
 * all bound to the layout's threads; worlds built vanilla get them as a diff; and the live binding
 * follows the roster changing only the blocks that must.
 */
class DemoHqFixturesTest {
	private static final int LIFT = DemoHq.lift(-60, -64, -5);

	/** Vanilla stand-ins for the module's blocks (the real ones need the frozen registries). */
	private static final HqBlockFixtures.States STANDIN = new HqBlockFixtures.States() {
		@Override
		public BlockState monitor(Direction facing) {
			return Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, facing);
		}

		@Override
		public BlockState lamp() {
			return Blocks.REDSTONE_LAMP.defaultBlockState();
		}

		@Override
		public BlockState boardLamp() {
			return Blocks.SEA_LANTERN.defaultBlockState();
		}
	};

	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@AfterEach
	void reset() {
		HqFixtures.use(null);
	}

	private static void blocksOn() {
		HqFixtures.use(new HqBlockFixtures(STANDIN));
	}

	private static NpcInfo npc(String id, String project, boolean settled) {
		return new NpcInfo(id, id, id, "vps", "", 0xFFFFFF, null, 0, settled, "idle", "vps:" + project, project, "", null);
	}

	private static List<NpcInfo> live(int projects, int threads) {
		List<NpcInfo> out = new ArrayList<>();
		for (int p = 0; p < projects; p++) {
			for (int t = 0; t < threads; t++) {
				out.add(npc("vps:p" + p + "-t" + t, "p" + p, false));
			}
		}
		return out;
	}

	private static Block block(dev.t3craft.hq.BuildPlan plan, BlockPos rel) {
		Placement p = plan.at(rel);
		assertNotNull(p, "no cell at " + rel);
		return p.state().getBlock();
	}

	private static String binding(dev.t3craft.hq.BuildPlan plan, BlockPos rel) {
		Placement p = plan.at(rel);
		return p == null || p.be() == null ? "" : p.be().getStringOr(BoundBlockEntity.KEY, "");
	}

	private static int count(List<BuildPlan> parts, Block b) {
		int n = 0;
		for (BuildPlan p : parts) {
			for (dev.t3craft.build.Placement pl : p.placements()) {
				if (pl.state().is(b)) {
					n++;
				}
			}
		}
		return n;
	}

	// ---- the plan ------------------------------------------------------------------------------

	@Test
	void blocksOnEveryDeskHasA3x2ScreenBoundToItsThreadAndNoLamp() {
		blocksOn();
		DemoHq.Step s = DemoHq.step(DemoHq.sample(), null, null, LIFT, true);
		assertFalse(s.plan().workstations().isEmpty());
		for (Workstation w : s.plan().workstations()) {
			String want = w.threadId() == null ? "" : Binding.thread(w.threadId()).wire();
			for (net.minecraft.core.BlockPos cell : w.screenCells()) {
				assertEquals(Blocks.OBSERVER, block(s.plan(), cell), "monitor at desk " + w.module() + "/" + w.slot() + " " + cell);
				assertEquals(want, binding(s.plan(), cell));
			}
		}
		assertEquals(dev.t3craft.hq.HqPlans.buildKey(), s.fixtures());
		// and they are really in what the demo builds (baked into demo coordinates)
		List<BuildPlan> built = DemoHq.full(s.plan(), LIFT);
		assertTrue(count(built, Blocks.OBSERVER) >= 6 * s.plan().workstations().size());
		assertTrue(count(built, Blocks.REDSTONE_LAMP) <= 1, "status lamps only for the hub beacon, none on desks");
	}

	@Test
	void blocksOnEveryModuleHasItsBulbOverTheDoor() {
		blocksOn();
		DemoHq.Step s = DemoHq.step(DemoHq.fromNpcs(live(3, 3)), null, null, LIFT, true);
		int modules = s.layout().modules().size();
		assertTrue(modules >= 3);
		List<HqBindings.Fixture> bulbs = new ArrayList<>();
		for (HqBindings.Fixture f : HqBindings.ofRole(s.plan(), HqBindings.Role.BOARD_LAMP)) {
			if (f.binding().kind() == Binding.Kind.MODULE) {
				bulbs.add(f);
			}
		}
		assertEquals(modules, bulbs.size(), "one bulb per module");
		for (HqBindings.Fixture f : bulbs) {
			assertEquals(Blocks.SEA_LANTERN, block(s.plan(), f.rel()));
			assertEquals(3, f.binding().moduleThreads().size(), "bound to the module's threads: " + f.binding());
		}
		// the hub board keeps its bulbs too
		assertTrue(HqBindings.ofRole(s.plan(), HqBindings.Role.BOARD_LAMP).size() > modules);
	}

	@Test
	void blocksOffTheDemoStaysVanilla() {
		DemoHq.Step s = DemoHq.step(DemoHq.sample(), null, null, LIFT, true);
		assertEquals(dev.t3craft.hq.HqPlans.buildKey(dev.t3craft.hq.HqFixtures.VANILLA), s.fixtures());
		List<BuildPlan> built = DemoHq.full(s.plan(), LIFT);
		assertEquals(0, count(built, Blocks.OBSERVER));
		assertEquals(0, count(built, Blocks.REDSTONE_LAMP));
		assertEquals(0, count(built, Blocks.SEA_LANTERN));
		for (Workstation w : s.plan().workstations()) {
			assertEquals("", binding(s.plan(), w.computerPos()));
		}
	}

	// ---- worlds built before the fix -------------------------------------------------------------

	@Test
	void worldBuiltVanillaGetsOnlyTheFixturesAsADiff() {
		HqInput in = DemoHq.sample();
		DemoHq.Step vanilla = DemoHq.step(in, null, null, LIFT, true);
		String vanillaKey = vanilla.fixtures();
		blocksOn();
		DemoHq.Step upgrade = DemoHq.step(in, vanilla.layout(), vanilla.hash(), LIFT, true, vanillaKey);
		assertNotEquals(vanilla.hash(), upgrade.hash(), "turning the module on changes the hash");
		assertFalse(upgrade.full());
		assertFalse(upgrade.upToDate());
		int fixtures = HqBindings.of(upgrade.plan()).size();
		int placed = upgrade.parts().stream().mapToInt(BuildPlan::size).sum();
		assertEquals(fixtures, placed, "only the fixture cells are placed");

		JsonObject st = DemoHq.state(upgrade);
		assertEquals(dev.t3craft.hq.HqPlans.buildKey(), st.get(DemoHq.FIXTURES).getAsString());
		assertTrue(st.get(DemoHq.FIXTURES).getAsString().startsWith(HqBlockFixtures.ID + "+"));
		DemoHq.Step again = DemoHq.step(in, upgrade.layout(), upgrade.hash(), LIFT, true, dev.t3craft.hq.HqPlans.buildKey());
		assertTrue(again.upToDate());
	}

	@Test
	void unknownPreviousFixturesRebuildEverything() {
		blocksOn();
		DemoHq.Step first = DemoHq.step(DemoHq.sample(), null, null, LIFT, true);
		DemoHq.Step other = DemoHq.step(DemoHq.sample(), first.layout(), "old", LIFT, true, "something-else");
		assertTrue(other.full());
	}

	@Test
	void markerFromBeforeTheFixGetsTheFixturesAsADiff() {
		List<NpcInfo> roster = live(2, 2);
		DemoWorldSpec spec = DemoWorldSpec.defaults();
		DemoHqSource vanilla = new DemoHqSource(() -> roster);
		vanilla.prepare(DemoMarker.EMPTY, spec, -64);
		DemoMarker built = DemoMarker.parse(new DemoMarker(1, null, 0).withBuilt(vanilla).toJson());
		// markers written before the fix carry no "fixtures": strip it to simulate one
		JsonObject st = built.state();
		assertNotNull(st);
		st.remove(DemoHq.FIXTURES);

		blocksOn();
		DemoHqSource now = new DemoHqSource(() -> roster);
		now.prepare(built, spec, -64);
		assertTrue(built.needsBuild(now));
		List<BuildPlan> parts = now.plansSince(built);
		assertFalse(parts.isEmpty());
		assertTrue(count(parts, Blocks.OBSERVER) > 0);
	}

	// ---- live binding --------------------------------------------------------------------------

	private static Map<Long, Binding> changesFor(DemoHqBindings b, Map<Long, Binding> before, List<NpcInfo> roster) {
		Map<Long, Binding> after = b.bindings(roster);
		Map<Long, Binding> ch = HqBindings.changes(before, after);
		Set<Long> keys = new HashSet<>(before.keySet());
		keys.addAll(after.keySet());
		for (long k : keys) {
			Binding was = before.getOrDefault(k, Binding.NONE);
			Binding is = after.getOrDefault(k, Binding.NONE);
			assertEquals(!was.equals(is), ch.containsKey(k), "change listed iff the binding changed at " + BlockPos.of(k));
		}
		return ch;
	}

	@Test
	void settlingAThreadUnbindsOnlyItsDeskAndModuleBulb() {
		blocksOn();
		List<NpcInfo> roster = live(2, 3);
		DemoHq.Step s = DemoHq.step(DemoHq.fromNpcs(roster), null, null, LIFT, true);
		HqSite site = DemoHq.worldSite(new BlockPos(0, -60, 0), LIFT);
		DemoHqBindings b = new DemoHqBindings(s.layout(), site);
		Map<Long, Binding> before = b.bindings(roster);
		assertEquals(HqBindings.world(s.plan(), site), before, "starts as built");
		assertTrue(changesFor(b, before, roster).isEmpty(), "same roster: nothing to write");

		String gone = "vps:p0-t1";
		Workstation desk = s.plan().workstations().stream().filter(w -> gone.equals(w.threadId())).findFirst().orElseThrow();
		List<NpcInfo> settled = new ArrayList<>();
		for (NpcInfo n : roster) {
			settled.add(n.id().equals(gone) ? npc(gone, "p0", true) : n);
		}
		Map<Long, Binding> ch = changesFor(b, before, settled);
		java.util.Set<Long> screen = new java.util.HashSet<>();
		for (net.minecraft.core.BlockPos cell : desk.screenCells()) {
			screen.add(site.cell(cell).asLong());
			assertEquals(Binding.NONE, ch.get(site.cell(cell).asLong()), "freed desk: screen cell unbound");
		}
		int moduleBulbs = 0;
		int archive = 0;
		for (Map.Entry<Long, Binding> e : ch.entrySet()) {
			if (screen.contains(e.getKey())) {
				continue;
			}
			if (e.getValue().kind() == Binding.Kind.MODULE) {
				moduleBulbs++;
				assertFalse(e.getValue().moduleThreads().contains(gone));
			} else {
				archive++;
				assertEquals(Binding.thread(gone), e.getValue(), "only the archive monitor of the settled thread");
			}
		}
		assertEquals(1, moduleBulbs, "only its module's bulb changes");
		assertTrue(archive <= 1);
	}

	@Test
	void newThreadTakesAFreeDeskWithoutMovingAnyone() {
		blocksOn();
		List<NpcInfo> roster = live(2, 3);
		DemoHq.Step s = DemoHq.step(DemoHq.fromNpcs(roster), null, null, LIFT, true);
		HqSite site = DemoHq.worldSite(new BlockPos(0, -60, 0), LIFT);
		DemoHqBindings b = new DemoHqBindings(s.layout(), site);
		Map<Long, Binding> before = b.bindings(roster);
		// vacant desks are not built (fix/hq-stale-stations): the free slot is in the layout, not in the plan
		assertTrue(!s.layout().modules().get(0).full(), "the first module has a free desk");

		List<NpcInfo> grown = new ArrayList<>(roster);
		String project = s.layout().modules().get(0).projectKey().substring("vps:".length());
		grown.add(npc("vps:newcomer", project, false));
		Map<Long, Binding> ch = changesFor(b, before, grown);
		Map<Binding.Kind, Integer> kinds = new HashMap<>();
		for (Binding v : ch.values()) {
			kinds.merge(v.kind(), 1, Integer::sum);
			if (v.kind() == Binding.Kind.THREAD) {
				assertEquals(Binding.thread("vps:newcomer"), v, "nobody else is rebound");
			}
		}
		assertEquals(6, kinds.getOrDefault(Binding.Kind.THREAD, 0), "the six monitors of its 3×2 screen (no desk lamp)");
		assertEquals(1, kinds.getOrDefault(Binding.Kind.MODULE, 0), "its module's bulb");
		assertEquals(7, ch.size());
	}
}
