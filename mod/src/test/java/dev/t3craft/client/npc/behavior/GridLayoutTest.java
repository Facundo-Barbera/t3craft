package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GridLayoutTest {
	static final Pos O = new Pos(100.5, 64, 200.5);

	/** 12 threads over 2 environments and 4 projects, unsorted on purpose. */
	static List<GridLayout.Member> roster() {
		List<GridLayout.Member> ms = new ArrayList<>();
		String[][] spec = {{"vps", "vps:api", "5"}, {"local", "local:mod", "3"}, {"vps", "vps:web", "2"}, {"local", "local:docs", "2"}};
		for (String[] s : spec) {
			for (int i = 0; i < Integer.parseInt(s[2]); i++) {
				ms.add(new GridLayout.Member(s[1] + "-t" + i, s[1], s[0]));
			}
		}
		java.util.Collections.reverse(ms);
		return ms;
	}

	static Map<String, Anchor> desks(GridLayout g, List<GridLayout.Member> ms) {
		Map<String, Anchor> out = new HashMap<>();
		for (GridLayout.Member m : ms) {
			out.put(m.id(), g.desk(m.id(), m.groupId()));
		}
		return out;
	}

	static GridLayout fresh(List<GridLayout.Member> ms) {
		GridLayout g = new GridLayout();
		g.update(O, ms, GridLayout.Ground.UNKNOWN);
		return g;
	}

	@Test
	void deterministicRegardlessOfInputOrder() {
		List<GridLayout.Member> ms = roster();
		List<GridLayout.Member> shuffled = new ArrayList<>(ms);
		java.util.Collections.shuffle(shuffled, new java.util.Random(7));
		assertEquals(desks(fresh(ms), ms), desks(fresh(shuffled), ms));
		assertEquals(fresh(ms).board(), fresh(shuffled).board());
		for (Anchor a : desks(fresh(ms), ms).values()) {
			assertNotNull(a);
			assertEquals(GridLayout.YAW, a.yaw(), "all desks face the same way");
		}
	}

	@Test
	void noTwoNpcsCloserThanTheSpacing() {
		List<GridLayout.Member> ms = new ArrayList<>(roster());
		for (int i = 0; i < 25; i++) {
			ms.add(new GridLayout.Member("big-" + i, "vps:big", "vps")); // overflows into extra blocks
		}
		GridLayout g = fresh(ms);
		List<Anchor> all = new ArrayList<>(desks(g, ms).values());
		assertEquals(ms.size(), all.size());
		assertTrue(GridLayout.SPACING >= 3, "nameplates need at least 3 blocks");
		for (int i = 0; i < all.size(); i++) {
			for (int j = i + 1; j < all.size(); j++) {
				assertTrue(all.get(i).pos().horizDist(all.get(j).pos()) >= GridLayout.SPACING - 1e-9,
						all.get(i) + " vs " + all.get(j));
			}
		}
	}

	@Test
	void groupedByProjectInAlignedRows() {
		List<GridLayout.Member> ms = roster();
		GridLayout g = fresh(ms);
		Map<String, List<Pos>> byGroup = new HashMap<>();
		for (GridLayout.Member m : ms) {
			byGroup.computeIfAbsent(m.groupId(), k -> new ArrayList<>()).add(g.desk(m.id(), m.groupId()).pos());
		}
		double blockW = (GridLayout.BLOCK_COLS - 1) * GridLayout.SPACING;
		double blockD = (GridLayout.BLOCK_ROWS - 1) * GridLayout.SPACING;
		for (Map.Entry<String, List<Pos>> e : byGroup.entrySet()) {
			List<Pos> ps = e.getValue();
			double minX = ps.stream().mapToDouble(Pos::x).min().orElseThrow();
			double minZ = ps.stream().mapToDouble(Pos::z).min().orElseThrow();
			for (Pos p : ps) {
				// Within one block and on the desk lattice (aligned rows and columns).
				assertTrue(p.x() - minX <= blockW && p.z() - minZ <= blockD, e.getKey());
				assertEquals(0, Math.floorMod((long) (p.x() - O.x()), GridLayout.SPACING), "column aligned");
				assertEquals(0, Math.floorMod((long) (p.z() - minZ), GridLayout.SPACING), "row aligned");
			}
			assertNotNull(g.header(e.getKey()), "sign spot");
			assertTrue(g.header(e.getKey()).z() < minZ, "header in front of the block");
			assertNotNull(g.home(e.getKey()));
		}
		// Different projects never share a block: the gap between them is at least an aisle.
		for (String a : byGroup.keySet()) {
			for (String b : byGroup.keySet()) {
				if (a.compareTo(b) < 0) {
					for (Pos p : byGroup.get(a)) {
						for (Pos q : byGroup.get(b)) {
							assertTrue(p.horizDist(q) >= GridLayout.SPACING + GridLayout.AISLE - 1e-9, a + " vs " + b);
						}
					}
				}
			}
		}
		// Ordered by environment then project: local:docs, local:mod, vps:api, vps:web.
		assertTrue(byGroup.get("local:docs").get(0).x() < byGroup.get("local:mod").get(0).x());
		assertTrue(byGroup.get("local:mod").get(0).x() < byGroup.get("vps:api").get(0).x());
	}

	@Test
	void addingOrRemovingThreadsNeverMovesTheOthers() {
		List<GridLayout.Member> ms = new ArrayList<>(roster());
		GridLayout g = fresh(ms);
		Map<String, Anchor> before = desks(g, ms);

		List<GridLayout.Member> more = new ArrayList<>(ms);
		more.add(new GridLayout.Member("local:aaa-new", "local:aaa", "local")); // sorts first
		more.add(new GridLayout.Member("vps:api-t00", "vps:api", "vps"));
		assertTrue(g.update(O, more, GridLayout.Ground.UNKNOWN));
		for (GridLayout.Member m : ms) {
			assertEquals(before.get(m.id()), g.desk(m.id(), m.groupId()), "moved " + m.id());
		}

		List<GridLayout.Member> fewer = new ArrayList<>(more);
		fewer.removeIf(m -> m.id().equals("vps:api-t2") || m.groupId().equals("local:docs"));
		assertTrue(g.update(O, fewer, GridLayout.Ground.UNKNOWN));
		for (GridLayout.Member m : fewer) {
			if (before.containsKey(m.id())) {
				assertEquals(before.get(m.id()), g.desk(m.id(), m.groupId()), "moved " + m.id());
			}
		}
		assertNull(g.desk("vps:api-t2", "vps:api"));
		assertNull(g.header("local:docs"));
		assertEquals(fewer.size(), g.size());
		assertFalse(g.update(O, fewer, GridLayout.Ground.UNKNOWN), "no change, no churn");

		// A freed cell is reused by the next thread of that project.
		Anchor freed = before.get("vps:api-t2");
		fewer.add(new GridLayout.Member("vps:api-t9", "vps:api", "vps"));
		g.update(O, fewer, GridLayout.Ground.UNKNOWN);
		assertEquals(freed, g.desk("vps:api-t9", "vps:api"));
	}

	@Test
	void followsTheGroundButIgnoresCliffs() {
		List<GridLayout.Member> ms = roster();
		// Gentle slope towards +x, and a pit at one column.
		GridLayout.Ground slope = (x, z) -> x == 108 ? 20 : 64 + (x - 100) / 8;
		GridLayout g = new GridLayout();
		g.update(O, ms, slope);
		for (GridLayout.Member m : ms) {
			Pos p = g.desk(m.id(), m.groupId()).pos();
			double expected = p.blockX() == 108 ? 64 : 64 + (p.blockX() - 100) / 8;
			assertEquals(expected, p.y(), 1e-9, m.id());
		}
	}

	@Test
	void groundProbePrefersTheFloorNearestTheOrigin() {
		// Floors at y 64 (grass) and y 70 (tree canopy): stay on the grass.
		Walkable two = (x, y, z) -> y == 64 || y == 70 ? y : Double.NaN;
		assertEquals(64, GridLayout.Ground.near(two, 65).y(0, 0));
		assertEquals(70, GridLayout.Ground.near(two, 69).y(0, 0));
		assertTrue(Double.isNaN(GridLayout.Ground.near(Walkable.flat(10), 64).y(0, 0)));
	}

	@Test
	void newOriginStartsOverAndEmptyHasNoBoard() {
		GridLayout g = new GridLayout();
		assertNull(g.board());
		List<GridLayout.Member> ms = roster();
		g.update(O, ms, GridLayout.Ground.UNKNOWN);
		Anchor a = g.desk(ms.get(0).id(), ms.get(0).groupId());
		assertNotNull(g.board());
		assertTrue(g.board().pos().z() < a.pos().z(), "board in front of the desks");
		g.update(O.add(50, 0, 0), ms, GridLayout.Ground.UNKNOWN);
		assertEquals(a.pos().x() + 50, g.desk(ms.get(0).id(), ms.get(0).groupId()).pos().x(), 1e-9);
	}
}
