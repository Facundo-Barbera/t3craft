package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class LayoutTest {
	static final Pos O = new Pos(100, 64, 200);

	@Test
	void ringMatchesTheRegistryLayout() {
		RingLayout r = RingLayout.of(O, List.of(new RingLayout.Member("a", "g1", 2), new RingLayout.Member("b", "g1", 2),
				new RingLayout.Member("c", "g2", 0)));
		// g2 is more urgent (priority 0) so it gets the first plot at the origin.
		assertEquals(O, r.home("g2").pos());
		assertEquals(O.add(RingLayout.PLOT, 0, 0), r.home("g1").pos());
		Anchor c = r.desk("c", "g2");
		assertEquals(RingLayout.ringRadius(1), c.pos().horizDist(O), 1e-9);
		// desks face the plot centre
		Pos ahead = c.pos().add(-Math.sin(Math.toRadians(c.yaw())), 0, Math.cos(Math.toRadians(c.yaw())));
		assertTrue(ahead.horizDist(O) < c.pos().horizDist(O));
		assertNotNull(r.board());
		assertNull(r.desk("zzz", "g1"));
	}

	@Test
	void physicalAnchorsWinOverTheRing() {
		Anchor desk = Anchor.at(1, 2, 3, 90);
		AnchorSource base = new AnchorSource() {
			@Override
			public @Nullable Anchor desk(String npcId, String groupId) {
				return npcId.equals("a") ? desk : null;
			}
		};
		RingLayout ring = RingLayout.of(O, List.of(new RingLayout.Member("a", "g", 0), new RingLayout.Member("b", "g", 0)));
		AnchorSource l = new LayeredAnchors(base, ring);
		assertEquals(desk, l.desk("a", "g"));
		assertEquals(ring.desk("b", "g"), l.desk("b", "g"), "falls back per NPC");
		assertEquals(ring.board(), l.board());
	}

	@Test
	void boardSlotsDoNotOverlapAndFaceTheBoard() {
		Anchor board = Anchor.at(0, 64, 0, 180); // NPCs face -z, stand on +z side
		java.util.Set<Pos> seen = new java.util.HashSet<>();
		for (int i = 0; i < 12; i++) {
			Anchor s = BoardSlots.slot(board, List.of(), i);
			assertTrue(s.pos().z() > 1, "in front of the board");
			assertEquals(180f, s.yaw());
			for (Pos p : seen) {
				assertTrue(p.horizDist(s.pos()) > 0.9, "slot " + i + " too close to another");
			}
			seen.add(s.pos());
		}
		Anchor explicit = Anchor.at(9, 9, 9, 0);
		assertEquals(explicit, BoardSlots.slot(board, List.of(explicit), 0));
	}
}
