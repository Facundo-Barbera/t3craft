package dev.t3craft.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** fix/hq-build-parallel: parts in parallel keep the sequential result and never meet. */
class PartScheduleTest {
	private static int[] box(int x0, int y0, int z0, int x1, int y1, int z1) {
		return new int[] {x0, y0, z0, x1, y1, z1};
	}

	@Test
	void touchingPartsWaitFarPartsDoNot() {
		PartSchedule s = PartSchedule.of(List.of(
				box(0, 0, 0, 9, 9, 9), // 0
				box(20, 0, 0, 29, 9, 9), // 1 far from 0
				box(10, 0, 0, 19, 9, 9), // 2 touches 0 and 1 (margin)
				box(0, 0, 10, 8, 9, 19))); // 3 right next to 0: within the margin (a one-block gap is apart)
		assertArrayEquals(new int[0], s.dependencies(1));
		assertArrayEquals(new int[] {0, 1}, s.dependencies(2));
		assertArrayEquals(new int[] {0}, s.dependencies(3));
		boolean[] fin = new boolean[4];
		boolean[] run = new boolean[4];
		assertEquals(0, s.next(fin, run));
		run[0] = true;
		assertEquals(1, s.next(fin, run), "a far part goes while 0 builds");
		run[1] = true;
		assertEquals(-1, s.next(fin, run), "2 and 3 wait for their neighbours");
		run[0] = false;
		fin[0] = true;
		assertEquals(3, s.next(fin, run), "lowest ready part first");
	}

	@Test
	void anyInterleavingEndsLikeTheSequentialBuild() {
		Random rnd = new Random(7);
		for (int round = 0; round < 200; round++) {
			int n = 2 + rnd.nextInt(12);
			List<int[]> boxes = new ArrayList<>();
			List<Map<Long, Integer>> cells = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				int x = rnd.nextInt(30);
				int z = rnd.nextInt(30);
				int w = 1 + rnd.nextInt(6);
				int d = 1 + rnd.nextInt(6);
				boxes.add(box(x, 0, z, x + w - 1, 2, z + d - 1));
				Map<Long, Integer> c = new HashMap<>();
				for (int k = 0; k < 10; k++) {
					long key = (long) (x + rnd.nextInt(w)) * 1000 + (z + rnd.nextInt(d));
					c.put(key, i * 100 + k);
				}
				cells.add(c);
			}
			Map<Long, Integer> sequential = new HashMap<>();
			cells.forEach(sequential::putAll);
			PartSchedule s = PartSchedule.of(boxes);
			boolean[] fin = new boolean[n];
			boolean[] run = new boolean[n];
			List<Integer> live = new ArrayList<>();
			Map<Long, Integer> world = new HashMap<>();
			int crews = 1 + rnd.nextInt(4);
			int done = 0;
			while (done < n) {
				int j;
				while (live.size() < crews && (j = s.next(fin, run)) >= 0) {
					live.add(j);
					run[j] = true;
				}
				for (int a = 0; a < live.size(); a++) {
					for (int b = a + 1; b < live.size(); b++) {
						assertTrue(s.independent(live.get(a), live.get(b)), "running parts never meet");
					}
				}
				// finish a random running part; its cells land in the world
				int k = live.remove(rnd.nextInt(live.size()));
				world.putAll(cells.get(k));
				run[k] = false;
				fin[k] = true;
				done++;
			}
			assertEquals(sequential, world, "round " + round);
		}
	}

	@Test
	void emptyPlansMeetNothing() {
		PartSchedule s = PartSchedule.ofPlans(List.of(BuildPlan.of(List.of()), BuildPlan.of(List.of())));
		assertTrue(s.independent(0, 1));
		assertFalse(s.independent(0, 0));
	}
}
