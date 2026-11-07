package dev.t3craft.t3;

import static dev.t3craft.t3.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class VisibilityTest {
	static final Visibility.Options OPTS = Visibility.Options.defaults();

	static List<String> ids(List<ThreadShell> xs) {
		return xs.stream().map(ThreadShell::id).toList();
	}

	@Test
	void hidesArchivedSettledDeletedSubagents() {
		var xs = List.of(
				thread("arch", H, "{\"status\":\"running\",\"archivedAt\":\"" + iso(H) + "\"}"),
				thread("settled", H, "{\"settledAt\":\"" + iso(H) + "\"}"),
				thread("settled2", H, "{\"settledOverride\":\"settled\",\"status\":\"waiting\"}"),
				thread("deleted", H, "{\"deletedAt\":\"" + iso(H) + "\"}"),
				thread("sub", H, "{\"status\":\"running\",\"lineage\":{\"parentThreadId\":\"p\",\"relationshipToParent\":\"subagent\",\"rootThreadId\":\"p\"}}"));
		assertEquals(List.of(), ids(Visibility.select(xs, NOW, OPTS)));
	}

	@Test
	void activeOverrideUndoesSettled() {
		assertEquals(List.of("u"), ids(Visibility.select(List.of(thread("u", H, "{\"settledAt\":\"" + iso(2 * H) + "\",\"settledOverride\":\"active\"}")), NOW, OPTS)));
	}

	@Test
	void keepsOldWorkingWaitingAndTurnInProgress() {
		var xs = List.of(
				thread("run", 30 * 24 * H, "{\"status\":\"running\"}"),
				thread("turn", 72 * H, "{\"activeRunId\":\"r1\"}"),
				thread("input", 72 * H, "{\"status\":\"running\",\"pendingRuntimeRequest\":{\"id\":\"q\",\"kind\":\"user_input\",\"createdAt\":\"" + iso(72 * H) + "\",\"blocking\":true}}"),
				thread("waiting", 72 * H, "{\"status\":\"waiting\"}"));
		assertEquals(List.of("input", "waiting", "run", "turn").stream().sorted().toList(), ids(Visibility.select(xs, NOW, OPTS)).stream().sorted().toList());
	}

	@Test
	void recentVsStale() {
		var xs = List.of(thread("recent", 23 * H, "{\"status\":\"completed\"}"), thread("stale", 25 * H, "{\"status\":\"completed\"}"));
		assertEquals(List.of("recent"), ids(Visibility.select(xs, NOW, OPTS)));
		// expiry: two hours later the recent one is gone
		assertEquals(List.of(), ids(Visibility.select(xs, NOW + 2 * H, OPTS)));
	}

	@Test
	void updatedAtAloneIsNotActivity() {
		assertEquals(List.of(), ids(Visibility.select(List.of(thread("touched", 72 * H, "{\"updatedAt\":\"" + iso(H) + "\"}")), NOW, OPTS)));
	}

	@Test
	void capAndPriority() {
		var xs = List.of(thread("r1", H), thread("r2", 2 * H), thread("r3", 3 * H),
				thread("work", 100 * H, "{\"status\":\"running\"}"), thread("att", 200 * H, "{\"status\":\"waiting\"}"));
		assertEquals(List.of("att", "work", "r1", "r2"), ids(Visibility.select(xs, NOW, new Visibility.Options(24 * H, 4, false))));
	}

	@Test
	void defaultCapIs40() {
		var many = new java.util.ArrayList<ThreadShell>();
		for (int i = 0; i < 55; i++) {
			many.add(thread("r%02d".formatted(i), (i + 1) * 60_000L));
		}
		var shown = Visibility.select(many, NOW, OPTS);
		assertEquals(40, shown.size());
		assertEquals("r00", shown.get(0).id());
		assertEquals("r39", shown.get(39).id());
	}

	@Test
	void presentationNamespacesIdsAndResolvesStyle() {
		NpcView a = Presentation.present("vps", "my-vps", thread("t", H, "{\"status\":\"waiting\",\"title\":\"Fix login\"}"), "telar");
		NpcView b = Presentation.present("acmesite", "my-acmesite", thread("t", H), null);
		assertEquals("vps:t", a.id());
		assertEquals("acmesite:t", b.id());
		assertEquals("! Fix login", a.label());
		assertEquals("my-vps · telar", a.sublabel());
		assertEquals(ThreadState.NEEDS_ATTENTION.color, a.color());
		assertEquals("vps:project-1", a.groupId());
		assertEquals("(no project)", b.groupLabel());
		assertEquals("attention", a.particle());
		assertEquals("vps/needs_attention", a.skin());
	}
}
