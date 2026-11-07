package dev.t3craft.client.archive;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Which closed thread sits in which shelf slot. Same rules as the HQ layout's archive
 * ({@code HqLayout.allocate}, rule 5), with the shelf's capacity:
 * <ol>
 *   <li>the {@code capacity} most recently closed threads get a slot ({@link ArchiveEntry#NEWEST_FIRST});
 *   older ones are only in the catalog, so a new close pushes out the oldest and nobody else moves;</li>
 *   <li>a thread that already had a slot keeps it;</li>
 *   <li>newcomers, in {@code (project key, entry key)} order, take the lowest free slot.</li>
 * </ol>
 * Keys are {@code <env>:<thread>} (as {@code NpcView.id()}). Pure and deterministic: the same set
 * of entries in any order, with the same previous assignment, gives the same slots.
 */
public final class ArchiveSlots {
	private ArchiveSlots() {
	}

	/** Entry key → slot (0..capacity-1), keeping {@code previous}'s slots where possible. */
	public static Map<String, Integer> assign(List<ArchiveEntry> entries, @Nullable Map<String, Integer> previous, int capacity) {
		List<ArchiveEntry> chosen = entries.stream().sorted(ArchiveEntry.NEWEST_FIRST).limit(Math.max(0, capacity)).toList();
		Map<String, Integer> prev = previous == null ? Map.of() : previous;
		TreeMap<Integer, String> bySlot = new TreeMap<>();
		TreeSet<Integer> taken = new TreeSet<>();
		List<ArchiveEntry> newcomers = new java.util.ArrayList<>();
		for (ArchiveEntry e : chosen.stream().sorted(Comparator.comparing((ArchiveEntry x) -> x.projectKey()).thenComparing(x -> x.key())).toList()) {
			Integer old = prev.get(e.key());
			if (old != null && old >= 0 && old < capacity && taken.add(old)) {
				bySlot.put(old, e.key());
			} else {
				newcomers.add(e);
			}
		}
		int next = 0;
		for (ArchiveEntry e : newcomers) {
			while (taken.contains(next)) {
				next++;
			}
			taken.add(next);
			bySlot.put(next, e.key());
		}
		Map<String, Integer> out = new LinkedHashMap<>();
		bySlot.forEach((slot, key) -> out.put(key, slot));
		return out;
	}
}
