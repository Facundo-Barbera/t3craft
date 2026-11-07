package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.client.hq.HqWorldAnchors;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.Workstation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * fix/npc-enter-hq: NPCs waiting on the grass of the provisional grid outside the real HQ (built the
 * way the site builds it: its floor one block above the grass, no step at the portico) find their
 * way in to their desks and get there, in every orientation.
 */
class HqEntryTest {
	/** Where the in-game report had the grid and the HQ: grid at -40,-60,9, HQ origin 0,-60,21. */
	static final Pos GRID = new Pos(-40.5, -60, 9.5);
	static final int MAX_TICKS = 200;
	/** At the desk: on the spot, or in the cell next to it when the spot itself is a seat. */
	static final double NEXT_TO = 1.01;
	private static BuildPlan plan;

	@BeforeAll
	static void boot() {
		plan = HqWorld.plan(2, 3);
	}

	record World(HqWorld level, HqWorldAnchors anchors, Navigation nav, Collision collision) {
		static World of(Rotation r) {
			HqWorld w = new HqWorld(plan, HqWorld.site(0, -60, 21, r), 70);
			HqWorldAnchors a = new HqWorldAnchors(plan, w.site, null);
			Navigation nav = Navigation.over(new LevelBlocks(w));
			nav.setVia(a.exit() == null ? null : a.exit().pos());
			return new World(w, a, nav, new Collision(new LevelBlocks(w)));
		}

		Anchor desk(String threadId) {
			return anchors.desk(threadId, threadId.substring(0, threadId.indexOf('-')));
		}
	}

	static List<String> threads() {
		List<String> out = new ArrayList<>();
		for (Workstation ws : plan.workstations()) {
			if (ws.threadId() != null) {
				out.add(ws.threadId());
			}
		}
		assertFalse(out.isEmpty());
		return out;
	}

	/** Asks for the route tick after tick (node budget) until it is no longer pending. */
	static Route routeOverTicks(Navigation nav, Pos from, Pos to) {
		Route r = Route.PENDING;
		for (int t = 0; t < MAX_TICKS && r.status() == Route.Status.PENDING; t++) {
			nav.beginTick();
			r = nav.route(from, to, null);
		}
		return r;
	}

	@Test
	void theHqFloorIsABlockAboveTheGrass() {
		// The cause of the report: the floor stands on the pad, a whole block above the grass, and
		// the portico has no step. Without hops there was no way in at all.
		World w = World.of(Rotation.NONE);
		dev.t3craft.hq.Anchor sp = w.level.site.anchor(plan.anchor(dev.t3craft.hq.AnchorNames.SPAWN));
		assertEquals(-59.0, w.collision.floor((int) Math.floor(sp.x()), (int) Math.floor(sp.y()), (int) Math.floor(sp.z())), 0.07);
		assertEquals(-60.0, w.collision.floor(GRID.blockX(), GRID.blockY(), GRID.blockZ()), 1e-9);
	}

	@Test
	void everyDeskIsReachableFromTheGridOutside() {
		for (Rotation r : Rotation.values()) {
			World w = World.of(r);
			for (String id : threads()) {
				Anchor desk = w.desk(id);
				assertNotNull(desk, id);
				Route route = routeOverTicks(w.nav, GRID, desk.pos());
				assertEquals(Route.Status.FOUND, route.status(), r + " " + id + " → " + desk.pos());
				Pos end = NavigationTest.walk(w.collision, route.points());
				// on the spot, or on the free cell next to it when the spot is a seat (the route's stand point)
				assertTrue(end.horizDist(route.points().getLast()) < 1e-6 && end.horizDist(desk.pos()) <= NEXT_TO,
						r + " " + id + " walked to " + end + ", desk " + desk.pos());
			}
		}
	}

	@Test
	void brainsWalkInWhenTheHqIsBuiltAndTheAnchorsMove() {
		World w = World.of(Rotation.CLOCKWISE_180);
		List<String> ids = threads();
		List<NpcBrain> brains = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			brains.add(new NpcBrain(ids.get(i), GRID.add(i % 3, 0, i / 3)));
		}
		// First the grid: everyone at their spot on the grass.
		for (int i = 0; i < ids.size(); i++) {
			Anchor grid = Anchor.at(GRID.x() + i % 3, GRID.y(), GRID.z() + i / 3, 0);
			w.nav.beginTick();
			brains.get(i).tick(Mode.WORKING, false, new NpcBrain.Targets(grid, null, grid, 2, grid), null, w.nav);
		}
		// The HQ is built: anchors move inside (what BehaviorDirector.setAnchorSource does).
		w.nav.invalidate();
		brains.forEach(NpcBrain::replan);
		for (int t = 0; t < 3000; t++) {
			w.nav.beginTick();
			boolean all = true;
			for (NpcBrain b : brains) {
				Anchor desk = w.desk(b.id());
				b.tick(Mode.WORKING, false, new NpcBrain.Targets(desk, null, desk, 2, desk), null, w.nav);
				assertFalse(w.collision.embedded(b.pos()), b.id() + " inside a block at " + b.pos());
				all &= b.phase() == NpcBrain.Phase.TYPING && b.pos().horizDist(desk.pos()) <= NEXT_TO;
			}
			if (all) {
				return;
			}
		}
		for (NpcBrain b : brains) {
			Anchor desk = w.desk(b.id());
			assertTrue(b.pos().horizDist(desk.pos()) <= NEXT_TO && b.phase() == NpcBrain.Phase.TYPING, b.id() + " stuck at " + b.pos() + " (" + b.phase() + "), desk " + desk.pos());
		}
	}
}
