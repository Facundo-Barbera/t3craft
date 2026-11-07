package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GridPathfinderTest {
	/** Flat floor at y=64 with solid wall cells (x,z) blocked. */
	static Walkable withWalls(Set<Long> walls) {
		return (x, y, z) -> y == 64 && !walls.contains(key(x, z)) ? 64 : Double.NaN;
	}

	static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	@Test
	void straightLineOnFlatGround() {
		List<Pos> p = new GridPathfinder(Walkable.flat(64)).find(new Pos(0.5, 64, 0.5), new Pos(8.5, 64, 0.5));
		assertNotNull(p);
		assertEquals(2, p.size(), "string pulling leaves only the endpoints");
		assertEquals(new Pos(8.5, 64, 0.5), p.getLast());
	}

	@Test
	void walksAroundAWall() {
		Set<Long> walls = new java.util.HashSet<>();
		for (int z = -3; z <= 3; z++) {
			walls.add(key(4, z));
		}
		GridPathfinder f = new GridPathfinder(withWalls(walls));
		List<Pos> p = f.find(new Pos(0.5, 64, 0.5), new Pos(8.5, 64, 0.5));
		assertNotNull(p);
		assertTrue(p.size() > 2, "needs a corner to get round the wall");
		for (int i = 0; i + 1 < p.size(); i++) {
			assertTrue(f.clear(p.get(i), p.get(i + 1), null), "every segment is walkable");
		}
	}

	@Test
	void noPathWhenEnclosed() {
		Set<Long> walls = new java.util.HashSet<>();
		for (int d = -1; d <= 1; d++) {
			walls.add(key(d, -1));
			walls.add(key(d, 1));
			walls.add(key(-1, d));
			walls.add(key(1, d));
		}
		assertNull(new GridPathfinder(withWalls(walls)).find(new Pos(0.5, 64, 0.5), new Pos(6.5, 64, 0.5)));
	}

	@Test
	void avoidsThePlayer() {
		Pos player = new Pos(4.5, 64, 0.5);
		GridPathfinder f = new GridPathfinder(Walkable.flat(64));
		List<Pos> p = f.find(new Pos(0.5, 64, 0.5), new Pos(8.5, 64, 0.5), player);
		assertNotNull(p);
		assertTrue(p.size() > 2, "detours around the player");
		for (int i = 0; i + 1 < p.size(); i++) {
			assertTrue(f.clear(p.get(i), p.get(i + 1), player));
		}
	}

	@Test
	void stepsUpOneBlock() {
		// x >= 4 is one block higher.
		Walkable stairs = (x, y, z) -> (x < 4 && y == 64) || (x >= 4 && y == 65) ? y : Double.NaN;
		List<Pos> p = new GridPathfinder(stairs).find(new Pos(0.5, 64, 0.5), new Pos(7.5, 65, 0.5));
		assertNotNull(p);
		assertEquals(65, p.getLast().y());
	}
}
