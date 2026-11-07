package dev.t3craft.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrontsTest {
	/** Every index exactly once, segments in plan order, lanes contiguous and ordered. */
	static void assertPartition(Fronts f, int[] y) {
		boolean[] seen = new boolean[y.length];
		int expectStart = 0;
		for (int s = 0; s < f.segments(); s++) {
			assertEquals(expectStart, f.segmentStart(s));
			int next = expectStart;
			for (int k = 0; k < f.fronts(); k++) {
				for (int i : f.lanes(s)[k]) {
					assertEquals(next++, i, "lanes are consecutive chunks of the plan order");
					assertTrue(!seen[i]);
					seen[i] = true;
					assertEquals(k, f.frontOf(i));
					assertEquals(y[expectStart], y[i], "one layer per segment");
				}
			}
			expectStart = next;
		}
		assertEquals(y.length, expectStart);
	}

	@Test
	void layersBecomeSegmentsSplitEvenlyAmongFronts() {
		int[] y = {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2};
		Fronts f = Fronts.split(y, null, 4);
		assertEquals(3, f.segments());
		assertPartition(f, y);
		for (int k = 0; k < 4; k++) {
			assertEquals(2, f.lanes(0)[k].length, "8 entries / 4 fronts");
			assertEquals(1, f.lanes(1)[k].length);
		}
		int busy = 0;
		for (int[] lane : f.lanes(2)) {
			busy += lane.length > 0 ? 1 : 0;
		}
		assertEquals(1, busy, "a one-block layer has a single front");
	}

	@Test
	void aLayerThatComesBackIsANewSegment() {
		// Hanging blocks placed top-down after the roof: plan order between layers is kept as is.
		int[] y = {0, 0, 3, 3, 2, 2, 1};
		Fronts f = Fronts.split(y, null, 2);
		assertEquals(4, f.segments());
		assertPartition(f, y);
		assertArrayEquals(new int[] {2}, f.lanes(1)[0]);
		assertArrayEquals(new int[] {4}, f.lanes(2)[0]);
	}

	@Test
	void entriesAtTheSamePositionStayInOneFront() {
		int[] y = {0, 0, 0, 0, 0, 0};
		long[] key = {1, 2, 3, 3, 4, 5}; // index 2 and 3: "clear with air, then place"
		Fronts f = Fronts.split(y, key, 2);
		assertPartition(f, y);
		assertEquals(f.frontOf(2), f.frontOf(3));
	}

	@Test
	void randomPlansArePartitionedExactly() {
		java.util.Random r = new java.util.Random(7);
		for (int round = 0; round < 200; round++) {
			int n = r.nextInt(60);
			int[] y = new int[n];
			long[] key = new long[n];
			int cur = 0;
			for (int i = 0; i < n; i++) {
				if (r.nextInt(5) == 0) {
					cur += r.nextBoolean() ? 1 : -1;
				}
				y[i] = cur;
				key[i] = r.nextInt(4) == 0 && i > 0 ? key[i - 1] : i * 10L;
			}
			Fronts f = Fronts.split(y, key, 1 + r.nextInt(6));
			assertPartition(f, y);
			for (int i = 1; i < n; i++) {
				if (key[i] == key[i - 1] && y[i] == y[i - 1]) {
					assertEquals(f.frontOf(i - 1), f.frontOf(i));
				}
			}
		}
	}

	@Test
	void emptyAndSingleFront() {
		assertEquals(0, Fronts.split(new int[0], null, 4).segments());
		int[] y = {0, 0, 1};
		Fronts f = Fronts.split(y, null, 1);
		List<Integer> order = new ArrayList<>();
		for (int s = 0; s < f.segments(); s++) {
			for (int i : f.lanes(s)[0]) {
				order.add(i);
			}
		}
		assertEquals(List.of(0, 1, 2), order, "one front = the old sequential order");
	}
}
