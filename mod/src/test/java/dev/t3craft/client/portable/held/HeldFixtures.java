package dev.t3craft.client.portable.held;

import dev.t3craft.client.portable.PortableState;
import dev.t3craft.client.portable.PortableThread;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Synthetic data for the held-screen tests (no real T3 data). */
final class HeldFixtures {
	private HeldFixtures() {
	}

	/** Monospace measure: 6 px per code point (the vanilla font's typical advance). */
	static final HeldScreenModel.Measure MONO = s -> s.codePointCount(0, s.length()) * 6;

	static PortableThread thread(String env, String id, String title, String state, int priority, String activity, boolean settled) {
		return new PortableThread(env + ":" + id, env, "my-" + env, id, title, "web", state, 0x55FF55, activity, priority, settled);
	}

	static PortableState connected(PortableThread... threads) {
		Map<String, String[]> envs = new LinkedHashMap<>();
		envs.put("vps", new String[] {"my-vps", "ok", ""});
		envs.put("mini", new String[] {"my-mini", "ok", ""});
		return PortableState.of("direct", envs, Arrays.asList(threads), true, null);
	}

	static HeldScreenModel.Input input(PortableState s) {
		return new HeldScreenModel.Input(s, env -> dev.t3craft.client.chat.EnvBadge.of(env, "my-" + env, null), HeldScreenRaster.textWidth(),
				HeldScreenRaster.maxRows(), MONO);
	}

	/** All text of a row, spans joined. */
	static String text(HeldScreenModel.Row r) {
		StringBuilder b = new StringBuilder();
		r.spans().forEach(s -> b.append(s.text()));
		return b.toString();
	}

	static List<String> texts(List<HeldScreenModel.Row> rows) {
		return rows.stream().map(HeldFixtures::text).toList();
	}
}
