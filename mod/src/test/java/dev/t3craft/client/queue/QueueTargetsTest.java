package dev.t3craft.client.queue;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.t3.ShellState;
import dev.t3craft.t3.ThreadShell;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QueueTargetsTest {
	static ThreadShell shell(String id, String status, String activity, String requestedAt, String archivedAt) {
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		o.addProperty("title", "Title " + id);
		o.addProperty("status", status);
		if (activity != null) {
			o.addProperty("activityRunStatus", activity);
		}
		if (requestedAt != null) {
			o.addProperty("latestRunRequestedAt", requestedAt);
		}
		if (archivedAt != null) {
			o.addProperty("archivedAt", archivedAt);
		}
		return ThreadShell.fromJson(o);
	}

	@Test
	void workingThreadsMostRecentFirstCapped() {
		ShellState.View vps = new ShellState.View(List.of(
				shell("idle", "idle", null, "2026-11-07T11:00:00Z", null),
				shell("old", "running", "running", "2026-11-07T08:00:00Z", null),
				shell("new", "running", "running", "2026-11-07T10:00:00Z", null),
				shell("gone", "running", "running", "2026-11-07T11:30:00Z", "2026-11-07T11:31:00Z")), Map.of(), true);
		ShellState.View foc = new ShellState.View(List.of(shell("mid", "queued", null, "2026-11-07T09:00:00Z", null)), Map.of(), true);
		Map<String, ShellState.View> views = Map.of("vps", vps, "acmesite", foc);

		assertEquals(List.of("vps:new", "acmesite:mid", "vps:old"),
				QueueTargets.working(views, 10).stream().map(QueueStore.Key::toString).toList());
		assertEquals(List.of("vps:new", "acmesite:mid"), QueueTargets.working(views, 2).stream().map(QueueStore.Key::toString).toList());
		assertTrue(QueueTargets.working(Map.of(), 4).isEmpty());

		assertEquals("Title new", QueueTargets.title(views, "vps", "new"));
		assertNull(QueueTargets.title(views, "vps", "nope"));
		assertNull(QueueTargets.title(views, "nope", "new"));
	}

	@Test
	void queueStringsAreTranslatedInBothLanguages() throws Exception {
		String[] keys = {"t3craft.queue.title", "t3craft.queue.badge", "t3craft.queue.empty", "t3craft.queue.none", "t3craft.queue.none_working",
				"t3craft.queue.unavailable", "t3craft.queue.unknown", "t3craft.queue.unknown_short", "t3craft.queue.loading", "t3craft.queue.paused",
				"t3craft.queue.held", "t3craft.queue.automatic", "t3craft.queue.up", "t3craft.queue.down", "t3craft.queue.cancel",
				"t3craft.queue.sending", "t3craft.queue.cmd.header", "t3craft.queue.cmd.usage", "t3craft.queue.cmd.no_run",
				"t3craft.operate.kind.queue_cancel", "t3craft.operate.kind.queue_reorder"};
		for (String l : new String[] {"en_us", "es_es"}) {
			JsonObject o;
			try (var in = getClass().getResourceAsStream("/assets/t3craft/lang/" + l + ".json")) {
				assertNotNull(in, l);
				o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			}
			for (String k : keys) {
				assertTrue(o.has(k), l + " misses " + k);
			}
			for (dev.t3craft.t3.OperateResult.Outcome out : dev.t3craft.t3.OperateResult.Outcome.values()) {
				assertTrue(o.has("t3craft.queue.result." + out.wire), l + " misses result " + out.wire);
			}
		}
	}
}
