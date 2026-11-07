package dev.t3craft.blocks;

import dev.t3craft.hq.HqInput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Vanilla bootstrap (block registries) and a small HQ roster with NPC-style keys. */
final class BlocksTestSupport {
	private static boolean booted;

	private BlocksTestSupport() {
	}

	static synchronized void boot() {
		if (!booted) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			booted = true;
		}
	}

	/** Two projects in two environments; {@code vps:t4} is settled (goes to the archive). */
	static List<HqInput.Entry> roster() {
		List<HqInput.Entry> r = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			r.add(new HqInput.Entry("vps", "VPS", "api", "API", "vps:t" + i, i == 4));
		}
		for (int i = 0; i < 3; i++) {
			r.add(new HqInput.Entry("acmesite", "Acmesite", "web", "Web", "acmesite:w" + i, false));
		}
		return r;
	}

	static List<HqInput.Entry> shuffled(long seed) {
		List<HqInput.Entry> r = roster();
		Collections.shuffle(r, new Random(seed));
		return r;
	}

	static HqInput input() {
		return HqInput.fromRoster(roster());
	}
}
