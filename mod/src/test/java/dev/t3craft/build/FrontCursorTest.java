package dev.t3craft.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FrontCursorTest {
	/** 3 layers of 8 entries. */
	static final int[] Y = {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2};

	@Test
	void frontsAdvanceTogetherOneStepEachPerTurn() {
		FrontCursor c = new FrontCursor(Fronts.split(Y, null, 4));
		List<Integer> fronts = new ArrayList<>();
		assertEquals(4, c.tick(4, (f, i) -> {
			fronts.add(f);
			return BuildCursor.Outcome.PLACED;
		}));
		assertEquals(List.of(0, 1, 2, 3), fronts, "four fronts placing in the same tick");
		assertEquals(4, c.placed());
	}

	@Test
	void layerBarrierNothingAboveBeforeBelowIsDone() {
		Fronts fr = Fronts.split(Y, null, 3);
		FrontCursor c = new FrontCursor(fr);
		List<Integer> order = new ArrayList<>();
		for (int t = 0; t < 50 && c.state() != JobState.COMPLETED; t++) {
			c.tick(5, (f, i) -> {
				order.add(i);
				return BuildCursor.Outcome.PLACED;
			});
		}
		assertEquals(JobState.COMPLETED, c.state());
		assertEquals(Y.length, order.size());
		for (int k = 1; k < order.size(); k++) {
			assertTrue(Y[order.get(k)] >= Y[order.get(k - 1)], "layer order kept at " + k);
		}
	}

	@Test
	void blockedFrontWaitsWhileTheOthersGoOnThenTheJobPauses() {
		FrontCursor c = new FrontCursor(Fronts.split(Y, null, 2));
		Set<Integer> placed = new HashSet<>();
		// Front 1 is short of its item for the whole first layer.
		FrontCursor.Step step = (f, i) -> {
			if (f == 1 && Y[i] == 0) {
				return BuildCursor.Outcome.BLOCKED;
			}
			placed.add(i);
			return BuildCursor.Outcome.PLACED;
		};
		c.tick(2, step);
		assertEquals(JobState.RUNNING, c.state(), "one front blocked, the other keeps building");
		assertTrue(c.frontBlocked(1));
		for (int t = 0; t < 10; t++) {
			c.tick(2, step);
		}
		assertEquals(Set.of(0, 1, 2, 3), placed, "front 0 finished its half of the layer");
		assertEquals(JobState.PAUSED, c.state());
		assertTrue(c.blocked(), "every front with work left is blocked: job waits at the layer");
		assertEquals(0, c.segment(), "never went up a layer");
		assertEquals(List.of(4, 5, 6, 7), toList(c.pendingInSegment()));
	}

	@Test
	void resumeNeverRepeatsNorSkipsWhateverThePausesAndBlocks() {
		Random r = new Random(42);
		for (int round = 0; round < 300; round++) {
			int n = r.nextInt(80);
			int[] y = new int[n];
			int cur = 0;
			for (int i = 0; i < n; i++) {
				if (r.nextInt(6) == 0) {
					cur++;
				}
				y[i] = cur;
			}
			FrontCursor c = new FrontCursor(Fronts.split(y, null, 1 + r.nextInt(5)), 3);
			int[] applied = new int[n];
			List<Integer> order = new ArrayList<>();
			for (int t = 0; t < 10_000 && !c.state().isTerminal(); t++) {
				int roll = r.nextInt(10);
				if (roll == 0) {
					c.pause();
				} else if (roll == 1 || c.blocked()) {
					c.resume();
				}
				c.tick(r.nextDouble() * 4, (f, i) -> {
					int o = r.nextInt(10);
					if (o == 0) {
						return BuildCursor.Outcome.BLOCKED;
					}
					applied[i]++;
					order.add(i);
					return o == 1 ? BuildCursor.Outcome.SKIPPED : o == 2 ? BuildCursor.Outcome.FAILED : BuildCursor.Outcome.PLACED;
				});
			}
			assertEquals(JobState.COMPLETED, c.state(), "round " + round);
			for (int i = 0; i < n; i++) {
				assertEquals(1, applied[i], "index " + i + " applied once (round " + round + ")");
			}
			assertEquals(n, c.done());
			assertEquals(n, c.placed() + c.skipped() + c.failed());
			for (int k = 1; k < order.size(); k++) {
				assertTrue(y[order.get(k)] >= y[order.get(k - 1)]);
			}
		}
	}

	@Test
	void pauseDoesNotAccrueCreditAndSkipsAreBounded() {
		FrontCursor c = new FrontCursor(Fronts.split(Y, null, 2), 5);
		c.pause();
		for (int t = 0; t < 20; t++) {
			assertEquals(0, c.tick(1, (f, i) -> BuildCursor.Outcome.PLACED));
		}
		c.resume();
		assertEquals(1, c.tick(1, (f, i) -> BuildCursor.Outcome.PLACED), "no burst after a long pause");
		assertEquals(5, c.tick(1, (f, i) -> BuildCursor.Outcome.SKIPPED), "skips are free but bounded");
	}

	@Test
	void fractionalAllowanceAndZeroAllowance() {
		FrontCursor c = new FrontCursor(Fronts.split(Y, null, 4));
		assertEquals(0, c.tick(0, (f, i) -> BuildCursor.Outcome.PLACED));
		assertEquals(0, c.tick(0.5, (f, i) -> BuildCursor.Outcome.PLACED));
		assertEquals(1, c.tick(0.5, (f, i) -> BuildCursor.Outcome.PLACED));
	}

	@Test
	void emptyPlanCompletesAndCancelIsTerminal() {
		FrontCursor e = new FrontCursor(Fronts.split(new int[0], null, 3));
		e.tick(1, (f, i) -> BuildCursor.Outcome.PLACED);
		assertEquals(JobState.COMPLETED, e.state());
		assertEquals(1.0, e.progress());
		FrontCursor c = new FrontCursor(Fronts.split(Y, null, 3));
		c.tick(3, (f, i) -> BuildCursor.Outcome.PLACED);
		assertTrue(c.cancel());
		assertFalse(c.cancel());
		assertEquals(0, c.tick(3, (f, i) -> BuildCursor.Outcome.PLACED));
		assertEquals(3, c.placed());
	}

	static List<Integer> toList(int[] a) {
		List<Integer> l = new ArrayList<>();
		for (int i : a) {
			l.add(i);
		}
		return l;
	}
}
