package dev.t3craft.client.portable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Synthetic rows for the portable terminal tests (no real T3 data). */
final class PortableFixtures {
	private PortableFixtures() {
	}

	static PortableThread thread(String env, String id, String title, String state, int priority) {
		return new PortableThread(env + ":" + id, env, env.toUpperCase(), id, title, "proj-" + env, state, 0x55FF55, null, priority, false);
	}

	static Map<String, String[]> envs(String... idStatus) {
		Map<String, String[]> m = new LinkedHashMap<>();
		for (int i = 0; i < idStatus.length; i += 2) {
			m.put(idStatus[i], new String[] {idStatus[i].toUpperCase(), idStatus[i + 1], ""});
		}
		return m;
	}

	/** vps ok with 3 threads (one needs attention), mini ok with 1. */
	static PortableState twoEnvs() {
		return PortableState.of("direct · read-only", envs("vps", "ok", "mini", "ok"), List.of(
				thread("vps", "a", "Alpha", "working", 2),
				thread("vps", "b", "Bravo", "needs_attention", 0),
				thread("vps", "c", "Charlie", "done", 3),
				thread("mini", "d", "Delta", "idle", 4)), true, null);
	}
}
