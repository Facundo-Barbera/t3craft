package dev.t3craft.client.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.client.config.T3CraftSettings.EnvPrefs;
import dev.t3craft.t3.NpcView;
import dev.t3craft.t3.ThreadShell;
import dev.t3craft.t3.ThreadState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class VisibilityPolicyTest {
	static final long NOW = Instant.parse("2026-11-07T12:00:00Z").toEpochMilli();
	static final long H = 3_600_000L;

	static ThreadShell thread(String id, long ago, String status) {
		String iso = Instant.ofEpochMilli(NOW - ago).toString();
		JsonObject o = JsonParser.parseString("""
				{"createdBy":"user","creationSource":"web","id":"%s","projectId":"project-1","title":"Thread %s",
				 "providerInstanceId":"claudeAgent","modelSelection":{"instanceId":"claudeAgent","model":"m"},
				 "runtimeMode":"approval-required","interactionMode":"default","branch":null,"worktreePath":null,
				 "lineage":{"parentThreadId":null,"relationshipToParent":null,"rootThreadId":"%s"},
				 "forkedFrom":null,"activeProviderThreadId":null,"latestRunId":null,"activeRunId":null,"status":"%s",
				 "pendingRuntimeRequest":null,"latestVisibleMessage":null,"latestUserMessageAt":"%s",
				 "hasActionableProposedPlan":false,"itemCount":0,"visibleItemCount":0,
				 "createdAt":"%s","updatedAt":"%s","archivedAt":null,"settledOverride":null,"settledAt":null,"deletedAt":null}
				""".formatted(id, id, id, status, iso, iso, iso)).getAsJsonObject();
		return ThreadShell.fromJson(o);
	}

	static final List<ThreadShell> THREADS = List.of(
			thread("att", 100 * H, "waiting"),
			thread("work", 50 * H, "running"),
			thread("r1", 1 * H, "completed"),
			thread("r6", 6 * H, "completed"),
			thread("r20", 20 * H, "completed"));

	static List<String> ids(List<ThreadShell> xs) {
		return xs.stream().map(ThreadShell::id).toList();
	}

	@Test
	void defaultsMatchVisibility() {
		assertEquals(List.of("att", "work", "r1", "r6", "r20"), ids(VisibilityPolicy.select(T3CraftSettings.defaults(), "e", THREADS, NOW)));
	}

	@Test
	void hiddenEnvironmentShowsNothing() {
		T3CraftSettings s = T3CraftSettings.defaults().withEnv("e", EnvPrefs.DEFAULT.withVisible(false));
		assertEquals(List.of(), VisibilityPolicy.select(s, "e", THREADS, NOW));
		assertEquals(5, VisibilityPolicy.select(s, "other", THREADS, NOW).size());
	}

	@Test
	void toggleWindowAndCapPerEnvironment() {
		T3CraftSettings s = T3CraftSettings.defaults().withEnv("e", EnvPrefs.DEFAULT.withFilter(false, true, true, 8.0, null));
		assertEquals(List.of("work", "r1", "r6"), ids(VisibilityPolicy.select(s, "e", THREADS, NOW)));
		s = T3CraftSettings.defaults().withEnv("e", EnvPrefs.DEFAULT.withFilter(true, false, false, null, null));
		assertEquals(List.of("att"), ids(VisibilityPolicy.select(s, "e", THREADS, NOW)));
		s = T3CraftSettings.defaults().withEnv("e", EnvPrefs.DEFAULT.withFilter(true, true, true, null, 2));
		assertEquals(List.of("att", "work"), ids(VisibilityPolicy.select(s, "e", THREADS, NOW)));
	}

	@Test
	void loosestCoversEveryEnvironment() {
		T3CraftSettings s = T3CraftSettings.defaults()
				.withEnv("a", EnvPrefs.DEFAULT.withFilter(true, true, true, 72.0, null))
				.withEnv("b", EnvPrefs.DEFAULT.withFilter(true, true, true, 1.0, 90));
		var o = VisibilityPolicy.loosest(s);
		assertEquals(72 * H, o.recentWindowMs());
		assertEquals(90, o.maxPerEnvironment());
	}

	static NpcView view(String id, String env, ThreadState st) {
		return new NpcView(id, env, env, id, id, null, st, st.color, st.glyph, id, "", null, st.priority, false, env, env, "steve", null);
	}

	@Test
	void filterViewsAppliesHiddenTogglesAndCap() {
		List<NpcView> views = List.of(view("1", "a", ThreadState.NEEDS_ATTENTION), view("2", "a", ThreadState.WORKING),
				view("3", "a", ThreadState.DONE), view("4", "a", ThreadState.IDLE), view("5", "b", ThreadState.WORKING));
		T3CraftSettings s = T3CraftSettings.defaults()
				.withEnv("a", EnvPrefs.DEFAULT.withFilter(true, false, true, null, 2))
				.withEnv("b", EnvPrefs.DEFAULT.withVisible(false));
		assertEquals(List.of("1", "3"), VisibilityPolicy.filterViews(s, views).stream().map(NpcView::id).toList());
	}
}
