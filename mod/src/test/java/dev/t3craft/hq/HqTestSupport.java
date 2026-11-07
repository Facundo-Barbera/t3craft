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

	/** {@code projects} projects "env:p0".."env:pN" with {@code threads} active threads each ("env:p0-t0"...). */
	static HqInput input(int projects, int threads) {
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < projects; p++) {
			b.project("env:p" + p, "Project " + p, "env", "env");
			for (int t = 0; t < threads; t++) {
				b.thread("env:p" + p, "Project " + p, "env", "env", "env:p" + p + "-t" + t);
			}
		}
		return b.build();
	}

	/** The roster lines of an input (active and settled). */
	static List<HqInput.Entry> roster(HqInput in) {
		List<HqInput.Entry> out = new ArrayList<>();
		for (HqInput.Project p : in.projects()) {
			String env = p.key().substring(0, p.key().indexOf(':'));
			String project = p.key().substring(p.key().indexOf(':') + 1);
			for (String t : p.threads()) {
				out.add(new HqInput.Entry(env, p.envLabel(), project, p.label(), t, false));
			}
			for (String t : p.settled()) {
				out.add(new HqInput.Entry(env, p.envLabel(), project, p.label(), t, true));
			}
		}
		return out;
	}

	/** {@code input} plus one active thread in a project. */
	static HqInput plus(HqInput input, String projectKey, String threadId) {
		List<HqInput.Project> out = new ArrayList<>();
		boolean found = false;
		for (HqInput.Project p : input.projects()) {
			if (p.key().equals(projectKey)) {
				List<String> t = new ArrayList<>(p.threads());
				t.add(threadId);
				out.add(new HqInput.Project(p.key(), p.label(), p.envKey(), p.envLabel(), t, p.settled()));
				found = true;
			} else {
				out.add(p);
			}
		}
		if (!found) {
			out.add(new HqInput.Project(projectKey, projectKey, "env", "env", List.of(threadId)));
		}
		return new HqInput(out);
	}

	/** {@code input} without one thread (its project stays, maybe empty). */
	static HqInput minus(HqInput input, String threadId) {
		List<HqInput.Project> out = new ArrayList<>();
		for (HqInput.Project p : input.projects()) {
			List<String> t = new ArrayList<>(p.threads());
			t.remove(threadId);
			List<String> s = new ArrayList<>(p.settled());
			s.remove(threadId);
			out.add(new HqInput.Project(p.key(), p.label(), p.envKey(), p.envLabel(), t, s));
		}
		return new HqInput(out);
	}

	/** {@code input} with one active thread settled. */
	static HqInput settle(HqInput input, String threadId) {
		List<HqInput.Project> out = new ArrayList<>();
		for (HqInput.Project p : input.projects()) {
			List<String> t = new ArrayList<>(p.threads());
			List<String> s = new ArrayList<>(p.settled());
			if (t.remove(threadId)) {
				s.add(threadId);
			}
			out.add(new HqInput.Project(p.key(), p.label(), p.envKey(), p.envLabel(), t, s));
		}
		return new HqInput(out);
	}
}
