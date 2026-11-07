package dev.t3craft.client.archive;

import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Which closed thread sits in which basement slot. Uses {@link HqLayout#allocate} itself with a
 * roster of only settled lines, so the slots are exactly the ones the HQ generator gives the same
 * threads (archive slots never depend on the active threads): a thread keeps its slot while it stays
 * listed, newcomers take the lowest free slot in {@code (project key, thread id)} order.
 *
 * <p>The archive has {@link HqLayout#ARCHIVE_CAPACITY} slots. When more threads are closed, the
 * {@code capacity} newest (by close time, {@link ArchiveEntry#NEWEST_FIRST}) get a slot; the rest
 * are only in the catalog. A new close therefore pushes out the oldest one and nobody else moves.
 * Thread ids in the layout are the entry keys ({@code <env>:<thread>}, as {@code NpcView.id()}).
 */
public final class ArchiveSlots {
	private ArchiveSlots() {
	}

	/** Slots for {@code entries} (any order), keeping {@code previous}'s slots where possible. */
	public static HqLayout assign(List<ArchiveEntry> entries, @Nullable HqLayout previous) {
		return assign(entries, previous, HqLayout.ARCHIVE_CAPACITY);
	}

	static HqLayout assign(List<ArchiveEntry> entries, @Nullable HqLayout previous, int capacity) {
		List<ArchiveEntry> chosen = entries.stream().sorted(ArchiveEntry.NEWEST_FIRST).limit(Math.max(0, capacity)).toList();
		HqInput.Builder b = HqInput.builder();
		for (ArchiveEntry e : chosen) {
			b.thread(e.projectKey(), e.projectLabel(), e.env(), e.envLabel(), e.key(), true);
		}
		HqLayout prev = previous == null ? null : new HqLayout(List.of(), previous.archive(), List.of());
		return HqLayout.allocate(b.build(), prev);
	}

	/** Entry key → slot index. */
	public static Map<String, Integer> slots(HqLayout layout) {
		Map<String, Integer> out = new LinkedHashMap<>();
		layout.archive().forEach(a -> out.put(a.threadId(), a.slot()));
		return out;
	}
}
