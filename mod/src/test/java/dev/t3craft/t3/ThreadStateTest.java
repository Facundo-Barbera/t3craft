package dev.t3craft.t3;

import static dev.t3craft.t3.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThreadStateTest {
	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = {
			"idle | {} | IDLE",
			"blocking approval | {\"status\":\"running\",\"pendingRuntimeRequest\":{\"id\":\"r\",\"kind\":\"command\",\"createdAt\":\"2026-11-07T10:00:00Z\",\"blocking\":true}} | NEEDS_ATTENTION",
			"request without flag | {\"status\":\"running\",\"pendingRuntimeRequest\":{\"id\":\"r\",\"kind\":\"file-change\",\"createdAt\":\"2026-11-07T10:00:00Z\"}} | NEEDS_ATTENTION",
			"non-blocking question | {\"status\":\"running\",\"activityRunStatus\":\"running\",\"pendingRuntimeRequest\":{\"id\":\"r\",\"kind\":\"user_input\",\"createdAt\":\"2026-11-07T10:00:00Z\",\"blocking\":false}} | WORKING",
			"waiting | {\"status\":\"waiting\"} | NEEDS_ATTENTION",
			"plan | {\"status\":\"completed\",\"hasActionableProposedPlan\":true} | NEEDS_ATTENTION",
			"guests | {\"pendingGuestMessageCount\":2} | NEEDS_ATTENTION",
			"running | {\"status\":\"running\"} | WORKING",
			"queued | {\"status\":\"queued\"} | WORKING",
			"activity run | {\"status\":\"completed\",\"activityRunStatus\":\"starting\"} | WORKING",
			"background task | {\"status\":\"completed\",\"pendingBackgroundTasks\":[{\"kind\":\"shell\",\"taskId\":\"t\"}]} | WORKING",
			"failed | {\"status\":\"failed\",\"lastError\":\"boom\"} | ERROR",
			"completed | {\"status\":\"completed\"} | DONE",
			"interrupted | {\"status\":\"interrupted\"} | IDLE",
			"cancelled | {\"status\":\"cancelled\"} | IDLE",
	})
	void maps(String name, String extra, ThreadState expected) {
		assertEquals(expected, ThreadState.of(thread("t", H, extra)));
	}
}
