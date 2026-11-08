package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.client.demo.DemoPlacement;
import dev.t3craft.client.demo.DemoWorldSpec;
import dev.t3craft.client.hq.HqWorldAnchors;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqPlans;
import dev.t3craft.hq.Workstation;
import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.hq.layout.HqLayouts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * fix/hq-npc-spawn: in every layout, with 4, 10 and 20 projects, the HQ <b>as the demo builds it</b>
 * ({@link DemoHqWorld}: superflat ground, lift, tuff plinth, every group baked at the demo site)
 * has a way in where an NPC can stand, and the NPCs' own {@link Navigation} (the A* over real
 * collision shapes, with its node budget, search box and via point, exactly what
 * {@link BehaviorDirector} uses) finds a route from it to every desk and every standing anchor
 * (stroll points, hub, benches, board, lounge, doors) that the NPC then walks to the end.
 *
 * <p>The plan validator ({@code HqAnchorPlacementTest}) only flood-fills the plan on its own: it never
 * saw where the NPCs really start. They start on the provisional grid around the player, and the
 * demo put the player (and so the grid) at the classic viewpoint, which for every layout with its
 * own ground plan is inside or under the building: see {@link #theOldViewpointWasInsideTheBuilding}
 * and docs/fix-hq-npc-spawn.md.
 */
class DemoHqReachabilityTest {
	static final int MAX_TICKS = 400;
	static final double NEXT_TO = 1.01;

	@BeforeAll
	static void boot() {
		HqWorld.boot();
	}

	@AfterEach
	void reset() {
		HqLayouts.use(null);
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

	/** {@code n} projects, 1..9 threads each (some big enough for extra floors and annexes). */
	static HqInput roster(int n) {
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < n; p++) {
			String key = "env:p" + p;
			b.project(key, "Project " + p, "env", "env");
			int threads = 1 + (p * 5) % 9;
			for (int t = 0; t < threads; t++) {
				b.thread(key, "Project " + p, "env", "env", key + "-t" + t);
			}
		}
		return b.build();
	}

	record Hq(BuildPlan plan, DemoHqWorld world, HqWorldAnchors anchors, Navigation nav, Collision collision) {
		static Hq of(String layout, int projects) {
			HqLayouts.use(layout);
			BuildPlan plan = HqPlans.plan(roster(projects), null).plan();
			DemoHqWorld w = new DemoHqWorld(plan);
			HqWorldAnchors a = new HqWorldAnchors(plan, w.site, null);
			Navigation nav = Navigation.over(new LevelBlocks(w));
			nav.setVia(a.exit() == null ? null : a.exit().pos());
			return new Hq(plan, w, a, nav, new Collision(new LevelBlocks(w)));
		}

		Pos world(dev.t3craft.hq.Anchor rel) {
			dev.t3craft.hq.Anchor a = world.site.anchor(rel);
			return new Pos(a.x(), a.y(), a.z());
		}

		/** Desks of every thread plus every standing anchor of the plan, by name, in world coordinates. */
		Map<String, Pos> targets() {
			Map<String, Pos> out = new LinkedHashMap<>();
			for (Workstation ws : plan.workstations()) {
				out.put("desk " + ws.threadId(), anchors.desk(ws.threadId(), "-").pos());
			}
			for (dev.t3craft.hq.Anchor a : plan.anchors().values()) {
				if (AnchorNames.standing(a.name())) {
					out.put(a.name(), world(a));
				}
			}
			return out;
		}
	}

	static Route routeOverTicks(Navigation nav, Pos from, Pos to) {
		Route r = Route.PENDING;
		for (int t = 0; t < MAX_TICKS && r.status() == Route.Status.PENDING; t++) {
			nav.beginTick();
			r = nav.route(from, to, null);
		}
		return r;
	}

	static void assertStandable(Collision c, Pos p, String ctx) {
		double floor = c.floor(p.blockX(), p.blockY(), p.blockZ());
		assertFalse(Double.isNaN(floor), ctx + ": nowhere to stand at " + p);
		assertEquals(floor, p.y(), 0.07, ctx + ": floats or sinks at " + p);
		assertTrue(c.fits(p.x(), floor, p.z()), ctx + ": feet and head not free at " + p);
	}

	@ParameterizedTest(name = "{0} with {1} projects")
	@MethodSource("cases")
	void fromTheWayInEveryDeskAndStrollPointIsWalkable(String layout, int projects) {
		Hq hq = Hq.of(layout, projects);
		String ctx = layout + " " + projects + "p";
		Anchor spawnAnchor = hq.anchors.spawn();
		assertNotNull(spawnAnchor, ctx + ": no spawn");
		Pos spawn = spawnAnchor.pos();
		assertStandable(hq.collision, spawn, ctx + " spawn");
		// the player's viewpoint for this layout is a place to stand too (never inside the building)
		int[] v = DemoPlacement.viewpoint(DemoWorldSpec.defaults(), DemoHqWorld.MIN_Y);
		Pos view = new Pos(v[0] + 0.5, v[1], v[2] + 0.5);
		assertFalse(Double.isNaN(hq.collision.floor(view.blockX(), view.blockY(), view.blockZ())), ctx + ": viewpoint inside the HQ " + view);

		List<String> failures = new ArrayList<>();
		for (Map.Entry<String, Pos> e : hq.targets().entrySet()) {
			Pos to = e.getValue();
			if (to.horizDist(spawn) < 0.05 && Math.abs(to.y() - spawn.y()) < 0.5) {
				continue;
			}
			Route r = routeOverTicks(hq.nav, spawn, to);
			if (r.status() != Route.Status.FOUND) {
				failures.add(e.getKey() + " " + to + ": " + r.status());
				continue;
			}
			Pos end = NavigationTest.walk(hq.collision, r.points());
			if (end.horizDist(to) > NEXT_TO || Math.abs(end.y() - to.y()) > 0.6) {
				failures.add(e.getKey() + " " + to + ": walked to " + end);
			}
		}
		assertTrue(failures.isEmpty(), ctx + ": " + failures.size() + " unreachable from the spawn " + spawn + ": "
				+ failures.subList(0, Math.min(8, failures.size())));
	}

	@Test
	void theOldViewpointWasInsideTheBuilding() {
		// The cause: the classic viewpoint (10 blocks in front of the classic portico) is where the demo
		// put the player, and the provisional NPC grid around them. In the ring it is in the cloister's
		// footing next to the hub podium: no floor to stand on.
		Hq ring = Hq.of(HqLayouts.RING, 4);
		int[] old = DemoPlacement.viewpoint(DemoWorldSpec.defaults());
		assertTrue(Double.isNaN(ring.collision.floor(old[0], old[1], old[2])), "the classic viewpoint is inside the ring HQ");
		Hq classic = Hq.of(HqLayouts.CLASSIC, 4);
		assertArrayEquals(old, DemoPlacement.viewpoint(DemoWorldSpec.defaults(), DemoHqWorld.MIN_Y), "classic keeps its viewpoint");
		assertFalse(Double.isNaN(classic.collision.floor(old[0], old[1], old[2])), "in front of the classic portico, on the grass");
	}

	@Test
	void npcsLeftOnTheGridInsideTheRingAreRescuedAndReachTheirDesks() {
		// The report: NPCs waited on the provisional grid around the player (the old viewpoint), the ring
		// HQ was built around them, and some had no way to their desks. They are put back on the way in.
		Hq hq = Hq.of(HqLayouts.RING, 4);
		int[] old = DemoPlacement.viewpoint(DemoWorldSpec.defaults());
		Pos origin = new Pos(old[0] + 0.5, old[1], old[2] + 0.5);
		List<GridLayout.Member> members = new ArrayList<>();
		for (Workstation ws : hq.plan.workstations()) {
			members.add(new GridLayout.Member(ws.threadId(), ws.threadId().substring(0, ws.threadId().indexOf('-')), "env"));
		}
		GridLayout grid = new GridLayout();
		grid.update(origin, members, GridLayout.Ground.near(hq.collision, origin.y()));
		List<NpcBrain> brains = new ArrayList<>();
		int trapped = 0;
		for (GridLayout.Member m : members) {
			Pos start = grid.desk(m.id(), m.groupId()).pos();
			Route r = routeOverTicks(hq.nav, start, hq.anchors.desk(m.id(), m.groupId()).pos());
			trapped += r.status() == Route.Status.FOUND ? 0 : 1;
			NpcBrain b = new NpcBrain(m.id(), start);
			// first the grid (no HQ yet): the brain stands on its grid spot
			Anchor gridSpot = grid.desk(m.id(), m.groupId());
			hq.nav.beginTick();
			b.tick(Mode.WORKING, false, new NpcBrain.Targets(gridSpot, null, gridSpot, 2, gridSpot), null, hq.nav);
			brains.add(b);
		}
		assertTrue(trapped > 0, "the repro: some grid spots have no way to their desks");
		hq.nav.invalidate();
		Anchor spawn = hq.anchors.spawn();
		int rescued = 0;
		for (int t = 0; t < 6000; t++) {
			hq.nav.beginTick();
			boolean all = true;
			for (NpcBrain b : brains) {
				Anchor desk = hq.anchors.desk(b.id(), "-");
				b.tick(Mode.WORKING, false, new NpcBrain.Targets(desk, null, desk, 2, desk), null, hq.nav);
				if (BehaviorDirector.rescue(b, spawn)) {
					rescued++;
				}
				all &= b.phase() == NpcBrain.Phase.TYPING && b.pos().horizDist(desk.pos()) <= NEXT_TO;
			}
			if (all) {
				assertTrue(rescued > 0, "the trapped ones got there through a rescue");
				return;
			}
		}
		for (NpcBrain b : brains) {
			Anchor desk = hq.anchors.desk(b.id(), "-");
			assertTrue(b.pos().horizDist(desk.pos()) <= NEXT_TO, b.id() + " stuck at " + b.pos() + " (" + b.phase() + "), desk " + desk.pos());
		}
	}

	@Test
	void aStrandedNpcAlreadyAtTheWayInStaysPut() {
		NpcBrain b = new NpcBrain("x", new Pos(0.5, 0, 0.5));
		Anchor spawn = Anchor.at(0.5, 0, 0.5, 0);
		assertFalse(BehaviorDirector.rescue(b, spawn), "not stranded");
		assertFalse(BehaviorDirector.rescue(b, null), "no way in known");
	}
}
