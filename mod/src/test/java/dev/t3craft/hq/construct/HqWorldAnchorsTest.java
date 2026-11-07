package dev.t3craft.hq.construct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.t3craft.client.hq.HqStations;
import dev.t3craft.client.hq.HqWorldAnchors;
import dev.t3craft.client.npc.behavior.Anchor;
import dev.t3craft.client.workstation.StationPlacement;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.WorkstationThread;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.HqAnchorSource;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.Workstation;
import dev.t3craft.site.CellPlan;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HqWorldAnchorsTest {
	@BeforeAll
	static void boot() {
		ConstructFixtures.boot();
	}

	@Test
	void everyNpcHasASpotInsideTheHqNeverTheRing() {
		HqInput in = ConstructFixtures.input(2, 7, 2); // 2 projects × 7 active: 4 wings, plus settled
		HqTarget t = HqTarget.compute(in, null, HqSiteFrame.MAX_MODULES);
		HqSite site = HqSiteFrame.hqSite(new CellPlan.Origin(10, 64, 10, CellPlan.Facing.SOUTH));
		// wings 0 and 1 stand, 2 and 3 are still being built
		HqWorldAnchors a = new HqWorldAnchors(t.plan(), site, Set.of("base", "wing-0", "wing-1"));
		HqAnchorSource raw = new HqAnchorSource(t.plan(), site);
		for (HqInput.Project p : in.projects()) {
			assertNotNull(a.home(p.key()));
			for (String id : p.threads()) {
				Anchor d = a.desk(id, p.key());
				assertNotNull(d, id);
				assertNotNull(a.rest(id, p.key()));
				Workstation w = t.plan().workstation(id);
				if (w.module() <= 1) {
					assertEquals(raw.desk(id, p.key()), d, "seated in a built wing: its own desk");
				} else {
					assertNotEquals(raw.desk(id, p.key()), d, "wing not built yet: waits in the hub");
				}
			}
			for (String id : p.settled()) {
				assertEquals(raw.named(AnchorNames.archive(id)), a.desk(id, p.key()), "settled: at its archived computer");
			}
		}
		assertNotNull(a.board());
		assertNotNull(a.desk("unknown-thread", "vps:nowhere"));
	}

	@Test
	void stationsSitOnTheHqComputers() {
		HqInput in = ConstructFixtures.input(1, 3, 1);
		HqTarget t = HqTarget.compute(in, null, HqSiteFrame.MAX_MODULES);
		HqSite site = HqSiteFrame.hqSite(new CellPlan.Origin(0, 70, 0, CellPlan.Facing.WEST));
		HqStations s = new HqStations(t.plan(), site, Set.of("base", "wing-0"));
		Workstation w = t.plan().workstation("vps:p0-a0");
		StationPlacement p = s.placement(thread("vps:p0-a0"));
		assertNotNull(p);
		BlockPos c = site.cell(w.computerPos());
		assertEquals(c, new BlockPos(p.computerX(), p.computerY(), p.computerZ()));
		StationPlacement archived = s.placement(thread("vps:p0-s0"));
		assertNotNull(archived, "settled threads show on their archived computer");
		assertNull(new HqStations(t.plan(), site, Set.of("base")).placement(thread("vps:p0-a0")), "no station before its wing stands");
	}

	private static WorkstationThread thread(String key) {
		return new WorkstationThread(key, "vps", key, "title", "vps:p0", 0, StationStatus.of("working", false), null, null, null);
	}
}
