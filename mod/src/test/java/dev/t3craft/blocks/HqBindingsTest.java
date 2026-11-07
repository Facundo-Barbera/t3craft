package dev.t3craft.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.Placement;
import dev.t3craft.hq.Workstation;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Thread ↔ block binding from the HQ plan, and the HQ adapter that writes it into the plan. */
class HqBindingsTest {
	/** Vanilla stand-ins (the real blocks cannot be built after the registries froze). */
	static final HqBlockFixtures.States STANDIN = new HqBlockFixtures.States() {
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
		BlocksTestSupport.boot();
	}

	@AfterEach
	void reset() {
		HqFixtures.use(null);
	}

	private static BuildPlan plan(List<HqInput.Entry> roster) {
		return new HqGenerator().generate(HqLayout.plan(HqInput.fromRoster(roster)));
	}

	@Test
	void everyDeskIsBoundToItsThread() {
		BuildPlan p = plan(BlocksTestSupport.roster());
		List<HqBindings.Fixture> fx = HqBindings.of(p);
		int taken = 0;
		for (Workstation w : p.workstations()) {
			HqBindings.Fixture mon = find(fx, w.computerPos());
			HqBindings.Fixture lamp = find(fx, w.lampPos());
			assertEquals(HqBindings.Role.MONITOR, mon.role());
			assertEquals(HqBindings.Role.LAMP, lamp.role());
			assertEquals(w.screenFacing(), mon.facing(), "the screen faces the chair");
			if (w.threadId() == null) {
				assertTrue(mon.binding().isNone(), "vacant desk stays unbound");
			} else {
				taken++;
				assertEquals(Binding.thread(w.threadId()), mon.binding());
				assertEquals(mon.binding(), lamp.binding());
			}
		}
		assertEquals(7, taken, "7 active threads, 1 settled");
	}

	@Test
	void boardBulbsAndArchive() {
		BuildPlan p = plan(BlocksTestSupport.roster());
		List<HqBindings.Fixture> bulbs = new java.util.ArrayList<>();
		int moduleBulbs = 0;
		for (HqBindings.Fixture f : HqBindings.ofRole(p, HqBindings.Role.BOARD_LAMP)) {
			if (f.binding().kind() == Binding.Kind.BOARD) {
				bulbs.add(f);
			} else {
				assertTrue(f.binding().kind() != Binding.Kind.THREAD, "the other bulbs are the modules' own");
				moduleBulbs++;
			}
		}
		assertFalse(bulbs.isEmpty());
		assertEquals(p.modules().stream().filter(m -> m.id().endsWith("_inner")).count(), moduleBulbs, "one bulb per module");
		Set<Integer> idx = new HashSet<>();
		for (HqBindings.Fixture f : bulbs) {
			idx.add(f.binding().boardIndex());
			assertEquals(p.anchor(AnchorNames.boardLamp(f.binding().boardIndex())).block(), f.rel());
		}
		assertEquals(bulbs.size(), idx.size());
		for (int i = 0; i < bulbs.size(); i++) {
			assertTrue(idx.contains(i), "bulbs 0..n-1");
		}
		// the settled thread's computer in the archive is a monitor bound to it
		BlockPos archived = p.anchor(AnchorNames.archive("vps:t4")).block();
		HqBindings.Fixture a = find(HqBindings.of(p), archived);
		assertEquals(Binding.thread("vps:t4"), a.binding());
		assertEquals(HqBindings.Role.MONITOR, a.role());
		assertNull(p.workstation("vps:t4"), "no desk for the settled thread");
	}

	@Test
	void deterministicForAnyRosterOrder() {
		List<HqBindings.Fixture> ref = HqBindings.of(plan(BlocksTestSupport.roster()));
		for (long seed = 1; seed <= 8; seed++) {
			assertEquals(ref, HqBindings.of(plan(BlocksTestSupport.shuffled(seed))), "seed " + seed);
		}
	}

	@Test
	void fixturesSwapOnlyFixtureCells() {
		BuildPlan vanilla = plan(BlocksTestSupport.roster());
		BuildPlan swapped = new HqBlockFixtures(STANDIN).apply(vanilla);
		Map<BlockPos, HqBindings.Fixture> fx = new java.util.HashMap<>();
		HqBindings.of(vanilla).forEach(f -> fx.put(f.rel(), f));
		assertEquals(vanilla.size(), swapped.size());
		assertEquals(vanilla.anchors(), swapped.anchors());
		assertEquals(vanilla.workstations(), swapped.workstations());
		for (int i = 0; i < vanilla.size(); i++) {
			Placement v = vanilla.placements().get(i);
			Placement s = swapped.placements().get(i);
			assertEquals(v.rel(), s.rel(), "same order, same cells");
			HqBindings.Fixture f = fx.get(v.rel());
			if (f == null) {
				assertTrue(v.sameContent(s), "non-fixture cell untouched at " + v.rel());
				continue;
			}
			CompoundTag be = s.be();
			assertNotNull(be);
			assertEquals(f.binding().wire(), be.getStringOr(BoundBlockEntity.KEY, ""));
			switch (f.role()) {
				case MONITOR -> assertEquals(STANDIN.monitor(f.facing()), s.state());
				case LAMP -> assertEquals(STANDIN.lamp(), s.state());
				case BOARD_LAMP -> assertEquals(STANDIN.boardLamp(), s.state());
			}
		}
	}

	@Test
	void fixturesAreIdempotentAndStable() {
		HqBlockFixtures f = new HqBlockFixtures(STANDIN);
		BuildPlan once = f.apply(plan(BlocksTestSupport.roster()));
		assertEquals(once.fingerprint(), f.apply(once).fingerprint(), "applying twice changes nothing");
		assertEquals(once.fingerprint(), f.apply(plan(BlocksTestSupport.shuffled(42))).fingerprint(), "same roster, any order");
	}

	@Test
	void hqPlansUseInstalledFixtures() {
		HqLayout layout = HqLayout.plan(BlocksTestSupport.input());
		String vanilla = HqPlans.generate(layout).fingerprint();
		HqFixtures.use(new HqBlockFixtures(STANDIN));
		BuildPlan withBlocks = HqPlans.generate(layout);
		assertFalse(vanilla.equals(withBlocks.fingerprint()));
		HqFixtures.use(null);
		assertEquals(vanilla, HqPlans.generate(layout).fingerprint(), "uninstalled → vanilla again");
	}

	@Test
	void worldBindingsFollowTheSite() {
		BuildPlan p = plan(BlocksTestSupport.roster());
		for (Rotation r : Rotation.values()) {
			HqSite site = new HqSite(new BlockPos(100, 64, -40), r);
			Map<Long, Binding> world = HqBindings.world(p, site);
			for (HqBindings.Fixture f : HqBindings.of(p)) {
				assertEquals(f.binding(), world.get(site.cell(f.rel()).asLong()), r + " " + f.rel());
			}
			assertEquals(HqBindings.of(p).size(), world.size());
		}
	}

	private static HqBindings.Fixture find(List<HqBindings.Fixture> fx, BlockPos rel) {
		for (HqBindings.Fixture f : fx) {
			if (f.rel().equals(rel)) {
				return f;
			}
		}
		throw new AssertionError("no fixture at " + rel);
	}

	@Test
	void hubLanternCapIsTheBeacon() {
		BuildPlan p = plan(BlocksTestSupport.roster());
		List<HqBindings.Fixture> beacons = HqBindings.ofRole(p, HqBindings.Role.BEACON);
		assertEquals(1, beacons.size());
		HqBindings.Fixture f = beacons.get(0);
		assertEquals(Binding.BEACON, f.binding());
		assertEquals(p.anchor(AnchorNames.BEACON).block(), f.rel());
		assertEquals(p.anchor(AnchorNames.HUB).block().above(8), f.rel(), "the lantern cap, above the hub floor");
		assertFalse(p.at(f.rel()).state().isAir(), "a solid cap block to swap");
		BuildPlan swapped = new HqBlockFixtures(STANDIN).apply(p);
		Placement lamp = swapped.at(f.rel());
		assertEquals(STANDIN.lamp(), lamp.state(), "the module puts a status lamp in the cap");
		assertEquals("beacon", lamp.be().getStringOr(BoundBlockEntity.KEY, ""));
	}
}
