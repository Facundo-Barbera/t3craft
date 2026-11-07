package dev.t3craft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Pure part of {@link T3CraftTab}: an ordered list of per-module contributions, flattened lazily
 * (when the tab is built) and de-duplicated. A module that is off simply never contributes.
 */
public final class TabContents<T> {
	private final List<Supplier<? extends List<? extends T>>> contributions = new ArrayList<>();

	public synchronized void add(Supplier<? extends List<? extends T>> contribution) {
		contributions.add(contribution);
	}

	public void add(List<? extends T> items) {
		List<? extends T> copy = List.copyOf(items);
		add(() -> copy);
	}

	/** Every contributed entry, in contribution order, without duplicates or nulls. */
	public synchronized List<T> entries() {
		Set<T> out = new LinkedHashSet<>();
		for (Supplier<? extends List<? extends T>> c : contributions) {
			List<? extends T> items = c.get();
			if (items == null) {
				continue;
			}
			for (T t : items) {
				if (t != null) {
					out.add(t);
				}
			}
		}
		return List.copyOf(out);
	}
}
