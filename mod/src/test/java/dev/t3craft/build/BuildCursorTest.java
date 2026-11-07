package dev.t3craft.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildCursorTest {
	private static BuildCursor.Step placeAll(List<Integer> seen) {
		return i -> {
			seen.add(i);
			return BuildCursor.Outcome.PLACED;
		};
	}

	@Test
	void placesBudgetPerTickInOrderAndCompletes() {
		List<Integer> seen = new ArrayList<>();
		BuildCursor c = new BuildCursor(5, 2);
		assertEquals(JobState.PENDING, c.state());
		assertEquals(2, c.tick(placeAll(seen)));
		assertEquals(JobState.RUNNING, c.state());
		assertEquals(2, c.tick(placeAll(seen)));
		assertEquals(1, c.tick(placeAll(seen)));
		assertEquals(List.of(0, 1, 2, 3, 4), seen);
		assertEquals(JobState.COMPLETED, c.state());
		assertEquals(1.0, c.progress());
		assertEquals(5, c.placed());
		assertEquals(0, c.tick(placeAll(seen)));
	}

	@Test
	void fractionalBudgetSpreadsOverTicks() {
		List<Integer> seen = new ArrayList<>();
		BuildCursor c = new BuildCursor(3, 0.5);
		int[] perTick = new int[6];
		for (int t = 0; t < 6; t++) {
			perTick[t] = c.tick(placeAll(seen));
		}
		assertEquals("[0, 1, 0, 1, 0, 1]", java.util.Arrays.toString(perTick));
		assertEquals(JobState.COMPLETED, c.state());
	}

	@Test
	void pauseStopsAndDoesNotAccrueCredit() {
		List<Integer> seen = new ArrayList<>();
		BuildCursor c = new BuildCursor(10, 1);
		c.tick(placeAll(seen));
		assertTrue(c.pause());
		for (int t = 0; t < 20; t++) {
			assertEquals(0, c.tick(placeAll(seen)));
		}
		assertEquals(JobState.PAUSED, c.state());
		assertTrue(c.resume());
		assertEquals(1, c.tick(placeAll(seen)), "no burst after a long pause");
		assertEquals(2, c.done());
		assertFalse(c.resume());
	}

	@Test
	void pauseBeforeFirstTickKeepsItPaused() {
		BuildCursor c = new BuildCursor(3, 1);
		assertTrue(c.pause());
		assertEquals(0, c.tick(i -> BuildCursor.Outcome.PLACED));
		assertEquals(JobState.PAUSED, c.state());
	}

	@Test
	void cancelIsTerminalAndKeepsCounts() {
		List<Integer> seen = new ArrayList<>();
		BuildCursor c = new BuildCursor(10, 3);
		c.tick(placeAll(seen));
		assertTrue(c.cancel());
		assertEquals(0, c.tick(placeAll(seen)));
		assertEquals(JobState.CANCELLED, c.state());
		assertEquals(3, c.placed());
		assertFalse(c.cancel());
		assertFalse(c.pause());
		assertFalse(c.resume());
		assertEquals(0.3, c.progress(), 1e-9);
	}

	@Test
	void skipsAreFreeButBounded() {
		BuildCursor c = new BuildCursor(100, 1, 10);
		// Everything already built: one tick processes at most the skip cap (+ nothing placed).
		int applied = c.tick(i -> BuildCursor.Outcome.SKIPPED);
		assertEquals(10, applied);
		assertEquals(10, c.skipped());
		// Mixed: skips do not consume the single block of budget.
		BuildCursor m = new BuildCursor(4, 1);
		int a = m.tick(i -> i < 3 ? BuildCursor.Outcome.SKIPPED : BuildCursor.Outcome.PLACED);
		assertEquals(4, a);
		assertEquals(JobState.COMPLETED, m.state());
		assertEquals(1, m.placed());
		assertEquals(3, m.skipped());
	}

	@Test
	void failuresConsumeBudget() {
		BuildCursor c = new BuildCursor(5, 2);
		assertEquals(2, c.tick(i -> BuildCursor.Outcome.FAILED));
		assertEquals(2, c.failed());
	}

	@Test
	void emptyPlanCompletesOnFirstTick() {
		BuildCursor c = new BuildCursor(0, 1);
		assertEquals(1.0, c.progress());
		c.tick(i -> BuildCursor.Outcome.PLACED);
		assertEquals(JobState.COMPLETED, c.state());
	}

	@Test
	void rejectsBadBudget() {
		assertThrows(IllegalArgumentException.class, () -> new BuildCursor(1, 0));
		assertThrows(IllegalArgumentException.class, () -> new BuildCursor(1, Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> new BuildCursor(-1, 1));
	}
}
