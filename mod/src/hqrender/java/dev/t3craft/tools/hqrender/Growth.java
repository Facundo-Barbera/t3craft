package dev.t3craft.tools.hqrender;

import dev.t3craft.hq.HqInput;
import java.util.Locale;

/**
 * The roster of the {@code --projects} / {@code --layout} renders of the citadel: N projects with varied thread
 * counts, three environments and settled threads. Each roster extends the previous one (same projects first, then new
 * ones), so growing {@code 4 -> 10 -> 20} keeps what existed in place.
 */
final class Growth {
	private Growth() {
	}

	private static final String[] NAMES = {"t3craft", "bridge", "website", "infra", "mobile", "docs", "billing", "search", "agentcraft", "ops",
			"design", "data", "auth", "chat", "metrics", "ml", "cli", "edge", "mail", "admin"};
	private static final String[] ENVS = {"vps", "laptop", "desktop", "cloud"};
	/** Active threads per project (7+ adds a floor, 13+ a third). */
	private static final int[] THREADS = {5, 9, 3, 6, 2, 13, 4, 1, 7, 3, 2, 5, 8, 1, 4, 3, 6, 2, 1, 4};

	/**
	 * {@code --leads N} (feat/lead-office): thread {@code t0} of each of the first N projects is a lead and sits
	 * in its own office project ({@code lead:<threadId>}, label {@code ★ <project>}), as
	 * {@code LeadOfficeClient.project} does in the game. 0 (the default) leaves every roster as it was.
	 */
	static int leads;

	static HqInput roster(int n) {
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < n; p++) {
			String env = ENVS[p % ENVS.length];
			String key = env + ":" + NAMES[p];
			for (int t = 0; t < THREADS[p]; t++) {
				String id = key + "-t" + t;
				if (t == 0 && p < leads) {
					b.thread(dev.t3craft.hq.lead.LeadOffices.projectKey(id), dev.t3craft.hq.lead.LeadOffices.label(NAMES[p]), env,
							env.toUpperCase(Locale.ROOT), id);
					continue;
				}
				b.thread(key, NAMES[p], env, env.toUpperCase(Locale.ROOT), id);
			}
			if (p % 3 == 0) {
				b.thread(key, NAMES[p], env, env.toUpperCase(Locale.ROOT), key + "-s0", true);
			}
		}
		return b.build();
	}
}
