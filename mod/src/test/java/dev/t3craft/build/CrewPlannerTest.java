package dev.t3craft.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link CrewPlanner} and {@link CrewCursor} on synthetic plans (plain ints, no Minecraft). */
class CrewPlannerTest {
	/** A synthetic plan: parallel arrays. */
	record P(int[] x, int[] y, int[] z, boolean[] air, boolean[] decor) {
		int n() {
			return x.length;
		}

		CrewSchedule plan(int k) {
			return CrewPlanner.plan(x, y, z, air, decor, k);
		}
	}

	static final class B {
		final List<int[]> e = new ArrayList<>();

		B add(int x, int y, int z, int kind) { // 0 solid, 1 air, 2 decor
			e.add(new int[] {x, y, z, kind});
			return this;
		}

		P build() {
			int n = e.size();
			P p = new P(new int[n], new int[n], new int[n], new boolean[n], new boolean[n]);
			for (int i = 0; i < n; i++) {
				int[] v = e.get(i);
				p.x[i] = v[0];
				p.y[i] = v[1];
				p.z[i] = v[2];
				p.air[i] = v[3] == 1;
				p.decor[i] = v[3] == 2;
			}
			return p;
		}
	}

	/** A hollow house w×d, h high, floor, walls with windows (decor), a roof, lanterns hanging under it, layer order. */
	static P house(int w, int d, int h) {
		B b = new B();
		for (int y = 0; y <= h + 1; y++) {
			for (int z = 0; z < d; z++) {
				for (int x = 0; x < w; x++) {
					boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
					if (y == 0 || y == h + 1) {
						b.add(x, y, z, 0);
					} else if (edge) {
						b.add(x, y, z, y == 2 && x % 3 == 1 ? 2 : 0);
					} else if (y == h && x % 4 == 2 && z % 4 == 2) {
						b.add(x, y, z, 2); // lantern hanging from the roof
					} else {
						b.add(x, y, z, 1);
					}
				}
			}
		}
		return b.build();
	}

	@Test
	void houseInvariants() {
		P p = house(24, 18, 5);
		for (int k : new int[] {1, 2, 4}) {
			CrewSchedule s = p.plan(k);
			assertEquals(0, checkInvariants(p, s), "a closed house has nothing floating");
		}
	}

	@Test
	void hangingLanternWaitsForTheRoof() {
		P p = house(10, 10, 4);
		CrewSchedule s = p.plan(2);
		for (int i = 0; i < p.n(); i++) {
			if (p.decor[i] && p.y[i] == 4) {
				int roof = -1;
				for (int j = 0; j < p.n(); j++) {
					if (p.x[j] == p.x[i] && p.z[j] == p.z[i] && p.y[j] == 5) {
						roof = j;
					}
				}
				assertTrue(s.rank(roof) < s.rank(i), "lantern after the roof block it hangs from");
			}
		}
	}

	@Test
	void notGlobalLayers() {
		// Two towers far apart, one builder each: each builds its tower up before the other is done,
		// i.e. layers interleave instead of a global barrier.
		B b = new B();
		for (int y = 0; y < 6; y++) {
			for (int x = 0; x < 3; x++) {
				for (int z = 0; z < 3; z++) {
					b.add(x, y, z, 0);
					b.add(x + 30, y, z, 0);
				}
			}
		}
		P p = b.build();
		CrewSchedule s = p.plan(2);
		checkInvariants(p, s);
		for (int lane = 0; lane < 2; lane++) {
			int[] l = s.lane(lane);
			int side = p.x[l[0]] >= 30 ? 1 : 0;
			for (int i : l) {
				assertEquals(side, p.x[i] >= 30 ? 1 : 0, "each builder keeps to its own zone");
			}
		}
	}

	@Test
	void floatingBlocksGoLastNotDeadlock() {
		B b = new B();
		for (int x = 0; x < 5; x++) {
			b.add(x, 0, 0, 0);
		}
		b.add(2, 5, 0, 0); // floating in the plan
		P p = b.build();
		CrewSchedule s = p.plan(2);
		assertEquals(1, checkInvariants(p, s));
		assertEquals(5, s.orderAt(5));
	}

	@Test
	void clearThenPlaceKeepsOrder() {
		B b = new B();
		for (int x = 0; x < 8; x++) {
			b.add(x, 0, 0, 1).add(x, 0, 0, 0).add(x, 1, 0, 0).add(x, 1, 0, 1);
		}
		P p = b.build();
		CrewSchedule s = p.plan(3);
		checkInvariants(p, s);
	}

	@Test
	void randomPlansKeepInvariants() {
		Random r = new Random(7);
		for (int round = 0; round < 40; round++) {
			B b = new B();
			int n = 50 + r.nextInt(400);
			for (int i = 0; i < n; i++) {
				b.add(r.nextInt(16), r.nextInt(6), r.nextInt(16), r.nextInt(10) < 2 ? 1 : r.nextInt(10) < 2 ? 2 : 0);
			}
			P p = b.build();
			checkInvariants(p, p.plan(1 + r.nextInt(4)));
		}
	}

	@Test
	void walksLessThanLayers() {
		P p = house(40, 30, 6);
		int k = 4;
		CrewSchedule s = p.plan(k);
		int[][] lanes = new int[k][];
		int[][] starts = new int[k][];
		for (int b = 0; b < k; b++) {
			lanes[b] = s.lane(b);
			starts[b] = s.start(b);
		}
		long[] key = new long[p.n()];
		for (int i = 0; i < p.n(); i++) {
			key[i] = CrewPlanner.key(p.x[i], p.y[i], p.z[i]);
		}
		Fronts f = Fronts.split(p.y, key, k);
		int[][] layer = new int[k][];
		for (int b = 0; b < k; b++) {
			List<Integer> l = new ArrayList<>();
			for (int seg = 0; seg < f.segments(); seg++) {
				for (int i : f.lanes(seg)[b]) {
					l.add(i);
				}
			}
			layer[b] = l.stream().mapToInt(Integer::intValue).toArray();
		}
		double crew = CrewPlanner.travel(p.x, p.y, p.z, p.air, lanes, starts);
		double layers = CrewPlanner.travel(p.x, p.y, p.z, p.air, layer, starts);
		assertTrue(crew < layers, crew + " vs " + layers);
	}

	// ---- CrewCursor ----------------------------------------------------------------------------------

	@Test
	void cursorAppliesEachOnceRespectingDepsAndWalks() {
		P p = house(16, 12, 4);
		CrewSchedule s = p.plan(4);
		Random r = new Random(3);
		CrewCursor c = new CrewCursor(s, 0, 8);
		boolean[] done = new boolean[p.n()];
		int[] walkStarted = new int[4];
		c.walker((front, index, step, ticks) -> {
			walkStarted[front]++;
			assertEquals(s.walk(front, step), ticks);
		});
		int ticks = 0;
		while (c.state() != JobState.COMPLETED) {
			assertTrue(++ticks < 200_000, "progress");
			c.tick(r.nextInt(6) * 0.75, (front, i) -> {
				assertEquals(s.owner(i), front);
				assertFalse(done[i], "applied twice " + i);
				for (int d : s.deps(i)) {
					assertTrue(done[d], "dependency first");
				}
				done[i] = true;
				return BuildCursor.Outcome.PLACED;
			});
		}
		for (boolean d : done) {
			assertTrue(d);
		}
		assertEquals(p.n(), c.placed());
		assertTrue(Arrays.stream(walkStarted).sum() > 0, "builders walk between spots");
	}

	@Test
	void cursorWaitsForTheWalk() {
		B b = new B();
		b.add(0, 0, 0, 0).add(20, 0, 0, 0);
		P p = b.build();
		CrewSchedule s = p.plan(1);
		int w = s.walk(0, 0) + s.walk(0, 1);
		assertTrue(w > 0);
		CrewCursor c = new CrewCursor(s);
		int ticks = 0;
		while (c.state() != JobState.COMPLETED) {
			ticks++;
			c.tick(100, (f, i) -> BuildCursor.Outcome.PLACED);
		}
		assertTrue(ticks >= w, "placements wait for the builder to get there: " + ticks + " < " + w);
	}

	@Test
	void resumeSkipsWithoutWalking() {
		P p = house(12, 12, 3);
		CrewSchedule s = p.plan(2);
		CrewCursor c = new CrewCursor(s);
		c.probe(i -> true); // everything already in the world
		int ticks = 0;
		while (c.state() != JobState.COMPLETED) {
			ticks++;
			c.tick(8, (f, i) -> BuildCursor.Outcome.SKIPPED);
		}
		assertEquals(p.n(), c.skipped());
		assertTrue(ticks <= p.n() / BuildCursor.DEFAULT_MAX_SKIPS_PER_TICK + 3, "no walking for what is there: " + ticks);
	}

	@Test
	void blockedFrontPausesJobAndResumeRetries() {
		P p = house(10, 10, 3);
		CrewSchedule s = p.plan(2);
		CrewCursor c = new CrewCursor(s, 64, 64);
		boolean[] block = {true};
		boolean[] done = new boolean[p.n()];
		int ticks = 0;
		while (c.state() != JobState.PAUSED && ticks++ < 10_000) {
			c.tick(4, (f, i) -> {
				if (block[0] && c.done() > 20) {
					return BuildCursor.Outcome.BLOCKED;
				}
				done[i] = true;
				return BuildCursor.Outcome.PLACED;
			});
		}
		assertTrue(c.blocked());
		int[] pending = c.pendingInSegment();
		assertTrue(pending.length > 0);
		block[0] = false;
		assertTrue(c.resume());
		while (c.state() != JobState.COMPLETED && ticks++ < 100_000) {
			c.tick(4, (f, i) -> {
				assertFalse(done[i]);
				done[i] = true;
				return BuildCursor.Outcome.PLACED;
			});
		}
		assertEquals(JobState.COMPLETED, c.state());
	}

	@Test
	void completesOnlyAfterTheLastPlacement() {
		// The desk NPCs move in when the site module is "built", i.e. when the job completes: that
		// must not happen before the last block of the structure is down.
		P p = house(12, 10, 4);
		CrewCursor c = new CrewCursor(p.plan(4));
		int[] placedCount = {0};
		while (c.state() != JobState.COMPLETED) {
			assertTrue(placedCount[0] < p.n() || c.state() == JobState.COMPLETED);
			c.tick(5, (f, i) -> {
				assertTrue(c.state() == JobState.RUNNING, "still running while placing");
				placedCount[0]++;
				return BuildCursor.Outcome.PLACED;
			});
			if (placedCount[0] < p.n()) {
				assertTrue(c.state() != JobState.COMPLETED, "not complete before the last block");
			}
		}
		assertEquals(p.n(), placedCount[0]);
	}

	/** Same checks as {@code HqBuildersAiTest.check}, on a synthetic plan; returns the forced count. */
	static int checkInvariants(P p, CrewSchedule s) {
		int n = p.n();
		int minY = Arrays.stream(p.y).min().orElse(0);
		int[] laneOf = new int[n];
		int[] stepOf = new int[n];
		Arrays.fill(laneOf, -1);
		for (int b = 0; b < s.builders(); b++) {
			int[] lane = s.lane(b);
			for (int st = 0; st < lane.length; st++) {
				assertEquals(-1, laneOf[lane[st]], "one owner");
				laneOf[lane[st]] = b;
				stepOf[lane[st]] = st;
			}
		}
		Map<Long, Integer> world = new HashMap<>();
		Map<Long, Integer> expected = new HashMap<>();
		for (int i = 0; i < n; i++) {
			assertTrue(laneOf[i] >= 0);
			expected.put(CrewPlanner.key(p.x[i], p.y[i], p.z[i]), i);
		}
		int[][] at = new int[s.builders()][];
		for (int b = 0; b < s.builders(); b++) {
			at[b] = s.start(b);
		}
		boolean[] done = new boolean[n];
		int forced = 0;
		int[][] faces = {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};
		for (int r = 0; r < n; r++) {
			int i = s.orderAt(r);
			for (int d : s.deps(i)) {
				assertTrue(done[d]);
			}
			int b = laneOf[i];
			int[] st = s.stand(b, stepOf[i]);
			at[b] = st;
			for (int o = 0; o < s.builders(); o++) {
				if (o != b) {
					assertFalse(at[o][0] == st[0] && at[o][2] == st[2] && Math.abs(at[o][1] - st[1]) < 2, "two builders in one cell");
				}
			}
			if (!p.air[i]) {
				assertTrue(CrewPlanner.inReach(st[0], st[1], st[2], p.x[i], p.y[i], p.z[i]), "in reach");
				if (s.forced(i)) {
					forced++;
				} else if (p.y[i] != minY) {
					boolean sup = false;
					for (int[] f : faces) {
						Integer w = world.get(CrewPlanner.key(p.x[i] + f[0], p.y[i] + f[1], p.z[i] + f[2]));
						sup |= w != null && !p.air[w];
					}
					assertTrue(sup, "supported " + i);
				}
			}
			world.put(CrewPlanner.key(p.x[i], p.y[i], p.z[i]), i);
			done[i] = true;
		}
		assertEquals(expected, world, "final world equals the plan");
		return forced;
	}
}
