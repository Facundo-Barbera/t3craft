package dev.t3craft.hq;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Synthetic rosters for the village (renders, tests): {@code n} projects with varied thread counts
 * across four environments, a few settled threads, and some threads in the singular rooms. The roster
 * of {@code n} projects contains the roster of any smaller {@code n} unchanged, so growth can be
 * compared step by step.
 */
public final class VillageDemo {
	private static final String[] NAMES = {"t3craft", "bridge", "website", "infra", "mobile", "docs", "billing", "search", "ml-lab",
			"design", "auth", "data", "chat", "maps", "ops", "shop", "api", "notify", "media", "games", "survey", "wiki", "crm", "edge"};
	private static final String[] ENVS = {"vps", "laptop", "acmesite", "cloud"};
	/** Active threads per project (more than 6 builds annexes; more than 14 a second hamlet). */
	private static final int[] THREADS = {9, 3, 6, 2, 12, 4, 1, 7, 5, 15, 3, 8, 2, 6, 10, 1, 4, 11, 5, 3, 7, 2, 6, 4};

	private VillageDemo() {
	}

	public static VillageLayout.Input roster(int projects) {
		HqInput.Builder b = HqInput.builder();
		Map<String, VillageLayout.Room> rooms = new HashMap<>();
		for (int p = 0; p < projects; p++) {
			String name = NAMES[p % NAMES.length] + (p >= NAMES.length ? "-" + p : "");
			String env = ENVS[p % ENVS.length];
			String key = env + ":" + name;
			for (int t = 0; t < THREADS[p % THREADS.length]; t++) {
				b.thread(key, name, env, env.toUpperCase(Locale.ROOT), key + "-t" + t);
			}
			for (int t = 0; t < p % 3; t++) {
				b.thread(key, name, env, env.toUpperCase(Locale.ROOT), key + "-s" + t, true);
			}
			// one extra thread per project in a singular room: review, scheduled, attention, lounge in turn
			VillageLayout.Room r = VillageLayout.Room.values()[1 + p % 4];
			String extra = key + "-x";
			b.thread(key, name, env, env.toUpperCase(Locale.ROOT), extra);
			rooms.put(extra, r);
		}
		return new VillageLayout.Input(b.build(), rooms);
	}
}
