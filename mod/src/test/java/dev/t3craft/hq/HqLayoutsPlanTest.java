package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.hq.layout.HqLayouts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * feat/hq-layouts: every registered layout ({@link HqLayouts}) through the shared pipeline
 * ({@link HqStyles#forLayout}, {@link HqPlans}) with 4, 10 and 20 projects: valid and reachable
 * anchors, everything inside the layout's footprint, one 3×2 screen per desk and no desk lamps,
 * determinism and stability while growing; classic stays exactly the pre-layouts HQ.
 */
class HqLayoutsPlanTest {
	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
	}

	@AfterEach
	void reset() {
		HqLayouts.use(null);
	}

	/** {@code n} projects in three environments, 1..9 active threads each, a settled thread every third project. */
	static HqInput roster(int n) {
		String[] envs = {"vps", "laptop", "acmesite"};
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < n; p++) {
			String env = envs[p % envs.length];
			String key = env + ":proj" + p;
			b.project(key, "proj" + p, env, env.toUpperCase(java.util.Locale.ROOT));
			int threads = 1 + (p * 5) % 9;
			for (int t = 0; t < threads; t++) {
				b.thread(key, "proj" + p, env, env.toUpperCase(java.util.Locale.ROOT), key + "-t" + t);
			}
			if (p % 3 == 0) {
				b.thread(key, "proj" + p, env, env.toUpperCase(java.util.Locale.ROOT), key + "-s0", true);
			}
		}
		return b.build();
	}

	static Stream<Arguments> cases() {
		List<Arguments> out = new ArrayList<>();
		for (HqLayoutInfo i : HqLayouts.all()) {
			for (int n : new int[] {4, 10, 20}) {
				out.add(Arguments.of(i.id(), n));
			}
		}
		return out.stream();
	}

	private static HqGenerator.Result plan(String layout, HqInput in, HqLayout previous) {
		return HqGenerator.plan(in, previous, HqStyles.forLayout(layout));
	}

	@ParameterizedTest(name = "{0} with {1} projects")
	@MethodSource("cases")
	void anchorsFootprintsScreensAndDeterminism(String layout, int n) {
		HqStyle style = HqStyles.forLayout(layout);
		HqInput in = roster(n);
		HqGenerator.Result r = plan(layout, in, null);
		BuildPlan p = r.plan();
		String ctx = layout + " " + n + "p";

		HqAnchorPlacementTest.assertAnchorsValid(p, ctx);

		HqFootprints.Area env = style.envelope(r.layout());
		for (Placement pl : p.placements()) {
			BlockPos c = pl.rel();
			assertTrue(env.contains(c.getX(), c.getZ()), ctx + ": outside the footprint " + c + " " + env);
			assertTrue(c.getY() >= style.bottom() && c.getY() <= style.top(), ctx + ": height " + c);
		}

		int threads = in.projects().stream().mapToInt(x -> x.threads().size()).sum();
		assertEquals(threads, p.workstations().size(), ctx + ": a desk for every active thread");
		Set<BlockPos> screens = new HashSet<>();
		for (Workstation w : p.workstations()) {
			List<BlockPos> cells = w.screenCells();
			assertEquals(Workstation.SCREEN_WIDTH * Workstation.SCREEN_HEIGHT, cells.size());
			assertEquals(3, Workstation.SCREEN_WIDTH);
			assertEquals(2, Workstation.SCREEN_HEIGHT);
			for (BlockPos c : cells) {
				assertEquals(style.computer(true), p.at(c).state(), ctx + ": screen cell " + c);
				assertTrue(screens.add(c), ctx + ": screens never shared");
			}
			assertNull(p.anchor(AnchorNames.lamp(w.threadId())), ctx + ": no desk lamp anchor");
			BlockPos stand = w.deskAnchor().block();
			assertTrue(p.at(stand).state().isAir() && p.at(stand.above()).state().isAir(), ctx + ": standing cell, no chair " + stand);
		}
		Set<BlockPos> boardLamps = new HashSet<>();
		p.anchors().values().stream().filter(a -> a.name().startsWith(AnchorNames.BOARD_LAMP)).forEach(a -> boardLamps.add(a.block()));
		for (Placement pl : p.placements()) {
			if (pl.state().getBlock() == style.statusLamp().getBlock() && !pl.state().isAir()) {
				assertTrue(boardLamps.contains(pl.rel()), ctx + ": a status lamp outside the board at " + pl.rel());
			}
		}
		assertTrue(p.anchor(AnchorNames.ARCHIVE_TERMINAL) != null, ctx + ": the archive terminal");

		// deterministic: same roster (also rebuilt in another order) → same layout and plan
		HqGenerator.Result again = plan(layout, HqInput.fromRoster(reversed(HqTestSupport.roster(in))), null);
		assertEquals(r.layout(), again.layout(), ctx);
		assertEquals(p.fingerprint(), again.plan().fingerprint(), ctx);
		// idempotent: allocating against itself changes nothing
		assertEquals(r.layout(), plan(layout, in, r.layout()).layout(), ctx);
	}

	private static List<HqInput.Entry> reversed(List<HqInput.Entry> l) {
		List<HqInput.Entry> out = new ArrayList<>(l);
		java.util.Collections.reverse(out);
		return out;
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("layouts")
	void growing4To10To20KeepsEveryDeskAndScreen(String layout) {
		HqGenerator.Result prev = null;
		for (int n : new int[] {4, 10, 20}) {
			HqGenerator.Result r = plan(layout, roster(n), prev == null ? null : prev.layout());
			if (prev != null) {
				Map<String, Workstation> before = new HashMap<>();
				prev.plan().workstations().forEach(w -> before.put(w.threadId(), w));
				for (Workstation w : r.plan().workstations()) {
					Workstation b = before.get(w.threadId());
					if (b != null) {
						assertEquals(b.computerPos(), w.computerPos(), layout + " " + n + "p: " + w.threadId() + "'s desk moved");
						assertEquals(b.deskAnchor().block(), w.deskAnchor().block(), layout + " " + n + "p");
					}
				}
				assertEquals(prev.layout().seats().keySet().stream().filter(t -> r.layout().seat(t) == null).toList(), List.of(),
						layout + ": nobody loses a desk while the roster only grows");
			}
			prev = r;
		}
	}

	static Stream<String> layouts() {
		return HqLayouts.all().stream().map(HqLayoutInfo::id);
	}

	// ------------------------------------------------------------------ classic = main

	@Test
	void classicIsTheHqOfMain() {
		HqStyle classic = HqStyles.forLayout("classic");
		assertSame(HqStyles.active(), classic, "classic draws with the configured style (cherry by default)");
		assertEquals(CherryStyle.ID, classic.id());
		assertFalse(classic.ownLayout());
		assertEquals(HqStyles.forLayout(null), classic);
		assertEquals(HqStyles.forLayout("nope"), classic);
		HqInput in = roster(10);
		HqLayouts.use("classic");
		HqGenerator.Result viaLayouts = HqPlans.plan(in, null);
		// what main did: the spine allocation and the configured style, nothing else
		HqLayout mainLayout = HqLayout.allocate(in, null);
		BuildPlan mainPlan = HqFixtures.active().apply(new HqGenerator(HqStyles.active()).generate(mainLayout));
		assertEquals(mainLayout, viaLayouts.layout());
		assertEquals(mainPlan.fingerprint(), viaLayouts.plan().fingerprint());
		assertTrue(HqPlans.buildKey().endsWith("@" + CherryStyle.ID), "classic build key unchanged: " + HqPlans.buildKey());
		assertEquals(mainLayout.toJson(), viaLayouts.layout().toJson(), "classic layout JSON (and so the demo hash) unchanged");
	}

	@Test
	void everyLayoutHasItsOwnStyleAndBuildKey() {
		Set<String> keys = new HashSet<>();
		for (HqLayoutInfo i : HqLayouts.all()) {
			HqLayouts.use(i.id());
			assertSame(HqStyles.forLayout(i.id()), HqStyles.current());
			assertTrue(keys.add(HqPlans.buildKey()), "build key of " + i.id() + " is distinct");
			assertEquals(i.id().equals("classic"), !HqStyles.current().ownLayout(), i.id());
			// one visual style: every layout paints with the cherry palette
			assertEquals(new CherryStyle().palette(), HqStyles.current().palette(), i.id());
		}
		HqLayouts.use("ring");
		assertNotEquals(HqLayout.allocate(roster(10), null), HqPlans.plan(roster(10), null).layout(), "the active layout drives HqPlans");
	}

	@Test
	void theSurvivalSiteKeepsTheSpineGrid() {
		for (HqLayoutInfo i : HqLayouts.all()) {
			HqLayouts.use(i.id());
			assertFalse(dev.t3craft.hq.construct.HqSiteFrame.style().ownLayout(), i.id());
		}
	}
}
