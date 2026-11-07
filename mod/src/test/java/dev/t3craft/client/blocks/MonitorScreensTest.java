package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.workstation.StationScreen;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.WorkstationModel;
import dev.t3craft.client.workstation.WorkstationThread;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Monitor content (reusing WorkstationModel) and its invalidation (version bumps only on change). */
class MonitorScreensTest {
	static WorkstationThread t(String key, StationStatus s, String activity) {
		return new WorkstationThread(key, "vps", key.substring(key.indexOf(':') + 1), "Fix login redirect loop", "vps:api", 0, s, activity, null);
	}

	@Test
	void sameLinesAsTheWorkstationModel() {
		MonitorScreens m = new MonitorScreens();
		WorkstationThread a = t("vps:a", StationStatus.WORKING, "Running tests in mod/: 12 of 40 passed");
		m.tick(List.of(a), 1_000);
		MonitorScreens.Shown s = m.show(Binding.thread("vps:a"));
		StationScreen expected = new WorkstationModel(MonitorScreens.LAYOUT).render(a, null);
		assertEquals(expected, s.screen());
		assertEquals(StationStatus.WORKING, s.screen().status());
		assertEquals(StationStatus.WORKING.color, s.screen().lines().get(1).color(), "status line in the status colour");
		for (StationScreen.Line l : s.screen().lines()) {
			assertTrue(l.text().length() <= MonitorScreens.LAYOUT.width(), l.text());
		}
	}

	@Test
	void versionOnlyMovesOnChange() {
		MonitorScreens m = new MonitorScreens();
		WorkstationThread a = t("vps:a", StationStatus.WORKING, "step 1");
		assertTrue(m.tick(List.of(a), 0));
		long v = m.show(Binding.thread("vps:a")).version();
		long rev = m.revision();
		for (int i = 1; i <= 50; i++) {
			assertFalse(m.tick(List.of(a), i * 10), "unchanged thread, first sighting has no age → no redraw");
		}
		assertEquals(v, m.show(Binding.thread("vps:a")).version());
		assertEquals(rev, m.revision());

		assertTrue(m.tick(List.of(t("vps:a", StationStatus.ATTENTION, "step 1")), 1_000));
		long v2 = m.show(Binding.thread("vps:a")).version();
		assertNotEquals(v, v2);
		assertTrue(m.show(Binding.thread("vps:a")).screen().plain().contains("NEEDS YOU"));
		// age label "now" → "1m": one more redraw, then quiet again within the same minute
		assertTrue(m.tick(List.of(t("vps:a", StationStatus.ATTENTION, "step 1")), 61_000));
		assertFalse(m.tick(List.of(t("vps:a", StationStatus.ATTENTION, "step 1")), 62_000));
	}

	@Test
	void unboundAndUnknown() {
		MonitorScreens m = new MonitorScreens();
		m.tick(List.of(t("vps:a", StationStatus.IDLE, null)), 0);
		assertEquals(MonitorScreens.UNBOUND, m.show(Binding.NONE).screen());
		assertEquals(MonitorScreens.UNBOUND, m.show(Binding.board(0)).screen(), "a board binding on a monitor shows nothing");
		MonitorScreens.Shown gone = m.show(Binding.thread("vps:gone"));
		assertTrue(gone.screen().plain().contains("OFFLINE"));
		assertTrue(gone.screen().plain().startsWith("gone"));
		assertNotEquals(gone.id(), m.show(Binding.thread("vps:a")).id());
	}

	@Test
	void forgetsThreadsThatLeave() {
		MonitorScreens m = new MonitorScreens();
		m.tick(List.of(t("vps:a", StationStatus.IDLE, null), t("vps:b", StationStatus.DONE, null)), 0);
		assertTrue(m.tick(List.of(t("vps:a", StationStatus.IDLE, null)), 10), "b left → revision moves");
		assertTrue(m.show(Binding.thread("vps:b")).screen().plain().contains("OFFLINE"));
	}

	@Test
	void archivedThreadOnAnArchiveMonitor() {
		MonitorScreens m = new MonitorScreens();
		m.tick(List.of(t("vps:old", StationStatus.ARCHIVED, "whatever")), 0);
		StationScreen s = m.show(Binding.thread("vps:old")).screen();
		assertEquals(StationStatus.ARCHIVED, s.status());
		assertEquals(2, s.lines().size(), "title + archived, no activity");
	}
}
