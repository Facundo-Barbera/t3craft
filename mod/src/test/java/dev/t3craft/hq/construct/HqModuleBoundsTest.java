package dev.t3craft.hq.construct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.blocks.HqBlockFixtures;
import dev.t3craft.hq.HqFixtures;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.Placement;
import dev.t3craft.site.Box;
import dev.t3craft.site.CellPlan;
import dev.t3craft.site.SiteHandle;
import dev.t3craft.site.SiteJournal;
import dev.t3craft.site.mc.BuildPlanBridge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Regression for "module wing-N: ~9000 placements outside its area skipped, 12-14 block entities
 * dropped": every site module is designed from exactly its own cells, so the site bridge clips
 * nothing and drops nothing, for growing rosters (1, 2, 4 projects, annexes, settled threads), on
 * every facing, with the blocks module on (monitors and lamps carry block entity data) and off.
 */
class HqModuleBoundsTest {
	/** Vanilla stand-ins for the module's blocks (the registries are frozen in tests). */
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
		ConstructFixtures.boot();
	}

	@AfterEach
	void reset() {
		HqFixtures.use(null);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void everyModuleIsDesignedFromItsOwnCellsOnly(boolean blocksModule) {
		HqFixtures.use(blocksModule ? new HqBlockFixtures(STANDIN) : null);
		// growth then settling, each step from the previous layout (as the construction does)
		List<HqInput> steps = List.of(
				ConstructFixtures.input(1, 3, 0),
				ConstructFixtures.input(1, 6, 2),
				ConstructFixtures.input(2, 6, 1),
				ConstructFixtures.input(2, 9, 3), // annex: 9 threads need more than one module
				ConstructFixtures.input(4, 6, 3),
				ConstructFixtures.input(4, 2, 8)); // most threads settled
		HqLayout prev = null;
		int bes = 0;
		for (HqInput in : steps) {
			HqTarget t = HqTarget.compute(in, prev, HqSiteFrame.MAX_MODULES);
			prev = t.layout();
			List<String> ids = new ArrayList<>(List.of(HqSiteFrame.BASE));
			for (int i = 0; i <= t.maxIndex(); i++) {
				ids.add(HqSiteFrame.wingId(i));
			}

			// every buildable cell belongs to exactly one built module, block entities included
			int buildable = 0;
			for (Placement p : t.plan().placements()) {
				if (!HqSiteFrame.builds(p.rel())) {
					continue;
				}
				buildable++;
				String owner = HqSiteFrame.owner(p.rel().getX(), p.rel().getZ());
				assertNotNull(owner, "no module owns " + p.rel());
				assertTrue(ids.contains(owner), owner + " is not built for " + p.rel());
				if (p.be() != null) {
					bes++;
				}
			}
			int cut = 0;
			for (String id : ids) {
				cut += HqSiteFrame.sitePlan(t.plan(), id).placements().size();
			}
			assertEquals(buildable, cut, "module plans must split the plan exactly");

			// the bridge, on every facing: nothing outside the module area, no block entity dropped
			for (CellPlan.Facing f : CellPlan.Facing.values()) {
				CellPlan.Origin o = new CellPlan.Origin(100, 64, -40, f);
				SiteHandle h = new SiteHandle("s", "minecraft:overworld", SiteJournal.Phase.BUILT, o, HqSiteFrame.request(),
						new Box(0, 0, 0, 0, 0, 0), List.of(), List.of());
				for (String id : ids) {
					dev.t3craft.build.BuildPlan part = HqSiteFrame.sitePlan(t.plan(), id);
					BuildPlanBridge.Cut c = BuildPlanBridge.cut(part, h.moduleArea(HqSiteFrame.module(id).rect()), o);
					String what = id + " facing " + f + " roster " + in.hashCode();
					assertEquals(0, c.clipped(), what + ": placements outside the module area");
					assertEquals(0, c.dropped(), what + ": block entities handed to the site");
					assertEquals(part.placements().size(), c.plan().count(), what + ": cells lost");
				}
			}
		}
		if (blocksModule) {
			assertTrue(bes > 0, "the blocks module puts block entity data on monitors and lamps");
		}
	}
}
