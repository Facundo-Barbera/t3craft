package dev.t3craft.hq.construct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqFootprints;
import dev.t3craft.hq.HqGenerator;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import dev.t3craft.hq.HqSite;
import dev.t3craft.hq.Placement;
import dev.t3craft.site.CellPlan;
import dev.t3craft.site.Rect;
import dev.t3craft.site.SiteFrame;
import dev.t3craft.site.SiteRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HqSiteFrameTest {
	@BeforeAll
	static void boot() {
		ConstructFixtures.boot();
	}

	@Test
	void requestFitsTheSiteLimits() {
		SiteRequest r = HqSiteFrame.request();
		assertEquals(HqSiteFrame.BASE_DEPTH, r.baseDepth());
		assertEquals(5, r.baseDepth());
		assertTrue(r.envelope().depth() <= SiteRequest.MAX_ENVELOPE);
		assertTrue(r.envelope().width() <= SiteRequest.MAX_ENVELOPE);
		// the front of the site (-z) is the HQ entrance (+z): the portico is the base's front edge
		assertEquals(r.envelope().minZ(), r.base().rect().minZ());
		assertEquals(SiteRequest.fromTag(r.toTag()), r);
	}

	@Test
	void slotsDoNotOverlapAndFitTheEnvelope() {
		List<Rect> rects = new ArrayList<>();
		rects.add(HqSiteFrame.module(HqSiteFrame.BASE).rect());
		for (int i = 0; i < HqSiteFrame.MAX_MODULES; i++) {
			rects.add(HqSiteFrame.module(HqSiteFrame.wingId(i)).rect());
		}
		Rect env = HqSiteFrame.request().envelope();
		for (int a = 0; a < rects.size(); a++) {
			assertTrue(env.contains(rects.get(a)), rects.get(a) + " in " + env);
			// a module box is rect + 2-block apron: it must stay within 3x3 chunks (≤ 33 blocks a side)
			assertTrue(rects.get(a).width() + 4 <= 33 && rects.get(a).depth() + 4 <= 33, "chunk budget " + rects.get(a));
			for (int b = a + 1; b < rects.size(); b++) {
				assertFalse(rects.get(a).intersects(rects.get(b)), rects.get(a) + " vs " + rects.get(b));
			}
		}
	}

	@Test
	void everyPlacementBelongsToABuiltSlot() {
		Random rnd = new Random(7);
		HqLayout prev = null;
		for (int step = 0; step < 40; step++) {
			HqInput in = ConstructFixtures.input(1 + rnd.nextInt(3), rnd.nextInt(9), rnd.nextInt(4));
			HqTarget t = HqTarget.compute(in, prev, HqSiteFrame.MAX_MODULES);
			prev = t.layout();
			assertTrue(t.maxIndex() < HqSiteFrame.MAX_MODULES);
			for (Placement p : t.plan().placements()) {
				String owner = HqSiteFrame.owner(p.rel().getX(), p.rel().getZ());
				assertNotNull(owner, "no slot owns " + p.rel());
				Integer idx = HqSiteFrame.indexOf(owner);
				assertTrue(idx != null && idx <= t.maxIndex(), owner + " for " + p.rel() + " beyond module " + t.maxIndex());
				assertTrue(p.rel().getY() >= HqFootprints.bottom() && p.rel().getY() <= HqFootprints.top());
				if (p.rel().getY() < 0) {
					assertEquals(HqSiteFrame.BASE, owner, "only the base goes below the floor");
				}
			}
		}
	}

	@Test
	void hqSiteMatchesTheSiteFrame() {
		BuildPlan plan = new HqGenerator().generate(HqLayout.plan(ConstructFixtures.input(2, 5, 1)));
		for (CellPlan.Facing f : CellPlan.Facing.values()) {
			CellPlan.Origin o = new CellPlan.Origin(100, 64, -40, f);
			HqSite s = HqSiteFrame.hqSite(o);
			for (Placement p : plan.placements().subList(0, 500)) {
				BlockPos site = HqSiteFrame.toSite(p.rel());
				int[] w = SiteFrame.toWorld(o, site.getX(), site.getY(), site.getZ());
				assertEquals(new BlockPos(w[0], w[1], w[2]), s.cell(p.rel()), f + " " + p.rel());
			}
		}
	}

	@Test
	void capacityLeavesExtraThreadsWithoutADesk() {
		// 5 projects × 6 threads = 5 modules; only 4 fit
		HqTarget t = HqTarget.compute(ConstructFixtures.input(5, 6, 0), null, HqSiteFrame.MAX_MODULES);
		assertEquals(4, t.layout().modules().size());
		assertEquals(6, t.unseated().size());
	}
}
