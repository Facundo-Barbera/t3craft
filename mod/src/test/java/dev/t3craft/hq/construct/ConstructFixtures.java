package dev.t3craft.hq.construct;

import dev.t3craft.hq.HqInput;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

final class ConstructFixtures {
	private static boolean booted;

	private ConstructFixtures() {
	}

	static synchronized void boot() {
		if (!booted) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			booted = true;
		}
	}

	/** {@code projects} projects alternating vps/acmesite, {@code active} active and {@code settled} settled threads each. */
	static HqInput input(int projects, int active, int settled) {
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < projects; p++) {
			String env = p % 2 == 0 ? "vps" : "acmesite";
			String key = env + ":p" + p;
			b.project(key, "Project " + p, env, env);
			for (int t = 0; t < active; t++) {
				b.thread(key, "Project " + p, env, env, key + "-a" + t, false);
			}
			for (int t = 0; t < settled; t++) {
				b.thread(key, "Project " + p, env, env, key + "-s" + t, true);
			}
		}
		return b.build();
	}
}
