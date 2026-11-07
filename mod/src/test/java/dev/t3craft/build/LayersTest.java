package dev.t3craft.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LayersTest {
	@Test
	void bottomLayerFirstThenRowsSnakingAlongX() {
		// Given in a scrambled order: (x, y, z)
		int[][] p = {
				{1, 1, 0}, // 0
				{0, 0, 1}, // 1
				{1, 0, 0}, // 2
				{0, 0, 0}, // 3
				{1, 0, 1}, // 4
				{0, 1, 0}, // 5
		};
		int[] order = order(p);
		// y=0: row z=0 ascending x (3, 2), row z=1 descending x (4, 1); then y=1 row z=0 (5, 0).
		assertArrayEquals(new int[] {3, 2, 4, 1, 5, 0}, order);
	}

	@Test
	void samePositionKeepsOriginalOrder() {
		int[][] p = {{2, 0, 0}, {0, 0, 0}, {2, 0, 0}, {0, 0, 0}};
		assertArrayEquals(new int[] {1, 3, 0, 2}, order(p));
	}

	@Test
	void negativeRowsSnakeToo() {
		int[][] p = {{0, 0, -1}, {1, 0, -1}};
		// z = -1 is odd: descending x.
		assertArrayEquals(new int[] {1, 0}, order(p));
	}

	@Test
	void rejectsMismatchedArrays() {
		assertThrows(IllegalArgumentException.class, () -> Layers.order(new int[1], new int[2], new int[1]));
	}

	private static int[] order(int[][] p) {
		int[] x = new int[p.length];
		int[] y = new int[p.length];
		int[] z = new int[p.length];
		for (int i = 0; i < p.length; i++) {
			x[i] = p[i][0];
			y[i] = p[i][1];
			z[i] = p[i][2];
		}
		return Layers.order(x, y, z);
	}
}
