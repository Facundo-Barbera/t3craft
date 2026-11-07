package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.workstation.StationScreen;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.WorkstationThread;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A large screen bound to a project or environment: active threads, most urgent first, with their last event. */
class ProjectFeedTest {
	static WorkstationThread t(String key, String group, String title, StationStatus s, String activity) {
		int colon = key.indexOf(':');
		return new WorkstationThread(key, key.substring(0, colon), key.substring(colon + 1), title, group, 0, s, activity, null);
	}

	static final List<WorkstationThread> THREADS = List.of(
			t("vps:t0", "vps:api", "Fix login", StationStatus.WORKING, "Editing auth.ts"),
			t("vps:t1", "vps:api", "Add tests", StationStatus.ATTENTION, "Approve command?"),
			t("vps:t2", "vps:api", "Old work", StationStatus.ARCHIVED, "done"),
			t("vps:t3", "vps:web", "Landing", StationStatus.IDLE, null),
			t("acmesite:w0", "acmesite:web", "Other env", StationStatus.ERROR, "boom"));

	static List<String> text(StationScreen s) {
		return s.lines().stream().map(StationScreen.Line::text).toList();
	}

	@Test
	void projectFeedListsItsActiveThreadsMostUrgentFirst() {
		StationScreen s = ProjectFeed.render(Binding.project("vps:api"), THREADS, 40, 10);
		assertEquals(List.of(
				"▤ api · 2 active threads",
				"! Add tests", "↳ Approve command?",
				"⚙ Fix login", "↳ Editing auth.ts"), text(s));
		assertEquals(StationStatus.ATTENTION.color, s.lines().get(1).color(), "title in the status colour");
		assertEquals(StationStatus.ATTENTION, s.status(), "the feed's status is its most urgent thread");
	}

	@Test
	void envFeedCoversEveryProjectOfTheEnvironment() {
		StationScreen s = ProjectFeed.render(Binding.env("vps"), THREADS, 40, 4);
		// three active threads, three rows: one line each, no room for "+N more"
		assertEquals(List.of("▤ vps · 3 active threads", "! Add tests — Approve command?", "⚙ Fix login — Editing auth.ts",
				"· Landing"), text(s));
	}

	@Test
	void entriesThatDoNotFitCollapse() {
		StationScreen s = ProjectFeed.render(Binding.env("vps"), THREADS, 40, 3);
		assertEquals(List.of("▤ vps · 3 active threads", "! Add tests — Approve command?", "+2 more"), text(s));
	}

	@Test
	void linesAreClippedToTheScreenWidth() {
		StationScreen s = ProjectFeed.render(Binding.project("vps:api"), THREADS, 12, 10);
		for (String line : text(s)) {
			assertTrue(line.length() <= 12, line);
		}
	}

	@Test
	void emptyFeed() {
		assertEquals(List.of("▤ nothing · 0 active threads", "No active threads"),
				text(ProjectFeed.render(Binding.project("vps:nothing"), THREADS, 40, 5)));
	}

	@Test
	void memoisedUntilAThreadChanges() {
		MonitorScreens screens = new MonitorScreens();
		screens.tick(THREADS, 1_000);
		MonitorScreens.Shown a = screens.show(Binding.project("vps:api"), 40, 10);
		MonitorScreens.Shown b = screens.show(Binding.project("vps:api"), 40, 10);
		assertEquals(a, b);
		screens.tick(List.of(THREADS.get(0), t("vps:t1", "vps:api", "Add tests", StationStatus.WORKING, "Running tests")), 2_000);
		MonitorScreens.Shown c = screens.show(Binding.project("vps:api"), 40, 10);
		assertNotEquals(a.version(), c.version());
		assertTrue(text(c.screen()).contains("⚙ Add tests"));
		// threads and other bindings keep the lone-monitor screen
		assertEquals(screens.show(Binding.thread("vps:t0")), screens.show(Binding.thread("vps:t0"), 40, 10));
	}
}
