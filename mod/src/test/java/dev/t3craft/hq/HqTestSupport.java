package dev.t3craft.hq;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Shared fixtures: vanilla bootstrap (block registries) and synthetic inputs. */
final class HqTestSupport {
	private static boolean booted;

	private HqTestSupport() {
	}

	static synchronized void boot() {
		if (!booted) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			booted = true;
		}
	}

	/** {@code projects} projects "p0".."pN" with {@code threads} threads each ("p0-t0"...). */
	static HqInput input(int projects, int threads) {
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < projects; p++) {
			b.project("env:p" + p, "Project " + p, "env");
			for (int t = 0; t < threads; t++) {
				b.thread("env:p" + p, "Project " + p, "env", "env:p" + p + "-t" + t);
			}
		}
		return b.build();
	}

	/** {@code input} plus one thread in a project. */
	static HqInput plus(HqInput input, String projectKey, String threadId) {
		List<HqInput.Project> out = new ArrayList<>();
		boolean found = false;
		for (HqInput.Project p : input.projects()) {
			if (p.key().equals(projectKey)) {
				List<String> t = new ArrayList<>(p.threads());
				t.add(threadId);
				out.add(new HqInput.Project(p.key(), p.label(), p.envLabel(), t));
				found = true;
			} else {
				out.add(p);
			}
		}
		if (!found) {
			out.add(new HqInput.Project(projectKey, projectKey, "env", List.of(threadId)));
		}
		return new HqInput(out);
	}

	/** {@code input} without one thread (its project stays, maybe empty). */
	static HqInput minus(HqInput input, String threadId) {
		List<HqInput.Project> out = new ArrayList<>();
		for (HqInput.Project p : input.projects()) {
			List<String> t = new ArrayList<>(p.threads());
			t.remove(threadId);
			out.add(new HqInput.Project(p.key(), p.label(), p.envLabel(), t));
		}
		return new HqInput(out);
	}
}
