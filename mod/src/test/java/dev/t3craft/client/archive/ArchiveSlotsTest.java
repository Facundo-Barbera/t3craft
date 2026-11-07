package dev.t3craft.client.archive;

import static dev.t3craft.client.archive.ArchiveFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ArchiveSlotsTest {
	static List<ArchiveEntry> entries(int n) {
		List<ArchiveEntry> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(entry(i % 2 == 0 ? "vps" : "acmesite", "t" + i, "p" + (i % 3), (i + 1) * H));
		}
		return out;
	}

	/**
	 * Same rules as the HQ layout's archive: with its capacity, the slots equal those of
	 * {@code HqLayout.allocate} over the whole roster (actives included), at every step of a history.
	 */
	@Test
	void matchesHqLayoutRules() {
		Random rnd = new Random(7);
		List<ArchiveEntry> pool = entries(40);
		HqLayout hq = null;
		Map<String, Integer> ours = null;
		for (int step = 0; step < 30; step++) {
			List<ArchiveEntry> closed = new ArrayList<>();
			HqInput.Builder b = HqInput.builder();
			for (ArchiveEntry e : pool) {
				int r = rnd.nextInt(3);
				if (r == 0) {
					closed.add(e);
					b.thread(e.projectKey(), e.projectLabel(), e.env(), e.envLabel(), e.key(), true);
				} else if (r == 1) {
					b.thread(e.projectKey(), e.projectLabel(), e.env(), e.envLabel(), e.key(), false);
				}
			}
			hq = HqLayout.allocate(b.build(), hq);
			Collections.shuffle(closed, rnd);
			ours = ArchiveSlots.assign(closed, ours, HqLayout.ARCHIVE_CAPACITY);
			Map<String, Integer> expected = new LinkedHashMap<>();
			hq.archive().forEach(a -> expected.put(a.threadId(), a.slot()));
			assertEquals(expected, ours, "step " + step);
		}
	}

	@Test
	void slotsAreStableAndIndependentOfOrder() {
		List<ArchiveEntry> list = entries(10);
		Map<String, Integer> before = ArchiveSlots.assign(list, null, 24);
		List<ArchiveEntry> shuffled = new ArrayList<>(list);
		Collections.shuffle(shuffled, new Random(1));
		assertEquals(before, ArchiveSlots.assign(shuffled, null, 24));
		assertEquals(10, new HashSet<>(before.values()).size());

		// one leaves, one arrives: nobody else moves; the newcomer takes the freed lowest slot
		List<ArchiveEntry> next = new ArrayList<>(list.subList(1, 10));
		next.add(entry("vps", "new", "p0", 0));
		Map<String, Integer> after = ArchiveSlots.assign(next, before, 24);
		for (ArchiveEntry e : list.subList(1, 10)) {
			assertEquals(before.get(e.key()), after.get(e.key()), e.key());
		}
		assertEquals(before.get(list.get(0).key()), after.get("vps:new"));
	}

	@Test
	void onlyTheNewestGetASlotWhenFull() {
		List<ArchiveEntry> list = entries(8);
		Map<String, Integer> s = ArchiveSlots.assign(list, null, 5);
		// entries(i) closed (i+1) hours ago: t0..t4 are the newest
		assertEquals(List.of("acmesite:t1", "acmesite:t3", "vps:t0", "vps:t2", "vps:t4"), s.keySet().stream().sorted().toList());
		assertTrue(s.values().stream().allMatch(v -> v >= 0 && v < 5));

		// a new close pushes out the oldest slotted one; the others keep their slots
		List<ArchiveEntry> more = new ArrayList<>(list);
		more.add(entry("vps", "fresh", "p9", 0));
		Map<String, Integer> s2 = ArchiveSlots.assign(more, s, 5);
		assertFalse(s2.containsKey("vps:t4"), "the oldest of the five leaves the shelf");
		assertEquals(s.get("vps:t4"), s2.get("vps:fresh"));
		for (String k : List.of("vps:t0", "acmesite:t1", "vps:t2", "acmesite:t3")) {
			assertEquals(s.get(k), s2.get(k));
		}
	}

	@Test
	void previousSlotsBeyondANewCapacityAreReassigned() {
		Map<String, Integer> big = ArchiveSlots.assign(entries(30), null, 148);
		Map<String, Integer> small = ArchiveSlots.assign(entries(30), big, 24);
		assertEquals(24, small.size());
		assertTrue(small.values().stream().allMatch(v -> v >= 0 && v < 24));
		assertEquals(24, new HashSet<>(small.values()).size());
		assertTrue(ArchiveSlots.assign(entries(3), null, 0).isEmpty());
	}
}
