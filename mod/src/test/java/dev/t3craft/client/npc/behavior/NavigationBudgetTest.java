package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Pathfinding work counts (fix/hq-fps). The FPS drop after the HQ was built: every NPC got a desk
 * inside the building; when it had no route there, each one ran a full A* every
 * {@link Navigation#MISS_TTL} ticks forever, which kept the shared node budget saturated on the
 * render thread. Misses now back off exponentially and the tick also has a time cap.
 */
class NavigationBudgetTest {
	static final int Y = 64;

	/** A wide field with a closed 2-high room (the "HQ" nobody can enter) around {@code (20, 0)}. */
	static CellWorld closedRoom(int half) {
		CellWorld w = new CellWorld().floor(-half, -half, half, half, Y);
		for (int x = 16; x <= 24; x++) {
			w.wall(x, Y, -4);
			w.wall(x, Y, 4);
		}
		for (int z = -4; z <= 4; z++) {
			w.wall(16, Y, z);
			w.wall(24, Y, z);
		}
		return w;
	}

	static List<Pos> npcs(int n) {
		List<Pos> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(new Pos(-10.5 + i * 2, Y, -10.5));
		}
		return out;
	}

	@Test
	void backoffDoublesUpToTheCap() {
		assertEquals(40, Navigation.missTtl(1));
		assertEquals(80, Navigation.missTtl(2));
		assertEquals(160, Navigation.missTtl(3));
		assertEquals(640, Navigation.missTtl(5));
		assertEquals(Navigation.MISS_TTL_MAX, Navigation.missTtl(6));
		assertEquals(Navigation.MISS_TTL_MAX, Navigation.missTtl(50));
	}

	@Test
	void unreachableDesksStopEatingTheBudget() {
		CellWorld w = closedRoom(30);
		Navigation nav = Navigation.over(w);
		Pos desk = new Pos(20.5, Y, 0.5);
		List<Pos> walkers = npcs(10);
		int ticks = 2_400; // two minutes
		for (int t = 0; t < ticks; t++) {
			nav.beginTick();
			for (Pos p : walkers) {
				// worst case: every NPC asks every tick (the brain asks every RETRY_TICKS)
				Route r = nav.route(p, desk, null);
				assertTrue(r.status() != Route.Status.FOUND, "the room is closed");
			}
		}
		long naive = (long) walkers.size() * (ticks / Navigation.MISS_TTL);
		// 40 + 80 + 160 + 320 + 640 + 1200 > 2400: at most 7 searches per NPC in two minutes
		assertTrue(nav.searches() <= walkers.size() * 7L, "searches: " + nav.searches() + " (fixed 40-tick retry: " + naive + ")");
		assertTrue(nav.searches() * 5 < naive, "at least 5× fewer searches than before");
		System.out.printf("[fix/hq-fps] unreachable desks, 10 NPCs, 2 min: %d searches, %d nodes (fixed retry: ~%d searches, ~%d nodes)%n",
				nav.searches(), nav.nodes(), naive, nav.nodes() / Math.max(1, nav.searches()) * naive);
	}

	@Test
	void aFoundRouteResetsTheStreak() {
		CellWorld w = closedRoom(30);
		Navigation nav = Navigation.over(w);
		Pos from = new Pos(-10.5, Y, -10.5);
		Pos desk = new Pos(20.5, Y, 0.5);
		for (int t = 0; t < 400; t++) {
			nav.beginTick();
			nav.route(from, desk, null);
		}
		long before = nav.searches();
		w.clear(16, Y, 0).clear(16, Y + 1, 0); // the door opens
		nav.blocked(from, new Pos(-9.5, Y, -10.5), desk); // a walker got stuck: retry misses now
		nav.beginTick();
		assertEquals(Route.Status.FOUND, nav.route(from, desk, null).status());
		assertEquals(before + 1, nav.searches());
	}

	@Test
	void timeCapDefersTheRestOfTheTick() {
		// Each clock read advances 1 ms: after 2 chunks (2 ms) the tick is spent.
		AtomicLong clock = new AtomicLong();
		CellWorld w = closedRoom(40);
		Navigation nav = Navigation.over(w, Navigation.TICK_BUDGET, Navigation.TIME_BUDGET_NS, () -> clock.addAndGet(1_000_000));
		Pos desk = new Pos(20.5, Y, 0.5);
		nav.beginTick();
		assertEquals(Route.Status.PENDING, nav.route(new Pos(-30.5, Y, -30.5), desk, null).status());
		assertTrue(nav.spentThisTick() >= Navigation.TIME_BUDGET_NS);
		int left = nav.budgetLeft();
		assertTrue(left > 0, "stopped by time, not nodes");
		assertEquals(Route.Status.PENDING, nav.route(new Pos(-28.5, Y, -30.5), desk, null).status(), "next NPC waits");
		assertEquals(left, nav.budgetLeft(), "and spends nothing");
	}

	@Test
	void timeCapNeverStarvesSmallSearches() {
		// A slow clock (10 ms per read) still lets a short route finish in one tick.
		AtomicLong clock = new AtomicLong();
		CellWorld w = new CellWorld().floor(-2, -8, 18, 8, Y);
		Navigation nav = Navigation.over(w, Navigation.TICK_BUDGET, Navigation.TIME_BUDGET_NS, () -> clock.addAndGet(10_000_000));
		nav.beginTick();
		assertEquals(Route.Status.FOUND, nav.route(new Pos(0.5, Y, 0.5), new Pos(8.5, Y, 0.5), null).status());
	}

	/** Microbenchmark (printed, not asserted beyond sanity): cost of one failed search over collision shapes. */
	@Test
	void microbenchFailedSearch() {
		CellWorld w = closedRoom(40);
		Pos desk = new Pos(20.5, Y, 0.5);
		long best = Long.MAX_VALUE;
		long nodes = 0;
		for (int rep = 0; rep < 8; rep++) {
			Navigation nav = Navigation.over(w, Integer.MAX_VALUE / 2);
			nav.beginTick();
			long t0 = System.nanoTime();
			while (nav.route(new Pos(-30.5, Y, -30.5), desk, null).status() == Route.Status.PENDING) {
				// SLICE nodes per call
			}
			best = Math.min(best, System.nanoTime() - t0);
			nodes = nav.nodes();
		}
		assertTrue(nodes > 1_000, "a failed search explores the whole box: " + nodes);
		System.out.printf("[fix/hq-fps] one failed search: %d nodes in %.2f ms (%.0f ns/node)%n", nodes, best / 1e6, (double) best / nodes);
	}
}
