package dev.t3craft.client.npc.behavior;

import static dev.t3craft.client.npc.behavior.NavigationTest.Y;
import static dev.t3craft.client.npc.behavior.NavigationTest.found;
import static dev.t3craft.client.npc.behavior.NavigationTest.passesCell;
import static dev.t3craft.client.npc.behavior.NavigationTest.walk;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** fix/npc-enter-hq: hops up a whole block, routes through the entrance, re-planning when the base changes. */
class HopViaReplanTest {
	/** Field whose half x ≥ 5 is a whole block higher (a terrace with no step). */
	static CellWorld terrace() {
		CellWorld w = NavigationTest.field();
		for (int x = 5; x <= 18; x++) {
			for (int z = -8; z <= 8; z++) {
				w.set(x, Y, z, CellWorld.FULL);
			}
		}
		return w;
	}

	@Test
	void hopsUpAWholeBlockWhenThereIsNoStep() {
		CellWorld w = terrace();
		Route r = found(Navigation.over(w), new Pos(1.5, Y, 0.5), new Pos(9.5, Y + 1, 0.5));
		Pos end = walk(new Collision(w), r.points());
		assertEquals(9.5, end.x(), 1e-6);
		assertEquals(Y + 1, end.y(), 1e-6);
		// and back down
		Pos back = walk(new Collision(w), found(Navigation.over(w), end, new Pos(1.5, Y, 0.5)).points());
		assertEquals(1.5, back.x(), 1e-6);
		assertEquals(Y, back.y(), 1e-6);
	}

	@Test
	void noHopUnderALowCeiling() {
		CellWorld w = terrace();
		for (int z = -8; z <= 8; z++) {
			w.set(4, Y + 2, z, CellWorld.FULL); // a lintel over the last low cell: no room to rise
		}
		assertEquals(Route.Status.NONE, Navigation.over(w).route(new Pos(1.5, Y, 0.5), new Pos(9.5, Y + 1, 0.5), null).status());
	}

	@Test
	void hopsAreTheLastResortNotAShortcutOverDesks() {
		// A row of full-block desks across the room with a gap at z = 4: walk round, never over.
		CellWorld w = NavigationTest.field();
		for (int z = -8; z <= 8; z++) {
			if (z != 4) {
				w.set(4, Y, z, CellWorld.FULL);
			}
		}
		Route r = found(Navigation.over(w), new Pos(2.5, Y, 0.5), new Pos(6.5, Y, 0.5));
		assertFalse(passesCell(r.points(), 4, 0), "not over the desk");
		for (Pos p : r.points()) {
			assertEquals(Y, p.y(), 1e-9, "stays on the floor");
		}
	}

	@Test
	void longRoutesGoThroughTheEntrance() {
		// A walled yard whose only opening is far away (z = 40): out of reach of one bounded search.
		CellWorld w = new CellWorld().floor(-4, -8, 14, 44, Y);
		for (int z = -8; z <= 44; z++) {
			if (z != 40) {
				w.wall(4, Y, z);
			}
		}
		for (int x = 4; x <= 14; x++) {
			w.wall(x, Y, -8).wall(x, Y, 44);
		}
		Navigation nav = Navigation.over(w);
		Pos from = new Pos(1.5, Y, 0.5);
		Pos to = new Pos(8.5, Y, 0.5);
		assertEquals(Route.Status.NONE, nav.route(from, to, null).status(), "no direct route within the search box");
		nav.setVia(new Pos(4.5, Y, 40.5));
		Route r = HqEntryTest.routeOverTicks(nav, from, to);
		assertEquals(Route.Status.FOUND, r.status());
		Pos end = walk(new Collision(w), r.points());
		assertEquals(8.5, end.x(), 1e-6);
		assertEquals(0.5, end.z(), 1e-6);
	}

	@Test
	void replanAfterTheBaseChangesInsteadOfWaiting() {
		CellWorld w = NavigationTest.wallAcross(NavigationTest.field(), 5);
		Navigation nav = Navigation.over(w);
		Anchor home = Anchor.at(1.5, Y, 0.5, 0);
		Anchor desk = Anchor.at(9.5, Y, 0.5, 0);
		NpcBrain b = new NpcBrain("npc", home.pos());
		nav.beginTick();
		b.tick(Mode.WORKING, false, new NpcBrain.Targets(home, null, home, 2, home), null, nav);
		NpcBrain.Targets atDesk = new NpcBrain.Targets(desk, null, home, 2, desk);
		for (int t = 0; t < 5; t++) {
			nav.beginTick();
			b.tick(Mode.WORKING, false, atDesk, null, nav);
		}
		assertFalse(b.walking(), "no way through the wall yet");
		// The wall gets a doorway (the base is built): the director invalidates and re-plans everyone.
		w.clear(5, Y, 0).clear(5, Y + 1, 0);
		nav.invalidate();
		b.replan();
		nav.beginTick();
		b.tick(Mode.WORKING, false, atDesk, null, nav);
		assertTrue(b.walking(), "walks right away instead of waiting out the retry");
	}

	@Test
	void noRouteForLongNudgesToAFreeCellWithoutTeleporting() {
		// Boxed in: the NPC stands off-centre against a wall in a closed room and never has a route.
		CellWorld w = NavigationTest.field();
		for (int z = -3; z <= 3; z++) {
			w.wall(-1, Y, z).wall(3, Y, z);
		}
		for (int x = -1; x <= 3; x++) {
			w.wall(x, Y, -3).wall(x, Y, 3);
		}
		Navigation nav = Navigation.over(w);
		Pos start = new Pos(0.31, Y, 0.5);
		NpcBrain b = new NpcBrain("npc", start);
		Anchor here = new Anchor(start, 0);
		Anchor desk = Anchor.at(9.5, Y, 0.5, 0);
		nav.beginTick();
		b.tick(Mode.WORKING, false, new NpcBrain.Targets(here, null, here, 2, here), null, nav);
		Pos last = b.pos();
		boolean moved = false;
		for (int t = 0; t < NpcBrain.RETRY_TICKS * (NpcBrain.NO_ROUTE_NUDGE + 2); t++) {
			nav.beginTick();
			b.tick(Mode.WORKING, false, new NpcBrain.Targets(desk, null, here, 2, desk), null, nav);
			assertTrue(b.pos().horizDist(last) < 0.3, "small steps only, no teleport: " + last + " → " + b.pos());
			moved |= b.pos().horizDist(start) > 0.05;
			last = b.pos();
			assertFalse(new Collision(w).embedded(b.pos()));
		}
		assertTrue(moved, "nudged off the wall after a while");
		assertTrue(b.pos().x() > -1 && b.pos().x() < 3, "still inside the room");
	}
}
