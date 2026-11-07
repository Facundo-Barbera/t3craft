package dev.t3craft.client.workstation;

import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.client.npc.behavior.Anchor;
import dev.t3craft.client.npc.behavior.Pos;
import dev.t3craft.client.npc.behavior.GridLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlacementTest {
	private static WorkstationThread t(String id, String group, StationStatus s) {
		return new WorkstationThread(id, "vps", id, id, group, 2, s, null, null);
	}

	@Test
	void inFrontOfFacingSouth() {
		// Yaw 0 looks towards +z: computer one block south, screen turned back north (yaw ±180).
		StationPlacement p = StationPlacement.inFrontOf(10.5, 64, 20.5, 0f, 1.0);
		assertEquals(10, p.computerX());
		assertEquals(64, p.computerY());
		assertEquals(21, p.computerZ());
		assertEquals(180f, Math.abs(p.screenYaw()), 1e-4);
		assertEquals(64 + StationPlacement.SCREEN_HEIGHT, p.screenY(), 1e-9);
		// Nudged towards the desk (north, −z) from the block centre.
		assertTrue(p.screenZ() < 21.5);
		assertEquals(10.5, p.screenX(), 1e-6);
	}

	@Test
	void inFrontOfFacingEast() {
		// Yaw −90 looks towards +x.
		StationPlacement p = StationPlacement.inFrontOf(0.5, 70, 0.5, -90f, 1.0);
		assertEquals(1, p.computerX());
		assertEquals(0, p.computerZ());
		assertEquals(90f, p.screenYaw(), 1e-4);
	}

	@Test
	void blockKeyDistinguishesNeighboursAndNegatives() {
		Set<Long> keys = new HashSet<>();
		for (int x = -2; x <= 2; x++) {
			for (int y = -64; y <= -62; y++) {
				for (int z = -2; z <= 2; z++) {
					assertTrue(keys.add(StationPlacement.blockKey(x, y, z)));
				}
			}
		}
	}

	@Test
	void deskPlacementSitsInFrontOfGridDesk() {
		Pos origin = new Pos(100.5, 64, 100.5);
		List<WorkstationThread> threads = List.of(t("vps:b", "g1", StationStatus.WORKING), t("vps:a", "g1", StationStatus.IDLE),
				t("vps:c", "g2", StationStatus.ERROR));
		GridLayout grid = new GridLayout();
		List<GridLayout.Member> ms = new ArrayList<>();
		for (WorkstationThread th : threads) {
			ms.add(new GridLayout.Member(th.key(), th.groupId(), "vps"));
		}
		grid.update(origin, ms, GridLayout.Ground.UNKNOWN);
		DeskPlacement src = new DeskPlacement(() -> grid);
		src.prepare(threads);
		Set<Long> used = new HashSet<>();
		for (WorkstationThread th : threads) {
			StationPlacement p = src.placement(th);
			assertNotNull(p, th.key());
			Anchor desk = grid.desk(th.key(), th.groupId());
			assertEquals(StationPlacement.inFrontOf(desk.pos().x(), desk.pos().y(), desk.pos().z(), desk.yaw(), DeskPlacement.DISTANCE), p);
			assertTrue(used.add(p.computerKey()), "two stations on one block");
		}
	}

	@Test
	void deskPlacementHidesArchivedAndNeedsOrigin() {
		GridLayout grid = new GridLayout();
		grid.update(new Pos(0, 64, 0), List.of(new GridLayout.Member("vps:z", "g", "vps")), GridLayout.Ground.UNKNOWN);
		DeskPlacement src = new DeskPlacement(() -> grid);
		WorkstationThread archived = t("vps:z", "g", StationStatus.ARCHIVED);
		src.prepare(List.of(archived));
		assertNull(src.placement(archived));

		DeskPlacement noOrigin = new DeskPlacement(() -> null);
		WorkstationThread w = t("vps:w", "g", StationStatus.WORKING);
		noOrigin.prepare(List.of(w));
		assertNull(noOrigin.placement(w));
		assertNull(WorkstationPlacementSource.NONE.placement(w));
	}

	@Test
	void stationOnBlockWithFacing() {
		StationPlacement p = StationPlacement.on(5, 60, -3, 90f);
		assertEquals(5, p.computerX());
		assertEquals(-3, p.computerZ());
		assertEquals(5.4, p.screenX(), 1e-6); // nudged towards −x (yaw 90 faces west)
		assertEquals(-2.5, p.screenZ(), 1e-6);
	}
}
