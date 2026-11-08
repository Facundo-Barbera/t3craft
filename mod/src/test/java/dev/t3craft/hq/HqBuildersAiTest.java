package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.build.CrewPlanner;
import dev.t3craft.build.CrewSchedule;
import dev.t3craft.build.Fronts;
import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.hq.layout.HqLayouts;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * fix/hq-builders-ai on the real HQ plans: every layout with 20 projects, every group as a crew of 4
 * would build it. The final world equals the plan, nothing is placed without support (except what
 * the plan itself leaves floating), every placement is within reach of its builder where it stands,
 * no two builders share an entry or a cell, and the crew walks less than with the layer order.
 */
class HqBuildersAiTest {
	static final int CREW = 4;
	static final Map<String, double[]> METRICS = new HashMap<>();
	static final int[] RELAXED = {0};

	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
	}

	static Stream<String> layouts() {
		return HqLayouts.all().stream().map(HqLayoutInfo::id);
	}

	@ParameterizedTest(name = "{0} with 20 projects")
	@MethodSource("layouts")
	void crewPlanOnEveryLayout(String layout) {
		BuildPlan plan = HqGenerator.plan(HqLayoutsPlanTest.roster(20), null, HqStyles.forLayout(layout)).plan();
		double crew = 0;
		double layered = 0;
		long ms = 0;
		long maxMs = 0;
		RELAXED[0] = 0;
		int entries = 0;
		int forced = 0;
		for (dev.t3craft.build.BuildPlan gp : HqPlans.byGroup(plan).values()) {
			int n = gp.size();
			entries += n;
			int[] x = new int[n];
			int[] y = new int[n];
			int[] z = new int[n];
			boolean[] air = new boolean[n];
			boolean[] decor = new boolean[n];
			for (int i = 0; i < n; i++) {
				var p = gp.get(i);
				x[i] = p.pos().getX();
				y[i] = p.pos().getY();
				z[i] = p.pos().getZ();
				air[i] = p.state().isAir();
				decor[i] = !air[i] && !p.state().canOcclude();
			}
			long t0 = System.nanoTime();
			CrewSchedule s = CrewSchedule.of(gp, CREW);
			long dt = (System.nanoTime() - t0) / 1_000_000;
			ms += dt;
			maxMs = Math.max(maxMs, dt);
			forced += check(layout, x, y, z, air, decor, s);

			int[][] crewLanes = new int[CREW][];
			int[][] starts = new int[CREW][];
			for (int b = 0; b < CREW; b++) {
				crewLanes[b] = s.lane(b);
				starts[b] = s.start(b);
			}
			Fronts f = Fronts.of(gp, CREW);
			int[][] layerLanes = new int[CREW][];
			for (int b = 0; b < CREW; b++) {
				java.util.List<Integer> l = new java.util.ArrayList<>();
				for (int seg = 0; seg < f.segments(); seg++) {
					for (int i : f.lanes(seg)[b]) {
						l.add(i);
					}
				}
				layerLanes[b] = l.stream().mapToInt(Integer::intValue).toArray();
			}
			crew += CrewPlanner.travel(x, y, z, air, crewLanes, starts);
			layered += CrewPlanner.travel(x, y, z, air, layerLanes, starts);
		}
		System.out.printf("[builders-ai] %-9s entries=%6d plan=%4dms (max group %4dms) forced=%3d relaxed=%3d walk crew=%8.0f layers=%8.0f (%.0f%%)%n", layout, entries, ms, maxMs, forced, RELAXED[0], crew, layered,
				100 * crew / layered);
		METRICS.put(layout, new double[] {crew, layered});
		assertTrue(crew < layered, layout + ": crew walks less than the layer order (" + crew + " vs " + layered + ")");
		assertTrue(ms < 20_000, layout + ": planning time " + ms + "ms");
	}

	/** All the per-schedule invariants; returns how many entries were forced. */
	static int check(String ctx, int[] x, int[] y, int[] z, boolean[] air, boolean[] decor, CrewSchedule s) {
		int n = x.length;
		int minY = Integer.MAX_VALUE;
		for (int v : y) {
			minY = Math.min(minY, v);
		}
		// one owner per entry, every entry exactly once
		int[] laneOf = new int[n];
		int[] stepOf = new int[n];
		java.util.Arrays.fill(laneOf, -1);
		for (int b = 0; b < s.builders(); b++) {
			int[] lane = s.lane(b);
			for (int st = 0; st < lane.length; st++) {
				assertEquals(-1, laneOf[lane[st]], ctx + ": entry " + lane[st] + " in two lanes");
				laneOf[lane[st]] = b;
				stepOf[lane[st]] = st;
			}
		}
		for (int i = 0; i < n; i++) {
			assertTrue(laneOf[i] >= 0, ctx + ": entry " + i + " never placed");
		}
		Map<Long, Integer> world = new HashMap<>();
		Map<Long, Integer> expected = new HashMap<>();
		for (int i = 0; i < n; i++) {
			expected.put(CrewPlanner.key(x[i], y[i], z[i]), i);
		}
		int[][] at = new int[s.builders()][];
		for (int b = 0; b < s.builders(); b++) {
			at[b] = s.start(b);
		}
		boolean[] done = new boolean[n];
		int forced = 0;
		int relaxed = 0;
		for (int r = 0; r < n; r++) {
			relaxed += s.relaxed(s.orderAt(r)) ? 1 : 0;
			int i = s.orderAt(r);
			for (int d : s.deps(i)) {
				assertTrue(done[d], ctx + ": dependency " + d + " of " + i + " not done first");
			}
			long k = CrewPlanner.key(x[i], y[i], z[i]);
			int b = laneOf[i];
			int[] st = s.stand(b, stepOf[i]);
			at[b] = st;
			for (int o = 0; o < s.builders(); o++) {
				if (o != b) {
					boolean sameBody = at[o][0] == st[0] && at[o][2] == st[2] && Math.abs(at[o][1] - st[1]) < 2;
					assertFalse(sameBody, ctx + ": builders " + b + " and " + o + " in one cell " + java.util.Arrays.toString(st));
				}
			}
			if (!air[i]) {
				assertTrue(CrewPlanner.inReach(st[0], st[1], st[2], x[i], y[i], z[i]), ctx + ": entry " + i + " out of reach from " + java.util.Arrays.toString(st));
				assertFalse(st[0] == x[i] && st[2] == z[i] && (st[1] == y[i] || st[1] + 1 == y[i]), ctx + ": placing into its own body");
				if (s.forced(i)) {
					forced++;
				} else if (y[i] != minY) {
					boolean sup = false;
					for (int[] f : new int[][] {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}}) {
						Integer w = world.get(CrewPlanner.key(x[i] + f[0], y[i] + f[1], z[i] + f[2]));
						if (w != null && !air[w]) {
							sup = true;
							break;
						}
					}
					assertTrue(sup, ctx + ": entry " + i + " placed without support");
				}
				if (decor[i] && !s.forced(i) && !s.relaxed(i)) {
					for (int[] f : new int[][] {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}}) {
						long q = CrewPlanner.key(x[i] + f[0], y[i] + f[1], z[i] + f[2]);
						Integer fin = expected.get(q);
						if (fin != null && !air[fin] && !decor[fin]) {
							assertEquals(fin, world.get(q), ctx + ": decoration " + i + " before its solid neighbour");
						}
					}
				}
			}
			world.put(k, i);
			done[i] = true;
		}
		assertEquals(expected, world, ctx + ": final world equals the plan");
		RELAXED[0] += relaxed;
		return forced;
	}

	@Test
	void sameScheduleEveryTime() {
		BuildPlan plan = HqGenerator.plan(HqLayoutsPlanTest.roster(4), null, HqStyles.forLayout("classic")).plan();
		for (dev.t3craft.build.BuildPlan gp : HqPlans.byGroup(plan).values()) {
			CrewSchedule a = CrewSchedule.of(gp, CREW);
			CrewSchedule b = CrewSchedule.of(gp, CREW);
			for (int k = 0; k < CREW; k++) {
				assertTrue(java.util.Arrays.equals(a.lane(k), b.lane(k)), "deterministic lanes");
			}
		}
	}
}
