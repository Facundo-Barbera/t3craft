package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduledTaskTest {
	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void decodesAFullTask() {
		ScheduledTask t = ScheduledTask.fromJson(json("""
				{"id":"st-1","title":"Nightly audit","prompt":"Run the audit","enabled":true,
				 "schedule":{"type":"interval","everyMs":86400000},"projectId":"p1","threadId":null,
				 "workspaceStrategy":{"type":"local"},"modelSelection":{"provider":"x"},
				 "nextRunAt":"2026-11-07T10:00:00.000Z","lastRunAt":"2026-11-06T10:00:00Z",
				 "lastRunStatus":"failed","lastRunError":"boom","runCount":4,"someNewField":[1,2]}"""));
		assertEquals("st-1", t.id());
		assertEquals("Nightly audit", t.name());
		assertTrue(t.enabled());
		assertTrue(t.active());
		assertEquals("interval", t.scheduleType());
		assertEquals("every 1d", t.schedule());
		assertEquals("p1", t.projectId());
		assertNull(t.threadId());
		assertEquals(Instant.parse("2026-11-07T10:00:00Z").toEpochMilli(), t.nextRunAt());
		assertEquals(Instant.parse("2026-11-06T10:00:00Z").toEpochMilli(), t.lastRunAt());
		assertEquals(ScheduledTask.RunStatus.FAILED, t.lastRunStatus());
		assertEquals("boom", t.lastRunError());
		assertEquals(4, t.runCount());
		assertNull(t.blockedByPolicy());
	}

	@Test
	void toleratesMissingAndWrongFields() {
		ScheduledTask t = ScheduledTask.fromJson(json("{\"id\":\"x\",\"enabled\":\"yes\",\"nextRunAt\":\"not a date\",\"runCount\":-3,\"schedule\":5,\"lastRunStatus\":\"exploded\"}"));
		assertEquals("x", t.name(), "falls back to the id");
		assertFalse(t.enabled());
		assertNull(t.nextRunAt());
		assertEquals(0, t.runCount());
		assertEquals("", t.schedule());
		assertEquals(ScheduledTask.RunStatus.UNKNOWN, t.lastRunStatus());
		assertNull(ScheduledTask.fromJson(json("{\"title\":\"no id\"}")));
		assertNull(ScheduledTask.fromJson(null));
	}

	@Test
	void nameFallsBackToThePromptsFirstLine() {
		ScheduledTask t = ScheduledTask.fromJson(json("{\"id\":\"x\",\"title\":\"  \",\"prompt\":\"\\n  Check deps\\nand more\"}"));
		assertEquals("Check deps", t.name());
	}

	@Test
	void blockedByPolicyIsNotActive() {
		ScheduledTask t = ScheduledTask.fromJson(json("{\"id\":\"x\",\"enabled\":true,\"blockedByPolicy\":\"webhooks off\"}"));
		assertEquals("webhooks off", t.blockedByPolicy());
		assertFalse(t.active());
	}

	@Test
	void describesSchedules() {
		assertEquals("every 30m", ScheduledTask.describe(json("{\"type\":\"interval\",\"everyMs\":1800000}")));
		assertEquals("every 1h 30m", ScheduledTask.describe(json("{\"type\":\"interval\",\"everyMs\":5400000}")));
		assertEquals("every 45s", ScheduledTask.describe(json("{\"type\":\"interval\",\"everyMs\":45000}")));
		assertEquals("09:30", ScheduledTask.describe(json("{\"type\":\"fixed_time\",\"timeOfDay\":\"09:30\"}")));
		assertEquals("09:30 Mon-Fri", ScheduledTask.describe(json("{\"type\":\"fixed_time\",\"timeOfDay\":\"09:30\",\"weekdays\":[1,2,3,4,5]}")));
		assertEquals("18:00 Sat-Sun", ScheduledTask.describe(json("{\"type\":\"fixed_time\",\"timeOfDay\":\"18:00\",\"weekdays\":[6,0]}")));
		assertEquals("07:00 Mon,Wed,Sun", ScheduledTask.describe(json("{\"type\":\"fixed_time\",\"timeOfDay\":\"07:00\",\"weekdays\":[0,3,1,9,\"x\"]}")));
		assertEquals("once", ScheduledTask.describe(json("{\"type\":\"once\",\"runAt\":\"2026-12-01T10:00:00Z\"}")));
		assertEquals("webhook", ScheduledTask.describe(json("{\"type\":\"webhook\",\"signature\":null}")));
		assertEquals("cron", ScheduledTask.describe(json("{\"type\":\"cron\"}")), "unknown types pass through");
		assertEquals("", ScheduledTask.describe(null));
	}

	@Test
	void listFromAChunkValue() {
		List<ScheduledTask> l = ScheduledTask.listFrom(json("{\"tasks\":[{\"id\":\"a\"},42,{\"title\":\"no id\"},{\"id\":\"b\"}]}"));
		assertEquals(List.of("a", "b"), l.stream().map(ScheduledTask::id).toList());
		assertTrue(ScheduledTask.listFrom(json("{\"tasks\":[]}")).isEmpty());
		assertNull(ScheduledTask.listFrom(json("{\"other\":1}")));
	}

	@Test
	void runStatusWire() {
		assertEquals(ScheduledTask.RunStatus.NEVER, ScheduledTask.RunStatus.of(null));
		assertEquals(ScheduledTask.RunStatus.RUNNING, ScheduledTask.RunStatus.of("running"));
		assertEquals(ScheduledTask.RunStatus.SUCCEEDED, ScheduledTask.RunStatus.of("succeeded"));
		assertEquals(ScheduledTask.RunStatus.UNKNOWN, ScheduledTask.RunStatus.of("?"));
	}
}
