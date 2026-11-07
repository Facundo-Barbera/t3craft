package dev.t3craft.client.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The registered {@link DemoStructureSource}s and the rule to pick one. Other features add theirs
 * with {@link #register} (re-registering an id replaces it). Instance owned by {@link DemoClient};
 * reachable for integration through {@link DemoClient#structures()}.
 */
public final class DemoStructures {
	/** Config value meaning "highest priority available source". */
	public static final String AUTO = "auto";

	private final List<DemoStructureSource> sources = new CopyOnWriteArrayList<>();

	public void register(DemoStructureSource source) {
		sources.removeIf(s -> s.id().equals(source.id()));
		sources.add(source);
	}

	public List<DemoStructureSource> all() {
		return List.copyOf(sources);
	}

	public Optional<DemoStructureSource> choose(String preferred) {
		return choose(sources, preferred);
	}

	/**
	 * The source named {@code preferred} if it is available; otherwise (or with {@link #AUTO}) the
	 * available source with the highest priority, ties broken by registration order. Empty if none
	 * is available.
	 */
	public static Optional<DemoStructureSource> choose(List<DemoStructureSource> candidates, String preferred) {
		List<DemoStructureSource> ok = new ArrayList<>();
		for (DemoStructureSource s : candidates) {
			if (safeAvailable(s)) {
				ok.add(s);
			}
		}
		if (preferred != null && !preferred.isBlank() && !AUTO.equalsIgnoreCase(preferred)) {
			for (DemoStructureSource s : ok) {
				if (s.id().equalsIgnoreCase(preferred)) {
					return Optional.of(s);
				}
			}
		}
		// Strictly greater replaces: on a tie the earlier registration stays.
		return ok.stream().reduce((a, b) -> b.priority() > a.priority() ? b : a);
	}

	private static boolean safeAvailable(DemoStructureSource s) {
		try {
			return s.available();
		} catch (RuntimeException e) {
			return false;
		}
	}
}
