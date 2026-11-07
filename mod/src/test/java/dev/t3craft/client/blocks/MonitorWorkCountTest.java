package dev.t3craft.client.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.t3craft.blocks.Binding;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.WorkstationThread;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Work counting for the monitors (fix/hq-fps): the renderer rebuilds a monitor's glyph runs only when
 * {@link MonitorScreens.Shown#version()} changes, so with steady threads a frame must rebuild nothing
 * and allocate no new screen, whatever the monitor shows (a thread, an unknown thread, nothing).
 */
class MonitorWorkCountTest {
	/** The renderer's cache decision ({@code MonitorRenderer.extractRenderState}), counting rebuilds. */
	static final class FakeRenderer {
		final Map<String, Long> cached = new HashMap<>();
		int rebuilds;

		void frame(MonitorScreens screens, List<Binding> monitors) {
			for (Binding b : monitors) {
				MonitorScreens.Shown s = screens.show(b);
				Long v = cached.get(s.id());
				if (v == null || v != s.version()) {
					cached.put(s.id(), s.version());
					rebuilds++;
				}
			}
		}
	}

	static WorkstationThread t(String key, StationStatus s, String activity) {
		return new WorkstationThread(key, "vps", key.substring(key.indexOf(':') + 1), "Title " + key, "vps:api", 0, s, activity, null);
	}

	@Test
	void steadyHqRebuildsNothingPerFrame() {
		MonitorScreens screens = new MonitorScreens();
		List<WorkstationThread> threads = List.of(t("vps:a", StationStatus.WORKING, "a"), t("vps:b", StationStatus.IDLE, "b"),
				t("vps:c", StationStatus.ATTENTION, "c"));
		List<Binding> monitors = List.of(Binding.thread("vps:a"), Binding.thread("vps:b"), Binding.thread("vps:c"),
				Binding.thread("vps:gone"), Binding.NONE, Binding.NONE);
		FakeRenderer r = new FakeRenderer();
		screens.tick(threads, 0);
		r.frame(screens, monitors);
		int first = r.rebuilds;
		assertEquals(5, first, "one build per distinct screen (two unbound monitors share one)");
		// 20 s at 20 tps and 60 fps, same minute: no rebuild at all
		for (int tick = 1; tick <= 400; tick++) {
			screens.tick(threads, tick * 50L);
			for (int f = 0; f < 3; f++) {
				r.frame(screens, monitors);
			}
		}
		assertEquals(first, r.rebuilds, "monitor model recomputed without a state change");
	}

	@Test
	void unboundAndMissingScreensAreNotRebuiltPerFrame() {
		MonitorScreens screens = new MonitorScreens();
		screens.tick(List.of(), 0);
		assertSame(screens.show(Binding.NONE), screens.show(Binding.NONE));
		assertSame(screens.show(Binding.thread("vps:x")), screens.show(Binding.thread("vps:x")));
	}

	@Test
	void oneChangeRebuildsOneMonitor() {
		MonitorScreens screens = new MonitorScreens();
		FakeRenderer r = new FakeRenderer();
		List<Binding> monitors = List.of(Binding.thread("vps:a"), Binding.thread("vps:b"));
		screens.tick(List.of(t("vps:a", StationStatus.WORKING, "a"), t("vps:b", StationStatus.WORKING, "b")), 0);
		r.frame(screens, monitors);
		int before = r.rebuilds;
		screens.tick(List.of(t("vps:a", StationStatus.ATTENTION, "a"), t("vps:b", StationStatus.WORKING, "b")), 50);
		r.frame(screens, monitors);
		r.frame(screens, monitors);
		assertEquals(before + 1, r.rebuilds);
	}

	@Test
	void aThreadThatLeavesAndComesBackGetsAFreshVersion() {
		// The cache restarts a returning thread at version 1; the epoch keeps the renderer from
		// reusing glyphs it cached for that thread's old content.
		MonitorScreens screens = new MonitorScreens();
		screens.tick(List.of(t("vps:a", StationStatus.WORKING, "old")), 0);
		long v1 = screens.show(Binding.thread("vps:a")).version();
		screens.tick(List.of(), 50);
		screens.tick(List.of(t("vps:a", StationStatus.ERROR, "new")), 100);
		assertNotEquals(v1, screens.show(Binding.thread("vps:a")).version());
	}
}
