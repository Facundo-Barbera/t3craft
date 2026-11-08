package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** A* over real collision shapes, collision-resolved walking, budget, re-planning and unsticking. */
class NavigationTest {
	static final int Y = 64;

	/** Flat floor 0..16 × -8..8 with feet at y=64. */
	static CellWorld field() {
		return new CellWorld().floor(-2, -8, 18, 8, Y);
	}

	/** A 2-high wall along x = {@code x} from z=-8..8, except the cells in {@code gaps}. */
	static CellWorld wallAcross(CellWorld w, int x, int... gaps) {
		outer:
		for (int z = -8; z <= 8; z++) {
			for (int g : gaps) {
				if (g == z) {
					continue outer;
				}
			}
			w.wall(x, Y, z);
		}
		return w;
	}

	static Route found(Navigation nav, Pos from, Pos to) {
		Route r = nav.route(from, to, null);
		assertEquals(Route.Status.FOUND, r.status(), "route " + from + " → " + to);
		return r;
	}

	/** Walk the route with Motion + collision; asserts the body never overlaps a block. Returns the end position. */
	static Pos walk(Collision c, List<Pos> route) {
		Motion m = new Motion();
		m.snapYaw(route.getFirst().yawTo(route.get(1)));
		m.walk(route, 0f);
		Pos p = route.getFirst();
		// enough ticks for the route's length at walking pace (twice over), at least 2000
		double length = 0;
		for (int i = 0; i + 1 < route.size(); i++) {
			length += Math.sqrt(route.get(i).distSqr(route.get(i + 1)));
		}
		int maxTicks = Math.max(2000, (int) Math.ceil(2 * length / Motion.SPEED));
		for (int i = 0; i < maxTicks && m.walking(); i++) {
			p = m.step(p, null, c::move);
			assertFalse(c.embedded(p), "inside a block at " + p);
		}
		assertFalse(m.walking(), "walk ends");
		return p;
	}

	static boolean passesCell(List<Pos> route, int x, int z) {
		for (int i = 0; i + 1 < route.size(); i++) {
			Pos a = route.get(i);
			Pos b = route.get(i + 1);
			for (int s = 0; s <= 50; s++) {
				double t = s / 50.0;
				if ((int) Math.floor(a.x() + (b.x() - a.x()) * t) == x && (int) Math.floor(a.z() + (b.z() - a.z()) * t) == z) {
					return true;
				}
			}
		}
		return false;
	}

	@Test
	void throughAnOpenableDoor() {
		// LevelBlocks reports hand-openable doors with no boxes: the doorway is just free cells.
		CellWorld w = wallAcross(field(), 4, 0);
		Navigation nav = Navigation.over(w);
		Route r = found(nav, new Pos(0.5, Y, 0.5), new Pos(8.5, Y, -3.5));
		assertTrue(passesCell(r.points(), 4, 0), "goes through the doorway");
		Pos end = walk(new Collision(w), r.points());
		assertTrue(end.horizDist(new Pos(8.5, Y, -3.5)) < 1e-6);
	}

	@Test
	void ironDoorPanelBlocks() {
		CellWorld w = wallAcross(field(), 4, 0);
		w.set(4, Y, 0, CellWorld.IRON_DOOR_WEST).set(4, Y + 1, 0, CellWorld.IRON_DOOR_WEST);
		assertEquals(Route.Status.NONE, Navigation.over(w).route(new Pos(0.5, Y, 0.5), new Pos(8.5, Y, 0.5), null).status());
	}

	@Test
	void upStairsAndASlab() {
		// Two flights: stairs at (4,64) and (5,65), upper floor stands at y=66 from x=6; a 2-block cliff otherwise.
		CellWorld w = new CellWorld().floor(-2, -2, 5, 2, Y);
		for (int z = -2; z <= 2; z++) {
			for (int x = 6; x <= 12; x++) {
				w.set(x, Y, z, CellWorld.FULL).set(x, Y + 1, z, CellWorld.FULL);
			}
		}
		w.set(4, Y, 0, CellWorld.STAIRS_EAST).set(5, Y, 0, CellWorld.FULL).set(5, Y + 1, 0, CellWorld.STAIRS_EAST);
		Navigation nav = Navigation.over(w);
		Route r = found(nav, new Pos(0.5, Y, 0.5), new Pos(10.5, Y + 2, 0.5));
		assertTrue(passesCell(r.points(), 4, 0) && passesCell(r.points(), 5, 0), "takes the stairs");
		Pos end = walk(new Collision(w), r.points());
		assertEquals(Y + 2, end.y(), 1e-6);
		assertTrue(end.horizDist(new Pos(10.5, Y + 2, 0.5)) < 1e-6);

		// A bottom slab is half a step: walkable at 64.5.
		CellWorld s = field();
		s.set(3, Y, 0, CellWorld.BOTTOM_SLAB);
		Collision c = new Collision(s);
		assertEquals(Y + 0.5, c.floor(3, Y, 0), 1e-9);
		Route rs = found(Navigation.over(s), new Pos(0.5, Y, 0.5), new Pos(3.5, Y + 0.5, 0.5));
		assertEquals(Y + 0.5, walk(c, rs.points()).y(), 1e-6);
	}

	@Test
	void carpetIsFloor() {
		CellWorld w = field();
		for (int x = 2; x <= 6; x++) {
			w.set(x, Y, 0, CellWorld.CARPET);
		}
		Collision c = new Collision(w);
		Route r = found(Navigation.over(w), new Pos(0.5, Y, 0.5), new Pos(8.5, Y, 0.5));
		Pos end = walk(c, r.points());
		assertTrue(end.horizDist(new Pos(8.5, Y, 0.5)) < 1e-6, "walks over the carpet: " + end);
	}

	/** A desk row along z=-2..2 at x=4 built from the given shape (two layers for fences: one is enough). */
	static CellWorld desk(List<Box> shape) {
		CellWorld w = field();
		for (int z = -2; z <= 2; z++) {
			w.set(4, Y, z, shape);
		}
		return w;
	}

	@Test
	void goesAroundSlabTrapdoorAndFenceDesks() {
		for (List<Box> shape : List.of(CellWorld.TOP_SLAB, CellWorld.TOP_TRAPDOOR, CellWorld.FENCE_X, CellWorld.FENCE_POST)) {
			CellWorld w = desk(shape);
			Collision c = new Collision(w);
			assertTrue(Double.isNaN(c.floor(4, Y, 0)), "cannot stand in the desk " + shape);
			Route r = found(Navigation.over(w), new Pos(2.5, Y, 0.5), new Pos(6.5, Y, 0.5));
			assertTrue(r.points().size() > 2, "detours around the desk " + shape);
			assertFalse(passesCell(r.points(), 4, 0), "not through the desk " + shape);
			Pos end = walk(c, r.points());
			assertTrue(end.horizDist(new Pos(6.5, Y, 0.5)) < 1e-6);
		}
	}

	@Test
	void goalInsideADeskEndsOnTheNearestFreeSpot() {
		CellWorld w = desk(CellWorld.TOP_SLAB);
		Collision c = new Collision(w);
		Route r = found(Navigation.over(w), new Pos(0.5, Y, 0.5), new Pos(4.5, Y, 0.5));
		Pos end = r.points().getLast();
		assertFalse(c.embedded(end), "stand point is free: " + end);
		assertTrue(end.horizDist(new Pos(4.5, Y, 0.5)) <= 1.01, "next to the desk: " + end);
		assertFalse(c.embedded(walk(c, r.points())));
	}

	@Test
	void waterAndLavaAreNotWalked() {
		CellWorld w = field();
		for (int z = -8; z <= 8; z++) {
			if (z != 6) {
				w.water(4, Y - 1, z).water(4, Y, z);
			}
		}
		Collision c = new Collision(w);
		assertTrue(Double.isNaN(c.floor(4, Y, 0)));
		Route r = found(Navigation.over(w), new Pos(0.5, Y, 0.5), new Pos(8.5, Y, 0.5));
		assertTrue(passesCell(r.points(), 4, 6), "goes round by the only dry cell");
	}

	@Test
	void nodeBudgetSpreadsTheSearchOverTicks() {
		CellWorld w = wallAcross(field(), 8, 8);
		Navigation nav = Navigation.over(w, 50);
		Pos from = new Pos(0.5, Y, -6.5);
		Pos to = new Pos(16.5, Y, -6.5);
		int ticks = 0;
		Route r;
		do {
			nav.beginTick();
			r = nav.route(from, to, null);
			assertTrue(nav.budgetLeft() >= 0 && nav.budgetLeft() <= 50, "never over the tick budget");
			ticks++;
		} while (r.status() == Route.Status.PENDING && ticks < 1000);
		assertEquals(Route.Status.FOUND, r.status());
		assertTrue(ticks > 2, "took several ticks: " + ticks);
		// Once found, it is cached: answered at once, without spending budget.
		nav.beginTick();
		assertEquals(Route.Status.FOUND, nav.route(from, to, null).status());
		assertEquals(50, nav.budgetLeft());
	}

	@Test
	void cachedRouteIsDroppedWhenBlocked() {
		CellWorld w = wallAcross(field(), 4, 0, 5);
		Navigation nav = Navigation.over(w);
		Pos from = new Pos(0.5, Y, 0.5);
		Pos to = new Pos(8.5, Y, 0.5);
		assertTrue(passesCell(found(nav, from, to).points(), 4, 0));
		w.wall(4, Y, 0); // someone closes the near gap
		nav.beginTick();
		Route r = found(nav, from, to);
		assertFalse(passesCell(r.points(), 4, 0));
		assertTrue(passesCell(r.points(), 4, 5), "re-planned through the other gap");
	}

	@Test
	void motionStopsAtAWallAndReportsStuck() {
		CellWorld w = field();
		w.wall(3, Y, 0);
		Collision c = new Collision(w);
		Motion m = new Motion();
		m.snapYaw(-90);
		Pos p = new Pos(0.5, Y, 0.5);
		m.walk(List.of(p, new Pos(6.5, Y, 0.5)), 0f);
		for (int i = 0; i < 80; i++) {
			p = m.step(p, null, c::move);
			assertFalse(c.embedded(p));
		}
		assertTrue(p.x() <= 3 - Collision.HALF + 1e-6, "stopped at the wall face: " + p);
		assertTrue(m.stuckTicks() > NpcBrain.STUCK_TICKS, "reports being stuck");
	}

	@Test
	void collisionStepsUpHalfABlockButNotAWholeOne() {
		CellWorld w = field();
		w.set(2, Y, 0, CellWorld.BOTTOM_SLAB).set(2, Y, 3, CellWorld.FULL);
		Collision c = new Collision(w);
		Pos onSlab = c.move(new Pos(1.5, Y, 0.5), new Pos(2.5, Y, 0.5));
		assertEquals(2.5, onSlab.x(), 1e-6);
		assertEquals(Y + 0.5, onSlab.y(), 1e-6);
		Pos atBlock = c.move(new Pos(1.5, Y, 3.5), new Pos(2.5, Y, 3.5));
		assertEquals(2 - Collision.HALF, atBlock.x(), 1e-6);
		assertEquals(Y, atBlock.y(), 1e-6);
	}

	/** Navigator over a world whose routes lie (straight through anything), but whose moves collide. */
	static NpcBrain.Navigator lying(Collision c) {
		return new NpcBrain.Navigator() {
			@Override
			public @Nullable List<Pos> path(Pos from, Pos to, @Nullable Pos avoid) {
				return List.of(from, to);
			}

			@Override
			public Pos move(Pos from, Pos to) {
				return c.move(from, to);
			}

			@Override
			public boolean embedded(Pos p) {
				return c.embedded(p);
			}

			@Override
			public @Nullable Pos nearestFree(Pos p) {
				return c.nearestFree(p);
			}
		};
	}

	@Test
	void stuckWalkerNeverPassesTheWallAndCalmsDown() {
		CellWorld w = field();
		for (int z = -8; z <= 8; z++) {
			w.wall(5, Y, z);
		}
		Collision c = new Collision(w);
		NpcBrain.Navigator nav = lying(c);
		Anchor desk = Anchor.at(9.5, Y, 0.5, 0);
		NpcBrain.Targets t = new NpcBrain.Targets(desk, null, desk, 2, desk);
		NpcBrain stuck = new NpcBrain("vps:stuck", new Pos(1.5, Y, 0.5));
		int walkingTicks = 0;
		// Placed at a near desk first, then the desk "moves" behind the wall.
		for (int i = 0; i < 5; i++) {
			stuck.tick(Mode.WORKING, false, new NpcBrain.Targets(Anchor.at(1.5, Y, 0.5, 0), null, desk, 2, desk), null, nav);
		}
		for (int i = 0; i < 1500; i++) {
			stuck.tick(Mode.WORKING, false, t, null, nav);
			assertFalse(c.embedded(stuck.pos()), "never inside the wall");
			assertTrue(stuck.pos().x() < 5, "never through the wall: " + stuck.pos());
			if (stuck.walking()) {
				walkingTicks++;
			}
		}
		assertTrue(walkingTicks < 1300, "gives up for a while instead of pushing forever: " + walkingTicks);
	}

	@Test
	void npcInsideABlockIsPushedOut() {
		CellWorld w = desk(CellWorld.TOP_SLAB);
		Navigation nav = Navigation.over(w);
		Anchor inDesk = Anchor.at(4.5, Y, 0.5, 0);
		NpcBrain b = new NpcBrain("vps:in", inDesk.pos());
		b.tick(Mode.WORKING, false, new NpcBrain.Targets(inDesk, null, inDesk, 2, inDesk), null, nav);
		assertFalse(nav.embedded(b.pos()), "placed outside the desk: " + b.pos());
		assertTrue(b.pos().horizDist(inDesk.pos()) <= 1.01);
		for (int i = 0; i < 200; i++) {
			nav.beginTick();
			b.tick(Mode.WORKING, false, new NpcBrain.Targets(inDesk, null, inDesk, 2, inDesk), null, nav);
			assertFalse(nav.embedded(b.pos()));
		}
		assertEquals(NpcBrain.Phase.TYPING, b.phase(), "types from the free spot next to the desk");

		// A block placed on a standing NPC: it is moved to the nearest free cell.
		w.set(b.pos().blockX(), Y, b.pos().blockZ(), CellWorld.FULL);
		for (int i = 0; i < NpcBrain.EMBED_CHECK_TICKS + 1; i++) {
			nav.beginTick();
			b.tick(Mode.WORKING, false, new NpcBrain.Targets(inDesk, null, inDesk, 2, inDesk), null, nav);
		}
		assertFalse(nav.embedded(b.pos()), "pushed out: " + b.pos());
	}

	@Test
	void brainRePlansWhenTheWayIsBlockedMidWalk() {
		CellWorld w = wallAcross(field(), 6, 0, -6);
		Navigation nav = Navigation.over(w);
		Anchor home = Anchor.at(1.5, Y, 0.5, 0);
		Anchor desk = Anchor.at(10.5, Y, 0.5, 0);
		NpcBrain b = new NpcBrain("vps:replan", home.pos());
		NpcBrain.Targets atHome = new NpcBrain.Targets(home, null, home, 2, home);
		NpcBrain.Targets atDesk = new NpcBrain.Targets(desk, null, home, 2, desk);
		nav.beginTick();
		b.tick(Mode.WORKING, false, atHome, null, nav);
		boolean closed = false;
		for (int i = 0; i < 1200; i++) {
			nav.beginTick();
			b.tick(Mode.WORKING, false, atDesk, null, nav);
			if (!closed && b.pos().x() > 3.5) {
				w.wall(6, Y, 0); // the doorway is closed in front of it
				closed = true;
			}
			assertFalse(nav.embedded(b.pos()), "never inside a block: " + b.pos());
		}
		assertTrue(closed);
		assertTrue(b.pos().horizDist(desk.pos()) < 0.6, "got to the desk the long way: " + b.pos());
		assertEquals(NpcBrain.Phase.TYPING, b.phase());
	}

	@Test
	void walksToThePlayerInsideAnHq() {
		// Room with walls and a door; the approach spot is behind a desk row.
		CellWorld w = wallAcross(field(), 6, 3);
		for (int z = -2; z <= 2; z++) {
			w.set(9, Y, z, CellWorld.TOP_SLAB);
		}
		Navigation nav = Navigation.over(w);
		Collision c = new Collision(w);
		Route r = found(nav, new Pos(1.5, Y, 0.5), new Pos(11.5, Y, 0.5));
		assertFalse(passesCell(r.points(), 9, 0));
		Pos end = walk(c, r.points());
		assertTrue(end.horizDist(new Pos(11.5, Y, 0.5)) < 1e-6);
	}

	@Test
	void noRouteMeansNoWalkingThroughWalls() {
		// The desk is in a closed room: the NPC stays where it is instead of walking straight at it.
		CellWorld w = field();
		for (int d = -2; d <= 2; d++) {
			w.wall(8 + d, Y, -2).wall(8 + d, Y, 2).wall(6, Y, d).wall(10, Y, d);
		}
		Navigation nav = Navigation.over(w);
		Anchor start = Anchor.at(1.5, Y, 0.5, 0);
		Anchor desk = Anchor.at(8.5, Y, 0.5, 0);
		NpcBrain b = new NpcBrain("vps:closed", start.pos());
		nav.beginTick();
		b.tick(Mode.WORKING, false, new NpcBrain.Targets(start, null, start, 2, start), null, nav);
		for (int i = 0; i < 300; i++) {
			nav.beginTick();
			b.tick(Mode.WORKING, false, new NpcBrain.Targets(desk, null, start, 2, desk), null, nav);
			assertFalse(b.walking(), "does not walk without a route");
		}
		assertEquals(start.pos(), b.pos());
	}

	@Test
	void departureWalksOutThroughTheDoorNeverThroughTheWall() {
		CellWorld w = wallAcross(field(), 4, -6);
		Navigation nav = Navigation.over(w);
		Departure d = new Departure(new Pos(1.5, Y, 0.5), -90, Anchor.at(8.5, Y, 0.5, 0));
		boolean crossed = false;
		for (int i = 0; i < Departure.WAVE_TICKS + Departure.WALK_TICKS + 5; i++) {
			nav.beginTick();
			d.tick(nav, null);
			assertFalse(nav.embedded(d.pos()), "never inside a block: " + d.pos());
			if (d.pos().x() >= 4 && d.pos().x() < 5) {
				assertTrue(d.pos().z() >= -6 && d.pos().z() < -5, "in the wall column only at the doorway: " + d.pos());
			}
			crossed |= d.pos().x() >= 5;
		}
		assertTrue(crossed, "got out through the doorway: " + d.pos());

		// Walled in: no route, it fades out where it stands.
		CellWorld closed = wallAcross(field(), 4);
		Navigation nc = Navigation.over(closed);
		Departure stay = new Departure(new Pos(1.5, Y, 0.5), -90, Anchor.at(8.5, Y, 0.5, 0));
		for (int i = 0; i < Departure.WAVE_TICKS + Departure.WALK_TICKS + 5; i++) {
			nc.beginTick();
			stay.tick(nc, null);
		}
		assertEquals(new Pos(1.5, Y, 0.5), stay.pos());
		assertNotEquals(Departure.Stage.WALK, stay.stage());
	}
}
