package dev.t3craft.client.npc.behavior;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class LayoutTest {
	static final Pos O = new Pos(100, 64, 200);

	@Test
	void physicalAnchorsWinOverTheGrid() {
		Anchor desk = Anchor.at(1, 2, 3, 90);
		Anchor hqBoard = Anchor.at(7, 8, 9, 0);
		AnchorSource base = new AnchorSource() {
			@Override
			public @Nullable Anchor desk(String npcId, String groupId) {
				return npcId.equals("a") ? desk : null;
			}

			@Override
			public @Nullable Anchor board() {
				return hqBoard;
			}
		};
		GridLayout grid = new GridLayout();
		grid.update(O, List.of(new GridLayout.Member("a", "g", "e"), new GridLayout.Member("b", "g", "e")), GridLayout.Ground.UNKNOWN);
		AnchorSource l = new LayeredAnchors(base, grid);
		assertEquals(desk, l.desk("a", "g"));
		assertNotEquals(grid.desk("a", "g"), l.desk("a", "g"));
		assertEquals(grid.desk("b", "g"), l.desk("b", "g"), "falls back per NPC");
		assertEquals(hqBoard, l.board());
		assertEquals(grid.board(), new LayeredAnchors(AnchorSource.NONE, grid).board(), "no HQ: grid board");
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
