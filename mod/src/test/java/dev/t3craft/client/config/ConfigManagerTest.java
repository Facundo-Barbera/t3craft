package dev.t3craft.client.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {
	@TempDir
	Path dir;

	@Test
	void updateSavesAndNotifiesListeners() throws Exception {
		List<String> warnings = new ArrayList<>();
		ConfigManager m = new ConfigManager(dir.resolve("t3craft.json"), warnings::add);
		m.load();
		List<T3CraftSettings> seen = new ArrayList<>();
		m.addListener(seen::add);
		ConfigManager.SaveResult r = m.update(s -> s.withNotifications(s.notifications().withEnabled(false)));
		assertTrue(r.ok(), r.message());
		assertFalse(m.settings().notifications().allowChat());
		assertEquals(1, seen.size());
		assertTrue(Files.readString(dir.resolve("t3craft.json")).contains("\"enabled\": false"));

		ConfigManager again = new ConfigManager(dir.resolve("t3craft.json"), warnings::add);
		assertFalse(again.load().notifications().enabled());
		assertTrue(warnings.isEmpty(), warnings.toString());
	}

	@Test
	void failingListenerDoesNotBreakOthers() {
		ConfigManager m = new ConfigManager(dir.resolve("t3craft.json"), w -> { });
		List<Integer> calls = new ArrayList<>();
		m.addListener(s -> {
			throw new IllegalStateException("boom");
		});
		m.addListener(s -> calls.add(1));
		m.update(s -> s);
		assertEquals(List.of(1), calls);
	}

	@Test
	void resetIsSafeWithoutInstance() {
		ConfigManager.reset();
		ConfigManager.reset();
	}

	@Test
	void notificationRules() {
		var n = T3CraftSettings.Notifications.defaults();
		assertTrue(n.allowSound("attention"));
		assertFalse(new T3CraftSettings.Notifications(true, true, true, true, false, true, true, true).allowSound("attention"));
		assertFalse(new T3CraftSettings.Notifications(true, true, true, false, true, true, true, true).allowSound("done"));
		assertFalse(n.withEnabled(false).allowToast());
		assertFalse(n.withEnabled(false).allowConnectionToast());
	}
}
