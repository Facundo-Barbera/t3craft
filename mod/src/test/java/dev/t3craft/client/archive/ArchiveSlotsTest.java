package dev.t3craft.client.archive;

import static dev.t3craft.client.archive.ArchiveFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.t3craft.hq.HqInput;
import dev.t3craft.hq.HqLayout;
import java.util.ArrayList;
import java.util.Collections;
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

	/** Same slots as the HQ layout of the full roster (actives + settled), at every step of a history. */
	@Test
	void matchesHqLayoutOfTheWholeRoster() {
		Random rnd = new Random(7);
		List<ArchiveEntry> pool = entries(40);
		HqLayout hq = null;
		HqLayout ours = null;
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
			ours = ArchiveSlots.assign(closed, ours);
			assertEquals(hq.archive(), ours.archive(), "step " + step);
		}
	}

	@Test
	void slotsAreStableAndIndependentOfOrder() {
		List<ArchiveEntry> list = entries(10);
		HqLayout a = ArchiveSlots.assign(list, null);
		List<ArchiveEntry> shuffled = new ArrayList<>(list);
		Collections.shuffle(shuffled, new Random(1));
		assertEquals(a, ArchiveSlots.assign(shuffled, null));

		// one leaves, one arrives: nobody else moves; the newcomer takes the freed lowest slot
		Map<String, Integer> before = ArchiveSlots.slots(a);
		List<ArchiveEntry> next = new ArrayList<>(list.subList(1, 10));
		next.add(entry("vps", "new", "p0", 0));
		Map<String, Integer> after = ArchiveSlots.slots(ArchiveSlots.assign(next, a));
		for (ArchiveEntry e : list.subList(1, 10)) {
			assertEquals(before.get(e.key()), after.get(e.key()), e.key());
		}
		assertEquals(before.get(list.get(0).key()), after.get("vps:new"));
	}

	@Test
	void onlyTheNewestGetASlotWhenFull() {
		List<ArchiveEntry> list = entries(8);
		HqLayout l = ArchiveSlots.assign(list, null, 5);
		// entries(i) closed (i+1) hours ago: t0..t4 are the newest
		assertEquals(List.of("acmesite:t1", "acmesite:t3", "vps:t0", "vps:t2", "vps:t4"),
				l.archive().stream().map(HqLayout.Archived::threadId).sorted().toList());
		assertTrue(l.overflow().isEmpty(), "older ones are catalog-only, not HQ overflow");
		assertEquals(HqLayout.ARCHIVE_CAPACITY, ArchiveSlots.assign(entries(200), null).archive().size());
	}

	@Test
	void ignoresModulesOfAPreviousFullLayout() {
		HqInput in = HqInput.builder().thread("vps:p", "p", "vps", "vps", "vps:a", false).thread("vps:p", "p", "vps", "vps", "vps:s", true).build();
		HqLayout full = HqLayout.plan(in);
		HqLayout ours = ArchiveSlots.assign(List.of(entry("vps", "s", "p", H)), full);
		assertTrue(ours.modules().isEmpty());
		assertEquals(full.archive(), ours.archive());
	}
}
